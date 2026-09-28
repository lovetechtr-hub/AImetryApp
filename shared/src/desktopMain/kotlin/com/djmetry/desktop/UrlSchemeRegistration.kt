package com.djmetry.desktop

import java.io.File
import java.util.Locale

/**
 * Регистрация схемы `djmetry://` в ОС для установленного приложения.
 * - macOS: делает установщик — `CFBundleURLTypes` в Info.plist (desktopApp/build.gradle.kts), тут ничего не нужно.
 * - Windows: ключи в HKCU\Software\Classes\djmetry (без прав администратора), при каждом запуске —
 *   чтобы путь к exe был актуальным после обновления.
 * - Linux: .desktop-файл с `MimeType=x-scheme-handler/djmetry` + `xdg-mime default`.
 */
object UrlSchemeRegistration {
    const val SCHEME = "djmetry"

    /** Команды `reg add` для Windows. */
    fun windowsCommands(exePath: String): List<List<String>> {
        val root = "HKCU\\Software\\Classes\\$SCHEME"
        return listOf(
            listOf("reg", "add", root, "/ve", "/d", "URL:DJMetry Protocol", "/f"),
            listOf("reg", "add", root, "/v", "URL Protocol", "/d", "", "/f"),
            listOf("reg", "add", "$root\\DefaultIcon", "/ve", "/d", "\"$exePath\",0", "/f"),
            listOf("reg", "add", "$root\\shell\\open\\command", "/ve", "/d", "\"$exePath\" \"%1\"", "/f"),
        )
    }

    /** Содержимое .desktop-файла для Linux. */
    fun linuxDesktopEntry(execPath: String): String = """
        [Desktop Entry]
        Type=Application
        Name=DJMetry
        Exec="$execPath" %u
        Terminal=false
        NoDisplay=true
        MimeType=x-scheme-handler/$SCHEME;
    """.trimIndent() + "\n"

    /** Регистрирует схему, если это установленное приложение на Windows/Linux. Ошибки не роняют запуск. */
    fun ensureRegistered(appPath: String? = System.getProperty("jpackage.app-path")) {
        val path = appPath ?: return
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        runCatching {
            when {
                os.contains("win") -> windowsCommands(path).forEach { ProcessBuilder(it).start().waitFor() }
                os.contains("linux") -> {
                    val apps = File(System.getProperty("user.home"), ".local/share/applications").apply { mkdirs() }
                    File(apps, "djmetry-url.desktop").writeText(linuxDesktopEntry(path))
                    ProcessBuilder("xdg-mime", "default", "djmetry-url.desktop", "x-scheme-handler/$SCHEME").start().waitFor()
                }
                else -> Unit // macOS — через Info.plist установщика
            }
        }
    }
}
