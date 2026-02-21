package com.skilift.app.ui.tripdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.Leg
import com.skilift.app.ui.common.format.formatDistanceKm
import com.skilift.app.ui.common.format.formatDurationMinutes
import com.skilift.app.ui.common.format.formatTime
import com.skilift.app.ui.common.transport.toUi

@Composable
fun LegTimelineItem(leg: Leg, isLast: Boolean) {
    val modeUi = leg.mode.toUi()

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
                    .background(modeUi.color)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(modeUi.color.copy(alpha = 0.5f))
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
                        text = modeUi.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = modeUi.color
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatDurationMinutes(leg.durationSeconds),
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
                    text = "${formatTime(leg.startTime)} - ${leg.from.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${formatTime(leg.endTime)} - ${leg.to.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = formatDistanceKm(leg.distanceMeters),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
