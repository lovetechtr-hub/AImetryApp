package com.djmetry.desktop

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * Системные уведомления на десктопе: macOS — Центр уведомлений, Windows — всплывающее, Linux — через трей.
 * Нужен значок в трее/строке меню (так устроен AWT). AWT не сообщает, по какому из уведомлений кликнули:
 * если с прошлого клика пришло одно — [onOpen] ведёт на него; если несколько — только окно вперёд
 * (раньше клик по старому уведомлению открывал последнее, чужое).
 */
object DesktopNotifier {
    private var tray: TrayIcon? = null
    private var lastUrl: String? = null
    var onOpen: (String?, Map<String, String>) -> Unit = { _, _ -> }
    private var lastData: Map<String, String> = emptyMap()
    /** Сколько уведомлений показано с прошлого клика. */
    private var unopened = 0

    private fun ensureTray(): TrayIcon? {
        tray?.let { return it }
        if (!SystemTray.isSupported()) return null
        val image = javaClass.getResource("/djmetry.png")?.let { Toolkit.getDefaultToolkit().getImage(it) } ?: return null
        return runCatching {
            TrayIcon(image, "DJMetry").apply {
                isImageAutoSize = true
                addActionListener {
                    if (unopened == 1) onOpen(lastUrl, lastData) else onOpen(null, emptyMap())
                    unopened = 0
                }
                SystemTray.getSystemTray().add(this)
            }
        }.getOrNull()?.also { tray = it }
    }

    fun show(title: String, body: String, url: String?, data: Map<String, String> = emptyMap()) {
        val icon = ensureTray() ?: return
        lastUrl = url
        lastData = data
        unopened++
        icon.displayMessage(title, body, TrayIcon.MessageType.NONE)
    }

    fun dispose() {
        tray?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        tray = null
    }
}
