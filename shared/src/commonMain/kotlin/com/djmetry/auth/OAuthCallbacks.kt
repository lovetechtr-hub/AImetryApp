package com.djmetry.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Возврат из браузера с кодом, который никто не ждал: приложение было выгружено, пока человек входил
 * (Android убил процесс во время 2FA). Платформа кладёт ссылку сюда, приложение завершает вход
 * ([com.djmetry.data.repository.AuthRepository.resumeSignIn]).
 */
object OAuthCallbacks {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(url: String) { if (url.isNotBlank()) _pending.value = url }
    fun consume(): String? = _pending.value.also { _pending.value = null }
}
