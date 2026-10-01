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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed interface SessionState {
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val me: MeResponse) : SessionState
}

/** Попытка входа — на уровне приложения: поворот и пересоздание экрана её не обрывают. */
sealed interface SignInState {
    data object Idle : SignInState
    /** [provider] null — завершаем вход после перезапуска (код пришёл, а приложение было выгружено). */
    data class InProgress(val provider: OAuthProvider?) : SignInState
    data class Failed(val error: Throwable) : SignInState
}

/** Почему вышли не сами: экран входа объясняет, а не молча показывает кнопки. */
enum class SignOutReason { Expired }

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
    /** Фон для входа и выхода: живёт дольше экрана. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** Кешируем токен в памяти, чтобы не читать Keychain/Keystore на каждый запрос (читают разные потоки). */
    @kotlin.concurrent.Volatile
    var currentToken: String? = storage.getAuthToken()
        private set

    private val _signIn = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signIn.asStateFlow()
    private var signInJob: Job? = null

    private val _signOutReason = MutableStateFlow<SignOutReason?>(null)
    /** Причина последнего выхода «не по своей воле» (сессия истекла) — показать на экране входа. */
    val signOutReason: StateFlow<SignOutReason?> = _signOutReason.asStateFlow()
    fun consumeSignOutReason() { _signOutReason.value = null }

    /** Одна проверка сессии за раз: её собственный `/me` тоже может вернуть 401. */
    private val verifying = Mutex()

    /** При запуске: есть токен → проверяем через /api/me. Без сети оставляем вход, чтобы не выкидывать офлайн. */
    suspend fun restore(): SessionState {
        if (currentToken == null) currentToken = storage.getAuthToken()
        if (currentToken == null) {
            // iPhone до первой разблокировки: Keychain недоступен — это не «вышли», ждём и пробуем снова
            if (storage.isAuthStorageLocked()) return SessionState.Unknown
            return signedOutLocally()
        }
        // Мгновенный старт: последний профиль сразу, проверка /me — следом
        cachedMe()?.let { if (_session.value == SessionState.Unknown) _session.value = SessionState.SignedIn(it) }
        val state = userApi.getMe().fold(
            onSuccess = { me -> if (me.isAuthed) signedIn(me) else { _signOutReason.value = SignOutReason.Expired; signedOutLocally() } },
            onFailure = { e ->
                if (e is ApiException && e.isUnauthorized) { _signOutReason.value = SignOutReason.Expired; signedOutLocally() }
                // Без сети — последний профиль (а не пустая заглушка: иначе артист видел себя фанатом)
                else SessionState.SignedIn(cachedMe() ?: MeResponse(isAuthed = true))
            },
        )
        _session.value = state
        return state
    }

    /** Профиль — пустая заглушка (запуск без сети): перечитать, когда появится сеть или приложение вернётся. */
    val isStubProfile: Boolean get() = (_session.value as? SessionState.SignedIn)?.me?.let { it.user == null && it.userId == null } == true

    /** Вход в фоне приложения; повторное нажатие другого провайдера отменяет прежнюю попытку. */
    fun startSignIn(provider: OAuthProvider) = launchSignIn(SignInState.InProgress(provider)) { signIn(provider) }

    /** Отменить вход (закрыл браузер, передумал): крутилка гаснет сразу. */
    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        storage.setPref(PENDING_OAUTH, null)
        _signIn.value = SignInState.Idle
    }

    fun consumeSignInError() { if (_signIn.value is SignInState.Failed) _signIn.value = SignInState.Idle }

    /**
     * Код вернулся, а вход его не ждал: Android выгрузил приложение, пока пользователь был в браузере
     * (2FA, переключение на почту). Ключ PKCE сохранён — завершаем вход.
     */
    fun resumeSignIn(callbackUrl: String) {
        if (_signIn.value is SignInState.InProgress && signInJob?.isActive == true) return
        launchSignIn(SignInState.InProgress(null)) {
            val pending = pendingVerifier() ?: return@launchSignIn Result.failure(ApiException(status = 0, code = "invalid_or_expired_code", message = null))
            try { Result.success(complete(Url(callbackUrl), pending)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
        }
    }

    private fun launchSignIn(state: SignInState.InProgress, block: suspend () -> Result<MeResponse>) {
        signInJob?.cancel()
        _signIn.value = state
        signInJob = scope.launch {
            val r = block()
            _signIn.value = r.fold({ SignInState.Idle }, { e -> if (e is com.djmetry.auth.OAuthCancelledException) SignInState.Idle else SignInState.Failed(e) })
        }
    }

    /**
     * Полный вход: PKCE → браузер ([OAuthRedirect]) → редирект с одноразовым кодом → обмен на токен → /api/me.
     * Токен сохраняется только после успешного `/me`, иначе неудачный вход молча «войдёт» при следующем запуске.
     * Отмена пользователем — [OAuthCancelledException]; ошибка бэкенда — [ApiException] с кодом
     * (`user_blocked`, `oauth_failed`, `invalid_or_expired_code`, …).
     */
    suspend fun signIn(provider: OAuthProvider): Result<MeResponse> = try {
        val pkce = Pkce.generate()
        // Ключ — на случай, если Android выгрузит приложение, пока пользователь в браузере
        storage.setPref(PENDING_OAUTH, "${pkce.verifier}|${kotlin.time.Clock.System.now().toEpochMilliseconds()}")
        val callback = Url(redirect.authorize { appRedirect -> authApi.mobileStartUrl(provider, pkce.challenge, appRedirect, redirect.handoffPage) })
        Result.success(complete(callback, pkce.verifier))
    } catch (e: CancellationException) {
        storage.setPref(PENDING_OAUTH, null)
        throw e // структурная отмена не превращается в «ошибку входа»
    } catch (e: Throwable) {
        storage.setPref(PENDING_OAUTH, null)
        Result.failure(e)
    }

    /** Редирект с кодом → обмен на токен → `/me`. Общий для обычного входа и завершения после перезапуска. */
    private suspend fun complete(callback: Url, verifier: String): MeResponse {
        storage.setPref(PENDING_OAUTH, null)
        callback.parameters["error"]?.let {
            throw ApiException(status = 0, code = it, message = it, retryAfterSeconds = callback.parameters["retry_after"]?.toIntOrNull())
        }
        val code = callback.parameters["code"] ?: throw ApiException(status = 0, code = "oauth_failed", message = null)

        val token = authApi.exchangeCode(code, verifier).getOrThrow().token
        currentToken = token
        val me = userApi.getMe().onFailure {
            currentToken = storage.getAuthToken()
            // Сессия на сервере уже создана — не оставляем её «сиротой» на 30 дней
            scope.launch { authApi.logout(bearer = token) }
        }.getOrThrow()
        if (!me.isAuthed) {
            signedOutLocally()
            throw ApiException(status = 401, code = "session_not_found", message = null)
        }
        storage.saveAuthToken(token)
        // Новый вход — с чистого листа: ни одного значения прошлого аккаунта
        clearUserData()
        _signOutReason.value = null
        _session.value = signedIn(me)
        return me
    }

    suspend fun refreshMe(): Result<MeResponse> = userApi.getMe().onSuccess { me ->
        _session.value = if (me.isAuthed) signedIn(me) else signedOutLocally()
    }

    private fun signedIn(me: MeResponse): SessionState {
        runCatching { storage.setPref(ME_CACHE, com.djmetry.api.DJMetryJson.encodeToString(MeResponse.serializer(), me)) }
        return SessionState.SignedIn(me)
    }

    private fun cachedMe(): MeResponse? =
        storage.getPref(ME_CACHE)?.let { runCatching { com.djmetry.api.DJMetryJson.decodeFromString(MeResponse.serializer(), it) }.getOrNull() }?.takeIf { it.isAuthed }

    /** Сохранённый ключ PKCE, если вход начат не больше 10 минут назад (код живёт 5 минут). */
    private fun pendingVerifier(): String? {
        val raw = storage.getPref(PENDING_OAUTH) ?: return null
        val verifier = raw.substringBefore('|')
        val at = raw.substringAfter('|').toLongOrNull() ?: return null
        val age = kotlin.time.Clock.System.now().toEpochMilliseconds() - at
        return verifier.takeIf { age in 0..PENDING_OAUTH_TTL_MS && it.isNotBlank() }
    }

    /**
     * Запрос с токеном [sentToken] получил 401. Сам по себе 401 ещё не выход (аудитория отвечает им на
     * мобильный токен) — проверяем `/me`: сессии нет → выходим. Без сети ничего не делаем.
     */
    suspend fun verifySession(sentToken: String) {
        if (sentToken != currentToken || !verifying.tryLock()) return
        try {
            val gone = userApi.getMe().fold({ !it.isAuthed }, { it is ApiException && it.isUnauthorized })
            if (gone && sentToken == currentToken) { _signOutReason.value = SignOutReason.Expired; signedOutLocally() }
        } finally {
            verifying.unlock()
        }
    }

    /**
     * Выход. Без сети локально выходим всё равно, а снятие пушей и отзыв сессии откладываем:
     * старый токен лежит в защищённом хранилище, [retryPendingSignOut] доделает при следующем запуске.
     */
    suspend fun logout() = withContext(NonCancellable) {
        // Нельзя прервать: ушли с экрана посреди выхода — пуши уже сняты, а сессия осталась бы живой
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

    suspend fun deleteAccount(reason: String? = null): Result<Unit> = withContext(NonCancellable) {
        runCatching { beforeSignOut() }
        authApi.deleteAccount(reason)
            .map { signedOutLocally(); Unit }
            .onFailure { runCatching { onSignOutAborted() } }
    }

    private suspend fun signedOutLocally(): SessionState {
        storage.clearAuth()
        storage.setPref(ME_CACHE, null)
        currentToken = null
        clearUserData()
        _session.value = SessionState.SignedOut
        return SessionState.SignedOut
    }
}

private const val PENDING_OAUTH = "oauth_pending"
private const val PENDING_OAUTH_TTL_MS = 10 * 60 * 1000L
private const val ME_CACHE = "me_cache"

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
