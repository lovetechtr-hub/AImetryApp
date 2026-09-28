package com.djmetry

import androidx.compose.runtime.staticCompositionLocalOf
import com.djmetry.api.createApiClient
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.api.endpoints.RadarApi
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.data.local.SessionStorage
import com.djmetry.data.repository.AuthRepository
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.data.repository.NotificationsRepository
import com.djmetry.data.repository.ProfileRepository
import com.djmetry.i18n.LocalizationManager

/** Зависимости приложения. Создаётся один раз на платформе (MainActivity / MainViewController). */
/** [engine] — для тестов (MockEngine); в приложении — платформенный HTTP-клиент (публичный конструктор). */
class AppContainer internal constructor(val storage: SessionStorage, private val engine: io.ktor.client.engine.HttpClientEngine?) {
    constructor(storage: SessionStorage) : this(storage, null)

    val localization = LocalizationManager(storage)

    private val http: io.ktor.client.HttpClient by lazy {
        createApiClient(
            tokenProvider = { auth.currentToken },
            languageProvider = { localization.currentLocale.value.code },
            engine = engine,
        )
    }

    val authApi: AuthApi by lazy { AuthApi(http) }
    val userApi: UserApi by lazy { UserApi(http) }
    val artistApi: ArtistApi by lazy { ArtistApi(http) }
    val auth: AuthRepository by lazy { AuthRepository(authApi, userApi, storage) }
    val discover: DiscoverRepository by lazy { DiscoverRepository(artistApi, userApi) }
    val notificationsApi: NotificationsApi by lazy { NotificationsApi(http) }
    val bookingApi: BookingApi by lazy { BookingApi(http) }
    val radarApi: RadarApi by lazy { RadarApi(http) }
    val notifications: NotificationsRepository by lazy { NotificationsRepository(notificationsApi) }
    val profile: ProfileRepository by lazy { ProfileRepository(userApi, artistApi, bookingApi, radarApi) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }
