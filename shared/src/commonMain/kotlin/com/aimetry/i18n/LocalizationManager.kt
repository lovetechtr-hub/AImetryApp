package com.aimetry.i18n

import com.aimetry.data.local.SessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalizationManager(
    private val sessionStorage: SessionStorage
) {
    private val _currentLocale = MutableStateFlow<Locale>(Locale.ENGLISH)
    val currentLocale: StateFlow<Locale> = _currentLocale.asStateFlow()
    
    private val translationsCache = mutableMapOf<Locale, Map<String, String>>()
    
    init {
        loadSavedLocale()
    }
    
    private fun loadSavedLocale() {
        val savedLocaleCode = sessionStorage.getLocale()
        if (savedLocaleCode != null) {
            _currentLocale.value = Locale.fromCode(savedLocaleCode)
        } else {
            _currentLocale.value = Locale.getDefaultLocale()
        }
    }
    
    fun setLocale(locale: Locale) {
        _currentLocale.value = locale
        sessionStorage.saveLocale(locale.code)
    }
    
    fun getString(key: String): String {
        val locale = _currentLocale.value
        val translations = translationsCache.getOrPut(locale) {
            Translations.getTranslations(locale)
        }
        return translations[key] ?: key
    }
    
    fun getString(key: String, vararg args: Any): String {
        val template = getString(key)
        return try {
            template.format(*args)
        } catch (e: Exception) {
            template
        }
    }
    
    companion object {
        const val LOCALE_STORAGE_KEY = "aimetry_locale"
    }
}


