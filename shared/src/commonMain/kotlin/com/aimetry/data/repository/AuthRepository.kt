package com.aimetry.data.repository

import com.aimetry.api.endpoints.AuthApi
import com.aimetry.api.models.MeResponse
import com.aimetry.api.models.RegisterRequest
import com.aimetry.api.models.LoginRequest
import com.aimetry.domain.model.LoginType
import com.aimetry.domain.model.OAuthProvider
import com.aimetry.domain.model.UserType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthResult {
    data class Success(val userData: MeResponse) : AuthResult()
    data class NeedsVerification(val userData: MeResponse) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

sealed class ArtistAuthState {
    object Loading : ArtistAuthState()
    data class OAuthStarted(val url: String) : ArtistAuthState()
    data class NeedsVerification(val userData: MeResponse) : ArtistAuthState()
    data class Success(val userData: MeResponse) : ArtistAuthState()
    data class Error(val message: String) : ArtistAuthState()
}

class AuthRepository(
    private val authApi: AuthApi,
    private val userRepository: UserRepository
) {
    private val _currentUser = MutableStateFlow<MeResponse?>(null)
    val currentUser: StateFlow<MeResponse?> = _currentUser.asStateFlow()
    
    suspend fun getUserType(): UserType {
        val userData = userRepository.getMe().getOrNull()
        return when {
            userData == null || !userData.isAuthed -> UserType.UNAUTHENTICATED
            userData.artistVerification?.isVerified == true -> UserType.ARTIST
            else -> UserType.REGULAR_USER
        }
    }
    
    suspend fun startOAuth(provider: OAuthProvider): String {
        return authApi.startOAuth(provider)
    }
    
    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
        loginType: LoginType
    ): AuthResult {
        return try {
            val request = RegisterRequest(email, password, displayName)
            val userData = authApi.register(request)
            _currentUser.value = userData
            
            when (loginType) {
                is LoginType.User -> AuthResult.Success(userData)
                is LoginType.Artist -> {
                    if (userData.artistVerification?.isVerified == true) {
                        AuthResult.Success(userData)
                    } else {
                        AuthResult.NeedsVerification(userData)
                    }
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Ошибка регистрации")
        }
    }
    
    suspend fun login(
        email: String,
        password: String,
        loginType: LoginType
    ): AuthResult {
        return try {
            val request = LoginRequest(email, password)
            val userData = authApi.login(request)
            _currentUser.value = userData
            
            when (loginType) {
                is LoginType.User -> AuthResult.Success(userData)
                is LoginType.Artist -> {
                    if (userData.artistVerification?.isVerified == true) {
                        AuthResult.Success(userData)
                    } else {
                        AuthResult.NeedsVerification(userData)
                    }
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Ошибка входа")
        }
    }
    
    suspend fun handleOAuthCallback(
        provider: OAuthProvider,
        loginType: LoginType
    ): AuthResult {
        return try {
            val userData = userRepository.getMe().getOrNull()
                ?: return AuthResult.Failure("Ошибка получения данных пользователя")
            
            _currentUser.value = userData
            
            when (loginType) {
                is LoginType.User -> {
                    if (userData.isAuthed) {
                        AuthResult.Success(userData)
                    } else {
                        AuthResult.Failure("Ошибка авторизации")
                    }
                }
                is LoginType.Artist -> {
                    if (!userData.isAuthed) {
                        AuthResult.Failure("Ошибка авторизации")
                    } else if (userData.artistVerification?.isVerified == true) {
                        AuthResult.Success(userData)
                    } else {
                        AuthResult.NeedsVerification(userData)
                    }
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Ошибка обработки OAuth callback")
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            authApi.logout()
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun updateCurrentUser(userData: MeResponse) {
        _currentUser.value = userData
    }
}

