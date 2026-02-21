package com.skilift.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
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

    // Transit route decorations: endpoint circles and route code badges
    itinerary.legs.forEach { leg ->
        val isTransit = leg.mode != TransportMode.BICYCLE && leg.mode != TransportMode.WALK
        if (isTransit && leg.geometry.isNotEmpty()) {
            val modeUi = leg.mode.toUi()

            // Circle at start of transit leg
            CircleAnnotation(
                point = Point.fromLngLat(leg.geometry.first().longitude, leg.geometry.first().latitude)
            ) {
                circleRadius = 6.0
                circleColor = modeUi.color
                circleStrokeWidth = 1.0
                circleStrokeColor = Color.White
            }

            // Circle at end of transit leg
            CircleAnnotation(
                point = Point.fromLngLat(leg.geometry.last().longitude, leg.geometry.last().latitude)
            ) {
                circleRadius = 6.0
                circleColor = modeUi.color
                circleStrokeWidth = 1.0
                circleStrokeColor = Color.White
            }

            // Route code badge at midpoint
            leg.routeShortName?.let { routeName ->
                val midPoint = leg.geometry[leg.geometry.size / 2]
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(Point.fromLngLat(midPoint.longitude, midPoint.latitude))
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = modeUi.color,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = routeName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
