package com.djmetry.data.local

import com.github.javakeyring.Keyring
import java.util.prefs.Preferences

/**
 * Десктоп: Bearer-токен — в системном хранилище секретов (macOS Keychain, Windows Credential Manager,
 * Linux Secret Service), остальное — в java.util.prefs. Если системное хранилище недоступно
 * (например, Linux без Secret Service), токен живёт только в памяти до закрытия приложения —
 * в открытом виде на диск он не пишется.
 */
actual class SessionStorageImpl actual constructor() : SessionStorage {

    private val prefs: Preferences = Preferences.userRoot().node("com/djmetry/desktop")
    private val keyring: Keyring? = runCatching { Keyring.create() }.getOrNull()
    private var memoryToken: String? = null
    /** Токен уже записан или стёрт в этом процессе: память главнее хранилища (стереть могло не получиться). */
    private var memoryAuthoritative = false

    override fun saveAuthToken(token: String?) {
        memoryToken = token
        memoryAuthoritative = true
        val ring = keyring ?: return
        runCatching {
            if (token == null) ring.deletePassword(SERVICE, ACCOUNT) else ring.setPassword(SERVICE, ACCOUNT, token)
        }
    }

    override fun getAuthToken(): String? =
        // После выхода токен не «воскресает» из связки ключей, если удалить его там не удалось
        if (memoryAuthoritative) memoryToken else memoryToken ?: keyring?.let { ring -> runCatching { ring.getPassword(SERVICE, ACCOUNT) }.getOrNull() }?.also { memoryToken = it }

    override fun saveLocale(locale: String) = prefs.put(KEY_LOCALE, locale)

    override fun getLocale(): String? = prefs.get(KEY_LOCALE, null)

    override fun setOnboardingSeen() = prefs.putBoolean(KEY_ONBOARDING, true)

    override fun isOnboardingSeen(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    override fun clearAuth() = saveAuthToken(null)

    override fun saveNavState(value: String?) = if (value == null) prefs.remove(KEY_NAV) else prefs.put(KEY_NAV, value)

    override fun getNavState(): String? = prefs.get(KEY_NAV, null)

    // Секрет — только в системном хранилище; без него отложенный выход не переживёт перезапуск
    override fun savePendingSignOut(value: String?) {
        val ring = keyring ?: return
        runCatching { if (value == null) ring.deletePassword(SERVICE, PENDING) else ring.setPassword(SERVICE, PENDING, value) }
    }

    override fun getPendingSignOut(): String? = keyring?.let { ring -> runCatching { ring.getPassword(SERVICE, PENDING) }.getOrNull() }

    private companion object {
        const val KEY_NAV = "nav_state"
        const val SERVICE = "com.djmetry.desktop"
        const val ACCOUNT = "auth_token"
        const val PENDING = "pending_signout"
        const val KEY_LOCALE = "djmetry_locale"
        const val KEY_ONBOARDING = "onboarding_seen"
    }
}
