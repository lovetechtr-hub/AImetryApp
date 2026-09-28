package com.djmetry.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.djmetry.LocalAppContainer
import com.djmetry.data.repository.SessionState
import com.djmetry.ui.screens.*

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val session by container.auth.session.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var splashDone by remember { mutableStateOf(false) }

    // Сессию восстанавливаем параллельно со сплэшем
    LaunchedEffect(Unit) { container.auth.restore() }
    LaunchedEffect(splashDone, session) {
        if (splashDone && screen == Screen.Splash && session != SessionState.Unknown) {
            screen = startDestination(session is SessionState.SignedIn, container.storage.isOnboardingSeen())
        }
    }
    // Токен истёк или вышли на другом устройстве → на вход
    LaunchedEffect(session) {
        if (session == SessionState.SignedOut && screen == Screen.Main) screen = Screen.Login
    }

    Crossfade(targetState = screen, modifier = modifier, label = "screen") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onSplashComplete = { splashDone = true })
            Screen.Onboarding -> OnboardingContainer(onComplete = {
                container.storage.setOnboardingSeen()
                screen = Screen.Login
            })
            Screen.Login -> LoginScreen(onSignedIn = { screen = Screen.Main })
            Screen.Main -> MainShell(
                me = (session as? SessionState.SignedIn)?.me,
                onLoggedOut = { screen = Screen.Login },
            )
        }
    }
}
