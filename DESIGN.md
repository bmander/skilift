# Skilift Design Document

## Project Overview

Skilift is a bike+transit trip planner for Seattle. Seattle's hilly terrain and extensive transit network create an underserved trip planning modality: combining bicycle and public transit. Riders can bike to transit stops, bring bikes aboard (where allowed), use express routes to gain hilltop access, and enjoy downhill rides to destinations.

Existing trip planners treat cycling and transit as separate modes. Skilift treats them as a unified journey, letting users express how much biking versus transit they prefer on a continuous slider scale. The result is a set of itineraries that blend the two modes in proportion to the rider's comfort and fitness level.

## Architecture

```
+---------------------+          +---------------------+
|   Android App       |          |   OTP2 Server       |
|   (Kotlin/Compose)  | -------> |   (Docker)          |
|                     |  GraphQL |                     |
|   - Mapbox map      |  over    |   - GTFS feeds      |
|   - Ktor client     |  HTTP    |   - OSM street net  |
|   - DataStore prefs |          |   - DEM elevation   |
+---------------------+          +---------------------+
```

- **No custom API.** The Android app calls OTP2's GraphQL endpoint directly.
- **All user data stays on-device.** Preferences (slider position, saved places) are stored in DataStore Preferences. No accounts, no telemetry.
- **OTP2 runs as a standalone Docker container.** It can be self-hosted on a small cloud VM or run locally for development.

## Tech Stack

### Android App

| Component | Library |
|---|---|
| UI toolkit | Compose BOM (latest stable) |
| Design system | Material3 |
| Map | Mapbox Maps Compose (requires free API key) |
| HTTP client | Ktor Client (Android engine) |
| Local storage | DataStore Preferences |
| Dependency injection | Hilt + KSP |
| Navigation | Navigation Compose |

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35
- **Language:** Kotlin (100%)
- **Build system:** Gradle with version catalogs

### Backend

| Component | Details |
|---|---|
| Trip planner | OpenTripPlanner 2.7.0 |
| Container | Docker / docker-compose |
| Transit data | GTFS (King County Metro, Sound Transit) |
| Street network | OpenStreetMap (Geofabrik Washington extract, clipped) |
| Elevation | USGS DEM GeoTIFF |

## OTP2 Configuration

OTP2 runs inside Docker with two phases:

1. **Build phase** (`otp-build`): Ingests GTFS feeds, OSM data, and DEM tiles to build a routing graph. This is a one-time operation (re-run when data updates). Requires ~12 GB heap.
2. **Serve phase** (`otp-server`): Loads the pre-built graph and exposes the GraphQL API on port 8080. Requires ~8 GB heap.

Data files placed in `otp/data/`:

| File | Source |
|---|---|
| `kcm-gtfs.zip` | King County Metro GTFS feed |
| `st-gtfs.zip` | Sound Transit GTFS feed |
| `seattle.osm.pbf` | OSM extract clipped to Seattle area |
| `seattle-dem.tif` | USGS DEM GeoTIFF covering Seattle |

Configuration files placed in `otp/data/` (copied from `otp/` before build):

| File | Purpose |
|---|---|
| `build-config.json` | References local GTFS, OSM, and DEM files |
| `otp-config.json` | Enables GraphQL API, Debug UI, floating bike |
| `router-config.json` | Default routing parameters for bicycle+transit |

## App Structure

```
com.skilift.app/
  di/                     # Hilt modules
    NetworkModule.kt
    RepositoryModule.kt
  data/
    remote/
      OtpGraphQlClient.kt   # Ktor-based GraphQL client
      dto/
        OtpResponse.kt      # @Serializable response DTOs
    local/
      UserPreferencesDataStore.kt
    repository/
      TripRepository.kt       # Interface
      TripRepositoryImpl.kt   # Maps DTOs to domain models
      PreferencesRepository.kt
  domain/
    model/
      LatLng.kt
      TransportMode.kt
      Place.kt
      Leg.kt
      Itinerary.kt
      TripPreferences.kt
  ui/
    theme/
      Theme.kt
      Color.kt
      Type.kt
    map/
      MapScreen.kt
      MapViewModel.kt
      components/
        PreferenceSlider.kt
    tripdetails/
      TripDetailsScreen.kt
      TripDetailsViewModel.kt
    settings/
      SettingsScreen.kt
      SettingsViewModel.kt
    navigation/
      SkiliftNavGraph.kt
      Screen.kt
  util/
    PolylineDecoder.kt
  SkiliftApp.kt            # Application class (Hilt entry point)
  MainActivity.kt          # Single activity
```

## Screens

### 1. Map Screen (Start/Destination)

The primary screen. Contains:

- **Mapbox map** filling the screen, using Mapbox Streets style.
- **Default center:** Seattle (47.6062, -122.3321), zoom level 12.
- **Origin/destination input:** Tap the map to alternate between setting origin and destination markers.
- **Preference slider:** Horizontal slider at the bottom of the screen, ranging from 0.0 (all transit) to 1.0 (max biking). The slider label updates dynamically: "More Transit" on the left, "More Biking" on the right.
- **Route display:** Once origin and destination are set, routes are fetched automatically. Up to 5 itineraries are shown as polylines on the map, color-coded by mode. The user can tap a route to select it and see details.
- **Trip summary cards:** A horizontally scrollable row of cards at the bottom showing total time and mode icons for each itinerary.

### 2. Trip Details Screen

Shown when the user selects an itinerary. Contains:

- **Header:** Itinerary number and total duration.
- **Leg timeline:** Vertical timeline of `LegTimelineItem` composables, each showing:
  - Mode icon and color indicator
  - Duration and distance
  - Route name/number for transit legs
  - Headsign for transit legs
  - Departure/arrival times and place names
- **Back button** returns to the map with the route still displayed.

### 3. Settings Screen

Accessible from a gear icon on the Map Screen. Contains:

- **Default bike/transit balance:** Slider matching the map screen slider, sets the default position.
- **Cycling optimization:** Radio group (Quick / Safe Streets / Flat Streets).
- **Max bike speed:** Slider (3.0-8.0 m/s) with mph conversion display.
- **About section:** App version, map/routing/transit data attributions.

## GraphQL Query Structure

Skilift uses the OTP2 `planConnection` query with `modes` configured for bicycle access/egress combined with transit:

```graphql
{
  planConnection(
    origin: { coordinate: { latitude: $lat, longitude: $lon } }
    destination: { coordinate: { latitude: $lat, longitude: $lon } }
    first: $numItineraries
    modes: {
      directMode: BICYCLE
      transitModes: [
        { mode: BUS }
        { mode: RAIL }
        { mode: TRAM }
        { mode: FERRY }
      ]
      accessMode: BICYCLE
      egressMode: BICYCLE
      transferMode: BICYCLE
    }
    preferences: {
      street: {
        bicycle: {
          reluctance: $bicycleReluctance
          boardCost: $bicycleBoardCost
          speed: $bicycleSpeed
          optimization: SAFE_STREETS
        }
      }
    }
  ) {
    edges {
      node {
        duration
        startTime
        endTime
        walkDistance
        legs {
          mode
          startTime
          endTime
          duration
          distance
          from { name lat lon stop { code platformCode } }
          to { name lat lon stop { code platformCode } }
          route { shortName longName }
          headsign
          legGeometry { points }
        }
      }
    }
  }
}
```

### Key design decisions

- **`accessMode: BICYCLE` and `egressMode: BICYCLE`**: The rider bikes to the first transit stop and from the last transit stop to the destination.
- **`directMode: BICYCLE`**: Also returns a pure-cycling option for comparison.
- **`transferMode: BICYCLE`**: Transfers between transit legs are done by bike, allowing transfers between distant stops.
- **Transit modes include BUS, RAIL, TRAM, and FERRY**: Covers King County Metro buses, Sound Transit Link light rail and Sounder commuter rail, streetcars, and Washington State Ferries.

## Preference Slider Mapping

The slider on the Map Screen ranges from 0.0 to 1.0 and controls two OTP2 parameters:

| Slider Value | Meaning | `bicycle.reluctance` | `bicycle.boardCost` |
|---|---|---|---|
| 0.0 | All transit | 5.0 (high reluctance) | 1200 (high cost to board) |
| 0.5 | Balanced | 2.75 | 630 |
| 1.0 | Max biking | 0.5 (low reluctance) | 60 (low cost to board) |

The mapping is linear interpolation:

```
reluctance = 5.0 - (sliderValue * 4.5)    // 5.0 down to 0.5
boardCost  = 1200 - (sliderValue * 1140)   // 1200 down to 60
```

- **High reluctance + high board cost** (slider left): OTP2 penalizes cycling distance and makes boarding cheap, favoring transit legs. The rider bikes short distances to/from stops.
- **Low reluctance + low board cost** (slider right): OTP2 allows longer bike legs and penalizes boarding less. The rider bikes more of the journey directly.

## Route Rendering

Routes returned by OTP2 contain encoded polylines (Google Polyline Encoding) in each leg's `legGeometry.points` field.

### Decoding

Each leg's polyline is decoded by `PolylineDecoder` into a list of `LatLng` points and drawn as a `PolylineAnnotation` on the Mapbox map.

### Color by Mode

| Mode | Color | Hex |
|---|---|---|
| BICYCLE | Green | `#4CAF50` |
| BUS | Blue | `#2196F3` |
| RAIL | Purple | `#9C27B0` |
| TRAM | Purple | `#9C27B0` |
| WALK | Gray | `#9E9E9E` |
| FERRY | Teal | `#009688` |

Polyline width: 5dp for transit legs, 3dp for bike/walk legs.

### Markers

- **Origin:** Teal circle with white stroke (`CircleAnnotation`)
- **Destination:** Red circle with white stroke (`CircleAnnotation`)

## Theme

Material3 with a teal primary:

| Role | Color | Hex |
|---|---|---|
| Primary | Teal | `#00796B` |
| Primary Container | Light Teal | `#48A999` |
| Secondary | Bike Green | `#4CAF50` |
| Surface | Near White | `#FAFAFA` |
| On Surface | Dark Gray | `#1C1B1F` |

## Map Configuration

- **Tile provider:** Mapbox Streets (requires free API key).
- **Default center:** Seattle -- latitude 47.6062, longitude -122.3321.
- **Default zoom:** 12 (shows greater Seattle area).
- **Interaction:** Tap alternates between setting origin (teal) and destination (red).
- **Setup:** Requires `MAPBOX_DOWNLOADS_TOKEN` in `~/.gradle/gradle.properties` and a public access token at runtime.

## Implementation Phases

### Phase 0: Project Bootstrap -- DONE

- [x] Gradle version catalogs (`gradle/libs.versions.toml`)
- [x] Root `build.gradle.kts` and `settings.gradle.kts`
- [x] App module `build.gradle.kts` with all dependencies (Compose, Mapbox, Ktor, Hilt, DataStore)
- [x] `AndroidManifest.xml` with permissions (INTERNET, LOCATION) and network security config
- [x] `SkiliftApp.kt` (`@HiltAndroidApp`) and `MainActivity.kt` (`@AndroidEntryPoint`)
- [x] Resource files (`strings.xml`, `themes.xml`, `colors.xml`)
- [x] `.gitignore` (Android-appropriate, excludes OTP data)
- [x] Removed legacy `.devcontainer/`
- [x] Gradle wrapper (`gradle-wrapper.properties` for Gradle 8.9)
- [x] ProGuard rules for Ktor, kotlinx.serialization, Hilt

### Phase 1: OTP2 Backend Setup -- DONE

- [x] `otp/docker-compose.yml` (build + serve services)
- [x] `otp/build-config.json` (GTFS feeds, OSM, DEM references)
- [x] `otp/otp-config.json` (GraphQL API, Debug UI, FloatingBike)
- [x] `otp/router-config.json` (bicycle defaults)
- [x] `otp/README.md` (data acquisition, build, run, GCP deployment instructions)
- [ ] Download actual GTFS/OSM/DEM data files (manual step)
- [ ] Build graph and verify server (manual step)

### Phase 2: Core Architecture -- DONE

- [x] Domain models (`LatLng`, `TransportMode`, `Place`, `Leg`, `Itinerary`, `TripPreferences`)
- [x] GraphQL response DTOs (`OtpResponse.kt` with `@Serializable` classes)
- [x] `OtpGraphQlClient` (Ktor-based, builds `planConnection` query)
- [x] `UserPreferencesDataStore` (DataStore Preferences wrapper)
- [x] `TripRepository` interface and `TripRepositoryImpl` (DTO-to-domain mapping)
- [x] `PreferencesRepository` (exposes Flow-based preferences)
- [x] `NetworkModule` (Hilt: Json, HttpClient, OTP base URL, OtpGraphQlClient)
- [x] `RepositoryModule` (Hilt: binds TripRepositoryImpl to TripRepository)
- [x] Navigation (`Screen.kt` sealed class, `SkiliftNavGraph.kt` with NavHost)
- [x] Theme (`Color.kt`, `Type.kt`, `Theme.kt` with Material3 teal scheme)

### Phase 3: Map Integration -- DONE

- [x] `MapScreen.kt` with Mapbox `MapboxMap` composable
- [x] Default center on Seattle, zoom 12
- [x] Tap-to-set origin/destination with `CircleAnnotation` markers
- [x] Auto-search when both points are set

### Phase 4: Trip Planning -- DONE

- [x] `MapViewModel` with `mapSliderToOtpPreferences()` mapping
- [x] GraphQL query construction with bicycle reluctance/boardCost/speed parameters
- [x] Route polylines rendered as `PolylineAnnotation`, color-coded by mode
- [x] Itinerary selection cards in horizontal `LazyRow`
- [x] Loading and error states

### Phase 5: Preferences & Polish -- DONE

- [x] `SettingsViewModel` and `SettingsScreen` with:
  - [x] Default bike/transit balance slider
  - [x] Cycling optimization radio group (Quick / Safe Streets / Flat Streets)
  - [x] Max bike speed slider (3.0-8.0 m/s with mph display)
  - [x] About/attribution section
- [x] `PreferenceSlider` reusable component
- [x] `TripDetailsScreen` with itinerary header and `LegTimelineItem` composable
- [x] `PolylineDecoder` utility

### Phase 6: Testing -- NOT STARTED

- [ ] Unit tests for `PolylineDecoder` (known encoded strings)
- [ ] Unit tests for `mapSliderToOtpPreferences()` (boundary values)
- [ ] Unit tests for `TripRepositoryImpl` (DTO-to-domain mapping)
- [ ] Integration tests for `OtpGraphQlClient` (Ktor MockEngine)
- [ ] Manual verification against running OTP2 instance
