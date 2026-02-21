package com.skilift.app.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.common.transport.toUi

@Composable
fun MapRoutesLayer(
    itineraries: List<Itinerary>,
    selectedItineraryIndex: Int,
    selectedLegIndex: Int? = null
) {
    if (itineraries.isEmpty()) return

    val selectedItinerary = itineraries[selectedItineraryIndex]

    // Base route lines — only recompose when the itinerary changes, not on leg selection
    MapRouteLines(selectedItinerary)

    // Selection highlight — separate composable so only this recomposes on tap
    if (selectedLegIndex != null) {
        val leg = selectedItinerary.legs.getOrNull(selectedLegIndex)
        if (leg != null && leg.mode == TransportMode.BICYCLE && leg.geometry.isNotEmpty()) {
            val points = remember(leg) {
                leg.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
            }
            PolylineAnnotation(points = points) {
                lineColor = leg.mode.toUi().color
                lineWidth = 6.0
            }
        }
    }
}

@Composable
private fun MapRouteLines(itinerary: Itinerary) {
    itinerary.legs.forEach { leg ->
        if (leg.geometry.isNotEmpty()) {
            val modeUi = leg.mode.toUi()
            val points = remember(leg) {
                leg.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
            }
            PolylineAnnotation(points = points) {
                lineColor = modeUi.color
                lineWidth = if (leg.mode == TransportMode.BICYCLE || leg.mode == TransportMode.WALK) 3.0 else 5.0
            }
        }
    }
}
