package com.djmetry.api

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.DJMagRankingsResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Реальные ответы прода (снято 2026-09-28): схемы top100 и trends отличаются. */
class ProdPayloadsTest {

    private val top100 = """{"count": 2, "artists": [{"id": 1, "spotify_artist_id": "1Cs0zKBU1kc0i8ypK3B9ai", "name": "David Guetta", "image_url": "https://i.scdn.co/image/ab6761610000e5ebf150017ca69c8793503c2d4f", "followers": 28584543, "popularity": 91, "genres_json": "[\"edm\"]", "is_electronic": true, "is_dj": true, "dj_tier": "A", "djmag_rank": 7, "catalog_verified": true, "confidence": "High", "status": "Stable", "country": null, "slug": "david-guetta", "description": "French DJ and producer, one of the most famous artists in the EDM world. Grammy Award winner, performs at the world's largest festivals. Famous tracks: Titanium, When Love Takes Over, Sexy Bitch, Memories, Without You", "score": 53.16, "position": 1, "genres": ["edm"], "topRegions": [], "regionsCount": 0, "votes": 3, "previousRank": 1, "positionChange": 0, "rankChange": null}, {"id": 31, "spotify_artist_id": "7CajNmpbOovFoOoasH2HaY", "name": "Calvin Harris", "image_url": "https://i.scdn.co/image/ab6761610000e5eb8ebba5e60113b48de8c11f6b", "followers": 24064691, "popularity": 89, "genres_json": "[\"edm\"]", "is_electronic": true, "is_dj": true, "dj_tier": "A", "djmag_rank": null, "catalog_verified": true, "confidence": "High", "status": "Stable", "country": null, "slug": "calvin-harris", "description": "Famous DJ and producer, DJ Mag #16, 23.2M+ followers on Spotify", "score": 51.46, "position": 2, "genres": ["edm"], "topRegions": [], "regionsCount": 0, "votes": 0, "previousRank": 2, "positionChange": 0, "rankChange": null}]}"""
    private val trends = """{"category": "growing", "count": 1, "artists": [{"spotifyArtistId": "21A7bhIL1m6CNZn8y57PIZ", "name": "Reinier Zonneveld", "imageUrl": "https://i.scdn.co/image/ab6761610000e5ebbd9daa643e753aab5489f490", "followers": 214487, "popularity": 60, "genres": ["techno", "acid techno", "hard techno", "minimal techno", "tekno"], "primaryCategory": "dj", "aimetryScore": 33, "slug": "reinier-zonneveld", "trend": {"score24h": 6.45, "score3d": 6.45, "score7d": 6.45, "volatility": 0, "growthRate": 6.45, "trend": "up", "category": "growing", "tags": [], "dataQuality": "fresh"}, "lastUpdate": "2026-09-28 00:22:12", "snapshotsCount": 1}]}"""

    @Test
    fun top100SnakeCaseIsParsed() = runTest {
        val backend = FakeBackend(mapOf("GET /api/artists/top100" to (HttpStatusCode.OK to top100)))
        val first = ArtistApi(backend.client()).topN(1).getOrThrow().artists.first()
        assertEquals("1Cs0zKBU1kc0i8ypK3B9ai", first.spotifyArtistId)
        assertEquals("David Guetta", first.name)
        assertEquals(53.16, first.djmetryScore)
        assertEquals(1, first.position)
        assertEquals(7, first.djMagRank)
        assertTrue(first.imageUrl!!.startsWith("https://i.scdn.co/"))
    }

    @Test
    fun trendsCamelCaseIsParsed() = runTest {
        val backend = FakeBackend(mapOf("GET /api/artists/trends" to (HttpStatusCode.OK to trends)))
        val artist = ArtistApi(backend.client()).trends("growing").getOrThrow().artists.single()
        assertEquals("Reinier Zonneveld", artist.name)
        assertEquals(33.0, artist.djmetryScore)
        assertEquals(6.45, artist.trend?.score7d)
        assertNotNull(artist.imageUrl)
    }

    /** 2026-09-28 прод отдал `"previousYearRank":NaN` — весь рейтинг DJ Mag не парсился, в карточке пропал DJ Mag. */
    @Test
    fun djMagRankingsSurviveNaN() {
        val body = """{"year":2025,"count":3,"rankings":[{"rank":2,"name":"Martin Garrix","spotifyArtistId":"60d","previousYearRank":1},{"rank":48,"name":"X","spotifyArtistId":null,"previousYearRank":NaN},{"rank":49,"name":"Y","previousYearRank":"7"}]}"""
        val r = DJMetryJson.decodeFromString(DJMagRankingsResponse.serializer(), body)
        assertEquals(listOf(1, null, 7), r.rankings.map { it.previousYearRank })
        assertEquals(2025, r.year)
    }
}
