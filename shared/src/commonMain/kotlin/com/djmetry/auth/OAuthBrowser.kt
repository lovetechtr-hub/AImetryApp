package com.djmetry.auth

/** Пользователь закрыл окно входа, не завершив авторизацию. */
class OAuthCancelledException : Exception("OAuth cancelled")

/**
 * Открывает [startUrl] в системном браузере (Custom Tab / ASWebAuthenticationSession)
 * и возвращает URL, на который бэкенд перенаправил в приложение (`djmetry://oauth?...`).
 * Бросает [OAuthCancelledException], если пользователь закрыл окно.
 */
// Реализован на Android и iOS; десктоп использует свои стратегии (DesktopOAuth.kt).
internal expect suspend fun authenticateInBrowser(startUrl: String, callbackScheme: String): String
