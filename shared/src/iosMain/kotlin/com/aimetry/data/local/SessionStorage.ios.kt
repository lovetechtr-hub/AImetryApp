package com.aimetry.data.local

import platform.Foundation.NSUserDefaults

actual class SessionStorageImpl actual constructor() : SessionStorage {
    
    private val userDefaults = NSUserDefaults.standardUserDefaults
    
    override fun saveSessionId(sessionId: String?) {
        if (sessionId != null) {
            userDefaults.setObject(sessionId, "session_id")
        } else {
            userDefaults.removeObjectForKey("session_id")
        }
        userDefaults.synchronize()
    }
    
    override fun getSessionId(): String? {
        return userDefaults.stringForKey("session_id")
    }
    
    override fun saveLocale(locale: String) {
        userDefaults.setObject(locale, "aimetry_locale")
        userDefaults.synchronize()
    }
    
    override fun getLocale(): String? {
        return userDefaults.stringForKey("aimetry_locale")
    }
    
    override fun clear() {
        userDefaults.removeObjectForKey("session_id")
        userDefaults.removeObjectForKey("aimetry_locale")
        userDefaults.synchronize()
    }
}

