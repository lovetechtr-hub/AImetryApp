//
//  PushAppDelegate.swift
//  DJMetryApp
//
//  Пуши (docs/BACKEND_API.md → «Пуши»): бэкенд шлёт через FCM, на iOS Firebase доставляет их через APNs.
//  Здесь только нативная часть: Firebase, разрешение на уведомления, FCM-токен и тап по уведомлению.
//  Регистрация токена на бэкенде (POST /api/push/devices) — в общем Kotlin-коде (PushTokens / PushRegistrar).
//

import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

final class PushAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Без GoogleService-Info.plist (например, в чужой сборке) Firebase не поднимаем — приложение работает без пушей
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return true }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        // APNs-токен регистрируем всегда: FCM-токен нужен даже до ответа пользователя
        application.registerForRemoteNotifications()

        // Холодный старт по тапу на уведомление
        if let info = launchOptions?[.remoteNotification] as? [AnyHashable: Any] { open(info) }
        return true
    }

    // APNs-токен — в Firebase (он свяжет его с FCM-токеном)
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // MARK: MessagingDelegate — новый или обновлённый FCM-токен → общий код
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken, !token.isEmpty else { return }
        DispatchQueue.main.async { PushTokens.shared.onNewToken(token: token, platform: "ios") }
    }

    // MARK: UNUserNotificationCenterDelegate
    // Приложение открыто — всё равно показываем баннер
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    // Тап по уведомлению — data.url в общий код, он откроет карточку артиста или страницу
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        open(response.notification.request.content.userInfo)
        completionHandler()
    }

    private func open(_ userInfo: [AnyHashable: Any]) {
        guard let url = userInfo["url"] as? String else { return }
        DispatchQueue.main.async { PushTokens.shared.onNotificationOpened(url: url) }
    }
}
