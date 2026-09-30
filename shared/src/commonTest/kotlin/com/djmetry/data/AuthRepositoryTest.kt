package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.ME_AUTHED
import com.djmetry.ME_GUEST
import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.auth.CustomSchemeRedirect
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.auth.Pkce
import com.djmetry.data.repository.AuthRepository
import com.djmetry.data.repository.SessionState
import com.djmetry.domain.model.OAuthProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthRepositoryTest {

    private val tokenOk = """{"token":"session-42","tokenType":"Bearer","expiresAt":"2026-10-28T00:00:00Z"}"""

    private fun repo(
        backend: FakeBackend,
        storage: FakeSessionStorage,
        browser: suspend (String, String) -> String,
    ): AuthRepository {
        lateinit var repository: AuthRepository
        val client = backend.client(token = { repository.currentToken })
        repository = AuthRepository(AuthApi(client), UserApi(client), storage, CustomSchemeRedirect(open = browser))
        return repository
    }

    @Test
    fun signInExchangesCodeWithPkceAndStoresToken() = runTest {
        val backend = FakeBackend(
            mapOf(
                "POST /api/auth/mobile/token" to (HttpStatusCode.OK to tokenOk),
                "GET /api/me" to (HttpStatusCode.OK to ME_AUTHED),
            )
        )
        val storage = FakeSessionStorage()
        var openedUrl = ""
        var scheme = ""
        val auth = repo(backend, storage) { url, callbackScheme ->
            openedUrl = url; scheme = callbackScheme
            "djmetry://oauth?code=one-time-code"
        }

        val me = auth.signIn(OAuthProvider.APPLE).getOrThrow()

        assertEquals("Slava", me.user?.displayName)
        assertEquals("djmetry", scheme)
        val start = Url(openedUrl)
        assertEquals("/api/auth/apple/start", start.encodedPath)
        val challenge = assertNotNull(start.parameters["code_challenge"])

        val exchange = (backend.request("POST", "/api/auth/mobile/token")!!.body as TextContent).text
        assertTrue(exchange.contains("\"code\":\"one-time-code\""))
        val verifier = Regex("\"code_verifier\":\"([^\"]+)\"").find(exchange)!!.groupValues[1]
        assertEquals(challenge, Pkce.challengeFor(verifier), "verifier должен соответствовать challenge из start-URL")

        assertEquals("session-42", storage.token)
        assertEquals("Bearer session-42", backend.request("GET", "/api/me")!!.headers[HttpHeaders.Authorization])
        assertIs<SessionState.SignedIn>(auth.session.value)
    }

    @Test
    fun backendErrorInRedirectFailsWithCode() = runTest {
        val backend = FakeBackend(emptyMap())
        val storage = FakeSessionStorage()
        val auth = repo(backend, storage) { _, _ -> "djmetry://oauth?error=user_blocked" }

        val error = auth.signIn(OAuthProvider.GOOGLE).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals("user_blocked", error.code)
        assertNull(storage.token)
        assertNull(backend.request("POST", "/api/auth/mobile/token"), "при ошибке код не обмениваем")
    }

    @Test
    fun cancelledLoginIsReportedAsCancellation() = runTest {
        val auth = repo(FakeBackend(emptyMap()), FakeSessionStorage()) { _, _ -> throw OAuthCancelledException() }
        assertIs<OAuthCancelledException>(auth.signIn(OAuthProvider.FACEBOOK).exceptionOrNull())
    }

    /** Бэкенд возвращает лимит в deep-link: `djmetry://oauth?error=too_many_requests&retry_after=840`. */
    @Test
    fun rateLimitFromDeepLinkKeepsRetryAfter() = runTest {
        val storage = FakeSessionStorage()
        val auth = repo(FakeBackend(emptyMap()), storage) { _, _ -> "djmetry://oauth?error=too_many_requests&retry_after=840" }

        val error = auth.signIn(OAuthProvider.GOOGLE).exceptionOrNull()

        assertIs<ApiException>(error)
        assertTrue(error.isRateLimited)
        assertEquals(840, error.retryAfterSeconds)
        assertNull(storage.token)
    }

    /** Лимит на обмене кода: 429 + заголовок Retry-After (express-rate-limit). */
    @Test
    fun rateLimitOnTokenExchangeReadsRetryAfterHeader() = runTest {
        val engine = MockEngine {
            respond("""{"error":"too_many_requests"}""", HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("600")))
        }
        val client = com.djmetry.api.createApiClient({ null }, { null }, engine)
        val auth = AuthRepository(AuthApi(client), UserApi(client), FakeSessionStorage(), CustomSchemeRedirect { _, _ -> "djmetry://oauth?code=c1" })

        val error = auth.signIn(OAuthProvider.GOOGLE).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(429, error.status)
        assertTrue(error.isRateLimited)
        assertEquals(600, error.retryAfterSeconds)
    }

    @Test
    fun expiredCodeDoesNotStoreToken() = runTest {
        val backend = FakeBackend(
            mapOf("POST /api/auth/mobile/token" to (HttpStatusCode.BadRequest to """{"error":"invalid_or_expired_code"}"""))
        )
        val storage = FakeSessionStorage()
        val auth = repo(backend, storage) { _, _ -> "djmetry://oauth?code=old" }

        val error = auth.signIn(OAuthProvider.APPLE).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals("invalid_or_expired_code", error.code)
        assertNull(storage.token)
    }

    @Test
    fun restoreWithoutTokenIsSignedOut() = runTest {
        val backend = FakeBackend(emptyMap())
        val auth = repo(backend, FakeSessionStorage()) { _, _ -> error("not used") }
        assertEquals(SessionState.SignedOut, auth.restore())
        assertTrue(backend.requests.isEmpty(), "без токена сеть не нужна")
    }

    @Test
    fun restoreWithValidTokenIsSignedIn() = runTest {
        val backend = FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.OK to ME_AUTHED)))
        val auth = repo(backend, FakeSessionStorage("session-42")) { _, _ -> error("not used") }
        assertIs<SessionState.SignedIn>(auth.restore())
        assertEquals("Bearer session-42", backend.request("GET", "/api/me")!!.headers[HttpHeaders.Authorization])
    }

    @Test
    fun restoreWithExpiredSessionClearsToken() = runTest {
        // /api/me без живой сессии отвечает 200 { isAuthed:false }, а не 401
        val storage = FakeSessionStorage("expired")
        val auth = repo(FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.OK to ME_GUEST))), storage) { _, _ -> error("not used") }
        assertEquals(SessionState.SignedOut, auth.restore())
        assertNull(storage.token)
        assertNull(auth.currentToken)
    }

    @Test
    fun restoreOfflineKeepsUserSignedIn() = runTest {
        val storage = FakeSessionStorage("session-42")
        val offline = MockEngine { throw IllegalStateException("no network") }
        val client = com.djmetry.api.createApiClient(tokenProvider = { storage.token }, engine = offline)
        val auth = AuthRepository(AuthApi(client), UserApi(client), storage, redirect = { _ -> error("not used") })

        assertIs<SessionState.SignedIn>(auth.restore())
        assertEquals("session-42", storage.token)
    }

    @Test
    fun logoutClearsToken() = runTest {
        val storage = FakeSessionStorage("session-42")
        val backend = FakeBackend(mapOf("POST /api/logout" to (HttpStatusCode.OK to """{"success":true}""")))
        val auth = repo(backend, storage) { _, _ -> error("not used") }

        auth.logout()

        assertNotNull(backend.request("POST", "/api/logout"))
        assertNull(storage.token)
        assertEquals(SessionState.SignedOut, auth.session.value)
    }

    @Test
    fun logoutWorksOffline() = runTest {
        val storage = FakeSessionStorage("session-42")
        val client = com.djmetry.api.createApiClient(tokenProvider = { storage.token }, engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        val auth = AuthRepository(AuthApi(client), UserApi(client), storage, redirect = { _ -> error("not used") })
        auth.logout()
        assertNull(storage.token)
    }

    @Test
    fun redirectStrategyChoosesAppRedirect() = runTest {
        // Десктоп с loopback: app_redirect = http://127.0.0.1:<порт>/oauth
        val backend = FakeBackend(
            mapOf(
                "POST /api/auth/mobile/token" to (HttpStatusCode.OK to tokenOk),
                "GET /api/me" to (HttpStatusCode.OK to ME_AUTHED),
            )
        )
        val client = backend.client()
        var started = ""
        val loopback = com.djmetry.auth.OAuthRedirect { startUrlFor ->
            started = startUrlFor("http://127.0.0.1:53682/oauth")
            "http://127.0.0.1:53682/oauth?code=desk"
        }
        val auth = AuthRepository(AuthApi(client), UserApi(client), FakeSessionStorage(), loopback)

        auth.signIn(OAuthProvider.GOOGLE).getOrThrow()

        assertEquals("http://127.0.0.1:53682/oauth", Url(started).parameters["app_redirect"])
        assertTrue((backend.request("POST", "/api/auth/mobile/token")!!.body as TextContent).text.contains("\"code\":\"desk\""))
    }
}
