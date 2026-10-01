package com.djmetry.desktop

import com.djmetry.auth.DesktopDeepLinks
import com.djmetry.auth.DesktopRedirectKind
import com.djmetry.auth.DesktopSchemeRedirect
import com.djmetry.auth.LoopbackRedirect
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.auth.chooseDesktopRedirect
import io.ktor.http.Url
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.*

class DesktopOAuthTest {

    // ───── выбор стратегии ─────

    @Test
    fun packagedAppUsesSchemeAndDevRunUsesLoopback() {
        assertEquals(DesktopRedirectKind.Scheme, chooseDesktopRedirect(override = null, isPackaged = true))
        assertEquals(DesktopRedirectKind.Loopback, chooseDesktopRedirect(override = null, isPackaged = false))
    }

    @Test
    fun envOverrideWins() {
        assertEquals(DesktopRedirectKind.Loopback, chooseDesktopRedirect(" LOOPBACK ", isPackaged = true))
        assertEquals(DesktopRedirectKind.Scheme, chooseDesktopRedirect("scheme", isPackaged = false))
        assertEquals(DesktopRedirectKind.Loopback, chooseDesktopRedirect("unknown", isPackaged = false))
    }

    // ───── loopback (RFC 8252) ─────

    /** «Браузер»: берёт app_redirect из start-URL и делает GET туда, как это сделал бы бэкенд редиректом. */
    private fun fakeBrowser(query: String, onHtml: (Int, String) -> Unit = { _, _ -> }): (String) -> Unit = { startUrl ->
        val redirect = Url(startUrl).parameters["app_redirect"]!!
        thread {
            val conn = URI("$redirect?$query").toURL().openConnection() as HttpURLConnection
            onHtml(conn.responseCode, conn.inputStream.bufferedReader().readText())
        }
    }

    @Test
    fun loopbackReceivesCodeOnLocalhostOnly(): Unit = runBlocking {
        var status = 0
        var html = ""
        var redirectSeen = ""
        val redirect = LoopbackRedirect(openBrowser = fakeBrowser("code=one-time") { s, h -> status = s; html = h })

        val callback = redirect.authorize { appRedirect ->
            redirectSeen = appRedirect
            "https://djmetry.com/api/auth/google/start?mobile=1&app_redirect=$appRedirect"
        }

        assertTrue(Regex("""http://127\.0\.0\.1:\d+/oauth""").matches(redirectSeen), redirectSeen)
        assertEquals("one-time", Url(callback).parameters["code"])
        Thread.sleep(200)
        assertEquals(200, status)
        assertTrue("DJMetry" in html)
    }

    @Test
    fun loopbackPassesBackendErrors(): Unit = runBlocking {
        val redirect = LoopbackRedirect(openBrowser = fakeBrowser("error=user_blocked"))
        val callback = redirect.authorize { "https://djmetry.com/start?app_redirect=$it" }
        assertEquals("user_blocked", Url(callback).parameters["error"])
    }

    @Test
    fun loopbackServerIsClosedAfterLogin(): Unit = runBlocking {
        var redirectUri = ""
        LoopbackRedirect(openBrowser = fakeBrowser("code=x")).authorize { redirectUri = it; "https://djmetry.com/start?app_redirect=$it" }
        val result = runCatching { (URI("$redirectUri?code=again").toURL().openConnection() as HttpURLConnection).responseCode }
        assertTrue(result.isFailure, "после входа порт должен быть закрыт")
    }

    @Test
    fun loopbackTimesOut() {
        assertFailsWith<TimeoutCancellationException> {
            runBlocking { LoopbackRedirect(openBrowser = {}, timeoutMillis = 300).authorize { "https://djmetry.com/start?app_redirect=$it" } }
        }
    }

    // ───── схема djmetry:// ─────

    @Test
    fun schemeRedirectWaitsForOsDeepLink(): Unit = runBlocking {
        var appRedirect = ""
        val redirect = DesktopSchemeRedirect(openBrowser = {
            thread { Thread.sleep(100); DesktopDeepLinks.deliver("djmetry://oauth?code=from-os") }
        })
        val callback = redirect.authorize { appRedirect = it; "https://djmetry.com/start" }
        assertEquals("djmetry://oauth", appRedirect)
        assertEquals("from-os", Url(callback).parameters["code"])
        assertTrue(redirect.handoffPage, "системный браузер: просим у бэкенда страницу с кнопкой вместо голого 302")
    }

    @Test
    fun deepLinksIgnoreForeignSchemesAndUnexpectedLinks() {
        assertFalse(DesktopDeepLinks.deliver("https://evil.example/oauth?code=x"))
        assertFalse(DesktopDeepLinks.deliver("djmetry://oauth?code=nobody-waits"))
    }

    @Test
    fun newLoginCancelsPreviousWait(): Unit = runBlocking {
        val first = runCatching {
            DesktopSchemeRedirect(openBrowser = {
                thread {
                    Thread.sleep(50)
                    // второй вход начался раньше, чем пришла ссылка первого
                    runBlocking { DesktopSchemeRedirect(openBrowser = {}, timeoutMillis = 200).runCatching { authorize { "x" } } }
                }
            }, timeoutMillis = 2_000).authorize { "https://djmetry.com/start" }
        }
        assertIs<OAuthCancelledException>(first.exceptionOrNull())
    }

    @Test
    fun finishingOldAttemptDoesNotCancelNewOne() {
        // Ревью: finally первой попытки отменял ожидание второй — вход «не возвращался в приложение»
        val first = DesktopDeepLinks.expect()
        val second = DesktopDeepLinks.expect()
        DesktopDeepLinks.cancel(first)
        assertTrue(DesktopDeepLinks.deliver("djmetry://oauth?code=c2"))
        assertEquals("djmetry://oauth?code=c2", kotlinx.coroutines.runBlocking { second.await() })
    }
}
