package com.djmetry.data.repository

import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AudienceApi
import com.djmetry.api.models.*
import com.djmetry.data.analytics.AudienceScope
import com.djmetry.data.analytics.FanSegment
import com.djmetry.data.analytics.LeadSource
import com.djmetry.data.analytics.withFanSegment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Сводка выбранного сегмента: всего людей, воронка по сегментам фанов, страны и платформы. */
data class AudienceOverview(
    val total: Int,
    val funnel: Map<FanSegment, Int>,
    val countries: List<AudienceCountryCount>,
    val platforms: List<AudiencePlatformCount>,
)

/** Почему аудитории нет. */
enum class AudienceBlock {
    /** 401: бэкенд пока не принимает токен приложения в `/audience/…` — открыть на сайте. */
    WebOnly,
    /** 403 или 400 `spotify_artist_id_required`: нет карточки артиста и BIO-страницы. */
    NoAccess,
}

class AudienceBlockedException(val block: AudienceBlock) : Exception(block.name)

/** Чья аудитория у аккаунта: проверенный артист — его карточка, иначе своя BIO-страница. */
fun audienceScope(me: MeResponse): AudienceScope =
    verifiedArtistId(me)?.let { AudienceScope.Artist(it) } ?: AudienceScope.BioOwner

/**
 * Аудитория: сегменты, воронка фанов, люди, лиды и экспорт CSV. Всё считает бэкенд. Отдельного счётчика по
 * сегментам фанов у него нет — берём `total` пяти превью с фильтром `fan_segment` (page_size = 1), параллельно.
 */
class AudienceRepository(private val api: AudienceApi) : UserScoped {
    private val lock = Mutex()
    private val overviews = mutableMapOf<Pair<AudienceScope, JsonElement>, AudienceOverview>()

    override suspend fun clearUserData() = lock.withLock { overviews.clear(); catalogs.clear() }

    suspend fun segments(scope: AudienceScope): Result<List<AudienceSegment>> =
        api.segments(scope).map { it.segments }.mapBlocked()

    suspend fun overview(scope: AudienceScope, filters: JsonElement = JsonArray(emptyList()), refresh: Boolean = false): Result<AudienceOverview> {
        val key = scope to filters
        if (!refresh) lock.withLock { overviews[key] }?.let { return Result.success(it) }
        return coroutineScope {
            val main = api.preview(scope, filters, page = 1, pageSize = 1).getOrElse { return@coroutineScope Result.failure(blocked(it) ?: it) }
            // Воронка — из того же ответа (`stats.fan_segments`); старый бэкенд — по запросу на сегмент
            val funnel = main.stats.fan_segments?.let { list ->
                FanSegment.entries.associateWith { fan -> list.firstOrNull { it.segment == fan.key }?.count ?: 0 }
            } ?: FanSegment.entries.map { fan -> async { fan to api.preview(scope, withFanSegment(filters, fan), 1, 1) } }
                .awaitAll().associate { (fan, r) -> fan to (r.getOrNull()?.total ?: 0) }
            val o = AudienceOverview(main.total, funnel, main.stats.countries_top, main.stats.platforms)
            lock.withLock { overviews[key] = o }
            Result.success(o)
        }
    }

    /** Каталог полей конструктора: с бэкенда, при 404/501/ошибке — офлайн-каталог (как сайт). Кэш на область. */
    private val catalogs = mutableMapOf<AudienceScope, List<com.djmetry.data.analytics.FilterField>>()

    suspend fun filterCatalog(scope: AudienceScope): List<com.djmetry.data.analytics.FilterField> {
        lock.withLock { catalogs[scope] }?.let { return it }
        val r = api.filterCatalog(scope)
        val list = com.djmetry.data.analytics.catalogFromApi(r.getOrNull())
        // Офлайн-каталог из-за сбоя сети не запоминаем: появится сеть — придёт настоящий. 404/501 — эндпоинта нет, запоминаем
        val absent = (r.exceptionOrNull() as? com.djmetry.api.ApiException)?.status in setOf(404, 501)
        if (r.isSuccess || absent) lock.withLock { catalogs[scope] = list }
        return list
    }

    /** Новый сегмент из черновика; пустое имя — без запроса. Пресеты так не создаются — только свои. */
    suspend fun createSegment(scope: AudienceScope, rawName: String, filters: JsonElement): Result<AudienceSegment> {
        val name = com.djmetry.data.analytics.normalizeSegmentName(rawName) ?: return Result.failure(IllegalArgumentException("empty_name"))
        return api.createSegment(scope, name, filters).map { it.segment }.mapBlocked()
    }

    /** Удалить свой сегмент; 404 — уже удалён, тоже успех. */
    suspend fun deleteSegment(id: String): Result<Unit> = api.deleteSegment(id).let { r ->
        val e = r.exceptionOrNull()
        if (e is ApiException && e.status == 404) Result.success(Unit) else r
    }

    /** Люди сегмента (по убыванию fan score — так сортирует бэкенд); [fan] — только эта плитка воронки. */
    suspend fun people(scope: AudienceScope, filters: JsonElement, fan: FanSegment?, page: Int, pageSize: Int = PAGE_SIZE): Result<AudiencePreviewResponse> =
        api.preview(scope, withFanSegment(filters, fan), page, pageSize).mapBlocked()

    suspend fun leads(source: LeadSource, page: Int = 1, pageSize: Int = PAGE_SIZE): Result<AudienceLeadsResponse> =
        api.leads(source.key, page, pageSize).mapBlocked()

    /** CSV сегмента: создаём задание и ждём готовности (409 — ещё считается). */
    suspend fun exportCsv(scope: AudienceScope, filters: JsonElement, attempts: Int = 10, pause: Long = 1000): Result<String> {
        val job = api.startExport(scope, filters).getOrElse { return Result.failure(blocked(it) ?: it) }
        repeat(attempts) { i ->
            val r = api.downloadExport(job.id)
            val e = r.exceptionOrNull()
            if (e !is ApiException || e.status != 409) return r
            if (i < attempts - 1) delay(pause)
        }
        return Result.failure(ApiException(409, "export_not_ready", null))
    }

    private fun <T> Result<T>.mapBlocked(): Result<T> = exceptionOrNull()?.let { e -> Result.failure(blocked(e) ?: e) } ?: this

    private fun blocked(e: Throwable): AudienceBlockedException? = (e as? ApiException)?.let {
        when {
            it.status == 401 -> AudienceBlockedException(AudienceBlock.WebOnly)
            it.status == 403 || it.code == "spotify_artist_id_required" -> AudienceBlockedException(AudienceBlock.NoAccess)
            else -> null
        }
    }

    companion object {
        const val PAGE_SIZE = 25
    }
}
