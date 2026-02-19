package com.skilift.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.map.components.PreferenceSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onItinerarySelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-122.3321, 47.6062))
            zoom(12.0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skilift") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (uiState.origin != null || uiState.destination != null) {
                        IconButton(onClick = { viewModel.clearPoints() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                style = { MapboxStandardStyle() },
                onMapClickListener = OnMapClickListener { point ->
                    viewModel.onMapTap(
                        com.skilift.app.domain.model.LatLng(point.latitude(), point.longitude())
                    )
                    true
                }
            ) {
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
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
                    ItineraryCards(
                        itineraries = uiState.itineraries,
                        selectedIndex = uiState.selectedItineraryIndex,
                        onSelect = { viewModel.selectItinerary(it) },
                        onDetails = { onItinerarySelected(it) }
                    )
                }

                PreferenceSlider(
                    value = uiState.bikeTransitBalance,
                    onValueChange = { viewModel.onSliderChanged(it) },
                    onValueChangeFinished = { viewModel.onSliderChangeFinished() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Text(
                    text = if (uiState.isSelectingOrigin) "Tap to set origin" else "Tap to set destination",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                    if (index == selectedIndex) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
