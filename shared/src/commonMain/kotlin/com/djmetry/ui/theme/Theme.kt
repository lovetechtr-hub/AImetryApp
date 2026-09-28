package com.djmetry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DJMetryColors.Accent,
    secondary = DJMetryColors.Accent2,
    background = DJMetryColors.Background,
    surface = DJMetryColors.Panel,
    onPrimary = DJMetryColors.Background,
    onSecondary = DJMetryColors.Background,
    onBackground = DJMetryColors.Text,
    onSurface = DJMetryColors.Text,
    surfaceVariant = DJMetryColors.PanelStrong,
    onSurfaceVariant = DJMetryColors.Muted,
    outline = DJMetryColors.Border,
    outlineVariant = DJMetryColors.Border.copy(alpha = 0.5f)
)

@Composable
fun DJMetryTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

