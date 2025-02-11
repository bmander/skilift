# skilift
a simple bike+bus trip planner

# terraform

* Set `TF_VAR_GCP_PROJECT_ID` to current project id.
* Set `GOOGLE_CLOUD_KEYFILE_JSON` to the _contents_ of the service account json key.

```
cd terraform
terraform apply
```

# container

```
$ docker pull opentripplanner/opentripplanner:latest
$ mkdir otpdata
$ wget https://download.geofabrik.de/north-america/us/washington-latest.osm.pbf
$ wget https://metro.kingcounty.gov/GTFS/google_transit.zip
$ docker run -e JAVA_OPTS="-Xmx12G" -v /home/badhill/otpdata:/var/opentripplanner opentripplanner/opentripplanner:latest --build --save
```