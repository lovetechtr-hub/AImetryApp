package com.djmetry.ui

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.RankedArtist
import com.djmetry.data.repository.DeckSource
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.ui.components.SingleFlight
import com.djmetry.ui.screens.DeckState
import com.djmetry.ui.screens.SwipeAction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.*

/** Колода «Открытий» без экрана: двойной свайп, откат при ошибке, кэш следующей подборки. */
class DeckStateTest {
    private val a = RankedArtist(spotifyArtistId = "a", name = "A")
    private val b = RankedArtist(spotifyArtistId = "b", name = "B")

    /** Ответ фейка приходит в другом потоке: ждём по-настоящему, прокручивая и тестовый планировщик. */
    private suspend fun kotlinx.coroutines.test.TestScope.waitUntil(cond: () -> Boolean) {
        repeat(500) { if (cond()) return; advanceUntilIdle(); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(10) } }
        fail("не дождались")
    }

    private fun backend(followStatus: HttpStatusCode = HttpStatusCode.OK) = FakeBackend(mapOf(
        "GET /api/me/follows" to (HttpStatusCode.OK to """{"follows":[],"count":0}"""),
        "GET /api/vote/status" to (HttpStatusCode.OK to """{"votes":[],"count":0}"""),
        "POST /api/artists/a/follow" to (followStatus to """{"success":true,"error":"too_many_follows"}"""),
        "GET /api/artists/trends" to (HttpStatusCode.ServiceUnavailable to "{}"),
    ))

    @Test
    fun doubleSwipeOnSameCardSendsOneFollow() = runTest {
        val be = backend()
        val deck = DeckState(DiscoverRepository(ArtistApi(be.client()), UserApi(be.client())), this)
        deck.cards.addAll(listOf(a, b))
        var done = 0
        deck.act(a, SwipeAction.Follow) { _, _ -> done++ }
        deck.act(a, SwipeAction.Follow) { _, _ -> done++ }
        waitUntil { done > 0 }
        assertEquals(1, be.requests.count { it.url.encodedPath == "/api/artists/a/follow" })
        assertEquals(1, deck.followed)
        assertEquals(listOf(b), deck.cards.toList())
    }

    @Test
    fun failedFollowReturnsCardAndCounters() = runTest {
        val be = backend(HttpStatusCode.BadRequest)
        val deck = DeckState(DiscoverRepository(ArtistApi(be.client()), UserApi(be.client())), this)
        deck.cards.addAll(listOf(a, b))
        var ok: Boolean? = null
        deck.act(a, SwipeAction.Follow) { success, _ -> ok = success }
        waitUntil { ok != null }
        assertEquals(false, ok)
        assertEquals(listOf(a, b), deck.cards.toList(), "как у голоса: карточка вернулась")
        assertEquals(0, deck.followed)
        assertEquals(0, deck.seen)
    }

    @Test
    fun failedPreviewIsNotCachedAsEmptyDeck() = runTest {
        val be = backend()
        val deck = DeckState(DiscoverRepository(ArtistApi(be.client()), UserApi(be.client())), this)
        assertTrue(deck.preview(DeckSource.Rising).isEmpty())
        deck.preview(DeckSource.Rising)
        assertEquals(2, be.requests.count { it.url.encodedPath == "/api/artists/trends" }, "ошибка не кэшируется — второй раз снова в сеть")
    }

    @Test
    fun singleFlightIgnoresSecondTapUntilDone() = runTest {
        val flight = SingleFlight()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        flight.run(this) { runs++; gate.await() }
        flight.run(this) { runs++ }
        assertTrue(flight.busy)
        advanceUntilIdle()
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(flight.busy)
        flight.run(this) { runs++ }
        advanceUntilIdle()
        assertEquals(2, runs)
    }
}
