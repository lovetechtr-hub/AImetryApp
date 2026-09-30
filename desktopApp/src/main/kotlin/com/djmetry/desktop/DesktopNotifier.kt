package com.djmetry.desktop

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * Системные уведомления на десктопе: macOS — Центр уведомлений, Windows — всплывающее, Linux — через трей.
 * Нужен значок в трее/строке меню (так устроен AWT). Клик по уведомлению или значку — [onOpen] с ссылкой
 * последнего уведомления: окно выходит вперёд, приложение открывает карточку артиста или страницу.
 */
object DesktopNotifier {
    private var tray: TrayIcon? = null
    private var lastUrl: String? = null
    var onOpen: (String?) -> Unit = {}

    private fun ensureTray(): TrayIcon? {
        tray?.let { return it }
        if (!SystemTray.isSupported()) return null
        val image = javaClass.getResource("/djmetry.png")?.let { Toolkit.getDefaultToolkit().getImage(it) } ?: return null
        return runCatching {
            TrayIcon(image, "DJMetry").apply {
                isImageAutoSize = true
                addActionListener { onOpen(lastUrl) }
                SystemTray.getSystemTray().add(this)
            }
        }.getOrNull()?.also { tray = it }
    }

    fun show(title: String, body: String, url: String?) {
        val icon = ensureTray() ?: return
        lastUrl = url
        icon.displayMessage(title, body, TrayIcon.MessageType.NONE)
    }

    fun dispose() {
        tray?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        tray = null
    }
}
