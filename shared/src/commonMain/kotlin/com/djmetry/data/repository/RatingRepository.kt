package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.DJMagRankingsResponse
import com.djmetry.api.models.RankedArtist
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Подборки таблицы рейтинга (вкладки над таблицей). */
enum class RatingSource { Top100, Rising, Breakthrough, DJMag }

/** Что показывать под местом: сдвиг в рейтинге или прирост Score — оба посчитаны бэкендом. */
sealed interface RatingChange {
    /** Сдвиг места: +2 — поднялся на 2. */
    data class Places(val delta: Int) : RatingChange
    /** Прирост Score за 7 дней (подборки «Растут», «Прорывы»). */
    data class Growth(val score: Double) : RatingChange
}

/** Одна строка таблицы — общая для всех рейтингов (TOP 100…1000, тренды, DJ Mag, дальше жанры и страны). */
data class RatingRow(
    val position: Int,
    val spotifyArtistId: String?,
    val name: String,
    val imageUrl: String?,
    val genre: String?,
    val score: Double?,
    val change: RatingChange?,
    val djMagRank: Int?,
    val followers: Long?,
)

/** Подиум (2-е, 1-е, 3-е — слева направо) и остальной список. Меньше трёх строк — без подиума. */
data class PodiumSplit(val podium: List<RatingRow>, val rest: List<RatingRow>)

internal fun podiumSplit(rows: List<RatingRow>): PodiumSplit =
    if (rows.size < 3) PodiumSplit(emptyList(), rows)
    else PodiumSplit(listOf(rows[1], rows[0], rows[2]), rows.drop(3))

/**
 * Таблица рейтинга. Места, Score, сдвиги и прирост считает бэкенд; здесь только приведение
 * разных ответов (`top100` snake_case, `trends` camelCase, `djmag/rankings`) к одной строке.
 */
class RatingRepository(private val artistApi: ArtistApi) {
    private val lock = Mutex()
    private val cache = mutableMapOf<RatingSource, List<RatingRow>>()

    suspend fun load(source: RatingSource, refresh: Boolean = false): Result<List<RatingRow>> {
        if (!refresh) lock.withLock { cache[source] }?.let { return Result.success(it) }
        val result = when (source) {
            RatingSource.Top100 -> artistApi.topN(1).map { fromTop(it.artists) }
            RatingSource.Rising -> artistApi.trends("growing", limit = 100).map { fromTrends(it.artists) }
            RatingSource.Breakthrough -> artistApi.trends("breakthrough", limit = 100).map { fromTrends(it.artists) }
            RatingSource.DJMag -> artistApi.djMagRankings().map(::fromDjMag)
        }
        result.onSuccess { rows -> lock.withLock { cache[source] = rows } }
        return result
    }

    internal companion object {
        fun fromTop(list: List<RankedArtist>): List<RatingRow> = list.mapIndexed { i, a ->
            RatingRow(
                position = a.position ?: a.rank ?: (i + 1),
                spotifyArtistId = a.spotifyArtistId, name = a.name, imageUrl = a.imageUrl,
                genre = a.genres.firstOrNull(), score = a.djmetryScore,
                change = a.positionChange?.takeIf { it != 0 }?.let { RatingChange.Places(it) },
                djMagRank = a.djMagRank, followers = a.followers,
            )
        }

        /** В трендах мест нет — порядок бэкенда, под местом прирост Score за 7 дней. */
        fun fromTrends(list: List<RankedArtist>): List<RatingRow> = list.mapIndexed { i, a ->
            RatingRow(
                position = i + 1,
                spotifyArtistId = a.spotifyArtistId, name = a.name, imageUrl = a.imageUrl,
                genre = a.genres.firstOrNull(), score = a.djmetryScore,
                change = (a.trend?.score7d ?: a.trend?.score24h)?.let { RatingChange.Growth(it) },
                djMagRank = a.djMagRank, followers = a.followers,
            )
        }

        /** DJ Mag: сдвиг = место в прошлом году − место сейчас (поднялся — плюс). */
        fun fromDjMag(r: DJMagRankingsResponse): List<RatingRow> = r.rankings.map { d ->
            RatingRow(
                position = d.rank,
                spotifyArtistId = d.spotifyArtistId, name = d.name, imageUrl = d.imageUrl,
                genre = null, score = null,
                change = d.previousYearRank?.let { prev -> (prev - d.rank).takeIf { it != 0 }?.let { RatingChange.Places(it) } },
                djMagRank = d.rank, followers = null,
            )
        }
    }
}
