package com.djmetry.data.repository

import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AnalyticsApi
import com.djmetry.api.models.BreakdownResponse
import com.djmetry.api.models.GeoOptionsResponse
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.NetworkResponse
import com.djmetry.data.analytics.AnalyticsQuery
import com.djmetry.data.analytics.AnalyticsSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Данные экрана: сеть визитов (итоги, сегменты, клики) и разбивка (страны, источники, устройства). */
data class AnalyticsReport(val network: NetworkResponse, val breakdown: BreakdownResponse?)

/** Что показать вместо данных. */
enum class AnalyticsBlock {
    /** 403 `no_music_page` — BIO-страницы ещё нет: создать на сайте. */
    NoMusicPage,
    /** 403 `catalog_analytics_unavailable` — карточка доступна только проверенному артисту. */
    NotVerified,
}

class AnalyticsBlockedException(val block: AnalyticsBlock) : Exception(block.name)

/** Источники, доступные аккаунту: карточка DJMetry — только проверенному артисту (по умолчанию открывается она, как на сайте). */
fun availableSources(me: MeResponse): List<AnalyticsSource> =
    if (verifiedArtistId(me) != null) listOf(AnalyticsSource.DJMetry, AnalyticsSource.Bio) else listOf(AnalyticsSource.Bio)

/**
 * Аналитика BIO-страницы и карточки артиста. Считает всё бэкенд; здесь — параллельная загрузка, кэш по запросу
 * и перевод 403 в понятное состояние. Разбивка необязательна: упала — экран показывает итоги без стран и устройств.
 */
class AnalyticsRepository(private val api: AnalyticsApi) : UserScoped {
    private val lock = Mutex()
    private val cache = mutableMapOf<Pair<AnalyticsSource, AnalyticsQuery>, AnalyticsReport>()
    private val geoCache = mutableMapOf<Pair<AnalyticsSource, AnalyticsQuery>, GeoOptionsResponse>()

    override suspend fun clearUserData() = lock.withLock { cache.clear(); geoCache.clear() }

    suspend fun load(source: AnalyticsSource, query: AnalyticsQuery, refresh: Boolean = false): Result<AnalyticsReport> {
        val key = source to query
        if (!refresh) lock.withLock { cache[key] }?.let { return Result.success(it) }
        return coroutineScope {
            val network = async { api.network(source, query) }
            val breakdown = async { api.breakdown(source, query) }
            val n = network.await().getOrElse { return@coroutineScope Result.failure(blocked(it) ?: it) }
            val report = AnalyticsReport(n, breakdown.await().getOrNull())
            lock.withLock { cache[key] = report }
            Result.success(report)
        }
    }

    /** Варианты гео-фильтра за период (без страны и города в ключе — они сюда не влияют). */
    suspend fun geoOptions(source: AnalyticsSource, query: AnalyticsQuery): Result<GeoOptionsResponse> {
        val key = source to query.copy(country = null, city = null)
        lock.withLock { geoCache[key] }?.let { return Result.success(it) }
        return api.geoOptions(source, key.second).onSuccess { g -> lock.withLock { geoCache[key] = g } }
    }

    private fun blocked(e: Throwable): AnalyticsBlockedException? = (e as? ApiException)?.takeIf { it.status == 403 }?.let {
        when (it.code) {
            "no_music_page" -> AnalyticsBlockedException(AnalyticsBlock.NoMusicPage)
            "catalog_analytics_unavailable" -> AnalyticsBlockedException(AnalyticsBlock.NotVerified)
            else -> null
        }
    }
}
