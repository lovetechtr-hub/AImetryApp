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
)

/**
 * Ошибка API. [code] — машинный код бэкенда (`unauthorized`, `artist_limit_reached`, `invalid_or_expired_code`, …).
 */
class ApiException(
    val status: Int,
    val code: String?,
    message: String?,
    val maxCount: Int? = null,
) : Exception(message ?: code ?: "HTTP $status") {
    val isUnauthorized: Boolean get() = status == 401
}

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
            )
        )
    }
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
