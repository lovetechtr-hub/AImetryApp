package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.DJMetryJson
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.BookingCompanyDetail
import com.djmetry.api.models.BookingEarnings
import com.djmetry.api.models.BookingPerformance
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.BookingRequestArtist
import com.djmetry.data.booking.*
import com.djmetry.data.repository.BookingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Кабинет букинга (вариант A) и форма заявки (вариант B): разбор ответов, суммы, столбики, проверка формы, запросы. */
class BookingCabinetTest {
    private val today = LocalDate(2026, 9, 30)
    private val ok = HttpStatusCode.OK

    private val earningsJson = """{"booking_company_id":"c1","company_tax_percent_applied":10,
        "week":{"by_currency":{"USD":5000},"by_currency_company_fee":{"USD":1000},"by_currency_artist_fee":{"USD":4000},"total_requests":2,
                "by_currency_company_fee_after_tax":{"USD":900},"by_currency_artist_fee_after_tax":{"USD":3400},"by_currency_after_tax":{"USD":4300}},
        "month":{"by_currency_company_fee":{"USD":1000,"EUR":300},"by_currency_company_fee_after_tax":{"USD":900,"EUR":270},"by_currency_artist_fee":{},"by_currency_artist_fee_after_tax":{},"total_requests":3},
        "year":{"total_requests":0},
        "all_time":{"by_currency_artist_fee":{"EUR":48900},"by_currency_artist_fee_after_tax":{"EUR":41565},"total_requests":9}}"""

    @Test
    fun earningsByRoleAndTax() {
        val e = DJMetryJson.decodeFromString(BookingEarnings.serializer(), earningsJson)
        assertEquals(mapOf("USD" to 900.0), ownEarnings(e.period(EarningsRange.Week), BookingRole.Company, beforeTax = false))
        assertEquals(mapOf("USD" to 1000.0), ownEarnings(e.period(EarningsRange.Week), BookingRole.Company, beforeTax = true))
        assertEquals(mapOf("USD" to 3400.0), ownEarnings(e.period(EarningsRange.Week), BookingRole.Artist, beforeTax = false))
        assertEquals(mapOf("EUR" to 41565.0), ownEarnings(e.period(EarningsRange.AllTime), BookingRole.Artist, beforeTax = false))
        assertTrue(ownEarnings(e.period(EarningsRange.Year), BookingRole.Company, false).isEmpty())
        val (main, rest) = splitCurrencies(ownEarnings(e.month, BookingRole.Company, false))
        assertEquals("USD" to 900.0, main)
        assertEquals(listOf("EUR" to 270.0), rest)
        // «Всё время» — только у артиста (у агентства бэкенд его не считает)
        assertEquals(3, earningsRanges(BookingRole.Company).size)
        assertEquals(EarningsRange.AllTime, earningsRanges(BookingRole.Artist).last())
    }

    @Test
    fun flagsAsNumbersOrBooleans() {
        val d = DJMetryJson.decodeFromString(
            BookingCompanyDetail.serializer(),
            """{"company":{"id":"c1","name":"BM","is_verified":1,"default_company_tax_percent":10},"artists":[
               {"spotify_artist_id":"a1","name":"A","approved":1},{"spotify_artist_id":"a2","approved":0},{"spotify_artist_id":"a3","approved":true}],"myRole":"owner"}""",
        )
        assertEquals(listOf(true, false, true), d.artists.map { it.approved })
        assertTrue(d.company.is_verified)
        assertEquals("owner", d.myRole)
    }

    private fun paid(month: String, fee: Double, status: String = "paid", currency: String = "USD", percent: Double? = null) = BookingRequest(
        id = month + fee, event_date = "$month-15", payment_status = status, payment_currency = currency, payment_percent = percent,
        company_fee_amount = fee, artists = listOf(BookingRequestArtist("a1", artist_fee_amount = fee * 4)),
    )

    @Test
    fun monthBarsFromPaidRequests() {
        val list = listOf(
            paid("2026-09", 100.0), paid("2026-09", 50.0, status = "partially_paid", percent = 50.0),
            paid("2026-07", 200.0), paid("2026-08", 999.0, status = "unpaid"), paid("2026-09", 70.0, currency = "EUR"),
            paid("2025-01", 500.0),
        )
        assertEquals(listOf(0.0, 0.0, 0.0, 200.0, 0.0, 125.0), monthlyBars(list, BookingRole.Company, null, "USD", today))
        assertEquals(listOf(0.0, 0.0, 0.0, 800.0, 0.0, 500.0), monthlyBars(list, BookingRole.Artist, "a1", "USD", today))
        assertEquals(6, monthlyBars(emptyList(), BookingRole.Company, null, null, today).size)
    }

    @Test
    fun performancesGroupedByCountry() {
        val m = performanceCountries(listOf(
            BookingPerformance("1", event_country = "DE"), BookingPerformance("2", event_country = "de"), BookingPerformance("3", event_country = "ES"),
            BookingPerformance("4", event_country = "Germany"), BookingPerformance("5"),
        ))
        assertEquals(listOf("DE" to 2, "ES" to 1), m.map { it.iso to it.visits })
        assertEquals(1.0, m.first().share)
    }

    @Test
    fun requestFormValidationLikeTheWebsite() {
        assertEquals(RequestField.entries.toSet(), requestFormErrors(RequestForm(), today))
        val ok = RequestForm("c1", setOf("a1"), "Festival", "2026-11-20", "DE", "Berlin", "500", "Set 90 min")
        assertTrue(requestFormErrors(ok, today).isEmpty())
        assertEquals(setOf(RequestField.Date), requestFormErrors(ok.copy(date = "2026-09-01"), today))
        assertEquals(setOf(RequestField.Guests), requestFormErrors(ok.copy(guests = "0"), today))
        assertEquals(setOf(RequestField.Place), requestFormErrors(ok.copy(city = " "), today))
        assertEquals(setOf(RequestField.Message), requestFormErrors(ok.copy(message = "x".repeat(REQUEST_MESSAGE_MAX + 1)), today))
        assertEquals(99.0, clampTax(150.0)); assertEquals(0.0, clampTax(-3.0))
    }

    private val req = """{"id":"r1","request_number":7,"status":"new","company":{"id":"c1","name":"BM"},"artists":[{"spotify_artist_id":"a1","name":"A"}]}"""
    private fun backend() = FakeBackend(mapOf(
        "POST /api/booking/requests" to (HttpStatusCode.Created to """{"success":true,"request":$req}"""),
        "PUT /api/booking/companies/c1" to (ok to """{"success":true}"""),
        "PUT /api/booking/companies/c1/artist-tax-defaults" to (ok to """{"artist_tax_defaults":[]}"""),
        "POST /api/booking/companies/c1/artist-confirm-token" to (ok to """{"success":true,"artist_confirm_token":"a1b2c3d4e5f6a1b2c3d4e5f6"}"""),
        "PATCH /api/booking/companies/c1/members/u2" to (ok to """{"success":true}"""),
        "GET /api/booking/artists/a1/requests" to (ok to """{"requests":[$req]}"""),
        "GET /api/booking/artists/a1/companies" to (ok to """{"companies":[{"company_id":"c1","approved":true,"company":{"id":"c1","name":"BM","image_url":"https://x/logo.png"}}]}"""),
        "GET /api/booking/confirm-by-token/preview" to (ok to """{"company":{"id":"c1","name":"BM"}}"""),
        "GET /api/booking/companies/c1/earnings" to (ok to earningsJson),
        "GET /api/booking/artists/a1/rider" to (HttpStatusCode.NoContent to ""),
        "GET /api/booking/artists/a1/press-kit" to (ok to """{"press_kit_url":"booking/a1/press.pdf"}"""),
    ))
    private fun repo(b: FakeBackend) = BookingRepository(BookingApi(b.client()), ArtistEditorApi(b.client()))

    @Test
    fun createRequestSendsSiteShape() = runTest {
        val b = backend()
        val f = RequestForm("c1", setOf("a1"), " Festival ", "2026-11-20", "de", "Berlin", "1200", " Set 90 min ")
        assertEquals(7, repo(b).createRequest(f, "Germany").getOrThrow().number)
        assertEquals(
            """{"booking_company_id":"c1","spotify_artist_ids":["a1"],"event_type":"Festival","event_date":"2026-11-20","event_location":"Berlin, Germany","event_country":"DE","expected_attendees":1200,"message":"Set 90 min"}""",
            (b.request("POST", "/api/booking/requests")!!.body as TextContent).text,
        )
    }

    @Test
    fun cabinetCalls() = runTest {
        val b = backend(); val r = repo(b)
        r.saveTaxes("c1", 12.0, true, mapOf("a1" to 120.0)).getOrThrow()
        assertEquals("""{"default_company_tax_percent":12.0,"default_artist_calculates_own_tax":true}""", (b.request("PUT", "/api/booking/companies/c1")!!.body as TextContent).text)
        assertEquals("""{"artist_tax_defaults":[{"spotify_artist_id":"a1","default_artist_tax_percent":99.0}]}""",
            (b.request("PUT", "/api/booking/companies/c1/artist-tax-defaults")!!.body as TextContent).text)
        assertEquals("a1b2c3d4e5f6a1b2c3d4e5f6", r.artistToken("c1").getOrThrow())
        r.updateMember("c1", "u2", false, listOf("DE", "AT")).getOrThrow()
        assertEquals("""{"accept_all_requests":false,"responsible_regions":["DE","AT"]}""", (b.request("PATCH", "/api/booking/companies/c1/members/u2")!!.body as TextContent).text)
        assertEquals("BM", r.previewToken(" tok ").getOrThrow().name)
        assertEquals(mapOf("USD" to 900.0), ownEarnings(r.earnings(BookingRole.Company, "c1", null).getOrThrow().week, BookingRole.Company, false))
        // Райдера нет (204), пресс-кит есть
        assertFalse(r.hasDoc("a1", com.djmetry.api.endpoints.BookingDoc.Rider))
        assertTrue(r.hasDoc("a1", com.djmetry.api.endpoints.BookingDoc.PressKit))
    }

    @Test
    fun artistRequestsGetAgencyLogo() = runTest {
        val list = repo(backend()).requests(BookingRole.Artist, null, "a1").getOrThrow()
        assertEquals("https://x/logo.png", list.single().company_image_url)
    }
}
