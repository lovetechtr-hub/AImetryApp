package com.djmetry.api.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.Serializable

// ───────── Топы, рейтинг, тренды, DJ Mag, Talents — docs §5.3–5.6 ─────────

/**
 * Артист в рейтингах. Внимание: прод отдаёт разные схемы —
 * `artists/top100…` в snake_case (`spotify_artist_id`, `image_url`, `score`, `djmag_rank`),
 * `artists/trends` и `artists/top` в camelCase (`spotifyArtistId`, `imageUrl`, `aimetryScore`).
 * Модель принимает оба варианта.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RankedArtist(
    @JsonNames("spotify_artist_id") val spotifyArtistId: String,
    val name: String,
    @JsonNames("image_url") val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("aimetryScore") @JsonNames("score") val djmetryScore: Double? = null,
    val rank: Int? = null,
    val position: Int? = null,
    val previousRank: Int? = null,
    val positionChange: Int? = null,
    val rankChange: String? = null, // "up" | "down" | "new" | null
    @JsonNames("djmag_rank") val djMagRank: Int? = null,
    @JsonNames("dj_tier") val djTier: String? = null,
    val status: String? = null,
    val votes: Int? = null,
    val description: String? = null,
    val slug: String? = null,
    val trend: TrendInfo? = null,
)

@Serializable
data class ArtistsListResponse(val count: Int? = null, val artists: List<RankedArtist> = emptyList())

@Serializable
data class Pagination(val page: Int = 1, val limit: Int = 0, val total: Int = 0, val totalPages: Int = 0)

@Serializable
data class RankingPageResponse(val artists: List<RankedArtist> = emptyList(), val pagination: Pagination? = null)

@Serializable
data class TrendsResponse(
    val category: String? = null,
    val count: Int = 0,
    val averageScore: Double? = null,
    val artists: List<RankedArtist> = emptyList(),
)

@Serializable
data class DJMagRankingsResponse(val year: Int? = null, val count: Int = 0, val rankings: List<DJMagRanking> = emptyList())

@Serializable
data class DJMagRanking(
    val rank: Int,
    val name: String,
    val spotifyArtistId: String? = null,
    val imageUrl: String? = null,
    val spotifyUrl: String? = null,
    val previousYearRank: Int? = null,
)

@Serializable
data class TalentsResponse(val count: Int = 0, val artists: List<TalentArtist> = emptyList())

@Serializable
data class TalentArtist(
    val rank: Int? = null,
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val talentScore: Double? = null,
    val growthScore: Double? = null,
    val consistencyScore: Double? = null,
    val communityScore: Double? = null,
    val weeksInRank: Int? = null,
)
