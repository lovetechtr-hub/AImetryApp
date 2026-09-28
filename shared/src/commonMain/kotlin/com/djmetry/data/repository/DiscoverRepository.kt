package com.djmetry.data.repository

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

/** Подборки колоды: три категории трендов бэкенда + TOP 100. */
enum class DeckSource(val trendCategory: String?) {
    Rising("growing"),
    Breakthrough("breakthrough"),
    Stable("stable"),
    Top(null),
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
) {
    private val _follows = MutableStateFlow<List<FollowedArtist>>(emptyList())
    val follows: StateFlow<List<FollowedArtist>> = _follows.asStateFlow()

    private val _votes = MutableStateFlow<List<String>>(emptyList())
    val votes: StateFlow<List<String>> = _votes.asStateFlow()

    /** Загружает подборку и убирает артистов, на которых пользователь уже подписан. */
    suspend fun deck(source: DeckSource): Result<List<RankedArtist>> = coroutineScope {
        val artists = async {
            if (source.trendCategory != null) artistApi.trends(source.trendCategory, limit = 40).map { it.artists }
            else artistApi.topN(1).map { it.artists }
        }
        val followsLoaded = async { refreshMine() }
        followsLoaded.await()
        artists.await().map { list ->
            val followed = _follows.value.map { it.spotifyArtistId }.toSet()
            list.filterNot { it.spotifyArtistId in followed }
        }
    }

    /** Подписки и голоса пользователя. Без входа — просто пусто. */
    suspend fun refreshMine() = coroutineScope {
        val f = async { userApi.getFollows() }
        val v = async { userApi.getVotes() }
        f.await().onSuccess { _follows.value = it.follows }
        v.await().onSuccess { _votes.value = it.votes }
    }

    suspend fun follow(artist: RankedArtist): Result<Unit> = userApi.follow(artist.spotifyArtistId).map {
        _follows.update { current ->
            if (current.any { it.spotifyArtistId == artist.spotifyArtistId }) current
            else current + FollowedArtist(artist.spotifyArtistId, artist.name, artist.imageUrl)
        }
    }

    suspend fun unfollow(spotifyArtistId: String): Result<Unit> = userApi.unfollow(spotifyArtistId).map {
        _follows.update { list -> list.filterNot { it.spotifyArtistId == spotifyArtistId } }
    }

    /** Отдаёт голос; при 3 голосах возвращает [VoteLimitException], ничего не отправляя. */
    suspend fun vote(spotifyArtistId: String): Result<List<String>> {
        val current = _votes.value
        if (spotifyArtistId in current) return Result.success(current)
        if (current.size >= MAX_VOTES) return Result.failure(VoteLimitException(MAX_VOTES))
        return userApi.setVotes(current + spotifyArtistId).map { saved ->
            saved.votes.ifEmpty { current + spotifyArtistId }.also { _votes.value = it }
        }
    }

    suspend fun removeVote(spotifyArtistId: String): Result<List<String>> {
        val next = _votes.value - spotifyArtistId
        return userApi.setVotes(next).map { saved -> saved.votes.also { _votes.value = it } }
    }

    companion object {
        const val MAX_VOTES = 3
    }
}
