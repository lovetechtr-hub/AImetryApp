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
    // Недавно были в приложении (iOS выгрузил его из фона) — без сплэша, на тот же экран
    val restored = remember {
        com.djmetry.data.local.NavMemory.attach(container.storage)
        com.djmetry.data.local.NavMemory.restore()?.takeIf { container.storage.getAuthToken() != null }
    }
    var splashDone by remember { mutableStateOf(restored != null) }

    // Сессию восстанавливаем параллельно со сплэшем
    LaunchedEffect(Unit) { container.auth.restore() }
    // Пуши: токен устройства регистрируется за вошедшим пользователем, пока приложение открыто
    LaunchedEffect(Unit) { container.push.run(com.djmetry.push.PushTokens.token, container.auth.session) }
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
                onLoggedOut = { com.djmetry.data.local.NavMemory.forget(); screen = Screen.Login },
                initialTab = restored?.tab?.let { t -> MainTab.entries.firstOrNull { it.name == t } } ?: MainTab.Discover,
                initialArtistId = restored?.artistId,
            )
        }
    }
}
