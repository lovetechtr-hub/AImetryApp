package com.djmetry.push

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual suspend fun systemNotificationsState(): SystemNotifications = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        val on = when (settings?.authorizationStatus) {
            UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> true
            else -> false
        }
        cont.resume(if (on) SystemNotifications.On else SystemNotifications.Off)
    }
}

actual fun openSystemNotificationSettings() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.getNotificationSettingsWithCompletionHandler { settings ->
        if (settings?.authorizationStatus == UNAuthorizationStatusNotDetermined) {
            // Ещё не спрашивали — системный запрос вместо настроек
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound) { _, _ -> }
        } else {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
                }
            }
        }
    }
}
