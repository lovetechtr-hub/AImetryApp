package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.endpoints.RadarApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.data.repository.ProfileRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProfileRepositoryTest {

    private val id = "5QnualJTEdYTH1bUfyziGn"
    private val artistMe = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = id))
    private val fanMe = MeResponse(isAuthed = true)

    // Формы — реальные ответы прода (artist, public booking) и из веб-спеки (feed, requests)
    private val routes = mapOf(
        "GET /api/artists/spotify/$id" to (HttpStatusCode.OK to """{"spotifyArtistId":"$id","name":"LovetechMusic","aimetryScore":20.43,"position":1435,"votes":1,"followsCount":6,"city":"Barcelona","country":"ES","isVerified":true,"genres":["melodic house"]}"""),
        "GET /api/me/snapshots" to (HttpStatusCode.OK to """{"snapshots":[{"date":"2026-09-01","score":18.6},{"date":"2026-09-28","score":20.43}]}"""),
        "GET /api/artists/spotify/$id/tracks" to (HttpStatusCode.OK to """{"tracks":[{"spotifyTrackId":"t1","name":"Elephant","albumImageUrl":"x"}]}"""),
        "GET /api/booking/artists/public/$id/booking" to (HttpStatusCode.OK to """{"spotify_artist_id":"$id","count":1,"companies":[{"id":"c1","name":"Booking Machine","country":"TR","city":"Antalya","image_url":"data:image/png;base64,AAA"}]}"""),
        "GET /api/booking/artists/$id/requests" to (HttpStatusCode.OK to """{"requests":[{"id":"r2","status":"artist_finished_performance","event_date":"2026-06-24","payment_amount":10000,"payment_currency":"USD"}]}"""),
        "GET /api/me/release-radar/feed" to (HttpStatusCode.OK to """{"artists":[
            {"spotify_artist_id":"m","artist_name":"Marshmello","latest":[{"album_id":"a1","name":"Happier","release_date":"2026-09-20"},{"album_id":"a0","name":"Old","release_date":"2025-01-01"}]},
            {"spotify_artist_id":"f","artist_name":"FISHER","latest":[{"album_id":"a2","name":"Losing It","release_date":"2026-09-25"}]}]}"""),
    )

    private fun repo(b: FakeBackend) = b.client().let { ProfileRepository(UserApi(it), ArtistApi(it), BookingApi(it), RadarApi(it)) }

    @Test
    fun verifiedArtistGetsFullDashboard() = runTest {
        val b = FakeBackend(routes)
        val d = repo(b).load(artistMe, lang = "ru")

        assertTrue(d.isArtist)
        assertEquals(20.43, d.artist?.djmetryScore)
        assertEquals("ru", b.request("GET", "/api/artists/spotify/$id")!!.url.parameters["lang"])
        assertEquals(2, d.scoreHistory.size)
        assertEquals("Elephant", d.tracks.single().name)
        assertTrue(d.hasBooking)
        assertEquals("Booking Machine", d.bookingCompanies.single().name)
        assertEquals(10000.0, d.bookingRequests.single().payment_amount)
        assertEquals(listOf("Losing It", "Happier", "Old"), d.releases.map { it.release.name }, "новые релизы сверху")
    }

    @Test
    fun fanDoesNotQueryArtistEndpoints() = runTest {
        val b = FakeBackend(routes)
        val d = repo(b).load(fanMe)

        assertFalse(d.isArtist)
        assertFalse(d.hasBooking)
        assertEquals(3, d.releases.size)
        assertNull(b.request("GET", "/api/artists/spotify/$id"))
        assertNull(b.request("GET", "/api/booking/artists/public/$id/booking"))
    }

    @Test
    fun noBookingButtonWithoutCompanies() = runTest {
        val b = FakeBackend(routes + ("GET /api/booking/artists/public/$id/booking" to (HttpStatusCode.OK to """{"count":0,"companies":[]}""")))
        assertFalse(repo(b).load(artistMe).hasBooking)
    }

    @Test
    fun failingSourcesDoNotBreakDashboard() = runTest {
        val broken = routes + mapOf(
            "GET /api/booking/artists/$id/requests" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized"}"""),
            "GET /api/me/release-radar/feed" to (HttpStatusCode.InternalServerError to """{"error":"boom"}"""),
            "GET /api/me/snapshots" to (HttpStatusCode.BadRequest to """{"error":"No artist selected"}"""),
        )
        val d = repo(FakeBackend(broken)).load(artistMe)
        assertEquals("LovetechMusic", d.artist?.name)
        assertTrue(d.bookingRequests.isEmpty())
        assertTrue(d.releases.isEmpty())
        assertTrue(d.scoreHistory.isEmpty())
        assertTrue(d.hasBooking, "компании пришли — кнопка букинга есть")
    }

    @Test
    fun unverifiedClaimIsNotArtist() = runTest {
        val me = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = false, verifiedSpotifyArtistId = id))
        assertFalse(repo(FakeBackend(routes)).load(me).isArtist)
    }

    @Test
    fun verifiedArtistStaysArtistWhenCardFails() = runTest {
        val b = FakeBackend(routes - "GET /api/artists/spotify/$id")
        val d = repo(b).load(artistMe)
        assertTrue(d.isArtist, "сбой сети не превращает артиста в фаната")
        assertNull(d.artist)
    }
}
