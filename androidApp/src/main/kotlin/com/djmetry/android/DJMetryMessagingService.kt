package com.djmetry.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.djmetry.push.PushTokens
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Пуши FCM (docs/BACKEND_API.md → «Пуши»). Новый токен — в общий код, он зарегистрирует его на бэкенде.
 * Приложение в фоне — уведомление показывает система (тап открывает MainActivity с `url` в extras);
 * приложение открыто — система не показывает, рисуем сами с тем же переходом.
 */
class DJMetryMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokens.onNewToken(token, "android")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        show(this, n.title.orEmpty(), n.body.orEmpty(), message.data, message.messageId.hashCode())
    }

    companion object {
        const val CHANNEL_ID = "djmetry_default"
        const val EXTRA_URL = "url"
        /** Маркер «это тап по нашему пушу»: в extras лежат все поля `message.data` (type, request_id, event_id…). */
        const val EXTRA_PUSH = "djmetry_push"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "DJMetry", NotificationManager.IMPORTANCE_HIGH))
            }
        }

        fun show(context: Context, title: String, body: String, data: Map<String, String>, id: Int) {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_PUSH, true)
                data.forEach { (k, v) -> putExtra(k, v) }
            }
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_djmetry)
                .setColor(0xFF5EE6A8.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                nm.notify(id, notification)
            }
        }
    }
}
