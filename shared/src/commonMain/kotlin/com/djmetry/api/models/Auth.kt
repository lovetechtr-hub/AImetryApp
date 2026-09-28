package com.djmetry.api.models

import kotlinx.serialization.Serializable

// ───────── Мобильный OAuth (PKCE + Bearer), docs §1.2 ─────────

@Serializable
data class MobileTokenRequest(val code: String, val code_verifier: String)

@Serializable
data class MobileTokenResponse(val token: String, val tokenType: String = "Bearer", val expiresAt: String? = null)

@Serializable
data class SuccessResponse(val success: Boolean = false, val ok: Boolean = false, val message: String? = null)

@Serializable
data class DeleteAccountRequest(val password: String? = null, val reason: String? = null)

// ───────── Артист аккаунта, docs §2 ─────────

@Serializable
data class SpotifyArtistIdRequest(val spotifyArtistId: String)

@Serializable
data class SelectArtistResponse(
    val success: Boolean = false,
    val selectedArtistId: String? = null,
    val selectedArtist: SelectedArtist? = null,
)

@Serializable
data class ClaimArtistResponse(
    val ok: Boolean = false,
    val selectedArtistId: String? = null,
    val collection: List<ManagedArtist> = emptyList(),
    val artist: ClaimedArtist? = null,
)

@Serializable
data class ClaimedArtist(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val isDJ: Boolean = false,
    val djTier: String? = null,
    val isElectronic: Boolean = false,
    val djMagRank: Int? = null,
    val confidence: String? = null,
    val status: String? = null,
)

@Serializable
data class LinkSpotifyArtistRequest(val spotify_url: String)

/** Ответ link-spotify-artist: либо привязали (ok=true), либо это наш DJ и надо идти в claim (is_our_dj=true). */
@Serializable
data class LinkSpotifyArtistResponse(
    val ok: Boolean = false,
    val linked: Boolean? = null,
    val is_our_dj: Boolean = false,
    val is_djmetry_artist: Boolean = false,
    val claim_endpoint: String? = null,
    val artistId: String? = null,
    val artistName: String? = null,
    val imageUrl: String? = null,
    val spotifyUrl: String? = null,
    val message: String? = null,
)

@Serializable
data class VerificationFixedRequest(val userFixedReported: Boolean = true, val requestId: String? = null)

@Serializable
data class UpdateLocationRequest(val city: String? = null, val country: String? = null, val region: String? = null)

@Serializable
data class LocationData(val city: String? = null, val country: String? = null, val region: String? = null)

@Serializable
data class UpdateLocationResponse(val success: Boolean = false, val location: LocationData? = null)

@Serializable
data class UpdateYouTubeHandleRequest(val youtubeHandle: String, val spotifyArtistId: String? = null)

@Serializable
data class YouTubeChannel(
    val channelId: String? = null,
    val title: String? = null,
    val subscribers: Long? = null,
    val views: Long? = null,
    val isOAC: Boolean = false,
    val customUrl: String? = null,
)

@Serializable
data class UpdateYouTubeHandleResponse(val success: Boolean = false, val channel: YouTubeChannel? = null, val handle: String? = null)

// ───────── Пуши и настройки уведомлений, docs §3–4 ─────────

@Serializable
data class DeviceTokenRequest(
    val deviceToken: String,
    val platform: String? = null, // "ios" | "android" | "web" — только для регистрации
    val spotifyArtistId: String,
)

@Serializable
data class PushPreferences(val enabled: Boolean = true, val types: Map<String, Boolean> = emptyMap())

@Serializable
data class ReleaseRadarSettings(
    val releaseRadarEnabled: Boolean? = null,
    val releaseRadarFrequency: String? = null, // "immediate" | "weekly_digest"
)

@Serializable
data class ConcertAlertsSettings(
    val concertAlertsEnabled: Boolean? = null,
    val concertAlertsFrequency: String? = null,
    val concertAlertCountry: String? = null,
    val concertAlertCity: String? = null,
    val effectiveCountry: String? = null,
    val effectiveCity: String? = null,
    val needsLocationHint: Boolean? = null,
)
