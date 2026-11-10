package com.shawady.bodywise.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/**
 * One rounded design language for every component: pills for buttons and chips, 20dp for text fields, menus and
 * cards, larger radii for dialogs. Components read these through MaterialTheme.shapes, so nothing falls back to
 * Material's sharp defaults.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(20.dp),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BodyWiseTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = AppShapes, content = content)
}

/** Meaning colors with light and dark variants, so warnings stay readable in both themes. */
object Status {
    @Composable private fun dark() = MaterialTheme.colorScheme.background.luminance() < 0.5f
    @Composable fun danger(): Color = if (dark()) Color(0xFFFF8A80) else Color(0xFFD32F2F)
    @Composable fun warning(): Color = if (dark()) Color(0xFFFFB74D) else Color(0xFFC77700)
    @Composable fun success(): Color = if (dark()) Color(0xFF81C784) else Color(0xFF2E7D32)
}
