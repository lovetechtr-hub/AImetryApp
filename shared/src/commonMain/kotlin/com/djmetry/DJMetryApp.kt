package com.djmetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.navigation.AppNavigation
import com.djmetry.ui.theme.DJMetryTheme

/** Корень UI — общий для Android и iOS. */
@Composable
fun DJMetryApp(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        DJMetryTheme {
            I18nProvider(localizationManager = container.localization) {
                AppNavigation()
            }
        }
    }
}
