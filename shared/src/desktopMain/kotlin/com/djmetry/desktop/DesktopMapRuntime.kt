package com.djmetry.desktop

import kotlinx.io.files.Path
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapRuntimeOptions
import java.io.File

/**
 * Кэш тайлов карты аналитики (MapLibre) на десктопе. Сам MapLibre угадывает папку по главному классу процесса —
 * в упакованном приложении и в тестах это ненадёжно, поэтому путь задаём явно: системная папка кэшей ОС.
 * Вызывать до первой карты (из main); повторный вызов безопасен.
 */
object DesktopMapRuntime {
    private var configured = false

    @Synchronized
    fun configure() {
        if (configured) return
        val dir = cacheDir().apply { mkdirs() }
        runCatching { DefaultMapRuntime.configure(MapRuntimeOptions(cacheFile = Path(File(dir, "maplibre-cache.db").absolutePath))) }
        configured = true
    }

    /** macOS: ~/Library/Caches/DJMetry, Windows: %LOCALAPPDATA%\DJMetry\Cache, Linux: ~/.cache/djmetry. */
    internal fun cacheDir(os: String = System.getProperty("os.name"), home: String = System.getProperty("user.home"), localAppData: String? = System.getenv("LOCALAPPDATA")): File {
        val name = os.lowercase()
        return when {
            name.contains("mac") -> File(home, "Library/Caches/DJMetry")
            name.contains("win") -> File(localAppData ?: "$home\\AppData\\Local", "DJMetry\\Cache")
            else -> File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", "djmetry")
        }
    }
}
