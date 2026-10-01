package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.data.booking.*
import com.djmetry.data.repository.BookingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class BookingTest {
    private val today = LocalDate(2026, 9, 30)

    @Test
    fun stagesLikeTheWebsite() {
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 4, 5), listOf("new", "in_progress", "accepted", "paid", "artist_on_the_way", "artist_at_hotel", "artist_at_venue", "artist_finished_performance").map(::bookingStage))
        assertTrue(isDeclined("declined")); assertEquals(0, bookingStage("declined"))
    }

    @Test
    fun artistPathAndFinishOnlyOnShowDay() {
        // Шаг — из allowed_statuses сервера; своей таблицы переходов у клиента больше нет
        fun r(status: String, date: String, vararg allowed: String) =
            BookingRequest(id = "r", status = status, event_date = date, allowed_statuses = allowed.toList())
        assertEquals("artist_on_the_way", artistNextFor(r("paid", "2026-10-12", "artist_on_the_way", "declined"), today))
        assertEquals("artist_at_hotel", artistNextFor(r("artist_on_the_way", "2026-10-12", "artist_at_hotel", "artist_at_venue"), today))
        assertNull(artistNextFor(r("artist_at_venue", "2026-10-12", "artist_finished_performance"), today), "«выступил» — не раньше дня шоу")
        assertEquals("artist_finished_performance", artistNextFor(r("artist_at_venue", "2026-09-30", "artist_finished_performance"), today))
        assertNull(artistNextFor(r("new", "2026-10-12", "in_progress", "accepted"), today))
        assertNull(artistNextFor(r("paid", "2026-10-12"), today), "сервер ничего не разрешил — кнопки нет")
    }

    @Test
    fun companyActionsAreWhatBackendAllows() {
        // Порядок кнопок — привычный, набор — из allowed_statuses; путевые статусы агентству не показываем
        fun r(vararg allowed: String) = BookingRequest(id = "r", allowed_statuses = allowed.toList())
        assertEquals(listOf("declined", "in_progress", "accepted"), companyActionsFor(r("accepted", "in_progress", "declined")))
        assertEquals(listOf("declined", "paid"), companyActionsFor(r("paid", "declined")))
        assertTrue(companyActionsFor(r("artist_on_the_way")).isEmpty())
    }

    @Test
    fun filtersAndMoney() {
        assertTrue(BookingFilter.OnTour.matches("artist_at_hotel")); assertFalse(BookingFilter.New.matches("paid"))
        assertEquals("12 000 €", moneyLabel(12000.0, "EUR"))
        assertEquals("10 000 $", moneyLabel(10000.0, "usd"))
        assertEquals("870 CHF", moneyLabel(870.4, "CHF"))
        assertNull(moneyLabel(null, "EUR"))
        assertEquals("2026-06-24", bookingDay("2026-06-24 10:00:00"))
    }

    @Test
    fun liveShowIsNearestActive() {
        val list = listOf(
            BookingRequest("a", status = "paid", event_date = "2026-11-15"),
            BookingRequest("b", status = "artist_on_the_way", event_date = "2026-10-01"),
            BookingRequest("c", status = "paid", event_date = "2026-09-01"),
            BookingRequest("d", status = "new", event_date = "2026-10-02"),
        )
        assertEquals("b", com.djmetry.ui.booking.liveShow(list, today)?.id)
        assertNull(com.djmetry.ui.booking.liveShow(listOf(BookingRequest("x", status = "declined", event_date = "2026-10-01")), today))
    }

    // ── Репозиторий ──
    private val req = """{"id":"r1","request_number":24,"status":"new","event_date":"2026-10-12","payment_status":"unpaid","artists":[{"spotify_artist_id":"a1","name":"Anyma","artist_fee_amount":8000}],"is_read":false,"company_name":"Booking Machine"}"""
    private fun backend() = FakeBackend(mapOf(
        "GET /api/booking/companies/my" to (HttpStatusCode.OK to """{"companies":[{"id":"c1","name":"Booking Machine","my_role":"owner"}]}"""),
        "GET /api/booking/my-requests" to (HttpStatusCode.OK to """{"total":1,"requests":[$req]}"""),
        "GET /api/booking/companies/c1/requests" to (HttpStatusCode.OK to """{"requests":[$req]}"""),
        "GET /api/booking/artists/a1/requests" to (HttpStatusCode.OK to """{"requests":[${req.replace("\"new\"", "\"paid\"")}]}"""),
        "PATCH /api/booking/requests/r1" to (HttpStatusCode.OK to """{"request":${req.replace("\"new\"", "\"accepted\"")}}"""),
        "PATCH /api/booking/artists/a1/requests/r1" to (HttpStatusCode.OK to """{"request":${req.replace("\"new\"", "\"artist_on_the_way\"")}}"""),
        "DELETE /api/booking/my-requests/r1" to (HttpStatusCode.OK to """{"success":true}"""),
    ))
    private fun repo(b: FakeBackend) = BookingRepository(BookingApi(b.client()))

    @Test
    fun rolesFromAccount() = runTest {
        val roles = repo(backend()).roles(MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1")))
        assertEquals(listOf(BookingRole.Requester, BookingRole.Company, BookingRole.Artist), roles.list)
        assertEquals(BookingRole.Artist, roles.default)
        assertEquals(BookingRole.Requester, repo(FakeBackend(emptyMap())).roles(MeResponse(isAuthed = true)).default)
    }

    @Test
    fun requestsPerRoleAndStatusChanges() = runTest {
        val b = backend(); val r = repo(b)
        assertEquals("Anyma", r.requests(BookingRole.Company, "c1", null).getOrThrow().single().artists.single().name)
        assertEquals("paid", r.requests(BookingRole.Artist, null, "a1").getOrThrow().single().status)
        assertEquals("accepted", r.setCompanyStatus("r1", "accepted").getOrThrow().status)
        assertEquals("""{"status":"accepted"}""", (b.request("PATCH", "/api/booking/requests/r1")!!.body as TextContent).text)
        assertEquals("artist_on_the_way", r.setArtistStatus("a1", "r1", "artist_on_the_way", "train").getOrThrow().status)
        assertEquals("""{"status":"artist_on_the_way","travel_transport":"train"}""", (b.request("PATCH", "/api/booking/artists/a1/requests/r1")!!.body as TextContent).text)
        r.cancel("r1").getOrThrow()
        assertNotNull(b.request("DELETE", "/api/booking/my-requests/r1"))
    }
}
