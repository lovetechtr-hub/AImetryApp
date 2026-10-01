package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.ME_AUTHED
import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.auth.CustomSchemeRedirect
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.data.repository.AuthRepository
import com.djmetry.data.repository.SessionState
import com.djmetry.data.repository.SignInState
import com.djmetry.data.repository.SignOutReason
import com.djmetry.domain.model.OAuthProvider
import com.djmetry.i18n.Strings
import com.djmetry.ui.screens.loginError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

/** Вход и выход «круто»: фон приложения, отмена, завершение после перезапуска, мгновенный старт, понятные ошибки. */
class AuthFlowTest {
    private val tokenOk = """{"token":"session-42","tokenType":"Bearer","expiresAt":"2026-10-28T00:00:00Z"}"""

    private fun repo(b: FakeBackend, storage: FakeSessionStorage, browser: suspend (String, String) -> String = { _, _ -> error("not used") }): AuthRepository {
        lateinit var auth: AuthRepository
        val client = b.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, CustomSchemeRedirect(open = browser))
        return auth
    }

    private suspend fun waitFor(cond: () -> Boolean) {
        repeat(400) { if (cond()) return; withContext(Dispatchers.Default) { delay(5) } }
        fail("не дождались")
    }

    @Test
    fun signInRunsInAppScopeAndCanBeCancelled() = runTest {
        val gate = CompletableDeferred<String>()
        val auth = repo(FakeBackend(emptyMap()), FakeSessionStorage()) { _, _ -> gate.await() }
        auth.startSignIn(OAuthProvider.GOOGLE)
        assertEquals(SignInState.InProgress(OAuthProvider.GOOGLE), auth.signInState.value)
        auth.cancelSignIn()
        assertEquals(SignInState.Idle, auth.signInState.value, "«Отменить» — крутилка гаснет сразу")
    }

    @Test
    fun resumeAfterProcessDeathUsesSavedVerifier() = runTest {
        // Первая жизнь процесса: вход начат, ключ сохранён, браузер открыт — процесс убит
        val storage = FakeSessionStorage()
        val first = repo(FakeBackend(emptyMap()), storage) { _, _ -> throw OAuthCancelledException() }
        first.signIn(OAuthProvider.GOOGLE)
        // Отмена стирает ключ — эмулируем «убили во время входа»: ключ на месте
        storage.setPref("oauth_pending", "verifier-xyz|${kotlin.time.Clock.System.now().toEpochMilliseconds()}")

        val b = FakeBackend(mapOf("POST /api/auth/mobile/token" to (HttpStatusCode.OK to tokenOk), "GET /api/me" to (HttpStatusCode.OK to ME_AUTHED)))
        val second = repo(b, storage)
        second.resumeSignIn("djmetry://oauth?code=one-time")
        waitFor { second.session.value is SessionState.SignedIn }
        assertTrue((b.request("POST", "/api/auth/mobile/token")!!.body as TextContent).text.contains("verifier-xyz"))
        assertEquals("session-42", storage.token)
        assertNull(storage.getPref("oauth_pending"), "ключ одноразовый")
    }

    @Test
    fun resumeWithoutSavedVerifierFailsClearly() = runTest {
        val auth = repo(FakeBackend(emptyMap()), FakeSessionStorage())
        auth.resumeSignIn("djmetry://oauth?code=x")
        waitFor { auth.signInState.value is SignInState.Failed }
        val e = (auth.signInState.value as SignInState.Failed).error
        assertEquals(Strings.LOGIN_ERROR_EXPIRED_CODE, loginError(e)?.key)
    }

    @Test
    fun offlineStartShowsLastProfileNotStub() = runTest {
        val storage = FakeSessionStorage("session-42")
        val online = repo(FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.OK to ME_AUTHED))), storage)
        online.restore()
        // Следующий запуск без сети
        val offline = repo(FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.ServiceUnavailable to "{}"))), storage)
        val s = offline.restore() as SessionState.SignedIn
        assertEquals("Slava", s.me.user?.displayName, "последний профиль, а не пустая заглушка")
        assertFalse(offline.isStubProfile)
    }

    @Test
    fun lockedKeychainIsNotSignOut() = runTest {
        val storage = FakeSessionStorage(null).apply { locked = true }
        val auth = repo(FakeBackend(emptyMap()), storage)
        assertEquals(SessionState.Unknown, auth.restore(), "iPhone до разблокировки — ждём, а не выкидываем")
    }

    @Test
    fun expiredSessionExplainsWhy() = runTest {
        val storage = FakeSessionStorage("old")
        val auth = repo(FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized"}"""))), storage)
        auth.restore()
        assertEquals(SignOutReason.Expired, auth.signOutReason.value)
    }

    @Test
    fun logoutCannotBeInterruptedByLeavingScreen() = runTest {
        val gate = CompletableDeferred<Unit>()
        val storage = FakeSessionStorage("session-42")
        val b = FakeBackend(mapOf("POST /api/logout" to (HttpStatusCode.OK to """{"success":true}""")))
        lateinit var auth: AuthRepository
        val client = b.client(token = { auth.currentToken })
        auth = AuthRepository(AuthApi(client), UserApi(client), storage, CustomSchemeRedirect { _, _ -> "" }, beforeSignOut = { gate.await(); null })
        val job = launch { auth.logout() }
        withContext(Dispatchers.Default) { delay(20) }
        job.cancel() // ушли с экрана посреди выхода
        gate.complete(Unit)
        waitFor { storage.token == null }
        assertEquals(SessionState.SignedOut, auth.session.value)
    }

    @Test
    fun failedProfileAfterExchangeRevokesOrphanSession() = runTest {
        val b = FakeBackend(mapOf(
            "POST /api/auth/mobile/token" to (HttpStatusCode.OK to tokenOk),
            "GET /api/me" to (HttpStatusCode.ServiceUnavailable to "{}"),
            "POST /api/logout" to (HttpStatusCode.OK to """{"success":true}"""),
        ))
        val auth = repo(b, FakeSessionStorage()) { _, _ -> "djmetry://oauth?code=c" }
        assertTrue(auth.signIn(OAuthProvider.GOOGLE).isFailure)
        waitFor { b.request("POST", "/api/logout") != null }
        assertEquals("Bearer session-42", b.request("POST", "/api/logout")!!.headers["Authorization"])
    }

    @Test
    fun loginErrorsAreHuman() {
        assertNull(loginError(OAuthCancelledException()))
        assertNull(loginError(ApiException(0, "access_denied", null)), "«Отмена» у Google — не ошибка")
        assertEquals(Strings.LOGIN_ERROR_DELETED, loginError(ApiException(0, "user_deleted", null))?.key)
        assertEquals(Strings.LOGIN_ERROR_EXPIRED_CODE, loginError(ApiException(400, "invalid_or_expired_code", null))?.key)
        assertEquals(Strings.LOGIN_ERROR_FAILED, loginError(IllegalStateException("Activity is not attached"))?.key, "не «нет сети»")
        assertEquals(Strings.LOGIN_ERROR_NETWORK, loginError(kotlinx.io.IOException("offline"))?.key)
    }
}
