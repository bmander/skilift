package com.skilift.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.TimeSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
fun LocationInputBar(
    origin: LatLng?,
    destination: LatLng?,
    originName: String? = null,
    destinationName: String? = null,
    originIsCurrentLocation: Boolean = false,
    destinationIsCurrentLocation: Boolean = false,
    timeSelection: TimeSelection,
    onClearOrigin: () -> Unit,
    onClearDestination: () -> Unit,
    onOriginRowClicked: () -> Unit = {},
    onDestinationRowClicked: () -> Unit = {},
    onTimeRowClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Origin row
            LocationRow(
                color = Color(0xFF00796B),
                latLng = origin,
                displayName = originName,
                isCurrentLocation = originIsCurrentLocation,
                placeholder = "Search or long-press map",
                onClear = onClearOrigin,
                onRowClicked = onOriginRowClicked
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Destination row
            LocationRow(
                color = Color(0xFFD32F2F),
                latLng = destination,
                displayName = destinationName,
                isCurrentLocation = destinationIsCurrentLocation,
                placeholder = "Search or long-press map",
                onClear = onClearDestination,
                onRowClicked = onDestinationRowClicked
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Time row
            Text(
                text = formatTimeSelection(timeSelection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTimeRowClicked() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LocationRow(
    color: Color,
    latLng: LatLng?,
    displayName: String? = null,
    isCurrentLocation: Boolean = false,
    placeholder: String,
    onClear: () -> Unit,
    onRowClicked: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        if (latLng != null) {
            InputChip(
                selected = false,
                onClick = onRowClicked,
                label = {
                    Text(
                        when {
                            isCurrentLocation -> "Current Location"
                            displayName != null -> displayName
                            else -> "%.3f, %.3f".format(latLng.latitude, latLng.longitude)
                        },
                        maxLines = 1
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            )
        } else {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable { onRowClicked() }
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

private fun formatTimeSelection(selection: TimeSelection): String {
    return when (selection) {
        is TimeSelection.DepartNow -> "Depart now"
        is TimeSelection.DepartAt -> {
            val zdt = Instant.ofEpochMilli(selection.epochMillis)
                .atZone(ZoneId.systemDefault())
            "Depart ${zdt.format(timeFormatter)}"
        }
        is TimeSelection.ArriveBy -> {
            val zdt = Instant.ofEpochMilli(selection.epochMillis)
                .atZone(ZoneId.systemDefault())
            "Arrive ${zdt.format(timeFormatter)}"
        }
    }
}
