package com.djmetry.auth

import com.djmetry.config.AppConfig

/**
 * Способ получить OAuth-редирект от бэкенда (docs/BACKEND_API.md, docs/RULES.md).
 *
 * Стратегия сама решает, какой `app_redirect` передать в `/api/auth/:provider/start`,
 * открывает браузер и ждёт возврата. Возвращает полный URL редиректа (`…?code=` или `…?error=`).
 */
fun interface OAuthRedirect {
    /** [startUrlFor] строит URL старта входа для выбранного `app_redirect`. */
    suspend fun authorize(startUrlFor: (appRedirect: String) -> String): String
}

/**
 * Кастомная схема `djmetry://oauth`: телефоны (Custom Tabs / ASWebAuthenticationSession)
 * и установленное десктоп-приложение.
 */
class CustomSchemeRedirect(
    private val appRedirect: String = AppConfig.OAUTH_REDIRECT,
    private val open: suspend (startUrl: String, callbackScheme: String) -> String,
) : OAuthRedirect {
    override suspend fun authorize(startUrlFor: (String) -> String): String =
        open(startUrlFor(appRedirect), AppConfig.OAUTH_SCHEME)
}

/** Стратегия по умолчанию для платформы. */
internal expect fun platformOAuthRedirect(): OAuthRedirect
