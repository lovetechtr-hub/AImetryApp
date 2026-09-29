package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Поля соцсетей редактора артиста — ровно как на бэке (`POST /me/artist/socials`). */
enum class SocialField(val key: String) {
    Instagram("instagram"), Facebook("facebook"), TikTok("tiktok"), Twitter("twitter"), SoundCloud("soundcloud"),
    YouTube("youtube"), AppleMusic("appleMusicUrl"), Beatport("beatportUrl"), Telegram("telegram"),
}

/** Райдер и пресс-кит — одинаковые эндпоинты, разный путь. */
enum class BookingDoc(val path: String) { Rider("rider"), PressKit("press-kit") }

/**
 * Редактор проверенного артиста (спека §14). Контракт — docs/BACKEND_API.md → «Редактор артиста».
 * ⚠️ Соцсети и локация: бэк меняет только пришедшие поля, очистка — явным `null` (пустая строка в локации не проходит `min(1)`),
 * а общий сериализатор null не шлёт — поэтому эти тела собираются вручную.
 */
class ArtistEditorApi(private val http: HttpClient) {

    suspend fun saveSocials(values: Map<SocialField, String>): Result<JsonObject> = apiCall {
        http.post("me/artist/socials") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                SocialField.values().forEach { f ->
                    val v = values[f]?.trim().orEmpty()
                    put(f.key, if (v.isEmpty()) JsonNull else JsonPrimitive(v))
                }
            })
        }
    }

    suspend fun saveGenres(genres: List<String>): Result<JsonObject> = apiCall {
        http.post("me/artist/genres") { contentType(ContentType.Application.Json); setBody(GenresPatch(genres)) }
    }

    /** Страна — ISO2 (обязательна), город и регион — строка или null (очистить). */
    suspend fun saveLocation(country: String, city: String?, region: String?): Result<JsonObject> = apiCall {
        http.post("me/artist/update-location") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("country", country)
                put("city", city?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("region", region?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        }
    }

    suspend fun tracks(): Result<CuratedTracksResponse> = apiCall { http.get("me/artist/tracks") }

    suspend fun addTrack(trackUrl: String): Result<CuratedTracksResponse> = apiCall {
        http.post("me/artist/tracks") { contentType(ContentType.Application.Json); setBody(buildJsonObject { put("trackUrl", trackUrl) }) }
    }

    suspend fun removeTrack(trackId: String): Result<CuratedTracksResponse> = apiCall { http.delete("me/artist/tracks/$trackId") }

    /** Бэк читает `trackIds`; сайт шлёт и `track_ids` — повторяем для совместимости. */
    suspend fun reorderTracks(ids: List<String>): Result<CuratedTracksResponse> = apiCall {
        http.put("me/artist/tracks/reorder") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("trackIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                put("track_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            })
        }
    }

    /** Нет файла — бэк отвечает 404: это не ошибка, а «не загружен». */
    suspend fun doc(artistId: String, doc: BookingDoc): Result<BookingDocResponse?> =
        apiCall<BookingDocResponse> { http.get("booking/artists/$artistId/${doc.path}") }
            .fold({ Result.success(it) }, { e -> if ((e as? com.djmetry.api.ApiException)?.status == 404) Result.success(null) else Result.failure(e) })

    suspend fun uploadDoc(artistId: String, doc: BookingDoc, file: PickedFile): Result<BookingDocResponse> = apiCall {
        http.submitFormWithBinaryData(
            url = "booking/artists/$artistId/${doc.path}",
            formData = formData {
                append("file", file.bytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/pdf")
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                })
            },
        ) { method = HttpMethod.Put }
    }

    suspend fun deleteDoc(artistId: String, doc: BookingDoc): Result<JsonObject> = apiCall { http.delete("booking/artists/$artistId/${doc.path}") }
}
