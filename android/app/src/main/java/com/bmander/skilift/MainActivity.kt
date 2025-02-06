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
import com.bmander.skilift.ui.theme.SkiliftTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

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
fun TripPlannerApp() {
    // Use delegated state for cleaner updates
    var startLocation by remember { mutableStateOf("") }
    var endLocation by remember { mutableStateOf("") }
    var isRouteDisplayed by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        // Use a Box so we can overlay the directions input on top of the map
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // The Map occupies the full screen
            MapComponent(
                modifier = Modifier.fillMaxSize()
            )

            // Only show the floating directions input if no route is currently displayed.
            if (!isRouteDisplayed) {
                FloatingDirectionsInput(
                    startLocation = startLocation,
                    endLocation = endLocation,
                    onStartLocationChange = { startLocation = it },
                    onEndLocationChange = { endLocation = it },
                    onPlanTrip = {
                        // When planning the trip, you can add your logic here.
                        // For now, we simulate that a route has been planned.
                        isRouteDisplayed = true
                    },
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
fun MapComponent(modifier: Modifier) {
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