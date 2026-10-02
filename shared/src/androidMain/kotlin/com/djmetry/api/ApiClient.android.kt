package com.djmetry.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import java.util.concurrent.TimeUnit

/**
 * OkHttp, а не движок `Android` (HttpURLConnection): тот при отмене запроса закрывал соединение в потоке
 * отменяющего — то есть в главном — и приложение падало с NetworkOnMainThreadException (карта DJ отменяет
 * запрос области на каждое движение камеры, экраны — при уходе). OkHttp закрывает соединения в своих потоках.
 */
actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
    }
}
