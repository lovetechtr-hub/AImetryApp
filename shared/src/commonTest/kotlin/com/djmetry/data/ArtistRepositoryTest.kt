package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.DJMagRanking
import com.djmetry.api.models.DJMagRankingsResponse
import com.djmetry.data.repository.ArtistRepository
import com.djmetry.data.repository.DJMagEntry
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Карточка артиста: формы ответов — реальные с прода (Martin Garrix, 2026-09-28), урезанные. */
class ArtistRepositoryTest {

    private val id = "60d24wfXkVzDSfLS6hyCjZ"

    private val details = """{"spotifyArtistId":"$id","name":"Martin Garrix","imageUrl":"https://i.scdn.co/image/x","followers":15384355,"popularity":78,
        "genres":["edm","progressive house","electronica"],"isVerified":true,"aimetryScore":48.32,"position":4,"followsCount":1,"votes":0,"slug":"martin-garrix",
        "trend":{"score24h":0,"score3d":0,"score7d":0},
        "youtube":{"subscribers":15200000,"views":7169896963,"isOAC":true,"url":"https://www.youtube.com/channel/UC5H_KXkPbEsGs0tFt8R35mA"},
        "socialMedia":{"instagram":"martingarrix","facebook":"martin.garrix","tiktok":"martingarrix","twitter":"MartinGarrix","soundcloud":"martingarrix","appleMusicUrl":"https://music.apple.com/us/artist/martin-garrix/430932944"}}"""

    private val routes = mapOf(
        "GET /api/artists/spotify/$id" to (HttpStatusCode.OK to details),
        "GET /api/artists/spotify/$id/tracks" to (HttpStatusCode.OK to """{"tracks":[{"spotifyTrackId":"23L5","name":"In the Name of Love","albumImageUrl":"https://i.scdn.co/image/c","durationMs":198815,"popularity":84,"externalUrl":"https://open.spotify.com/track/23L5"}]}"""),
        "GET /api/artists/$id/events" to (HttpStatusCode.OK to """{"spotifyArtistId":"$id","artistName":"Martin Garrix","cachedAt":"2026-09-28 13:21:36","events":[
            {"eventId":"2","datetime":"2026-10-31T22:00:00","venue":{"name":"OMNIA Nightclub","city":"Las Vegas","country":"United States"},"offers":[]},
            {"eventId":"1","datetime":"2026-10-16T20:00:00","onSaleDatetime":"2025-12-12T10:00:00","title":null,"url":"https://www.bandsintown.com/e/1","venue":{"name":"Palacio de los Deportes","city":"Mexico City","country":"Mexico"},"offers":[{"type":"Tickets","url":"https://tix/1","status":"available"}]}]}"""),
        "GET /api/booking/artists/public/$id/booking" to (HttpStatusCode.OK to """{"spotify_artist_id":"$id","count":0,"companies":[]}"""),
        "GET /api/djmag/rankings" to (HttpStatusCode.OK to """{"year":2025,"count":2,"rankings":[{"rank":1,"name":"David Guetta","spotifyArtistId":"1Cs0","previousYearRank":2},{"rank":2,"name":"Martin Garrix","spotifyArtistId":"$id","previousYearRank":1}]}"""),
    )

    private fun repo(b: FakeBackend) = b.client().let { ArtistRepository(ArtistApi(it), BookingApi(it)) }

    @Test
    fun loadsFullCardInParallel() = runTest {
        val b = FakeBackend(routes)
        val card = repo(b).load(id, lang = "ru").getOrThrow()

        assertEquals("Martin Garrix", card.details.name)
        assertEquals(48.32, card.details.djmetryScore)
        assertEquals("ru", b.request("GET", "/api/artists/spotify/$id")!!.url.parameters["lang"])
        assertEquals("5", b.request("GET", "/api/artists/spotify/$id/tracks")!!.url.parameters["limit"])
        assertEquals("true", b.request("GET", "/api/djmag/rankings")!!.url.parameters["latest"])
        assertEquals("In the Name of Love", card.tracks.single().name)
        assertEquals(listOf("1", "2"), card.events.map { it.eventId }, "концерты по дате")
        assertEquals(DJMagEntry(rank = 2, year = 2025, previousRank = 1), card.djMag)
        assertFalse(card.hasBooking, "нет агентства — нет кнопки «Забронировать»")
        assertTrue(card.details.youtube!!.isOAC)
    }

    @Test
    fun optionalSectionsFailSoftly() = runTest {
        val onlyDetails = FakeBackend(mapOf("GET /api/artists/spotify/$id" to routes.getValue("GET /api/artists/spotify/$id")))
        val card = repo(onlyDetails).load(id).getOrThrow()
        assertTrue(card.tracks.isEmpty() && card.events.isEmpty() && card.bookingCompanies.isEmpty())
        assertNull(card.djMag)
    }

    @Test
    fun missingArtistIsAnError() = runTest {
        val b = FakeBackend(routes - "GET /api/artists/spotify/$id")
        assertTrue(repo(b).load(id).isFailure)
    }

    @Test
    fun bookingButtonNeedsAgency() = runTest {
        val b = FakeBackend(routes + ("GET /api/booking/artists/public/$id/booking" to (HttpStatusCode.OK to """{"count":1,"companies":[{"id":"c1","name":"Booking Machine"}]}""")))
        assertTrue(repo(b).load(id).getOrThrow().hasBooking)
    }

    @Test
    fun djMagRankingsAreLoadedOncePerSession() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b)
        r.load(id).getOrThrow()
        r.load(id).getOrThrow()
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/djmag/rankings" })
    }

    @Test
    fun djMagFallsBackToRankFromCard() {
        val d = ArtistDetailsResponse(spotifyArtistId = "x", name = "X", djMagRank = 17)
        val other = DJMagRankingsResponse(year = 2025, rankings = listOf(DJMagRanking(rank = 1, name = "Y", spotifyArtistId = "y")))
        assertEquals(DJMagEntry(17, null, null), ArtistRepository.djMagEntry(d, other))
        assertNull(ArtistRepository.djMagEntry(d.copy(djMagRank = null), other))
        assertNull(ArtistRepository.djMagEntry(d.copy(djMagRank = null), null))
    }

    @Test
    fun slugFromSiteLinksResolvesToSpotifyId() = runTest {
        // Концерт-пуши и ссылки сайта ведут на /artist/<slug>
        val b = FakeBackend(routes + ("GET /api/artists/by-slug/calvin-harris" to routes.getValue("GET /api/artists/spotify/$id")))
        val card = repo(b).load("calvin-harris").getOrThrow()
        assertEquals(id, card.details.spotifyArtistId)
        assertNotNull(b.request("GET", "/api/artists/spotify/$id/tracks"))
        assertNull(b.request("GET", "/api/artists/spotify/$id"), "детали по slug уже есть — второй запрос не нужен")
        assertTrue(ArtistRepository.isSpotifyId(id))
        assertFalse(ArtistRepository.isSpotifyId("calvin-harris"))
    }

    @Test
    fun slugLookupNetworkErrorIsNotArtistNotFound() = runTest {
        // Ссылка /artist/<slug> без сети: ошибка экрана, а не slug, подставленный как Spotify id
        val b = FakeBackend(routes + ("GET /api/artists/by-slug/calvin-harris" to (HttpStatusCode.ServiceUnavailable to "{}")))
        assertTrue(repo(b).load("calvin-harris").isFailure)
        assertNull(b.request("GET", "/api/artists/spotify/calvin-harris"))
    }

    @Test
    fun slugIsEscapedInPath() = runTest {
        val b = FakeBackend(routes)
        repo(b).load("../me")
        assertTrue(b.requests.none { it.url.encodedPath == "/api/me" }, "slug из ссылки не уводит запрос на другой эндпоинт")
    }
}
