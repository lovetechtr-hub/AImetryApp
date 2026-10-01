package com.djmetry.api

import com.djmetry.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(): HttpClient

internal val DJMetryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = false
}

/**
 * HTTP-клиент DJMetry: base URL из [AppConfig], JSON и `Authorization: Bearer <token>`,
 * если пользователь вошёл. Заголовок Origin не подставляем — иначе бэкенд включит CSRF-проверку.
 */
fun createApiClient(
    tokenProvider: () -> String?,
    languageProvider: () -> String? = { null },
    engine: HttpClientEngine? = null, // для тестов: MockEngine
    /** 401 на запрос с нашим токеном — сессия, возможно, истекла (проверит [com.djmetry.data.repository.AuthRepository.verifySession]). */
    onUnauthorized: (sentToken: String) -> Unit = {},
): HttpClient = (engine?.let { HttpClient(it) } ?: createPlatformHttpClient()).config {
    expectSuccess = false
    defaultRequest {
        url(AppConfig.apiUrl + "/")
        contentType(ContentType.Application.Json)
        languageProvider()?.let { header(HttpHeaders.AcceptLanguage, it) }
    }
    install(ContentNegotiation) { json(DJMetryJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
    }
    install(Logging) {
        level = LogLevel.INFO
        // В лог — метод и путь без query: там бывают секреты (токен подтверждения агентства), поиск и город
        logger = object : Logger { override fun log(message: String) = Logger.DEFAULT.log(stripQuery(message)) }
    }
    install(createClientPlugin("DJMetryBearer") {
        onRequest { request, _ ->
            // Явный заголовок (повтор выхода старой сессией) не перетираем
            if (request.headers[HttpHeaders.Authorization] == null) {
                tokenProvider()?.let { request.headers[HttpHeaders.Authorization] = "Bearer $it" }
            }
        }
        onResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                response.call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.let(onUnauthorized)
            }
        }
    })
}

/** Убрать query из адресов в строке лога: `…/preview?token=abc` → `…/preview?…`. */
internal fun stripQuery(line: String): String = Regex("""(https?://[^\s?]+)\?[^\s]*""").replace(line) { "${it.groupValues[1]}?…" }
