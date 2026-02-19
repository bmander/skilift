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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    onBack: () -> Unit
) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Itinerary ${itineraryIndex + 1}",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Detailed leg-by-leg view will be displayed here when connected to OTP2.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
