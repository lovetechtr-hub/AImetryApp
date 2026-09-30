package com.djmetry.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.ISOCountryCodes

actual fun getPlatformDefaultLocale(): Locale {
    val preferredLanguages = NSLocale.preferredLanguages
    if (preferredLanguages.isNotEmpty()) {
        val languageCode = preferredLanguages.firstOrNull() as? String ?: "en"
        val localeCode = languageCode.take(minOf(5, languageCode.length))
        
        // Специальная обработка для китайского и португальского
        return when {
            localeCode.startsWith("zh") -> Locale.CHINESE_SIMPLIFIED
            localeCode.startsWith("pt-BR") -> Locale.PORTUGUESE_BR
            else -> {
                val code = localeCode.take(minOf(2, localeCode.length))
                Locale.fromCode(code)
            }
        }
    }
    return Locale.ENGLISH
}


actual fun localizedCountryName(iso: String, languageTag: String): String? =
    if (iso.uppercase() !in NSLocale.ISOCountryCodes) null else platform.Foundation.NSLocale(localeIdentifier = languageTag.replace('-', '_'))
        .displayNameForKey(platform.Foundation.NSLocaleCountryCode, value = iso.uppercase())
        ?.takeIf { it.isNotBlank() }
