package com.djmetry.api

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/** Тело ошибки бэкенда: `{ error, code, message }`. */
@Serializable
internal data class ErrorBody(
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    val currentCount: Int? = null,
    val maxCount: Int? = null,
    val retry_after: Int? = null,
)

/**
 * Ошибка API. [code] — машинный код бэкенда (`unauthorized`, `artist_limit_reached`, `invalid_or_expired_code`, …).
 */
class ApiException(
    val status: Int,
    val code: String?,
    message: String?,
    val maxCount: Int? = null,
    /** Через сколько секунд можно повторить — из `Retry-After` / `retry_after` (лимит запросов). */
    val retryAfterSeconds: Int? = null,
) : Exception(message ?: code ?: "HTTP $status") {
    val isUnauthorized: Boolean get() = status == 401

    /** Сработал лимит запросов бэкенда: 429 или код `too_many_*` (в том числе из deep-link входа). */
    val isRateLimited: Boolean get() = status == 429 || code?.startsWith("too_many_") == true
}

/** Секунды из заголовков лимитера: `Retry-After` (секунды), иначе `RateLimit-Reset`. HTTP-дату не разбираем. */
internal fun retryAfterFrom(headers: Headers): Int? =
    (headers[HttpHeaders.RetryAfter] ?: headers["RateLimit-Reset"])?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

/** Выполняет запрос и превращает ответ в Result: 2xx → тело, иначе [ApiException] с кодом бэкенда. */
internal suspend inline fun <reified T> apiCall(request: () -> HttpResponse): Result<T> = try {
    val response = request()
    if (response.status.isSuccess()) {
        Result.success(response.body<T>())
    } else {
        val body = runCatching { response.body<ErrorBody>() }.getOrNull()
        Result.failure(
            ApiException(
                status = response.status.value,
                code = body?.code ?: body?.error,
                message = body?.message ?: body?.error,
                maxCount = body?.maxCount,
                retryAfterSeconds = body?.retry_after ?: retryAfterFrom(response.headers),
            )
        )
    }
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
