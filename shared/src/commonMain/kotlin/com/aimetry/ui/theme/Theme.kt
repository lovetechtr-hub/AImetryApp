package com.aimetry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AimetryColors.Accent,
    secondary = AimetryColors.Accent2,
    background = AimetryColors.Background,
    surface = AimetryColors.Panel,
    onPrimary = AimetryColors.Background,
    onSecondary = AimetryColors.Background,
    onBackground = AimetryColors.Text,
    onSurface = AimetryColors.Text,
    surfaceVariant = AimetryColors.PanelStrong,
    onSurfaceVariant = AimetryColors.Muted,
    outline = AimetryColors.Border,
    outlineVariant = AimetryColors.Border.copy(alpha = 0.5f)
)

@Composable
fun AimetryTheme(
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

