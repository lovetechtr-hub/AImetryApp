package com.djmetry.data.local

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

actual class SessionStorageImpl actual constructor() : SessionStorage {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    init {
        // Keychain переживает удаление приложения, UserDefaults — нет: первый запуск после установки
        // начинается без входа прошлой установки
        // (обновление приложения — не установка: онбординг уже пройден, вход сохраняем)
        if (!userDefaults.boolForKey(KEY_INSTALLED)) {
            if (!userDefaults.boolForKey(KEY_ONBOARDING)) {
                Keychain.set(KEY_TOKEN, null)
                Keychain.set(KEY_PENDING, null)
            }
            userDefaults.setBool(true, KEY_INSTALLED)
        }
    }

    override fun saveAuthToken(token: String?) = Keychain.set(KEY_TOKEN, token)

    override fun getAuthToken(): String? = Keychain.get(KEY_TOKEN)

    override fun saveLocale(locale: String) = userDefaults.setObject(locale, KEY_LOCALE)

    override fun getLocale(): String? = userDefaults.stringForKey(KEY_LOCALE)

    override fun setOnboardingSeen() = userDefaults.setBool(true, KEY_ONBOARDING)

    override fun isOnboardingSeen(): Boolean = userDefaults.boolForKey(KEY_ONBOARDING)

    override fun clearAuth() = saveAuthToken(null)

    override fun saveNavState(value: String?) = if (value == null) userDefaults.removeObjectForKey(KEY_NAV) else userDefaults.setObject(value, KEY_NAV)

    override fun getNavState(): String? = userDefaults.stringForKey(KEY_NAV)

    override fun savePendingSignOut(value: String?) = Keychain.set(KEY_PENDING, value)

    override fun getPendingSignOut(): String? = Keychain.get(KEY_PENDING)

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_LOCALE = "djmetry_locale"
        const val KEY_ONBOARDING = "onboarding_seen"
        const val KEY_NAV = "nav_state"
        const val KEY_PENDING = "pending_signout"
        const val KEY_INSTALLED = "installed_marker"
    }
}

/**
 * Минимальная обёртка над Keychain (kSecClassGenericPassword) для одной строки на ключ.
 * Запись — обновлением существующего элемента (без окна «удалили, но не добавили»); всё, что мы retain-им
 * для словарей запроса, отпускаем — словарь сам держит свои значения.
 */
@OptIn(ExperimentalForeignApi::class)
private object Keychain {
    private const val SERVICE = "com.djmetry.ios"

    private fun query(key: String, fill: (CFMutableDictionaryRef?, MutableList<CFTypeRef?>) -> Unit = { _, _ -> }): Pair<CFMutableDictionaryRef?, List<CFTypeRef?>> {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val retained = mutableListOf<CFTypeRef?>()
        val service = CFBridgingRetain(SERVICE).also { retained += it }
        val account = CFBridgingRetain(key).also { retained += it }
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, service)
        CFDictionaryAddValue(dict, kSecAttrAccount, account)
        fill(dict, retained)
        return dict to retained
    }

    private fun release(q: Pair<CFMutableDictionaryRef?, List<CFTypeRef?>>) {
        q.second.forEach { it?.let(::CFRelease) }
        q.first?.let(::CFRelease)
    }

    fun set(key: String, value: String?) {
        if (value == null) {
            val q = query(key); SecItemDelete(q.first); release(q)
            return
        }
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val match = query(key)
        val attrs = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val dataRef = CFBridgingRetain(data)
        CFDictionaryAddValue(attrs, kSecValueData, dataRef)
        val status = SecItemUpdate(match.first, attrs)
        CFRelease(attrs)
        release(match)
        if (status == errSecItemNotFound) {
            val add = query(key) { dict, _ ->
                CFDictionaryAddValue(dict, kSecValueData, dataRef)
                CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            }
            SecItemAdd(add.first, null)
            release(add)
        }
        dataRef?.let(::CFRelease)
    }

    fun get(key: String): String? = memScoped {
        val q = query(key) { dict, _ ->
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q.first, result.ptr)
        release(q)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }
}
