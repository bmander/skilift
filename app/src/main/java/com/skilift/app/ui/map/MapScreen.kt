package com.skilift.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import kotlin.math.sqrt
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.map.components.BikeTriangleWidget
import com.skilift.app.ui.map.components.ElevationProfileChart
import com.skilift.app.ui.map.components.LocationInputBar
import com.skilift.app.ui.map.components.TimePickerOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onItinerarySelected: (Int) -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hasLocationPermission = remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission.value =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission.value) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

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

                // Hill Avoidance
                Text(
                    text = "Hill Avoidance",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Normal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Strong",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Slider(
                    value = uiState.hillReluctance,
                    onValueChange = { viewModel.onHillReluctanceChanged(it) },
                    onValueChangeFinished = { viewModel.onHillReluctanceChangeFinished() },
                    valueRange = 1.0f..3.0f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
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
            val inputBarHeightPx = remember { mutableIntStateOf(0) }
            val inputBarHeightDp = with(density) { inputBarHeightPx.intValue.toDp() }
            val fabBottomPadding = statusBarPadding + 8.dp + inputBarHeightDp + 8.dp + with(density) { fabHeightPx.intValue.toDp() } + 8.dp

            // Screen-pixel position for the context menu anchor
            var menuScreenX by remember { mutableStateOf(0f) }
            var menuScreenY by remember { mutableStateOf(0f) }
            var mapboxMap by remember { mutableStateOf<MapboxMap?>(null) }

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
                onMapClickListener = OnMapClickListener { clickPoint ->
                    if (uiState.showContextMenu) {
                        viewModel.dismissContextMenu()
                    } else {
                        // Check if tap is near the user's location puck
                        val userLoc = uiState.userLocation
                        if (userLoc != null && mapboxMap != null) {
                            val map = mapboxMap!!
                            val puckScreen = map.pixelForCoordinate(
                                Point.fromLngLat(userLoc.longitude, userLoc.latitude)
                            )
                            val tapScreen = map.pixelForCoordinate(clickPoint)
                            val dx = tapScreen.x - puckScreen.x
                            val dy = tapScreen.y - puckScreen.y
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist < 40.0) {
                                menuScreenX = puckScreen.x.toFloat()
                                menuScreenY = puckScreen.y.toFloat()
                                viewModel.onPuckTapped()
                                return@OnMapClickListener true
                            }
                        }

                        // Check if tap is near a bike route segment
                        val map = mapboxMap
                        if (map != null && uiState.itineraries.isNotEmpty()) {
                            val selectedItinerary = uiState.itineraries[uiState.selectedItineraryIndex]
                            val tapScreen = map.pixelForCoordinate(clickPoint)
                            val tapX = tapScreen.x
                            val tapY = tapScreen.y
                            val thresholdSq = 30.0 * 30.0

                            var closestLegIndex: Int? = null
                            var closestDistSq = Double.MAX_VALUE

                            selectedItinerary.legs.forEachIndexed { legIndex, leg ->
                                if (leg.mode == TransportMode.BICYCLE && leg.geometry.size >= 2) {
                                    for (i in 0 until leg.geometry.size - 1) {
                                        val p1 = map.pixelForCoordinate(
                                            Point.fromLngLat(leg.geometry[i].longitude, leg.geometry[i].latitude)
                                        )
                                        val p2 = map.pixelForCoordinate(
                                            Point.fromLngLat(leg.geometry[i + 1].longitude, leg.geometry[i + 1].latitude)
                                        )
                                        val d = pointToSegmentDistSq(tapX, tapY, p1.x, p1.y, p2.x, p2.y)
                                        if (d < closestDistSq) {
                                            closestDistSq = d
                                            closestLegIndex = legIndex
                                        }
                                    }
                                }
                            }

                            viewModel.selectLeg(if (closestDistSq <= thresholdSq) closestLegIndex else null)
                        }
                    }
                    true
                },
                onMapLongClickListener = OnMapLongClickListener { point ->
                    mapboxMap?.let { map ->
                        val screenCoord = map.pixelForCoordinate(point)
                        menuScreenX = screenCoord.x.toFloat()
                        menuScreenY = screenCoord.y.toFloat()
                    }
                    viewModel.onMapLongPress(
                        com.skilift.app.domain.model.LatLng(point.latitude(), point.longitude())
                    )
                    true
                }
            ) {
                // Store MapboxMap reference for coordinate conversion
                MapEffect(Unit) { mapView ->
                    mapboxMap = mapView.mapboxMap
                }
                // Location puck
                MapEffect(hasLocationPermission.value) { mapView ->
                    mapView.location.updateSettings {
                        enabled = hasLocationPermission.value
                        pulsingEnabled = hasLocationPermission.value
                    }
                }

                // Track user location changes
                MapEffect(hasLocationPermission.value) { mapView ->
                    if (hasLocationPermission.value) {
                        mapView.location.addOnIndicatorPositionChangedListener { point ->
                            viewModel.onUserLocationChanged(
                                com.skilift.app.domain.model.LatLng(point.latitude(), point.longitude())
                            )
                        }
                    }
                }

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

                // Origin marker (skip when using current location — puck already shows it)
                if (!uiState.originIsCurrentLocation) {
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
                }

                // Destination marker (skip when using current location)
                if (!uiState.destinationIsCurrentLocation) {
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
                }

                // Route polylines
                if (uiState.itineraries.isNotEmpty()) {
                    val selectedItinerary = uiState.itineraries[uiState.selectedItineraryIndex]
                    selectedItinerary.legs.forEachIndexed { legIndex, leg ->
                        if (leg.geometry.isNotEmpty()) {
                            val isSelectedBikeLeg = leg.mode == TransportMode.BICYCLE && legIndex == uiState.selectedLegIndex
                            PolylineAnnotation(
                                points = leg.geometry.map {
                                    Point.fromLngLat(it.longitude, it.latitude)
                                }
                            ) {
                                lineColor = colorForMode(leg.mode)
                                lineWidth = when {
                                    isSelectedBikeLeg -> 6.0
                                    leg.mode == TransportMode.BICYCLE || leg.mode == TransportMode.WALK -> 3.0
                                    else -> 5.0
                                }
                            }
                        }
                    }
                }
            }

            // Long-press context menu
            if (uiState.showContextMenu) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset(menuScreenX.toInt(), menuScreenY.toInt())
                    }
                ) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { viewModel.dismissContextMenu() }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set as Start") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFF00796B), CircleShape)
                                )
                            },
                            onClick = {
                                if (uiState.isPuckMenu) viewModel.setOriginToCurrentLocation()
                                else viewModel.setOriginFromMenu()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as End") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFFD32F2F), CircleShape)
                                )
                            },
                            onClick = {
                                if (uiState.isPuckMenu) viewModel.setDestinationToCurrentLocation()
                                else viewModel.setDestinationFromMenu()
                            }
                        )
                    }
                }
            }

            // Location input bar
            LocationInputBar(
                origin = uiState.origin,
                destination = uiState.destination,
                originIsCurrentLocation = uiState.originIsCurrentLocation,
                destinationIsCurrentLocation = uiState.destinationIsCurrentLocation,
                timeSelection = uiState.timeSelection,
                onClearOrigin = { viewModel.clearOrigin() },
                onClearDestination = { viewModel.clearDestination() },
                onTimeRowClicked = { viewModel.onTimeRowClicked() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding + 8.dp)
                    .onGloballyPositioned { inputBarHeightPx.intValue = it.size.height }
            )

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
                        top = statusBarPadding + 8.dp + inputBarHeightDp + 8.dp,
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
                    val selectedLeg = uiState.selectedLegIndex?.let { selectedItinerary.legs.getOrNull(it) }
                    if (selectedLeg != null && selectedLeg.mode == TransportMode.BICYCLE && selectedLeg.elevationProfile.isNotEmpty()) {
                        ElevationProfileChart(leg = selectedLeg)
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

            // Time picker overlay
            if (uiState.showTimePicker) {
                TimePickerOverlay(
                    currentSelection = uiState.timeSelection,
                    onConfirm = { viewModel.onTimeSelectionConfirmed(it) },
                    onDismiss = { viewModel.onTimePickerDismissed() }
                )
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

private fun pointToSegmentDistSq(
    px: Double, py: Double,
    x1: Double, y1: Double,
    x2: Double, y2: Double
): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    if (dx == 0.0 && dy == 0.0) {
        val ddx = px - x1
        val ddy = py - y1
        return ddx * ddx + ddy * ddy
    }
    val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
    val tc = t.coerceIn(0.0, 1.0)
    val projX = x1 + tc * dx
    val projY = y1 + tc * dy
    val ddx = px - projX
    val ddy = py - projY
    return ddx * ddx + ddy * ddy
}
