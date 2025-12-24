package com.aimetry.api.models

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val isAuthed: Boolean,
    val spotifyUserId: String? = null,
    val selectedArtistId: String? = null,
    val collection: List<ManagedArtist> = emptyList(),
    val selectedArtist: SelectedArtist? = null,
    val artistVerification: ArtistVerification? = null
)

@Serializable
data class ManagedArtist(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val isVerified: Boolean
)

@Serializable
data class SelectedArtist(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val artistId: String? = null,
    val isDJ: Boolean,
    val isElectronic: Boolean,
    val djMagRank: Int? = null,
    val genres: List<String> = emptyList(),
    val followers: Long,
    val popularity: Int,
    val city: String? = null,
    val country: String? = null,
    val region: String? = null,
    val youtube: YouTubeData? = null
)

@Serializable
data class YouTubeData(
    val channelId: String? = null,
    val subscribers: Long? = null,
    val views: Long? = null,
    val isOAC: Boolean,
    val customUrl: String? = null
)

@Serializable
data class ArtistVerification(
    val isVerified: Boolean,
    val verifiedSpotifyArtistId: String? = null,
    val verifiedAt: String? = null // ISO timestamp
)

