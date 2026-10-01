package com.djmetry.i18n

import com.djmetry.data.local.SessionStorage
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
    
    fun getString(key: String, vararg args: Any): String = formatTemplate(getString(key), *args)

    companion object {
        const val LOCALE_STORAGE_KEY = "djmetry_locale"
    }
}

/**
 * Подстановка аргументов: позиционные `%1$s`/`%2$d` — по номеру, простые `%s`/`%d` — по очереди
 * («%s из %s» → «3 из 7», а не «3 из 3»). `%%` — знак процента.
 */
internal fun formatTemplate(template: String, vararg args: Any): String {
    var next = 0
    return Regex("""%(?:(\d+)\$)?([sd%])""").replace(template) { m ->
        when {
            m.groupValues[2] == "%" -> "%"
            m.groupValues[1].isNotEmpty() -> args.getOrNull(m.groupValues[1].toInt() - 1)?.toString() ?: m.value
            else -> args.getOrNull(next++)?.toString() ?: m.value
        }
    }
}
