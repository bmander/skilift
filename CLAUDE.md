# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

## Test Commands

```bash
# Run all unit tests
./gradlew testDebugUnitTest
```

Test libraries: JUnit 4, MockK, Kotlinx Coroutines Test, Ktor Client Mock.

## Project Overview

Skilift is a bike+transit trip planner for Seattle. Users tap origin/destination on a map, and the app returns blended cycling+transit itineraries from an OpenTripPlanner 2.7.0 backend. A slider controls the bike/transit balance, and a triangle widget controls cycling optimization (time/safety/flatness).

## Architecture

**Android app** (Kotlin/Compose, single-activity) talks directly to **OTP2's GraphQL API** (no custom backend). All user preferences stored on-device via DataStore.

### Data flow

```
MapScreen → MapViewModel → TripRepository → OtpGraphQlClient → OTP2 GraphQL (:8080)
                                ↑
                        PreferencesRepository ← UserPreferencesDataStore
```

### Key packages (`com.skilift.app`)

- **`di/`** — Hilt modules: `NetworkModule` (HttpClient, OTP client, base URL), `RepositoryModule` (binds TripRepository)
- **`data/remote/`** — `OtpGraphQlClient` builds and posts GraphQL queries via Ktor; `dto/OtpResponse.kt` has `@Serializable` response DTOs
- **`data/repository/`** — `TripRepositoryImpl` maps DTOs to domain models, decodes polylines, returns `Result<List<Itinerary>>`
- **`data/local/`** — `UserPreferencesDataStore` wraps DataStore Preferences
- **`data/SelectedItineraryHolder`** — Hilt singleton bridging MapViewModel → TripDetailsViewModel (stores selected itinerary for navigation)
- **`domain/model/`** — `Itinerary`, `Leg`, `Place`, `TransportMode`, `ElevationPoint`, `TripPreferences`
- **`ui/map/`** — `MapScreen` (Mapbox map, itinerary cards, bottom sheet with controls), `MapViewModel` (trip search, slider mapping, coverage validation)
- **`ui/map/components/`** — `BikeTriangleWidget`, `ElevationProfileChart`, `PreferenceSlider`
- **`ui/tripdetails/`** — `TripDetailsScreen` (leg-by-leg timeline with times, modes, distances)
- **`ui/navigation/`** — `SkiliftNavGraph` (2 routes: `map`, `trip_details/{itineraryIndex}`)
- **`util/PolylineDecoder`** — Decodes Google polyline encoding to `List<LatLng>`

### Slider-to-OTP mapping

The bike/transit balance slider (0.0–1.0) maps to two OTP parameters in `MapViewModel.mapSliderToOtpPreferences()`:
- `reluctance = 5.0 - (4.5 * balance)` — higher = avoid biking
- `boardCost = (1200 - (1140 * balance)).toInt()` — higher = avoid boarding transit

### Screen navigation

MapScreen → (tap selected itinerary card) → MapViewModel.prepareForDetails() stores itinerary in SelectedItineraryHolder → navigate to TripDetailsScreen → TripDetailsViewModel reads from holder on init.

## Secrets

Mapbox tokens are **not** in source code:
- **`MAPBOX_ACCESS_TOKEN`** (public, `pk.` prefix) — injected via `resValue` in `app/build.gradle.kts` from gradle property
- **`MAPBOX_DOWNLOADS_TOKEN`** (secret, `sk.` prefix) — read in `settings.gradle.kts` for Maven auth

Both live in `~/.gradle/gradle.properties` for local dev and as GitHub secrets for CI.

## OTP Backend

Config files in `otp/`. Data sources: King County Metro GTFS, Sound Transit rail GTFS, OSM (clipped to Seattle bbox), USGS DEM elevation.

### Deployment (three Cloud Build pipelines, run in order)

```bash
# 1. Download/update source data (conditional, checks ETags)
gcloud builds submit --config=otp/cloudbuild-prepare-data.yaml .

# 2. Build graph (skips if inputs unchanged)
gcloud builds submit --config=otp/cloudbuild-build-graph.yaml --no-source

# 3. Start/restart server on VM
gcloud builds submit --config=otp/cloudbuild-start-server.yaml --no-source
```

The region bounding box is parameterized in `gradle.properties` (`REGION_BBOX_*`) and used by both the app (coverage validation) and Cloud Build (OSM clipping).
