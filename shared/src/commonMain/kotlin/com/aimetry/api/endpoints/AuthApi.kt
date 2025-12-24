package com.aimetry.api.endpoints

import com.aimetry.api.*
import com.aimetry.api.models.*
import com.aimetry.domain.model.OAuthProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class AuthApi(private val httpClient: HttpClient, private val baseUrl: String) {
    
    suspend fun startOAuth(provider: OAuthProvider): String {
        return "$baseUrl/auth/${provider.value}/start"
    }
    
    suspend fun register(request: RegisterRequest): MeResponse {
        val response = httpClient.post("$baseUrl/auth/register") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }
    
    suspend fun login(request: LoginRequest): MeResponse {
        val response = httpClient.post("$baseUrl/auth/login") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            httpClient.post("$baseUrl/logout")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

