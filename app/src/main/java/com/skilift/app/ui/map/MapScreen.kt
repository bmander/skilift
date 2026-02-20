package com.skilift.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.map.components.BikeTriangleWidget
import com.skilift.app.ui.map.components.ElevationProfileChart
import com.skilift.app.ui.map.components.hasBikingElevationData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onItinerarySelected: (Int) -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val drawerHeight = (configuration.screenHeightDp / 3).dp
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(MapViewModel.CENTER_LONGITUDE, MapViewModel.CENTER_LATITUDE))
            zoom(12.0)
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            Column(
                modifier = Modifier
                    .height(drawerHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Bike/Transit Balance
                Text(
                    text = "Bike/Transit Balance",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "More Transit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "More Biking",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Slider(
                    value = uiState.bikeTransitBalance,
                    onValueChange = { viewModel.onSliderChanged(it) },
                    onValueChangeFinished = { viewModel.onSliderChangeFinished() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Cycling Optimization
                Text(
                    text = "Cycling Optimization",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Drag the point to balance fast, safe, and flat routing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                BikeTriangleWidget(
                    weights = uiState.triangleWeights,
                    onWeightsChanged = { viewModel.onTriangleWeightsChanged(it) },
                    onWeightsChangeFinished = { viewModel.onTriangleWeightsChangeFinished() },
                    modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Max Bike Speed
                Text(
                    text = "Max Bike Speed",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.1f m/s (%.1f mph)".format(
                        uiState.maxBikeSpeedMps,
                        uiState.maxBikeSpeedMps * 2.237
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Slider(
                    value = uiState.maxBikeSpeedMps,
                    onValueChange = { viewModel.onMaxBikeSpeedChanged(it) },
                    onValueChangeFinished = { viewModel.onMaxBikeSpeedChangeFinished() },
                    valueRange = 3.0f..8.0f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("3.0 m/s", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("8.0 m/s", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    ) { paddingValues ->
        // Track the sheet's hidden-state offset as a reference point.
        // This avoids coordinate-space assumptions: the visible sheet height
        // is simply the difference between the hidden offset and the current offset.
        val hiddenOffsetPx = remember { mutableFloatStateOf(0f) }

        val visibleSheetDp = try {
            val currentOffset = bottomSheetState.requireOffset()
            if (currentOffset > hiddenOffsetPx.floatValue) {
                hiddenOffsetPx.floatValue = currentOffset
            }
            val visiblePx = (hiddenOffsetPx.floatValue - currentOffset).coerceAtLeast(0f)
            with(density) { visiblePx.toDp() }
        } catch (_: IllegalStateException) {
            0.dp
        }

        val overlayBottomPadding = max(visibleSheetDp, navigationBarPadding)

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val fabHeightPx = remember { mutableIntStateOf(0) }
            val fabBottomPadding = statusBarPadding + 8.dp + with(density) { fabHeightPx.intValue.toDp() } + 8.dp
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                compass = {
                    Compass(
                        contentPadding = PaddingValues(
                            top = fabBottomPadding,
                            end = 16.dp
                        )
                    )
                },
                scaleBar = {
                    ScaleBar(
                        contentPadding = PaddingValues(
                            top = statusBarPadding + 8.dp,
                            start = 8.dp
                        )
                    )
                },
                style = { MapboxStandardStyle() },
                onMapClickListener = OnMapClickListener { point ->
                    viewModel.onMapTap(
                        com.skilift.app.domain.model.LatLng(point.latitude(), point.longitude())
                    )
                    true
                }
            ) {
                // Coverage area overlay: dim the area outside the data boundary
                PolygonAnnotation(
                    points = listOf(
                        // Outer ring (large area beyond the map view)
                        listOf(
                            Point.fromLngLat(-130.0, 40.0),
                            Point.fromLngLat(-115.0, 40.0),
                            Point.fromLngLat(-115.0, 55.0),
                            Point.fromLngLat(-130.0, 55.0),
                            Point.fromLngLat(-130.0, 40.0),
                        ),
                        // Inner ring (coverage area hole)
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

                // Origin marker
                uiState.origin?.let { origin ->
                    CircleAnnotation(
                        point = Point.fromLngLat(origin.longitude, origin.latitude)
                    ) {
                        circleRadius = 10.0
                        circleColor = Color(0xFF00796B)
                        circleStrokeWidth = 2.0
                        circleStrokeColor = Color.White
                    }
                }

                // Destination marker
                uiState.destination?.let { dest ->
                    CircleAnnotation(
                        point = Point.fromLngLat(dest.longitude, dest.latitude)
                    ) {
                        circleRadius = 10.0
                        circleColor = Color(0xFFD32F2F)
                        circleStrokeWidth = 2.0
                        circleStrokeColor = Color.White
                    }
                }

                // Route polylines
                if (uiState.itineraries.isNotEmpty()) {
                    val selectedItinerary = uiState.itineraries[uiState.selectedItineraryIndex]
                    selectedItinerary.legs.forEach { leg ->
                        if (leg.geometry.isNotEmpty()) {
                            PolylineAnnotation(
                                points = leg.geometry.map {
                                    Point.fromLngLat(it.longitude, it.latitude)
                                }
                            ) {
                                lineColor = colorForMode(leg.mode)
                                lineWidth = if (leg.mode == TransportMode.BICYCLE || leg.mode == TransportMode.WALK) 3.0 else 5.0
                            }
                        }
                    }
                }
            }

            // Floating settings button
            val isDrawerOpen = bottomSheetState.currentValue == SheetValue.Expanded
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (isDrawerOpen) {
                            bottomSheetState.hide()
                        } else {
                            bottomSheetState.expand()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = statusBarPadding + 8.dp,
                        end = 16.dp
                    )
                    .onGloballyPositioned { fabHeightPx.intValue = it.size.height },
                containerColor = if (isDrawerOpen)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = if (isDrawerOpen)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Tuning")
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = overlayBottomPadding)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                    )
                }

                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (uiState.itineraries.isNotEmpty()) {
                    val selectedItinerary = uiState.itineraries[uiState.selectedItineraryIndex]
                    if (selectedItinerary.hasBikingElevationData()) {
                        ElevationProfileChart(itinerary = selectedItinerary)
                    }

                    ItineraryCards(
                        itineraries = uiState.itineraries,
                        selectedIndex = uiState.selectedItineraryIndex,
                        onSelect = { viewModel.selectItinerary(it) },
                        onDetails = {
                            viewModel.prepareForDetails(it)
                            onItinerarySelected(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItineraryCards(
    itineraries: List<Itinerary>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDetails: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        itemsIndexed(itineraries) { index, itinerary ->
            Card(
                onClick = {
                    if (index == selectedIndex) onDetails(index) else onSelect(index)
                },
                modifier = Modifier.width(160.dp).padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == selectedIndex)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${itinerary.durationSeconds / 60} min",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        itinerary.legs.forEach { leg ->
                            Text(
                                text = modeEmoji(leg.mode),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == selectedIndex)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent
                    )
                }
            }
        }
    }
}

private fun colorForMode(mode: TransportMode): Color = when (mode) {
    TransportMode.BICYCLE -> Color(0xFF4CAF50)
    TransportMode.BUS -> Color(0xFF2196F3)
    TransportMode.RAIL -> Color(0xFF9C27B0)
    TransportMode.TRAM -> Color(0xFF9C27B0)
    TransportMode.FERRY -> Color(0xFF009688)
    TransportMode.WALK -> Color(0xFF9E9E9E)
}

private fun modeEmoji(mode: TransportMode): String = when (mode) {
    TransportMode.BICYCLE -> "\uD83D\uDEB2"
    TransportMode.BUS -> "\uD83D\uDE8C"
    TransportMode.RAIL -> "\uD83D\uDE86"
    TransportMode.TRAM -> "\uD83D\uDE8A"
    TransportMode.FERRY -> "\u26F4"
    TransportMode.WALK -> "\uD83D\uDEB6"
}
