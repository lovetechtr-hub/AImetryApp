package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*

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
}

/** Радары: превью Release Radar для дашборда. */
class RadarApi(private val http: HttpClient) {
    suspend fun releaseFeed(perArtist: Int = 5): Result<ReleaseRadarFeed> =
        apiCall { http.get("me/release-radar/feed") { parameter("per_artist", perArtist) } }
}
