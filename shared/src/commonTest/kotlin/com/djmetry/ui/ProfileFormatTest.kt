package com.djmetry.ui

import com.djmetry.data.repository.NotificationFilter
import com.djmetry.i18n.Strings
import com.djmetry.ui.profile.*
import kotlinx.datetime.Instant
import kotlin.test.*

class ProfileFormatTest {

    private val now = Instant.parse("2026-09-28T12:00:00Z")

    @Test
    fun relativeTimeBuckets() {
        assertEquals(Strings.TIME_NOW to null, relativeTime("2026-09-28T11:59:40Z", now))
        assertEquals(Strings.TIME_MIN to 5L, relativeTime("2026-09-28T11:55:00Z", now))
        assertEquals(Strings.TIME_HOURS to 2L, relativeTime("2026-09-28 10:00:00", now), "формат бэкенда без T и зоны = UTC")
        assertEquals(Strings.TIME_DAYS to 3L, relativeTime("2026-09-25T12:00:00+00:00", now))
        assertNull(relativeTime(null, now))
        assertNull(relativeTime("вчера", now))
    }

    @Test
    fun futureTimestampIsNow() {
        assertEquals(Strings.TIME_NOW to null, relativeTime("2026-09-28T12:05:00Z", now))
    }

    @Test
    fun notificationKinds() {
        assertEquals(NotificationKind.Booking, notificationKind("booking"))
        assertEquals(NotificationKind.Release, notificationKind("release_radar"))
        assertEquals(NotificationKind.PreSave, notificationKind("pre_save"))
        assertEquals(NotificationKind.Concert, notificationKind("concert"))
        assertEquals(NotificationKind.Other, notificationKind("digest_top_djs"))
    }

    @Test
    fun everyFilterHasItsOwnLabel() {
        val labels = NotificationFilter.entries.map(::filterLabelKey)
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(listOf(null, "booking", "release_radar", "pre_save", "concert"), NotificationFilter.entries.map { it.apiType })
    }

    @Test
    fun bookingStatuses() {
        assertEquals(Strings.BS_FINISHED, bookingStatusKey("artist_finished_performance"))
        assertEquals(Strings.BS_ON_THE_WAY, bookingStatusKey("artist_on_the_way"))
        assertNull(bookingStatusKey("something_new"), "неизвестный статус показываем как есть")
    }

    @Test
    fun moneyFormatting() {
        assertEquals("10 000 $", formatMoney(10000.0, "USD"))
        assertEquals("1 500 €", formatMoney(1499.6, "eur"))
        assertEquals("250 CHF", formatMoney(250.0, "CHF"))
    }

    @Test
    fun notificationLinks() {
        assertEquals("https://djmetry.com/dashboard#booking", notificationTarget("/dashboard#booking", "https://djmetry.com"))
        assertEquals("https://open.spotify.com/album/1", notificationTarget("https://open.spotify.com/album/1", "https://djmetry.com"))
        assertNull(notificationTarget(null, "https://djmetry.com"))
        assertNull(notificationTarget("javascript:alert(1)", "https://djmetry.com"), "чужие схемы не открываем")
    }
}
