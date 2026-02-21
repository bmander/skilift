package com.skilift.app.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng
import kotlinx.coroutines.delay

/**
 * Side-effect composable that manages programmatic camera movements:
 * - Snaps to [userLocation] (no animation) on the first non-null fix
 * - Animates to fit [selectedItinerary] bounds when it changes
 *
 * @param topInsetPx  Pixels occupied by overlays at the top of the map (status bar + input bar)
 */
@Composable
fun MapCameraEffects(
    mapViewportState: MapViewportState,
    userLocation: LatLng?,
    selectedItinerary: Itinerary?,
    topInsetPx: Double,
) {
    // Snap to current location (no animation) on first fix
    var hasInitiallyLocated by remember { mutableStateOf(false) }
    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasInitiallyLocated) {
            hasInitiallyLocated = true
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(userLocation.longitude, userLocation.latitude))
                zoom(14.0)
            }
        }
    }

    // Fly to route bounds when itineraries arrive or selection changes
    LaunchedEffect(selectedItinerary) {
        if (selectedItinerary != null) {
            val allPoints = selectedItinerary.legs
                .flatMap { it.geometry }
                .map { Point.fromLngLat(it.longitude, it.latitude) }
            if (allPoints.isNotEmpty()) {
                delay(100)
                mapViewportState.transitionToOverviewState(
                    overviewViewportStateOptions = OverviewViewportStateOptions.Builder()
                        .geometry(MultiPoint.fromLngLats(allPoints))
                        .padding(EdgeInsets(topInsetPx, 100.0, 400.0, 100.0))
                        .build(),
                    defaultTransitionOptions = DefaultViewportTransitionOptions.Builder()
                        .maxDurationMs(1500L)
                        .build()
                )
            }
        }
    }
}
