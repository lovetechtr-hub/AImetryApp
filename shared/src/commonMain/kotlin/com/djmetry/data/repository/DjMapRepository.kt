package com.djmetry.data.repository

import com.djmetry.api.endpoints.DjMapApi
import com.djmetry.api.models.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Данные карты диджеев. Считает и фильтрует бэкенд; здесь — кэш ответов на 120 с по полному запросу (как сайт,
 * бэкенд ограничивает 100 запросов в минуту на IP) и `/filters` на всю сессию.
 */
class DjMapRepository(private val api: DjMapApi, private val clock: () -> Instant = { Clock.System.now() }) {
    private val ttl = 120.seconds
    // Каждый сдвиг карты — новый bbox и новый ключ: просроченное выкидываем, свежее держим не больше MAX_ENTRIES
    private val cache = com.djmetry.data.cache.TtlCache<String, Any>(ttl, MAX_ENTRIES, clock)

    companion object {
        const val MAX_ENTRIES = 60
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> Result<T>): Result<T> =
        cache.getOrLoad(key) { load() } as Result<T>

    internal suspend fun cacheSize(): Int = cache.size()

    private fun key(path: String, params: List<Pair<String, String>>) = path + "?" + params.joinToString("&") { "${it.first}=${it.second}" }

    suspend fun performances(params: List<Pair<String, String>>) = cached(key("performances", params)) { api.performances(params) }
    suspend fun tour(id: String) = cached("tour/$id") { api.tour(id) }
    suspend fun summary(id: String) = cached("summary/$id") { api.summary(id) }
    suspend fun venues(params: List<Pair<String, String>>) = cached(key("venues", params)) { api.venues(params) }
    suspend fun venueArtists(id: String) = cached("venue-artists/$id") { api.venueArtists(id) }
    suspend fun density(params: List<Pair<String, String>>) = cached(key("density", params)) { api.density(params) }
    suspend fun topTouring(params: List<Pair<String, String>>) = cached(key("top-touring", params)) { api.topTouring(params) }
    suspend fun origins(genre: String?) = cached("origins/$genre") { api.origins(genre) }
    suspend fun topArtists(iso2: String, genre: String?) = cached("top-artists/$iso2/$genre") { api.topArtists(iso2, genre) }
    suspend fun originArtists(iso2: String, genre: String?) = cached("origin-artists/$iso2/$genre") { api.originArtists(iso2, genre) }
    suspend fun filters() = cached("filters") { api.filters() }
}
