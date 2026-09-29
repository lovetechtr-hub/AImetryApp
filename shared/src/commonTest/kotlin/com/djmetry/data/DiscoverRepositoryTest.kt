package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.RankedArtist
import com.djmetry.data.repository.DeckSource
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.data.repository.VoteLimitException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DiscoverRepositoryTest {

    private val trends = """{"category":"growing","count":3,"artists":[
        {"spotifyArtistId":"a","name":"A","aimetryScore":30},
        {"spotifyArtistId":"b","name":"B","aimetryScore":20},
        {"spotifyArtistId":"c","name":"C","aimetryScore":10}]}"""

    private fun backend(
        follows: String = """{"follows":[{"spotifyArtistId":"b","name":"B"}],"count":1}""",
        votes: String = """{"votes":[],"count":0}""",
        extra: Map<String, Pair<HttpStatusCode, String>> = emptyMap(),
    ) = FakeBackend(
        mapOf(
            "GET /api/artists/trends" to (HttpStatusCode.OK to trends),
            "GET /api/artists/top100" to (HttpStatusCode.OK to """{"artists":[{"spotify_artist_id":"t1","name":"Top","score":50,"position":1}]}"""),
            "GET /api/me/follows" to (HttpStatusCode.OK to follows),
            "GET /api/vote/status" to (HttpStatusCode.OK to votes),
            "POST /api/artists/a/follow" to (HttpStatusCode.OK to """{"success":true}"""),
            "DELETE /api/artists/b/follow" to (HttpStatusCode.OK to """{"success":true}"""),
            "POST /api/vote" to (HttpStatusCode.OK to """{"votes":["x","a"],"count":2}"""),
        ) + extra
    )

    private fun repo(b: FakeBackend) = b.client().let { DiscoverRepository(ArtistApi(it), UserApi(it)) }

    @Test
    fun deckUsesCategoryAndHidesFollowedArtists() = runTest {
        val b = backend()
        val deck = repo(b).deck(DeckSource.Rising).getOrThrow()
        assertEquals(listOf("A", "C"), deck.map { it.name })
        assertEquals("growing", b.request("GET", "/api/artists/trends")!!.url.parameters["category"])
    }

    @Test
    fun topDeckUsesTop100() = runTest {
        val b = backend()
        assertEquals("Top", repo(b).deck(DeckSource.Top).getOrThrow().single().name)
        assertNull(b.request("GET", "/api/artists/trends"))
    }

    @Test
    fun deckWorksWithoutLogin() = runTest {
        val b = backend(extra = mapOf(
            "GET /api/me/follows" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized"}"""),
            "GET /api/vote/status" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized"}"""),
        ))
        assertEquals(3, repo(b).deck(DeckSource.Rising).getOrThrow().size)
    }

    @Test
    fun followPostsAndUpdatesList() = runTest {
        val b = backend()
        val r = repo(b)
        r.refreshMine()
        r.follow(RankedArtist(spotifyArtistId = "a", name = "A")).getOrThrow()
        assertNotNull(b.request("POST", "/api/artists/a/follow"))
        assertEquals(listOf("b", "a"), r.follows.value.map { it.spotifyArtistId })
    }

    @Test
    fun followFromArtistCardByIdIsIdempotent() = runTest {
        val b = backend()
        val r = repo(b)
        r.refreshMine()
        r.follow("a", "A", "https://img/a").getOrThrow()
        r.follow("a", "A", "https://img/a").getOrThrow()
        assertEquals(listOf("b", "a"), r.follows.value.map { it.spotifyArtistId }, "без дублей")
        assertEquals("https://img/a", r.follows.value.last().imageUrl)
    }

    @Test
    fun unfollowSendsDelete() = runTest {
        val b = backend()
        val r = repo(b)
        r.refreshMine()
        r.unfollow("b").getOrThrow()
        assertNotNull(b.request("DELETE", "/api/artists/b/follow"))
        assertTrue(r.follows.value.isEmpty())
    }

    @Test
    fun voteSendsWholeSetOfVotes() = runTest {
        val b = backend(votes = """{"votes":["x"],"count":1}""")
        val r = repo(b)
        r.refreshMine()
        assertEquals(listOf("x", "a"), r.vote("a").getOrThrow())
        val body = (b.request("POST", "/api/vote")!!.body as TextContent).text
        assertEquals("""{"votes":["x","a"]}""", body)
    }

    @Test
    fun fourthVoteIsRejectedWithoutRequest() = runTest {
        val b = backend(votes = """{"votes":["x","y","z"],"count":3}""")
        val r = repo(b)
        r.refreshMine()
        assertIs<VoteLimitException>(r.vote("a").exceptionOrNull())
        assertNull(b.request("POST", "/api/vote"))
    }

    @Test
    fun repeatedVoteIsNoOp() = runTest {
        val b = backend(votes = """{"votes":["a"],"count":1}""")
        val r = repo(b)
        r.refreshMine()
        assertEquals(listOf("a"), r.vote("a").getOrThrow())
        assertNull(b.request("POST", "/api/vote"))
    }

    @Test
    fun voteFollowsFirstWhenNotFollowing() = runTest {
        // Бэкенд принимает голос только за артиста из подписок — сначала подписка, потом голос
        val b = backend()
        val r = repo(b)
        r.refreshMine()
        r.vote("a", "A").getOrThrow()
        assertNotNull(b.request("POST", "/api/artists/a/follow"))
        assertTrue(r.follows.value.any { it.spotifyArtistId == "a" })
    }

    @Test
    fun voteForFollowedArtistDoesNotFollowAgain() = runTest {
        val b = backend(extra = mapOf("POST /api/vote" to (HttpStatusCode.OK to """{"votes":["b"]}""")))
        val r = repo(b)
        r.refreshMine()
        r.vote("b", "B").getOrThrow()
        assertNull(b.request("POST", "/api/artists/b/follow"))
    }

    @Test
    fun serverVoteLimitBecomesVoteLimitException() = runTest {
        val b = backend(extra = mapOf("POST /api/vote" to (HttpStatusCode.BadRequest to """{"error":"too_many_votes","message":"Maximum 3 votes allowed"}""")))
        val r = repo(b)
        r.refreshMine()
        assertIs<VoteLimitException>(r.vote("b").exceptionOrNull())
    }

    @Test
    fun removeVoteSendsRemainingVotes() = runTest {
        val b = backend(votes = """{"votes":["x","a"],"count":2}""", extra = mapOf("POST /api/vote" to (HttpStatusCode.OK to """{"votes":["x"]}""")))
        val r = repo(b)
        r.refreshMine()
        assertEquals(listOf("x"), r.removeVote("a").getOrThrow())
        assertEquals("""{"votes":["x"]}""", (b.request("POST", "/api/vote")!!.body as TextContent).text)
    }
}
