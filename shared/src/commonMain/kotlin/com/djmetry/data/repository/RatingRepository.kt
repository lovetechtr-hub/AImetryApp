package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.DJMagAllResponse
import com.djmetry.api.models.DJMagRanking
import com.djmetry.api.models.RankedArtist
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Тип рейтинга — верхняя капсула (как табы сайта aimetry / ambient / djmag / year). */
enum class RatingType(val category: String?) {
    DJMetry(null), Ambient("ambient"), DJMag(null), Year(null);

    /** Рейтинги по Score: диапазоны сотен + Talents, фильтры страна и жанр. У DJ Mag и итогов — годы, без фильтров. */
    val byScore: Boolean get() = this == DJMetry || this == Ambient
}

/** Деление на шкале под капсулой: сотня мест, Talents или год. */
sealed interface RatingRange {
    /** Места [to]−99…[to]; [to] = 100…1000 (эндпоинт `top{to}`). */
    data class Top(val to: Int) : RatingRange
    data object Talents : RatingRange
    data class Year(val year: Int) : RatingRange
}

/** Что показать: тип, деление шкалы, фильтры. Страна и жанр — только у рейтингов по Score. */
data class RatingQuery(
    val type: RatingType = RatingType.DJMetry,
    val range: RatingRange = RatingRange.Top(100),
    val genre: String? = null,
    val country: String? = null,
)

/** Что показывать под местом: сдвиг в рейтинге или прирост Score — оба посчитаны бэкендом. */
sealed interface RatingChange {
    /** Сдвиг места: +2 — поднялся на 2. */
    data class Places(val delta: Int) : RatingChange
    /** Прирост Score за 7 дней. */
    data class Growth(val score: Double) : RatingChange
}

/** Одна строка таблицы — общая для всех рейтингов. [talent] — счёт Talents (подпись «Talent score»). */
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
    val talent: Boolean = false,
)

/** Страница рейтинга. [notFinalized] — итоги года ещё не подведены (бэкенд отдаёт пусто и `finalized=false`). */
data class RatingPage(val rows: List<RatingRow>, val notFinalized: Boolean = false)

/** Подиум (2-е, 1-е, 3-е — слева направо) и остальной список. */
data class PodiumSplit(val podium: List<RatingRow>, val rest: List<RatingRow>)

/** Подиум — только если первые три строки — места 1, 2, 3 (корона у #1; в сотнях 101+ и в фильтре жанра — без подиума). */
internal fun podiumSplit(rows: List<RatingRow>): PodiumSplit =
    if (rows.size >= 3 && rows[0].position == 1 && rows[1].position == 2 && rows[2].position == 3)
        PodiumSplit(listOf(rows[1], rows[0], rows[2]), rows.drop(3))
    else PodiumSplit(emptyList(), rows)

/** Деления шкалы: для Score — доступные бэкенду диапазоны ("100"…"1000", "talents"); для DJ Mag и итогов — годы (новые слева). */
fun rulerRanges(type: RatingType, limits: List<String>, years: List<Int>): List<RatingRange> =
    if (type.byScore) limits.mapNotNull { l -> if (l == "talents") RatingRange.Talents else l.toIntOrNull()?.let { RatingRange.Top(it) } }
    else years.sortedDescending().map { RatingRange.Year(it) }

/** Подпись деления: «1–100», «101–200», «Talents», «2025». */
fun rangeLabel(range: RatingRange): String = when (range) {
    is RatingRange.Top -> "${range.to - 99}–${range.to}"
    RatingRange.Talents -> "Talents"
    is RatingRange.Year -> range.year.toString()
}

/**
 * Таблица рейтинга (docs/BACKEND_API.md → «Рейтинг»). Места, Score, сдвиги, фильтрация по жанру и стране — бэкенд;
 * здесь только выбор эндпоинта и приведение разных ответов к одной строке.
 */
class RatingRepository(private val artistApi: ArtistApi) {
    private val lock = Mutex()
    private val cache = mutableMapOf<RatingQuery, RatingPage>()
    private val limitsCache = mutableMapOf<RatingType, List<String>>()
    private val genresCache = mutableMapOf<RatingType, List<String>>()
    private var djMagAll: DJMagAllResponse? = null

    /** TOP 100 без фильтров — для боковой панели колоды. */
    suspend fun top100(): Result<List<RatingRow>> = load(RatingQuery()).map { it.rows }

    suspend fun load(query: RatingQuery, refresh: Boolean = false): Result<RatingPage> {
        if (!refresh) lock.withLock { cache[query] }?.let { return Result.success(it) }
        val q = if (query.type.byScore) query else query.copy(genre = null, country = null)
        val result: Result<RatingPage> = when (val r = q.range) {
            is RatingRange.Top -> artistApi.topN(r.to / 100, q.type.category, q.genre, q.country).map { RatingPage(fromTop(it.artists)) }
            RatingRange.Talents -> artistApi.talentsRanking(200, q.type.category, q.genre, q.country).map { RatingPage(fromTop(it.artists, talent = true)) }
            is RatingRange.Year -> if (q.type == RatingType.DJMag) djMag().map { RatingPage(fromDjMag(it.rankings[r.year.toString()].orEmpty())) }
            else artistApi.yearRanking(r.year).map { y -> RatingPage(fromTop(y.rankings), notFinalized = !y.finalized) }
        }
        result.onSuccess { page -> lock.withLock { cache[query] = page } }
        return result
    }

    /** Диапазоны для типа: у Score — от бэкенда (`available-limits`), у DJ Mag — годы из `/all`, у итогов — годы DJ Mag с текущего. */
    suspend fun ranges(type: RatingType): Result<List<RatingRange>> = when (type) {
        RatingType.DJMetry, RatingType.Ambient -> {
            lock.withLock { limitsCache[type] }?.let { Result.success(rulerRanges(type, it, emptyList())) }
                ?: artistApi.availableLimits(type.category).map { it.limits }.onSuccess { l -> lock.withLock { limitsCache[type] = l } }
                    .map { rulerRanges(type, it, emptyList()) }
        }
        RatingType.DJMag -> djMag().map { rulerRanges(type, emptyList(), it.years) }
        RatingType.Year -> djMag().map { d -> rulerRanges(type, emptyList(), yearsForResults(d.years)) }
    }

    /** Жанры для фильтра — у каждой категории свои (`available-genres?category=`). */
    suspend fun genres(type: RatingType): Result<List<String>> {
        lock.withLock { genresCache[type] }?.let { return Result.success(it) }
        return artistApi.genres(type.category).map { it.genres }.onSuccess { g -> lock.withLock { genresCache[type] = g } }
    }

    private suspend fun djMag(): Result<DJMagAllResponse> {
        lock.withLock { djMagAll }?.let { return Result.success(it) }
        return artistApi.djMagAll().onSuccess { d -> lock.withLock { djMagAll = d } }
    }

    internal companion object {
        /** Итоги года — последние 3 сезона DJ Mag (итоги подводит бэкенд, пока не финализированы — пусто). */
        fun yearsForResults(djMagYears: List<Int>): List<Int> = djMagYears.sortedDescending().take(3)

        fun fromTop(list: List<RankedArtist>, talent: Boolean = false): List<RatingRow> = list.mapIndexed { i, a ->
            RatingRow(
                // Место — из API, по индексу только если бэкенд его не дал
                position = a.position ?: a.rank ?: (i + 1),
                spotifyArtistId = a.spotifyArtistId, name = a.name, imageUrl = a.imageUrl,
                genre = a.genres.firstOrNull(),
                score = if (talent) a.talentScore ?: a.djmetryScore else a.djmetryScore?.takeIf { it > 0 },
                change = a.positionChange?.takeIf { it != 0 }?.let { RatingChange.Places(it) },
                djMagRank = a.djMagRank, followers = a.followers, talent = talent,
            )
        }

        /** DJ Mag: сдвиг = место в прошлом году − место сейчас (поднялся — плюс); прошлый год даёт бэкенд. */
        fun fromDjMag(list: List<DJMagRanking>): List<RatingRow> = list.map { d ->
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
