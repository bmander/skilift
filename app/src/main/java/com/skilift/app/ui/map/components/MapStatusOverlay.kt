package com.skilift.app.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode

@Composable
fun MapStatusOverlay(
    isLoading: Boolean,
    error: String?,
    itineraries: List<Itinerary>,
    selectedItineraryIndex: Int,
    selectedLegIndex: Int?,
    onSelectItinerary: (Int) -> Unit,
    onItineraryDetails: (Int) -> Unit,
    onElevationPositionSelected: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        }

        error?.let { errorText ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorText,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (itineraries.isNotEmpty()) {
            val selectedItinerary = itineraries[selectedItineraryIndex]
            val selectedLeg = selectedLegIndex?.let { selectedItinerary.legs.getOrNull(it) }
            if (selectedLeg != null && selectedLeg.mode == TransportMode.BICYCLE && selectedLeg.elevationProfile.isNotEmpty()) {
                ElevationProfileChart(
                    leg = selectedLeg,
                    onFractionSelected = onElevationPositionSelected
                )
            }

            ItineraryCardRow(
                itineraries = itineraries,
                selectedIndex = selectedItineraryIndex,
                onSelect = onSelectItinerary,
                onDetails = onItineraryDetails
            )
        }
    }
}
