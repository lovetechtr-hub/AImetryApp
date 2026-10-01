package com.djmetry.auth

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Мост между Activity и OAuth-флоу. MainActivity сообщает сюда о deep-link и о возврате в приложение.
 */
object AndroidOAuthBridge {
    private var activityRef: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<String>? = null
    private var browserOpened = false

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** Вызывать из onCreate/onNewIntent с data из intent. Возвращает true, если это OAuth-редирект. */
    fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != com.djmetry.config.AppConfig.OAUTH_SCHEME) return false
        val deferred = pending
        if (deferred == null) {
            // Вход его не ждал — процесс был выгружен, пока человек был в браузере: завершит AuthRepository
            OAuthCallbacks.offer(uri.toString())
            return true
        }
        pending = null
        deferred.complete(uri.toString())
        return true
    }

    /** Вызывать из onResume: если браузер закрыли без редиректа — считаем вход отменённым. */
    fun onAppResumed() {
        val deferred = pending ?: return
        if (browserOpened) {
            browserOpened = false
            activityRef?.get()?.window?.decorView?.postDelayed({
                if (pending === deferred) {
                    pending = null
                    deferred.completeExceptionally(OAuthCancelledException())
                }
            }, 700)
        }
    }

    internal suspend fun open(url: String): String {
        pending?.completeExceptionally(OAuthCancelledException())
        val deferred = CompletableDeferred<String>().also { pending = it }
        withContext(Dispatchers.Main) {
            val activity = activityRef?.get() ?: throw IllegalStateException("Activity is not attached")
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(0xFF0B1220.toInt())
                .build()
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .build()
                .launchUrl(activity, Uri.parse(url))
            browserOpened = true
        }
        // Отменили (другой провайдер, «Отмена», экран ушёл) — не оставляем «сиротский» ожидающий вход
        return try { deferred.await() } finally { if (pending === deferred) pending = null }
    }
}

internal actual suspend fun authenticateInBrowser(startUrl: String, callbackScheme: String): String =
    AndroidOAuthBridge.open(startUrl)
