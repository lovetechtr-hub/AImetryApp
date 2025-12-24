package com.aimetry

import androidx.compose.ui.window.ComposeUIViewController
import com.aimetry.data.local.SessionStorageImpl
import com.aimetry.i18n.LocalizationManager
import com.aimetry.ui.i18n.I18nProvider
import com.aimetry.ui.navigation.AppNavigation
import com.aimetry.ui.theme.AimetryTheme
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController =
    ComposeUIViewController {
        AimetryTheme {
            // Инициализируем SessionStorage и LocalizationManager для iOS
            val sessionStorage = SessionStorageImpl()
            val localizationManager = LocalizationManager(sessionStorage)
            
            I18nProvider(localizationManager = localizationManager) {
                AppNavigation(
                    onNavigateToMain = {
                        // TODO: Navigate to main screen after login
                    }
                )
            }
        }
    }

