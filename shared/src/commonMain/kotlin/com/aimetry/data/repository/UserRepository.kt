package com.aimetry.data.repository

import com.aimetry.api.endpoints.UserApi
import com.aimetry.api.models.*

class UserRepository(private val userApi: UserApi) {
    
    suspend fun getMe(): Result<MeResponse> {
        return userApi.getMe()
    }
    
    suspend fun claimArtist(spotifyArtistId: String): Result<ClaimArtistResponse> {
        return userApi.claimArtist(spotifyArtistId)
    }
    
    suspend fun selectArtist(spotifyArtistId: String): Result<SelectArtistResponse> {
        return userApi.selectArtist(spotifyArtistId)
    }
    
    suspend fun getArtistData(): Result<ArtistDataResponse> {
        return userApi.getArtistData()
    }
    
    suspend fun getSnapshots(days: Int = 30): Result<SnapshotsResponse> {
        return userApi.getSnapshots(days)
    }
    
    suspend fun verifyAsArtist(spotifyArtistId: String): Result<VerifyArtistResponse> {
        return userApi.verifyAsArtist(spotifyArtistId)
    }
    
    suspend fun unverifyAsArtist(): Result<Unit> {
        return userApi.unverifyAsArtist()
    }
    
    suspend fun updateArtistLocation(
        city: String? = null,
        country: String? = null,
        region: String? = null
    ): Result<UpdateLocationResponse> {
        return userApi.updateArtistLocation(city, country, region)
    }
    
    suspend fun updateYouTubeHandle(
        spotifyArtistId: String,
        youtubeHandle: String
    ): Result<UpdateYouTubeHandleResponse> {
        return userApi.updateYouTubeHandle(spotifyArtistId, youtubeHandle)
    }
    
    suspend fun registerDeviceToken(
        deviceToken: String,
        platform: String,
        spotifyArtistId: String
    ): Result<RegisterDeviceTokenResponse> {
        return userApi.registerDeviceToken(deviceToken, platform, spotifyArtistId)
    }
    
    suspend fun unregisterDeviceToken(
        deviceToken: String,
        spotifyArtistId: String
    ): Result<Unit> {
        return userApi.unregisterDeviceToken(deviceToken, spotifyArtistId)
    }
}

