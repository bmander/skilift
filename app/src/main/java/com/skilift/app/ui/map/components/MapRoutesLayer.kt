package com.skilift.app.ui.map.components

import androidx.compose.runtime.Composable
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.common.transport.toUi

@Composable
fun MapRoutesLayer(
    itineraries: List<Itinerary>,
    selectedItineraryIndex: Int
) {
    if (itineraries.isEmpty()) return

    val selectedItinerary = itineraries[selectedItineraryIndex]
    selectedItinerary.legs.forEach { leg ->
        if (leg.geometry.isNotEmpty()) {
            val modeUi = leg.mode.toUi()
            PolylineAnnotation(
                points = leg.geometry.map {
                    Point.fromLngLat(it.longitude, it.latitude)
                }
            ) {
                lineColor = modeUi.color
                lineWidth = if (leg.mode == TransportMode.BICYCLE || leg.mode == TransportMode.WALK) 3.0 else 5.0
            }
        }
    }
}
