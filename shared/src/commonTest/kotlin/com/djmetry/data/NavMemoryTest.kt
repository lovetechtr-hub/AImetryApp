package com.djmetry.data

import com.djmetry.data.local.NavMemory
import com.djmetry.data.local.NavState
import kotlin.test.*

/** Память навигации: после выгрузки из фона возвращаемся на ту же вкладку и карточку, если прошло меньше 30 минут. */
class NavMemoryTest {
    private val now = 1_800_000_000_000L

    @Test
    fun restoresRecentState() {
        val raw = NavMemory.encode(now, NavState("Radars", "0TnOYISbd1XYRBk9myaseg"))
        assertEquals(NavState("Radars", "0TnOYISbd1XYRBk9myaseg"), NavMemory.decode(raw, now + 5 * 60_000))
        assertEquals(NavState("Booking", null), NavMemory.decode(NavMemory.encode(now, NavState("Booking", null)), now))
    }

    @Test
    fun ignoresOldOrBroken() {
        val raw = NavMemory.encode(now, NavState("Radars", null))
        assertNull(NavMemory.decode(raw, now + NavMemory.MAX_AGE_MS + 1))
        assertNull(NavMemory.decode(raw, now - 1000)) // часы ушли назад
        assertNull(NavMemory.decode("garbage", now))
        assertNull(NavMemory.decode(null, now))
    }
}
