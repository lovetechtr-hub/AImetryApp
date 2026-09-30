package com.djmetry.push

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Контекст приложения для проверки уведомлений — ставит MainActivity. */
object AndroidPushContext {
    internal var context: Context? = null
    fun attach(context: Context) { this.context = context.applicationContext }
}

actual suspend fun systemNotificationsState(): SystemNotifications {
    val ctx = AndroidPushContext.context ?: return SystemNotifications.Unsupported
    return if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) SystemNotifications.On else SystemNotifications.Off
}

actual fun openSystemNotificationSettings() {
    val ctx = AndroidPushContext.context ?: return
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
}
