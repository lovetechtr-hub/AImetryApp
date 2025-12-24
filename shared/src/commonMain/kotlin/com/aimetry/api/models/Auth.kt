package com.aimetry.api.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class ClaimArtistRequest(
    val spotifyArtistId: String
)

@Serializable
data class ClaimArtistResponse(
    val ok: Boolean,
    val selectedArtistId: String? = null,
    val collection: List<com.aimetry.api.models.ManagedArtist> = emptyList(),
    val artist: ArtistDetailsResponse? = null,
    val error: String? = null,
    val message: String? = null,
    val currentCount: Int? = null,
    val maxCount: Int? = null
)

@Serializable
data class SelectArtistRequest(
    val spotifyArtistId: String
)

@Serializable
data class SelectArtistResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class VerifyArtistRequest(
    val spotifyArtistId: String
)

@Serializable
data class VerifyArtistResponse(
    val success: Boolean,
    val message: String? = null,
    val artistVerification: ArtistVerification? = null,
    val error: String? = null
)

@Serializable
data class UpdateLocationRequest(
    val city: String? = null,
    val country: String? = null,
    val region: String? = null
)

@Serializable
data class LocationData(
    val city: String? = null,
    val country: String? = null,
    val region: String? = null
)

@Serializable
data class UpdateLocationResponse(
    val success: Boolean,
    val message: String? = null,
    val location: LocationData? = null
)

@Serializable
data class UpdateYouTubeHandleRequest(
    val spotifyArtistId: String,
    val youtubeHandle: String // '@username' или 'https://www.youtube.com/@username'
)

@Serializable
data class YouTubeChannel(
    val channelId: String,
    val title: String,
    val subscribers: Long,
    val views: Long,
    val isOAC: Boolean,
    val customUrl: String? = null
)

@Serializable
data class UpdateYouTubeHandleResponse(
    val success: Boolean,
    val message: String? = null,
    val channel: YouTubeChannel? = null,
    val handle: String? = null
)

@Serializable
data class RegisterDeviceTokenRequest(
    val deviceToken: String,
    val platform: String, // "ios", "android", "web"
    val spotifyArtistId: String
)

@Serializable
data class RegisterDeviceTokenResponse(
    val success: Boolean,
    val message: String? = null
)

