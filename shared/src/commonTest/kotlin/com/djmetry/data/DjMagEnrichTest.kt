package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.DJMetryJson
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.DJMAG_NEW
import com.djmetry.api.models.DJMagAllResponse
import com.djmetry.data.repository.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** DJ Mag как на сайте: фото/жанр/Score — из `artists/batch`, «NEW» — метка новичка, `/all` — один запрос на всех. */
class DjMagEnrichTest {
    private val all = """{"years":[2024],"count":1,"rankings":{"2024":[
        {"rank":1,"name":"Martin Garrix","spotifyArtistId":"g","imageUrl":null,"previousYearRank":1},
        {"rank":2,"name":"Newbie","spotifyArtistId":"n","previousYearRank":"NEW"},
        {"rank":3,"name":"Nobody","previousYearRank":null}]}}"""
    private val batch = """{"artists":[{"spotifyArtistId":"g","name":"Martin Garrix","imageUrl":"https://i/g.jpg","genres":["edm","big room"],"aimetryScore":48.3},
        {"spotifyArtistId":"n","name":"Newbie","imageUrl":"https://i/n.jpg","genres":[],"aimetryScore":0}],"total":2,"requested":2}"""
    private fun backend() = FakeBackend(mapOf(
        "GET /api/djmag/rankings/all" to (HttpStatusCode.OK to all),
        "POST /api/artists/batch" to (HttpStatusCode.OK to batch),
    ))

    @Test
    fun newIsParsed() {
        val d = DJMetryJson.decodeFromString(DJMagAllResponse.serializer(), all)
        assertEquals(listOf(1, DJMAG_NEW, null), d.rankings.getValue("2024").map { it.previousYearRank })
    }

    @Test
    fun rowsGetPhotoGenreScoreFromBatch() = runTest {
        val b = backend()
        val rows = RatingRepository(ArtistApi(b.client())).load(RatingQuery(RatingType.DJMag, RatingRange.Year(2024))).getOrThrow().rows
        assertEquals(listOf("https://i/g.jpg", "https://i/n.jpg", null), rows.map { it.imageUrl })
        assertEquals(listOf("edm", null, null), rows.map { it.genre })
        assertEquals(listOf(48.3, null, null), rows.map { it.score })
        assertEquals(listOf(null, RatingChange.New, null), rows.map { it.change })
        assertEquals("""{"spotifyArtistIds":["g","n"]}""", (b.request("POST", "/api/artists/batch")!!.body as TextContent).text)
    }

    @Test
    fun djMagAllIsFetchedOnceForParallelCallers() = runTest {
        val b = backend()
        val repo = RatingRepository(ArtistApi(b.client()))
        listOf(
            async { repo.ranges(RatingType.DJMag) },
            async { repo.load(RatingQuery(RatingType.DJMag, RatingRange.Year(2024))) },
            async { repo.ranges(RatingType.Year) },
        ).awaitAll()
        assertEquals(1, b.requests.count { it.url.encodedPath.endsWith("djmag/rankings/all") })
    }
}
