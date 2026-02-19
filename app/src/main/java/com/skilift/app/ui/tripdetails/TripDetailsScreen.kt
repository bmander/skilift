package com.skilift.app.ui.tripdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.Leg
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.theme.BikeGreen
import com.skilift.app.ui.theme.FerryTeal
import com.skilift.app.ui.theme.RailPurple
import com.skilift.app.ui.theme.TransitBlue
import com.skilift.app.ui.theme.WalkGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    itineraryIndex: Int,
    onBack: () -> Unit,
    viewModel: TripDetailsViewModel = hiltViewModel()
) {
    val itinerary by viewModel.itinerary.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        val itin = itinerary
        if (itin == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Itinerary not available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    SummaryHeader(itinerary = itin)
                }
                itemsIndexed(itin.legs) { index, leg ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LegTimelineItem(
                            leg = leg,
                            isLast = index == itin.legs.lastIndex
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(itinerary: Itinerary) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val totalMinutes = itinerary.durationSeconds / 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$totalMinutes min total",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${timeFormat.format(Date(itinerary.startTime))} \u2192 ${timeFormat.format(Date(itinerary.endTime))}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                itinerary.legs.forEachIndexed { index, leg ->
                    Text(
                        text = modeEmoji(leg.mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (index < itinerary.legs.lastIndex) {
                        Text(
                            text = " \u203A ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${itinerary.legs.size} legs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun LegTimelineItem(leg: Leg, isLast: Boolean) {
    val modeColor = colorForMode(leg.mode)
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(modeColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(modeColor.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row {
                    Text(
                        text = modeName(leg.mode),
                        style = MaterialTheme.typography.titleMedium,
                        color = modeColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${leg.durationSeconds / 60} min",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                leg.routeShortName?.let { route ->
                    Text(
                        text = "Route $route",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                leg.headsign?.let { headsign ->
                    Text(
                        text = "toward $headsign",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "${timeFormat.format(Date(leg.startTime))} - ${leg.from.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${timeFormat.format(Date(leg.endTime))} - ${leg.to.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "%.1f km".format(leg.distanceMeters / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun colorForMode(mode: TransportMode): Color = when (mode) {
    TransportMode.BICYCLE -> BikeGreen
    TransportMode.BUS -> TransitBlue
    TransportMode.RAIL -> RailPurple
    TransportMode.TRAM -> RailPurple
    TransportMode.FERRY -> FerryTeal
    TransportMode.WALK -> WalkGray
}

private fun modeName(mode: TransportMode): String = when (mode) {
    TransportMode.BICYCLE -> "Bike"
    TransportMode.BUS -> "Bus"
    TransportMode.RAIL -> "Rail"
    TransportMode.TRAM -> "Tram"
    TransportMode.FERRY -> "Ferry"
    TransportMode.WALK -> "Walk"
}

private fun modeEmoji(mode: TransportMode): String = when (mode) {
    TransportMode.BICYCLE -> "\uD83D\uDEB2"
    TransportMode.BUS -> "\uD83D\uDE8C"
    TransportMode.RAIL -> "\uD83D\uDE86"
    TransportMode.TRAM -> "\uD83D\uDE8A"
    TransportMode.FERRY -> "\u26F4"
    TransportMode.WALK -> "\uD83D\uDEB6"
}
