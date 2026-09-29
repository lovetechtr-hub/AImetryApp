package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.BreakdownResponse
import com.djmetry.api.models.GeoOptionsResponse
import com.djmetry.api.models.NetworkResponse
import com.djmetry.data.analytics.AnalyticsQuery
import com.djmetry.data.analytics.AnalyticsSource
import com.djmetry.data.analytics.params
import io.ktor.client.*
import io.ktor.client.request.*

/**
 * Аналитика (спека §15, docs/BACKEND_API.md → «Аналитика»). BIO: `/me/music-page/analytics/…`,
 * карточка DJMetry: `/me/artist-catalog/analytics/…` — одинаковые параметры и форма ответа.
 * 403 `no_music_page` / `catalog_analytics_unavailable` — нет страницы или не проверенный артист.
 */
class AnalyticsApi(private val http: HttpClient) {

    suspend fun network(source: AnalyticsSource, query: AnalyticsQuery): Result<NetworkResponse> = apiCall {
        http.get("${source.base}/${source.networkPath}") { query.params().forEach { (k, v) -> parameter(k, v) } }
    }

    suspend fun breakdown(source: AnalyticsSource, query: AnalyticsQuery): Result<BreakdownResponse> = apiCall {
        http.get("${source.base}/breakdown") { query.params().forEach { (k, v) -> parameter(k, v) } }
    }

    /** Страны и города, где были визиты за период — для фильтра. Геофильтр бэкенд здесь не читает. */
    suspend fun geoOptions(source: AnalyticsSource, query: AnalyticsQuery): Result<GeoOptionsResponse> = apiCall {
        http.get("${source.base}/geo-options") { query.params(withGeo = false).forEach { (k, v) -> parameter(k, v) } }
    }
}
