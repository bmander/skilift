package com.skilift.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun MapContextMenu(
    screenX: Float,
    screenY: Float,
    onDismiss: () -> Unit,
    onSetOrigin: () -> Unit,
    onSetDestination: () -> Unit
) {
    Box(
        modifier = Modifier.offset {
            IntOffset(screenX.toInt(), screenY.toInt())
        }
    ) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text("Set as Start") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF00796B), CircleShape)
                    )
                },
                onClick = onSetOrigin
            )
            DropdownMenuItem(
                text = { Text("Set as End") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFFD32F2F), CircleShape)
                    )
                },
                onClick = onSetDestination
            )
        }
    }
}
