package com.aimetry.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun getPlatformDefaultLocale(): Locale {
    val preferredLanguages = NSLocale.preferredLanguages
    if (preferredLanguages.isNotEmpty()) {
        val languageCode = preferredLanguages.firstOrNull() as? String ?: "en"
        val localeCode = languageCode.substringToIndex(minOf(5, languageCode.length))
        
        // Специальная обработка для китайского и португальского
        return when {
            localeCode.startsWith("zh") -> Locale.CHINESE_SIMPLIFIED
            localeCode.startsWith("pt-BR") -> Locale.PORTUGUESE_BR
            else -> {
                val code = localeCode.substringToIndex(minOf(2, localeCode.length))
                Locale.fromCode(code)
            }
        }
    }
    return Locale.ENGLISH
}

