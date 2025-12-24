package com.aimetry.api.endpoints

import com.aimetry.api.*
import com.aimetry.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class UserApi(private val httpClient: HttpClient, private val baseUrl: String) {
    
    suspend fun getMe(): Result<MeResponse> {
        return try {
            val response = httpClient.get("$baseUrl/me")
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get user data: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun claimArtist(spotifyArtistId: String): Result<ClaimArtistResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/claim-artist") {
                contentType(ContentType.Application.Json)
                setBody(ClaimArtistRequest(spotifyArtistId))
            }
            val result = response.body<ClaimArtistResponse>()
            if (result.ok) {
                Result.success(result)
            } else {
                when (result.error) {
                    "artist_limit_reached" -> {
                        Result.failure(ArtistLimitReachedException(result.maxCount ?: 10))
                    }
                    "artist_not_catalog_verified" -> {
                        Result.failure(ArtistNotVerifiedException())
                    }
                    "artist_not_dj" -> {
                        Result.failure(ArtistNotDJException())
                    }
                    else -> {
                        Result.failure(Exception(result.message ?: "Ошибка добавления артиста"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun selectArtist(spotifyArtistId: String): Result<SelectArtistResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/select-artist") {
                contentType(ContentType.Application.Json)
                setBody(SelectArtistRequest(spotifyArtistId))
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getArtistData(): Result<ArtistDataResponse> {
        return try {
            val response = httpClient.get("$baseUrl/me/artist")
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSnapshots(days: Int = 30): Result<SnapshotsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/me/snapshots") {
                parameter("days", days)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyAsArtist(spotifyArtistId: String): Result<VerifyArtistResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/verify-as-artist") {
                contentType(ContentType.Application.Json)
                setBody(VerifyArtistRequest(spotifyArtistId))
            }
            val result = response.body<VerifyArtistResponse>()
            if (result.success) {
                Result.success(result)
            } else {
                when (result.error) {
                    "spotify_auth_required" -> {
                        Result.failure(Exception("Для верификации необходим доступ к Spotify API"))
                    }
                    "artist_not_managed" -> {
                        Result.failure(Exception("Артист не в вашей коллекции"))
                    }
                    else -> {
                        Result.failure(Exception(result.message ?: "Ошибка верификации"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unverifyAsArtist(): Result<Unit> {
        return try {
            httpClient.post("$baseUrl/me/unverify-as-artist")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateArtistLocation(
        city: String? = null,
        country: String? = null,
        region: String? = null
    ): Result<UpdateLocationResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/artist/update-location") {
                contentType(ContentType.Application.Json)
                setBody(UpdateLocationRequest(city, country, region))
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateYouTubeHandle(
        spotifyArtistId: String,
        youtubeHandle: String
    ): Result<UpdateYouTubeHandleResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/artist/youtube-handle") {
                contentType(ContentType.Application.Json)
                setBody(UpdateYouTubeHandleRequest(spotifyArtistId, youtubeHandle))
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun registerDeviceToken(
        deviceToken: String,
        platform: String,
        spotifyArtistId: String
    ): Result<RegisterDeviceTokenResponse> {
        return try {
            val response = httpClient.post("$baseUrl/me/register-device-token") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceTokenRequest(deviceToken, platform, spotifyArtistId))
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unregisterDeviceToken(
        deviceToken: String,
        spotifyArtistId: String
    ): Result<Unit> {
        return try {
            httpClient.delete("$baseUrl/me/unregister-device-token") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceTokenRequest(deviceToken, "android", spotifyArtistId))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

