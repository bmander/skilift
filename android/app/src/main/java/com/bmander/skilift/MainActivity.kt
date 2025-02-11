package com.bmander.skilift

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bmander.skilift.ui.theme.SkiliftTheme
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.libraries.places.api.model.AutocompletePrediction
import kotlinx.coroutines.launch
import org.osmdroid.views.overlay.Marker

/**
 * Utility to create a simple “person” bitmap used by the MyLocation overlay.
 */
fun createLocationBitmap(size: Int, color: Int, borderFraction: Float = 0.25f): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size / 2f * (1f - borderFraction)
    canvas.drawCircle(centerX, centerY, size / 2f, Paint().apply {
        this.color = Color.WHITE
        style = Paint.Style.FILL
    })
    canvas.drawCircle(centerX, centerY, radius, Paint().apply {
        this.color = color
        style = Paint.Style.FILL
    })
    return bitmap
}

fun currentUserLocationBitmap(size: Int, borderFraction: Float = 0.25f): Bitmap {
    return createLocationBitmap(size, Color.BLUE, borderFraction)
}

fun originLocationBitmap(size: Int, borderFraction: Float = 0.25f): Bitmap {
    return createLocationBitmap(size, Color.GREEN, borderFraction)
}

fun destinationLocationBitmap(size: Int, borderFraction: Float = 0.25f): Bitmap {
    return createLocationBitmap(size, Color.RED, borderFraction)
}

sealed class TerminusLocation(open val latitude: Double, open val longitude: Double) {
    data class CurrentLocation(override val latitude: Double, override val longitude: Double) : TerminusLocation(latitude, longitude)
    data class Address(val address: String, override val latitude: Double, override val longitude: Double) : TerminusLocation(latitude, longitude)
    data class MapPoint(override val latitude: Double, override val longitude: Double) : TerminusLocation(latitude, longitude)
}

sealed class LocationEntryContents {
    data class TextEntry(val address: String) : LocationEntryContents()
    data class Resolved(val type: TerminusLocation) : LocationEntryContents()
}

// An extension to get a display string from an entry:
val LocationEntryContents.displayText: String
    get() = when (this) {
        is LocationEntryContents.TextEntry -> address
        is LocationEntryContents.Resolved -> when (val loc = type) {
            is TerminusLocation.CurrentLocation -> "Current Location (${loc.latitude}, ${loc.longitude})"
            is TerminusLocation.Address -> loc.address
            is TerminusLocation.MapPoint -> "Map Point (${loc.latitude}, ${loc.longitude})"
        }
    }

// Note: We now have two “entry” fields.
data class TripPlannerState(
    val startEntry: LocationEntryContents = LocationEntryContents.TextEntry(""),
    val endEntry: LocationEntryContents = LocationEntryContents.TextEntry(""),
    val isRouteDisplayed: Boolean = false
) {
    // Helper getters: if the entry is resolved, return its location; otherwise null.
    val startLocation: TerminusLocation?
        get() = (startEntry as? LocationEntryContents.Resolved)?.type
    val endLocation: TerminusLocation?
        get() = (endEntry as? LocationEntryContents.Resolved)?.type
}

class TripPlannerViewModel : ViewModel() {
    private val _uiState = mutableStateOf(TripPlannerState())
    val uiState = _uiState

    fun updateStartEntry(newEntry: LocationEntryContents) {
        _uiState.value = _uiState.value.copy(startEntry = newEntry)
    }

    fun updateEndEntry(newEntry: LocationEntryContents) {
        _uiState.value = _uiState.value.copy(endEntry = newEntry)
    }

    fun planTrip() {
        // Insert your trip planning logic here using uiState.value.startLocation and endLocation.
        _uiState.value = _uiState.value.copy(isRouteDisplayed = true)
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("com.bmander.skilift", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "com.bmander.skilift"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
        }

        enableEdgeToEdge()
        setContent {
            SkiliftTheme {
                TripPlannerApp()
            }
        }
    }
}

@Composable
fun TripPlannerApp(viewModel: TripPlannerViewModel = viewModel()) {
    val state = viewModel.uiState.value

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MapComponent(modifier = Modifier.fillMaxSize())

            if (!state.isRouteDisplayed) {
                FloatingDirectionsInput(
                    startEntry = state.startEntry,
                    endEntry = state.endEntry,
                    onStartEntryChange = { viewModel.updateStartEntry(it) },
                    onEndEntryChange = { viewModel.updateEndEntry(it) },
                    onPlanTrip = { viewModel.planTrip() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}


@Composable
fun FloatingDirectionsInput(
    startEntry: LocationEntryContents,
    endEntry: LocationEntryContents,
    onStartEntryChange: (LocationEntryContents) -> Unit,
    onEndEntryChange: (LocationEntryContents) -> Unit,
    onPlanTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val addressResolver = remember { AddressResolverService(context) }

    Surface(
        modifier = modifier,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LocationEntryField(
                label = "Start Location",
                value = startEntry,
                onValueChange = onStartEntryChange,
                onClear = { onStartEntryChange(LocationEntryContents.TextEntry("")) },
                addressResolver = addressResolver
            )
            Spacer(modifier = Modifier.height(8.dp))
            LocationEntryField(
                label = "End Location",
                value = endEntry,
                onValueChange = onEndEntryChange,
                onClear = { onEndEntryChange(LocationEntryContents.TextEntry("")) },
                addressResolver = addressResolver
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onPlanTrip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Plan Trip")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEntryField(
    label: String,
    value: LocationEntryContents,
    onValueChange: (LocationEntryContents) -> Unit,
    onClear: () -> Unit,
    addressResolver: AddressResolverService
) {
    when (value) {
        is LocationEntryContents.Resolved -> {
            // Render a read-only “token”
            OutlinedTextField(
                value = value.displayText,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors()
            )
        }
        is LocationEntryContents.TextEntry -> {
            var suggestions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

            // Update suggestions when the text changes.
            LaunchedEffect(value.address) {
                suggestions = addressResolver.getSuggestions(value.address)
                Log.d("LocationEntryField", "Suggestions: $suggestions")
            }

            var expanded by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            val scope = rememberCoroutineScope()

            Box {
                OutlinedTextField(
                    value = value.address,
                    onValueChange = { newText ->
                        onValueChange(LocationEntryContents.TextEntry(newText))
                        expanded = newText.isNotEmpty() && suggestions.isNotEmpty()
                    },
                    label = { Text(label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { /* Optionally react to focus changes */ },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    )
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(),
                    properties = PopupProperties(focusable = false)
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion.getFullText(null).toString()) },
                            onClick = {
                                scope.launch {
                                    try {
                                        val place = addressResolver.fetchPlaceDetails(suggestion.placeId)
                                        val latLng = place.latLng
                                        val addressText = place.address ?: suggestion.getFullText(null).toString()
                                        val resolvedLocation = TerminusLocation.Address(
                                            address = addressText,
                                            latitude = latLng?.latitude ?: 0.0,
                                            longitude = latLng?.longitude ?: 0.0
                                        )
                                        onValueChange(LocationEntryContents.Resolved(resolvedLocation))
                                        expanded = false
                                        focusManager.clearFocus()
                                    } catch (e: Exception) {
                                        // Handle error
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MapComponent(
    modifier: Modifier = Modifier,
    viewModel: TripPlannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(47.6062, -122.3321))
        }
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setPersonIcon(currentUserLocationBitmap(30))
            setPersonAnchor(0.5f, 0.5f)
        }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var longPressPoint by remember { mutableStateOf<GeoPoint?>(null) }

    val eventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint) = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                longPressPoint = p
                showContextMenu = true
                return true
            }
        })
    }

    val state = viewModel.uiState.value
    val startLocation = state.startLocation
    val endLocation = state.endLocation

    val startMarker = remember { Marker(mapView).apply { icon = BitmapDrawable(context.resources, originLocationBitmap(30)) } }
    val endMarker = remember { Marker(mapView).apply { icon = BitmapDrawable(context.resources, destinationLocationBitmap(30)) } }

    LaunchedEffect(startLocation) {
        when (startLocation) {
            is TerminusLocation.Address, is TerminusLocation.MapPoint -> {
                startMarker.title = "Start"
                startMarker.position = GeoPoint(startLocation.latitude, startLocation.longitude)
                mapView.overlays.add(startMarker)
            }
            else -> {}
        }
    }

    LaunchedEffect(endLocation) {
        when (endLocation) {
            is TerminusLocation.Address, is TerminusLocation.MapPoint -> {
                endMarker.title = "End"
                endMarker.position = GeoPoint(endLocation.latitude, endLocation.longitude)
                mapView.overlays.add(endMarker)
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        if (hasPermission) {
            locationOverlay.enableMyLocation()
            mapView.overlays.add(locationOverlay)
        }
        mapView.overlays.add(eventsOverlay)
        onDispose {
            locationOverlay.disableMyLocation()
            mapView.overlays.remove(locationOverlay)
            mapView.overlays.remove(eventsOverlay)
        }
    }

    if (showContextMenu && longPressPoint != null) {
        Dialog(
            onDismissRequest = { showContextMenu = false },
            properties = DialogProperties(dismissOnClickOutside = true)
        ) {
            LocationPicker(
                latitude = longPressPoint!!.latitude,
                longitude = longPressPoint!!.longitude,
                onSetAsStart = {
                    viewModel.updateStartEntry(
                        LocationEntryContents.Resolved(
                            TerminusLocation.MapPoint(longPressPoint!!.latitude, longPressPoint!!.longitude)
                        )
                    )
                    showContextMenu = false
                },
                onSetAsEnd = {
                    viewModel.updateEndEntry(
                        LocationEntryContents.Resolved(
                            TerminusLocation.MapPoint(longPressPoint!!.latitude, longPressPoint!!.longitude)
                        )
                    )
                    showContextMenu = false
                },
                onDismiss = { showContextMenu = false }
            )
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
