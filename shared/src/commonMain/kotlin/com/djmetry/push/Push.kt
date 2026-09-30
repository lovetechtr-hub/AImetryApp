package com.djmetry.push

import com.djmetry.api.apiCall
import com.djmetry.data.repository.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
object PushTokens {
    private val _token = MutableStateFlow<PushToken?>(null)
    val token: StateFlow<PushToken?> = _token.asStateFlow()

    private val _opened = MutableStateFlow<String?>(null)
    /** Ссылка из тапнутого уведомления; экран сбрасывает её через [consumeOpened] после перехода. */
    val opened: StateFlow<String?> = _opened.asStateFlow()

    fun onNewToken(token: String, platform: String) {
        if (token.isNotBlank()) _token.value = PushToken(token, platform)
    }

    fun onNotificationOpened(url: String?) {
        if (!url.isNullOrBlank()) _opened.value = url
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

    suspend fun unregister(token: String): Result<Unit> = apiCall<Unit> {
        http.delete("push/devices") { contentType(ContentType.Application.Json); setBody(PushDeviceDelete(token)) }
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

    suspend fun unregister() = lock.withLock {
        registered?.let { (token, _) -> api.unregister(token) }
        registered = null
    }

    /** Следит за токеном и сессией, пока приложение открыто. */
    suspend fun run(tokens: StateFlow<PushToken?>, session: StateFlow<SessionState>) {
        combine(tokens, session) { t, s -> t to s }.collect { (t, s) -> sync(t, s) }
    }
}
