package com.skilift.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SkiliftColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = White,
    primaryContainer = TealLight,
    secondary = BikeGreen,
    surface = Surface,
    onSurface = OnSurface
)

@Composable
fun SkiliftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkiliftColorScheme,
        typography = SkiliftTypography,
        content = content
    )
}
