package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.DJMetryJson
import com.djmetry.api.endpoints.*
import com.djmetry.api.models.*
import com.djmetry.data.booking.*
import com.djmetry.data.repository.BookingRepository
import com.djmetry.data.repository.RadarRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Новый контракт бэкенда (Радар-агрегаты, Букинг: overview, поля заявки, read, months[], файлы) — приложение на нём. */
class BackendContractTest {
    private val ok = HttpStatusCode.OK

    @Test
    fun radarUsesServerConcertsAndUnread() = runTest {
        val b = FakeBackend(mapOf(
            "GET /api/me/concerts" to (ok to """{"concerts":[{"event_id":"ev1","spotify_artist_id":"a1","artist_name":"Anyma","datetime":"2026-10-05T20:00:00","url":"https://t","venue_name":"Club","city":"Berlin","country":"Germany","near":true}],"total":1,"limit":200,"offset":0}"""),
            "GET /api/me/radar/artists" to (ok to """{"artists":[{"spotify_artist_id":"a1","name":"Anyma","unread_releases":2,"unread_concerts":0},{"spotify_artist_id":"a2","name":"B"}],"total":2}"""),
            "POST /api/me/radar/seen" to (ok to """{"ok":true,"updated":2,"kind":"release","spotify_artist_id":"a1"}"""),
        ))
        val c = b.client()
        val repo = RadarRepository(RadarApi(c), ArtistApi(c), NotificationsApi(c), SettingsApi(c))
        val concerts = repo.serverConcerts()!!
        assertEquals("ev1", concerts.single().event.eventId)
        assertEquals("Berlin", concerts.single().event.venue?.city)
        assertTrue(concerts.single().near)
        assertEquals(setOf("a1"), repo.unreadArtists())
        repo.markSeen("release", "a1").getOrThrow()
        assertEquals("""{"kind":"release","spotify_artist_id":"a1"}""", (b.request("POST", "/api/me/radar/seen")!!.body as TextContent).text)
        // Старый бэкенд — null, вкладка обходит подписки сама
        assertNull(RadarRepository(RadarApi(FakeBackend(emptyMap()).client()), ArtistApi(c), NotificationsApi(c), SettingsApi(c)).serverConcerts())
    }

    private val req = """{"id":"r1","status":"accepted","event_date":"2026-11-20","stage":"confirmed","my_role":"company","allowed_statuses":["declined","paid"],"unread":true,"is_read":true,
        "event_timezone":"Europe/Berlin","company":{"id":"c1","name":"BM","slug":"bm","image_url":"https://i/bm.png"},"artists":[]}"""

    @Test
    fun bookingRequestServerFields() {
        val r = DJMetryJson.decodeFromString(BookingRequest.serializer(), req)
        assertTrue(r.isUnread, "серверный флаг роли важнее общего is_read")
        assertEquals(listOf(BookingStatus.DECLINED, BookingStatus.PAID), companyActionsFor(r))
        assertEquals(emptyList(), companyActionsFor(r.copy(status = "new", allowed_statuses = null)), "без allowed_statuses — кнопок нет, своих переходов клиент не держит")
        // Артист: следующий шаг только если сервер его разрешает
        val artist = r.copy(status = "paid", allowed_statuses = listOf("artist_on_the_way"))
        assertEquals(BookingStatus.ON_THE_WAY, artistNextFor(artist, LocalDate(2026, 10, 1)))
        assertNull(artistNextFor(artist.copy(allowed_statuses = emptyList()), LocalDate(2026, 10, 1)))
        assertEquals("https://i/bm.png", r.company?.image_url)
    }

    @Test
    fun bookingOverviewReadAndFiles() = runTest {
        val b = FakeBackend(mapOf(
            "GET /api/booking/me/overview" to (ok to """{"as_company":[{"company_id":"c1","name":"BM","my_role":"owner","total":10,"unread":2}],"as_artist":{"spotify_artist_id":"a1","total":5,"unread":1},"as_requester":{"total":2,"unread":0},"total_unread":3}"""),
            "GET /api/booking/requests/r1" to (ok to """{"request":$req}"""),
            "POST /api/booking/requests/r1/read" to (ok to """{"success":true,"request_id":"r1","role":"company"}"""),
            "GET /api/booking/artists/a1/files" to (ok to """{"spotify_artist_id":"a1","rider":{"signed_url":"/api/booking/artists/a1/rider/download?token=x","filename":"rider.pdf","size":824133,"updated_at":"2026-09-20T10:00:00.000Z"},"press_kit":null}"""),
            "DELETE /api/booking/companies/c1/members/invite/7" to (ok to """{"success":true,"invited_email":"a@b.c"}"""),
        ))
        val repo = BookingRepository(BookingApi(b.client()))
        val roles = repo.roles(MeResponse(isAuthed = true))
        assertEquals(listOf(BookingRole.Requester, BookingRole.Company, BookingRole.Artist), roles.list)
        assertEquals(2, roles.unread[BookingRole.Company]); assertEquals(1, roles.unread[BookingRole.Artist])
        assertEquals("owner", roles.companies.single().my_role)
        val r = DJMetryJson.decodeFromString(BookingRequest.serializer(), req)
        repo.open(BookingRole.Company, r, null).getOrThrow()
        assertEquals("""{"role":"company"}""", (b.request("POST", "/api/booking/requests/r1/read")!!.body as TextContent).text)
        val f = repo.files("a1")!!
        assertEquals("rider.pdf", f.rider?.filename); assertNull(f.press_kit)
        repo.revokeInvite("c1", 7).getOrThrow()
    }

    @Test
    fun earningsMonthsAndDelta() {
        val e = DJMetryJson.decodeFromString(BookingEarnings.serializer(), """{"month":{"by_currency_company_fee_after_tax":{"USD":150}},
            "months":[{"month":"2026-08","by_currency_company_fee_after_tax":{"USD":100}},{"month":"2026-09","by_currency_company_fee_after_tax":{"USD":150,"EUR":20}}],
            "previous":{"month":"2026-08","by_currency_company_fee_after_tax":{"USD":100}},"all_time":{"by_currency":{"USD":250}}}""")
        assertEquals(listOf(100.0, 150.0), serverBars(e.months, BookingRole.Company, beforeTax = false, currency = "USD"))
        assertEquals(50, monthDelta(e, BookingRole.Company, beforeTax = false, currency = "USD"))
        assertNull(monthDelta(e.copy(previous = null), BookingRole.Company, false, "USD"))
        assertNotNull(e.all_time)
    }

    @Test
    fun siteLinksOpenScreens() {
        val base = "https://djmetry.com"
        assertEquals(com.djmetry.ui.profile.NotificationRoute.Artist("anyma"), com.djmetry.ui.profile.routeNotification("https://djmetry.com/artist/anyma", null, null, base))
        assertTrue(com.djmetry.ui.profile.routeNotification("https://djmetry.com/booking/requests/r1", null, null, base) is com.djmetry.ui.profile.NotificationRoute.Booking)
    }
}
