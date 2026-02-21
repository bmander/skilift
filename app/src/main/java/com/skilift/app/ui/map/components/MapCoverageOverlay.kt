package com.skilift.app.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.skilift.app.ui.map.MapViewModel

@Composable
fun MapCoverageOverlay() {
    // Dim area outside the data boundary
    PolygonAnnotation(
        points = listOf(
            listOf(
                Point.fromLngLat(-130.0, 40.0),
                Point.fromLngLat(-115.0, 40.0),
                Point.fromLngLat(-115.0, 55.0),
                Point.fromLngLat(-130.0, 55.0),
                Point.fromLngLat(-130.0, 40.0),
            ),
            listOf(
                Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_SOUTH),
                Point.fromLngLat(MapViewModel.COVERAGE_EAST, MapViewModel.COVERAGE_SOUTH),
                Point.fromLngLat(MapViewModel.COVERAGE_EAST, MapViewModel.COVERAGE_NORTH),
                Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_NORTH),
                Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_SOUTH),
            )
        )
    ) {
        fillColor = Color(0x30000000)
        fillOutlineColor = Color(0x60000000)
    }

    // Coverage area border
    PolylineAnnotation(
        points = listOf(
            Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_SOUTH),
            Point.fromLngLat(MapViewModel.COVERAGE_EAST, MapViewModel.COVERAGE_SOUTH),
            Point.fromLngLat(MapViewModel.COVERAGE_EAST, MapViewModel.COVERAGE_NORTH),
            Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_NORTH),
            Point.fromLngLat(MapViewModel.COVERAGE_WEST, MapViewModel.COVERAGE_SOUTH),
        )
    ) {
        lineColor = Color(0x80000000)
        lineWidth = 1.5
    }
}
