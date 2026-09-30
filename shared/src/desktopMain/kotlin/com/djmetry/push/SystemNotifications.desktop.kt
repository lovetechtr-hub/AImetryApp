package com.djmetry.push

/** Десктоп: транспорта пушей пока нет (docs/BACKEND_API.md → «Пуши на десктопе»). */
actual suspend fun systemNotificationsState(): SystemNotifications = SystemNotifications.Unsupported

actual fun openSystemNotificationSettings() = Unit
