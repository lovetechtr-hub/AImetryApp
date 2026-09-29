package com.djmetry.api.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// ───────── Настройки пользователя и артиста (docs/BACKEND_API.md → «Настройки») ─────────

/** Колокольчик в приложении: мастер + типы (release_radar, pre_save, booking, concert). Нет типа — включён. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationsSettings(
    @JsonNames("in_app_enabled") val inAppEnabled: Boolean = true,
    val types: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class NotificationsSettingsPatch(val inAppEnabled: Boolean? = null, val types: Map<String, Boolean>? = null)

@Serializable
data class PushPreferencesPatch(val enabled: Boolean? = null, val types: Map<String, Boolean>? = null)

@Serializable
data class ReleaseRadarPatch(val releaseRadarEnabled: Boolean? = null, val releaseRadarFrequency: String? = null)

/** PUT /me/concert-alerts — всё сразу (явная кнопка «Сохранить»). Пустая строка страны/города = брать из профиля. */
@Serializable
data class ConcertAlertsPatch(
    val concertAlertsEnabled: Boolean,
    val concertAlertsFrequency: String,
    val concertAlertCountry: String,
    val concertAlertCity: String,
)

/**
 * PATCH /me/settings/profile — регион и дата рождения (спека называла `profile-region`, на бэке этот путь).
 * Пустая строка очищает поле (бэкенд: `'' → null`); null не отправляем — сериализатор без explicitNulls.
 */
@Serializable
data class ProfileSettingsPatch(
    val country: String,
    val city: String,
    val region: String,
    val birthDate: String,
)

@Serializable
data class LanguagePatch(val language: String)

@Serializable
data class GenresPatch(val genres: List<String>)

@Serializable
data class CountriesResponse(val count: Int = 0, val countries: List<Country> = emptyList())

@Serializable
data class Country(val code: String, val name: String)

@Serializable
data class CitiesResponse(val country: String? = null, val count: Int = 0, val cities: List<City> = emptyList())

@Serializable
data class City(val name: String, val population: Long? = null)

@Serializable
data class GenresList(val genres: List<String> = emptyList())
