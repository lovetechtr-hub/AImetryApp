package com.djmetry.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djmetry.api.models.ArtistSearchItem
import com.djmetry.data.search.ArtistSearchRepository
import com.djmetry.data.search.RecentArtist
import com.djmetry.data.search.SearchHistoryRepository
import com.djmetry.data.search.SearchScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Что показывает главный поиск. `results == null` — идёт загрузка. */
data class SearchUiState(
    val query: String = "",
    val results: List<ArtistSearchItem>? = emptyList(),
    val failed: Boolean = false,
)

/**
 * Главный поиск артистов. Состояние живёт здесь, а не в экране: запрос и результаты переживают смену вкладки,
 * поворот и пересоздание экрана. Ввод — с паузой [DEBOUNCE_MS]; в историю попадает запрос, по которому открыли
 * артиста (а не каждая набранная буква), и сам артист — в «Вы открывали».
 */
class SearchViewModel(
    private val search: ArtistSearchRepository,
    private val history: SearchHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQuery(text: String) {
        _state.update { it.copy(query = text) }
        load(debounce = true)
    }

    fun retry() = load(debounce = false)

    /** Запрос из истории — сразу в поле и в поиск, без паузы. */
    fun useQuery(text: String) {
        _state.update { it.copy(query = text) }
        load(debounce = false)
    }

    /** Нажали «Найти» на клавиатуре. */
    fun submit() = history.record(SearchScope.Artists, _state.value.query)

    /** Открыли артиста из результатов: запрос и артист — в историю. */
    fun onOpened(artist: ArtistSearchItem) {
        history.record(SearchScope.Artists, _state.value.query)
        history.recordArtist(RecentArtist(artist.spotifyArtistId, artist.name, artist.imageUrl))
    }

    private fun load(debounce: Boolean) {
        job?.cancel()
        val q = _state.value.query
        if (q.isBlank()) { _state.update { it.copy(results = emptyList(), failed = false) }; return }
        job = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            _state.update { it.copy(results = null, failed = false) }
            search.search(q).fold(
                // Ошибка сети — не «ничего не найдено», а «не удалось» с «Повторить»
                { list -> _state.update { it.copy(results = list) } },
                { _state.update { it.copy(results = emptyList(), failed = true) } },
            )
        }
    }

    companion object {
        const val DEBOUNCE_MS = 350L
    }
}
