package com.djmetry

import kotlinx.coroutines.sync.withLock
import com.djmetry.api.createApiClient
import com.djmetry.data.local.SessionStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*

/** Хранилище в памяти вместо Keychain / EncryptedSharedPreferences. */
class FakeSessionStorage(var token: String? = null) : SessionStorage {
    private var savedLocale: String? = null
    var onboardingSeen = false
    override fun saveAuthToken(token: String?) { this.token = token }
    override fun getAuthToken(): String? = token
    override fun saveLocale(locale: String) { savedLocale = locale }
    override fun getLocale(): String? = savedLocale
    override fun setOnboardingSeen() { onboardingSeen = true }
    override fun isOnboardingSeen(): Boolean = onboardingSeen
    override fun clearAuth() { token = null }
    var pending: String? = null
    val prefs = mutableMapOf<String, String>()
    override fun getPref(key: String): String? = prefs[key]
    override fun setPref(key: String, value: String?) { if (value == null) prefs.remove(key) else prefs[key] = value }
    var nav: String? = null
    override fun saveNavState(value: String?) { nav = value }
    override fun getNavState(): String? = nav
    override fun savePendingSignOut(value: String?) { pending = value }
    override fun getPendingSignOut(): String? = pending
}

/** Фейковый бэкенд: маршрут "METHOD /path" → (статус, JSON). Записывает все запросы. */
class FakeBackend(private val routes: Map<String, Pair<HttpStatusCode, String>>) {
    val requests = mutableListOf<HttpRequestData>()

    // Параллельные запросы (на iOS — разные потоки) пишут в список по очереди, иначе запись теряется
    private val lock = kotlinx.coroutines.sync.Mutex()

    val engine = MockEngine { request ->
        lock.withLock { requests += request }
        val key = "${request.method.value} ${request.url.encodedPath}"
        val (status, body) = routes[key] ?: (HttpStatusCode.NotFound to """{"error":"not_found","code":"not_found"}""")
        // 204 — как у настоящего бэкенда: без тела и без Content-Type
        if (status == HttpStatusCode.NoContent) respond("", status)
        else respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    fun client(token: () -> String? = { null }, language: () -> String? = { null }): HttpClient =
        createApiClient(tokenProvider = token, languageProvider = language, engine = engine)

    fun request(method: String, path: String): HttpRequestData? =
        requests.lastOrNull { it.method.value == method && it.url.encodedPath == path }
}

const val ME_AUTHED = """{"isAuthed":true,"userId":"u1","user":{"id":"u1","displayName":"Slava","email":"x@y.z"},"stats":{"followsCount":2,"votesCount":1,"maxFollows":10,"maxVotes":3},"unknownFieldFromBackend":42}"""
const val ME_GUEST = """{"isAuthed":false}"""
