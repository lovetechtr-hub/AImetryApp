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
import kotlin.time.Duration.Companion.minutes

/** Данные экрана: сеть визитов (итоги, сегменты, клики) и разбивка (страны, источники, устройства). */
private val CACHE_TTL = 10.minutes

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
    // Статистика растёт в течение дня: 10 минут — и перечитываем; ключей (период × фильтры) не больше 40
    private val cache = com.djmetry.data.cache.TtlCache<Pair<AnalyticsSource, AnalyticsQuery>, AnalyticsReport>(CACHE_TTL, 40)
    private val geoCache = com.djmetry.data.cache.TtlCache<Pair<AnalyticsSource, AnalyticsQuery>, GeoOptionsResponse>(CACHE_TTL, 40)

    override suspend fun clearUserData() { cache.clear(); geoCache.clear() }

    suspend fun load(source: AnalyticsSource, query: AnalyticsQuery, refresh: Boolean = false): Result<AnalyticsReport> =
        cache.getOrLoad(source to query, refresh) {
            coroutineScope {
                val network = async { api.network(source, query) }
                val breakdown = async { api.breakdown(source, query) }
                // Основной упал — разбивку не ждём, отменяем
                val n = network.await().getOrElse { breakdown.cancel(); return@coroutineScope Result.failure(blocked(it) ?: it) }
                // Разбивка не пришла — отчёт не кэшируем, иначе страны и устройства не появятся до выхода
                Result.success(AnalyticsReport(n, breakdown.await().getOrNull()))
            }
        }.also { r -> if (r.getOrNull()?.breakdown == null) cache.remove(source to query) }

    /** Варианты гео-фильтра за период (без страны и города в ключе — они сюда не влияют). */
    suspend fun geoOptions(source: AnalyticsSource, query: AnalyticsQuery): Result<GeoOptionsResponse> {
        val key = source to query.copy(country = null, city = null)
        return geoCache.getOrLoad(key) { api.geoOptions(source, key.second) }
    }

    private fun blocked(e: Throwable): AnalyticsBlockedException? = (e as? ApiException)?.takeIf { it.status == 403 }?.let {
        when (it.code) {
            "no_music_page" -> AnalyticsBlockedException(AnalyticsBlock.NoMusicPage)
            "catalog_analytics_unavailable" -> AnalyticsBlockedException(AnalyticsBlock.NotVerified)
            else -> null
        }
    }
}
