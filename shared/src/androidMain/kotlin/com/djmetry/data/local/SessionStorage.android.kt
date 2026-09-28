package com.djmetry.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SessionStorageImpl actual constructor() : SessionStorage {

    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    private val prefs: SharedPreferences?
        get() = context?.getSharedPreferences("djmetry_session", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences? by lazy {
        val ctx = context ?: return@lazy null
        val masterKey = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx,
            "djmetry_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveAuthToken(token: String?) {
        securePrefs?.edit()?.apply { if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token) }?.apply()
    }

    override fun getAuthToken(): String? = securePrefs?.getString(KEY_TOKEN, null)

    override fun saveLocale(locale: String) {
        prefs?.edit()?.putString(KEY_LOCALE, locale)?.apply()
    }

    override fun getLocale(): String? = prefs?.getString(KEY_LOCALE, null)

    override fun setOnboardingSeen() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING, true)?.apply()
    }

    override fun isOnboardingSeen(): Boolean = prefs?.getBoolean(KEY_ONBOARDING, false) ?: false

    override fun clearAuth() = saveAuthToken(null)

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_LOCALE = "djmetry_locale"
        const val KEY_ONBOARDING = "onboarding_seen"
    }
}
