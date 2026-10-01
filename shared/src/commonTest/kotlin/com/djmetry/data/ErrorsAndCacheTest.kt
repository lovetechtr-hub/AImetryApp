package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AnalyticsApi
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.AudienceApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.api.endpoints.RadarApi
import com.djmetry.api.endpoints.SettingsApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.PickedFile
import com.djmetry.data.analytics.AnalyticsQuery
import com.djmetry.data.analytics.AnalyticsSource
import com.djmetry.data.analytics.AudienceScope
import com.djmetry.data.cache.TtlCache
import com.djmetry.data.repository.AnalyticsRepository
import com.djmetry.data.repository.AudienceRepository
import com.djmetry.data.repository.BookingRepository
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.data.repository.InputException
import com.djmetry.data.repository.ProfileDashboard
import com.djmetry.data.repository.RadarRepository
import com.djmetry.i18n.Strings
import com.djmetry.ui.booking.isStaleBooking
import com.djmetry.ui.screens.actionErrorKey
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.test.*

/** Ошибка — это «Ошибка · Повторить», а не пустой экран; кэш не держит устаревшее и не держит сбои. */
class ErrorsAndCacheTest {
    private val ok = HttpStatusCode.OK
    private val down = HttpStatusCode.ServiceUnavailable to """{"error":"unavailable"}"""

    @Test
    fun failedFollowsAreNotEmptyFollows() = runTest {
        val b = FakeBackend(mapOf("GET /api/me/follows" to down, "GET /api/vote/status" to down))
        val repo = DiscoverRepository(ArtistApi(b.client()), UserApi(b.client()))
        assertFalse(repo.refreshMine(force = true), "экран покажет «Ошибка · Повторить», а не «вы ни на кого не подписаны»")
        val good = FakeBackend(mapOf("GET /api/me/follows" to (ok to """{"follows":[],"count":0}"""), "GET /api/vote/status" to (ok to """{"votes":[],"count":0}""")))
        assertTrue(DiscoverRepository(ArtistApi(good.client()), UserApi(good.client())).refreshMine(force = true))
    }

    @Test
    fun twoQuickVotesKeepBoth() = runTest {
        val b = FakeBackend(mapOf(
            "GET /api/me/follows" to (ok to """{"follows":[{"spotifyArtistId":"a","name":"A"},{"spotifyArtistId":"b","name":"B"}],"count":2}"""),
            "GET /api/vote/status" to (ok to """{"votes":[],"count":0}"""),
            "POST /api/vote" to (ok to """{"votes":["a"],"count":1}"""),
        ))
        val repo = DiscoverRepository(ArtistApi(b.client()), UserApi(b.client()))
        repo.refreshMine(force = true)
        listOf(async { repo.vote("a") }, async { repo.vote("b") }).awaitAll()
        val bodies = b.requests.filter { it.url.encodedPath == "/api/vote" }.map { (it.body as TextContent).text }
        assertEquals("""{"votes":["a","b"]}""", bodies.last(), "второй голос отправлен поверх первого, а не вместо него")
    }

    @Test
    fun concertsFallBackOnlyWhenEndpointIsMissing() = runTest {
        fun radar(b: FakeBackend) = b.client().let { RadarRepository(RadarApi(it), ArtistApi(it), NotificationsApi(it), SettingsApi(it)) }
        assertNull(radar(FakeBackend(emptyMap())).serverConcerts(), "404 — старый бэкенд, обходим подписки")
        val r = radar(FakeBackend(mapOf("GET /api/me/concerts" to down))).serverConcerts()
        assertNotNull(r); assertTrue(r.isFailure, "без сети обход 60 артистов тоже не пройдёт — показываем ошибку")
    }

    @Test
    fun bookingRolesAndFilesFailLoudly() = runTest {
        val offline = BookingRepository(BookingApi(FakeBackend(mapOf(
            "GET /api/booking/me/overview" to down, "GET /api/booking/companies/my" to down, "GET /api/booking/artists/a1/files" to down,
        )).client()))
        assertTrue(offline.roles(MeResponse(isAuthed = true)).isFailure, "агентство без сети не превращается в заказчика")
        assertTrue(offline.files("a1")!!.isFailure, "сбой — не «файлов нет»")
        val old = BookingRepository(BookingApi(FakeBackend(emptyMap()).client()))
        assertTrue(old.roles(MeResponse(isAuthed = true)).isSuccess, "404 — агентств нет, это не ошибка")
        assertNull(old.files("a1"), "404 — старый бэкенд")
    }

    @Test
    fun bookingUploadChecksPdfBeforeSending() = runTest {
        val b = FakeBackend(emptyMap())
        val repo = BookingRepository(BookingApi(b.client()), ArtistEditorApi(b.client()))
        val r = repo.uploadDoc("a1", BookingDoc.Rider, PickedFile("rider.docx", null, "PK".encodeToByteArray()))
        assertEquals("pdf_notpdf", (r.exceptionOrNull() as InputException).code)
        assertTrue(b.requests.isEmpty(), "не отправляем ради отказа сервера")
        assertEquals(Strings.ED_ERR_NOT_PDF, actionErrorKey(r.exceptionOrNull()!!))
    }

    @Test
    fun actionErrorsNameTheReason() {
        assertEquals(Strings.TOAST_FOLLOW_LIMIT, actionErrorKey(ApiException(403, "limit_exceeded", null, limit = 250)))
        assertEquals(Strings.TOAST_FOLLOW_LEGEND, actionErrorKey(ApiException(403, "legend_not_followable", null)))
        assertEquals(Strings.BK_ERR_STALE, actionErrorKey(ApiException(400, "invalid_status_transition", null)))
        assertEquals(Strings.BK_ERR_STALE, actionErrorKey(ApiException(409, null, null)))
        assertTrue(isStaleBooking(ApiException(409, null, null)))
        assertFalse(isStaleBooking(ApiException(500, null, null)))
        assertEquals(Strings.ED_ERR_TOO_BIG, actionErrorKey(ApiException(413, "file_too_large", null)))
    }

    @Test
    fun onlyRealRateLimitsSayWait() {
        assertTrue(ApiException(429, null, null).isRateLimited)
        assertTrue(ApiException(400, "too_many_requests", null).isRateLimited)
        assertTrue(ApiException(0, "too_many_oauth_init_requests", null).isRateLimited)
        assertTrue(ApiException(400, "spotify_rate_limited", null).isRateLimited)
        assertFalse(ApiException(400, "too_many_genres", null).isRateLimited, "ошибка ввода, а не «подождите»")
        assertFalse(ApiException(400, "too_many_artists", null).isRateLimited)
        assertFalse(ApiException(400, "too_many_votes", null).isRateLimited)
    }

    @Test
    fun verifiedArtistWithoutCardIsNotAFan() {
        val me = MeResponse(isAuthed = true, artistVerification = com.djmetry.api.models.ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
        assertTrue(ProfileDashboard(me).artistFailed)
        assertFalse(ProfileDashboard(MeResponse(isAuthed = true)).artistFailed)
    }

    @Test
    fun ttlCacheExpiresCapsAndSkipsFailures() = runTest {
        var now = Instant.fromEpochMilliseconds(0)
        val cache = TtlCache<String, Int>(10.minutes, maxEntries = 2) { now }
        var calls = 0
        assertTrue(cache.getOrLoad("k") { calls++; Result.failure(IllegalStateException()) }.isFailure)
        assertEquals(1, cache.getOrLoad("k") { calls++; Result.success(1) }.getOrThrow(), "сбой не закэширован")
        assertEquals(1, cache.getOrLoad("k") { calls++; Result.success(2) }.getOrThrow())
        assertEquals(2, calls)
        now += 11.minutes
        assertEquals(3, cache.getOrLoad("k") { Result.success(3) }.getOrThrow(), "просрочено — перечитали")
        cache.put("a", 1); cache.put("b", 2); cache.put("c", 3)
        assertEquals(2, cache.size(), "потолок размера")
    }

    @Test
    fun analyticsWithoutBreakdownIsNotCached() = runTest {
        val network = """{"range":{"preset":"7d","from":"a","to":"b"},"totals":{"total_page_views":1},"by_segment":[],"click_breakdown":[],"top_viewers":[]}"""
        val b = FakeBackend(mapOf(
            "GET /api/me/music-page/analytics/bio-network" to (ok to network),
            "GET /api/me/music-page/analytics/breakdown" to down,
        ))
        val repo = AnalyticsRepository(AnalyticsApi(b.client()))
        repo.load(AnalyticsSource.Bio, AnalyticsQuery()).getOrThrow()
        repo.load(AnalyticsSource.Bio, AnalyticsQuery()).getOrThrow()
        assertEquals(2, b.requests.count { it.url.encodedPath.endsWith("/bio-network") }, "страны и устройства подтянутся при следующем открытии")
    }

    @Test
    fun offlineAudienceCatalogIsNotCached() = runTest {
        val b = FakeBackend(mapOf("GET /api/audience/filter-catalog" to down))
        val repo = AudienceRepository(AudienceApi(b.client()))
        val scope = AudienceScope.Artist("a1")
        repo.filterCatalog(scope); repo.filterCatalog(scope)
        assertEquals(2, b.requests.size, "появится сеть — придёт настоящий каталог")
        val old = FakeBackend(emptyMap())
        val oldRepo = AudienceRepository(AudienceApi(old.client()))
        oldRepo.filterCatalog(scope); oldRepo.filterCatalog(scope)
        assertEquals(1, old.requests.size, "404 — эндпоинта нет, не спрашиваем снова")
    }
}
