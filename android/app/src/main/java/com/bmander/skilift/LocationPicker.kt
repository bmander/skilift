package com.bmander.skilift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocationPicker(
    latitude: Double,
    longitude: Double,
    onSetAsStart: () -> Unit,
    onSetAsEnd: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Location Selected", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Latitude: $latitude\nLongitude: $longitude")
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onSetAsStart) {
                    Text("Set as Start")
                }
                Button(onClick = onSetAsEnd) {
                    Text("Set as End")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cancel")
            }
        }
    }
}