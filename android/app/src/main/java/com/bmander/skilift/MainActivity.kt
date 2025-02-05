package com.bmander.skilift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bmander.skilift.ui.theme.SkiliftTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
    var startLocation = remember { mutableStateOf("") }
    var endLocation = remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MapComponent(
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LocationInputs()
        }
    }
}

@Composable
fun LocationInputs() {
    Text(text = "Location Inputs Placeholder")
}

@Composable
fun MapComponent(modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(org.osmdroid.util.GeoPoint(47.6062, -122.3321))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(factory = {mapView})

}