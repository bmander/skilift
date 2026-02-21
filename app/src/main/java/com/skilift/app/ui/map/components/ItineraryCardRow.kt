package com.skilift.app.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.ui.common.format.formatDurationMinutes
import com.skilift.app.ui.common.transport.toUi

@Composable
fun ItineraryCardRow(
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
                        text = formatDurationMinutes(itinerary.durationSeconds),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        itinerary.legs.forEach { leg ->
                            Text(
                                text = leg.mode.toUi().iconGlyph,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == selectedIndex)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent
                    )
                }
            }
        }
    }
}
