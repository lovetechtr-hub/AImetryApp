package com.djmetry.api.models

import kotlinx.serialization.Serializable

/**
 * GET /api/me. Без сессии бэкенд отвечает 200 { isAuthed: false }, а не 401.
 * Внимание: [spotifyUserId] — внутренний userId, не Spotify id.
 */
@Serializable
data class MeResponse(
    val isAuthed: Boolean = false,
    val userId: String? = null,
    val spotifyUserId: String? = null,
    val isAdmin: Boolean = false,
    val selectedArtistId: String? = null,
    val collection: List<ManagedArtist> = emptyList(),
    val selectedArtist: SelectedArtist? = null,
    val artistVerification: ArtistVerification? = null,
    val user: UserProfile? = null,
    val stats: UserStats? = null,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class UserProfile(
    val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val authProvider: String? = null,
    val preferredLanguage: String? = null,
    val country: String? = null,
    val city: String? = null,
    val region: String? = null,
    val birthDate: String? = null,
    @kotlinx.serialization.json.JsonNames("musicGenrePreferences") val music_genre_preferences: List<String>? = null,
)

@Serializable
data class UserStats(
    val followsCount: Int = 0,
    val votesCount: Int = 0,
    val maxFollows: Int = 10,
    val maxVotes: Int = 3,
)

@Serializable
data class ManagedArtist(
    val spotifyArtistId: String,
    val name: String = "",
    val imageUrl: String? = null,
    val isVerified: Boolean = false,
)

@Serializable
data class SelectedArtist(
    val spotifyArtistId: String,
    val name: String = "",
    val imageUrl: String? = null,
    val artistId: String? = null,
    val isDJ: Boolean = false,
    val isElectronic: Boolean = false,
    val djMagRank: Int? = null,
    val genres: List<String> = emptyList(),
    val followers: Long? = null,
    val popularity: Int? = null,
    val city: String? = null,
    val country: String? = null,
    val region: String? = null,
    val youtube: YouTubeData? = null,
)

@Serializable
data class YouTubeData(
    val channelId: String? = null,
    val subscribers: Long? = null,
    val views: Long? = null,
    val isOAC: Boolean = false,
    val customUrl: String? = null,
    val url: String? = null,
)

@Serializable
data class ArtistVerification(
    val isVerified: Boolean = false,
    val verifiedSpotifyArtistId: String? = null,
    val verifiedAt: String? = null,
)

// ───────── Подписки и голоса (как на сайте: /artists/:id/follow, /me/follows, /vote) ─────────

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class FollowedArtist(
    @kotlinx.serialization.json.JsonNames("spotify_artist_id") val spotifyArtistId: String,
    val name: String? = null,
    @kotlinx.serialization.json.JsonNames("image_url") val imageUrl: String? = null,
    /** Подписчики Spotify — для строки списка и сортировки «По популярности». */
    val followers: Long? = null,
    val popularity: Int? = null,
    /** Когда подписался (бэкенд уже отдаёт список от новых к старым). */
    val createdAt: String? = null,
)

@Serializable
data class FollowsResponse(val follows: List<FollowedArtist> = emptyList(), val count: Int = 0)

/** Голоса — список Spotify ID, максимум 3 одновременно. POST /vote заменяет весь набор. */
@Serializable
data class VotesPayload(val votes: List<String> = emptyList(), val count: Int? = null)
