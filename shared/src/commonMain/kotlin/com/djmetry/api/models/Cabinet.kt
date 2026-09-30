package com.djmetry.api.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// ───────── Колокольчик: GET /me/notifications, /me/notifications/unread-count ─────────

@Serializable
data class UnreadCount(val unread_total: Int = 0)

@Serializable
data class AppNotification(
    val id: String,
    val type: String, // release_radar | pre_save | booking | concert
    val read: Boolean = false,
    val created_at: String? = null,
    val title: String = "",
    val body: String? = null,
    val image_url: String? = null,
    val url: String? = null,
    /** release_radar / concert: `spotify_artist_id`, `album_id`, `event_id`… */
    val meta: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
data class NotificationsPage(
    val unread_total: Int = 0,
    val items: List<AppNotification> = emptyList(),
    val next_cursor: String? = null,
)

/** POST /me/notifications/read — либо `ids`, либо `all` (+ `type`). */
@Serializable
data class MarkReadRequest(val ids: List<String>? = null, val all: Boolean? = null, val type: String? = null)

// ───────── Букинг: /booking/artists/... ─────────

@Serializable
data class BookingCompany(
    val id: String,
    val name: String,
    val city: String? = null,
    val country: String? = null,
)

/** Публичный `GET /booking/artists/public/:id/booking` и авторизованный `/booking/artists/:id/companies`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookingCompaniesResponse(
    val count: Int? = null,
    @JsonNames("data") val companies: List<BookingCompany> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookingRequest(
    val id: String,
    @JsonNames("request_number", "number") val number: Int? = null,
    val status: String = "new",
    val event_date: String? = null,
    @JsonNames("event_country", "country_code") val country: String? = null,
    @JsonNames("event_city") val city: String? = null,
    val payment_status: String? = null,
    val payment_amount: Double? = null,
    val payment_currency: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookingRequestsResponse(@JsonNames("items", "data") val requests: List<BookingRequest> = emptyList())

// ───────── Release Radar: GET /me/release-radar/feed ─────────

@Serializable
data class ReleaseRadarRelease(
    val album_id: String,
    val name: String,
    val album_type: String? = null,
    val release_date: String? = null,
    val image_url: String? = null,
    val url: String? = null,
)

@Serializable
data class ReleaseRadarFeedArtist(
    val spotify_artist_id: String,
    val artist_name: String,
    val artist_image_url: String? = null,
    val latest: List<ReleaseRadarRelease> = emptyList(),
    val has_new_releases: Boolean = false,
    val artist_slug: String? = null,
    val genres: List<String> = emptyList(),
    /** Сколько релизов с даты отсечки (2026-01-01); больше, чем в [latest], — «Все (N)». */
    val total_releases: Int = 0,
    /** Свежих нет — в [latest] старые релизы («пока нет новинок»). */
    val showing_older: Boolean = false,
)

/** Все релизы артиста: `GET /me/release-radar/artist/:id/releases`. */
@Serializable
data class ArtistReleasesResponse(val releases: List<ReleaseRadarRelease> = emptyList(), val total: Int = 0)

@Serializable
data class ReleaseRadarFeed(val artists: List<ReleaseRadarFeedArtist> = emptyList())
