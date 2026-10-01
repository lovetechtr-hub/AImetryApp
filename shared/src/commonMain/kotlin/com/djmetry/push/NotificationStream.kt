package com.djmetry.push

import com.djmetry.api.DJMetryJson
import com.djmetry.data.repository.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable

/** Кадр SSE: `id:`, `event:`, `data:` (многострочные `data` склеиваются через перевод строки). */
data class SseEvent(val id: String?, val event: String, val data: String)

/**
 * Разбор Server-Sent Events построчно (spec WHATWG): пустая строка — конец кадра, `:` — комментарий (heartbeat),
 * `retry:` — пауза перед переподключением. Без сети и корутин — чтобы тестировать.
 */
class SseParser {
    private var id: String? = null
    private var event = "message"
    private val data = StringBuilder()
    /** Пауза переподключения из `retry:`, мс. */
    var retryMs: Long? = null
        private set

    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) {
            if (data.isEmpty()) { event = "message"; return null }
            val e = SseEvent(id, event, data.toString().removeSuffix("\n"))
            data.clear(); event = "message"
            return e
        }
        if (line.startsWith(":")) return null
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> data.append(value).append('\n')
            "retry" -> value.toLongOrNull()?.let { retryMs = it }
        }
        return null
    }
}

/** Уведомление из потока `/me/notifications/stream` (та же запись, что в `/me/notifications`). */
@Serializable
data class StreamNotification(
    val stream_id: Long? = null,
    val id: String = "",
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    val url: String? = null,
    val image_url: String? = null,
    /** Поля события (request_id, event_id, album_id…) — для перехода по клику, как у пуша. */
    val meta: kotlinx.serialization.json.JsonObject? = null,
) {
    /** Плоские строковые поля для [PushTokens.onNotificationOpened] (+ `type`). */
    val pushData: Map<String, String>
        get() = buildMap {
            meta?.forEach { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.let { put(k, it.content) } }
            type?.let { put("type", it) }
        }
}

/**
 * Уведомления в реальном времени для десктопа (у JVM нет FCM/APNs): `GET /api/me/notifications/stream` (SSE, Bearer).
 * Пока пользователь вошёл — держим соединение; оборвалось (сеть, мягкое закрытие сервером раз в 15 мин) —
 * переподключаемся с `Last-Event-ID`, чтобы догнать пропущенное. Вышел — поток закрывается.
 * Гейт (общий переключатель и типы из `push-preferences`) применяет бэкенд.
 */
class NotificationStream(private val http: HttpClient) {
    private var lastEventId: String? = null
    private var streamUser: String? = null

    suspend fun run(session: StateFlow<SessionState>, onNotification: suspend (StreamNotification) -> Unit) {
        session.collectLatest { state ->
            if (state !is SessionState.SignedIn) { lastEventId = null; return@collectLatest }
            // Другой аккаунт — догонять с чужого Last-Event-ID нельзя
            val user = state.me.userId ?: state.me.user?.id
            if (user != streamUser) { lastEventId = null; streamUser = user }
            var backoff = RETRY_MS
            while (true) {
                val parser = SseParser()
                val ok = try {
                    connect(parser, onNotification)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                // Нормальное закрытие — быстро обратно; ошибка — с нарастающей паузой до минуты
                backoff = if (ok) parser.retryMs ?: RETRY_MS else (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                delay(if (ok) parser.retryMs ?: RETRY_MS else backoff)
            }
        }
    }

    /** Одно соединение до закрытия. false — сервер ответил ошибкой (401, 5xx). */
    private suspend fun connect(parser: SseParser, onNotification: suspend (StreamNotification) -> Unit): Boolean =
        http.prepareGet("me/notifications/stream") {
            header("Accept", "text/event-stream")
            lastEventId?.let { header("Last-Event-ID", it) }
            // Поток живёт долго: общий таймаут запроса выключаем; heartbeat бэкенда — каждые ~25 с
            timeout { requestTimeoutMillis = io.ktor.client.plugins.HttpTimeoutConfig.INFINITE_TIMEOUT_MS; socketTimeoutMillis = 70_000 }
        }.execute { response ->
            if (!response.status.isSuccess()) return@execute false
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                val event = parser.feed(line) ?: continue
                event.id?.let { lastEventId = it }
                if (event.event != "notification") continue
                runCatching { DJMetryJson.decodeFromString(StreamNotification.serializer(), event.data) }
                    .getOrNull()?.let { onNotification(it) }
            }
            true
        }

    companion object {
        const val RETRY_MS = 3_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
