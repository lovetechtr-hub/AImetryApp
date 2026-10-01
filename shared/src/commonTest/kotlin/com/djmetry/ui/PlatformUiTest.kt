package com.djmetry.ui

import com.djmetry.ui.components.SHIMMER_PERIOD_MS
import com.djmetry.ui.components.shimmerProgress
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.screens.TABLET_RAIL_DP
import kotlin.test.*

/** Платформенный UI: раскладка по ширине контента, блик скелетона. */
class PlatformUiTest {
    @Test
    fun tabletContentIsMeasuredWithoutRail() {
        // Окно 700dp — планшетная раскладка оболочки, но экранам остаётся 588dp: телефонная раскладка, без давки
        assertEquals(LayoutClass.Medium, layoutClassFor(700f))
        assertEquals(LayoutClass.Compact, layoutClassFor(700f - TABLET_RAIL_DP))
        // 900dp окна — 788dp контента: ещё не десктопные три колонки
        assertEquals(LayoutClass.Medium, layoutClassFor(900f - TABLET_RAIL_DP))
        assertEquals(LayoutClass.Expanded, layoutClassFor(1280f - TABLET_RAIL_DP))
    }

    @Test
    fun shimmerProgressLoops() {
        assertEquals(0f, shimmerProgress(0))
        assertEquals(0.5f, shimmerProgress(SHIMMER_PERIOD_MS / 2L))
        assertEquals(0f, shimmerProgress(SHIMMER_PERIOD_MS.toLong()))
    }
}
