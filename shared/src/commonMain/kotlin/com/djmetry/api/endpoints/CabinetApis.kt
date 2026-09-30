package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.contentType

/** Колокольчик (in-app уведомления). Нативные пуши (Firebase) придут позже и откроют этот же список. */
class NotificationsApi(private val http: HttpClient) {
    suspend fun unreadCount(): Result<UnreadCount> = apiCall { http.get("me/notifications/unread-count") }

    /** [type] — фильтр на стороне бэкенда: release_radar | pre_save | booking | concert; null — все. */
    suspend fun list(type: String? = null, cursor: String? = null, limit: Int = 15): Result<NotificationsPage> =
        apiCall {
            http.get("me/notifications") {
                type?.let { parameter("type", it) }
                cursor?.let { parameter("cursor", it) }
                parameter("limit", limit)
            }
        }

    suspend fun markRead(ids: List<String>): Result<SuccessResponse> =
        apiCall { http.post("me/notifications/read") { setBody(MarkReadRequest(ids = ids)) } }

    suspend fun markAllRead(type: String? = null): Result<SuccessResponse> =
        apiCall { http.post("me/notifications/read") { setBody(MarkReadRequest(all = true, type = type)) } }
}

/** Букинг артиста. Статусы и допустимые переходы считает бэкенд — клиент только показывает. */
class BookingApi(private val http: HttpClient) {
    suspend fun publicCompanies(spotifyArtistId: String): Result<BookingCompaniesResponse> =
        apiCall { http.get("booking/artists/public/$spotifyArtistId/booking") }

    suspend fun artistRequests(spotifyArtistId: String): Result<BookingRequestsResponse> =
        apiCall { http.get("booking/artists/$spotifyArtistId/requests") }

    // ── Вкладка «Букинг»: заказчик ──
    suspend fun myRequests(): Result<BookingRequestsResponse> = apiCall { http.get("booking/my-requests") { parameter("limit", 200) } }

    /** Мягкая отмена заказчиком — заявка остаётся видна агентству как недействительная. */
    suspend fun cancelMyRequest(id: String): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/my-requests/$id") }.map { }

    suspend fun restoreMyRequest(id: String): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> { http.post("booking/my-requests/$id/restore") }.map { }

    // ── Агентство ──
    suspend fun myCompanies(): Result<BookingCompaniesResponse> = apiCall { http.get("booking/companies/my") }

    suspend fun companyRequests(companyId: String): Result<BookingRequestsResponse> =
        apiCall { http.get("booking/companies/$companyId/requests") { parameter("limit", 200) } }

    /** Открыть заявку (бэкенд помечает прочитанной). */
    suspend fun companyRequest(id: String): Result<BookingRequestEnvelope> = apiCall { http.get("booking/requests/$id") }

    /** Смена статуса агентством (переходы — VALID_TRANSITIONS бэкенда). */
    suspend fun setCompanyStatus(id: String, status: String): Result<BookingRequestEnvelope> = apiCall {
        http.patch("booking/requests/$id") { contentType(io.ktor.http.ContentType.Application.Json); setBody(BookingStatusPatch(status)) }
    }

    // ── Артист ──
    suspend fun artistRequest(spotifyArtistId: String, id: String): Result<BookingRequestEnvelope> =
        apiCall { http.get("booking/artists/$spotifyArtistId/requests/$id") }

    /** Путевой статус артиста (+ транспорт для «в пути»). */
    suspend fun setArtistStatus(spotifyArtistId: String, id: String, status: String, transport: String?): Result<BookingRequestEnvelope> = apiCall {
        http.patch("booking/artists/$spotifyArtistId/requests/$id") {
            contentType(io.ktor.http.ContentType.Application.Json); setBody(BookingStatusPatch(status, transport))
        }
    }
}

@kotlinx.serialization.Serializable
internal data class BookingStatusPatch(val status: String, val travel_transport: String? = null)

/** Радары: превью Release Radar для дашборда. */
class RadarApi(private val http: HttpClient) {
    suspend fun releaseFeed(perArtist: Int = 5): Result<ReleaseRadarFeed> =
        apiCall { http.get("me/release-radar/feed") { parameter("per_artist", perArtist) } }

    /** Все релизы артиста (с 2026-01-01): поиск, даты, сортировка `date_desc` | `date_asc` | `name`, страницы. */
    suspend fun artistReleases(spotifyArtistId: String, q: String?, sort: String, offset: Int, limit: Int = 24): Result<ArtistReleasesResponse> =
        apiCall {
            http.get("me/release-radar/artist/$spotifyArtistId/releases") {
                parameter("limit", limit); parameter("offset", offset); parameter("sort", sort)
                q?.takeIf { it.isNotBlank() }?.let { parameter("q", it.trim()) }
            }
        }
}
