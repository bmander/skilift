"""SkiLift is a minimal bicycle+transit journey planner."""

import math
import os
from abc import ABC, abstractmethod
from collections import Counter, defaultdict
from datetime import timedelta
from typing import (
    Any,
    Dict,
    Hashable,
    Iterable,
    Iterator,
    List,
    NamedTuple,
    Optional,
    Sequence,
    Set,
    Tuple,
)
from zipfile import ZipFile

import numpy as np
import osmium
import pandas as pd
import rasterio
from dotenv import load_dotenv
from numpy.typing import NDArray
from shapely.geometry import LineString, Point
from shapely.strtree import STRtree

ALIGHTING_WEIGHT = 60.0  # utils; where 1 util ~= 1 second of travel time
WALKING_SPEED = 1.2  # meters per second
WALKING_RELUCTANCE = 1.0  # utils per second of walking

SecondsSinceMidnight = int
GTFSID = Hashable


class AbstractNode(ABC):
    """Abstract base node for representing the state of an individual human
    traveler at some point at a specific point in time. For example an
    implementing subclass "AtStop" might represent a traveler who is waiting
    for a bus at a specific stop. Another subclass "OnBike" might represent a
    traveler who is riding a bike.
    """

    def __init__(self, context: Dict[str, Any]):
        self.context = context

    @property
    @abstractmethod
    def outgoing(self) -> List["Edge"]:
        pass

    @property
    @abstractmethod
    def incoming(self) -> List["Edge"]:
        pass

    # class must be usable as a dictionary key
    @abstractmethod
    def as_tuple(self) -> tuple[Hashable]:
        pass

    def _identity_tuple(self):
        return (self.__class__.__name__, self.as_tuple())

    def __hash__(self):
        return hash(self._identity_tuple())

    def __eq__(self, other):
        return self._identity_tuple() == other._identity_tuple()

    @abstractmethod
    def __repr__(self):
        pass


class Edge(NamedTuple):
    node: AbstractNode
    weight: float


class TimetableEvent(NamedTuple):
    """Represents an event in a GTFS timetable."""

    pattern_id: int
    service_id: int
    row: int  # the index of the trip
    col: int  # the index of the stop
    datetime: pd.Timestamp  # datetime of the event


class Timetable:
    """Represents a GTFS timetable."""

    def __init__(
        self,
        trip_ids: Sequence[GTFSID],
        stop_ids: Sequence[GTFSID],
        arrival_times: NDArray[np.uint32],
        departure_times: NDArray[np.uint32],
    ):
        self.trip_ids = trip_ids
        self.stop_ids = stop_ids
        self.arrival_times = arrival_times
        self.departure_times = departure_times

    def _lookup_departure(
        self, stop_idx: int, query_time: int
    ) -> Optional[Tuple[int, SecondsSinceMidnight]]:
        # if the stop_idx is the last stop, then there is no departure
        if stop_idx == len(self.stop_ids) - 1:
            return None

        # get the index of the first trip that is >= the time
        trip_idx = int(
            np.searchsorted(
                self.departure_times[:, stop_idx],
                query_time,
                side="left",
            )
        )

        # if the time is after the last departure, then there is no
        # departure
        if trip_idx == len(self.departure_times):
            return None

        event_time = self.departure_times[trip_idx, stop_idx]
        return trip_idx, event_time

    def _lookup_arrival(
        self, stop_idx: int, query_time: int
    ) -> Optional[Tuple[int, SecondsSinceMidnight]]:
        # if the stop_idx is the first stop, then there is no arrival
        if stop_idx == 0:
            return None

        # get the index of the first trip that is <= the time
        trip_idx = (
            int(
                np.searchsorted(
                    self.departure_times[:, stop_idx],
                    query_time,
                    side="right",
                )
            )
            - 1
        )

        # if the time is before the first departure, then there is no
        # arrival
        if trip_idx == -1:
            return None

        event_time = self.arrival_times[trip_idx, stop_idx]
        return trip_idx, event_time

    def find_stop_events(
        self,
        stop_id: Hashable,
        query_time: int,
        find_departure: bool = True,
    ) -> List[Tuple[int, int, SecondsSinceMidnight]]:
        """Looks up the next event (arrival or departure) for a given stop at
        a given time. Because a trip may visit a stop multiple times, an array
        of events is returned.

        Args:
            stop_id: ID of the stop to lookup.
            query_time: Time to lookup, in seconds since midnight.
            find_departure: If True, lookup the next departure event. If False,
                lookup the previous arrival event.

        Returns:
            A tuple of table row, column, and time for each event.
        """

        events = []

        # Get the indices of the stops in the timetable that match the stop_id.
        # It is possible that a stop_id appears multiple times in the
        # timetable.
        stop_idxs = np.flatnonzero(self.stop_ids == stop_id)

        for stop_idx in stop_idxs:
            if find_departure:
                event = self._lookup_departure(stop_idx, query_time)
            else:
                event = self._lookup_arrival(stop_idx, query_time)

            if event is not None:
                trip_idx, event_time = event
                events.append((trip_idx, stop_idx, event_time))

        return events


def expand_pairs(lst):
    """Expands a list of pairs.

    Args:
        lst: List of pairs, in the form [(key, list), ...])]

    Returns:
        List of pairs, in the form [(key, item), ...]
    """

    for key, items in lst:
        for item in items:
            yield (key, item)


def parse_gtfs_time(time_str: str) -> int:
    """Parses a GTFS time string into a pandas Timestamp.

    Args:
        time_str: GTFS time string in the format HH:MM:SS.

    Returns:
        Seconds since midnight.
    """

    h, m, s = time_str.split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


def seconds_since_midnight(time: pd.Timestamp) -> int:
    """Converts a pandas Timestamp into seconds since midnight.

    Args:
        time: pandas Timestamp.

    Returns:
        Seconds since midnight.
    """

    return time.hour * 3600 + time.minute * 60 + time.second


class GTFS:
    """Represents a GTFS feed."""

    def __init__(self, fname: str):
        """Reads in GTFS data from a zipfile."""

        with open(fname, "rb") as f:
            zf = ZipFile(f)

            if not self._is_gtfs_zip(zf):
                raise ValueError("Error: zipfile is not a valid GTFS zip file")

            self.zf = zf
            self.service_dates = self._expand_service_dates(zf)
            self.trips = self._read_trips(zf)
            self.stops = self._read_stops(zf)
            self.stop_times = self._read_stop_times(zf)

            self._augment_with_stop_patterns()
            self.timetables = self._get_timetables()

            self.day_start = self.stop_times["arrival_time"].min()
            self.day_end = self.stop_times["departure_time"].max()

    @classmethod
    def _is_gtfs_zip(cls, zf):
        """Check that the zipfile contains the required GTFS files.

        Args:
            zf: ZipFile object

        Returns:
            True if the zipfile contains the required GTFS files, False
            otherwise.
        """

        files = zf.namelist()

        required_files = [
            "stops.txt",
            "routes.txt",
            "trips.txt",
            "stop_times.txt",
        ]
        for file in required_files:
            if file not in files:
                return False

        return True

    def _augment_with_stop_patterns(self):
        # get trip_id -> stop pattern
        trip_stop_patterns = (
            self.stop_times.sort_values("stop_sequence")
            .groupby(["trip_id"])
            .agg({"stop_id": tuple})
            .to_dict(orient="index")
        )

        # reverse to get stop_pattern -> trip_ids
        stop_pattern_trips = defaultdict(set)
        for trip_id, stop_pattern in trip_stop_patterns.items():
            stop_pattern_trips[stop_pattern["stop_id"]].add(trip_id)
        stop_pattern_trips = dict(stop_pattern_trips)

        # create stop_pattern_id -> ordered list of stops
        self.stop_patterns = dict(
            zip(range(len(stop_pattern_trips)), stop_pattern_trips.keys())
        )

        # create a dataframe of (stop_pattern_id, trip_id)
        stop_pattern_id_trips = expand_pairs(
            zip(range(len(stop_pattern_trips)), stop_pattern_trips.values())
        )
        stop_pattern_id_trips_df = pd.DataFrame(
            stop_pattern_id_trips, columns=["stop_pattern_id", "trip_id"]
        )

        # augment the stop_times table with stop_pattern_id and service_id
        self.stop_times = self.stop_times.merge(
            stop_pattern_id_trips_df, on="trip_id"
        )
        self.stop_times = self.stop_times.merge(
            self.trips[["trip_id", "service_id"]], on="trip_id"
        )

        # create dict of stop_id -> stop_pattern_ids
        stop_pattern_ids = defaultdict(set)
        for stop_pattern_id, stop_pattern in self.stop_patterns.items():
            for stop_id in stop_pattern:
                stop_pattern_ids[stop_id].add(stop_pattern_id)
        self.stop_pattern_ids = dict(stop_pattern_ids)

    def _get_timetables(self) -> Dict[Tuple[int, Hashable], Timetable]:
        timetables = {}

        def grouper_func(group):
            """This converts a group of stop_times that share a stop_pattern_id
            and service_id into a timetable.

            The timetable is a dataframe with three columns: trip_id,
            arrival_time, and departure_time. The arrival_time and
            departure_time columns are lists of times, one for each stop in the
            stop_pattern. The timetable is sorted by the first arrival time in
            the list."""

            trips = (
                group.sort_values("stop_sequence")
                .groupby("trip_id")
                .agg(list)[["arrival_time", "departure_time"]]
                .sort_values(
                    "arrival_time", key=lambda x: x.map(lambda x: x[0])
                )
            )

            return trips

        timetable_df = (
            self.stop_times.groupby(["stop_pattern_id", "service_id"])
            .apply(grouper_func)
            .reset_index()
        )

        for (stop_pattern_id, service_id), timetable in timetable_df.groupby(
            [
                "stop_pattern_id",
                "service_id",
            ]
        ):
            key = (stop_pattern_id, service_id)

            stop_ids = self.stop_patterns[stop_pattern_id]
            trip_ids = timetable["trip_id"].tolist()
            arrival_times = np.array(timetable["arrival_time"].values.tolist())
            departure_times = np.array(
                timetable["departure_time"].values.tolist()
            )

            timetable = Timetable(
                trip_ids, stop_ids, arrival_times, departure_times
            )
            timetables[key] = timetable

        return timetables

    def _expand_service_dates(self, zf):
        """Expands the calendar.txt and calendar_dates.txt files into a
        dictionary of dates to service_ids.

        Args:
            zf: ZipFile object containing the GTFS data.

        Returns:
            Dictionary of dates to service_ids.
        """

        expanded_cal = defaultdict(set)

        # for each row in calendar, create a list of dates that are in the
        # service
        if "calendar.txt" in zf.namelist():
            with zf.open("calendar.txt") as f:
                calendar = pd.read_csv(
                    f, parse_dates=["start_date", "end_date"]
                )

            weekdays = [
                "monday",
                "tuesday",
                "wednesday",
                "thursday",
                "friday",
                "saturday",
                "sunday",
            ]

            def process_calendar_row(row):
                for date in pd.date_range(row.start_date, row.end_date):
                    if row[weekdays[date.dayofweek]] == 1:
                        expanded_cal[date.date()].add(row.service_id)

            calendar.apply(process_calendar_row, axis=1)

        # for each row in calendar_dates, add or remove the service_id from
        # the list
        if "calendar_dates.txt" in zf.namelist():
            with zf.open("calendar_dates.txt") as f:
                calendar_dates = pd.read_csv(f, parse_dates=["date"])

            def process_calendar_dates_row(row):
                add_service = 1
                remove_service = 2

                if row.exception_type == add_service:
                    expanded_cal[row.date.date()].add(row.service_id)
                elif row.exception_type == remove_service:
                    if row.service_id in expanded_cal[row.date.date()]:
                        expanded_cal[row.date.date()].remove(row.service_id)

            calendar_dates.apply(process_calendar_dates_row, axis=1)

        return dict(expanded_cal)

    def _read_trips(self, zf):
        if "trips.txt" not in zf.namelist():
            raise FileNotFoundError("trips.txt not found in GTFS zip file")

        with zf.open("trips.txt") as f:
            return pd.read_csv(f)

    def _read_stops(self, zf):
        if "stops.txt" not in zf.namelist():
            raise FileNotFoundError("stops.txt not found in GTFS zip file")

        with zf.open("stops.txt") as f:
            return pd.read_csv(f)

    def _read_stop_times(self, zf):
        if "stop_times.txt" not in zf.namelist():
            raise FileNotFoundError(
                "stop_times.txt not found in GTFS zip file"
            )

        with zf.open("stop_times.txt") as f:
            return pd.read_csv(
                f,
                converters={
                    "arrival_time": parse_gtfs_time,
                    "departure_time": parse_gtfs_time,
                },
            )

    def stops_with_name(self, name):
        """Returns a list of stops that match the given name.

        Args:
            name: Name to match.

        Returns:
            List of stops that match the given name.
        """

        return self.stops[self.stops.stop_name.str.contains(name)]

    def get_service_ids(self, date):
        """Returns a list of service_ids that are active on the given date.

        Args:
            date: Date to get service_ids for.

        Returns:
            List of service_ids that are active on the given date.
        """

        return self.service_dates[date]

    def find_stop_events(
        self,
        stop_id: Hashable,
        query_datetime: pd.Timestamp,
        find_departures: bool = True,
    ) -> List[TimetableEvent]:
        """Returns a list of timetable events (either arrivals or departures)
        at the given stop corresponding to the given time. If after is True,
        only returns events at or after; if after is False, only returns events
        at or before.

        Args:
            stop_id: Stop to get events for.
            query_datetime: Date and time to get events for.
            find_departures: If True, only return events (i.e., departures) at
                or after the given time. If False, only return events
                (i.e., arrivals) at or before the given time.

        Returns:
            List of events at the given stop at or after the given time.
        """

        secs_in_day = 24 * 60 * 60

        events = []

        query_time = seconds_since_midnight(query_datetime)
        query_date = query_datetime.date()

        # The query_time will always be between 0 and 24 hours, but gtfs
        # times can be greater than 24 hours to represent schedule events
        # in the early morning of the next day. If the query time is in the
        # early morning, we need to query the previous day's schedule.
        if query_time + secs_in_day < self.day_end:
            query_time += secs_in_day
            query_date -= timedelta(days=1)

        # TODO: If the query time is at the edges of the schedule, query
        # the previous/next day's schedule as well.

        # for each stop pattern that visits at this stop
        for stop_pattern_id in self.stop_pattern_ids[stop_id]:
            # for each service that is active on this date
            for service_id in self.get_service_ids(query_date):
                key = (stop_pattern_id, service_id)

                if key not in self.timetables:
                    continue

                timetable = self.timetables[key]

                for i, j, event_time in timetable.find_stop_events(
                    stop_id, query_time, find_departures
                ):
                    event_datetime = pd.Timestamp(
                        query_date, tz=query_datetime.tz
                    ) + pd.Timedelta(event_time, unit="s")

                    event = TimetableEvent(
                        stop_pattern_id, service_id, i, j, event_datetime
                    )
                    events.append(event)

        return events


class AtStopNode(AbstractNode):
    """Represents a passenger not aboard a transit vehicle at a named transit
    stop available to board a transit vehicle.

    Available outgoing transitions:
        - DepartureNode: Board a transit vehicle.
    """

    def __init__(self, context, stop_id, datetime):
        super().__init__(context)

        if "gtfs_feed" not in context:
            raise ValueError("Context must contain gtfs_feed")

        self.feed = context["gtfs_feed"]
        self.stop_id = stop_id
        self.datetime = datetime

    @property
    def outgoing(self):
        outgoing_edges = []

        for event in self.feed.find_stop_events(
            self.stop_id, self.datetime, find_departures=True
        ):
            node = DepartureNode(
                self.context,
                event.pattern_id,
                event.service_id,
                event.row,
                event.col,
                event.datetime,
            )
            weight = float((event.datetime - self.datetime).seconds)
            edge = Edge(node, weight)
            outgoing_edges.append(edge)

        return outgoing_edges

    @property
    def incoming(self):
        return NotImplementedError

    def as_tuple(self):
        return (self.stop_id, self.datetime)

    def __repr__(self):
        return f"AtStopNode(stop_id:{self.stop_id}, datetime:{self.datetime})"


class DepartureNode(AbstractNode):
    """Represents a passenger aboard a transit vehicle at the moment of
    departure from a named transit stop.

    Available outgoing transitions:
        - ArrivalNode: Travel to and then arrive at the
        vehicle's next scheduled stop
    """

    def __init__(self, context, pattern_id, service_id, row, col, datetime):
        super().__init__(context)

        if "gtfs_feed" not in context:
            raise ValueError("Context must contain gtfs_feed")

        self.feed = context["gtfs_feed"]
        self.pattern_id = pattern_id
        self.service_id = service_id
        self.row = row
        self.col = col
        self.datetime = datetime

    @property
    def outgoing(self):
        timetable = self.feed.timetables[(self.pattern_id, self.service_id)]

        departure_time = timetable.departure_times[self.row, self.col]
        next_arrival_time = timetable.arrival_times[self.row, self.col + 1]
        segment_duration = next_arrival_time - departure_time

        arrival_datetime = self.datetime + pd.Timedelta(
            segment_duration, unit="s"
        )

        node = ArrivalNode(
            self.context,
            self.pattern_id,
            self.service_id,
            self.row,
            self.col + 1,
            arrival_datetime,
        )
        weight = float(segment_duration)
        edge = Edge(node, weight)

        return [edge]

    @property
    def incoming(self):
        raise NotImplementedError

    def as_tuple(self):
        return (
            self.pattern_id,
            self.service_id,
            self.row,
            self.col,
            self.datetime,
        )

    def __repr__(self):
        return (
            f"DepartureNode(pattern_id:{self.pattern_id}, "
            f"service_id:{self.service_id}, "
            f"row:{self.row}, col:{self.col}, datetime:{self.datetime})"
        )


class ArrivalNode(AbstractNode):
    """Represents a passenger aboard a transit vehicle at the moment of arrival
    at a named transit stop.

    Available outgoing transitions:
        - AtStopNode: Alight from the transit vehicle.
        - DepartureNode: Stay in the vehicle until the moment it departs
        for the next schedules stop.
    """

    def __init__(self, context, pattern_id, service_id, row, col, datetime):
        super().__init__(context)

        if "gtfs_feed" not in context:
            raise ValueError("Context must contain gtfs_feed")

        self.feed = context["gtfs_feed"]
        self.pattern_id = pattern_id
        self.service_id = service_id
        self.row = row
        self.col = col
        self.datetime = datetime

    @property
    def type(self):
        return "arrival"

    @property
    def outgoing(self):
        outgoing_edges = []

        timetable = self.feed.timetables[(self.pattern_id, self.service_id)]

        # make an edge for waiting until departure
        arrival_time = timetable.arrival_times[self.row, self.col]
        departure_time = timetable.departure_times[self.row, self.col]
        wait_duration = departure_time - arrival_time
        node = DepartureNode(
            self.context,
            self.pattern_id,
            self.service_id,
            self.row,
            self.col,
            self.datetime + pd.Timedelta(wait_duration, unit="s"),
        )
        departure_edge = Edge(node, float(wait_duration))
        outgoing_edges.append(departure_edge)

        # make an edge for alighting to the stop
        stop_id = timetable.stop_ids[self.col]
        node = AtStopNode(self.context, stop_id, self.datetime)
        alighting_edge = Edge(node, ALIGHTING_WEIGHT)
        outgoing_edges.append(alighting_edge)

        return outgoing_edges

    @property
    def incoming(self):
        raise NotImplementedError

    def as_tuple(self):
        return (
            self.pattern_id,
            self.service_id,
            self.row,
            self.col,
            self.datetime,
        )

    def __repr__(self):
        return (
            f"ArrivalNode(pattern_id:{self.pattern_id}, "
            f"service_id:{self.service_id}, "
            f"row:{self.row}, col:{self.col}, datetime:{self.datetime})"
        )


def get_stop_node(feed, stop_name, datetime):
    stop_id = feed.stops[feed.stops.stop_name == stop_name]["stop_id"].iloc[0]

    return AtStopNode({"gtfs_feed": feed}, stop_id, datetime)


class Way(NamedTuple):
    nds: List[int]
    tags: Dict[str, str]


def read_osm(
    filename: str,
) -> Tuple[Dict[int, Tuple[float, float]], Dict[int, Way]]:
    """
    Read an OSM file and return a dictionary of nodes and ways.

    Args:
        filename (str): The path to the OSM file.

    Returns:
        nodes (dict): A dictionary of nodes, keyed by node ID.
        ways (dict): A dictionary of ways, keyed by way ID.
    """
    nodes = {}
    ways = {}

    way_nds = set()

    class HighwayHandler(osmium.SimpleHandler):
        def way(self, w):
            if "highway" not in w.tags:
                return

            nds = [n.ref for n in w.nodes]
            way_nds.update(nds)
            tags = dict(w.tags)

            ways[w.id] = Way(nds, tags)

    h = HighwayHandler()
    h.apply_file(filename)

    class NodeHandler(osmium.SimpleHandler):
        def node(self, n):
            # only keep nodes that are part of a highway
            if n.id in way_nds:
                nodes[n.id] = (n.location.lon, n.location.lat)

    n = NodeHandler()
    n.apply_file(filename)

    return nodes, ways


class ElevationRaster:
    def __init__(self, filename):
        self.filename = filename
        self._file = None
        self._elevdata = None

    def __enter__(self):
        self._file = rasterio.open(self.filename)
        self._elevdata = self._file.read(1)  # read the first band
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self._file.close()
        self._elevdata = None

    def _interpolate(self, A: NDArray, i: int, j: int) -> float:
        """Bilinear interpolation.

        Args:
            A (np.ndarray): A 2x2 array of values.
            i (float): The fractional row.
            j (float): The fractional column.

        Returns:
            float: The interpolated value."""

        baseline = A[0] * (1 - i) + A[1] * i
        return baseline[0] * (1 - j) + baseline[1] * j

    def get_elevation(self, lon: float, lat: float) -> float:
        """Get the elevation of a point.

        Args:
            lon (float): The longitude of the point.
            lat (float): The latitude of the point.

        Returns:
            float: The elevation of the point.
        """

        # make sure this is called in the context of a with statement
        if self._file is None:
            raise RuntimeError(
                "ElevationRaster must be used in a with statement."
            )

        # get the elevation at the node
        # apply the transform to get the fractional row and column
        row, col = self._file.index(lon, lat, op=lambda x: x)

        if (
            row < 0
            or col < 0
            or row >= self._file.height
            or col >= self._file.width
        ):
            return np.nan

        # read a small window around the point
        row_floor = math.floor(row)
        col_floor = math.floor(col)
        elevs_window = self._elevdata[
            row_floor : row_floor + 2, col_floor : col_floor + 2
        ]

        # interpolate the elevation
        row_frac = row - row_floor
        col_frac = col - col_floor
        elev = self._interpolate(elevs_window, row_frac, col_frac)

        return elev


def get_elevations_for_nodes(
    elev_raster_fn: str, nodes: Dict[int, Tuple[float, float]]
) -> Dict[int, float]:
    """Get the elevation of each node in a list of nodes.

    Args:
        elev_raster_fn (str): Path to the elevation raster file.
        nodes (Dict[int, Tuple(float, float)]): A dictionary of node IDs and
            their (lon, lat) coordinates.

    Returns:
        Dict[int, float]: A dictionary of node IDs and their elevations.
    """

    node_elevs: Dict[int, float] = {}

    with ElevationRaster(elev_raster_fn) as elev_raster:
        for node, (lon, lat) in nodes.items():
            node_elevs[node] = elev_raster.get_elevation(lon, lat)

    return node_elevs


def get_graph_nodes(ways: Dict[int, Way]) -> Set[int]:
    """Get all the graph nodes from the ways. The graph nodes are the nodes
    that are used more than once, or they are the start or end node of a street.

    Args:
        ways (Dict): A dictionary of ways.

    Returns:
        Set: A set of graph nodes.
    """

    graph_nodes = set()

    node_count: Counter = Counter()
    for way in ways.values():
        # if a way has 0 or 1 nodes, it's not a street
        if len(way.nds) < 2:
            continue

        # add the start and end nodes
        graph_nodes.add(way.nds[0])
        graph_nodes.add(way.nds[-1])

        # count the number of times a node appears
        node_count.update(way.nds)

    # get all nodes that appear more than once
    intersection_nodes = set(
        node for node, count in node_count.items() if count > 1
    )
    graph_nodes = graph_nodes.union(intersection_nodes)

    return graph_nodes


def get_node_references(
    ways: Dict[int, Way]
) -> Dict[int, Set[Tuple[int, int]]]:
    """Get a dictionary of node references.

    Args:
        ways (Dict): A dictionary with the format {way_id: Way}.

    Returns:
        Dict: A dictionary of instances in which a node was used in a way.
            The format is {node_id: {(way_id, node_index)}}"""

    nd_refs: Dict[int, Set[Tuple[int, int]]] = defaultdict(set)

    for way_id, way in ways.items():
        for i, nd in enumerate(way.nds):
            nd_refs[nd].add((way_id, i))

    return nd_refs


def geodesic_distance_meters(geo_pt: Point, geo_pt2: Point) -> float:
    """Compute the geodesic distance in meters between two points on the earth's surface."""

    # TODO: this can be vectorized if it ever becomes a bottleneck

    # convert decimal degrees to radians
    lon1, lat1, lon2, lat2 = map(
        np.radians, [geo_pt.x, geo_pt.y, geo_pt2.x, geo_pt2.y]
    )

    # haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = (
        np.sin(dlat / 2) ** 2
        + np.cos(lat1) * np.cos(lat2) * np.sin(dlon / 2) ** 2
    )
    c = 2 * np.arcsin(np.sqrt(a))

    earth_radius_km = 6371
    km = earth_radius_km * c
    return km * 1000


class OnEarthSurfaceNode(AbstractNode):
    """Represents a passenger standing on the surface of the earth at a
    particular location and time."""

    def __init__(self, context, lon: float, lat: float, time: pd.Timestamp):
        """Initialize the node.

        Args:
            lon (float): The longitude of the passenger.
            lat (float): The latitude of the passenger.
            time (pd.Timestamp): The time of the passenger.
        """

        if lon < -180 or lon > 180:
            raise ValueError(f"Invalid longitude: {lon}")
        if lat < -90 or lat > 90:
            raise ValueError(f"Invalid latitude: {lat}")

        super().__init__(context)

        self.osm: ElevationAwareStreetDataset | None = self.context.get("osm")
        self.lon = lon
        self.lat = lat
        self.time = time

    @property
    def outgoing(self) -> List[Edge]:
        """Get the outgoing edges from this node.

        Returns:
            List[Edge]: A list of outgoing edges.
        """

        edges: List[Edge] = []

        if self.osm is not None:
            segment = self.osm.get_nearest_segment(self.lon, self.lat)
            if segment is not None:
                way_id, segment_ix, linear_ref = segment

                closest_way_pt = self.osm.get_way_point(
                    way_id, segment_ix, linear_ref
                )
                distance = geodesic_distance_meters(
                    Point(self.lon, self.lat), closest_way_pt
                )

                dt = distance / WALKING_SPEED
                weight = dt * WALKING_RELUCTANCE

                node = MidstreetNode(
                    self.context,
                    way_id,
                    segment_ix,
                    linear_ref,
                    self.time + pd.Timedelta(seconds=dt),
                )

                edges.append(Edge(node, weight))

        return edges

    @property
    def incoming(self) -> List[Edge]:
        """Get the incoming edges to this node.

        Returns:
            List[Edge]: A list of incoming edges.
        """

        raise NotImplementedError()

    def as_tuple(self):
        return (self.lon, self.lat, self.time)

    def __repr__(self) -> str:
        return f"OnEarthSurfaceNode({self.lon}, {self.lat}, {self.time})"


class MidstreetNode(AbstractNode):
    """Represents a passenger standing on a street segment."""

    def __init__(
        self,
        context: dict[str, Any],
        way_id: int,
        segment_ix: int,
        linear_ref: float,
        time: pd.Timestamp,
    ):
        """Initialize the node.

        Args:
            way_id (int): The ID of the way.
            segment_ix (int): The index of the segment.
            linear_ref (float): The linear reference along the segment.
            time (pd.Timestamp): The time of the passenger.
        """

        super().__init__(context)

        if "osm" not in self.context:
            raise ValueError("The context must contain an OSM object.")

        self.osm: ElevationAwareStreetDataset = self.context["osm"]
        self.way_id = way_id
        self.segment_ix = segment_ix
        self.linear_ref = linear_ref
        self.time = time

    @property
    def outgoing(self) -> List[Edge]:
        edges: List[Edge] = []

        current_point = self.osm.get_way_point(
            self.way_id, self.segment_ix, self.linear_ref
        )

        next_segment = self.osm.next_vertex_node(self.segment_ix + 1)

        return edges

    @property
    def incoming(self) -> List[Edge]:
        raise NotImplementedError()

    def __repr__(self):
        return (
            f"OnStreetNode({self.way_id}, {self.segment_ix}, "
            f"{self.linear_ref}, {self.time})"
        )

    def as_tuple(self):
        return (self.way_id, self.segment_ix, self.linear_ref, self.time)


def cons(ary: Iterable) -> Iterator[Tuple[Any, Any]]:
    """Return a generator of consecutive pairs from the input iterable."""
    it = iter(ary)
    prev = next(it)
    for item in it:
        yield prev, item
        prev = item


class ElevationAwareStreetDataset:
    """Holds all the context needed to generate adjacent edges in a
    street network. The context includes street and elevation data."""

    def __init__(self, osm_fn: str, elevation_raster_fn: str):
        """Initialize the dataset.

        Args:
            osm_fn (str): Path to the OSM file.
            elevation_raster_fn (str): Path to the elevation raster file.
        """

        self.osm_fn = osm_fn
        self.elevation_raster_fn = elevation_raster_fn

        print("Reading OSM file...", end="", flush=True)
        self.nodes, self.ways = read_osm(osm_fn)
        print("done")

        print("Indexing ways...", end="", flush=True)
        self.node_refs = get_node_references(self.ways)
        print("done")

        print("Getting node elevations...", end="", flush=True)
        self.node_elevs = get_elevations_for_nodes(
            self.elevation_raster_fn, self.nodes
        )
        print("done")

        print("Creating spatial index...", end="", flush=True)
        (
            self.segment_way_refs,
            self.segments,
        ) = self._generate_way_segments()
        self.segment_spatial_index = STRtree(self.segments)
        print("done")

    def _generate_way_segments(
        self,
    ) -> Tuple[List[Tuple[int, int]], List[LineString]]:
        """Generate all the segments in the ways.

        This is a part of generating the index that is used to find the closest
        way to a particular location."""

        segment_way_refs = []
        segments = []
        for way_id, way in self.ways.items():
            for segment_ix, (nd1, nd2) in enumerate(cons(way.nds)):
                pt1 = self.nodes[nd1]
                pt2 = self.nodes[nd2]

                segment = LineString([pt1, pt2])

                segment_way_refs.append((way_id, segment_ix))
                segments.append(segment)

        return segment_way_refs, segments

    def get_nearest_segment(
        self, lon: float, lat: float, search_radius: float = 0.001
    ) -> Tuple[int, int, float] | None:
        """Get the nearest segment to a particular location.

        Args:
            lon (float): The longitude of the location.
            lat (float): The latitude of the location.
            search_radius (float, optional): The search radius in degrees.
                Defaults to 0.001, which is about 100 meters.

        Returns:
            Tuple[int, int, float]: A tuple containing the way ID, the index of
                the segment in the way, and the distance along the segment that
                is closest to the location.
        """

        query_pt = Point(lon, lat)
        search_area = query_pt.buffer(search_radius)
        nearby_segment_ids = self.segment_spatial_index.query(search_area)

        if len(nearby_segment_ids) == 0:
            return None

        i = np.argmin(
            [
                self.segments[nearby_segment_id].distance(query_pt)
                for nearby_segment_id in nearby_segment_ids
            ]
        )
        nearest_segment_id = nearby_segment_ids[i]

        way_id, segment_index = self.segment_way_refs[nearest_segment_id]
        distance_along_segment = self.segments[
            nearest_segment_id
        ].line_locate_point(query_pt, normalized=True)

        return way_id, segment_index, distance_along_segment

    def get_way_point(
        self, way_id: int, segment_ix: int, linear_ref: float
    ) -> Point:
        """Get the point along a way at a particular linear reference.

        Args:
            way_id (int): The ID of the way.
            segment_ix (int): The index of the segment in the way.
            linear_ref (float): The linear reference along the segment.

        Returns:
            Point: The point along the way at the specified linear reference.
        """

        way = self.ways[way_id]

        if segment_ix < 0 or segment_ix >= len(way.nds) - 1:
            raise ValueError(
                f"Segment index {segment_ix} is out of range for way {way_id}"
            )

        nd1 = way.nds[segment_ix]
        nd2 = way.nds[segment_ix + 1]

        pt1 = self.nodes[nd1]
        pt2 = self.nodes[nd2]

        ls = LineString([pt1, pt2])

        return ls.interpolate(linear_ref, normalized=True)

    def next_vertex_node(
        self, way_id: int, nd_index: int, search_forward=True
    ) -> int:
        """Get the position in the way of the next vertex node. A vertex node
        is a node that is either referenced by a way more than once, or is the
        first or last node. Essentially, it is node at which a turn can be
        made. The search is inclusive of the node at the specified index.

        Args:
            way_id (int): The ID of the way.
            nd_index (int): The index of the node in the way.
            search_forward (bool, optional): If true, finds the next vertex
                node. If false, finds the previous vertex node. Defaults to
                True.

        Returns:
            int: The index of the next vertex node in the way, inclusive
                of the node at the specified index."""

        way = self.ways[way_id]

        if nd_index < 0 or nd_index > len(way.nds) - 1:
            raise ValueError(
                f"Node index {nd_index} is out of range for way {way_id}"
            )

        if search_forward:
            step = 1
            end = len(way.nds) - 1
        else:
            step = -1
            end = 0

        for i in range(nd_index, end, step):
            nd = way.nds[i]
            if len(self.node_refs[nd]) > 1:
                return i

        # if we get here, we've reached the end of the way
        return end


def main():
    load_dotenv()

    # get environment variables

    data_dir = os.getenv("TRANSIT_DATA_DIR")

    if data_dir is None:
        print("Error: TRANSIT_DATA_DIR environment variable not set")
        exit(1)

    street_data_dir = os.getenv("STREET_DATA_DIR")

    if street_data_dir is None:
        print("Error: STREET_DATA_DIR environment variable not set")
        exit(1)

    # read in every file in the transit data directory
    for filename in os.listdir(data_dir):
        if filename.endswith(".zip"):
            fn = os.path.join(data_dir, filename)
            zf = ZipFile(fn, "r")

            gtfs = GTFS(zf)
            print(gtfs.calendar)

    # read in every file in the street data directory


if __name__ == "__main__":
    main()
