//
//  DJMetryApp.swift
//  DJMetryApp
//
//  Created by Viacheslav Loie on 30.12.2025.
//

import SwiftUI

@main
struct DJMetryApp: App {
    // Пуши: Firebase и уведомления живут в AppDelegate (PushAppDelegate.swift)
    @UIApplicationDelegateAdaptor(PushAppDelegate.self) private var pushDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
