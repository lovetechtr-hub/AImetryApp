package com.djmetry.auth

import com.djmetry.config.AppConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.Locale

/** Как десктоп получает OAuth-редирект. Подробно — docs/RULES.md. */
enum class DesktopRedirectKind {
    /** `djmetry://oauth` — ОС открывает установленное приложение. Бэкенд менять не нужно. */
    Scheme,

    /** `http://127.0.0.1:<порт>/oauth` — локальный сервер (RFC 8252 §7.3). Работает и из исходников. */
    Loopback,
}

/**
 * Выбор стратегии.
 * - [override] — переменная окружения `DJMETRY_OAUTH_REDIRECT` = `scheme` | `loopback`;
 * - иначе установленное приложение (jpackage) → [DesktopRedirectKind.Scheme],
 *   запуск из исходников (`./gradlew :desktopApp:run`) → [DesktopRedirectKind.Loopback]:
 *   схема `djmetry://` в этом режиме в ОС не зарегистрирована.
 */
fun chooseDesktopRedirect(override: String?, isPackaged: Boolean): DesktopRedirectKind =
    when (override?.trim()?.lowercase(Locale.ROOT)) {
        "scheme" -> DesktopRedirectKind.Scheme
        "loopback" -> DesktopRedirectKind.Loopback
        else -> if (isPackaged) DesktopRedirectKind.Scheme else DesktopRedirectKind.Loopback
    }

/** jpackage выставляет `jpackage.app-path` у установленного приложения. */
fun isPackagedApp(): Boolean = System.getProperty("jpackage.app-path") != null

/** Открывает URL в браузере по умолчанию. */
fun openInSystemBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        return
    }
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val command = when {
        os.contains("mac") -> listOf("open", url)
        os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
        else -> listOf("xdg-open", url)
    }
    ProcessBuilder(command).start()
}

/**
 * Приёмник deep-link `djmetry://…` на десктопе. Ссылки приходят из ОС:
 * macOS — `Desktop.setOpenURIHandler`, Windows/Linux — аргумент второго экземпляра, пересланный первому
 * ([com.djmetry.desktop.SingleInstance]).
 */
object DesktopDeepLinks {
    private var pending: CompletableDeferred<String>? = null

    /** Ссылка от ОС. Возвращает true, если её ждал вход. */
    @Synchronized
    fun deliver(uri: String): Boolean {
        if (!uri.startsWith("${AppConfig.OAUTH_SCHEME}://")) return false
        val deferred = pending ?: return false
        pending = null
        return deferred.complete(uri)
    }

    @Synchronized
    internal fun expect(): CompletableDeferred<String> {
        pending?.completeExceptionally(OAuthCancelledException())
        return CompletableDeferred<String>().also { pending = it }
    }

    /** Отменить свою попытку входа — и только её: новая попытка, начатая следом, не страдает. */
    @Synchronized
    internal fun cancel(own: CompletableDeferred<String>) {
        if (pending !== own) return
        own.completeExceptionally(OAuthCancelledException())
        pending = null
    }
}

/** Десктоп + схема `djmetry://`: работает в установленном приложении (схема регистрируется установщиком). */
class DesktopSchemeRedirect(
    private val openBrowser: (String) -> Unit = ::openInSystemBrowser,
    private val timeoutMillis: Long = LOGIN_TIMEOUT_MS,
) : OAuthRedirect {
    /** Системный браузер: 302 на `djmetry://` Chrome может не открыть — просим страницу с кнопкой. */
    override val handoffPage: Boolean get() = true

    override suspend fun authorize(startUrlFor: (String) -> String): String {
        val deferred = DesktopDeepLinks.expect()
        withContext(Dispatchers.IO) { openBrowser(startUrlFor(AppConfig.OAUTH_REDIRECT)) }
        return try {
            withTimeout(timeoutMillis) { deferred.await() }
        } finally {
            DesktopDeepLinks.cancel(deferred)
        }
    }
}

/**
 * Десктоп + loopback (RFC 8252 §7.3): поднимаем HTTP-сервер на 127.0.0.1 со случайным портом,
 * передаём бэкенду `app_redirect=http://127.0.0.1:<порт>/oauth` и ждём, пока браузер туда вернётся.
 * Слушаем только loopback-интерфейс; сервер живёт до первого ответа или таймаута.
 * Требует поддержки на бэкенде — контракт в docs/BACKEND_API.md.
 */
class LoopbackRedirect(
    private val openBrowser: (String) -> Unit = ::openInSystemBrowser,
    private val timeoutMillis: Long = LOGIN_TIMEOUT_MS,
) : OAuthRedirect {
    override suspend fun authorize(startUrlFor: (String) -> String): String {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val result = CompletableDeferred<String>()
        server.createContext(PATH) { exchange ->
            val query = exchange.requestURI.rawQuery.orEmpty()
            val ok = !query.contains("error=")
            val body = callbackPage(ok).toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            result.complete("http://$HOST:${server.address.port}$PATH" + if (query.isEmpty()) "" else "?$query")
        }
        server.start()
        try {
            val redirectUri = "http://$HOST:${server.address.port}$PATH"
            withContext(Dispatchers.IO) { openBrowser(startUrlFor(redirectUri)) }
            return withTimeout(timeoutMillis) { result.await() }
        } finally {
            server.stop(0)
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PATH = "/oauth"
    }
}

/** Страница в браузере после возврата: вход завершён — можно вернуться в приложение. */
internal fun callbackPage(success: Boolean): String {
    val title = if (success) "Готово — вернитесь в DJMetry" else "Не удалось войти"
    val text = if (success) "Вход выполнен. Это окно можно закрыть." else "Вернитесь в приложение и попробуйте ещё раз."
    return """<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>DJMetry</title>
<style>body{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;background:#0B1220;color:#E6EEFC;font-family:-apple-system,Segoe UI,Roboto,sans-serif}
div{text-align:center}h1{font-size:28px;margin:18px 0 8px}p{color:#9AB0D5}b{color:#5EE6A8;font-size:56px}</style></head>
<body><div><b>#</b><h1>$title</h1><p>$text</p></div></body></html>"""
}

private const val LOGIN_TIMEOUT_MS = 5 * 60_000L

internal actual fun platformOAuthRedirect(): OAuthRedirect =
    when (chooseDesktopRedirect(System.getenv("DJMETRY_OAUTH_REDIRECT"), isPackagedApp())) {
        DesktopRedirectKind.Scheme -> DesktopSchemeRedirect()
        DesktopRedirectKind.Loopback -> LoopbackRedirect()
    }

internal actual suspend fun authenticateInBrowser(startUrl: String, callbackScheme: String): String =
    DesktopSchemeRedirect().authorize { startUrl }
