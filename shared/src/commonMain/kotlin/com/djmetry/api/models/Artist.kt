package com.djmetry.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ───────── Поиск и карточка артиста, docs §5.1–5.2 ─────────

@Serializable
data class ArtistSearchResult(val artists: List<ArtistSearchItem> = emptyList())

@Serializable
data class ArtistSearchItem(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val isDJ: Boolean = false,
    val djTier: String? = null, // 'A' | 'B' | 'C'
    val djMagRank: Int? = null,
    val isDJMagVerified: Boolean = false,
    val slug: String? = null,
)

@Serializable
data class TrendInfo(
    val score24h: Double? = null,
    val score3d: Double? = null,
    val score7d: Double? = null,
    val volatility: Double? = null,
    val growthRate: Double? = null,
    val trend: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class SocialMedia(
    val instagram: String? = null,
    val facebook: String? = null,
    val tiktok: String? = null,
    val twitter: String? = null,
    val soundcloud: String? = null,
    val telegram: String? = null,
    val appleMusicUrl: String? = null,
    val beatportUrl: String? = null,
)

@Serializable
data class ArtistDetailsResponse(
    val spotifyArtistId: String,
    /** Треки, выбранные артистом в редакторе (до 5) — показываются первыми, как на сайте. */
    val curatedTracks: List<Track> = emptyList(),
    val name: String,
    val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val externalUrl: String? = null,
    val inSystem: Boolean = false,
    val isVerified: Boolean = false,
    val isLegend: Boolean = false,
    val isElectronic: Boolean = false,
    val isDJ: Boolean = false,
    val djMagRank: Int? = null,
    /** В JSON бэкенда поле называется aimetryScore. */
    @SerialName("aimetryScore") val djmetryScore: Double? = null,
    val primaryCategory: String? = null,
    val categoryRank: Int? = null,
    val position: Int? = null,
    val rankingCategory: String? = null,
    val talentPosition: Int? = null,
    val trend: TrendInfo? = null,
    val votes: Int? = null,
    val followsCount: Int? = null,
    val slug: String? = null,
    val canonicalUrl: String? = null,
    val city: String? = null,
    val country: String? = null,
    val region: String? = null,
    val description: String? = null,
    val youtube: YouTubeData? = null,
    val socialMedia: SocialMedia? = null,
)

@Serializable
data class ArtistTracksResponse(val tracks: List<Track> = emptyList())

@Serializable
data class Track(
    val spotifyTrackId: String? = null,
    val name: String,
    val albumName: String? = null,
    val albumImageUrl: String? = null,
    val durationMs: Long? = null,
    val popularity: Int? = null,
    val externalUrl: String? = null,
    val previewUrl: String? = null,
)

// ───────── Концерты (Bandsintown), GET /api/artists/:id/events ─────────

@Serializable
data class ArtistEventsResponse(
    val spotifyArtistId: String? = null,
    val artistName: String? = null,
    val events: List<ArtistEvent> = emptyList(),
)

@Serializable
data class ArtistEvent(
    val eventId: String,
    val datetime: String, // "2026-10-16T20:00:00", локальное время площадки
    val title: String? = null,
    val url: String? = null,
    val venue: EventVenue? = null,
    val lineup: List<String> = emptyList(),
    val offers: List<EventOffer> = emptyList(),
)

@Serializable
data class EventVenue(val name: String? = null, val city: String? = null, val region: String? = null, val country: String? = null)

@Serializable
data class EventOffer(val status: String? = null, val type: String? = null, val url: String? = null)

// ───────── Дашборд артиста аккаунта, docs §2.2 ─────────

@Serializable
data class ArtistAccount(
    val id: String? = null,
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val artistId: String? = null,
    val city: String? = null,
    val country: String? = null,
    val region: String? = null,
)

@Serializable
data class Snapshot(
    val date: String? = null,
    val score: Double? = null,
    val score24h: Double? = null,
    val score3d: Double? = null,
    val score7d: Double? = null,
    val status: String? = null,
    val confidence: String? = null,
    val spotify_followers: Long? = null,
    val spotify_popularity: Int? = null,
    val youtube_subscribers: Long? = null,
    val youtube_views24h: Long? = null,
    val youtube_views7d: Long? = null,
)

@Serializable
data class ArtistRanking(
    val score: Double? = null,
    val position: Int? = null,
    val totalArtists: Int? = null,
    val status: String? = null,
    val confidence: String? = null,
)

@Serializable
data class ArtistDataResponse(
    val account: ArtistAccount,
    val latest: Snapshot? = null,
    val ranking: ArtistRanking? = null,
)

/** GET /api/me/snapshots — только дата и очки, от старых к новым. */
@Serializable
data class SnapshotsResponse(val snapshots: List<SnapshotItem> = emptyList())

@Serializable
data class SnapshotItem(val date: String, val score: Double)
