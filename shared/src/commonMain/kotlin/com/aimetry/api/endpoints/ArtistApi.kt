package com.aimetry.api.endpoints

import com.aimetry.api.*
import com.aimetry.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class ArtistApi(private val httpClient: HttpClient, private val baseUrl: String) {
    
    suspend fun searchArtists(query: String, limit: Int = 10): Result<ArtistSearchResult> {
        return try {
            val response = httpClient.get("$baseUrl/artists/search") {
                parameter("q", query)
                parameter("limit", limit)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getArtistDetails(spotifyArtistId: String): Result<ArtistDetailsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/artists/spotify/$spotifyArtistId")
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTopArtists(limit: Int = 10): Result<TopArtistsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/artists/top") {
                parameter("limit", limit)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTrends(
        category: String? = null,
        limit: Int = 20
    ): Result<TrendsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/artists/trends") {
                category?.let { parameter("category", it) }
                parameter("limit", limit)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getDJMagRankings(year: Int): Result<DJMagRankingsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/djmag/rankings") {
                parameter("year", year)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

