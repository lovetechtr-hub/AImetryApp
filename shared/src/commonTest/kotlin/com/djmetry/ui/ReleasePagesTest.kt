package com.djmetry.ui

import com.djmetry.api.models.ArtistReleasesResponse
import com.djmetry.api.models.ReleaseRadarRelease
import com.djmetry.ui.radar.ReleasePages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class ReleasePagesTest {
    private fun page(offset: Int, size: Int = 24, total: Int = 100) =
        ArtistReleasesResponse((offset until minOf(offset + size, total)).map { ReleaseRadarRelease(album_id = "r$it", name = "R$it") }, total)

    @Test
    fun scrollAndNotificationSearchNeverAddSamePageTwice() = runTest {
        // Широкий экран: «почти конец» с первого кадра + поиск релиза из пуша — раньше оба брали offset 24
        val offsets = mutableListOf<Int>()
        val pages = ReleasePages { offset -> offsets += offset; yield(); Result.success(page(offset)) }
        pages.loadNext()
        listOf(async { pages.loadNext() }, async { pages.loadUntil("r40") }).awaitAll()
        assertEquals(pages.items.size, pages.items.map { it.album_id }.toSet().size, "дубли album_id роняют сетку")
        assertEquals(offsets.distinct(), offsets, "одна страница — один запрос")
        assertEquals(40, pages.items.indexOfFirst { it.album_id == "r40" })
    }

    @Test
    fun overlappingPageFromBackendIsDeduplicated() = runTest {
        val pages = ReleasePages { offset -> Result.success(page(maxOf(0, offset - 4))) }
        pages.loadNext(); pages.loadNext()
        assertEquals(44, pages.items.size)
        assertEquals(44, pages.items.map { it.album_id }.toSet().size)
    }

    @Test
    fun cancelledLoadDoesNotBlockNextOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val pages = ReleasePages { offset -> calls++; if (calls == 2) gate.await(); Result.success(page(offset)) }
        pages.loadNext()
        val job = launch { pages.loadNext() }
        yield()
        assertTrue(pages.loading)
        job.cancel(); job.join()
        assertFalse(pages.loading, "раньше флаг оставался true и догрузка вставала навсегда")
        assertTrue(pages.loadNext())
        assertEquals(48, pages.items.size)
    }

    @Test
    fun stopsAtTotalAndFailedFirstPageShowsEmpty() = runTest {
        val pages = ReleasePages { offset -> Result.success(page(offset, total = 30)) }
        pages.loadNext(); pages.loadNext()
        assertFalse(pages.loadNext())
        assertEquals(30, pages.items.size)

        val broken = ReleasePages { Result.failure(Exception("offline")) }
        broken.loadNext()
        assertEquals(0, broken.total, "без первой страницы — пусто, а не вечный скелетон")
    }

    @Test
    fun missingReleaseStopsAfterPageLimit() = runTest {
        var calls = 0
        val pages = ReleasePages { offset -> calls++; Result.success(page(offset, total = 10_000)) }
        assertNull(pages.loadUntil("nope", maxPages = 3))
        assertEquals(3, calls)
    }
}
