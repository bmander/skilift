package com.bmander.skilift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import com.bmander.skilift.ui.theme.SkiliftTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.lifecycle.viewmodel.compose.viewModel


// UI state data class
data class TripPlannerState(
    val startLocation: String = "",
    val endLocation: String = "",
    val isRouteDisplayed: Boolean = false
)

class TripPlannerViewModel : ViewModel() {
    private val _uiState = mutableStateOf(TripPlannerState())
    val uiState: State<TripPlannerState> = _uiState

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
            OutlinedTextField(
                value = startLocation,
                onValueChange = onStartLocationChange,
                label = { Text("Start Location") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = endLocation,
                onValueChange = onEndLocationChange,
                label = { Text("End Location") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Button(
                onClick = onPlanTrip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Plan Trip")
            }
        }
    }
}

@Composable
fun MapComponent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(47.6062, -122.3321))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}