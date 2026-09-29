package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.DjMapApi
import com.djmetry.api.models.MapEventPoint
import com.djmetry.api.models.MapVenue
import com.djmetry.api.models.MapVenueArtist
import com.djmetry.data.djmap.*
import com.djmetry.data.repository.DjMapRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Карта диджеев (спека §16): правила сайта — DjWorldMapPage.tsx, djmap/…, genreColors.ts. */
class DjMapTest {
    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private fun p(id: String, artist: String, dt: String?, city: String? = "Ibiza", lat: Double = 38.9, lng: Double = 1.4) =
        MapEventPoint(id, artist, artist.uppercase(), datetime = dt, venue_city = city, venue_country = "Spain", lat = lat, lng = lng)

    @Test
    fun paramsLikeSite() {
        val f = MapFilters(genre = "techno", country = "United States", from = LocalDate(2026, 10, 1))
        val b = Bounds(-10.123456, 35.0, 20.0, 60.0)
        assertEquals(listOf("genre" to "techno", "country" to "United States", "from" to "2026-10-01", "bbox" to "-10.1235,35.0,20.0,60.0", "limit" to "3000"),
            f.performanceParams(b, 3000), "без zoom — сервер отдаёт точки; bbox запад,юг,восток,север; страна — имя")
        assertEquals("artist_id" to "a1", f.artistParams("a1").first())
        assertEquals(listOf("level" to "venue", "limit" to "400"), f.densityParams(DensityLevel.Venue), "жанр на площадках не применяется")
        assertEquals(listOf("level" to "city", "genre" to "techno", "limit" to "300"), f.densityParams(DensityLevel.City))
        assertEquals(listOf("type" to "club", "zoom" to "7", "limit" to "800"), MapFilters(type = VenueTypeFilter.Club).venuesParams(null, 7, 800))
        assertEquals(3, f.activeCount)
    }

    @Test
    fun densityLevelsByZoom() {
        assertEquals(DensityLevel.Country, densityLevel(3.4)); assertEquals(DensityLevel.City, densityLevel(3.6), "зум округляется")
        assertEquals(DensityLevel.City, densityLevel(5.4)); assertEquals(DensityLevel.Venue, densityLevel(5.5))
    }

    @Test
    fun boundsPaddingAndContainment() {
        val b = Bounds(0.0, 0.0, 10.0, 10.0)
        val big = b.padded()
        assertEquals(Bounds(-2.0, -2.0, 12.0, 12.0), big)
        assertTrue(Bounds(1.0, 1.0, 9.0, 11.0) in big, "небольшой сдвиг — без перезапроса")
        assertFalse(Bounds(5.0, 5.0, 15.0, 15.0) in big)
        assertEquals(Bounds(-180.0, -85.0, 180.0, 85.0), Bounds(-179.0, -84.0, 179.0, 84.0).padded(), "не выходим за мир")
    }

    @Test
    fun onePointPerArtistNearestUpcomingElseLatestPast() {
        val pts = listOf(p("e1", "a", "2026-09-01T20:00:00"), p("e2", "a", "2026-10-05T20:00:00"), p("e3", "a", "2026-10-01T20:00:00"),
            p("e4", "b", "2026-08-01T20:00:00"), p("e5", "b", "2026-09-10T20:00:00"))
        assertEquals(listOf("e3", "e5"), onePerArtist(pts, now).map { it.event_id }, "a — ближайшее будущее, b — свежее прошедшее")
    }

    @Test
    fun eventDates() {
        assertEquals(Instant.parse("2026-10-04T22:00:00Z"), eventInstant("2026-10-04T22:00:00"))
        assertEquals(Instant.parse("2026-10-04T22:00:00Z"), eventInstant("2026-10-04T22:00:00Z"))
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"), eventInstant("2026-10-04"))
        assertNull(eventInstant("")); assertNull(eventInstant("вчера"))
    }

    @Test
    fun tourStopsCollapseRepeats() {
        val t = listOf(p("1", "a", null, "Ibiza"), p("2", "a", null, "Ibiza"), p("3", "a", null, null), p("4", "a", null, "Cairo"))
        assertEquals(listOf("Ibiza", "Spain", "Cairo"), tourStops(t).map { it.city }, "нет города — страна")
        assertEquals(emptyList(), tourStops(t.take(2)), "одна остановка — ленты нет")
    }

    @Test
    fun genreColorsMatchSite() {
        assertEquals(0xFF22C1A6, genreColor("Tech House")); assertEquals(0xFF5A7BFF, genreColor("techno"))
        assertEquals(0xFF4338CA, genreColor("hard techno"), "hard techno раньше techno")
        assertEquals(0xFF8B5CF6, genreColor("melodic techno"), "первое совпадение по таблице")
        assertEquals(0xFF94A3B8, genreColor("")); assertEquals(0xFF94A3B8, genreColor(null))
        assertEquals(genreColor("us"), genreColor("US"), "ISO стран — стабильный цвет из палитры")
        // Хеш как в JS: h = h*31 + code (uint32), индекс h % 12: "us" → (117*31+115) % 12 = 3742 % 12 = 10
        assertEquals(0xFFFACC15, genreColor("us"))
        assertEquals("techno", dominantGenre(mapOf("house" to 3, "techno" to 9))); assertNull(dominantGenre(emptyMap()))
        assertEquals(0.9f, densityAlpha(10, 10), 1e-6f); assertEquals(0.78f, originAlpha(5, 5))
    }

    @Test
    fun venues() {
        val v = MapVenue("v1", "Pacha", address = "Av. 8 d'Agost", city = "Ibiza", country = "Spain", lat = 38.9, lng = 1.4, venue_type = "club", google_place_id = "PID")
        assertEquals(VenueKind.Club, venueKind(v)); assertEquals(VenueKind.Top, venueKind(v.copy(is_top = true))); assertEquals(VenueKind.Club, venueKind(v.copy(venue_type = "venue")))
        assertEquals("https://www.google.com/maps/search/?api=1&query=Pacha%2C%20Av.%208%20d'Agost%2C%20Ibiza%2C%20Spain&query_place_id=PID", googleMapsUrl(v))
        assertEquals("https://www.google.com/maps/search/?api=1&query=38.9%2C1.4", googleMapsUrl(v.copy(address = null, google_place_id = null)))
        assertTrue(conflictsWithType(v.copy(venue_type = "festival"), VenueTypeFilter.Club)); assertTrue(conflictsWithType(v, VenueTypeFilter.Festival))
        assertFalse(conflictsWithType(v, VenueTypeFilter.All))
    }

    @Test
    fun lineupOrderAndBadge() {
        val a = listOf(
            MapVenueArtist("p1", "Past", datetime = "2026-09-01T20:00:00"), MapVenueArtist("u2", "Later", datetime = "2026-10-10T20:00:00"),
            MapVenueArtist("u1", "Soon", datetime = "2026-09-29T15:00:00"), MapVenueArtist("u1", "Soon dup", datetime = "2026-09-29T15:00:00"),
            MapVenueArtist("p2", "Older", datetime = "2026-08-01T20:00:00"),
        )
        val l = lineup(a, now)
        assertEquals(listOf("Soon", "Later", "Past", "Older"), l.map { it.artist.name })
        assertEquals(LineupBadge.Now, l[0].badge, "старт в ближайшие 6 часов — «Сейчас»"); assertNull(l[1].badge)
        assertEquals(LineupBadge.Next, lineup(a.drop(2).take(1).map { it.copy(datetime = "2026-10-02T20:00:00") }, now)[0].badge)
        assertNull(lineup(listOf(a[0]), now)[0].badge, "только прошедшие — без бейджа")
    }

    @Test
    fun arcsBendAndSkipNearDuplicates() {
        val tour = listOf(p("1", "a", null, lat = 37.39, lng = -5.98), p("2", "a", null, lat = 43.6, lng = 1.44), p("3", "a", null, lat = 43.62, lng = 1.45))
        val arcs = tourArcs(tour, zoom = 4.0)
        assertEquals(1, arcs.size, "почти совпадающие точки — без дуги")
        val arc = arcs.single()
        assertEquals(65, arc.line.size)
        assertEquals(-5.98, arc.line.first().first, 1e-6); assertEquals(43.6, arc.line.last().second, 1e-6)
        val straightMidLon = (-5.98 + 1.44) / 2
        assertTrue(kotlin.math.abs(arc.mid.first - straightMidLon) > 0.3, "дуга изогнута, а не прямая")
        assertTrue(arc.headingDeg in 0.0..90.0, "Севилья → Тулуза: на северо-восток")
    }

    @Test
    fun shareUrl() {
        assertEquals("https://djmetry.com/map", mapShareUrl("https://djmetry.com", MapLayer.Performances, MapFilters(), null))
        assertEquals("https://djmetry.com/map?layer=venues&country=United%20States&type=festival",
            mapShareUrl("https://djmetry.com", MapLayer.Venues, MapFilters(country = "United States", type = VenueTypeFilter.Festival), null))
        assertEquals("https://djmetry.com/map?artist_id=a1", mapShareUrl("https://djmetry.com", MapLayer.Performances, MapFilters(), "a1"))
    }

    @Test
    fun repositoryCachesFor120Seconds() = runTest {
        var t = now
        val b = FakeBackend(mapOf("GET /api/map/filters" to (HttpStatusCode.OK to """{"countries":[{"country":"Spain","count":5}],"genres":["techno"]}"""),
            "GET /api/map/dj/a1/tour" to (HttpStatusCode.OK to """{"spotifyArtistId":"a1","points":[{"event_id":"e","spotify_artist_id":"a1","artist_name":"A","lat":1.0,"lng":2.0}],"total":1}""")))
        val repo = DjMapRepository(DjMapApi(b.client()), clock = { t })
        assertEquals("Spain", repo.filters().getOrThrow().countries.single().country)
        repo.filters(); assertEquals(1, b.requests.size)
        t += 121.seconds; repo.filters(); assertEquals(2, b.requests.size, "через 120 с — заново")
        assertEquals(1, repo.tour("a1").getOrThrow().points.size, "camelCase spotifyArtistId не мешает")
    }
}
