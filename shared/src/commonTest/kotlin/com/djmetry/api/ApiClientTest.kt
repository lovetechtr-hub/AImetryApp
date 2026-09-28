package com.djmetry.api

import com.djmetry.FakeBackend
import com.djmetry.ME_AUTHED
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.domain.model.OAuthProvider
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApiClientTest {

    @Test
    fun sendsBearerTokenAndLanguageToProdApi() = runTest {
        val backend = FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.OK to ME_AUTHED)))
        val me = UserApi(backend.client(token = { "tok-123" }, language = { "ru" })).getMe().getOrThrow()

        val request = assertNotNull(backend.request("GET", "/api/me"))
        assertEquals("djmetry.com", request.url.host)
        assertEquals(URLProtocol.HTTPS, request.url.protocol)
        assertEquals("Bearer tok-123", request.headers[HttpHeaders.Authorization])
        assertEquals("ru", request.headers[HttpHeaders.AcceptLanguage])
        assertNull(request.headers[HttpHeaders.Origin], "Origin включает CSRF-проверку на бэке — не слать")
        assertTrue(me.isAuthed)
        assertEquals("Slava", me.user?.displayName)
    }

    @Test
    fun noAuthorizationHeaderWhenSignedOut() = runTest {
        val backend = FakeBackend(mapOf("GET /api/me" to (HttpStatusCode.OK to """{"isAuthed":false}""")))
        UserApi(backend.client()).getMe()
        assertNull(backend.request("GET", "/api/me")?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun backendErrorBecomesApiExceptionWithCode() = runTest {
        val backend = FakeBackend(
            mapOf("POST /api/me/claim-artist" to (HttpStatusCode.BadRequest to """{"error":"artist_limit_reached","currentCount":10,"maxCount":10}"""))
        )
        val error = UserApi(backend.client()).claimArtist("1Cs0zKBU1kc0i8ypK3B9ai").exceptionOrNull()
        assertIs<ApiException>(error)
        assertEquals(400, error.status)
        assertEquals("artist_limit_reached", error.code)
        assertEquals(10, error.maxCount)
    }

    @Test
    fun unauthorizedIsDetected() = runTest {
        val backend = FakeBackend(mapOf("GET /api/me/artist" to (HttpStatusCode.Unauthorized to """{"error":"unauthorized","code":"unauthorized"}""")))
        val error = UserApi(backend.client()).getArtistData().exceptionOrNull()
        assertIs<ApiException>(error)
        assertTrue(error.isUnauthorized)
    }

    @Test
    fun aimetryScoreFieldMapsToDjmetryScore() = runTest {
        val backend = FakeBackend(
            mapOf("GET /api/artists/top100" to (HttpStatusCode.OK to """{"count":1,"artists":[{"spotifyArtistId":"a","name":"David Guetta","aimetryScore":53.16,"rankChange":"up"}]}"""))
        )
        val top = ArtistApi(backend.client()).topN(1, category = "dj").getOrThrow()
        assertEquals(53.16, top.artists.single().djmetryScore)
        assertEquals("dj", backend.request("GET", "/api/artists/top100")?.url?.parameters?.get("category"))
    }

    @Test
    fun topNIsClampedToSupportedSlices() = runTest {
        val backend = FakeBackend(mapOf("GET /api/artists/top1000" to (HttpStatusCode.OK to """{"artists":[]}""")))
        ArtistApi(backend.client()).topN(42)
        assertNotNull(backend.request("GET", "/api/artists/top1000"))
    }

    @Test
    fun searchLimitIsClamped() = runTest {
        val backend = FakeBackend(mapOf("GET /api/artists/search" to (HttpStatusCode.OK to """{"artists":[]}""")))
        ArtistApi(backend.client()).search("fisher", limit = 500)
        assertEquals("50", backend.request("GET", "/api/artists/search")?.url?.parameters?.get("limit"))
    }

    @Test
    fun deviceTokenUnregisterSendsBodyOnDelete() = runTest {
        val backend = FakeBackend(mapOf("DELETE /api/me/unregister-device-token" to (HttpStatusCode.OK to """{"success":true}""")))
        UserApi(backend.client()).unregisterDeviceToken("device-1", "artist-1")
        val body = backend.request("DELETE", "/api/me/unregister-device-token")!!.body
        val text = (body as io.ktor.http.content.TextContent).text
        assertTrue(text.contains("\"deviceToken\":\"device-1\""))
        assertTrue(text.contains("\"spotifyArtistId\":\"artist-1\""))
    }

    @Test
    fun mobileStartUrlHasPkceAndDeepLink() {
        val url = Url(AuthApi(FakeBackend(emptyMap()).client()).mobileStartUrl(OAuthProvider.GOOGLE, "challenge-xyz"))
        assertEquals("/api/auth/google/start", url.encodedPath)
        assertEquals("1", url.parameters["mobile"])
        assertEquals("challenge-xyz", url.parameters["code_challenge"])
        assertEquals("djmetry://oauth", url.parameters["app_redirect"])
        assertNull(url.parameters["handoff"], "телефоны остаются на 302 — Custom Tabs / ASWebAuthenticationSession ловят его сами")
    }

    @Test
    fun desktopAsksForHandoffPage() {
        val url = Url(AuthApi(FakeBackend(emptyMap()).client()).mobileStartUrl(OAuthProvider.FACEBOOK, "c", handoffPage = true))
        assertEquals("page", url.parameters["handoff"])
    }

    @Test
    fun phoneStrategyDoesNotAskForHandoffPage() {
        assertFalse(com.djmetry.auth.CustomSchemeRedirect { _, _ -> "" }.handoffPage)
    }
}
