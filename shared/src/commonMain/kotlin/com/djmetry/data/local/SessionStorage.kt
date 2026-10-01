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
    /** Незавершённый выход (старый токен сессии) — секрет, хранится там же, где токен. */
    fun savePendingSignOut(value: String?) {}
    fun getPendingSignOut(): String? = null
    /**
     * Обычные настройки приложения по ключу (история поиска и т. п.) — не секреты: лежат в открытых
     * настройках платформы. `null` — удалить.
     */
    fun getPref(key: String): String? = null
    fun setPref(key: String, value: String?) {}
    /** Защищённое хранилище временно недоступно (iPhone до первой разблокировки) — токен не «пропал». */
    fun isAuthStorageLocked(): Boolean = false
}

expect class SessionStorageImpl() : SessionStorage
