package com.skilift.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import kotlin.math.sqrt
import com.skilift.app.ui.map.components.GeocoderSearchOverlay
import com.skilift.app.ui.map.components.LocationInputBar
import com.skilift.app.ui.map.components.MapContextMenu
import com.skilift.app.ui.map.components.MapCoverageOverlay
import com.skilift.app.ui.map.components.MapMarkersLayer
import com.skilift.app.ui.map.components.MapRoutesLayer
import com.skilift.app.ui.map.components.MapStatusOverlay
import com.skilift.app.ui.map.components.MapTuningSheet
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

    var hasInitiallyLocated by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.userLocation) {
        val loc = uiState.userLocation
        if (loc != null && !hasInitiallyLocated) {
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(loc.longitude, loc.latitude))
                    .zoom(14.0)
                    .build()
            )
            hasInitiallyLocated = true
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            MapTuningSheet(
                bikeTransitBalance = uiState.bikeTransitBalance,
                onSliderChanged = { viewModel.onSliderChanged(it) },
                onSliderChangeFinished = { viewModel.onSliderChangeFinished() },
                triangleWeights = uiState.triangleWeights,
                onWeightsChanged = { viewModel.onTriangleWeightsChanged(it) },
                onWeightsChangeFinished = { viewModel.onTriangleWeightsChangeFinished() },
                hillReluctance = uiState.hillReluctance,
                onHillReluctanceChanged = { viewModel.onHillReluctanceChanged(it) },
                onHillReluctanceChangeFinished = { viewModel.onHillReluctanceChangeFinished() },
                maxBikeSpeedMps = uiState.maxBikeSpeedMps,
                onMaxBikeSpeedChanged = { viewModel.onMaxBikeSpeedChanged(it) },
                onMaxBikeSpeedChangeFinished = { viewModel.onMaxBikeSpeedChangeFinished() },
                height = drawerHeight
            )
        },
    ) { paddingValues ->
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
                MapEffect(Unit) { mapView ->
                    mapboxMap = mapView.mapboxMap
                }
                MapEffect(hasLocationPermission.value) { mapView ->
                    mapView.location.updateSettings {
                        enabled = hasLocationPermission.value
                        pulsingEnabled = hasLocationPermission.value
                    }
                }
                MapEffect(hasLocationPermission.value) { mapView ->
                    if (hasLocationPermission.value) {
                        mapView.location.addOnIndicatorPositionChangedListener { point ->
                            viewModel.onUserLocationChanged(
                                com.skilift.app.domain.model.LatLng(point.latitude(), point.longitude())
                            )
                        }
                    }
                }

                MapCoverageOverlay()
                MapMarkersLayer(
                    origin = uiState.origin,
                    destination = uiState.destination,
                    originIsCurrentLocation = uiState.originIsCurrentLocation,
                    destinationIsCurrentLocation = uiState.destinationIsCurrentLocation
                )
                MapRoutesLayer(
                    itineraries = uiState.itineraries,
                    selectedItineraryIndex = uiState.selectedItineraryIndex
                )
            }

            if (uiState.showContextMenu) {
                MapContextMenu(
                    screenX = menuScreenX,
                    screenY = menuScreenY,
                    onDismiss = { viewModel.dismissContextMenu() },
                    onSetOrigin = {
                        if (uiState.isPuckMenu) viewModel.setOriginToCurrentLocation()
                        else viewModel.setOriginFromMenu()
                    },
                    onSetDestination = {
                        if (uiState.isPuckMenu) viewModel.setDestinationToCurrentLocation()
                        else viewModel.setDestinationFromMenu()
                    }
                )
            }

            LocationInputBar(
                origin = uiState.origin,
                destination = uiState.destination,
                originName = uiState.originName,
                destinationName = uiState.destinationName,
                originIsCurrentLocation = uiState.originIsCurrentLocation,
                destinationIsCurrentLocation = uiState.destinationIsCurrentLocation,
                timeSelection = uiState.timeSelection,
                onClearOrigin = { viewModel.clearOrigin() },
                onClearDestination = { viewModel.clearDestination() },
                onOriginRowClicked = { viewModel.onLocationRowClicked(SearchTarget.ORIGIN) },
                onDestinationRowClicked = { viewModel.onLocationRowClicked(SearchTarget.DESTINATION) },
                onTimeRowClicked = { viewModel.onTimeRowClicked() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding + 8.dp)
                    .onGloballyPositioned { inputBarHeightPx.intValue = it.size.height }
            )

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

            MapStatusOverlay(
                isLoading = uiState.isLoading,
                error = uiState.error,
                itineraries = uiState.itineraries,
                selectedItineraryIndex = uiState.selectedItineraryIndex,
                onSelectItinerary = { viewModel.selectItinerary(it) },
                onItineraryDetails = {
                    viewModel.prepareForDetails(it)
                    onItinerarySelected(it)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = overlayBottomPadding)
            )

            if (uiState.showTimePicker) {
                TimePickerOverlay(
                    currentSelection = uiState.timeSelection,
                    onConfirm = { viewModel.onTimeSelectionConfirmed(it) },
                    onDismiss = { viewModel.onTimePickerDismissed() }
                )
            }

            // Geocoder search overlay
            if (uiState.showGeocoderSearch) {
                GeocoderSearchOverlay(
                    query = uiState.geocoderQuery,
                    results = uiState.geocoderResults,
                    isLoading = uiState.isGeocoderLoading,
                    onQueryChanged = { viewModel.onGeocoderQueryChanged(it) },
                    onResultSelected = { viewModel.onGeocoderResultSelected(it) },
                    onDismiss = { viewModel.onGeocoderSearchDismissed() }
                )
            }
        }
    }
}
