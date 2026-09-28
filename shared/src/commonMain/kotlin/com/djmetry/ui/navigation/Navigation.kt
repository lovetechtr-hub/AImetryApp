package com.djmetry.ui.navigation

sealed class Screen {
    data object Splash : Screen()
    data object Onboarding : Screen()
    data object Login : Screen()
    data object Main : Screen()
}

/** Куда вести после сплэша: вошёл → главный; первый запуск → онбординг; иначе → вход. */
internal fun startDestination(isSignedIn: Boolean, onboardingSeen: Boolean): Screen = when {
    isSignedIn -> Screen.Main
    !onboardingSeen -> Screen.Onboarding
    else -> Screen.Login
}
