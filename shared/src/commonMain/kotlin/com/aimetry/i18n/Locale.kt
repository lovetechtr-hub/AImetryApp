package com.aimetry.i18n

enum class Locale(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    RUSSIAN("ru", "Русский"),
    UKRAINIAN("uk", "Українська"),
    TURKISH("tr", "Türkçe"),
    JAPANESE("ja", "日本語"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    PORTUGUESE_BR("pt-BR", "Português (BR)"),
    ITALIAN("it", "Italiano"),
    KOREAN("ko", "한국어");

    companion object {
        fun fromCode(code: String): Locale {
            return values().find { it.code == code } ?: ENGLISH
        }
        
        fun getDefaultLocale(): Locale {
            // Получим дефолтный язык из platform-specific кода
            return getPlatformDefaultLocale()
        }
    }
}

expect fun getPlatformDefaultLocale(): Locale

