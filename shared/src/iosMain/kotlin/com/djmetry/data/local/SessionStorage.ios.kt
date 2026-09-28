package com.djmetry.data.local

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

actual class SessionStorageImpl actual constructor() : SessionStorage {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    override fun saveAuthToken(token: String?) = Keychain.set(KEY_TOKEN, token)

    override fun getAuthToken(): String? = Keychain.get(KEY_TOKEN)

    override fun saveLocale(locale: String) = userDefaults.setObject(locale, KEY_LOCALE)

    override fun getLocale(): String? = userDefaults.stringForKey(KEY_LOCALE)

    override fun setOnboardingSeen() = userDefaults.setBool(true, KEY_ONBOARDING)

    override fun isOnboardingSeen(): Boolean = userDefaults.boolForKey(KEY_ONBOARDING)

    override fun clearAuth() = saveAuthToken(null)

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_LOCALE = "djmetry_locale"
        const val KEY_ONBOARDING = "onboarding_seen"
    }
}

/** Минимальная обёртка над Keychain (kSecClassGenericPassword) для одной строки на ключ. */
@OptIn(ExperimentalForeignApi::class)
private object Keychain {
    private const val SERVICE = "com.djmetry.ios"

    private fun query(key: String, fill: (CFMutableDictionaryRef?) -> Unit = {}): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFBridgingRetain(key))
        fill(dict)
        return dict
    }

    fun set(key: String, value: String?) {
        val deleteQuery = query(key)
        SecItemDelete(deleteQuery)
        CFRelease(deleteQuery)
        if (value == null) return
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val addQuery = query(key) { dict ->
            CFDictionaryAddValue(dict, kSecValueData, CFBridgingRetain(data))
            CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        }
        SecItemAdd(addQuery, null)
        CFRelease(addQuery)
    }

    fun get(key: String): String? = memScoped {
        val readQuery = query(key) { dict ->
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(readQuery, result.ptr)
        CFRelease(readQuery)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }
}
