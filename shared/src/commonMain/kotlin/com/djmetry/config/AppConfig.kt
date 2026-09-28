package com.djmetry.config

/**
 * Настройки подключения к бэкенду DJMetry.
 * API-префикс вынесен отдельно: за прод-прокси он может отличаться (см. docs/BACKEND_API.md).
 */
object AppConfig {
    const val BASE_URL = "https://djmetry.com"
    const val API_PREFIX = "/api"
    val apiUrl: String get() = BASE_URL + API_PREFIX

    /** Deep-link, куда бэкенд возвращает OAuth-код. Схема должна быть в MOBILE_OAUTH_REDIRECT_SCHEMES на бэке. */
    const val OAUTH_SCHEME = "djmetry"
    const val OAUTH_REDIRECT = "$OAUTH_SCHEME://oauth"

    /** Публичная страница артиста на сайте. */
    fun artistUrl(spotifyArtistId: String) = "$BASE_URL/artist/$spotifyArtistId"

    const val TERMS_URL = "$BASE_URL/terms"
    const val PRIVACY_URL = "$BASE_URL/privacy"
}
