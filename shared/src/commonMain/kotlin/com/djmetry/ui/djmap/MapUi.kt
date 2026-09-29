package com.djmetry.ui.djmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цвета интерфейса карты: тёмная тема (цвета DJMetry) и светлая — как на сайте в светлой теме (белые панели,
 * тёмный текст). Переключается вместе с подложкой кнопкой «Светлая / тёмная карта».
 */
@Immutable
internal data class MapUiColors(
    val glass: Color, val popup: Color, val inner: Color, val secondary: Color,
    val text: Color, val muted: Color, val border: Color, val hairline: Color,
    val accent: Color, val onAccent: Color, val bg: Color, val water: Color,
) {
    companion object {
        val Dark = MapUiColors(
            glass = Color(0xF70E1728), popup = Color(0xFF131C31), inner = Color(0xFF0E1728), secondary = Color(0xFF1E2A44),
            text = Color(0xFFE7EEFC), muted = Color(0xFF9AB0D5), border = Color(0xFF1F2D45), hairline = Color(0x17FFFFFF),
            accent = Color(0xFF5EE6A8), onAccent = Color(0xFF0B1220), bg = Color(0xFF0B1220), water = Color(0xFF080E1A),
        )
        val Light = MapUiColors(
            glass = Color(0xF7FFFFFF), popup = Color(0xFFFFFFFF), inner = Color(0xFFF1F4F9), secondary = Color(0xFFEEF2F7),
            text = Color(0xFF0F172A), muted = Color(0xFF64748B), border = Color(0xFFE2E8F0), hairline = Color(0x14000000),
            accent = Color(0xFF10B981), onAccent = Color(0xFFFFFFFF), bg = Color(0xFFF8FAFC), water = Color(0xFFCFE0EC),
        )
    }
}

internal val LocalMapUi = staticCompositionLocalOf { MapUiColors.Dark }

/** Короткий доступ к цветам карты в разметке. */
internal object MapUi {
    val glass @Composable get() = LocalMapUi.current.glass
    val popup @Composable get() = LocalMapUi.current.popup
    val inner @Composable get() = LocalMapUi.current.inner
    val secondary @Composable get() = LocalMapUi.current.secondary
    val text @Composable get() = LocalMapUi.current.text
    val muted @Composable get() = LocalMapUi.current.muted
    val border @Composable get() = LocalMapUi.current.border
    val hairline @Composable get() = LocalMapUi.current.hairline
    val accent @Composable get() = LocalMapUi.current.accent
    val onAccent @Composable get() = LocalMapUi.current.onAccent
    val bg @Composable get() = LocalMapUi.current.bg
    val water @Composable get() = LocalMapUi.current.water
}
