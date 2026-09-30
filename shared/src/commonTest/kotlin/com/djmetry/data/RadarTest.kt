package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.*
import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.EventVenue
import com.djmetry.api.models.ReleaseRadarFeedArtist
import com.djmetry.data.radar.*
import com.djmetry.data.repository.RadarRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class RadarTest {
    // ── «Рядом» как на бэкенде ──
    private val barcelona = RadarLocation("Barcelona", "ES", listOf("Spain", "Испания"))

    @Test fun cityAndCountryMatch() = assertTrue(eventNearUser(EventVenue(city = "Barcelona", country = "Spain"), barcelona))
    @Test fun sameCityOtherCountryIsNotNear() = assertFalse(eventNearUser(EventVenue(city = "Barcelona", country = "Venezuela"), barcelona))
    @Test fun otherCityIsNotNear() = assertFalse(eventNearUser(EventVenue(city = "Madrid", country = "Spain"), barcelona))
    @Test fun countryOnlyMatchesByName() = assertTrue(eventNearUser(EventVenue(city = "Madrid", country = "Spain"), RadarLocation(null, "ES", listOf("Spain"))))
    @Test fun shortIsoNeverMatchesBySubstring() = assertFalse(eventNearUser(EventVenue(city = "Stockholm", country = "Sweden"), RadarLocation(null, "DE")))
    @Test fun unknownLocationIsNeverNear() = assertFalse(eventNearUser(EventVenue(city = "Berlin", country = "Germany"), RadarLocation(null, null)))

    // ── «Истории», группы, релизы ──
    private fun a(id: String, name: String, total: Int = 0, latest: Int = 0, older: Boolean = false) = ReleaseRadarFeedArtist(
        id, name, latest = List(latest) { com.djmetry.api.models.ReleaseRadarRelease("$id-$it", "R$it") }, total_releases = total, showing_older = older,
    )

    @Test
    fun storiesUnreadFirstThenSoonConcertThenByName() {
        val list = listOf(a("1", "zeta"), a("2", "Alpha"), a("3", "beta"), a("4", "Gamma"))
        assertEquals(listOf("3", "4", "2", "1"), storyOrder(list, unread = setOf("3"), soonConcert = setOf("4")).map { it.spotify_artist_id })
    }

    @Test
    fun allButtonOnlyWhenMoreThanShown() {
        assertTrue(hasMoreReleases(a("1", "A", total = 11, latest = 5)))
        assertFalse(hasMoreReleases(a("1", "A", total = 5, latest = 5)))
        assertFalse(hasMoreReleases(a("1", "A", total = 0, latest = 5, older = true)), "старые релизы — без «Все»")
    }

    @Test
    fun freshReleaseWithinTwoWeeksFullDatesOnly() {
        val today = LocalDate(2026, 9, 30)
        assertTrue(isFreshRelease("2026-09-20", today))
        assertFalse(isFreshRelease("2026-09-01", today))
        assertFalse(isFreshRelease("2026-09", today))
        assertFalse(isFreshRelease("2026-10-05", today), "будущие — не NEW")
    }

    @Test
    fun concertsGroupedByMonthInDateOrder() {
        fun c(dt: String) = RadarConcert("x", "X", null, ArtistEvent(eventId = dt, datetime = dt), near = false)
        val g = groupByMonth(listOf(c("2026-11-02T20:00:00"), c("2026-10-25T20:00:00"), c("2026-10-12T20:00:00")))
        assertEquals(listOf("2026-10", "2026-11"), g.map { it.first })
        assertEquals("2026-10-12T20:00:00", g.first().second.first().event.datetime)
    }

    @Test fun searchIgnoresCase() { assertTrue(matchesArtist("Alan Walker", "walk")); assertTrue(matchesArtist("Alok", " ")); assertFalse(matchesArtist("Alok", "zed")) }

    // ── Репозиторий ──
    private fun repo(b: FakeBackend): RadarRepository {
        val c = b.client()
        return RadarRepository(RadarApi(c), ArtistApi(c), NotificationsApi(c), SettingsApi(c), now = { kotlin.time.Instant.parse("2026-09-30T10:00:00Z") })
    }

    @Test
    fun concertsFromEachFollowedArtistUpcomingOnlyAndNear() = runTest {
        val b = FakeBackend(mapOf(
            "GET /api/artists/a1/events" to (HttpStatusCode.OK to """{"events":[{"eventId":"e1","datetime":"2026-10-12T23:00:00","venue":{"city":"Barcelona","country":"Spain"}},{"eventId":"old","datetime":"2026-09-01T20:00:00"}]}"""),
            "GET /api/artists/a2/events" to (HttpStatusCode.OK to """{"events":[{"eventId":"e2","datetime":"2026-10-05T20:00:00","venue":{"city":"Berlin","country":"Germany"}}]}"""),
            "GET /api/artists/a3/events" to (HttpStatusCode.NotFound to """{"error":"artist_not_found"}"""),
        ))
        var last = 0 to 0
        val list = repo(b).concerts(listOf(a("a1", "A1"), a("a2", "A2"), a("a3", "A3")), barcelona) { d, t -> last = d to t }
        assertEquals(listOf("e2", "e1"), list.map { it.event.eventId }, "только будущие, ближайшие первыми; упавший артист не ломает список")
        assertEquals(listOf(false, true), list.map { it.near })
        assertEquals(3 to 3, last)
    }

    @Test
    fun eventsAreCachedBetweenCalls() = runTest {
        val b = FakeBackend(mapOf("GET /api/artists/a1/events" to (HttpStatusCode.OK to """{"events":[]}""")))
        val r = repo(b)
        r.concerts(listOf(a("a1", "A1")), barcelona); r.concerts(listOf(a("a1", "A1")), barcelona)
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/artists/a1/events" })
    }

    @Test
    fun unreadArtistsFromNotificationMeta() = runTest {
        val b = FakeBackend(mapOf("GET /api/me/notifications" to (HttpStatusCode.OK to """{"items":[
            {"id":"1","type":"release_radar","read":false,"meta":{"spotify_artist_id":"a1"}},
            {"id":"2","type":"release_radar","read":true,"meta":{"spotify_artist_id":"a2"}},
            {"id":"3","type":"concert","read":false}]}""")))
        assertEquals(setOf("a1"), repo(b).unreadArtists())
    }

    @Test
    fun artistReleasesSendsSearchSortAndPage() = runTest {
        val b = FakeBackend(mapOf("GET /api/me/release-radar/artist/a1/releases" to (HttpStatusCode.OK to """{"releases":[{"album_id":"x","name":"Golden Bird","album_type":"single","release_date":"2026-08-06"}],"total":11}""")))
        val res = repo(b).artistReleases("a1", " bird ", "name", 24).getOrThrow()
        assertEquals(11, res.total)
        val p = b.request("GET", "/api/me/release-radar/artist/a1/releases")!!.url.parameters
        assertEquals("bird", p["q"]); assertEquals("name", p["sort"]); assertEquals("24", p["offset"]); assertEquals("24", p["limit"])
    }

    /** Уведомление о релизе → артист и релиз: из ссылки сайта или из meta; остальное — не релиз. */
    @Test
    fun releaseLinks() {
        assertEquals(com.djmetry.data.radar.ReleaseOpen("a1", "al9"), com.djmetry.data.radar.releaseLink("/dashboard/music/release-radar?artist=a1&album=al9"))
        val meta = com.djmetry.api.DJMetryJson.parseToJsonElement("""{"spotify_artist_id":"a2","album_id":"x","artist_name":"Alok"}""") as kotlinx.serialization.json.JsonObject
        assertEquals(com.djmetry.data.radar.ReleaseOpen("a2", "x", "Alok"), com.djmetry.data.radar.releaseLink(null, "release_radar", meta))
        assertNull(com.djmetry.data.radar.releaseLink("/artist/abc"))
        assertNull(com.djmetry.data.radar.releaseLink("/dashboard#booking", "booking"))
    }
}
