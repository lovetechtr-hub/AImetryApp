package com.djmetry.i18n

actual fun getPlatformDefaultLocale(): Locale {
    val system = java.util.Locale.getDefault()
    return when {
        system.language == "zh" -> Locale.CHINESE_SIMPLIFIED
        system.language == "pt" && system.country == "BR" -> Locale.PORTUGUESE_BR
        else -> Locale.fromCode(system.language)
    }
}

actual fun localizedCountryName(iso: String, languageTag: String): String? =
    if (iso.uppercase() !in java.util.Locale.getISOCountries()) null else runCatching { java.util.Locale.Builder().setRegion(iso.uppercase()).build().getDisplayCountry(java.util.Locale.forLanguageTag(languageTag)) }.getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(iso, ignoreCase = true) }
