package com.djmetry.api

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import platform.Foundation.*

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                // Вход — только Bearer: cookie-сессия бэкенда главнее Bearer, общий NSHTTPCookieStorage её бы подхватил
                setHTTPShouldHandleCookies(false)
            }
            configureSession {
                setHTTPCookieStorage(null)
                setHTTPShouldSetCookies(false)
            }
        }
    }
}

