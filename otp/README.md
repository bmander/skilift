# OTP2 Backend Setup

This directory contains the OpenTripPlanner 2 configuration for Skilift's bike+transit routing backend. OTP2 ingests GTFS transit feeds, OpenStreetMap street data, and USGS elevation data to build a routing graph, then serves trip planning queries over a GraphQL API.

## Prerequisites

- Docker and docker-compose
- At least 16 GB of RAM (the graph build phase requires ~12 GB heap)
- ~5 GB of disk space for source data and the built graph

## Data Acquisition

Before building the graph, you need to download the source data files into the `otp/data/` directory.

### 1. Create the data directory

```bash
mkdir -p otp/data
```

### 2. King County Metro GTFS

Download the current GTFS feed from King County Metro:

```bash
curl -L -o otp/data/kcm-gtfs.zip \
  "http://metro.kingcounty.gov/gtfs/google_transit.zip"
```

### 3. Sound Transit GTFS

Download the current GTFS feed from Sound Transit:

```bash
curl -L -o otp/data/st-gtfs.zip \
  "https://www.soundtransit.org/GTFS-KCM/google_transit.zip"
```

### 4. OpenStreetMap Data

Download the Washington state extract from Geofabrik, then clip it to the Seattle bounding box using osmium:

```bash
# Download Washington state extract
curl -L -o /tmp/washington-latest.osm.pbf \
  "https://download.geofabrik.de/north-america/us/washington-latest.osm.pbf"

# Clip to region bounding box (values from gradle.properties REGION_BBOX_* properties)
osmium extract \
  --bbox=-122.5,47.3,-122.1,47.8 \
  --output=otp/data/seattle.osm.pbf \
  /tmp/washington-latest.osm.pbf

# Clean up the full state file
rm /tmp/washington-latest.osm.pbf
```

Install osmium if needed: `brew install osmium-tool` (macOS) or `apt install osmium-tool` (Debian/Ubuntu).

### 5. USGS DEM (Elevation Data)

Download GeoTIFF elevation tiles covering the Seattle area from the USGS National Map. The Seattle area is covered by 1/3 arc-second DEM tiles. You need tiles covering approximately 47.3-47.8N, 122.1-122.5W.

Visit the USGS National Map download page (https://apps.nationalmap.gov/downloader/) and:

1. Set the area of interest to the Seattle bounding box.
2. Select "Elevation Products (3DEP)" > "1/3 arc-second DEM" > GeoTIFF format.
3. Download the relevant tiles.
4. If multiple tiles are needed, merge them with GDAL:

```bash
gdal_merge.py -o otp/data/seattle-dem.tif tile1.tif tile2.tif
```

Install GDAL if needed: `brew install gdal` (macOS) or `apt install gdal-bin` (Debian/Ubuntu).

## Copy Configuration Files

The OTP2 configuration files must be present in the data directory alongside the source data. Copy them before building:

```bash
cp otp/build-config.json otp/data/
cp otp/otp-config.json otp/data/
cp otp/router-config.json otp/data/
```

## Building the Graph

The graph build ingests all source data and produces a `graph.obj` file. This takes 10-30 minutes depending on hardware.

```bash
cd otp
docker compose run --rm otp-build
```

After a successful build, you should see `otp/data/graph.obj` (approximately 200-500 MB).

## Running the Server

Start the OTP2 server, which loads the pre-built graph and serves the GraphQL API:

```bash
cd otp
docker compose up otp-server
```

The server will be available at:

- **GraphQL endpoint:** http://localhost:8080/otp/routers/default/index/graphql
- **GraphiQL UI:** http://localhost:8080/graphiql
- **Debug UI:** http://localhost:8080/

To run in the background:

```bash
docker compose up -d otp-server
```

### Verify the Server

Test with a simple GraphQL query:

```bash
curl -X POST http://localhost:8080/otp/routers/default/index/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ planConnection(origin: {coordinate: {latitude: 47.6062, longitude: -122.3321}}, destination: {coordinate: {latitude: 47.6205, longitude: -122.3493}}, first: 1, modes: {accessMode: BICYCLE, egressMode: BICYCLE, transitModes: [{mode: BUS}]}) { edges { node { duration } } } }"
  }'
```

## Updating Data (Manual)

When GTFS feeds are updated (typically every few months):

1. Re-download the GTFS files (steps 2 and 3 above).
2. Re-copy the config files into `otp/data/`.
3. Rebuild the graph: `docker compose run --rm otp-build`
4. Restart the server: `docker compose restart otp-server`

## Updating Data (Cloud Build)

Three Cloud Build jobs automate the data refresh, graph build, and server deployment on GCP:

- **`cloudbuild-prepare-data.yaml`** — Downloads KCM + Sound Transit GTFS feeds, downloads and clips Washington OSM data to the region bounding box (configurable via `_REGION_BBOX_*` substitution variables), downloads USGS DEM elevation data, and uploads everything (including config files) to `gs://skilift-otp-data/`.
- **`cloudbuild-build-graph.yaml`** — Runs the OTP graph build inside Cloud Build (on a high-memory worker), then uploads the built `graph.obj` to GCS.
- **`cloudbuild-start-server.yaml`** — SSHes into the `skilift-otp` VM, downloads the pre-built graph from GCS, and (re)starts the server container. No graph building happens on the VM.

### One-Time Setup

Before running the Cloud Build jobs for the first time:

```bash
# Create GCS bucket
gcloud storage buckets create gs://skilift-otp-data --project=skilift-450102 --location=us-west1

# Enable Cloud Build API
gcloud services enable cloudbuild.googleapis.com --project=skilift-450102

# Grant Cloud Build service account SSH access to the VM
PROJECT_NUM=$(gcloud projects describe skilift-450102 --format='value(projectNumber)')
gcloud projects add-iam-policy-binding skilift-450102 \
  --member="serviceAccount:${PROJECT_NUM}@cloudbuild.gserviceaccount.com" \
  --role="roles/compute.instanceAdmin.v1"
gcloud projects add-iam-policy-binding skilift-450102 \
  --member="serviceAccount:${PROJECT_NUM}@cloudbuild.gserviceaccount.com" \
  --role="roles/iap.tunnelResourceAccessor"
gcloud projects add-iam-policy-binding skilift-450102 \
  --member="serviceAccount:${PROJECT_NUM}@cloudbuild.gserviceaccount.com" \
  --role="roles/compute.osLogin"
```

### Running the Jobs

```bash
# Refresh data only (run from repo root so config files are included)
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-prepare-data.yaml .

# Build graph only (uses data already in bucket, runs on high-memory Cloud Build worker)
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-build-graph.yaml --no-source

# (Re)start server only (loads pre-built graph from GCS — fast, no graph build)
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-start-server.yaml --no-source

# Full pipeline: refresh data, build graph, then start server
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-prepare-data.yaml . && \
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-build-graph.yaml --no-source && \
gcloud builds submit --project=skilift-450102 --config=otp/cloudbuild-start-server.yaml --no-source
```

### Verification

1. After prepare-data: `gsutil ls -lh gs://skilift-otp-data/` should show `kcm-gtfs.zip`, `st-gtfs.zip`, `seattle.osm.pbf`, `seattle-dem.tif`, and the 3 config JSON files.
2. After build-graph: `gsutil ls -lh gs://skilift-otp-data/graph.obj` should show the built graph file.
3. After start-server: `gcloud compute ssh skilift-otp --zone=us-west1-b --command="sudo docker ps"` should show `otp-server` running.
4. `curl http://35.197.42.97:8080/otp/routers/default/index/graphql` should return a GraphQL response.

## GCP Deployment

For production deployment on Google Cloud Platform:

### VM Setup

1. Create a Compute Engine VM with at least an `e2-standard-4` instance (4 vCPUs, 16 GB RAM):

```bash
gcloud compute instances create skilift-otp \
  --machine-type=e2-standard-4 \
  --zone=us-west1-b \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB
```

2. SSH into the instance and install Docker:

```bash
gcloud compute ssh skilift-otp --zone=us-west1-b

# Install Docker
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
# Log out and back in for group change to take effect
```

3. Clone the repo, acquire the data (steps above), and build the graph.

### Firewall Rule

Allow traffic to port 8080:

```bash
gcloud compute firewall-rules create allow-otp \
  --allow=tcp:8080 \
  --target-tags=otp-server \
  --description="Allow OTP2 GraphQL API access"

gcloud compute instances add-tags skilift-otp \
  --zone=us-west1-b \
  --tags=otp-server
```

### Running in Production

```bash
cd skilift/otp
docker compose up -d otp-server
```

The server will restart automatically on VM reboot due to the `restart: always` policy.

### Monitoring

Check server logs:

```bash
docker compose logs -f otp-server
```

Check server health by visiting `http://<VM_EXTERNAL_IP>:8080/` in a browser.
