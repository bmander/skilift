## Expected behavior

When reconstructing a leg's full elevation profile by concatenating per-step `elevationProfile` arrays (offsetting each step by the cumulative `step.distance`), the resulting distances should be monotonically increasing.

## Observed behavior

There are two related issues that produce non-monotonic cumulative distances when concatenating per-step elevation profiles:

### Issue 1: `elevationProfileComponent.distance` exceeds `step.distance`

A step's last `elevationProfileComponent.distance` can significantly exceed the step's own `distance` field. When the next step's profile is offset by `step.distance`, its first point lands *before* the previous step's last point.

Example from a BICYCLE leg (`distance: 5752.26`):

| Step | `step.distance` | Last elevation profile `distance` | Ratio |
|------|-----------------|-----------------------------------|-------|
| 0    | 9.41            | 11.36                             | 1.21x |
| 0 (other route) | 24.92 | 47.88                            | 1.92x |

In the second example, the elevation profile extends to nearly **2x** the step distance. When the next step is offset by `step.distance` (24.92), its first point at cumulative distance 24.92 falls far behind the previous step's last point at 47.88.

Non-monotonic points found at step boundaries (6 of 601 points in one leg):

| Index | Previous distance | Current distance | Delta |
|-------|-------------------|------------------|-------|
| 4     | 11.36             | 9.41             | -1.95 |
| 203   | 1883.10           | 1883.09          | -0.01 |
| 296   | 2794.98           | 2794.97          | -0.01 |
| 305   | 2838.84           | 2838.83          | -0.01 |
| 529   | 4952.30           | 4952.28          | -0.02 |

### Issue 2: Large backward jumps across step boundaries

Even after compensating for Issue 1 by using `maxOf(step.distance, maxElevationProfileDistance)` as the step offset, some routes still exhibit large backward jumps. For example, in a multi-leg itinerary (bike-bus-bike), a bike leg's elevation profile showed a cumulative distance jumping from 172.48 back to 0.00 at a step boundary — a 172m backward jump.

This suggests that in some cases the relationship between `step.distance` and `elevationProfileComponent.distance` is inconsistent in ways beyond simple overshoot.

### Impact

These issues make it difficult to render a smooth elevation profile chart. Non-monotonic x-coordinates cause the rendered polyline to zigzag backward, and sorting the points by distance to fix the x-axis places elevation values at incorrect positions, causing vertical jitter.

### Workaround

Drop any elevation profile point whose cumulative distance does not strictly exceed the maximum distance seen so far:

```kotlin
var maxDistSoFar = -1.0
for (step in steps) {
    for (component in step.elevationProfile) {
        val dist = cumulativeOffset + component.distance
        if (dist > maxDistSoFar) {
            result.add(ElevationPoint(dist, component.elevation))
            maxDistSoFar = dist
        }
        // else: discard this point
    }
    cumulativeOffset += maxOf(step.distance, maxDistInStep)
}
```

## Version of OTP used (exact commit hash or JAR name)

Unknown — queried via a deployed instance's GraphQL API. The instance was built from OTP2 (dev-2.x branch).

## Data sets in use (links to GTFS and OSM PBF files)

- OSM: Washington State extract
- GTFS: King County Metro, Sound Transit, and other Puget Sound agencies
- Elevation: USGS DEM GeoTIFF

## Command line used to start OTP

N/A — observed via GraphQL API on a deployed instance.

## Router config and graph build config JSON

Not available (deployed instance).

## Steps to reproduce the problem

1. Build an OTP2 graph with elevation data (USGS DEM GeoTIFF)
2. Query a BICYCLE leg via GraphQL requesting `steps { distance elevationProfile { distance elevation } }`
3. Concatenate per-step elevation profiles, offsetting each step by cumulative `step.distance`
4. Check for non-monotonic cumulative distances

Example query:

```graphql
{
  planConnection(
    origin: { location: { coordinate: { latitude: 47.6062, longitude: -122.3321 } } }
    destination: { location: { coordinate: { latitude: 47.65, longitude: -122.35 } } }
    first: 3
    modes: {
      direct: [BICYCLE]
      transit: {
        access: [BICYCLE]
        egress: [BICYCLE]
        transfer: [BICYCLE]
        transit: [{ mode: BUS }]
      }
    }
  ) {
    edges {
      node {
        legs {
          mode
          distance
          steps {
            distance
            elevationProfile { distance elevation }
          }
        }
      }
    }
  }
}
```

Verification script (Python):

```python
cumDist = 0.0
maxSoFar = -1.0
non_monotonic = []
for step in leg['steps']:
    for p in step.get('elevationProfile', []):
        d = cumDist + p['distance']
        if d < maxSoFar:
            non_monotonic.append((maxSoFar, d))
        else:
            maxSoFar = d
    cumDist += step['distance']
print(f"Non-monotonic points: {len(non_monotonic)}")
for prev, cur in non_monotonic[:10]:
    print(f"  {prev:.2f} -> {cur:.2f} (delta={cur-prev:.2f})")
```
