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
    /** Talents: свой счёт (`talent_score`), показываем вместо Score. */
    @JsonNames("talent_score") val talentScore: Double? = null,
    val rank: Int? = null,
    val position: Int? = null,
    /** Talents: `position` — место среди талантов, общее место в DJMetry — здесь. */
    @JsonNames("global_position") val globalPosition: Int? = null,
    val previousRank: Int? = null,
    val positionChange: Int? = null,
    val rankChange: String? = null, // "up" | "down" | "new" | null
    @JsonNames("djmag_rank", "djmagRank") val djMagRank: Int? = null,
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
    /** Прод иногда отдаёт здесь NaN (невалидный JSON) — читаем как null, чтобы не терять весь рейтинг. */
    /** `"NEW"` — впервые в рейтинге ([DJMAG_NEW]). */
    @Serializable(with = PreviousRankSerializer::class) val previousYearRank: Int? = null,
    /** Впервые в рейтинге — теперь отдельным флагом (раньше строкой «NEW» в previousYearRank). */
    val isNew: Boolean? = null,
    /** Жанры и slug — от бэкенда (фото — с запасным из карточек артистов), без второго запроса за ними. */
    val genres: List<String> = emptyList(),
    val slug: String? = null,
)

/** `POST /artists/batch` — данные артистов по списку Spotify id (для DJ Mag: фото, жанры, Score). */
@Serializable
data class ArtistsBatchResponse(val artists: List<RankedArtist> = emptyList())

@Serializable
internal data class ArtistsBatchRequest(val spotifyArtistIds: List<String>, val includeTracks: Boolean = false)

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

/** GET /artists/available-limits?category= — какие диапазоны есть: "100"…"1000", "talents". */
@Serializable
data class AvailableLimits(val limits: List<String> = emptyList())

@Serializable
data class GenresResponse(val genres: List<String> = emptyList())

/** GET /djmag/rankings/all — все годы одним запросом; ключ карты — год строкой. */
@Serializable
data class DJMagAllResponse(val years: List<Int> = emptyList(), val rankings: Map<String, List<DJMagRanking>> = emptyMap())

/** GET /dj/year-ranking?year= — итоги года; пока не финализирован — `finalized=false` и пустой список. */
@Serializable
data class YearRankingResponse(
    val year: Int? = null,
    val finalized: Boolean = false,
    val message: String? = null,
    val rankings: List<RankedArtist> = emptyList(),
)
