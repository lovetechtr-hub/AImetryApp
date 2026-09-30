package com.djmetry.data.local

/**
 * Локальное хранилище сессии. Bearer-токен лежит в защищённом хранилище платформы
 * (iOS Keychain / Android EncryptedSharedPreferences), остальное — в обычных настройках.
 */
interface SessionStorage {
    fun saveAuthToken(token: String?)
    fun getAuthToken(): String?
    fun saveLocale(locale: String)
    fun getLocale(): String?
    fun setOnboardingSeen()
    fun isOnboardingSeen(): Boolean
    fun clearAuth()
    /** Где был пользователь (NavMemory): после выгрузки iOS из фона приложение возвращается туда же. */
    fun saveNavState(value: String?) {}
    fun getNavState(): String? = null
}

expect class SessionStorageImpl() : SessionStorage
