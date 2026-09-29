package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*

/**
 * Мировая карта диджеев (спека §16): публичные `/api/map/…`, лимит 100 запросов в минуту на IP.
 * `country` у выступлений и площадок — ИМЯ страны (как у Bandsintown), у попапов стран — ISO2.
 * bbox — `запад,юг,восток,север`.
 */
class DjMapApi(private val http: HttpClient) {

    suspend fun performances(params: List<Pair<String, String>>): Result<MapPointsResponse> =
        apiCall { http.get("map/performances") { params.forEach { (k, v) -> parameter(k, v) } } }

    /** Весь тур одного DJ по дате (включая прошедшие) — для дуг маршрута. */
    suspend fun tour(spotifyArtistId: String): Result<MapPointsResponse> = apiCall { http.get("map/dj/$spotifyArtistId/tour") }

    suspend fun summary(spotifyArtistId: String): Result<MapDjSummary> = apiCall { http.get("map/dj/$spotifyArtistId/summary") }

    suspend fun venues(params: List<Pair<String, String>>): Result<MapVenuesResponse> =
        apiCall { http.get("map/venues") { params.forEach { (k, v) -> parameter(k, v) } } }

    suspend fun venueArtists(venueId: String): Result<MapVenueArtistsResponse> = apiCall { http.get("map/venues/$venueId/artists") }

    suspend fun topArtists(iso2: String, genre: String?): Result<MapCountryArtistsResponse> = apiCall {
        http.get("map/top-artists") { parameter("country", iso2.uppercase()); genre?.let { parameter("genre", it) }; parameter("limit", 50) }
    }

    suspend fun originArtists(iso2: String, genre: String?): Result<MapCountryArtistsResponse> = apiCall {
        http.get("map/origin-artists") { parameter("country", iso2.uppercase()); genre?.let { parameter("genre", it) }; parameter("limit", 50) }
    }

    suspend fun density(params: List<Pair<String, String>>): Result<DensityResponse> =
        apiCall { http.get("map/event-density") { params.forEach { (k, v) -> parameter(k, v) } } }

    suspend fun topTouring(params: List<Pair<String, String>>): Result<TopTouringResponse> =
        apiCall { http.get("map/top-touring") { params.forEach { (k, v) -> parameter(k, v) } } }

    suspend fun origins(genre: String?): Result<DjOriginsResponse> = apiCall { http.get("map/dj-origins") { genre?.let { parameter("genre", it) } } }

    suspend fun filters(): Result<MapFiltersResponse> = apiCall { http.get("map/filters") }
}
