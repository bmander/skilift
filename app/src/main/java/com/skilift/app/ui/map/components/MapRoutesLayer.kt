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
    selectedItineraryIndex: Int,
    selectedLegIndex: Int? = null
) {
    if (itineraries.isEmpty()) return

    val selectedItinerary = itineraries[selectedItineraryIndex]
    selectedItinerary.legs.forEachIndexed { legIndex, leg ->
        if (leg.geometry.isNotEmpty()) {
            val modeUi = leg.mode.toUi()
            val isSelectedBikeLeg = leg.mode == TransportMode.BICYCLE && legIndex == selectedLegIndex
            PolylineAnnotation(
                points = leg.geometry.map {
                    Point.fromLngLat(it.longitude, it.latitude)
                }
            ) {
                lineColor = modeUi.color
                lineWidth = when {
                    isSelectedBikeLeg -> 6.0
                    leg.mode == TransportMode.BICYCLE || leg.mode == TransportMode.WALK -> 3.0
                    else -> 5.0
                }
            }
        }
    }
}
