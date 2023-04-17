from datetime import timedelta
import os
from collections import defaultdict
from typing import (
    Dict,
    Hashable,
    List,
    NamedTuple,
    Optional,
    Sequence,
    Tuple,
)
from zipfile import ZipFile

import numpy as np
import pandas as pd
from dotenv import load_dotenv
from numpy.typing import NDArray

SecondsSinceMidnight = int
GTFSID = Hashable


class TimetableEvent(NamedTuple):
    pattern_id: int
    service_id: int
    row: int  # the index of the trip
    col: int  # the index of the stop
    datetime: pd.Timestamp  # datetime of the event


class Timetable:
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
    def __init__(self, zf):
        """Reads in GTFS data from a zipfile."""

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
                ADD_SERVICE = 1
                REMOVE_SERVICE = 2

                if row.exception_type == ADD_SERVICE:
                    expanded_cal[row.date.date()].add(row.service_id)
                elif row.exception_type == REMOVE_SERVICE:
                    if row.service_id in expanded_cal[row.date.date()]:
                        expanded_cal[row.date.date()].remove(row.service_id)

            calendar_dates.apply(process_calendar_dates_row, axis=1)

        return dict(expanded_cal)

    def _read_trips(self, zf):
        if "trips.txt" not in zf.namelist():
            raise Exception("trips.txt not found in GTFS zip file")

        with zf.open("trips.txt") as f:
            return pd.read_csv(f)

    def _read_stops(self, zf):
        if "stops.txt" not in zf.namelist():
            raise Exception("stops.txt not found in GTFS zip file")

        with zf.open("stops.txt") as f:
            return pd.read_csv(f)

    def _read_stop_times(self, zf):
        if "stop_times.txt" not in zf.namelist():
            raise Exception("stop_times.txt not found in GTFS zip file")

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

        SECS_IN_DAY = 24 * 60 * 60

        events = []

        query_time = seconds_since_midnight(query_datetime)
        query_date = query_datetime.date()

        # The query_time will always be between 0 and 24 hours, but gtfs
        # times can be greater than 24 hours to represent schedule events
        # in the early morning of the next day. If the query time is in the
        # early morning, we need to query the previous day's schedule.
        if query_time + SECS_IN_DAY < self.day_end:
            query_time += SECS_IN_DAY
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


class TransitGraph:
    ALIGHTING_WEIGHT = 60  # utils; where 1 util ~= 1 second of travel time

    def __init__(self, feed: GTFS):
        self.feed = feed

    def adjacent_forward(self, node: Tuple) -> Sequence[Tuple[Tuple, float]]:
        """Returns adjacent nodes along with the edge weight. Nodes are tuples
        describing rider states, for example ('at_stop', stop_id, current_time)
        or ('departing', stop_pattern_id, pattern_row, pattern_col,
        current_time).

        Args:
            node: Node for which to get adjacent nodes.

        Returns:
            List of tuples describing adjacent nodes and the edge weight.
        """

        outgoing_edges: List[Tuple[Tuple, float]] = []

        node_type = node[0]

        if node_type == "at_stop":
            _, stop_id, current_time = node

            for event in self.feed.find_stop_events(
                stop_id, current_time, find_departures=True
            ):
                node = (
                    "departing",
                    event.pattern_id,
                    event.service_id,
                    event.row,
                    event.col,
                    event.datetime,
                )
                weight = float((event.datetime - current_time).seconds)
                edge = (node, weight)
                outgoing_edges.append(edge)
        elif node_type == "departing":
            _, pattern_id, service_id, row, col, current_time = node

            timetable = self.feed.timetables[(pattern_id, service_id)]

            last_departure_time = timetable.departure_times[row, col]
            arrival_time = timetable.arrival_times[row, col + 1]
            segment_duration = arrival_time - last_departure_time

            arrival_datetime = current_time + pd.Timedelta(
                segment_duration, unit="s"
            )

            node = (
                "arriving",
                pattern_id,
                service_id,
                row,
                col + 1,
                arrival_datetime,
            )
            weight = float(segment_duration)
            edge = (node, weight)
            outgoing_edges.append(edge)
        elif node_type == "arriving":
            _, pattern_id, service_id, row, col, current_time = node

            timetable = self.feed.timetables[(pattern_id, service_id)]

            # make an edge for waiting until departure
            arrival_time = timetable.arrival_times[row, col]
            departure_time = timetable.departure_times[row, col]
            wait_duration = departure_time - arrival_time
            node = (
                "departing",
                pattern_id,
                service_id,
                row,
                col,
                current_time + pd.Timedelta(wait_duration, unit="s"),
            )
            weight = float(wait_duration)
            departure_edge = (node, weight)
            outgoing_edges.append(departure_edge)

            # make an edge for alighting to the stop
            stop_id = timetable.stop_ids[col]
            node = ("at_stop", stop_id, current_time)
            weight = self.ALIGHTING_WEIGHT
            alighting_edge = (node, weight)
            outgoing_edges.append(alighting_edge)

        return outgoing_edges

    @classmethod
    def load(cls, filename):
        with ZipFile(filename, "r") as zf:
            feed = GTFS(zf)
            ret = cls(feed)
        zf = ZipFile(filename, "r")

        feed = GTFS(zf)
        ret = cls(feed)

        return ret


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

            if TransitGraph._is_gtfs_zip(zf):
                gtfs = GTFS(zf)
                print(gtfs.calendar)

    # read in every file in the street data directory


if __name__ == "__main__":
    main()
