package com.djmetry.api.models

import kotlinx.serialization.Serializable

/**
 * Мировая карта диджеев (спека §16). Все `/api/map/…` публичные, snake_case (кроме `spotifyArtistId` в `/dj/:id/tour`).
 * Контракт — djmetry-api/src/api/routes/map.ts, docs/BACKEND_API.md → «Карта диджеев».
 */
@Serializable
data class MapServerCluster(val lat: Double, val lng: Double, val count: Int = 0, val top: Boolean = false)

@Serializable
data class MapEventPoint(
    val event_id: String,
    val spotify_artist_id: String,
    val artist_name: String,
    val artist_image_url: String? = null,
    val genres: List<String> = emptyList(),
    val datetime: String? = null,
    val venue_name: String? = null,
    val venue_city: String? = null,
    val venue_region: String? = null,
    val venue_country: String? = null,
    val lat: Double,
    val lng: Double,
    /** Страница события Bandsintown — кнопка «Билеты». */
    val url: String? = null,
)

@Serializable
data class MapPointsResponse(val clusters: List<MapServerCluster> = emptyList(), val points: List<MapEventPoint> = emptyList(), val total: Int = 0)

@Serializable
data class MapDjSummary(
    val spotify_artist_id: String? = null,
    val has_points: Boolean = false,
    val point_count: Int = 0,
    val country_count: Int = 0,
    val countries: List<String> = emptyList(),
    val first_datetime: String? = null,
    val last_datetime: String? = null,
)

@Serializable
data class MapVenue(
    val id: String,
    val name: String,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val country_code: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val venue_type: String? = null,
    val is_top: Boolean = false,
    val description: String? = null,
    val address: String? = null,
    val website: String? = null,
    val image_url: String? = null,
    val image_attribution: String? = null,
    val google_place_id: String? = null,
    val event_count: Int = 0,
    val artist_count: Int = 0,
)

@Serializable
data class MapVenuesResponse(val clusters: List<MapServerCluster> = emptyList(), val points: List<MapVenue> = emptyList(), val total: Int = 0)

@Serializable
data class MapVenueArtist(
    val spotify_artist_id: String,
    val name: String,
    val image_url: String? = null,
    val slug: String? = null,
    val datetime: String? = null,
    val ticket_url: String? = null,
)

@Serializable
data class MapVenueArtistsResponse(val artists: List<MapVenueArtist> = emptyList(), val total: Int = 0)

/** Диджей в попапе страны: `/top-artists` (играют в стране) и `/origin-artists` (родом оттуда — без даты и билетов). */
@Serializable
data class MapCountryArtist(
    val spotify_artist_id: String,
    val name: String,
    val image_url: String? = null,
    val slug: String? = null,
    val ticket_url: String? = null,
    val spotify_url: String? = null,
    val city: String? = null,
    val datetime: String? = null,
)

/** `total` у `/top-artists` — длина страницы, у `/origin-artists` — полное число. */
@Serializable
data class MapCountryArtistsResponse(val artists: List<MapCountryArtist> = emptyList(), val total: Int = 0, val shown: Int? = null)

@Serializable
data class DensityCountry(val country: String, val country_code: String? = null, val count: Int = 0, val djs: Int = 0)

@Serializable
data class DensityCity(val city: String, val country: String? = null, val country_code: String? = null, val lat: Double, val lng: Double, val count: Int = 0, val djs: Int = 0)

@Serializable
data class DensityResponse(
    val countries: List<DensityCountry> = emptyList(),
    val cities: List<DensityCity> = emptyList(),
    val venues: List<MapVenue> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MapTouringDj(
    val spotify_artist_id: String,
    val name: String,
    val image_url: String? = null,
    val slug: String? = null,
    val spotify_url: String? = null,
    val events: Int = 0,
    val cities: Int = 0,
    val countries: Int = 0,
    val genres: List<String> = emptyList(),
)

@Serializable
data class TopTouringResponse(val djs: List<MapTouringDj> = emptyList(), val total: Int = 0)

@Serializable
data class MapOrigin(val country: String, val country_code: String? = null, val count: Int = 0, val genres: Map<String, Int> = emptyMap())

@Serializable
data class DjOriginsResponse(val origins: List<MapOrigin> = emptyList(), val total: Int = 0)

@Serializable
data class MapFilterCountry(val country: String, val count: Int = 0)

@Serializable
data class MapFiltersResponse(val countries: List<MapFilterCountry> = emptyList(), val genres: List<String> = emptyList())
