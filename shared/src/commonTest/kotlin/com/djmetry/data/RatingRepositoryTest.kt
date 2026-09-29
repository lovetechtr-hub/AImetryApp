package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.data.repository.*
import com.djmetry.ui.rating.changeIsUp
import com.djmetry.ui.rating.changeLabel
import io.ktor.http.HttpStatusCode
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Навигация рейтингов (вариант A): тип → деление шкалы → фильтры. Формы ответов — реальные с прода (2026-09-29). */
class RatingRepositoryTest {

    private val ok = HttpStatusCode.OK
    private val top = """{"count":3,"artists":[
        {"spotify_artist_id":"g","name":"David Guetta","image_url":"https://i/g","score":53.16,"position":1,"genres":["edm"],"djmag_rank":7,"positionChange":0,"followers":28584543},
        {"spotify_artist_id":"c","name":"Calvin Harris","score":51.46,"position":2,"genres":["edm"],"positionChange":-3},
        {"spotify_artist_id":"t","name":"Tiësto","score":48.72,"position":3,"genres":[],"positionChange":2}]}"""
    private val routes = mapOf(
        "GET /api/artists/top100" to (ok to top),
        "GET /api/artists/top200" to (ok to """{"count":1,"artists":[{"spotify_artist_id":"x","name":"X","score":30.1,"position":101,"genres":["ambient"]}]}"""),
        "GET /api/artists/talents" to (ok to """{"count":1,"artists":[{"spotify_artist_id":"gr","name":"Greggio","score":25,"talent_score":44.1,"position":1,"genres":["tech house"],"positionChange":15}]}"""),
        "GET /api/artists/available-limits" to (ok to """{"limits":["100","200","300","talents"],"cached_at":"2026-09-29"}"""),
        "GET /api/artists/available-genres" to (ok to """{"genres":["ambient","downtempo"],"count":2}"""),
        "GET /api/djmag/rankings/all" to (ok to """{"years":[2024,2025,2023],"count":3,"rankings":{
            "2025":[{"rank":1,"name":"David Guetta","spotifyArtistId":"g","previousYearRank":2},{"rank":2,"name":"Martin Garrix","spotifyArtistId":"mg","previousYearRank":1},{"rank":3,"name":"Alok","previousYearRank":NaN}],
            "2024":[{"rank":1,"name":"Martin Garrix","spotifyArtistId":"mg","previousYearRank":1}]}}"""),
        "GET /api/dj/year-ranking" to (ok to """{"year":2025,"finalized":false,"message":"Year ranking not yet finalized","rankings":[]}"""),
    )
    private fun repo(b: FakeBackend) = RatingRepository(ArtistApi(b.client()))

    @Test
    fun defaultIsDjmetryTop100WithBackendPlaces() = runTest {
        val page = repo(FakeBackend(routes)).load(RatingQuery()).getOrThrow()
        assertEquals(listOf(1, 2, 3), page.rows.map { it.position })
        assertNull(page.rows[0].change, "0 — без стрелки")
        assertEquals(RatingChange.Places(-3), page.rows[1].change)
        assertNull(page.rows[2].genre, "пустые жанры — без подписи")
        assertFalse(page.notFinalized)
    }

    @Test
    fun ambientRangeUsesCategoryAndHundred() = runTest {
        val b = FakeBackend(routes)
        val page = repo(b).load(RatingQuery(RatingType.Ambient, RatingRange.Top(200))).getOrThrow()
        assertEquals(101, page.rows.single().position, "место 101 — из API, не по индексу")
        assertEquals("ambient", b.request("GET", "/api/artists/top200")!!.url.parameters["category"])
    }

    @Test
    fun filtersGoToBackendForScoreRatings() = runTest {
        val b = FakeBackend(routes)
        repo(b).load(RatingQuery(genre = "techno", country = "US")).getOrThrow()
        val req = b.request("GET", "/api/artists/top100")!!
        assertEquals("techno", req.url.parameters["genre"]); assertEquals("US", req.url.parameters["country"])
    }

    @Test
    fun talentsShowTalentScore() = runTest {
        val b = FakeBackend(routes)
        val row = repo(b).load(RatingQuery(range = RatingRange.Talents)).getOrThrow().rows.single()
        assertTrue(row.talent)
        assertEquals(44.1, row.score, "Talent score вместо Score")
        assertEquals("200", b.request("GET", "/api/artists/talents")!!.url.parameters["limit"])
    }

    @Test
    fun djMagYearsFromOneRequestFiltersIgnored() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b)
        val y25 = r.load(RatingQuery(RatingType.DJMag, RatingRange.Year(2025), genre = "techno", country = "US")).getOrThrow()
        assertEquals(RatingChange.Places(1), y25.rows[0].change, "Guetta 2 → 1")
        assertEquals(RatingChange.Places(-1), y25.rows[1].change)
        assertNull(y25.rows[2].change, "NaN с прода — без стрелки")
        r.load(RatingQuery(RatingType.DJMag, RatingRange.Year(2024))).getOrThrow()
        assertEquals(listOf(RatingRange.Year(2025), RatingRange.Year(2024), RatingRange.Year(2023)), r.ranges(RatingType.DJMag).getOrThrow(), "годы — новые слева")
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/djmag/rankings/all" }, "DJ Mag — один запрос на все годы")
    }

    @Test
    fun yearResultsNotFinalized() = runTest {
        val b = FakeBackend(routes)
        val page = repo(b).load(RatingQuery(RatingType.Year, RatingRange.Year(2025))).getOrThrow()
        assertTrue(page.notFinalized); assertTrue(page.rows.isEmpty())
        val years = b.requests.filter { it.url.encodedPath == "/api/dj/year-ranking" }.map { it.url.parameters["year"] }
        assertEquals(listOf("2025", "2024"), years, "сначала выбранный год, не подведён — прошлый")
    }

    @Test
    fun yearResultsFallBackToPreviousFinalizedYear() = runTest {
        // Ответ зависит от года — свой движок вместо FakeBackend
        val engine = io.ktor.client.engine.mock.MockEngine { req ->
            val year = req.url.parameters["year"]
            val body = if (year == "2024") """{"year":2024,"finalized":true,"rankings":[{"spotify_artist_id":"g","name":"David Guetta","position":1,"score":53}]}"""
                else """{"year":$year,"finalized":false,"rankings":[]}"""
            respond(body, HttpStatusCode.OK, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }
        val r = RatingRepository(ArtistApi(com.djmetry.api.createApiClient({ null }, { null }, engine)))
        val page = r.load(RatingQuery(RatingType.Year, RatingRange.Year(2025))).getOrThrow()
        assertEquals(2024, page.shownYear, "2025 не подведён — показан 2024")
        assertEquals("David Guetta", page.rows.single().name)
        assertFalse(page.notFinalized)
    }

    @Test
    fun rulerFromBackendLimits() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b)
        assertEquals(listOf(RatingRange.Top(100), RatingRange.Top(200), RatingRange.Top(300), RatingRange.Talents), r.ranges(RatingType.DJMetry).getOrThrow())
        r.ranges(RatingType.Ambient)
        assertEquals("ambient", b.requests.last { it.url.encodedPath == "/api/artists/available-limits" }.url.parameters["category"])
        assertEquals(listOf(RatingRange.Year(2025), RatingRange.Year(2024), RatingRange.Year(2023)), r.ranges(RatingType.Year).getOrThrow())
        assertEquals(listOf("ambient", "downtempo"), r.genres(RatingType.Ambient).getOrThrow())
    }

    @Test
    fun cachedPerQueryRefreshReloads() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b)
        r.load(RatingQuery()); r.load(RatingQuery()); r.top100()
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/artists/top100" }, "панель колоды берёт тот же кэш")
        r.load(RatingQuery(), refresh = true)
        assertEquals(2, b.requests.count { it.url.encodedPath == "/api/artists/top100" })
    }

    @Test
    fun failureReported() = runTest {
        assertTrue(repo(FakeBackend(emptyMap())).load(RatingQuery()).isFailure)
    }

    @Test
    fun podiumOnlyForPlacesOneTwoThree() = runTest {
        val rows = repo(FakeBackend(routes)).load(RatingQuery()).getOrThrow().rows
        assertEquals(listOf(2, 1, 3), podiumSplit(rows).podium.map { it.position })
        assertTrue(podiumSplit(rows.map { it.copy(position = it.position + 100) }).podium.isEmpty(), "101+ — без подиума")
        assertTrue(podiumSplit(rows.take(2)).podium.isEmpty())
    }

    @Test
    fun labels() {
        assertEquals("1–100", rangeLabel(RatingRange.Top(100)))
        assertEquals("901–1000", rangeLabel(RatingRange.Top(1000)))
        assertEquals("Talents", rangeLabel(RatingRange.Talents))
        assertEquals("2025", rangeLabel(RatingRange.Year(2025)))
        assertEquals("2", changeLabel(RatingChange.Places(2)), "стрелка — иконкой, не символом")
        assertEquals("3", changeLabel(RatingChange.Places(-3)))
        assertEquals("+6.45", changeLabel(RatingChange.Growth(6.45)))
        assertNull(changeLabel(null))
        assertFalse(changeIsUp(RatingChange.Places(-1)))
        assertTrue(RatingType.DJMetry.byScore); assertFalse(RatingType.DJMag.byScore)
    }
}
