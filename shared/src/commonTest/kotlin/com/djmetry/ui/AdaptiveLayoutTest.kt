package com.djmetry.ui

import androidx.compose.ui.input.key.Key
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.screens.SwipeAction
import com.djmetry.ui.screens.keyAction
import kotlin.test.*

/** Правило docs/RULES.md: раскладка по ширине окна, границы 600 и 840 dp. */
class AdaptiveLayoutTest {

    @Test
    fun phonesAreCompact() {
        assertEquals(LayoutClass.Compact, layoutClassFor(320f)) // iPhone SE
        assertEquals(LayoutClass.Compact, layoutClassFor(430f)) // iPhone Pro Max
        assertEquals(LayoutClass.Compact, layoutClassFor(599.9f))
    }

    @Test
    fun tabletPortraitIsMedium() {
        assertEquals(LayoutClass.Medium, layoutClassFor(600f))
        assertEquals(LayoutClass.Medium, layoutClassFor(744f)) // iPad mini портрет
        assertEquals(LayoutClass.Medium, layoutClassFor(839f))
    }

    @Test
    fun tabletLandscapeIsExpanded() {
        assertEquals(LayoutClass.Expanded, layoutClassFor(840f))
        assertEquals(LayoutClass.Expanded, layoutClassFor(1180f)) // iPad Air альбом
        assertEquals(LayoutClass.Expanded, layoutClassFor(1366f)) // iPad Pro 13" альбом
    }

    @Test
    fun splitViewOnIpadFallsBackToPhoneLayout() {
        assertEquals(LayoutClass.Compact, layoutClassFor(375f)) // iPad Split View 1/3
        assertFalse(layoutClassFor(375f).isTablet)
        assertTrue(layoutClassFor(820f).isTablet)
    }

    @Test
    fun arrowKeysMirrorSwipes() {
        assertEquals(SwipeAction.Skip, keyAction(Key.DirectionLeft))
        assertEquals(SwipeAction.Follow, keyAction(Key.DirectionRight))
        assertEquals(SwipeAction.Vote, keyAction(Key.DirectionUp))
        assertNull(keyAction(Key.DirectionDown))
        assertNull(keyAction(Key.Spacebar))
    }

    /** Списки и текст не растягиваются: телефон — вся ширина, iPad-альбом, десктоп и 4K — не шире 760 dp. */
    @Test
    fun readableWidthOnDeviceMatrix() {
        assertEquals(375f, com.djmetry.ui.layout.readableWidthDp(375f))
        assertEquals(393f, com.djmetry.ui.layout.readableWidthDp(393f))
        listOf(820f, 1180f, 1560f, 2560f).forEach { w ->
            assertEquals(760f, com.djmetry.ui.layout.readableWidthDp(w), "окно $w")
        }
    }
}
