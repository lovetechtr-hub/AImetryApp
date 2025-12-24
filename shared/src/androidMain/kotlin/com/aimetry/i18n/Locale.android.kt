package com.aimetry.i18n

import android.os.Build
import java.util.*

actual fun getPlatformDefaultLocale(): Locale {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        java.util.Locale.getDefault()
    }
    
    val languageCode = locale.language
    val countryCode = locale.country
    
    // Сопоставляем системную локаль с поддерживаемыми
    val fullCode = if (countryCode.isNotEmpty()) {
        "$languageCode-$countryCode"
    } else {
        languageCode
    }
    
    // Специальная обработка для китайского и португальского
    return when {
        fullCode.startsWith("zh") -> Locale.CHINESE_SIMPLIFIED
        fullCode.startsWith("pt-BR") -> Locale.PORTUGUESE_BR
        else -> Locale.fromCode(languageCode)
    }
}

