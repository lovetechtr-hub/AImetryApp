package com.djmetry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Слой поверх всего экрана, включая таббар: шторки и поповеры (например, уведомления).
 * Рисуется в [com.djmetry.ui.screens.MainShell] последним.
 */
@Stable
class OverlayController {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    fun show(overlay: @Composable () -> Unit) { content = overlay }
    fun dismiss() { content = null }
}

val LocalOverlay = staticCompositionLocalOf { OverlayController() }
