package com.skilift.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    // Transit route decorations: endpoint circles and route code badges
    selectedItinerary.legs.forEach { leg ->
        val isTransit = leg.mode != TransportMode.BICYCLE && leg.mode != TransportMode.WALK
        if (isTransit && leg.geometry.isNotEmpty()) {
            val modeUi = leg.mode.toUi()

            // Circle at start of transit leg
            CircleAnnotation(
                point = Point.fromLngLat(leg.from.location.longitude, leg.from.location.latitude)
            ) {
                circleRadius = 6.0
                circleColor = modeUi.color
                circleStrokeWidth = 2.0
                circleStrokeColor = Color.White
            }

            // Circle at end of transit leg
            CircleAnnotation(
                point = Point.fromLngLat(leg.to.location.longitude, leg.to.location.latitude)
            ) {
                circleRadius = 6.0
                circleColor = modeUi.color
                circleStrokeWidth = 2.0
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
