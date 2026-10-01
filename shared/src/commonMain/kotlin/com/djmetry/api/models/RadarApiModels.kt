package com.djmetry.api.models

import kotlinx.serialization.Serializable

// ───────── Радар: агрегаты бэкенда (`/me/radar/artists`, `/me/concerts`, `/me/radar/seen`) ─────────

@Serializable
data class RadarNextConcert(
    val event_id: String? = null,
    val datetime: String? = null,
    val city: String? = null,
    val country: String? = null,
    val near: Boolean = false,
)

/** Подписка с бейджами: сервер сортирует — непрочитанное, концерт в 30 днях, имя. */
@Serializable
data class RadarArtistSummary(
    val spotify_artist_id: String,
    val name: String = "",
    val image_url: String? = null,
    val slug: String? = null,
    val unread_releases: Int = 0,
    val unread_concerts: Int = 0,
    val latest_release_date: String? = null,
    val total_releases: Int = 0,
    val upcoming_concerts: Int = 0,
    val next_concert: RadarNextConcert? = null,
)

@Serializable
data class RadarArtistsResponse(val artists: List<RadarArtistSummary> = emptyList(), val total: Int = 0)

/** Концерт подписки одним списком (кэш Bandsintown); `near` — по локации Concert Radar. */
@Serializable
data class MeConcert(
    val event_id: String,
    val spotify_artist_id: String,
    val artist_name: String = "",
    val artist_image_url: String? = null,
    val artist_slug: String? = null,
    val datetime: String = "",
    val title: String? = null,
    val url: String? = null,
    val venue_name: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val near: Boolean = false,
)

@Serializable
data class MeConcertsResponse(
    val concerts: List<MeConcert> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    val effective_city: String? = null,
    val effective_country: String? = null,
)

/** `kind`: release | concert | all; без артиста — всё выбранного вида. */
@Serializable
internal data class RadarSeenBody(val kind: String, val spotify_artist_id: String? = null)
