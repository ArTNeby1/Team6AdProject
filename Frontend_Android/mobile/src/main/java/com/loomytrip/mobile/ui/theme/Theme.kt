package com.loomytrip.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LoomyTripColors = lightColorScheme(
    primary = Color(0xFF1F5D42),
    onPrimary = Color.White,
    secondary = Color(0xFFE07A3F),
    background = Color(0xFFFFFDF8),
    surface = Color(0xFFFFFDF8),
    surfaceVariant = Color(0xFFF1EEE6),
    onBackground = Color(0xFF17201B),
    onSurface = Color(0xFF17201B)
)

@Composable
fun LoomyTripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoomyTripColors,
        content = content
    )
}
