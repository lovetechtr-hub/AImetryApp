package com.djmetry.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Окно для листа входа. `keyWindow` устарел при сценах SwiftUI и на iPad (Split View, Stage Manager) бывает nil —
 * берём ключевое окно активной сцены, иначе любое окно сцены.
 */
private fun activeWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
    val active = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    val windows = active?.windows?.mapNotNull { it as? UIWindow }.orEmpty()
    return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIApplication.sharedApplication.keyWindow
}

private class PresentationContext : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        activeWindow() ?: ASPresentationAnchor()
}

// Сессию и контекст держим в памяти, пока идёт вход: иначе их соберёт GC и окно закроется
private var activeSession: ASWebAuthenticationSession? = null
private val presentationContext = PresentationContext()

internal actual suspend fun authenticateInBrowser(startUrl: String, callbackScheme: String): String =
    suspendCancellableCoroutine { cont ->
        val url = NSURL.URLWithString(startUrl) ?: run {
            cont.resumeWithException(IllegalArgumentException("Bad OAuth URL"))
            return@suspendCancellableCoroutine
        }
        val session = ASWebAuthenticationSession(url, callbackScheme) { callbackUrl, error ->
            activeSession = null
            if (!cont.isActive) return@ASWebAuthenticationSession
            when {
                callbackUrl?.absoluteString != null -> cont.resume(callbackUrl.absoluteString!!)
                error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> cont.resumeWithException(OAuthCancelledException())
                else -> cont.resumeWithException(Exception(error?.localizedDescription ?: "OAuth failed"))
            }
        }
        session.presentationContextProvider = presentationContext
        session.prefersEphemeralWebBrowserSession = false
        activeSession = session
        // Отмена (кнопка «Отменить», другой провайдер) — закрыть лист на главном потоке
        cont.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) { session.cancel() } }
        // Лист не открылся (нет окна, уже идёт другая сессия) — completion не придёт никогда: не вечная крутилка
        if (!session.start()) {
            activeSession = null
            cont.resumeWithException(Exception("Sign-in window could not be opened"))
        }
    }
