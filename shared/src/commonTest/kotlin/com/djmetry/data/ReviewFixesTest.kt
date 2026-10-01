package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.AudienceApi
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.endpoints.DjMapApi
import com.djmetry.api.models.EarningsMonth
import com.djmetry.data.booking.BookingRole
import com.djmetry.data.booking.serverBars
import com.djmetry.data.repository.AudienceRepository
import com.djmetry.data.repository.DjMapRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Точечные баги из ревью (пакет 4): 204 без тела, валюты заработка, кэш карты. */
class ReviewFixesTest {
    @Test
    fun deletingSegmentAnswered204IsSuccess() = runTest {
        // Бэкенд: res.status(204).send() — пустое тело без Content-Type
        val b = FakeBackend(mapOf("DELETE /api/audience/segments/s1" to (HttpStatusCode.NoContent to "")))
        assertTrue(AudienceRepository(AudienceApi(b.client())).deleteSegment("s1").isSuccess)
    }

    @Test
    fun missingRiderIs204AndMeansNotUploaded() = runTest {
        val b = FakeBackend(mapOf("GET /api/booking/artists/a1/rider" to (HttpStatusCode.NoContent to "")))
        assertNull(ArtistEditorApi(b.client()).doc("a1", BookingDoc.Rider).getOrThrow())
    }

    @Test
    fun riderNetworkErrorIsNotNotUploaded() = runTest {
        val b = FakeBackend(mapOf("GET /api/booking/artists/a1/rider" to (HttpStatusCode.InternalServerError to "{}")))
        assertIs<ApiException>(ArtistEditorApi(b.client()).doc("a1", BookingDoc.Rider).exceptionOrNull())
    }

    @Test
    fun barsOfOtherCurrencyAreZeroNotSummed() {
        val months = listOf(
            EarningsMonth("2026-08", by_currency_artist_fee_after_tax = mapOf("EUR" to 1000.0)),
            EarningsMonth("2026-09", by_currency_artist_fee_after_tax = mapOf("USD" to 5000.0)),
        )
        assertEquals(listOf(1000.0, 0.0), serverBars(months, BookingRole.Artist, beforeTax = false, currency = "EUR"))
        assertEquals(listOf(1000.0, 5000.0), serverBars(months, BookingRole.Artist, beforeTax = false, currency = null))
    }

    @Test
    fun mapCacheIsBounded() = runTest {
        var now = kotlin.time.Instant.parse("2026-10-01T10:00:00Z")
        val b = FakeBackend(mapOf("GET /api/map/event-density" to (HttpStatusCode.OK to """{"cells":[]}""")))
        val repo = DjMapRepository(DjMapApi(b.client())) { now }
        repeat(DjMapRepository.MAX_ENTRIES + 40) { i -> repo.density(listOf("bbox" to "$i")) }
        assertTrue(repo.cacheSize() <= DjMapRepository.MAX_ENTRIES, "каждый сдвиг карты — новая запись; без потолка память растёт")
        now += kotlin.time.Duration.parse("3m")
        repo.density(listOf("bbox" to "new"))
        assertEquals(1, repo.cacheSize(), "просроченное выкидывается")
    }
}
