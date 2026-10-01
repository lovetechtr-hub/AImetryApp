package com.djmetry.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
    // Только для первого входа в оболочку: после выхода следующий аккаунт начинает с «Открытий»
    var restored by remember {
        com.djmetry.data.local.NavMemory.attach(container.storage)
        mutableStateOf(com.djmetry.data.local.NavMemory.restore()?.takeIf { container.storage.getAuthToken() != null })
    }
    var splashDone by remember { mutableStateOf(restored != null) }

    // Сессию восстанавливаем параллельно со сплэшем
    // Хранилище ключей заблокировано (iPhone до первой разблокировки) — сессия «неизвестна», пробуем снова
    LaunchedEffect(Unit) {
        var state = container.auth.restore()
        while (state == SessionState.Unknown) { kotlinx.coroutines.delay(2_000); state = container.auth.restore() }
    }
    // Код входа пришёл, а приложение было выгружено (Android во время 2FA) — завершаем вход
    val oauthCallback by com.djmetry.auth.OAuthCallbacks.pending.collectAsState()
    LaunchedEffect(oauthCallback) { com.djmetry.auth.OAuthCallbacks.consume()?.let { container.auth.resumeSignIn(it) } }
    // Запуск без сети: профиль — заглушка; как только сеть есть — настоящий (иначе артист видит себя фанатом)
    LaunchedEffect(session) {
        var wait = 3_000L
        while (container.auth.isStubProfile) { kotlinx.coroutines.delay(wait); container.auth.refreshMe(); wait = (wait * 2).coerceAtMost(60_000) }
    }
    // Приложение снова на экране: свежий профиль и бейдж колокольчика; ушло в фон — запомнить, где были
    // (Android и десктоп раньше этого не делали — после выгрузки возвращались на «Открытия»)
    val lifecycleScope = rememberCoroutineScope()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (session is SessionState.SignedIn) lifecycleScope.launch {
            container.auth.refreshMe()
            container.notifications.refreshUnread()
        }
        onPauseOrDispose { com.djmetry.data.local.NavMemory.persist() }
    }
    // Прошлый выход был без сети — снять пуши и отозвать старую сессию
    LaunchedEffect(Unit) { container.auth.retryPendingSignOut() }
    // Пуши: токен устройства регистрируется за вошедшим пользователем, пока приложение открыто
    LaunchedEffect(Unit) { container.push.run(com.djmetry.push.PushTokens.token, container.auth.session) }
    LaunchedEffect(splashDone, session) {
        if (splashDone && screen == Screen.Splash && session != SessionState.Unknown) {
            screen = startDestination(session is SessionState.SignedIn, container.storage.isOnboardingSeen())
        }
    }
    // Токен истёк или вышли на другом устройстве → на вход
    LaunchedEffect(session) {
        if (session == SessionState.SignedOut && screen == Screen.Main) {
            com.djmetry.data.local.NavMemory.forget()
            restored = null
            screen = Screen.Login
        }
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
                onLoggedOut = { com.djmetry.data.local.NavMemory.forget(); restored = null; screen = Screen.Login },
                initialTab = restored?.tab?.let { t -> MainTab.entries.firstOrNull { it.name == t } } ?: MainTab.Discover,
                initialArtistId = restored?.artistId,
            )
        }
    }
}
