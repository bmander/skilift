from dotenv import load_dotenv
import os
import pandas as pd
from zipfile import ZipFile
from collections import defaultdict
import numpy as np


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


class GTFS:
    def __init__(self, zf):
        """Reads in GTFS data from a zipfile."""

        self.zf = zf
        self.calendar = self._expand_service_dates(zf)
        self.trips = self._read_trips(zf)
        self.stops = self._read_stops(zf)
        self.stop_times = self._read_stop_times(zf)

        self._augment_with_stop_patterns()
        self.schedules = self._get_schedules()

    def _augment_with_stop_patterns(self):

        # get trip_id -> stop pattern
        trip_stop_patterns = self.stop_times.sort_values("stop_sequence") \
                                            .groupby(["trip_id"]) \
                                            .agg({"stop_id": tuple}) \
                                            .to_dict(orient="index")

        # reverse to get stop_pattern -> trip_ids
        stop_pattern_trips = defaultdict(set)
        for trip_id, stop_pattern in trip_stop_patterns.items():
            stop_pattern_trips[stop_pattern["stop_id"]].add(trip_id)
        stop_pattern_trips = dict(stop_pattern_trips)

        # create stop_pattern_id -> ordered list of stops
        self.stop_patterns = dict(zip(range(len(stop_pattern_trips)),
                                  stop_pattern_trips.keys()))

        # create a dataframe of (stop_pattern_id, trip_id)
        stop_pattern_id_trips = \
            expand_pairs(zip(range(len(stop_pattern_trips)),
                         stop_pattern_trips.values()))
        stop_pattern_id_trips_df = \
            pd.DataFrame(stop_pattern_id_trips,
                         columns=["stop_pattern_id", "trip_id"])

        # augment the stop_times table with stop_pattern_id and service_id
        self.stop_times = self.stop_times.merge(stop_pattern_id_trips_df,
                                                on="trip_id")
        self.stop_times = \
            self.stop_times.merge(self.trips[["trip_id", "service_id"]],
                                  on="trip_id")

        # create dict of stop_id -> stop_pattern_ids
        stop_pattern_ids = defaultdict(set)
        for stop_pattern_id, stop_pattern in self.stop_patterns.items():
            for stop_id in stop_pattern:
                stop_pattern_ids[stop_id].add(stop_pattern_id)
        self.stop_pattern_ids = dict(stop_pattern_ids)

    def _get_schedules(self):
        scheds = {}

        pattern_groups = self.stop_times.sort_values(["trip_id",
                                                      "stop_sequence"]) \
                                        .groupby(["stop_pattern_id",
                                                  "service_id"])

        for (stop_pattern_id, service_id), sched in pattern_groups:

            sched = [(trip_id, trip[["arrival_time", "departure_time"]].values)
                     for trip_id, trip in sched.groupby("trip_id")]

            # sort trips ascending by first arrival time
            sched.sort(key=lambda x: x[1][0, 0])
            trip_ids = [x[0] for x in sched]
            sched = [x[1] for x in sched]
            sched = np.stack(sched)  # (n_trips, n_stops, 2)

            # ensure that the time increases along the trip dimension
            assert (np.diff(sched, axis=0) > 0).all()
            # ensure that the time increases along the stop dimension
            assert (np.diff(sched, axis=1) > 0).all()

            scheds[(stop_pattern_id, service_id)] = (trip_ids, sched)

        return scheds

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
                calendar = pd.read_csv(f,
                                       parse_dates=["start_date", "end_date"])

            weekdays = ["monday", "tuesday", "wednesday", "thursday", "friday",
                        "saturday", "sunday"]

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
            return pd.read_csv(f,
                               converters={"arrival_time": parse_gtfs_time,
                                           "departure_time": parse_gtfs_time})

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


class TransitGraph:
    def __init__(self, uri):
        self.uri = uri

    def adjacent(self, node):
        """Returns adjacent nodes along with the edge weight. Nodes are tuples
        describing rider states, for example ('at_stop', stop_id, current_time)
        or ('on_route', stoptime_id).

        Args:
            node: Node to get adjacent nodes for.

        Returns:
            List of tuples describing adjacent nodes and the edge weight.
        """

        node_type = node[0]

        if node_type == 'at_stop':
            # find all following stop_times at this stop

            _, stop_id, current_time = node

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

        required_files = ["stops.txt", "routes.txt", "trips.txt",
                          "stop_times.txt"]
        for file in required_files:
            if file not in files:
                return False

        return True

    @classmethod
    def load(cls, filename):
        zf = ZipFile(filename, "r")

        if not cls._is_gtfs_zip(zf):
            print("Error: " + filename + " is not a valid GTFS zip file")
            exit(1)

        # get agency
        agency = pd.read_csv(zf.open("agency.txt"))
        print(agency)

        # read in stops.txt
        stops = pd.read_csv(zf.open("stops.txt"))

        print(stops)


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
