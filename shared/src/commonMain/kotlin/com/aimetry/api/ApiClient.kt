package com.aimetry.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(): HttpClient

fun createApiClient(baseUrl: String = "http://127.0.0.1:8080/api"): HttpClient {
    return createPlatformHttpClient().config {
        // Cookies поддерживаются автоматически через platform-specific engines
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
}

