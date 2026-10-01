package com.djmetry.ui.radar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.djmetry.api.models.ArtistReleasesResponse
import com.djmetry.api.models.ReleaseRadarRelease
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Страницы «Всех релизов». Один загрузчик на экран: догрузка при прокрутке и поиск релиза из уведомления
 * идут через него по очереди — одна и та же страница не добавится дважды (дубли `album_id` роняют сетку).
 */
internal class ReleasePages(private val load: suspend (offset: Int) -> Result<ArtistReleasesResponse>) {
    val items = mutableStateListOf<ReleaseRadarRelease>()
    /** Сколько всего у артиста; null — первая страница ещё не пришла. */
    var total by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private val lock = Mutex()

    val hasMore: Boolean get() = total.let { it == null || items.size < it }

    /** Следующая страница; false — дальше нет или ошибка. Отмена посреди загрузки не оставляет «вечную загрузку». */
    suspend fun loadNext(): Boolean = lock.withLock {
        if (!hasMore) return@withLock false
        loading = true
        try {
            load(items.size).fold(
                onSuccess = { page ->
                    val known = items.mapTo(HashSet()) { it.album_id }
                    items.addAll(page.releases.filter { known.add(it.album_id) })
                    total = page.total
                    page.releases.isNotEmpty()
                },
                // Первая страница не пришла — показываем пусто, а не вечный скелетон
                onFailure = { if (total == null) total = 0; false },
            )
        } finally {
            loading = false
        }
    }

    /** Догружать, пока релиза [albumId] нет в списке (не больше [maxPages] страниц). */
    suspend fun loadUntil(albumId: String, maxPages: Int = 10): Int? {
        var pages = 0
        while (items.none { it.album_id == albumId } && pages++ < maxPages && loadNext()) Unit
        return items.indexOfFirst { it.album_id == albumId }.takeIf { it >= 0 }
    }
}
