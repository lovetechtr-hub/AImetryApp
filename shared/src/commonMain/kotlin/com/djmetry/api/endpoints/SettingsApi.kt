package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Простые тумблеры писем и уведомлений: у всех одна форма — `GET` → `{<field>: bool}`, `PUT {<field>: bool}`.
 * [artistsOnly] — только для проверенных артистов (гейтинг из спеки).
 */
enum class EmailToggle(val path: String, val field: String, val artistsOnly: Boolean = false) {
    SmartLink("me/smart-link-notifications", "smartLinkNotificationsEnabled"),
    TrackSupport("me/track-support-emails", "trackSupportEmailsEnabled"),
    Weekly("me/weekly-digest", "weeklyDigestEnabled"),
    Venues("me/venues-digest", "venuesDigestEnabled"),
    Ranking("me/ranking-digest", "rankingDigestEnabled", artistsOnly = true),
    Talents("me/talents-digest", "talentsDigestEnabled", artistsOnly = true),
}

/** Экран «Настройки». Все запросы авторизованы. Контракт — docs/BACKEND_API.md → «Настройки». */
class SettingsApi(private val http: HttpClient) {

    suspend fun toggle(t: EmailToggle): Result<Boolean> =
        apiCall<JsonObject> { http.get(t.path) }.map { it.getValue(t.field).jsonPrimitive.boolean }

    suspend fun setToggle(t: EmailToggle, value: Boolean): Result<Boolean> =
        apiCall<JsonObject> {
            http.put(t.path) { contentType(ContentType.Application.Json); setBody(buildJsonObject { put(t.field, value) }) }
        }.map { it[t.field]?.jsonPrimitive?.boolean ?: value }

    suspend fun push(): Result<PushPreferences> = apiCall { http.get("me/push-preferences") }
    suspend fun setPush(patch: PushPreferencesPatch): Result<PushPreferences> =
        apiCall { http.put("me/push-preferences") { contentType(ContentType.Application.Json); setBody(patch) } }

    suspend fun inApp(): Result<NotificationsSettings> = apiCall { http.get("me/notifications-settings") }
    suspend fun setInApp(patch: NotificationsSettingsPatch): Result<NotificationsSettings> =
        apiCall { http.put("me/notifications-settings") { contentType(ContentType.Application.Json); setBody(patch) } }

    suspend fun releaseRadar(): Result<ReleaseRadarSettings> = apiCall { http.get("me/release-radar") }
    suspend fun setReleaseRadar(patch: ReleaseRadarPatch): Result<ReleaseRadarSettings> =
        apiCall { http.put("me/release-radar") { contentType(ContentType.Application.Json); setBody(patch) } }

    suspend fun concertAlerts(): Result<ConcertAlertsSettings> = apiCall { http.get("me/concert-alerts") }
    suspend fun setConcertAlerts(patch: ConcertAlertsPatch): Result<ConcertAlertsSettings> =
        apiCall { http.put("me/concert-alerts") { contentType(ContentType.Application.Json); setBody(patch) } }

    suspend fun saveProfile(patch: ProfileSettingsPatch): Result<JsonObject> =
        apiCall { http.patch("me/settings/profile") { contentType(ContentType.Application.Json); setBody(patch) } }

    suspend fun saveLanguage(code: String): Result<JsonObject> =
        apiCall { http.put("me/settings/language") { contentType(ContentType.Application.Json); setBody(LanguagePatch(code)) } }

    suspend fun saveGenres(genres: List<String>): Result<JsonObject> =
        apiCall { http.post("me/settings/music-genres") { contentType(ContentType.Application.Json); setBody(GenresPatch(genres)) } }

    /** Все жанры для выбора «Музыкальных направлений» (публичный список бэкенда). */
    suspend fun genres(): Result<List<String>> = apiCall<GenresList> { http.get("artists/available-genres") }.map { it.genres }

    suspend fun countries(): Result<CountriesResponse> = apiCall { http.get("location/countries") }
    suspend fun cities(country: String, query: String? = null, limit: Int = 30): Result<CitiesResponse> =
        apiCall {
            http.get("location/cities") {
                parameter("country", country)
                query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
                parameter("limit", limit)
            }
        }
}
