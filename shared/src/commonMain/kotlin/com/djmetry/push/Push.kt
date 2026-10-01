package com.djmetry.push

import com.djmetry.api.apiCall
import com.djmetry.data.repository.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.client.request.header
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** FCM registration token устройства и платформа (`android` | `ios`). */
data class PushToken(val value: String, val platform: String)

/**
 * Мост от нативного кода к общему. Android — `DJMetryMessagingService.onNewToken` и `FirebaseMessaging.token`,
 * iOS — делегат Firebase Messaging (`PushTokens.shared.onNewToken(token:platform:)`). Тап по уведомлению —
 * [onNotificationOpened] с `data.url`, приложение открывает нужный экран.
 */
/** Тап по уведомлению: ссылка и поля пуша. */
data class OpenedPush(val url: String?, val data: Map<String, String> = emptyMap()) {
    val type: String? get() = data["type"]
    /** Поля пуша как meta уведомления — тот же разбор, что у строки колокольчика. */
    val meta: kotlinx.serialization.json.JsonObject
        get() = kotlinx.serialization.json.JsonObject(data.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
}

object PushTokens {
    private val _token = MutableStateFlow<PushToken?>(null)
    val token: StateFlow<PushToken?> = _token.asStateFlow()

    private val _opened = MutableStateFlow<OpenedPush?>(null)
    /** Тапнутое уведомление (ссылка + поля `data`); экран сбрасывает его через [consumeOpened] после перехода. */
    val opened: StateFlow<OpenedPush?> = _opened.asStateFlow()

    fun onNewToken(token: String, platform: String) {
        if (token.isNotBlank()) _token.value = PushToken(token, platform)
    }

    fun onNotificationOpened(url: String?) = onNotificationOpened(url, emptyMap())

    /**
     * [data] — `message.data` пуша: бэкенд кладёт туда `type` и плоские поля meta (`request_id`, `company_id`,
     * `spotify_artist_id`, `album_id`, `event_id`…) — по ним открываем саму заявку, релиз или концерт.
     */
    fun onNotificationOpened(url: String?, data: Map<String, String>) {
        val d = data.filterValues { it.isNotBlank() }
        if (url.isNullOrBlank() && d.isEmpty()) return
        _opened.value = OpenedPush(url?.takeIf { it.isNotBlank() } ?: d["url"], d)
    }

    fun consumeOpened() { _opened.value = null }
}

@Serializable
internal data class PushDeviceBody(val transport: String, val token: String, val platform: String? = null, val app: String? = null)

@Serializable
internal data class PushDeviceDelete(val token: String)

/** `POST` / `DELETE /api/push/devices` (docs/BACKEND_API.md → «Пуши»): пользователь — из сессии, ответ 204. */
class PushApi(private val http: HttpClient) {
    suspend fun register(token: PushToken): Result<Unit> = apiCall<Unit> {
        http.post("push/devices") { contentType(ContentType.Application.Json); setBody(PushDeviceBody("fcm", token.value, token.platform, "mobile")) }
    }

    /** [bearer] — снять токен от имени старой сессии (повтор после выхода без сети). */
    suspend fun unregister(token: String, bearer: String? = null): Result<Unit> = apiCall<Unit> {
        http.delete("push/devices") {
            contentType(ContentType.Application.Json)
            bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(PushDeviceDelete(token))
        }
    }
}

/**
 * Держит токен устройства зарегистрированным за вошедшим пользователем: после входа, при новом токене
 * (переустановка, ротация) и при смене аккаунта. Перед выходом — [unregister], пока сессия ещё жива,
 * иначе на телефон продолжат идти пуши прошлого пользователя.
 */
class PushRegistrar(private val api: PushApi) {
    private val lock = Mutex()
    /** Что уже зарегистрировано: токен + пользователь. */
    private var registered: Pair<String, String>? = null

    suspend fun sync(token: PushToken?, session: SessionState) = lock.withLock {
        val user = (session as? SessionState.SignedIn)?.me?.let { it.userId ?: it.user?.id ?: "me" } ?: return@withLock
        if (token == null || registered == token.value to user) return@withLock
        api.register(token).onSuccess { registered = token.value to user }
    }

    /** Снять токен с сервера. Не вышло (нет сети) — вернуть его, чтобы выход повторил снятие позже. */
    suspend fun unregister(): String? = lock.withLock {
        val token = registered?.first
        registered = null
        token?.takeIf { api.unregister(it).isFailure }
    }

    /** Повтор снятия после выхода без сети: от имени старой сессии [bearer]. */
    suspend fun unregisterWith(bearer: String, token: String): Result<Unit> = api.unregister(token, bearer)

    /** Следит за токеном и сессией, пока приложение открыто. */
    suspend fun run(tokens: StateFlow<PushToken?>, session: StateFlow<SessionState>) {
        combine(tokens, session) { t, s -> t to s }.collect { (t, s) -> sync(t, s) }
    }
}
