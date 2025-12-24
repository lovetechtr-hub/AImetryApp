package com.aimetry.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aimetry.data.local.SessionStorageImpl
import com.aimetry.i18n.LocalizationManager
import com.aimetry.ui.i18n.I18nProvider
import com.aimetry.ui.navigation.AppNavigation
import com.aimetry.ui.theme.AimetryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AimetryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Инициализируем SessionStorage с Context
                    val sessionStorage = remember {
                        SessionStorageImpl().apply {
                            initialize(this@MainActivity)
                        }
                    }
                    
                    val localizationManager = remember {
                        LocalizationManager(sessionStorage)
                    }
                    
                    I18nProvider(localizationManager = localizationManager) {
                        AppNavigation(
                            onNavigateToMain = {
                                // TODO: Navigate to main screen after login
                            }
                        )
                    }
                }
            }
        }
    }
}

