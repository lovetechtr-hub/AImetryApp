package com.djmetry.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Сторож манифеста Android: настройки, от которых зависит, падает ли приложение и что уходит в чужие руки. */
class AndroidManifestGuardTest {
    private val manifest = File("../androidApp/src/main/AndroidManifest.xml").readText()

    @Test
    fun backupIsOff() {
        // Ключ шифрования токена живёт в Keystore и в копию не попадает: восстановленный файл не расшифровать
        assertTrue(manifest.contains("android:allowBackup=\"false\""), "allowBackup должен быть false")
    }

    @Test
    fun dependenciesLiveInApplication() {
        // AppContainer — в Application: поворот планшета не пересоздаёт HTTP-клиент и сессию
        assertTrue(manifest.contains("android:name=\".DJMetryApplication\""))
    }
}
