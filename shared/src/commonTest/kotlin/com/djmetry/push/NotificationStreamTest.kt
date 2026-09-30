package com.djmetry.push

import com.djmetry.api.createApiClient
import com.djmetry.api.models.MeResponse
import com.djmetry.data.repository.SessionState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NotificationStreamTest {
    @Test
    fun parserJoinsDataSkipsHeartbeatAndReadsRetry() {
        val p = SseParser()
        val lines = listOf("retry: 3000", ": ping", "id: 1284", "event: notification", "data: {\"a\":1,", "data: \"b\":2}", "")
        val events = lines.mapNotNull { p.feed(it) }
        assertEquals(listOf(SseEvent("1284", "notification", "{\"a\":1,\n\"b\":2}")), events)
        assertEquals(3000L, p.retryMs)
        assertNull(p.feed(""), "пустая строка без данных — не событие")
    }

    @Test
    fun streamDeliversNotificationAndResumesWithLastEventId() = runTest {
        val frame = "retry: 1\n: ping\nid: 77\nevent: notification\ndata: {\"stream_id\":77,\"id\":\"ntf_1\",\"type\":\"release_radar\",\"title\":\"New release\",\"body\":\"Anyma\",\"url\":\"/artist/abc\"}\n\n"
        val headers = mutableListOf<String?>()
        val secondCall = CompletableDeferred<Unit>()
        val engine = MockEngine { req ->
            headers += req.headers["Last-Event-ID"]
            if (headers.size >= 2) secondCall.complete(Unit)
            respond(frame, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val http = createApiClient(tokenProvider = { "t" }, engine = engine)
        val session = MutableStateFlow<SessionState>(SessionState.SignedIn(MeResponse(isAuthed = true, userId = "u1")))
        val got = mutableListOf<StreamNotification>()
        val job = launch { NotificationStream(http).run(session) { got += it } }
        secondCall.await()
        job.cancel()
        assertEquals("New release", got.first().title)
        assertEquals("/artist/abc", got.first().url)
        assertEquals(listOf(null, "77"), headers.take(2), "переподключение догоняет с Last-Event-ID")
    }

    @Test
    fun noStreamWhenSignedOut() = runTest {
        var calls = 0
        val http = createApiClient(tokenProvider = { null }, engine = MockEngine { calls++; respond("", HttpStatusCode.OK) })
        val job = launch { NotificationStream(http).run(MutableStateFlow(SessionState.SignedOut)) { } }
        testScheduler.advanceUntilIdle()
        job.cancel()
        assertEquals(0, calls)
    }
}
