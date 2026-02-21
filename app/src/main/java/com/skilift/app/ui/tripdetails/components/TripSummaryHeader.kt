package com.skilift.app.ui.tripdetails.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.ui.common.format.formatDurationMinutes
import com.skilift.app.ui.common.format.formatTime
import com.skilift.app.ui.common.transport.toUi

@Composable
fun TripSummaryHeader(itinerary: Itinerary) {
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
                text = "${formatDurationMinutes(itinerary.durationSeconds)} total",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${formatTime(itinerary.startTime)} \u2192 ${formatTime(itinerary.endTime)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                itinerary.legs.forEachIndexed { index, leg ->
                    Text(
                        text = leg.mode.toUi().iconGlyph,
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
