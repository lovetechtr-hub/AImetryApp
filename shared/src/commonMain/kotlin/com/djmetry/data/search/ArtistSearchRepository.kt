package com.djmetry.data.search

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.ArtistSearchItem

/** Поиск артистов — один вход для всех экранов (главный поиск, карта DJ), вместо прямых вызовов API из UI. */
class ArtistSearchRepository(private val api: ArtistApi) {
    suspend fun search(query: String, limit: Int = 20): Result<List<ArtistSearchItem>> {
        val q = query.trim()
        if (q.isEmpty()) return Result.success(emptyList())
        return api.search(q, limit).map { it.artists }
    }
}
