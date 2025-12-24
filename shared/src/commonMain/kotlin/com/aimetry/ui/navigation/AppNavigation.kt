package com.aimetry.ui.navigation

import androidx.compose.runtime.*
import com.aimetry.domain.model.LoginType
import com.aimetry.ui.screens.*

@Composable
fun AppNavigation(
    onNavigateToMain: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    
    when (currentScreen) {
        is Screen.Splash -> {
            SplashScreen(
                onSplashComplete = {
                    currentScreen = Screen.Onboarding
                },
                modifier = modifier
            )
        }
        
        is Screen.Onboarding -> {
            OnboardingContainer(
                onComplete = {
                    currentScreen = Screen.LoginType
                },
                modifier = modifier
            )
        }
        
        is Screen.LoginType -> {
            LoginTypeScreen(
                onSelectUserLogin = {
                    currentScreen = Screen.Login(LoginType.User)
                },
                onSelectArtistLogin = {
                    currentScreen = Screen.Login(LoginType.Artist)
                },
                modifier = modifier
            )
        }
        
        is Screen.Login -> {
            LoginScreen(
                loginType = (currentScreen as Screen.Login).loginType,
                onOAuthClick = { /* TODO: Handle OAuth */ },
                onEmailLogin = { _, _ -> /* TODO: Handle email login */ },
                onRegister = { _, _, _ -> /* TODO: Handle register */ },
                onBack = {
                    currentScreen = Screen.LoginType
                },
                modifier = modifier
            )
        }
        
        else -> {
            // Fallback to main screen
            onNavigateToMain()
        }
    }
}

