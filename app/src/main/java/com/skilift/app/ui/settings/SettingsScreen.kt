package com.skilift.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skilift.app.domain.model.CyclingOptimization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Default bike/transit balance
            Text(
                text = "Default Bike/Transit Balance",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("More Transit", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("More Biking", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = preferences.bikeTransitBalance,
                onValueChange = { viewModel.updateBikeTransitBalance(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Cycling optimization
            Text(
                text = "Cycling Optimization",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                CyclingOptimization.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = preferences.cyclingOptimization == option,
                                onClick = { viewModel.updateCyclingOptimization(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferences.cyclingOptimization == option,
                            onClick = null
                        )
                        Text(
                            text = optimizationLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Max bike speed
            Text(
                text = "Max Bike Speed",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.1f m/s (%.1f mph)".format(
                    preferences.maxBikeSpeedMps,
                    preferences.maxBikeSpeedMps * 2.237
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Slider(
                value = preferences.maxBikeSpeedMps,
                onValueChange = { viewModel.updateMaxBikeSpeed(it) },
                valueRange = 3.0f..8.0f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("3.0 m/s", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("8.0 m/s", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // About
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Skilift v0.1.0",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "A bike + transit trip planner for Seattle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Map: Mapbox",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Routing: OpenTripPlanner 2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Transit data: King County Metro, Sound Transit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun optimizationLabel(opt: CyclingOptimization): String = when (opt) {
    CyclingOptimization.QUICK -> "Quick (fastest route)"
    CyclingOptimization.SAFE_STREETS -> "Safe Streets (prefer bike infrastructure)"
    CyclingOptimization.FLAT_STREETS -> "Flat Streets (minimize hills)"
}
