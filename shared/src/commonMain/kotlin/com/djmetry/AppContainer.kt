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
import com.djmetry.data.repository.ArtistRepository
import com.djmetry.data.repository.AuthRepository
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.data.repository.NotificationsRepository
import com.djmetry.data.repository.ProfileRepository
import com.djmetry.data.repository.RatingRepository
import com.djmetry.data.repository.SettingsRepository
import com.djmetry.data.repository.ArtistEditorRepository
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.SettingsApi
import com.djmetry.i18n.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Зависимости приложения. Создаётся один раз на платформе (MainActivity / MainViewController). */
/** [engine] — для тестов (MockEngine); в приложении — платформенный HTTP-клиент (публичный конструктор). */
class AppContainer internal constructor(val storage: SessionStorage, private val engine: io.ktor.client.engine.HttpClientEngine?) {
    constructor(storage: SessionStorage) : this(storage, null)

    val localization = LocalizationManager(storage)

    /** Фоновые задачи уровня приложения (проверка сессии по 401). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http: io.ktor.client.HttpClient by lazy {
        createApiClient(
            tokenProvider = { auth.currentToken },
            languageProvider = { localization.currentLocale.value.code },
            // 401 посреди сессии: токен истёк или отозван — проверяем /me и, если так, выходим
            onUnauthorized = { sent -> appScope.launch { auth.verifySession(sent) } },
            engine = engine,
        )
    }

    val authApi: AuthApi by lazy { AuthApi(http) }
    val userApi: UserApi by lazy { UserApi(http) }
    val artistApi: ArtistApi by lazy { ArtistApi(http) }
    val auth: AuthRepository by lazy {
        AuthRepository(
            authApi, userApi, storage,
            beforeSignOut = { push.unregister() },
            retryUnregister = { bearer, token -> push.unregisterWith(bearer, token) },
            onSignOutAborted = { push.sync(com.djmetry.push.PushTokens.token.value, auth.session.value) },
            clearUserData = { userScoped().forEach { it.clearUserData() } },
        )
    }

    /** Всё, что хранит данные вошедшего пользователя: при выходе и новом входе сбрасывается. */
    internal fun userScoped(): List<com.djmetry.data.repository.UserScoped> =
        listOf(discover, radar, settings, artistEditor, notifications, analytics, audience)
    /** Токен пушей устройства ↔ вошедший пользователь (POST / DELETE /push/devices). */
    val push: com.djmetry.push.PushRegistrar by lazy { com.djmetry.push.PushRegistrar(com.djmetry.push.PushApi(http)) }
    /** Десктоп: уведомления в реальном времени по SSE (у JVM нет FCM/APNs). */
    val notificationStream: com.djmetry.push.NotificationStream by lazy { com.djmetry.push.NotificationStream(http) }
    val discover: DiscoverRepository by lazy { DiscoverRepository(artistApi, userApi) { rating.topArtists() } }
    val notificationsApi: NotificationsApi by lazy { NotificationsApi(http) }
    val bookingApi: BookingApi by lazy { BookingApi(http) }
    val radarApi: RadarApi by lazy { RadarApi(http) }
    val notifications: NotificationsRepository by lazy { NotificationsRepository(notificationsApi) }
    val booking: com.djmetry.data.repository.BookingRepository by lazy { com.djmetry.data.repository.BookingRepository(bookingApi, ArtistEditorApi(http)) }
    val radar: com.djmetry.data.repository.RadarRepository by lazy { com.djmetry.data.repository.RadarRepository(radarApi, artistApi, notificationsApi, settingsApi) }
    val profile: ProfileRepository by lazy { ProfileRepository(userApi, artistApi, bookingApi, radarApi, radar) }
    val artists: ArtistRepository by lazy { ArtistRepository(artistApi, bookingApi) }
    val rating: RatingRepository by lazy { RatingRepository(artistApi) }
    val settingsApi: SettingsApi by lazy { SettingsApi(http) }
    val settings: SettingsRepository by lazy { SettingsRepository(settingsApi) }
    val artistEditor: ArtistEditorRepository by lazy { ArtistEditorRepository(ArtistEditorApi(http), artistApi) }
    val analytics: com.djmetry.data.repository.AnalyticsRepository by lazy { com.djmetry.data.repository.AnalyticsRepository(com.djmetry.api.endpoints.AnalyticsApi(http)) }
    val audience: com.djmetry.data.repository.AudienceRepository by lazy { com.djmetry.data.repository.AudienceRepository(com.djmetry.api.endpoints.AudienceApi(http)) }
    val djMap: com.djmetry.data.repository.DjMapRepository by lazy { com.djmetry.data.repository.DjMapRepository(com.djmetry.api.endpoints.DjMapApi(http)) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }
