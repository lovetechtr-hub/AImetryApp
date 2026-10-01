package com.djmetry.data

import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.ME_AUTHED
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.data.repository.AnalyticsRepository
import com.djmetry.data.repository.ArtistEditorRepository
import com.djmetry.data.repository.AudienceRepository
import com.djmetry.data.repository.AuthRepository
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.data.repository.NotificationsRepository
import com.djmetry.data.repository.PendingSignOut
import com.djmetry.data.repository.RadarRepository
import com.djmetry.data.repository.SessionState
import com.djmetry.data.repository.SettingsRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Смена пользователя на устройстве: ни одного значения прошлого аккаунта; 401 и выход без сети. */
class SessionScopeTest {
    private val ok = HttpStatusCode.OK
    private val notifications = """{"unread_total":3,"items":[{"id":"n1","type":"booking","read":false,"title":"A"}],"next_cursor":"c2"}"""

    private fun backend(extra: Map<String, Pair<HttpStatusCode, String>> = emptyMap()) = FakeBackend(
        mapOf(
            "GET /api/me" to (ok to ME_AUTHED),
            "POST /api/logout" to (ok to """{"success":true}"""),
            "GET /api/me/follows" to (ok to """{"follows":[{"spotifyArtistId":"a1","name":"A"}],"count":1}"""),
            "GET /api/vote/status" to (ok to """{"votes":["a1"],"count":1}"""),
            "GET /api/me/notifications" to (ok to notifications),
        ) + extra
    )

    @Test
    fun logoutWipesEveryUserScopedRepository() = runTest {
        val b = backend()
        val c = AppContainer(FakeSessionStorage("user-a"), b.engine)
        c.discover.refreshMine(force = true)
        c.notifications.load()
        assertTrue(c.discover.follows.value.isNotEmpty() && c.notifications.items.value.isNotEmpty())

        c.auth.logout()

        assertTrue(c.discover.follows.value.isEmpty(), "подписки A")
        assertTrue(c.discover.votes.value.isEmpty(), "голоса A")
        assertTrue(c.notifications.items.value.isEmpty(), "уведомления A")
        assertEquals(0, c.notifications.unread.value)
        assertNull(c.notifications.hasMore.value)
        assertNull(c.settings.state.value)
        assertNull(c.artistEditor.state.value)
    }

    @Test
    fun everyRepositoryWithPersonalDataIsCleared() {
        // Новый репозиторий с личными данными — добавить в AppContainer.userScoped()
        val c = AppContainer(FakeSessionStorage(), backend().engine)
        val kinds = c.userScoped().map { it::class }.toSet()
        listOf(
            DiscoverRepository::class, RadarRepository::class, SettingsRepository::class, ArtistEditorRepository::class,
            NotificationsRepository::class, AnalyticsRepository::class, AudienceRepository::class,
        ).forEach { assertTrue(it in kinds, "$it не сбрасывается при выходе") }
    }

    @Test
    fun unauthorizedWithDeadSessionSignsOut() = runTest {
        val b = backend(mapOf("GET /api/me" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized"}""")))
        val storage = FakeSessionStorage("expired")
        var cleared = 0
        lateinit var auth: AuthRepository
        val client = b.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, redirect = { error("not used") }, clearUserData = { cleared++ })

        auth.verifySession("expired")

        assertEquals(SessionState.SignedOut, auth.session.value)
        assertNull(storage.token)
        assertEquals(1, cleared)
    }

    @Test
    fun unauthorizedWithLiveSessionKeepsUser() = runTest {
        // Аудитория отвечает 401 на мобильный токен — это не выход
        val b = backend()
        val storage = FakeSessionStorage("live")
        lateinit var auth: AuthRepository
        val client = b.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, redirect = { error("not used") })

        auth.verifySession("live")
        auth.verifySession("someone-else")

        assertEquals("live", storage.token)
        assertNotEquals(SessionState.SignedOut, auth.session.value)
        assertEquals(1, b.requests.count { it.url.encodedPath == "/api/me" }, "чужой токен не проверяем")
    }

    @Test
    fun offlineLogoutIsFinishedOnNextStart() = runTest {
        val storage = FakeSessionStorage("old")
        val offline = backend(mapOf("POST /api/logout" to (HttpStatusCode.ServiceUnavailable to "{}")))
        lateinit var auth: AuthRepository
        val client = offline.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, redirect = { error("not used") }, beforeSignOut = { "fcm-1" })

        auth.logout()
        assertNull(storage.token, "локально вышли сразу")
        assertEquals(PendingSignOut("old", "fcm-1"), PendingSignOut.decode(storage.pending))

        // Следующий запуск, сеть есть: снимаем пуши и отзываем старую сессию её же токеном
        val online = backend()
        var retried: Pair<String, String>? = null
        val client2 = online.client(token = { null })
        val next = AuthRepository(AuthApi(client2), UserApi(client2), storage, redirect = { error("not used") },
            retryUnregister = { bearer, token -> retried = bearer to token; Result.success(Unit) })
        next.retryPendingSignOut()

        assertEquals("old" to "fcm-1", retried)
        assertEquals("Bearer old", online.request("POST", "/api/logout")!!.headers[HttpHeaders.Authorization])
        assertNull(storage.pending)
    }

    @Test
    fun failedSignInDoesNotKeepToken() = runTest {
        val b = backend(mapOf(
            "POST /api/auth/mobile/token" to (ok to """{"token":"t-1","tokenType":"Bearer","expiresAt":"2026-10-28T00:00:00Z"}"""),
            "GET /api/me" to (HttpStatusCode.ServiceUnavailable to "{}"),
        ))
        val storage = FakeSessionStorage()
        lateinit var auth: AuthRepository
        val client = b.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, com.djmetry.auth.CustomSchemeRedirect(open = { _, _ -> "djmetry://oauth?code=c" }))

        assertTrue(auth.signIn(com.djmetry.domain.model.OAuthProvider.GOOGLE).isFailure)
        assertNull(storage.token, "без /me при следующем запуске молча не входим")
        assertNull(auth.currentToken)
    }

    @Test
    fun pendingSignOutRoundTrip() {
        assertEquals(PendingSignOut("t", null), PendingSignOut.decode(PendingSignOut("t", null).encode()))
        assertEquals(PendingSignOut("t", "p"), PendingSignOut.decode(PendingSignOut("t", "p").encode()))
        assertNull(PendingSignOut.decode(null))
        assertNull(PendingSignOut.decode(""))
    }
}
