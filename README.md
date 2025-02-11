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

build:
```
$ docker pull opentripplanner/opentripplanner:latest
$ mkdir otpdata
$ wget https://download.geofabrik.de/north-america/us/washington-latest.osm.pbf
$ wget https://metro.kingcounty.gov/GTFS/google_transit.zip -O kingcometro.gtfs.zip

$ wget https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/13/TIFF/historical/n48w122/USGS_13_n48w122_20230307.tif
$ wget https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/13/TIFF/historical/n48w123/USGS_13_n48w123_20240327.tif

$ docker run -e JAVA_OPTS="-Xmx12G" -v /home/badhill/otpdata:/var/opentripplanner opentripplanner/opentripplanner:latest --build --save
```

run:
```
$ docker run -d -p 80:80 -e JAVA_OPTS="-Xmx12G" -v /home/badhill/otpdata:/var/opentripplanner opentripplanner/opentripplanner:latest --load --serve --port 80
```