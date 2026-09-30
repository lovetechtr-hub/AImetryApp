package com.djmetry.push

import com.djmetry.api.models.ArtistEvent
import com.djmetry.data.booking.BookingOpen
import com.djmetry.data.radar.RadarConcert
import com.djmetry.data.radar.ReleaseOpen
import com.djmetry.ui.profile.ConcertOpen
import com.djmetry.ui.profile.NotificationRoute
import com.djmetry.ui.profile.routeNotification
import com.djmetry.ui.radar.concertIndex
import kotlin.test.*

/**
 * Пуш и строка колокольчика ведут в одно место — по `type` и полям пуша (плоская meta бэкенда):
 * релиз → релиз в списке артиста, концерт → Радар, букинг → заявка, остальное — артист или сайт.
 */
class NotificationRouteTest {
    private val base = "https://djmetry.com"
    private fun route(url: String?, data: Map<String, String>) = OpenedPush(url, data).let { routeNotification(it.url, it.type, it.meta, base) }

    @Test
    fun releasePushOpensTheRelease() {
        assertEquals(NotificationRoute.Release(ReleaseOpen("a1", "al9")),
            route("/dashboard/music/release-radar?artist=a1&album=al9", mapOf("type" to "release_radar", "spotify_artist_id" to "a1", "album_id" to "al9")))
    }

    @Test
    fun concertPushOpensConcertInRadarNotArtistCard() {
        assertEquals(NotificationRoute.Concert(ConcertOpen("a2", "ev7")),
            route("/artist/cloonee", mapOf("type" to "concert", "spotify_artist_id" to "a2", "event_id" to "ev7")))
        // Старый пуш без полей — хотя бы Радар с артистом из ссылки
        assertEquals(NotificationRoute.Concert(ConcertOpen("cloonee", null)), route("/artist/cloonee", mapOf("type" to "concert")))
    }

    @Test
    fun bookingPushOpensTheRequest() {
        assertEquals(NotificationRoute.Booking(BookingOpen("r5", "c1", null)),
            route("/dashboard#booking", mapOf("type" to "booking", "request_id" to "r5", "company_id" to "c1")))
    }

    @Test
    fun otherPushes() {
        assertEquals(NotificationRoute.Artist("abc"), route("/artist/abc", mapOf("type" to "pre_save")))
        assertEquals(NotificationRoute.Web("https://djmetry.com/pricing"), route("/pricing", emptyMap()))
        PushTokens.onNotificationOpened(null, emptyMap())
        assertNull(PushTokens.opened.value)
    }

    @Test
    fun concertPositionInLazyList() {
        fun c(id: String, day: String) = RadarConcert("a", "A", null, ArtistEvent(id, "2026-$day"), false)
        val list = listOf(c("1", "10-01T20:00:00"), c("2", "10-05T20:00:00"), c("3", "11-02T20:00:00"))
        // Телефон: шапка, сегменты, заголовок концертов (3) → «ОКТ» (3) → 1 (4), 2 (5) → «НОЯ» (6) → 3 (7)
        assertEquals(7, concertIndex(list, list[2], headItems = 3))
        assertEquals(4, concertIndex(list, list[0], headItems = 3))
        assertEquals(2, concertIndex(list, list[0], headItems = 1))
    }
}
