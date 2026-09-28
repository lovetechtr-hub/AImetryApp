package com.djmetry

import androidx.compose.ui.window.ComposeUIViewController
import com.djmetry.data.local.SessionStorageImpl
import platform.UIKit.UIViewController

private val container by lazy { AppContainer(SessionStorageImpl()) }

/** Точка входа для iOS: SwiftUI оборачивает этот контроллер (см. iosApp/DJMetryApp/ContentView.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { DJMetryApp(container) }
