package com.shawady.bodywise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF347B52),
    onPrimary = Color.White,
    secondary = Color(0xFF347B52),
    background = Color(0xFFF6F8F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7F1E8),
    onSurfaceVariant = Color(0xFF4A5A4F),
    outlineVariant = Color(0xFFD5DDD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BC49B),
    onPrimary = Color(0xFF0B2A17),
    secondary = Color(0xFF8BC49B),
    background = Color(0xFF101512),
    surface = Color(0xFF181E1A),
    surfaceVariant = Color(0xFF232B26),
    onSurfaceVariant = Color(0xFFB5C2B8),
    outlineVariant = Color(0xFF3A463E)
)

@Composable
fun BodyWiseTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
