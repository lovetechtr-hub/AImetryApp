package com.djmetry.data.repository

import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.FollowedArtist
import com.djmetry.api.models.RankedArtist
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Подборки колоды в порядке меню: TOP 10 рейтинга и четыре категории трендов бэкенда
 * (`GET /artists/trends?category=…&sortBy=…`, как на сайте). У «Теряют импульс» бэкенд сам ставит падение сверху.
 */
enum class DeckSource(val trendCategory: String?, val sortBy: String? = null) {
    Top(null),
    Rising("growing", "score24h"),
    Breakthrough("breakthrough", "score24h"),
    Stable("stable"),
    Losing("losing_momentum", "score3d"),
}

/** Сколько карточек в TOP-подборке колоды. */
const val DECK_TOP_SIZE = 10

/** Сортировка «Подписок». */
enum class FollowSort { Recent, Name, Popular }

/** Недавние — порядок бэкенда (новые сверху); по имени — без учёта регистра; по популярности — подписчики Spotify. */
fun sortFollows(list: List<FollowedArtist>, sort: FollowSort): List<FollowedArtist> = when (sort) {
    FollowSort.Recent -> list
    FollowSort.Name -> list.sortedBy { it.name.orEmpty().lowercase() }
    FollowSort.Popular -> list.sortedByDescending { it.followers ?: -1L }
}

/** Голосов уже максимум — надо снять один, чтобы отдать новый. */
class VoteLimitException(val max: Int) : Exception("Vote limit $max reached")

/**
 * Колода «Открытия»: карточки артистов, подписка (свайп вправо), голос (вверх), пропуск (влево).
 * Держит подписки и голоса пользователя, чтобы не показывать уже отмеченных артистов.
 */
class DiscoverRepository(
    private val artistApi: ArtistApi,
    private val userApi: UserApi,
    /** TOP 100 из общего кэша рейтинга; без него — свой запрос. */
    private val topArtists: (suspend () -> Result<List<RankedArtist>>)? = null,
) {
    private val _follows = MutableStateFlow<List<FollowedArtist>>(emptyList())
    val follows: StateFlow<List<FollowedArtist>> = _follows.asStateFlow()

    private val _votes = MutableStateFlow<List<String>>(emptyList())
    val votes: StateFlow<List<String>> = _votes.asStateFlow()

    /** Загружает подборку и убирает артистов, на которых пользователь уже подписан. */
    suspend fun deck(source: DeckSource): Result<List<RankedArtist>> = coroutineScope {
        val artists = async {
            if (source.trendCategory != null) artistApi.trends(source.trendCategory, limit = 40, sortBy = source.sortBy).map { it.artists }
            else (topArtists?.invoke() ?: artistApi.topN(1).map { it.artists }).map { it.take(DECK_TOP_SIZE) }
        }
        val followsLoaded = async { refreshMine() }
        followsLoaded.await()
        artists.await().map { list ->
            val followed = _follows.value.map { it.spotifyArtistId }.toSet()
            list.filterNot { it.spotifyArtistId in followed }
        }
    }

    private var mineAt = 0L

    /**
     * Подписки и голоса пользователя. Без входа — просто пусто. Их зовут колода, Радар, профиль, карточка артиста —
     * чаще раза в [MINE_TTL_MS] не перезапрашиваем (свои подписки/голоса меняем локально сразу), [force] — принудительно.
     */
    suspend fun refreshMine(force: Boolean = false): Unit = coroutineScope {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (!force && now - mineAt < MINE_TTL_MS) return@coroutineScope
        val f = async { userApi.getFollows() }
        val v = async { userApi.getVotes() }
        f.await().onSuccess { _follows.value = it.follows; mineAt = now }
        v.await().onSuccess { _votes.value = it.votes }
    }

    suspend fun follow(artist: RankedArtist): Result<Unit> = follow(artist.spotifyArtistId, artist.name, artist.imageUrl)

    /** Подписка из карточки артиста, поиска и т.д. — где нет [RankedArtist]. */
    suspend fun follow(spotifyArtistId: String, name: String, imageUrl: String?): Result<Unit> = userApi.follow(spotifyArtistId).map {
        _follows.update { current ->
            if (current.any { it.spotifyArtistId == spotifyArtistId }) current
            else current + FollowedArtist(spotifyArtistId, name, imageUrl)
        }
    }

    suspend fun unfollow(spotifyArtistId: String): Result<Unit> = userApi.unfollow(spotifyArtistId).map {
        _follows.update { list -> list.filterNot { it.spotifyArtistId == spotifyArtistId } }
    }

    /**
     * Отдаёт голос; при 3 голосах возвращает [VoteLimitException], ничего не отправляя.
     * Бэкенд принимает голос только за артиста из подписок (`not_following`) — поэтому сначала подписываемся.
     */
    suspend fun vote(spotifyArtistId: String, name: String = "", imageUrl: String? = null): Result<List<String>> {
        val current = _votes.value
        if (spotifyArtistId in current) return Result.success(current)
        if (current.size >= MAX_VOTES) return Result.failure(VoteLimitException(MAX_VOTES))
        if (_follows.value.none { it.spotifyArtistId == spotifyArtistId }) {
            follow(spotifyArtistId, name, imageUrl).onFailure { return Result.failure(it) }
        }
        return userApi.setVotes(current + spotifyArtistId)
            .map { saved -> saved.votes.ifEmpty { current + spotifyArtistId }.also { _votes.value = it } }
            .recoverCatching { e -> throw if (e is ApiException && e.code == "too_many_votes") VoteLimitException(MAX_VOTES) else e }
    }

    suspend fun removeVote(spotifyArtistId: String): Result<List<String>> {
        val next = _votes.value - spotifyArtistId
        return userApi.setVotes(next).map { saved -> saved.votes.also { _votes.value = it } }
    }

    companion object {
        const val MAX_VOTES = 3
        const val MINE_TTL_MS = 30_000L
    }
}
