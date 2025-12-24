package com.aimetry.data.repository

import com.aimetry.api.endpoints.ArtistApi
import com.aimetry.api.models.*

class ArtistRepository(private val artistApi: ArtistApi) {
    
    suspend fun searchArtists(query: String, limit: Int = 10): Result<ArtistSearchResult> {
        return artistApi.searchArtists(query, limit)
    }
    
    suspend fun getArtistDetails(spotifyArtistId: String): Result<ArtistDetailsResponse> {
        return artistApi.getArtistDetails(spotifyArtistId)
    }
    
    suspend fun getTopArtists(limit: Int = 10): Result<TopArtistsResponse> {
        return artistApi.getTopArtists(limit)
    }
    
    suspend fun getTrends(
        category: String? = null,
        limit: Int = 20
    ): Result<TrendsResponse> {
        return artistApi.getTrends(category, limit)
    }
    
    suspend fun getDJMagRankings(year: Int): Result<DJMagRankingsResponse> {
        return artistApi.getDJMagRankings(year)
    }
}

