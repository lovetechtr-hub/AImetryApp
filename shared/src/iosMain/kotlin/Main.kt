package com.djmetry

import androidx.compose.ui.window.ComposeUIViewController
import com.djmetry.data.local.SessionStorageImpl
import com.djmetry.ui.components.RemoteImages
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIViewController

private val container by lazy { AppContainer(SessionStorageImpl()) }

/**
 * Память: iOS выгружает из фона самые «тяжёлые» приложения. На предупреждение о памяти отдаём кэш картинок
 * целиком, при уходе в фон — ужимаем вдвое, чтобы по возвращении приложение было на месте, а не стартовало заново.
 */
private val memoryObservers by lazy {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    listOf(
        center.addObserverForName(UIApplicationDidReceiveMemoryWarningNotification, null, queue) { _ -> RemoteImages.clear() },
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ -> RemoteImages.onBackground(); com.djmetry.data.local.NavMemory.persist() },
    )
}

/** Точка входа для iOS: SwiftUI оборачивает этот контроллер (см. iosApp/DJMetryApp/ContentView.swift). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController {
    memoryObservers
    // parallelRendering: кадр рисуется в отдельном потоке, главный поток свободен для касаний и прокрутки (плавнее на 120 Гц)
    return ComposeUIViewController(configure = { parallelRendering = true }) { DJMetryApp(container) }
}
