package com.djmetry.ui

import com.djmetry.ui.djmap.MapZoomRequests
import com.djmetry.ui.djmap.nextZoom
import kotlin.test.*

/** Кнопки +/− карты: быстрые нажатия не теряются, зум в пределах. */
class MapZoomTest {
    @Test
    fun quickPressesAddUpWithinLimits() {
        assertEquals(5.0, nextZoom(3.0, listOf(1.0, 1.0), 0.0, 18.0), "два быстрых «+» — два шага, а не один")
        assertEquals(18.0, nextZoom(17.5, listOf(1.0), 0.0, 18.0))
        assertEquals(0.0, nextZoom(0.5, listOf(-1.0), 0.0, 18.0))
    }

    @Test
    fun samePressTwiceIsQueuedTwice() {
        val r = MapZoomRequests()
        r.zoom(1.0); r.zoom(1.0)
        assertEquals(1.0, r.steps.tryReceive().getOrNull())
        assertEquals(1.0, r.steps.tryReceive().getOrNull(), "одинаковое нажатие подряд — не схлопывается (раньше терялось)")
    }
}
