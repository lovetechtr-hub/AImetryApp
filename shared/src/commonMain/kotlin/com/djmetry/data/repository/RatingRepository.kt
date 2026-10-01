package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.DJMagAllResponse
import com.djmetry.api.models.DJMagRanking
import com.djmetry.api.models.RankedArtist
import kotlinx.coroutines.async
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
    /** Впервые в рейтинге (DJ Mag `"NEW"`). */
    data object New : RatingChange
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

/**
 * Страница рейтинга. [notFinalized] — итоги года (и прошлого) ещё не подведены;
 * [shownYear] — итоги выбранного года не подведены, показан прошлый (спека: фолбек на год−1).
 */
data class RatingPage(val rows: List<RatingRow>, val notFinalized: Boolean = false, val shownYear: Int? = null)

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
    /** Страница и время загрузки: Score меняется в течение дня — старше [CACHE_TTL_MS] грузим заново. */
    private val cache = mutableMapOf<RatingQuery, Pair<Long, RatingPage>>()
    private fun now() = kotlin.time.Clock.System.now().toEpochMilliseconds()
    private val limitsCache = mutableMapOf<RatingType, List<String>>()
    private val genresCache = mutableMapOf<RatingType, List<String>>()
    private var djMagAll: DJMagAllResponse? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
    /** Один запрос `/djmag/rankings/all` на всех: шкала и таблица открываются одновременно. */
    private var djMagLoading: kotlinx.coroutines.Deferred<Result<DJMagAllResponse>>? = null
    /** Данные артистов DJ Mag из `artists/batch` (фото, жанр, Score) — на сессию, по Spotify id. */
    private val djMagArtists = mutableMapOf<String, RankedArtist>()

    /** Сырой TOP 100 (как отдаёт бэкенд) — колоде «TOP 10» нужны полные данные артиста, а не строки таблицы. */
    private var topRaw: List<RankedArtist>? = null

    /**
     * TOP 100 для колоды: тот же запрос и кэш, что у рейтинга и панели TOP 10 (раньше колода качала его третий раз).
     */
    suspend fun topArtists(): Result<List<RankedArtist>> =
        load(RatingQuery()).mapCatching { lock.withLock { topRaw } ?: load(RatingQuery(), refresh = true).getOrThrow().let { lock.withLock { topRaw }.orEmpty() } }

    /** TOP 100 без фильтров — для боковой панели колоды. */
    suspend fun top100(): Result<List<RatingRow>> = load(RatingQuery()).map { it.rows }

    /** Одинаковые запросы, идущие одновременно (рейтинг + TOP 10 колоды), — один сетевой. */
    private val inflight = mutableMapOf<RatingQuery, kotlinx.coroutines.Deferred<Result<RatingPage>>>()

    suspend fun load(query: RatingQuery, refresh: Boolean = false): Result<RatingPage> {
        if (!refresh) lock.withLock { cache[query]?.takeIf { now() - it.first < CACHE_TTL_MS }?.second }?.let { return Result.success(it) }
        // Запись убирает сама загрузка, когда закончится, — даже если тот, кто её начал, ушёл с экрана
        val job = lock.withLock {
            inflight[query]?.takeIf { !refresh } ?: run {
                lateinit var d: kotlinx.coroutines.Deferred<Result<RatingPage>>
                d = scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try { fetch(query) } finally {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { lock.withLock { if (inflight[query] === d) inflight.remove(query) } }
                    }
                }
                inflight[query] = d
                d.also { it.start() }
            }
        }
        return job.await()
    }

    private suspend fun fetch(query: RatingQuery): Result<RatingPage> {
        val q = if (query.type.byScore) query else query.copy(genre = null, country = null)
        val result: Result<RatingPage> = when (val r = q.range) {
            is RatingRange.Top -> artistApi.topN(r.to / 100, q.type.category, q.genre, q.country).map { resp ->
                if (q == RatingQuery()) lock.withLock { topRaw = resp.artists }
                RatingPage(fromTop(resp.artists))
            }
            RatingRange.Talents -> artistApi.talentsRanking(200, q.type.category, q.genre, q.country).map { RatingPage(fromTop(it.artists, talent = true)) }
            is RatingRange.Year -> if (q.type == RatingType.DJMag) djMag().map { d ->
                val list = d.rankings[r.year.toString()].orEmpty()
                RatingPage(enrich(fromDjMag(list, djMagPhotos(d)), artistsFor(list.mapNotNull { it.spotifyArtistId })))
            }
            else yearResults(r.year)
        }
        result.onSuccess { page -> lock.withLock { cache[query] = now() to page } }
        return result
    }

    /** Итоги года; не подведены — пробуем прошлый год (как на сайте), не подведён и он — «ещё не подведены». */
    private suspend fun yearResults(year: Int): Result<RatingPage> {
        val y = artistApi.yearRanking(year).getOrElse { return Result.failure(it) }
        if (y.finalized) return Result.success(RatingPage(fromTop(y.rankings)))
        val prev = artistApi.yearRanking(year - 1).getOrNull()
        return Result.success(
            if (prev?.finalized == true) RatingPage(fromTop(prev.rankings), shownYear = year - 1)
            else RatingPage(emptyList(), notFinalized = true)
        )
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

    private suspend fun djMag(): Result<DJMagAllResponse> = kotlinx.coroutines.coroutineScope {
        val job = lock.withLock {
            djMagAll?.let { return@coroutineScope Result.success(it) }
            djMagLoading ?: scope.async {
                // Итог записывает сама загрузка: отмена вызывающего не оставит в кэше завершённый Deferred с ошибкой
                artistApi.djMagAll().also { r -> lock.withLock { r.onSuccess { djMagAll = it }; djMagLoading = null } }
            }.also { djMagLoading = it }
        }
        job.await()
    }

    /**
     * Фото и жанры DJ Mag теперь отдаёт сам бэкенд; `POST artists/batch` остаётся только ради Score DJMetry
     * в таблице (его в ответе DJ Mag нет). Не удалось (лимит, сеть) — таблица остаётся как есть, без ошибки.
     */
    private suspend fun artistsFor(ids: List<String>): Map<String, RankedArtist> {
        val missing = lock.withLock { ids.filter { it !in djMagArtists } }.distinct()
        if (missing.isNotEmpty()) artistApi.batch(missing).onSuccess { b -> lock.withLock { b.artists.forEach { djMagArtists[it.spotifyArtistId] = it } } }
        return lock.withLock { ids.mapNotNull { id -> djMagArtists[id]?.let { id to it } }.toMap() }
    }

    internal companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L

        /** Итоги года — последние 3 сезона DJ Mag (итоги подводит бэкенд, пока не финализированы — пусто). */
        fun yearsForResults(djMagYears: List<Int>): List<Int> = djMagYears.sortedDescending().take(3)

        /** Дополнить строки DJ Mag данными артистов DJMetry: фото (если нет), жанр, Score. */
        fun enrich(rows: List<RatingRow>, artists: Map<String, RankedArtist>): List<RatingRow> = rows.map { r ->
            val a = r.spotifyArtistId?.let(artists::get) ?: return@map r
            r.copy(
                imageUrl = r.imageUrl ?: a.imageUrl?.takeIf { it.isNotBlank() },
                genre = r.genre ?: a.genres.firstOrNull(),
                score = r.score ?: a.djmetryScore?.takeIf { it > 0 },
            )
        }

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

        /**
         * Фото артистов из всех лет DJ Mag: бэкенд отдаёт `imageUrl` только за 2020 и 2025 (остальные годы — null),
         * а артисты те же — берём самое свежее фото по Spotify id.
         */
        fun djMagPhotos(all: DJMagAllResponse): Map<String, String> = buildMap {
            all.rankings.entries.sortedBy { it.key }.forEach { (_, list) ->
                list.forEach { d -> val id = d.spotifyArtistId; val img = d.imageUrl; if (id != null && !img.isNullOrBlank()) put(id, img) }
            }
        }

        /** DJ Mag: сдвиг = место в прошлом году − место сейчас (поднялся — плюс); прошлый год даёт бэкенд. */
        fun fromDjMag(list: List<DJMagRanking>, photos: Map<String, String> = emptyMap()): List<RatingRow> = list.map { d ->
            RatingRow(
                position = d.rank,
                spotifyArtistId = d.spotifyArtistId, name = d.name, imageUrl = d.imageUrl ?: d.spotifyArtistId?.let(photos::get),
                genre = d.genres.firstOrNull(), score = null,
                change = when {
                    d.isNew == true || d.previousYearRank == com.djmetry.api.models.DJMAG_NEW -> RatingChange.New
                    else -> d.previousYearRank?.let { prev -> (prev - d.rank).takeIf { it != 0 }?.let { RatingChange.Places(it) } }
                },
                djMagRank = d.rank, followers = null,
            )
        }
    }
}
