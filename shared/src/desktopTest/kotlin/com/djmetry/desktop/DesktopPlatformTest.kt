package com.djmetry.desktop

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopPlatformTest {

    @Test
    fun secondInstanceForwardsDeepLinkToFirst() {
        val dir = Files.createTempDirectory("djmetry-instance").toFile()
        val received = CountDownLatch(1)
        var message = ""
        val first = SingleInstance(dir)
        assertTrue(first.acquireOrForward(null) { message = it; received.countDown() })

        val second = SingleInstance(dir)
        assertFalse(second.acquireOrForward("djmetry://oauth?code=abc") { fail("второй экземпляр не должен слушать") })

        assertTrue(received.await(3, TimeUnit.SECONDS))
        assertEquals("djmetry://oauth?code=abc", message)
        first.release()
        assertTrue(SingleInstance(dir).acquireOrForward(null) {}, "после закрытия первого можно запуститься снова")
    }

    @Test
    fun windowsRegistryCommands() {
        val cmds = UrlSchemeRegistration.windowsCommands("""C:\Users\me\AppData\Local\DJMetry\DJMetry.exe""")
        assertEquals(listOf("reg", "add", """HKCU\Software\Classes\djmetry""", "/ve", "/d", "URL:DJMetry Protocol", "/f"), cmds[0])
        assertTrue(cmds.any { "URL Protocol" in it }, "без 'URL Protocol' Windows не считает ключ протоколом")
        val open = cmds.last()
        assertEquals("""HKCU\Software\Classes\djmetry\shell\open\command""", open[2])
        assertEquals("\"C:\\Users\\me\\AppData\\Local\\DJMetry\\DJMetry.exe\" \"%1\"", open[5])
        assertTrue(cmds.all { it[2].startsWith("HKCU") }, "только HKCU — без прав администратора")
    }

    @Test
    fun linuxDesktopEntryRegistersScheme() {
        val entry = UrlSchemeRegistration.linuxDesktopEntry("/opt/djmetry/bin/DJMetry")
        assertTrue("MimeType=x-scheme-handler/djmetry;" in entry)
        assertTrue("Exec=\"/opt/djmetry/bin/DJMetry\" %u" in entry)
    }
}
