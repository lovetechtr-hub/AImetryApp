package com.djmetry.i18n

actual fun getPlatformDefaultLocale(): Locale {
    val system = java.util.Locale.getDefault()
    return when {
        system.language == "zh" -> Locale.CHINESE_SIMPLIFIED
        system.language == "pt" && system.country == "BR" -> Locale.PORTUGUESE_BR
        else -> Locale.fromCode(system.language)
    }
}
