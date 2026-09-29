package com.djmetry.ui

import com.djmetry.api.models.ClickBreakdownRow
import com.djmetry.data.analytics.ActivityBucket
import com.djmetry.i18n.Strings
import com.djmetry.ui.analytics.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class AnalyticsFormatTest {
    private val months = "янв фев мар апр май июн июл авг сен окт ноя дек"

    @Test
    fun numbersAndCtr() {
        assertEquals("12 480", groupThousands(12480))
        assertEquals("999", groupThousands(999))
        assertEquals("1 000 000", groupThousands(1_000_000))
        assertEquals("24.8%", ctrLabel(24.8)); assertEquals("10%", ctrLabel(10.0)); assertEquals("33.3%", ctrLabel(100.0 / 3))
    }

    @Test
    fun axisLabels() {
        assertEquals("22 сен", pointLabel(LocalDate(2026, 9, 22), ActivityBucket.Day, months))
        assertEquals("сен 2026", pointLabel(LocalDate(2026, 9, 1), ActivityBucket.Month, months))
        assertEquals(1, labelStep(8, 8)); assertEquals(8, labelStep(31, 4), "31 день на телефоне — не больше 4 подписей")
    }

    @Test
    fun clickLabelsGroupByPlatform() {
        assertEquals(ClickLabel.Text("Apple Music"), clickLabel(ClickBreakdownRow("apple_music", "outbound_click", 1)))
        assertEquals(ClickLabel.Text("SoundCloud"), clickLabel(ClickBreakdownRow("soundcloud", "social_click", 1)))
        assertEquals(ClickLabel.Key(Strings.AN_EV_SMART), clickLabel(ClickBreakdownRow(null, "smart_link_click", 1)))
        val g = groupedClicks(listOf(ClickBreakdownRow("spotify", "outbound_click", 5), ClickBreakdownRow("Spotify", "view_content", 7), ClickBreakdownRow("beatport", "outbound_click", 9), ClickBreakdownRow("x", "y", 0)))
        assertEquals(listOf(ClickLabel.Text("Spotify") to 12, ClickLabel.Text("Beatport") to 9), g, "склейка одинаковых площадок, нули — вон")
    }

    @Test
    fun devices() {
        assertEquals(Strings.AN_DEV_MOBILE, deviceKey("Mobile")); assertEquals(Strings.AN_UNKNOWN, deviceKey("unknown")); assertNull(deviceKey("smart-tv"))
    }

    @Test
    fun niceYAxis() {
        assertEquals(2500.0 to 500.0, niceAxis(2229))
        assertEquals(10.0 to 2.0, niceAxis(9))
        assertEquals(4.0 to 1.0, niceAxis(0), "пустой график — ось 0…4")
        assertEquals(1.0 to 1.0, niceAxis(1))
        for (m in listOf(3, 57, 733, 12480, 99_999, 1_000_000)) {
            val (top, step) = niceAxis(m)
            assertTrue(top >= m && top / step <= 5.0 && step >= 1.0, "$m → $top/$step")
        }
    }

    @Test
    fun worldFitsMapWidth() {
        assertEquals(0.0, worldZoom(343f), "узкая карточка — весь мир, без отрицательного зума")
        assertEquals(1.0, worldZoom(1024f), 1e-9)
        assertTrue(worldZoom(1180f) > worldZoom(820f))
    }
}
