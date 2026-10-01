package com.djmetry.data.search

import com.djmetry.data.local.SessionStorage
import com.djmetry.data.repository.UserScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Где искали. У каждого поиска своя история; новый поиск — новый элемент здесь (ключ — в хранилище,
 * менять нельзя). [withArtists] — показывать ли «Вы открывали».
 */
enum class SearchScope(val key: String, val withArtists: Boolean = false) {
    Artists("artists", withArtists = true),
    Radar("radar"),
    Releases("releases"),
    Map("map"),
    Country("country"),
    City("city"),
    Genre("genre"),
}

/** Недавно открытый из поиска артист. */
@Serializable
data class RecentArtist(val id: String, val name: String, val imageUrl: String? = null)

/** Вся история на устройстве: запросы по поискам и открытые артисты. */
@Serializable
data class SearchHistoryData(
    val queries: Map<String, List<String>> = emptyMap(),
    val artists: List<RecentArtist> = emptyList(),
)

/** Правила истории — чистые функции, без хранилища (тестируются отдельно). */
object SearchHistoryRules {
    const val MAX_QUERIES = 20
    const val MAX_ARTISTS = 12
    /** Короче — это ещё не запрос («а»), длиннее — не запрос, а вставленный текст. */
    const val MIN_QUERY = 2
    const val MAX_QUERY = 80

    /** Нормализованный запрос или null, если сохранять нечего. */
    fun normalize(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.length in MIN_QUERY..MAX_QUERY }

    /** Свежий запрос — первым; тот же без учёта регистра — поднимается, а не дублируется. */
    fun addQuery(list: List<String>, raw: String): List<String> {
        val q = normalize(raw) ?: return list
        return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_QUERIES)
    }

    fun addArtist(list: List<RecentArtist>, a: RecentArtist): List<RecentArtist> =
        (listOf(a) + list.filterNot { it.id == a.id }).take(MAX_ARTISTS)

    /** Подсказки из истории при вводе: начинается с набранного — выше, содержит — ниже. */
    fun matching(list: List<String>, typed: String): List<String> {
        val t = typed.trim()
        if (t.isEmpty()) return list
        val starts = list.filter { it.startsWith(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }
        val contains = list.filter { it !in starts && it.contains(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }
        return starts + contains
    }
}

/**
 * История поиска на устройстве — одна для всех поисков приложения (главный поиск, Радар, карта, страны, города).
 * Хранится в обычных настройках (`SessionStorage.setPref`), принадлежит вошедшему пользователю: при выходе
 * стирается ([UserScoped]).
 */
class SearchHistoryRepository(private val storage: SessionStorage) : UserScoped {
    private val json = Json { ignoreUnknownKeys = true }
    private val _data = MutableStateFlow(load())
    val data: StateFlow<SearchHistoryData> = _data.asStateFlow()

    fun queries(scope: SearchScope): List<String> = _data.value.queries[scope.key].orEmpty()

    fun record(scope: SearchScope, query: String) = edit { d ->
        d.copy(queries = d.queries + (scope.key to SearchHistoryRules.addQuery(d.queries[scope.key].orEmpty(), query)))
    }

    fun remove(scope: SearchScope, query: String) = edit { d ->
        d.copy(queries = d.queries + (scope.key to d.queries[scope.key].orEmpty().filterNot { it == query }))
    }

    fun recordArtist(artist: RecentArtist) = edit { it.copy(artists = SearchHistoryRules.addArtist(it.artists, artist)) }

    fun removeArtist(id: String) = edit { d -> d.copy(artists = d.artists.filterNot { it.id == id }) }

    /** «Очистить» в поиске: его запросы, а у главного поиска — и открытых артистов. */
    fun clear(scope: SearchScope) = edit { d ->
        d.copy(queries = d.queries - scope.key, artists = if (scope.withArtists) emptyList() else d.artists)
    }

    override suspend fun clearUserData() {
        _data.value = SearchHistoryData()
        storage.setPref(KEY, null)
    }

    private fun edit(change: (SearchHistoryData) -> SearchHistoryData) {
        _data.update(change)
        storage.setPref(KEY, json.encodeToString(SearchHistoryData.serializer(), _data.value))
    }

    private fun load(): SearchHistoryData =
        storage.getPref(KEY)?.let { runCatching { json.decodeFromString(SearchHistoryData.serializer(), it) }.getOrNull() } ?: SearchHistoryData()

    companion object {
        const val KEY = "search_history_v1"
    }
}
