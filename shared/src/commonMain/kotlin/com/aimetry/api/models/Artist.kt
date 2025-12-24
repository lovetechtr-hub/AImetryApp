package com.aimetry.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ArtistSearchResult(
    val artists: List<ArtistSearchItem>
)

@Serializable
data class ArtistSearchItem(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long,
    val popularity: Int,
    val genres: List<String> = emptyList(),
    val isDJ: Boolean,
    val djTier: String? = null // 'A' или 'B'
)

@Serializable
data class ArtistDetailsResponse(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long,
    val popularity: Int,
    val genres: List<String> = emptyList(),
    val externalUrl: String,
    val inSystem: Boolean,
    val isVerified: Boolean,
    val isElectronic: Boolean,
    val isDJ: Boolean,
    val djMagRank: Int? = null,
    val aimetryScore: Double? = null,
    val primaryCategory: String? = null,
    val categoryRank: Int? = null,
    val city: String? = null,
    val country: String? = null,
    val region: String? = null
)

@Serializable
data class ArtistAccount(
    val id: Int,
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null
)

@Serializable
data class Snapshot(
    val date: String,
    val score: Double,
    val status: String,
    val confidence: String,
    val spotify_followers: Long,
    val spotify_popularity: Int
)

@Serializable
data class ArtistRanking(
    val score: Double,
    val position: Int,
    val totalArtists: Int,
    val status: String,
    val confidence: String
)

@Serializable
data class ArtistDataResponse(
    val account: ArtistAccount,
    val artist: ArtistDetailsResponse? = null,
    val latest: Snapshot? = null,
    val ranking: ArtistRanking? = null
)

@Serializable
data class SnapshotsResponse(
    val snapshots: List<SnapshotItem>
)

@Serializable
data class SnapshotItem(
    val date: String,
    val score: Double
)

