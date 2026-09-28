package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import com.djmetry.config.AppConfig
import com.djmetry.domain.model.OAuthProvider
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/** Авторизация: только OAuth (Google / Apple / Facebook). Email/пароля на бэкенде нет. */
class AuthApi(private val http: HttpClient) {

    /** URL для системного браузера: GET /api/auth/:provider/start?mobile=1&code_challenge&app_redirect */
    fun mobileStartUrl(provider: OAuthProvider, codeChallenge: String, appRedirect: String = AppConfig.OAUTH_REDIRECT): String =
        URLBuilder(AppConfig.apiUrl + "/auth/${provider.value}/start").apply {
            parameters.append("mobile", "1")
            parameters.append("code_challenge", codeChallenge)
            parameters.append("app_redirect", appRedirect)
        }.buildString()

    /** POST /api/auth/mobile/token — одноразовый код + PKCE verifier → Bearer-токен на 30 дней. */
    suspend fun exchangeCode(code: String, codeVerifier: String): Result<MobileTokenResponse> =
        apiCall { http.post("auth/mobile/token") { setBody(MobileTokenRequest(code, codeVerifier)) } }

    /** POST /api/logout — инвалидирует токен. */
    suspend fun logout(): Result<SuccessResponse> = apiCall { http.post("logout") }

    /** DELETE /api/me — удаление аккаунта (soft-delete). */
    suspend fun deleteAccount(reason: String? = null): Result<SuccessResponse> =
        apiCall { http.delete("me") { setBody(DeleteAccountRequest(reason = reason)) } }
}
