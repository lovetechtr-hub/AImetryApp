package com.djmetry.push

/** Разрешены ли уведомления DJMetry в системе. [Unsupported] — платформа без пушей (десктоп). */
enum class SystemNotifications { On, Off, Unsupported }

/** Состояние системного разрешения: Android — настройки приложения, iOS — UNUserNotificationCenter. */
expect suspend fun systemNotificationsState(): SystemNotifications

/** Открыть системные настройки уведомлений DJMetry (iOS: если ещё не спрашивали — сначала запрос). */
expect fun openSystemNotificationSettings()
