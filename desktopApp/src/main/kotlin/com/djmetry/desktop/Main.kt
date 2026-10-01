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

    // macOS: ссылка приходит событием УЖЕ после старта процесса. Ловим её до проверки «второй копии»: если браузер
    // запустил другую копию DJMetry.app (сборка, временная папка jpackage), она перешлёт ссылку открытому окну,
    // а не выйдет молча (так терялся вход через Google — кнопка «Открыть DJMetry» ничего не делала)
    val primary = java.util.concurrent.atomic.AtomicBoolean(false)
    val early = java.util.concurrent.LinkedBlockingQueue<String>()
    val macLinks = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)
    if (macLinks) {
        Desktop.getDesktop().setOpenURIHandler { event ->
            val uri = event.uri.toString()
            if (primary.get()) DesktopDeepLinks.deliver(uri) else early.offer(uri)
        }
    }

    val instance = SingleInstance(File(System.getProperty("user.home"), ".djmetry"))
    if (!instance.acquireOrForward(deepLink) { DesktopDeepLinks.deliver(it) }) {
        if (macLinks && deepLink == null) early.poll(3, java.util.concurrent.TimeUnit.SECONDS)?.let(instance::forward)
        exitProcess(0) // ссылка передана уже открытому окну
    }
    primary.set(true)
    deepLink?.let(DesktopDeepLinks::deliver)
    generateSequence { early.poll() }.forEach { DesktopDeepLinks.deliver(it) }
    UrlSchemeRegistration.ensureRegistered()
    DesktopMapRuntime.configure() // кэш карты аналитики — до первой карты

    val container = AppContainer(SessionStorageImpl())

    // Окно — по размеру экрана: ноутбук 1366×768 с масштабом 125% меньше, чем 1280×840 (низ с вкладками уходил за край)
    val screen = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
    val startW = screen?.let { minOf(1280, (it.width * 0.9).toInt()) } ?: 1280
    val startH = screen?.let { minOf(840, (it.height * 0.9).toInt()) } ?: 840

    application {
        val state = rememberWindowState(size = DpSize(startW.dp, startH.dp))
        Window(
            onCloseRequest = { DesktopNotifier.dispose(); instance.release(); exitApplication() },
            state = state,
            title = "DJMetry",
            icon = painterResource("djmetry.png"),
        ) {
            window.minimumSize = Dimension(380, minOf(640, screen?.height ?: 640))
            // Вернулись из браузера после входа — окно вперёд (Windows и Linux оставляли его за браузером)
            DesktopDeepLinks.onReceived = {
                java.awt.EventQueue.invokeLater { window.isVisible = true; window.toFront(); window.requestFocus() }
            }
            // Пуши на десктопе: SSE-поток, пока приложение открыто; клик по уведомлению — окно вперёд и переход
            DesktopNotifier.onOpen = { url, data ->
                java.awt.EventQueue.invokeLater { window.isVisible = true; window.toFront(); window.requestFocus() }
                com.djmetry.push.PushTokens.onNotificationOpened(url, data)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                container.notificationStream.run(container.auth.session) { n ->
                    DesktopNotifier.show(n.title ?: "DJMetry", n.body.orEmpty(), n.url, n.pushData)
                    container.notifications.refreshUnread() // бейдж колокольчика
                }
            }
            DesktopMapHost(window) { DJMetryApp(container) }
        }
    }
}
