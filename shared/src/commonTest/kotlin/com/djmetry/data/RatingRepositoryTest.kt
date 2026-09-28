package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.data.repository.RatingChange
import com.djmetry.data.repository.RatingRepository
import com.djmetry.data.repository.RatingSource
import com.djmetry.data.repository.podiumSplit
import com.djmetry.ui.rating.changeIsUp
import com.djmetry.ui.rating.changeLabel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Таблица рейтинга: три разных ответа прода → одна строка. Формы — реальные (2026-09-29), урезанные. */
class RatingRepositoryTest {

    private val routes = mapOf(
        "GET /api/artists/top100" to (HttpStatusCode.OK to """{"count":3,"artists":[
            {"spotify_artist_id":"g","name":"David Guetta","image_url":"https://i/g","score":53.16,"position":1,"genres":["edm"],"djmag_rank":7,"positionChange":0,"followers":28584543},
            {"spotify_artist_id":"o","name":"Ofenbach","score":33.45,"position":95,"genres":[],"positionChange":-3,"rankChange":"down"},
            {"spotify_artist_id":"k","name":"Kaskade","score":33.29,"position":97,"genres":["edm"],"positionChange":2,"djmag_rank":84}]}"""),
        "GET /api/artists/trends" to (HttpStatusCode.OK to """{"category":"growing","count":2,"artists":[
            {"spotifyArtistId":"r","name":"Reinier Zonneveld","aimetryScore":33,"genres":["techno"],"trend":{"score24h":1.2,"score7d":6.45}},
            {"spotifyArtistId":"m","name":"Mochakk","aimetryScore":33,"genres":["tech house"],"trend":{"score24h":6.18}}]}"""),
        "GET /api/djmag/rankings" to (HttpStatusCode.OK to """{"year":2025,"count":3,"rankings":[
            {"rank":1,"name":"David Guetta","spotifyArtistId":"g","previousYearRank":2},
            {"rank":2,"name":"Martin Garrix","spotifyArtistId":"mg","previousYearRank":1},
            {"rank":48,"name":"X","previousYearRank":NaN}]}"""),
    )

    private fun repo(b: FakeBackend) = RatingRepository(ArtistApi(b.client()))

    @Test
    fun top100RowsKeepPlacesAndChanges() = runTest {
        val rows = repo(FakeBackend(routes)).load(RatingSource.Top100).getOrThrow()
        assertEquals(listOf(1, 95, 97), rows.map { it.position })
        assertNull(rows[0].change, "0 — без стрелки")
        assertEquals(RatingChange.Places(-3), rows[1].change)
        assertEquals(RatingChange.Places(2), rows[2].change)
        assertEquals("edm", rows[0].genre)
        assertNull(rows[1].genre, "пустой список жанров — без подписи")
        assertEquals(7, rows[0].djMagRank)
        assertEquals(53.16, rows[0].score)
    }

    @Test
    fun trendsNumberedInBackendOrderWithGrowth() = runTest {
        val b = FakeBackend(routes)
        val rows = repo(b).load(RatingSource.Rising).getOrThrow()
        assertEquals("growing", b.request("GET", "/api/artists/trends")!!.url.parameters["category"])
        assertEquals(listOf(1, 2), rows.map { it.position })
        assertEquals(RatingChange.Growth(6.45), rows[0].change, "прирост за 7 дней")
        assertEquals(RatingChange.Growth(6.18), rows[1].change, "нет 7 дней — берём 24 ч")
        repo(b).load(RatingSource.Breakthrough)
        assertEquals("breakthrough", b.request("GET", "/api/artists/trends")!!.url.parameters["category"])
    }

    @Test
    fun djMagChangeIsLastYearMinusNow() = runTest {
        val rows = repo(FakeBackend(routes)).load(RatingSource.DJMag).getOrThrow()
        assertEquals(RatingChange.Places(1), rows[0].change, "Guetta: 2 → 1, поднялся")
        assertEquals(RatingChange.Places(-1), rows[1].change, "Garrix: 1 → 2")
        assertNull(rows[2].change, "NaN с прода — без стрелки")
        assertNull(rows[0].score)
    }

    @Test
    fun cachedPerSourceRefreshReloads() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b)
        r.load(RatingSource.Top100); r.load(RatingSource.Top100)
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/artists/top100" })
        r.load(RatingSource.Top100, refresh = true)
        assertEquals(2, b.requests.count { it.url.encodedPath == "/api/artists/top100" })
    }

    @Test
    fun failureIsReportedAndNotCached() = runTest {
        val r = repo(FakeBackend(emptyMap()))
        assertTrue(r.load(RatingSource.Top100).isFailure)
    }

    @Test
    fun podiumIsSecondFirstThird() = runTest {
        val rows = repo(FakeBackend(routes)).load(RatingSource.DJMag).getOrThrow()
        val split = podiumSplit(rows)
        assertEquals(listOf(2, 1, 48), split.podium.map { it.position })
        assertTrue(split.rest.isEmpty())
        assertTrue(podiumSplit(rows.take(2)).podium.isEmpty(), "меньше трёх — без подиума")
    }

    @Test
    fun changeLabels() {
        assertEquals("▲2", changeLabel(RatingChange.Places(2)))
        assertEquals("▼3", changeLabel(RatingChange.Places(-3)))
        assertEquals("+6.45", changeLabel(RatingChange.Growth(6.45)))
        assertNull(changeLabel(null))
        assertFalse(changeIsUp(RatingChange.Places(-1)))
        assertTrue(changeIsUp(RatingChange.Growth(0.5)))
    }
}
