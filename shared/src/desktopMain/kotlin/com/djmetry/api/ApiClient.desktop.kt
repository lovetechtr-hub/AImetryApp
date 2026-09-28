package com.djmetry.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)
