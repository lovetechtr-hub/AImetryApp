package com.djmetry.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class PresentationContext : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
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
            when {
                callbackUrl?.absoluteString != null -> cont.resume(callbackUrl.absoluteString!!)
                error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> cont.resumeWithException(OAuthCancelledException())
                else -> cont.resumeWithException(Exception(error?.localizedDescription ?: "OAuth failed"))
            }
        }
        session.presentationContextProvider = presentationContext
        session.prefersEphemeralWebBrowserSession = false
        activeSession = session
        cont.invokeOnCancellation { session.cancel() }
        session.start()
    }
