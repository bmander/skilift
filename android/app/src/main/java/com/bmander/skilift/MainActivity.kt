package com.bmander.skilift

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
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

/**
 * Utility to create a simple “person” bitmap used by the MyLocation overlay.
 */
fun currentUserLocationBitmap(size: Int, borderFraction: Float = 0.25f): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size / 2f * (1f - borderFraction)
    canvas.drawCircle(centerX, centerY, size / 2f, Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    })
    canvas.drawCircle(centerX, centerY, radius, Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    })
    return bitmap
}

// UI state data class
data class TripPlannerState(
    val startLocation: String = "",
    val endLocation: String = "",
    val isRouteDisplayed: Boolean = false
)

class TripPlannerViewModel : ViewModel() {
    private val _uiState = mutableStateOf(TripPlannerState())
    val uiState = _uiState

    fun updateStartLocation(newStart: String) {
        _uiState.value = _uiState.value.copy(startLocation = newStart)
    }

    fun updateEndLocation(newEnd: String) {
        _uiState.value = _uiState.value.copy(endLocation = newEnd)
    }

    fun planTrip() {
        // Insert your trip planning logic here.
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
                    startLocation = state.startLocation,
                    endLocation = state.endLocation,
                    onStartLocationChange = { viewModel.updateStartLocation(it) },
                    onEndLocationChange = { viewModel.updateEndLocation(it) },
                    onPlanTrip = { viewModel.planTrip() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * FloatingDirectionsInput now uses our custom [LocationEntryField] for each location.
 */
@Composable
fun FloatingDirectionsInput(
    startLocation: String,
    endLocation: String,
    onStartLocationChange: (String) -> Unit,
    onEndLocationChange: (String) -> Unit,
    onPlanTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a Surface to create a floating card-like effect.
    Surface(
        modifier = modifier,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LocationEntryField(
                label = "Start Location",
                value = startLocation,
                onValueChange = onStartLocationChange,
                onClear = { onStartLocationChange("") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LocationEntryField(
                label = "End Location",
                value = endLocation,
                onValueChange = onEndLocationChange,
                onClear = { onEndLocationChange("") }
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

/**
 * LocationEntryField renders a location “field” that can be either in an editable mode
 * (with an auto‑complete dropdown of address suggestions) or in token mode (showing an
 * immutable token with a clear “×” button). The convention is that a token value is wrapped in [ and ].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    // If the value is already tokenized (starts with "[" and ends with "]") then show it as a token.
    val isToken = value.startsWith("[") && value.endsWith("]")
    if (isToken) {
        OutlinedTextField(
            value = value,
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
    } else {
        // Editable mode with reactive suggestions.
        var query by remember { mutableStateOf(value) }
        // Update local query when the external value changes.
        LaunchedEffect(value) {
            query = value
        }
        val addressResolver = remember { AddressResolverService() }
        val suggestions = addressResolver.getSuggestions(query)
        var expanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

        Box {
            OutlinedTextField(
                value = query,
                onValueChange = { newValue ->
                    query = newValue
                    onValueChange(newValue)
                    expanded = newValue.isNotEmpty() && suggestions.isNotEmpty()
                },
                label = { Text(label) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { /* Removed auto-tokenizing logic here */ },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { 
                        focusManager.clearFocus()
                        suggestions.find { it.address.equals(query, ignoreCase = true) }?.let { match ->
                            onValueChange("[$match]")
                            expanded = false
                        }
                    }
                )
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
                properties = PopupProperties(focusable=false)
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion.address) },
                        onClick = {
                            onValueChange("[$suggestion]")
                            query = suggestion.address
                            expanded = false
                            focusManager.clearFocus()
                        }
                    )
                }
            }
        }
    }
}

/**
 * MapComponent remains largely the same except that when a location is chosen via a map long-press,
 * we update the view model with the token "[selected from map]".
 */
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
        Dialog(onDismissRequest = { showContextMenu = false }) {
            LocationPicker(
                latitude = longPressPoint!!.latitude,
                longitude = longPressPoint!!.longitude,
                onSetAsStart = {
                    viewModel.updateStartLocation("[selected from map]")
                    showContextMenu = false
                },
                onSetAsEnd = {
                    viewModel.updateEndLocation("[selected from map]")
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