package com.skilift.app.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.skilift.app.domain.model.LatLng

@Composable
fun MapMarkersLayer(
    origin: LatLng?,
    destination: LatLng?,
    originIsCurrentLocation: Boolean,
    destinationIsCurrentLocation: Boolean,
    elevationCursorPosition: LatLng? = null
) {
    // Origin marker (skip when using current location -- puck already shows it)
    if (!originIsCurrentLocation) {
        origin?.let { point ->
            CircleAnnotation(
                point = Point.fromLngLat(point.longitude, point.latitude)
            ) {
                circleRadius = 10.0
                circleColor = Color(0xFF00796B)
                circleStrokeWidth = 2.0
                circleStrokeColor = Color.White
            }
        }
    }

    // Destination marker (skip when using current location)
    if (!destinationIsCurrentLocation) {
        destination?.let { point ->
            CircleAnnotation(
                point = Point.fromLngLat(point.longitude, point.latitude)
            ) {
                circleRadius = 10.0
                circleColor = Color(0xFFD32F2F)
                circleStrokeWidth = 2.0
                circleStrokeColor = Color.White
            }
        }
    }

    // Elevation profile cursor
    elevationCursorPosition?.let { pos ->
        CircleAnnotation(
            point = Point.fromLngLat(pos.longitude, pos.latitude)
        ) {
            circleRadius = 8.0
            circleColor = Color(0xFF4CAF50)
            circleStrokeWidth = 2.0
            circleStrokeColor = Color.White
        }
    }
}
