package com.djmetry.push

import com.djmetry.FakeBackend
import com.djmetry.api.models.MeResponse
import com.djmetry.data.repository.SessionState
import com.djmetry.ui.profile.pushArtistId
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PushRegistrarTest {
    private fun backend() = FakeBackend(mapOf(
        "POST /api/push/devices" to (HttpStatusCode.NoContent to ""),
        "DELETE /api/push/devices" to (HttpStatusCode.NoContent to ""),
    ))
    private fun signedIn(id: String) = SessionState.SignedIn(MeResponse(isAuthed = true, userId = id))
    private val token = PushToken("fcm-1", "android")

    @Test
    fun registersFcmTokenAfterLoginOnce() = runTest {
        val b = backend(); val r = PushRegistrar(PushApi(b.client()))
        r.sync(token, SessionState.SignedOut)
        assertNull(b.request("POST", "/api/push/devices"), "без входа — не шлём")
        r.sync(token, signedIn("u1"))
        r.sync(token, signedIn("u1"))
        assertEquals(1, b.requests.count { it.method.value == "POST" }, "повтор того же — без запроса")
        assertEquals("""{"transport":"fcm","token":"fcm-1","platform":"android","app":"mobile"}""", (b.request("POST", "/api/push/devices")!!.body as TextContent).text)
    }

    @Test
    fun newTokenOrOtherUserRegistersAgain() = runTest {
        val b = backend(); val r = PushRegistrar(PushApi(b.client()))
        r.sync(token, signedIn("u1"))
        r.sync(PushToken("fcm-2", "android"), signedIn("u1"))
        r.sync(PushToken("fcm-2", "android"), signedIn("u2"))
        assertEquals(3, b.requests.count { it.method.value == "POST" })
    }

    @Test
    fun unregisterRemovesTokenBeforeLogout() = runTest {
        val b = backend(); val r = PushRegistrar(PushApi(b.client()))
        r.sync(PushToken("fcm-9", "ios"), signedIn("u1"))
        r.unregister()
        assertEquals("""{"token":"fcm-9"}""", (b.request("DELETE", "/api/push/devices")!!.body as TextContent).text)
        // После выхода и нового входа — регистрируем заново
        r.sync(PushToken("fcm-9", "ios"), signedIn("u1"))
        assertEquals(2, b.requests.count { it.method.value == "POST" })
    }

    @Test
    fun failedUnregisterReturnsTokenForRetry() = runTest {
        val b = FakeBackend(mapOf("POST /api/push/devices" to (HttpStatusCode.NoContent to ""),
            "DELETE /api/push/devices" to (HttpStatusCode.ServiceUnavailable to "{}")))
        val r = PushRegistrar(PushApi(b.client()))
        r.sync(PushToken("fcm-7", "android"), signedIn("u1"))
        assertEquals("fcm-7", r.unregister(), "не сняли — токен уходит в отложенный выход")
    }

    @Test
    fun unregisterWithOldSessionUsesItsBearer() = runTest {
        val b = backend()
        PushRegistrar(PushApi(b.client(token = { "new-user" }))).unregisterWith("old-session", "fcm-7")
        assertEquals("Bearer old-session", b.request("DELETE", "/api/push/devices")!!.headers[io.ktor.http.HttpHeaders.Authorization])
    }

    @Test
    fun unregisterWithoutTokenDoesNothing() = runTest {
        val b = backend(); PushRegistrar(PushApi(b.client())).unregister()
        assertTrue(b.requests.isEmpty())
    }

    @Test
    fun pushLinkToArtistOrReleaseRadar() {
        val base = "https://djmetry.com"
        assertEquals("abc", pushArtistId("/artist/abc", base))
        assertEquals("4tZ", pushArtistId("/dashboard/music/release-radar?artist=4tZ&album=1x", base))
        assertNull(pushArtistId("/dashboard/music/release-radar", base))
        assertNull(pushArtistId("/dashboard#booking", base))
    }

    @Test
    fun tokenBridgeIgnoresBlank() {
        PushTokens.onNewToken(" ", "ios")
        PushTokens.onNotificationOpened(null)
        assertNull(PushTokens.opened.value)
    }
}
