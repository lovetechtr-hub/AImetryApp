package com.djmetry.search

import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.models.ArtistSearchItem
import com.djmetry.data.search.ArtistSearchRepository
import com.djmetry.data.search.RecentArtist
import com.djmetry.data.search.SearchHistoryRepository
import com.djmetry.data.search.SearchHistoryRules
import com.djmetry.data.search.SearchScope
import com.djmetry.ui.search.SearchViewModel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/** История поиска (design/search/history-variants.html, вариант A) и архитектура: Koin, ViewModel. */
class SearchHistoryTest {
    @Test
    fun rulesKeepFreshFirstWithoutDuplicates() {
        var list = emptyList<String>()
        list = SearchHistoryRules.addQuery(list, "anyma")
        list = SearchHistoryRules.addQuery(list, "  Fisher ")
        list = SearchHistoryRules.addQuery(list, "ANYMA")
        assertEquals(listOf("ANYMA", "Fisher"), list, "повтор поднимается наверх, а не дублируется")
        assertEquals(list, SearchHistoryRules.addQuery(list, "a"), "одна буква — не запрос")
        assertEquals("melodic techno", SearchHistoryRules.normalize("melodic   techno "))
        repeat(30) { list = SearchHistoryRules.addQuery(list, "q$it") }
        assertEquals(SearchHistoryRules.MAX_QUERIES, list.size)
        assertEquals("q29", list.first())
    }

    @Test
    fun matchingPutsPrefixFirst() {
        val list = listOf("techno berlin", "anyma", "melodic techno", "Tech House")
        assertEquals(listOf("techno berlin", "Tech House", "melodic techno"), SearchHistoryRules.matching(list, "tech"))
        assertEquals(list, SearchHistoryRules.matching(list, " "))
    }

    @Test
    fun repositoryPersistsPerScopeAndClears() = runTest {
        val storage = FakeSessionStorage()
        val repo = SearchHistoryRepository(storage)
        repo.record(SearchScope.Artists, "anyma")
        repo.record(SearchScope.Radar, "fisher")
        repo.recordArtist(RecentArtist("a1", "Anyma"))
        // Новый экземпляр (перезапуск приложения) читает то же
        val again = SearchHistoryRepository(storage)
        assertEquals(listOf("anyma"), again.queries(SearchScope.Artists))
        assertEquals(listOf("fisher"), again.queries(SearchScope.Radar))
        assertEquals("Anyma", again.data.value.artists.single().name)

        again.clear(SearchScope.Artists)
        assertTrue(again.queries(SearchScope.Artists).isEmpty())
        assertTrue(again.data.value.artists.isEmpty(), "«Очистить» в главном поиске — и «Вы открывали»")
        assertEquals(listOf("fisher"), again.queries(SearchScope.Radar), "другие поиски не трогаем")

        again.clearUserData()
        assertNull(storage.getPref(SearchHistoryRepository.KEY), "выход из аккаунта стирает историю")
    }

    @Test
    fun brokenStoredHistoryStartsEmpty() {
        val storage = FakeSessionStorage().apply { setPref(SearchHistoryRepository.KEY, "{broken") }
        assertTrue(SearchHistoryRepository(storage).data.value.queries.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun viewModelDebouncesAndRecordsOnOpen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val backend = FakeBackend(mapOf("GET /api/artists/search" to (HttpStatusCode.OK to """{"artists":[{"spotifyArtistId":"a1","name":"Anyma"}]}""")))
            val history = SearchHistoryRepository(FakeSessionStorage())
            val vm = SearchViewModel(ArtistSearchRepository(ArtistApi(backend.client())), history)
            vm.onQuery("a"); vm.onQuery("an"); vm.onQuery("anyma")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 10)
            assertTrue(backend.requests.isEmpty(), "пока печатают — не ищем")
            var tries = 0
            while (vm.state.value.results.isNullOrEmpty() && tries++ < 200) { advanceTimeBy(20); kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(5) } }
            assertEquals(1, backend.requests.size, "один запрос на последнюю строку")
            assertEquals("Anyma", vm.state.value.results!!.single().name)
            assertTrue(history.queries(SearchScope.Artists).isEmpty(), "набранное без открытия — не история")

            vm.onOpened(ArtistSearchItem(spotifyArtistId = "a1", name = "Anyma"))
            assertEquals(listOf("anyma"), history.queries(SearchScope.Artists))
            assertEquals("a1", history.data.value.artists.single().id)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun koinGraphResolvesAndViewModelsClearOnLogout() = runTest {
        val c = AppContainer(FakeSessionStorage(), FakeBackend(emptyMap()).engine)
        // Граф собирается: всё, что объявлено в appModule, создаётся
        assertNotNull(c.koin.koin.get<ArtistSearchRepository>())
        assertNotNull(c.koin.koin.get<SearchViewModel>())
        assertSame(c.searchHistory, c.koin.koin.get<SearchHistoryRepository>(), "история — одна на приложение")
        assertTrue(c.userScoped().contains(c.searchHistory), "история стирается при выходе")
        c.searchHistory.record(SearchScope.Artists, "anyma")
        c.userScoped().forEach { it.clearUserData() }
        assertTrue(c.searchHistory.queries(SearchScope.Artists).isEmpty())
    }
}
