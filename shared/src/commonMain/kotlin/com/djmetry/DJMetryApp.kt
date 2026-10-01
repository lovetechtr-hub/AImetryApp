package com.djmetry

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.djmetry.ui.components.ShimmerProvider
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.navigation.AppNavigation
import com.djmetry.ui.theme.DJMetryTheme

/** Корень UI — общий для Android и iOS. */
/** Предел системного масштаба шрифта в приложении (docs/RULES.md §3). */
const val MAX_FONT_SCALE = 1.2f

@Composable
fun DJMetryApp(container: AppContainer) {
    // Системный крупный шрифт (iOS Dynamic Type, Android «Размер шрифта») учитываем, но не больше ×MAX_FONT_SCALE:
    // на iPhone с крупным шрифтом (~×1.45) подписи вылезали из капсул и плиток. Вёрстка проверяется при ×1.3.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE)),
    ) {
        DJMetryTheme {
            I18nProvider(localizationManager = container.localization) {
                // Тап мимо поля ввода — клавиатура прячется (на всех экранах и платформах)
                com.djmetry.ui.components.DismissKeyboardOnTap(androidx.compose.ui.Modifier.fillMaxSize()) { ShimmerProvider { AppNavigation() } }
            }
        }
    }
}
