package com.djmetry.data.repository

import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.AuthApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.MeResponse
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.auth.Pkce
import com.djmetry.auth.OAuthRedirect
import com.djmetry.auth.platformOAuthRedirect
import com.djmetry.data.local.SessionStorage
import com.djmetry.domain.model.OAuthProvider
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val me: MeResponse) : SessionState
}

/**
 * Сессия пользователя: нативный OAuth с PKCE → Bearer-токен (docs §1.2).
 * Токен хранится в защищённом хранилище и подставляется во все запросы через [currentToken].
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val storage: SessionStorage,
    /** Как получить редирект: схема djmetry:// (телефоны, установленный десктоп) или loopback (десктоп). */
    private val redirect: OAuthRedirect = platformOAuthRedirect(),
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** Кешируем токен в памяти, чтобы не читать Keychain/Keystore на каждый запрос. */
    var currentToken: String? = storage.getAuthToken()
        private set

    /** При запуске: есть токен → проверяем через /api/me. Без сети оставляем вход, чтобы не выкидывать офлайн. */
    suspend fun restore(): SessionState {
        if (currentToken == null) return SessionState.SignedOut.also { _session.value = it }
        val state = userApi.getMe().fold(
            onSuccess = { me -> if (me.isAuthed) SessionState.SignedIn(me) else signedOutLocally() },
            onFailure = { e ->
                if (e is ApiException && e.isUnauthorized) signedOutLocally()
                else SessionState.SignedIn(MeResponse(isAuthed = true))
            },
        )
        _session.value = state
        return state
    }

    /**
     * Полный вход: PKCE → браузер ([OAuthRedirect]) → редирект с одноразовым кодом → обмен на токен → /api/me.
     * Отмена пользователем — [OAuthCancelledException]; ошибка бэкенда — [ApiException] с кодом
     * (`user_blocked`, `oauth_failed`, `invalid_or_expired_code`, …).
     */
    suspend fun signIn(provider: OAuthProvider): Result<MeResponse> = runCatching {
        val pkce = Pkce.generate()
        val callback = Url(redirect.authorize { appRedirect -> authApi.mobileStartUrl(provider, pkce.challenge, appRedirect) })
        callback.parameters["error"]?.let { throw ApiException(status = 0, code = it, message = it) }
        val code = callback.parameters["code"] ?: throw ApiException(status = 0, code = "oauth_failed", message = null)

        val token = authApi.exchangeCode(code, pkce.verifier).getOrThrow().token
        storage.saveAuthToken(token)
        currentToken = token

        val me = userApi.getMe().getOrThrow()
        if (!me.isAuthed) {
            signedOutLocally()
            throw ApiException(status = 401, code = "session_not_found", message = null)
        }
        _session.value = SessionState.SignedIn(me)
        me
    }

    suspend fun refreshMe(): Result<MeResponse> = userApi.getMe().onSuccess { me ->
        _session.value = if (me.isAuthed) SessionState.SignedIn(me) else signedOutLocally()
    }

    suspend fun logout() {
        authApi.logout()
        signedOutLocally()
    }

    suspend fun deleteAccount(reason: String? = null): Result<Unit> =
        authApi.deleteAccount(reason).map { signedOutLocally(); Unit }

    private fun signedOutLocally(): SessionState {
        storage.clearAuth()
        currentToken = null
        _session.value = SessionState.SignedOut
        return SessionState.SignedOut
    }
}
