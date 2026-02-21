package com.skilift.app.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MapTuningSheet(
    bikeTransitBalance: Float,
    onSliderChanged: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    triangleWeights: TriangleWeights,
    onWeightsChanged: (TriangleWeights) -> Unit,
    onWeightsChangeFinished: () -> Unit,
    hillReluctance: Float,
    onHillReluctanceChanged: (Float) -> Unit,
    onHillReluctanceChangeFinished: () -> Unit,
    maxBikeSpeedMps: Float,
    onMaxBikeSpeedChanged: (Float) -> Unit,
    onMaxBikeSpeedChangeFinished: () -> Unit,
    height: Dp
) {
    Column(
        modifier = Modifier
            .height(height)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        // Bike/Transit Balance
        Text(
            text = "Bike/Transit Balance",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "More Transit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "More Biking",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Slider(
            value = bikeTransitBalance,
            onValueChange = onSliderChanged,
            onValueChangeFinished = onSliderChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Cycling Optimization
        Text(
            text = "Cycling Optimization",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Drag the point to balance fast, safe, and flat routing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        BikeTriangleWidget(
            weights = triangleWeights,
            onWeightsChanged = onWeightsChanged,
            onWeightsChangeFinished = onWeightsChangeFinished,
            modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Hill Avoidance
        Text(
            text = "Hill Avoidance",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Normal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Strong",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Slider(
            value = hillReluctance,
            onValueChange = onHillReluctanceChanged,
            onValueChangeFinished = onHillReluctanceChangeFinished,
            valueRange = 1.0f..3.0f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Max Bike Speed
        Text(
            text = "Max Bike Speed",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "%.1f m/s (%.1f mph)".format(
                maxBikeSpeedMps,
                maxBikeSpeedMps * 2.237
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Slider(
            value = maxBikeSpeedMps,
            onValueChange = onMaxBikeSpeedChanged,
            onValueChangeFinished = onMaxBikeSpeedChangeFinished,
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
    }
}
