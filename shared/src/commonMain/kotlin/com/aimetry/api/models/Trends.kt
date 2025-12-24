package com.aimetry.api.models

import kotlinx.serialization.Serializable

@Serializable
data class TrendsResponse(
    val category: String? = null,
    val count: Int,
    val averageScore: Double? = null,
    val artists: List<TrendingArtist>
)

@Serializable
data class TrendingArtist(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val primaryCategory: String? = null,
    val aimetryScore: Double,
    val trend: TrendData,
    val lastUpdate: String
)

@Serializable
data class TrendData(
    val score24h: Double,
    val score7d: Double,
    val volatility: Double,
    val growthRate: Double,
    val trend: String, // "up", "down", "stable"
    val category: String, // "growing", "breakthrough", "stable", "losing_momentum"
    val tags: List<String> = emptyList()
)

@Serializable
data class TopArtistsResponse(
    val artists: List<ArtistListItem>
)

@Serializable
data class ArtistListItem(
    val spotifyArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val followers: Long,
    val popularity: Int,
    val genres: List<String> = emptyList(),
    val aimetryScore: Double? = null,
    val djMagRank: Int? = null
)

@Serializable
data class DJMagRankingsResponse(
    val year: Int,
    val count: Int,
    val rankings: List<DJMagRanking>
)

@Serializable
data class DJMagRanking(
    val rank: Int,
    val name: String,
    val spotifyArtistId: String? = null,
    val imageUrl: String? = null,
    val spotifyUrl: String? = null,
    val previousYearRank: Int? = null
)

