package com.aimetry.ui.i18n

import androidx.compose.runtime.*
import com.aimetry.i18n.LocalizationManager
import com.aimetry.i18n.Locale

/**
 * Composable функция для получения доступа к локализации
 * В реальном приложении LocalizationManager должен быть предоставлен через DI/CompositionLocal
 */
@Composable
fun useI18n(localizationManager: LocalizationManager? = null): I18nContext {
    // В реальном приложении это должно быть через CompositionLocal или DI
    val manager = localizationManager ?: remember { 
        LocalizationManager(com.aimetry.data.local.SessionStorageImpl())
    }
    
    val currentLocale by manager.currentLocale.collectAsState()
    
    return remember(manager, currentLocale) {
        I18nContext(
            locale = currentLocale,
            setLocale = { manager.setLocale(it) },
            t = { key -> manager.getString(key) },
            tWithArgs = { key, vararg args -> manager.getString(key, *args) }
        )
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
) {
    fun t(key: String, vararg args: Any): String {
        return if (args.isEmpty()) {
            t(key)
        } else {
            tWithArgs(key, args)
        }
    }
}

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
        i18n.t(key, *args)
    }
    
    content(text)
}

