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
    val slug: String? = null,
    val image_url: String? = null,
    /** `owner` | `manager` — только в `/companies/my`. */
    val my_role: String? = null,
    /** none | pending_moderation | approved | rejected | blocked */
    val moderation_status: String? = null,
    /** Публичный список агентств артиста: ростер — галочки в форме заявки без второго запроса. */
    val artists: List<BookingCompanyArtist> = emptyList(),
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
    // ── Полная заявка (docs/BACKEND_API.md → «Букинг»; сериализатор toBookingRequestResponse) ──
    val booking_company_id: String? = null,
    val artists: List<BookingRequestArtist> = emptyList(),
    val requester_name: String? = null,
    val requester_email: String? = null,
    val event_type: String? = null,
    val event_time: String? = null,
    /** «город, страна / площадка» — как ввёл заказчик. */
    val event_location: String? = null,
    val expected_attendees: Long? = null,
    val message: String? = null,
    /** plane | train | car | ship — при «в пути». */
    val travel_transport: String? = null,
    val payment_percent: Double? = null,
    val company_fee_amount: Double? = null,
    val artist_fee_amount: Double? = null,
    val company_tax_percent: Double? = null,
    val artist_tax_percent: Double? = null,
    val artist_calculates_own_tax: Boolean? = null,
    /** Гонорары после налога — считает бэкенд; null — налога нет или артист считает налог сам. */
    val company_fee_amount_after_tax: Double? = null,
    val artist_fee_amount_after_tax: Double? = null,
    /** club | festival | private | corporate | wedding | other. */
    val event_type_key: String? = null,
    val is_read: Boolean = true,
    val deleted_by_requester: Boolean = false,
    /** Заказчику — плоско; `null` — агентство отключено. */
    val company_name: String? = null,
    val company_image_url: String? = null,
    /** Артисту — `{id, name}`. */
    val company: BookingCompanyRef? = null,
    val created_at: String? = null,
    // ── Поля сервера для вкладки (docs/BACKEND_API.md → «Букинг: агрегат, статусы, поля») ──
    /** new | confirmed | active | completed | declined */
    val stage: String? = null,
    /** company | artist | requester */
    val my_role: String? = null,
    /** Какие статусы сейчас можно поставить — кнопки (роль сервер всё равно проверит). */
    val allowed_statuses: List<String>? = null,
    /** Непрочитано именно для моей роли (у агентства и артиста — раздельно). */
    val unread: Boolean? = null,
    /** IANA-пояс площадки; null — страна в нескольких поясах. */
    val event_timezone: String? = null,
) {
    /** Новое для меня: серверный флаг роли, на старом бэкенде — общий `is_read`. */
    val isUnread: Boolean get() = unread ?: !is_read
}

@Serializable
data class BookingRequestArtist(
    val spotify_artist_id: String,
    val name: String? = null,
    val image_url: String? = null,
    val artist_fee_amount: Double? = null,
    val artist_tax_percent: Double? = null,
)

@Serializable
data class BookingCompanyRef(val id: String? = null, val name: String? = null, val slug: String? = null, val image_url: String? = null)

/** Одна заявка: `{request: {...}}` (GET/PATCH). */
@Serializable
data class BookingRequestEnvelope(val request: BookingRequest)

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
