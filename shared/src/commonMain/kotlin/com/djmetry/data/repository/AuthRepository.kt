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
import kotlinx.coroutines.sync.Mutex

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
    /**
     * Перед выходом, пока сессия жива: снять токен пушей с сервера. Не удалось (нет сети) — вернуть токен
     * устройства: снятие повторится в [retryPendingSignOut], иначе пуши прошлого пользователя продолжат идти.
     */
    private val beforeSignOut: suspend () -> String? = { null },
    /** Повторное снятие токена пушей старой сессией (`bearer`). */
    private val retryUnregister: suspend (bearer: String, pushToken: String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    /** Выход не состоялся (удаление аккаунта отказано) — вернуть пуши. */
    private val onSignOutAborted: suspend () -> Unit = {},
    /** Сбросить данные пользователя во всех репозиториях ([UserScoped]). */
    private val clearUserData: suspend () -> Unit = {},
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** Кешируем токен в памяти, чтобы не читать Keychain/Keystore на каждый запрос. */
    var currentToken: String? = storage.getAuthToken()
        private set

    /** Одна проверка сессии за раз: её собственный `/me` тоже может вернуть 401. */
    private val verifying = Mutex()

    /** При запуске: есть токен → проверяем через /api/me. Без сети оставляем вход, чтобы не выкидывать офлайн. */
    suspend fun restore(): SessionState {
        if (currentToken == null) return signedOutLocally()
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
     * Токен сохраняется только после успешного `/me`, иначе неудачный вход молча «войдёт» при следующем запуске.
     * Отмена пользователем — [OAuthCancelledException]; ошибка бэкенда — [ApiException] с кодом
     * (`user_blocked`, `oauth_failed`, `invalid_or_expired_code`, …).
     */
    suspend fun signIn(provider: OAuthProvider): Result<MeResponse> = runCatching {
        val pkce = Pkce.generate()
        val callback = Url(redirect.authorize { appRedirect -> authApi.mobileStartUrl(provider, pkce.challenge, appRedirect, redirect.handoffPage) })
        callback.parameters["error"]?.let {
            throw ApiException(status = 0, code = it, message = it, retryAfterSeconds = callback.parameters["retry_after"]?.toIntOrNull())
        }
        val code = callback.parameters["code"] ?: throw ApiException(status = 0, code = "oauth_failed", message = null)

        val token = authApi.exchangeCode(code, pkce.verifier).getOrThrow().token
        currentToken = token
        val me = userApi.getMe().onFailure { currentToken = storage.getAuthToken() }.getOrThrow()
        if (!me.isAuthed) {
            signedOutLocally()
            throw ApiException(status = 401, code = "session_not_found", message = null)
        }
        storage.saveAuthToken(token)
        // Новый вход — с чистого листа: ни одного значения прошлого аккаунта
        clearUserData()
        _session.value = SessionState.SignedIn(me)
        me
    }

    suspend fun refreshMe(): Result<MeResponse> = userApi.getMe().onSuccess { me ->
        _session.value = if (me.isAuthed) SessionState.SignedIn(me) else signedOutLocally()
    }

    /**
     * Запрос с токеном [sentToken] получил 401. Сам по себе 401 ещё не выход (аудитория отвечает им на
     * мобильный токен) — проверяем `/me`: сессии нет → выходим. Без сети ничего не делаем.
     */
    suspend fun verifySession(sentToken: String) {
        if (sentToken != currentToken || !verifying.tryLock()) return
        try {
            val gone = userApi.getMe().fold({ !it.isAuthed }, { it is ApiException && it.isUnauthorized })
            if (gone && sentToken == currentToken) signedOutLocally()
        } finally {
            verifying.unlock()
        }
    }

    /**
     * Выход. Без сети локально выходим всё равно, а снятие пушей и отзыв сессии откладываем:
     * старый токен лежит в защищённом хранилище, [retryPendingSignOut] доделает при следующем запуске.
     */
    suspend fun logout() {
        val token = currentToken
        val pushLeft = runCatching { beforeSignOut() }.getOrNull()
        val revoked = authApi.logout().isSuccess
        if (token != null && (pushLeft != null || !revoked)) storage.savePendingSignOut(PendingSignOut(token, pushLeft).encode())
        signedOutLocally()
    }

    /** Доделать отложенный выход: снять пуши и отозвать старую сессию. 401 — сессии уже нет, делать нечего. */
    suspend fun retryPendingSignOut() {
        val pending = PendingSignOut.decode(storage.getPendingSignOut()) ?: return
        fun done(r: Result<*>) = r.isSuccess || (r.exceptionOrNull() as? ApiException)?.isUnauthorized == true
        val push = pending.pushToken?.let { retryUnregister(pending.bearer, it) } ?: Result.success(Unit)
        if (!done(push)) return
        if (!done(authApi.logout(bearer = pending.bearer))) {
            storage.savePendingSignOut(PendingSignOut(pending.bearer, null).encode())
            return
        }
        storage.savePendingSignOut(null)
    }

    suspend fun deleteAccount(reason: String? = null): Result<Unit> {
        runCatching { beforeSignOut() }
        return authApi.deleteAccount(reason)
            .map { signedOutLocally(); Unit }
            .onFailure { runCatching { onSignOutAborted() } }
    }

    private suspend fun signedOutLocally(): SessionState {
        storage.clearAuth()
        currentToken = null
        clearUserData()
        _session.value = SessionState.SignedOut
        return SessionState.SignedOut
    }
}

/** Незавершённый выход: старый токен сессии и токен пушей, который не удалось снять. */
internal data class PendingSignOut(val bearer: String, val pushToken: String?) {
    fun encode(): String = if (pushToken == null) bearer else "$bearer\n$pushToken"

    companion object {
        fun decode(value: String?): PendingSignOut? {
            val parts = value?.split('\n')?.takeIf { it.first().isNotBlank() } ?: return null
            return PendingSignOut(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
        }
    }
}
