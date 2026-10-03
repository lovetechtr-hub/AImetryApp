package com.djmetry.ui

import com.djmetry.ui.components.swipeShift
import kotlin.test.*

/** Свайп «назад» от края: слой идёт за пальцем, не выходит за 0…1. */
class SwipeBackTest {
    @Test
    fun shiftFollowsFingerWithinBounds() {
        assertEquals(0f, swipeShift(0f))
        assertEquals(1f, swipeShift(1f))
        assertEquals(0f, swipeShift(-0.3f), "назад дальше края — не уезжает влево")
        assertEquals(1f, swipeShift(1.4f))
        assertTrue(swipeShift(0.5f) in 0.4f..0.5f, "в середине — чуть позади пальца, как у системы")
        assertTrue(swipeShift(0.3f) < swipeShift(0.6f), "монотонно")
    }
}
