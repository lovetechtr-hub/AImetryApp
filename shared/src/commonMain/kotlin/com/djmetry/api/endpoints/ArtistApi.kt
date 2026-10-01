package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonObject

/** Публичное discovery API (без авторизации), docs §5. */
class ArtistApi(private val http: HttpClient) {

    suspend fun search(query: String, limit: Int = 10): Result<ArtistSearchResult> =
        apiCall { http.get("artists/search") { parameter("q", query); parameter("limit", limit.coerceIn(1, 50)) } }

    suspend fun details(spotifyArtistId: String, lang: String? = null): Result<ArtistDetailsResponse> =
        apiCall { http.get("artists/spotify/$spotifyArtistId") { lang?.let { parameter("lang", it) } } }

    /** Slug приходит из внешней ссылки — экранируем: «../» не уведёт запрос на другой эндпоинт. */
    suspend fun detailsBySlug(slug: String, lang: String? = null): Result<ArtistDetailsResponse> =
        apiCall { http.get("artists/by-slug/${slug.encodeURLPathPart()}") { lang?.let { parameter("lang", it) } } }

    suspend fun tracks(spotifyArtistId: String, limit: Int = 10): Result<ArtistTracksResponse> =
        apiCall { http.get("artists/spotify/$spotifyArtistId/tracks") { parameter("limit", limit.coerceIn(1, 50)) } }

    suspend fun events(spotifyArtistId: String): Result<ArtistEventsResponse> = apiCall { http.get("artists/$spotifyArtistId/events") }

    suspend fun top(limit: Int = 10): Result<ArtistsListResponse> =
        apiCall { http.get("artists/top") { parameter("limit", limit.coerceIn(1, 200)); parameter("lite", 1) } }

    /** Срезы TOP 100 … TOP 1000: [hundreds] = 1..10. category = "dj" | "ambient". */
    suspend fun topN(hundreds: Int = 1, category: String? = null, genre: String? = null, country: String? = null): Result<ArtistsListResponse> =
        apiCall {
            http.get("artists/top${hundreds.coerceIn(1, 10) * 100}") {
                // Список: без регионов и голосов на каждого артиста (бэкенд убирает N+1) — заметно быстрее
                parameter("lite", 1)
                category?.let { parameter("category", it) }
                genre?.let { parameter("genre", it) }
                country?.let { parameter("country", it) }
            }
        }

    suspend fun ranking(page: Int = 1, limit: Int = 50, category: String? = null, genre: String? = null, country: String? = null): Result<RankingPageResponse> =
        apiCall {
            http.get("artists/ranking") {
                parameter("page", page)
                parameter("limit", limit.coerceIn(1, 100))
                category?.let { parameter("category", it) }
                genre?.let { parameter("genre", it) }
                country?.let { parameter("country", it) }
            }
        }

    /** category = growing | breakthrough | stable | losing_momentum; sortBy = score24h | score3d | score7d. */
    suspend fun trends(category: String? = null, limit: Int = 20, sortBy: String? = null): Result<TrendsResponse> =
        apiCall {
            http.get("artists/trends") {
                category?.let { parameter("category", it) }
                parameter("limit", limit)
                sortBy?.let { parameter("sortBy", it) }
            }
        }

    suspend fun availableGenres(): Result<JsonObject> = apiCall { http.get("artists/available-genres") }

    suspend fun availableCountries(): Result<JsonObject> = apiCall { http.get("artists/available-countries") }

    suspend fun djMagRankings(year: Int? = null): Result<DJMagRankingsResponse> =
        apiCall { http.get("djmag/rankings") { if (year != null) parameter("year", year) else parameter("latest", true) } }

    /** Какие диапазоны доступны для категории (dj — без параметра, ambient). */
    suspend fun availableLimits(category: String? = null): Result<AvailableLimits> =
        apiCall { http.get("artists/available-limits") { category?.let { parameter("category", it) } } }

    suspend fun genres(category: String? = null): Result<GenresResponse> =
        apiCall { http.get("artists/available-genres") { category?.let { parameter("category", it) } } }

    /** Talents для таблицы рейтинга: тот же формат, что top100 (snake_case), счёт — `talent_score`. */
    suspend fun talentsRanking(limit: Int = 200, category: String? = null, genre: String? = null, country: String? = null): Result<ArtistsListResponse> =
        apiCall {
            http.get("artists/talents") {
                parameter("limit", limit)
                category?.let { parameter("category", it) }
                genre?.let { parameter("genre", it) }
                country?.let { parameter("country", it) }
            }
        }

    /** DJ Mag за все годы одним запросом (кэшируем в репозитории). */
    suspend fun djMagAll(): Result<DJMagAllResponse> = apiCall { http.get("djmag/rankings/all") }

    /** До 200 артистов за раз (лимит бэкенда 30 запросов в минуту) — без треков. */
    suspend fun batch(ids: List<String>): Result<ArtistsBatchResponse> = apiCall {
        http.post("artists/batch") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(ArtistsBatchRequest(ids.take(200), includeTracks = false))
        }
    }

    suspend fun yearRanking(year: Int): Result<YearRankingResponse> = apiCall { http.get("dj/year-ranking") { parameter("year", year) } }

    /** Пусто, если на проде не включён ENABLE_TALENT_RANKING. */
    suspend fun talents(limit: Int = 100, category: String? = null): Result<TalentsResponse> =
        apiCall { http.get("talents/top100") { parameter("limit", limit); category?.let { parameter("category", it) } } }
}
