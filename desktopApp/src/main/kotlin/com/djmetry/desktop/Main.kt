package com.djmetry.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.djmetry.AppContainer
import com.djmetry.DJMetryApp
import com.djmetry.auth.DesktopDeepLinks
import com.djmetry.data.local.SessionStorageImpl
import java.awt.Desktop
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Windows/Linux: ОС запускает приложение с djmetry://… в аргументах
    val deepLink = args.firstOrNull { it.startsWith("${UrlSchemeRegistration.SCHEME}://") }

    val instance = SingleInstance(File(System.getProperty("user.home"), ".djmetry"))
    if (!instance.acquireOrForward(deepLink) { DesktopDeepLinks.deliver(it) }) {
        exitProcess(0) // ссылка передана уже открытому окну
    }
    deepLink?.let(DesktopDeepLinks::deliver)

    // macOS: ссылки приходят событием в работающий процесс
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
        Desktop.getDesktop().setOpenURIHandler { event -> DesktopDeepLinks.deliver(event.uri.toString()) }
    }
    UrlSchemeRegistration.ensureRegistered()
    DesktopMapRuntime.configure() // кэш карты аналитики — до первой карты

    val container = AppContainer(SessionStorageImpl())

    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 840.dp))
        Window(
            onCloseRequest = { instance.release(); exitApplication() },
            state = state,
            title = "DJMetry",
            icon = painterResource("djmetry.png"),
        ) {
            window.minimumSize = Dimension(380, 640)
            DJMetryApp(container)
        }
    }
}
