package com.aimetry.data.local

import android.content.Context
import android.content.SharedPreferences

actual class SessionStorageImpl actual constructor() : SessionStorage {
    
    // Note: In a real app, you would inject Context via dependency injection
    // For now, this is a placeholder that needs Context injection
    private var context: Context? = null
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
    
    private val prefs: SharedPreferences?
        get() = context?.getSharedPreferences(
            "aimetry_session",
            Context.MODE_PRIVATE
        )
    
    override fun saveSessionId(sessionId: String?) {
        prefs?.edit()?.putString("session_id", sessionId)?.apply()
    }
    
    override fun getSessionId(): String? {
        return prefs?.getString("session_id", null)
    }
    
    override fun saveLocale(locale: String) {
        prefs?.edit()?.putString("aimetry_locale", locale)?.apply()
    }
    
    override fun getLocale(): String? {
        return prefs?.getString("aimetry_locale", null)
    }
    
    override fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}

