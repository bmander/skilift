"""SkiLift is a minimal bicycle+transit journey planner."""

import datetime
import math
import os
from abc import ABC, abstractmethod
from collections import defaultdict
from datetime import timedelta
from types import TracebackType
from typing import (
    Any,
    Generator,
    Hashable,
    Iterable,
    Iterator,
    NamedTuple,
    Self,
    Sequence,
    Type,
    TypeVar,
)
from zipfile import ZipFile

import numpy as np
import osmium
import pandas as pd
import rasterio
from dotenv import load_dotenv
from numpy.typing import NDArray
from shapely import geometry
from shapely.strtree import STRtree

from utils import cons, expand_pairs

# constants
ALIGHTING_WEIGHT = 60.0  # utils; where 1 util ~= 1 second of travel time
WALKING_SPEED = 1.2  # meters per second
WALKING_RELUCTANCE = 1.0  # utils per second of walking

# types
SecondsSinceMidnight = int
GTFSID = Hashable
ArrayIndex = int  # integer between 0 and length of the array-1
SegmentIndex = int  # integer between 0 and length of the array-2
TimetableId = tuple[ArrayIndex, GTFSID]  # stoppatternid, serviceid
StopPattern = tuple[GTFSID, ...]
WayId = int
NodeId = int


class NodeRef(NamedTuple):
    way_id: WayId
    node_index: ArrayIndex


class SegmentRef(NamedTuple):
    """A segment the space between two nodes in a way. It is defined by the
    index of the first node."""

    way_id: WayId
    segment_index: SegmentIndex


class MidSegmentRef:
    segment: SegmentRef
    offset_int: int  # normalized offset between 0 and 1, times 100000

    def __init__(self, segment: SegmentRef, offset: float):
        self.segment = segment
        self.offset_int = int(offset * 100000)

    @property
    def offset(self) -> float:
        return self.offset_int / 100000

    # same style as a NamedTuple subclass
    def __repr__(self) -> str:
        return f"MidSegmentRef({self.segment}, {self.offset})"

    def __hash__(self) -> int:
        return hash((self.segment, self.offset_int))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, MidSegmentRef):
            return False
        return (
            self.segment == other.segment
            and self.offset_int == other.offset_int
        )


class Segment(NamedTuple):
    ref: SegmentRef
    geometry: geometry.LineString


class EdgeProvider(ABC):
    """Abstract base class for providing edges for a given vertex."""

    @abstractmethod
    def outgoing(self, vertex: "AbstractVertex") -> list["Edge"]:
        pass

    @abstractmethod
    def incoming(self, vertex: "AbstractVertex") -> list["Edge"]:
        pass


class AbstractVertex(ABC):
    """Abstract base vertex for representing the state of an individual human
    traveler at some point at a specific point in time. For example an
    implementing subclass "AtStop" might represent a traveler who is waiting
    for a bus at a specific stop. Another subclass "OnBike" might represent a
    traveler who is riding a bike.
    """

    # class must be usable as a dictionary key
    @abstractmethod
    def as_tuple(self) -> tuple[Hashable, ...]:
        pass

    def _identity_tuple(self) -> tuple[str, tuple[Hashable, ...]]:
        return (self.__class__.__name__, self.as_tuple())

    def __hash__(self) -> int:
        return hash(self._identity_tuple())

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, AbstractVertex):
            return False
        return self._identity_tuple() == other._identity_tuple()

    @abstractmethod
    def __repr__(self) -> str:
        pass


class Edge(NamedTuple):
    vertex: AbstractVertex
    weight: float


class TimetableEvent(NamedTuple):
    """Represents an event in a GTFS timetable."""

    row: ArrayIndex  # the index of the trip
    col: ArrayIndex  # the index of the stop
    datetime: pd.Timestamp  # datetime of the event


class TransitEvent(NamedTuple):
    """Represents an event in a GTFS timetable."""

    pattern_id: int
    service_id: GTFSID
    row: ArrayIndex  # the index of the trip
    col: ArrayIndex  # the index of the stop
    datetime: pd.Timestamp  # datetime of the event


class Timetable:
    """Represents a GTFS timetable.

    A GTFS timetable is a 2d array of times, where each row represents a trip
    and each column represents a stop. The times are in seconds since midnight.
    All trips in the timetable must have the exact same sequence of stops, and
    no trip may overtake another trip.

    Colleting trips into a timetable is useful because in GTFS, all the trips
    in a route don't necessarily have the same stops, which means that to
    query adjacent stops you'd have to look at every trip. With all the trips
    in a route grouped into timetables, you only have to look at one relevant
    trip in each timetable to get adjacent stops."""

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
        self, stop_index: ArrayIndex, query_time: SecondsSinceMidnight
    ) -> tuple[int, SecondsSinceMidnight] | None:
        # if the stop_index is the last stop, then there is no departure
        if stop_index == len(self.stop_ids) - 1:
            return None

        # get the index of the first trip that is >= the time
        trip_index = int(
            np.searchsorted(
                self.departure_times[:, stop_index],
                query_time,
                side="left",
            )
        )

        # if the time is after the last departure, then there is no
        # departure
        if trip_index == len(self.departure_times):
            return None

        event_time = self.departure_times[trip_index, stop_index]
        return trip_index, event_time

    def _lookup_arrival(
        self, stop_index: ArrayIndex, query_time: SecondsSinceMidnight
    ) -> tuple[int, SecondsSinceMidnight] | None:
        # if the stop_index is the first stop, then there is no arrival
        if stop_index == 0:
            return None

        # get the index of the first trip that is <= the time
        trip_index = (
            int(
                np.searchsorted(
                    self.departure_times[:, stop_index],
                    query_time,
                    side="right",
                )
            )
            - 1
        )

        # if the time is before the first departure, then there is no
        # arrival
        if trip_index == -1:
            return None

        event_time = self.arrival_times[trip_index, stop_index]
        return trip_index, event_time

    def find_timetable_events(
        self,
        stop_id: GTFSID,
        query_time: SecondsSinceMidnight,
        find_departure: bool = True,
    ) -> list[TimetableEvent]:
        """Looks up the next event (arrival or departure) for a given stop at
        a given time. Because a trip may visit a stop multiple times, an array
        of events is returned.

        Args:
            stop_id: ID of the stop to lookup.
            query_time: Time at lookup.
            find_departure: If True, lookup the next departure event. If False,
                lookup the previous arrival event.

        Returns:
            A tuple of table row, column, and time for each event.
        """

        events: list[TimetableEvent] = []

        # Get the indices of the stops in the timetable that match the stop_id.
        # It is possible that a stop_id appears multiple times in the
        # timetable.
        stop_indices = np.flatnonzero(self.stop_ids == stop_id)

        for stop_index in stop_indices:
            if find_departure:
                event = self._lookup_departure(stop_index, query_time)
            else:
                event = self._lookup_arrival(stop_index, query_time)

            if event is not None:
                trip_index, event_time = event
                events.append(
                    TimetableEvent(trip_index, stop_index, event_time)
                )

        return events


def parse_gtfs_time(time_str: str) -> int:
    """Parses a GTFS time string into a pandas Timestamp.

    Args:
        time_str: GTFS time string in the format HH:MM:SS.

    Returns:
        Seconds since midnight.
    """

    h, m, s = time_str.split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


def seconds_since_midnight(time: pd.Timestamp) -> SecondsSinceMidnight:
    """Converts a pandas Timestamp into seconds since midnight.

    Args:
        time: pandas Timestamp.

    Returns:
        Seconds since midnight.
    """

    return int(time.hour * 3600 + time.minute * 60 + time.second)


class GTFSFeed:
    """Represents a GTFS feed."""

    def __init__(self, fname: str):
        """Reads in GTFS data from a zipfile."""

        with open(fname, "rb") as f:
            zf = ZipFile(f)

            if not self._is_gtfs_zip(zf):
                raise ValueError("Error: zipfile is not a valid GTFS zip file")

            self._zf = zf
            self._service_dates = self._expand_service_dates(zf)
            self._trips = self._read_trips(zf)
            self._stops = self._read_stops(zf)
            self._stop_times = self._read_stop_times(zf)

            self._augment_with_stop_patterns()
            self._timetables = self._get_timetables()

            self._day_start = self._stop_times["arrival_time"].min()
            self._day_end = self._stop_times["departure_time"].max()

    @classmethod
    def _is_gtfs_zip(cls, zf: ZipFile) -> bool:
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

    def _augment_with_stop_patterns(self) -> None:
        # get trip_id -> stop pattern
        trip_stop_patterns: dict[GTFSID, StopPattern] = (
            self._stop_times.sort_values("stop_sequence")
            .groupby(["trip_id"])
            .agg({"stop_id": tuple})
            .to_dict()["stop_id"]
        )  # dict of trip_id -> stop_pattern

        # reverse to get stop_pattern -> trip_ids
        stop_pattern_trips_collector: dict[
            StopPattern, set[GTFSID]
        ] = defaultdict(set)
        for trip_id, stop_pattern in trip_stop_patterns.items():
            stop_pattern_trips_collector[stop_pattern].add(trip_id)
        stop_pattern_trips: dict[StopPattern, set[GTFSID]] = dict(
            stop_pattern_trips_collector
        )

        # create stop_pattern_id -> ordered list of stops
        self.stop_patterns: dict[ArrayIndex, StopPattern] = dict(
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
        self.stop_times = self._stop_times.merge(
            stop_pattern_id_trips_df, on="trip_id"
        )
        self.stop_times = self.stop_times.merge(
            self._trips.reset_index()[["trip_id", "service_id"]], on="trip_id"
        )

        # create dict of stop_id -> stop_pattern_ids
        stop_pattern_ids: dict[GTFSID, set[ArrayIndex]] = defaultdict(set)
        for stop_pattern_id, stop_pattern in self.stop_patterns.items():
            for stop_id in stop_pattern:
                stop_pattern_ids[stop_id].add(stop_pattern_id)
        self.stop_pattern_ids: dict[GTFSID, set[ArrayIndex]] = dict(
            stop_pattern_ids
        )

    def _get_timetables(self) -> dict[TimetableId, Timetable]:
        timetables = {}

        def grouper_func(group: pd.DataFrame) -> pd.DataFrame:
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

    def _expand_service_dates(
        self, zf: ZipFile
    ) -> dict[datetime.date, set[GTFSID]]:
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

            def process_calendar_row(row: pd.Series) -> None:
                for date in pd.date_range(row.start_date, row.end_date):
                    if row[weekdays[date.dayofweek]] == 1:
                        expanded_cal[date.date()].add(row.service_id)

            calendar.apply(process_calendar_row, axis=1)

        # for each row in calendar_dates, add or remove the service_id from
        # the list
        if "calendar_dates.txt" in zf.namelist():
            with zf.open("calendar_dates.txt") as f:
                calendar_dates = pd.read_csv(f, parse_dates=["date"])

            def process_calendar_dates_row(row: pd.Series) -> None:
                add_service = 1
                remove_service = 2

                if row.exception_type == add_service:
                    expanded_cal[row.date.date()].add(row.service_id)
                elif row.exception_type == remove_service:
                    if row.service_id in expanded_cal[row.date.date()]:
                        expanded_cal[row.date.date()].remove(row.service_id)

            calendar_dates.apply(process_calendar_dates_row, axis=1)

        return dict(expanded_cal)

    def _read_trips(self, zf: ZipFile) -> pd.DataFrame:
        if "trips.txt" not in zf.namelist():
            raise FileNotFoundError("trips.txt not found in GTFS zip file")

        with zf.open("trips.txt") as f:
            return pd.read_csv(f, index_col="trip_id")

    def _read_stops(self, zf: ZipFile) -> pd.DataFrame:
        if "stops.txt" not in zf.namelist():
            raise FileNotFoundError("stops.txt not found in GTFS zip file")

        with zf.open("stops.txt") as f:
            return pd.read_csv(f, index_col="stop_id")

    def _read_stop_times(self, zf: ZipFile) -> pd.DataFrame:
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

    def get_stop_by_name(self, name: str) -> GTFSID:
        stop_id = self._stops[self._stops.stop_name == name].index[0]

        if (
            isinstance(stop_id, str)
            or isinstance(stop_id, int)
            or isinstance(stop_id, np.int64)
        ):
            return stop_id
        else:
            raise TypeError(
                f"stop_id has unsupported type '{repr(type(stop_id))}'"
            )

    def get_stop_locations(self) -> list[tuple[GTFSID, float, float]]:
        """Returns a list of tuples containing the stop_id, longitude, and
        latitude of each stop."""

        return [
            (stop_id, lon, lat)
            for stop_id, lon, lat in zip(
                self._stops.index,
                self._stops.stop_lon,
                self._stops.stop_lat,
            )
        ]

    def get_service_ids(self, date: datetime.date) -> set[GTFSID]:
        """Returns a list of service_ids that are active on the given date.

        Args:
            date: Date to get service_ids for.

        Returns:
            List of service_ids that are active on the given date.
        """

        return self._service_dates[date]

    def find_stop_events(
        self,
        stop_id: GTFSID,
        query_datetime: pd.Timestamp,
        find_departures: bool = True,
    ) -> list[TransitEvent]:
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
        if query_time + secs_in_day < self._day_end:
            query_time += secs_in_day
            query_date -= timedelta(days=1)

        # TODO: If the query time is at the edges of the schedule, query
        # the previous/next day's schedule as well.

        # for each stop pattern that visits at this stop
        for stop_pattern_id in self.stop_pattern_ids[stop_id]:
            # for each service that is active on this date
            for service_id in self.get_service_ids(query_date):
                key = (stop_pattern_id, service_id)

                if key not in self._timetables:
                    continue

                timetable = self._timetables[key]

                for (
                    trip_index,
                    stop_index,
                    event_time,
                ) in timetable.find_timetable_events(
                    stop_id, query_time, find_departures
                ):
                    event_datetime = pd.Timestamp(
                        query_date, tz=query_datetime.tz
                    ) + pd.Timedelta(event_time, unit="s")

                    event = TransitEvent(
                        stop_pattern_id,
                        service_id,
                        trip_index,
                        stop_index,
                        event_datetime,
                    )
                    events.append(event)

        return events

    def get_stop_point(self, stop_id: GTFSID) -> geometry.Point:
        """Returns the geographical point corresponding to the given stop_id."""

        stop_row = self._stops.loc[stop_id]

        return geometry.Point(stop_row.stop_lon, stop_row.stop_lat)


class TransitEdgeProvider(EdgeProvider):
    def __init__(self, feed: GTFSFeed):
        self.feed = feed

    def _at_stop_vertex_outgoing(self, vertex: "AtStopVertex") -> list[Edge]:
        outgoing_edges = []

        for event in self.feed.find_stop_events(
            vertex.stop_id, vertex.time, find_departures=True
        ):
            adj_vertex = DepartureVertex(
                event.pattern_id,
                event.service_id,
                event.row,
                event.col,
                event.datetime,
            )
            weight = float((event.datetime - adj_vertex.datetime).seconds)
            edge = Edge(adj_vertex, weight)
            outgoing_edges.append(edge)

        return outgoing_edges

    def _departure_vertex_outgoing(
        self, vertex: "DepartureVertex"
    ) -> list[Edge]:
        timetable = self.feed._timetables[
            (vertex.pattern_id, vertex.service_id)
        ]

        departure_time = timetable.departure_times[vertex.row, vertex.col]
        next_arrival_time = timetable.arrival_times[vertex.row, vertex.col + 1]
        segment_duration = next_arrival_time - departure_time

        arrival_datetime = vertex.datetime + pd.Timedelta(
            segment_duration, unit="s"
        )

        adj_vertex = ArrivalVertex(
            vertex.pattern_id,
            vertex.service_id,
            vertex.row,
            vertex.col + 1,
            arrival_datetime,
        )
        weight = float(segment_duration)
        edge = Edge(adj_vertex, weight)

        return [edge]

    def _arrival_vertex_outgoing(self, vertex: "ArrivalVertex") -> list[Edge]:
        outgoing_edges = []

        timetable = self.feed._timetables[
            (vertex.pattern_id, vertex.service_id)
        ]

        # make an edge for waiting until departure
        arrival_time = timetable.arrival_times[vertex.row, vertex.col]
        departure_time = timetable.departure_times[vertex.row, vertex.col]
        wait_duration = departure_time - arrival_time
        departure_vertex = DepartureVertex(
            vertex.pattern_id,
            vertex.service_id,
            vertex.row,
            vertex.col,
            vertex.datetime + pd.Timedelta(wait_duration, unit="s"),
        )
        departure_edge = Edge(departure_vertex, float(wait_duration))
        outgoing_edges.append(departure_edge)

        # make an edge for alighting to the stop
        stop_id = timetable.stop_ids[vertex.col]
        at_stop_vertex = AtStopVertex(stop_id, vertex.datetime)
        alighting_edge = Edge(at_stop_vertex, ALIGHTING_WEIGHT)
        outgoing_edges.append(alighting_edge)

        return outgoing_edges

    def outgoing(self, vertex: AbstractVertex) -> list[Edge]:
        if isinstance(vertex, AtStopVertex):
            return self._at_stop_vertex_outgoing(vertex)
        elif isinstance(vertex, DepartureVertex):
            return self._departure_vertex_outgoing(vertex)
        elif isinstance(vertex, ArrivalVertex):
            return self._arrival_vertex_outgoing(vertex)
        else:
            return []

    def incoming(self, vertex: AbstractVertex) -> list[Edge]:
        raise NotImplementedError()


class AtStopVertex(AbstractVertex):
    """Represents a passenger not aboard a transit vehicle at a named transit
    stop available to board a transit vehicle.

    Available outgoing transitions:
        - DepartureVertex: Board a transit vehicle.
    """

    def __init__(
        self,
        stop_id: GTFSID,
        time: pd.Timestamp,
    ):
        self.stop_id = stop_id
        self.time = time

    def as_tuple(self) -> tuple[GTFSID, pd.Timestamp]:
        return (self.stop_id, self.time)

    def __repr__(self) -> str:
        return f"AtStopvertex(stop_id:{self.stop_id}, datetime:{self.time})"


class DepartureVertex(AbstractVertex):
    """Represents a passenger aboard a transit vehicle at the moment of
    departure from a named transit stop.

    Available outgoing transitions:
        - ArrivalVertex: Travel to and then arrive at the
        vehicle's next scheduled stop
    """

    def __init__(
        self,
        pattern_id: int,
        service_id: GTFSID,
        row: int,
        col: int,
        datetime: pd.Timestamp,
    ):
        self.pattern_id = pattern_id
        self.service_id = service_id
        self.row = row
        self.col = col
        self.datetime = datetime

    def as_tuple(self) -> tuple[int, GTFSID, int, int, pd.Timestamp]:
        return (
            self.pattern_id,
            self.service_id,
            self.row,
            self.col,
            self.datetime,
        )

    def __repr__(self) -> str:
        return (
            f"DepartureVertex(pattern_id:{self.pattern_id}, "
            f"service_id:{self.service_id}, "
            f"row:{self.row}, col:{self.col}, datetime:{self.datetime})"
        )


class ArrivalVertex(AbstractVertex):
    """Represents a passenger aboard a transit vehicle at the moment of arrival
    at a named transit stop.

    Available outgoing transitions:
        - AtStopVertex: Alight from the transit vehicle.
        - DepartureVertex: Stay in the vehicle until the moment it departs
        for the next schedules stop.
    """

    def __init__(
        self,
        pattern_id: int,
        service_id: GTFSID,
        row: int,
        col: int,
        datetime: pd.Timestamp,
    ):
        self.pattern_id = pattern_id
        self.service_id = service_id
        self.row = row
        self.col = col
        self.datetime = datetime

    def as_tuple(self) -> tuple[int, GTFSID, int, int, pd.Timestamp]:
        return (
            self.pattern_id,
            self.service_id,
            self.row,
            self.col,
            self.datetime,
        )

    def __repr__(self) -> str:
        return (
            f"ArrivalVertex(pattern_id:{self.pattern_id}, "
            f"service_id:{self.service_id}, "
            f"row:{self.row}, col:{self.col}, datetime:{self.datetime})"
        )


def get_stop_vertex(
    feed: GTFSFeed, stop_name: str, datetime: pd.Timestamp
) -> AtStopVertex:
    stop_id = feed.get_stop_by_name(stop_name)

    return AtStopVertex(stop_id, datetime)


class Way(NamedTuple):
    nds: list[NodeId]
    tags: dict[str, str]


class Node(NamedTuple):
    lon: float
    lat: float


def read_osm(
    filename: str,
) -> tuple[dict[NodeId, Node], dict[WayId, Way]]:
    """
    Read an OSM file and return a dictionary of nodes and ways.

    Args:
        filename (str): The path to the OSM file.

    Returns:
        nodes (dict): A dictionary of osm nodes, keyed by node ID.
        ways (dict): A dictionary of osm ways, keyed by way ID.
    """
    nodes: dict[NodeId, Node] = {}
    ways: dict[WayId, Way] = {}

    way_nds = set()

    class HighwayHandler(osmium.SimpleHandler):
        def way(self, w: osmium.osm.Way) -> None:
            if "highway" not in w.tags:
                return

            if w.tags.get("highway") in {"motorway", "motorway_link"}:
                return

            nds = [n.ref for n in w.nodes]
            way_nds.update(nds)
            tags = dict(w.tags)

            ways[w.id] = Way(nds, tags)

    h = HighwayHandler()
    h.apply_file(filename)

    class NodeHandler(osmium.SimpleHandler):
        def node(self, n: osmium.osm.Node) -> None:
            # only keep nodes that are part of a highway
            if n.id in way_nds:
                nodes[n.id] = Node(n.location.lon, n.location.lat)

    n = NodeHandler()
    n.apply_file(filename)

    return nodes, ways


class ElevationRaster:
    def __init__(self, filename: str):
        self.filename = filename
        self._file: rasterio.DatasetReader | None = None
        self._elevdata: NDArray[np.float64] | None = None

    def __enter__(self) -> Self:
        self._file = rasterio.open(self.filename)
        self._elevdata = self._file.read(1)  # read the first band
        return self

    def __exit__(
        self,
        exc_type: Type[BaseException] | None,
        exc_value: Exception | None,
        traceback: TracebackType | None,
    ) -> None:
        if self._file is not None:
            self._file.close()
        self._elevdata = None

    def _interpolate(self, A: NDArray[np.float64], i: int, j: int) -> float:
        """Bilinear interpolation.

        Args:
            A (np.ndarray): A 2x2 array of values.
            i (float): The fractional row.
            j (float): The fractional column.

        Returns:
            float: The interpolated value."""

        baseline = A[0] * (1 - i) + A[1] * i
        return float(baseline[0] * (1 - j) + baseline[1] * j)

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

        if self._elevdata is None:
            raise RuntimeError(
                "ElevationRaster must be used in a with statement."
            )

        # get the elevation at the point
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


def geodesic_distance(
    geo_pt: geometry.Point, geo_pt2: geometry.Point
) -> float:
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

    earth_radius_km = 6371.0
    km = earth_radius_km * c
    return float(km * 1000.0)


def geodesic_linestring_length(ls: geometry.LineString) -> float:
    """Compute the geodesic length of a linestring in meters."""

    length = 0.0

    for i in range(len(ls.coords) - 1):
        pt1 = geometry.Point(ls.coords[i])
        pt2 = geometry.Point(ls.coords[i + 1])
        length += geodesic_distance(pt1, pt2)

    return length


class OnEarthSurfaceVertex(AbstractVertex):
    """Represents a passenger standing on the surface of the earth at a
    particular location and time."""

    def __init__(
        self,
        lon: float,
        lat: float,
        time: pd.Timestamp,
    ):
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

        self.lon = lon
        self.lat = lat
        self.time = time

    def as_tuple(self) -> tuple[float, float, pd.Timestamp]:
        return (self.lon, self.lat, self.time)

    def __repr__(self) -> str:
        return f"OnEarthSurfaceNode({self.lon}, {self.lat}, {self.time})"


class MidstreetVertex(AbstractVertex):
    """Represents a passenger standing on a street segment."""

    def __init__(
        self,
        midseg_ref: MidSegmentRef,
        time: pd.Timestamp,
    ):
        """Initialize the node.

        Args:
            midseg_ref (MidSegmentRef): A reference to the way segment.
            time (pd.Timestamp): The time of the passenger.
        """

        self.midseg_ref = midseg_ref
        self.time = time

    def __repr__(self) -> str:
        return f"MidStreetVertex({self.midseg_ref}, {self.time})"

    def as_tuple(self) -> tuple[MidSegmentRef, pd.Timestamp]:
        return (self.midseg_ref, self.time)


class StreetNodeVertex(AbstractVertex):
    """Represents the passenger standing on a street at a node."""

    def __init__(self, node_id: NodeId, time: pd.Timestamp):
        """Initialize the node.

        Args:
            node_ref (NodeRef): A reference to a node in a way.
            datetime (pd.Timestamp): The time of the passenger.
        """

        self.node_id = node_id
        self.time = time

    def __repr__(self) -> str:
        return f"StreetNodeVertex({self.node_id}, {self.time})"

    def as_tuple(self) -> tuple[NodeId, pd.Timestamp]:
        return (self.node_id, self.time)


class StreetData:
    """Holds all the context needed to generate adjacent edges in a
    street network. The context includes street and elevation data."""

    def __init__(self, osm_fn: str, elevation_raster_fn: str | None = None):
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
        self.node_refs = self._get_node_references()
        self.way_vertex_nodes = self._get_way_vertex_nodes()
        print("done")

        print("Getting node elevations...", end="", flush=True)
        self.node_elevs: dict[NodeId, float] = {}
        if self.elevation_raster_fn is not None:
            with ElevationRaster(self.elevation_raster_fn) as elev_raster:
                for node, (lon, lat) in self.nodes.items():
                    self.node_elevs[node] = elev_raster.get_elevation(lon, lat)
        print("done")

        print("Creating spatial index...", end="", flush=True)
        self.segments = self._generate_way_segments()
        self.segment_spatial_index = STRtree(
            [x.geometry for x in self.segments]
        )
        print("done")

    def _get_node_references(self) -> dict[NodeId, set[NodeRef]]:
        """Get a dictionary of node references.

        Returns:
            Dict: A dictionary of instances in which a node was used in a way.
                The format is {node_id: {(way_id, node_index)}}"""

        nd_refs: dict[NodeId, set[NodeRef]] = defaultdict(set)

        for way_id, way in self.ways.items():
            for i, nd in enumerate(way.nds):
                nd_refs[nd].add(NodeRef(way_id, i))

        return nd_refs

    def _get_way_vertex_nodes(
        self,
    ) -> dict[WayId, list[ArrayIndex]]:
        """Returns a dictionary of "vertex nodes" for each way. A vertex node is
        a node that is either referenced by a way more than once, or is the
        first or last node. Each vertex node is represented by the index of the
        node in the way.
        """

        way_vertex_nodes: dict[WayId, list[ArrayIndex]] = {}

        for way_id, way in self.ways.items():
            vertex_nodes: list[ArrayIndex] = []

            for i, nd in enumerate(way.nds):
                if (
                    i == 0
                    or i == len(way.nds) - 1
                    or len(self.node_refs[nd]) > 1
                ):
                    vertex_nodes.append(i)

            way_vertex_nodes[way_id] = vertex_nodes

        return way_vertex_nodes

    def _generate_way_segments(
        self,
    ) -> list[Segment]:
        """Generate all the segments in the ways.

        This is a part of generating the index that is used to find the closest
        way to a particular location.

        Returns:
            list[Segment]: A list of every segment in the OSM file. A segment
                is a pair of adjacent nodes in a way.
        """

        segments: list[Segment] = []

        for way_id, way in self.ways.items():
            for segment_index, (nd1, nd2) in enumerate(cons(way.nds)):
                pt1 = self.nodes[nd1]
                pt2 = self.nodes[nd2]

                segment = geometry.LineString([pt1, pt2])

                segments.append(
                    Segment(SegmentRef(way_id, segment_index), segment)
                )

        return segments

    def get_nearest_segment(
        self, lon: float, lat: float, search_radius: float = 0.001
    ) -> MidSegmentRef | None:
        """Get the nearest segment to a particular location.

        Args:
            lon (float): The longitude of the location.
            lat (float): The latitude of the location.
            search_radius (float, optional): The search radius in degrees.
                Defaults to 0.001, which is about 100 meters.

        Returns:
            MidSegmentRef: The nearest segment and the distance along the
                segment that is closest to the location. If there are no
                segments within the search radius, returns None.
        """

        # create a search geometry
        query_pt = geometry.Point(lon, lat)
        search_area = query_pt.buffer(search_radius)

        # find all the segments that intersect the search geometry
        query_result_indices = self.segment_spatial_index.query(search_area)

        # if there are no results, return None
        if len(query_result_indices) == 0:
            return None

        # find the closest segment
        i = np.argmin(
            [
                self.segments[query_result_index].geometry.distance(query_pt)
                for query_result_index in query_result_indices
            ]
        )
        nearest_query_result_index = query_result_indices[i]

        # find the distance along the segment that is closest to the location
        segment = self.segments[nearest_query_result_index]
        distance_along_segment = segment.geometry.line_locate_point(
            query_pt, normalized=True
        )

        return MidSegmentRef(segment.ref, distance_along_segment)

    def get_way_point(self, midseg_ref: MidSegmentRef) -> geometry.Point:
        """Get the point in the middle of a way segment.

        Args:
            midseg_ref (MidSegmentRef): The reference to the segment and the
                distance along the segment.

        Raises:
            ValueError: If the segment index is out of range.

        Returns:
            geometry.Point: The point in the middle of the segment.
        """

        way = self.ways[midseg_ref.segment.way_id]

        if (
            midseg_ref.segment.segment_index < 0
            or midseg_ref.segment.segment_index >= len(way.nds) - 1
        ):
            raise ValueError(
                f"Segment index {midseg_ref.segment.segment_index} "
                f"is out of range "
                f"for way {midseg_ref.segment.way_id}"
            )

        nd1 = way.nds[midseg_ref.segment.segment_index]
        nd2 = way.nds[midseg_ref.segment.segment_index + 1]

        pt1 = self.nodes[nd1]
        pt2 = self.nodes[nd2]

        ls = geometry.LineString([pt1, pt2])

        return ls.interpolate(midseg_ref.offset, normalized=True)

    def adj_vertex_node(
        self,
        node_ref: NodeRef,
        search_forward: bool = True,
    ) -> ArrayIndex:
        """Get the position in the way of the next vertex node. A vertex node
        is a node that is either referenced by a way more than once, or is the
        first or last node. Essentially, it is node at which a turn can be
        made. The search is inclusive of the node at the specified index.

        Args:
            node_ref (NodeRef)): The way_id and node_index of the node.
            search_forward (bool, optional): If true, finds the next vertex
                node. If false, finds the previous vertex node. Defaults to
                True.

        Returns:
            int: The index of the next vertex node in the way, inclusive
                of the node at the specified index."""

        way_vertex_nodes = self.way_vertex_nodes[node_ref.way_id]

        if search_forward:
            for way_vertex_node in way_vertex_nodes:
                if way_vertex_node >= node_ref.node_index:
                    return way_vertex_node
        else:
            for way_vertex_node in reversed(way_vertex_nodes):
                if way_vertex_node <= node_ref.node_index:
                    return way_vertex_node

    def is_oneway(self, way_id: WayId) -> bool:
        """Check if a way is one-way.

        Args:
            way_id (int): The ID of the way.

        Returns:
            bool: True if the way is one-way, False otherwise.
        """

        return self.ways[way_id].tags.get("oneway") in ["yes", "true", "1"]


class StreetEdgeProvider(EdgeProvider):
    def __init__(self, osm_data: StreetData):
        self.osm_data = osm_data

    def _outgoing_on_earth_surface_vertex(
        self, node: OnEarthSurfaceVertex
    ) -> list[Edge]:
        """Get the outgoing edges from an OnEarthSurfaceVertex.

        Args:
            node (OnEarthSurfaceNode): _description_

        Returns:
            list[Edge]: _description_
        """
        # get nearest segment
        midseg_ref = self.osm_data.get_nearest_segment(node.lon, node.lat)

        if midseg_ref is None:
            return []

        # get closest point on nearest segment
        closest_way_pt = self.osm_data.get_way_point(midseg_ref)
        distance = geodesic_distance(
            geometry.Point(node.lon, node.lat), closest_way_pt
        )

        # convert distance to time and utility
        dt = distance / WALKING_SPEED
        weight = dt * WALKING_RELUCTANCE

        # make adjacent node
        adj_vertex = MidstreetVertex(
            midseg_ref,
            node.time + pd.Timedelta(seconds=dt),
        )

        # return a single edge
        return [Edge(adj_vertex, weight)]

    def _outgoing_midstreet_vertex(
        self, vertex: MidstreetVertex
    ) -> list[Edge]:
        edges: list[Edge] = []

        midpoint = self.osm_data.get_way_point(vertex.midseg_ref)

        # get forward segment
        seg_start = vertex.midseg_ref.segment.segment_index + 1
        seg_end = self.osm_data.adj_vertex_node(
            NodeRef(vertex.midseg_ref.segment.way_id, seg_start)
        )

        # include both endpoints
        nds = self.osm_data.ways[vertex.midseg_ref.segment.way_id].nds[
            seg_start : seg_end + 1
        ]

        # compute distance and time
        ls = geometry.LineString(
            [midpoint] + [self.osm_data.nodes[nd] for nd in nds]
        )

        distance = geodesic_linestring_length(ls)
        dt = distance / WALKING_SPEED
        weight = dt * WALKING_RELUCTANCE

        # make vertex
        forward_vertex = StreetNodeVertex(
            nds[-1],
            vertex.time + pd.Timedelta(seconds=dt),
        )
        edges.append(Edge(forward_vertex, weight))

        if not self.osm_data.is_oneway(vertex.midseg_ref.segment.way_id):
            # get reverse segment
            seg_start = vertex.midseg_ref.segment.segment_index
            seg_end = self.osm_data.adj_vertex_node(
                NodeRef(vertex.midseg_ref.segment.way_id, seg_start),
                search_forward=False,
            )

            inclusive_seg_end = seg_end - 1 if seg_end != 0 else None
            nds = self.osm_data.ways[vertex.midseg_ref.segment.way_id].nds[
                seg_start:inclusive_seg_end:-1
            ]

            # compute distance and time
            ls = geometry.LineString(
                [midpoint] + [self.osm_data.nodes[nd] for nd in nds]
            )

            distance = geodesic_linestring_length(ls)
            dt = distance / WALKING_SPEED
            weight = dt * WALKING_RELUCTANCE

            # make vertex
            reverse_vertex = StreetNodeVertex(
                nds[-1],
                vertex.time + pd.Timedelta(seconds=dt),
            )
            edges.append(Edge(reverse_vertex, weight))

        return edges

    def _get_forward_block(
        self, start: NodeRef
    ) -> tuple[geometry.LineString, NodeId] | None:
        """Get the block (ie, the segment of a road between two junctions)
            beginning at a node and continuing forward to the next junction
            node.

        Args:
            start (NodeRef): The node to start from.

        Raises:
            ValueError: If the node index is out of range.

        Returns:
            tuple[geometry.LineString, NodeId] | None: A geometry
                containing all the geo points of the segment, and the ID of the
                last node in the segment. If the node is at the end of the way,
                returns None.
        """
        way = self.osm_data.ways[start.way_id]

        if start.node_index < 0 or start.node_index >= len(way.nds):
            raise ValueError(
                f"Node index {start.node_index} is out of range for "
                f"way {start.way_id}"
            )

        # if we're at the end of the way, there is no forward segment
        if start.node_index == len(way.nds) - 1:
            return None

        # get forward segment
        seg_end = self.osm_data.adj_vertex_node(
            NodeRef(start.way_id, start.node_index + 1)
        )

        # include both endpoints
        seg_end_inclusive = seg_end + 1
        nds = self.osm_data.ways[start.way_id].nds[
            start.node_index : seg_end_inclusive
        ]

        ls: geometry.LineString = geometry.LineString(
            [self.osm_data.nodes[nd] for nd in nds]
        )

        return ls, nds[-1]

    def _get_reverse_block(
        self, start: NodeRef
    ) -> tuple[geometry.LineString, NodeId] | None:
        """Get the block beginning at a node and continuing backward to the
        previous junction node.

        Args:
            start (NodeRef): The node to start from.

        Raises:
            ValueError: If the node index is out of range.

        Returns:
            tuple[geometry.LineString, ArrayIndex] | None: A geometry
                containing all the geo points of the segment, and the ID of the
                of the last node in the segment. If the node is at the
                beginning of the way, returns None.
        """
        way = self.osm_data.ways[start.way_id]

        if start.node_index < 0 or start.node_index >= len(way.nds):
            raise ValueError(
                f"Node index {start.node_index} is out of range for "
                f"way {start.way_id}"
            )

        # if we're at the beginning of the way, there is no reverse segment
        if start.node_index == 0:
            return None

        # get reverse segment
        seg_end = self.osm_data.adj_vertex_node(
            NodeRef(start.way_id, start.node_index - 1),
            search_forward=False,
        )

        # include both endpoints
        seg_end_inclusive = seg_end - 1 if seg_end != 0 else None
        nds = self.osm_data.ways[start.way_id].nds[
            start.node_index : seg_end_inclusive : -1
        ]

        ls: geometry.LineString = geometry.LineString(
            [self.osm_data.nodes[nd] for nd in nds]
        )

        return ls, nds[-1]

    def _outgoing_street_node_vertex(
        self, vertex: StreetNodeVertex
    ) -> list[Edge]:
        edges: list[Edge] = []

        # for each referenced way
        for node_ref in self.osm_data.node_refs[vertex.node_id]:
            # get forward segment
            block = self._get_forward_block(node_ref)
            if block is not None:
                ls, block_end_node_id = block

                distance = geodesic_linestring_length(ls)
                dt = distance / WALKING_SPEED
                weight = dt * WALKING_RELUCTANCE

                vertex = StreetNodeVertex(
                    block_end_node_id,
                    vertex.time + pd.Timedelta(seconds=dt),
                )
                edge = Edge(vertex, weight)

                edges.append(edge)

            # get reverse segment
            if not self.osm_data.is_oneway(node_ref.way_id):
                block = self._get_reverse_block(node_ref)
                if block is not None:
                    ls, block_end_node_id = block

                    distance = geodesic_linestring_length(ls)
                    dt = distance / WALKING_SPEED
                    weight = dt * WALKING_RELUCTANCE

                    vertex = StreetNodeVertex(
                        block_end_node_id,
                        vertex.time + pd.Timedelta(seconds=dt),
                    )
                    edge = Edge(vertex, weight)

                    edges.append(edge)

        return edges

    def outgoing(self, vertex: AbstractVertex) -> list[Edge]:
        if isinstance(vertex, OnEarthSurfaceVertex):
            return self._outgoing_on_earth_surface_vertex(vertex)
        elif isinstance(vertex, MidstreetVertex):
            return self._outgoing_midstreet_vertex(vertex)
        elif isinstance(vertex, StreetNodeVertex):
            return self._outgoing_street_node_vertex(vertex)
        else:
            return []

    def incoming(self, vertex: AbstractVertex) -> list[Edge]:
        raise NotImplementedError()


class TransitStreetConnectionEdgeProvider(EdgeProvider):
    def __init__(self, transit_data: GTFSFeed, street_data: StreetData):
        self.transit_data = transit_data
        self.street_data = street_data

        links = self._find_links()

        # dict for looking up stop_id -> midsegment_ref
        self.stop_to_midseg_ref: dict[GTFSID, MidSegmentRef] = dict(links)

        # dict for midseg_ref -> stops
        self.midseg_ref_to_stops: dict[
            MidSegmentRef, set[GTFSID]
        ] = defaultdict(set)

        # dict for segment_ref -> midseg_refs
        self.segment_ref_to_midseg_refs: dict[
            SegmentRef, set[MidSegmentRef]
        ] = defaultdict(set)

        # dict for node_id -> midseg_refs
        self.node_to_midseg_refs: dict[
            NodeId, set[MidSegmentRef]
        ] = defaultdict(set)

        for stop_id, midseg_ref in links:
            # midseg_ref -> stop_ids
            self.midseg_ref_to_stops[midseg_ref].add(stop_id)

            # segment_ref -> midseg_refs
            self.segment_ref_to_midseg_refs[midseg_ref.segment].add(midseg_ref)

            # node_id -> midseg_refs
            way = self.street_data.ways[midseg_ref.segment.way_id]
            seg_start_node = way.nds[midseg_ref.segment.segment_index]
            seg_end_node = way.nds[midseg_ref.segment.segment_index + 1]
            self.node_to_midseg_refs[seg_start_node].add(midseg_ref)
            self.node_to_midseg_refs[seg_end_node].add(midseg_ref)

    def _find_links(self) -> list[tuple[GTFSID, MidSegmentRef]]:
        """Find the links between stops and street segments.

        Returns:
            list[tuple[GTFSID, MidSegmentRef]]: A list of tuples containing
                the stop ID and the reference to the street segment.
        """
        links = []

        for (
            stop_id,
            stop_lon,
            stop_lat,
        ) in self.transit_data.get_stop_locations():
            nearest = self.street_data.get_nearest_segment(stop_lon, stop_lat)

            if nearest is not None:
                links.append((stop_id, nearest))

        return links

    def _outgoing_at_stop_vertex(self, vertex: AtStopVertex) -> list[Edge]:
        # In the case of an AtStopVertex, returns a MidstreetVertex at the
        # nearest point on the street to the stop.
        midseg_ref = self.stop_to_midseg_ref.get(vertex.stop_id)
        if midseg_ref is None:
            return []

        stop_point = self.transit_data.get_stop_point(vertex.stop_id)
        street_point = self.street_data.get_way_point(midseg_ref)

        distance = geodesic_distance(street_point, stop_point)
        dt = distance / WALKING_SPEED
        weight = dt * WALKING_RELUCTANCE

        adj_vertex = MidstreetVertex(
            midseg_ref, vertex.time + pd.Timedelta(seconds=dt)
        )
        edge = Edge(adj_vertex, weight)

        return [edge]

    def _outgoing_street_node_vertex(
        self, vertex: StreetNodeVertex
    ) -> list[Edge]:
        # In the case of a StreetNodeVertex, returns all MidstreetVertices that
        # are adjacent to the node.
        edges: list[Edge] = []

        node_point = self.street_data.nodes[vertex.node_id]

        for midseg_ref in self.node_to_midseg_refs[vertex.node_id]:
            midseg_point = self.street_data.get_way_point(midseg_ref)

            distance = geodesic_distance(
                geometry.Point(node_point.lon, node_point.lat), midseg_point
            )
            dt = distance / WALKING_SPEED
            weight = dt * WALKING_RELUCTANCE

            adj_vertex = MidstreetVertex(
                midseg_ref, vertex.time + pd.Timedelta(seconds=dt)
            )
            edge = Edge(adj_vertex, weight)

            edges.append(edge)

        return edges

    def _outgoing_midstreet_vertex(
        self, vertex: MidstreetVertex
    ) -> list[Edge]:
        edges: list[Edge] = []

        return edges

    def outgoing(self, vertex: AbstractVertex) -> list[Edge]:
        if isinstance(vertex, StreetNodeVertex):
            return self._outgoing_street_node_vertex(vertex)
        elif isinstance(vertex, AtStopVertex):
            return self._outgoing_at_stop_vertex(vertex)
        elif isinstance(vertex, MidstreetVertex):
            return self._outgoing_midstreet_vertex(vertex)
        else:
            return []

    def incoming(self, vertex: AbstractVertex) -> list[Edge]:
        raise NotImplementedError()


def main() -> None:
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


if __name__ == "__main__":
    main()
