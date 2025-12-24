package com.aimetry.ui.i18n

import androidx.compose.runtime.*
import com.aimetry.i18n.LocalizationManager
import com.aimetry.i18n.Locale

private val LocalLocalizationManager = compositionLocalOf<LocalizationManager> {
    error("No LocalizationManager provided")
}

/**
 * Composable функция для получения доступа к локализации
 */
@Composable
fun useI18n(): I18nContext {
    val manager = LocalLocalizationManager.current
    val currentLocale by manager.currentLocale.collectAsState()
    
    return remember(manager, currentLocale) {
        I18nContext(
            locale = currentLocale,
            setLocale = { manager.setLocale(it) },
            t = { keyStr -> manager.getString(keyStr) },
            tWithArgs = { keyStr: String, args: Array<out Any> -> manager.getString(keyStr, *args) }
        )
    }
}

/**
 * Provider для локализации
 */
@Composable
fun I18nProvider(
    localizationManager: LocalizationManager? = null,
    content: @Composable () -> Unit
) {
    val manager = localizationManager ?: remember { 
        LocalizationManager(com.aimetry.data.local.SessionStorageImpl())
    }
    
    CompositionLocalProvider(LocalLocalizationManager provides manager) {
        content()
    }
}

/**
 * Context для работы с локализацией
 */
data class I18nContext(
    val locale: Locale,
    val setLocale: (Locale) -> Unit,
    val t: (String) -> String,
    val tWithArgs: (String, Array<out Any>) -> String
)

/**
 * Composable для изменения локали
 */
@Composable
fun LocalizedText(
    key: String,
    vararg args: Any,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    style: androidx.compose.ui.text.TextStyle? = null,
    color: androidx.compose.ui.graphics.Color? = null,
    content: @Composable (String) -> Unit
) {
    val i18n = useI18n()
    val text = if (args.isEmpty()) {
        i18n.t(key)
    } else {
        i18n.tWithArgs(key, args)
    }
    
    content(text)
}

