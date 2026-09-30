package com.djmetry.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.djmetry.ui.components.Photo
import com.djmetry.ui.components.RemoteImages
import com.djmetry.ui.components.photoNow
import com.djmetry.ui.components.rememberRemoteImage
import kotlin.test.*

/**
 * Регрессия: подиум рейтинга менял имена, а фото оставались прежними — элемент переиспользовался с новой
 * ссылкой, а загрузка пропускалась, «раз уже что-то показано». Проверяем настоящей перерисовкой Compose.
 */
class RemoteImageTest {
    private val a = ImageBitmap(1, 1)
    private val b = ImageBitmap(2, 2)

    @BeforeTest fun cache() {
        RemoteImages.put("https://img/a", a)
        RemoteImages.put("https://img/b", b)
    }

    @Test
    fun newUrlReplacesOldPhotoInReusedElement() {
        var url by mutableStateOf<String?>("https://img/a")
        var seen: Photo? = null
        val scene = ImageComposeScene(10, 10, Density(1f)) { seen = rememberRemoteImage(url).value }
        try {
            scene.render(0)
            assertEquals(Photo.Ready(a), seen)
            url = "https://img/b"
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(Photo.Ready(b), seen, "после смены ссылки — новое фото, не старое")
            url = null
            scene.render(48_000_000)
            scene.render(64_000_000)
            assertEquals(Photo.None, seen, "нет ссылки — нет фото (а не прошлое)")
        } finally { scene.close() }
    }

    @Test
    fun immediateStateWithoutNetwork() {
        assertEquals(Photo.Ready(a), photoNow("https://img/a"))
        assertEquals(Photo.Loading, photoNow("https://img/not-cached"))
        assertEquals(Photo.None, photoNow(null))
    }

    /** Кэш с бюджетом по байтам: давние вытесняются, чтение продлевает жизнь; фон — половина, нехватка памяти — ноль. */
    @Test
    fun cacheIsBoundedLru() {
        RemoteImages.clear()
        val big = ImageBitmap(2048, 2048) // 16 МБ
        repeat(8) { RemoteImages.put("https://img/big$it", big) } // 128 МБ > бюджета 96
        assertTrue(RemoteImages.totalBytes <= RemoteImages.BUDGET_BYTES)
        assertNull(RemoteImages.cached("https://img/big0"), "самая давняя вытеснена")
        assertNotNull(RemoteImages.cached("https://img/big2")) // прочитали — стала свежей
        RemoteImages.put("https://img/big8", big)
        assertNotNull(RemoteImages.cached("https://img/big2"), "недавно прочитанная пережила вытеснение")
        assertNull(RemoteImages.cached("https://img/big3"))
        RemoteImages.onBackground()
        assertTrue(RemoteImages.totalBytes <= RemoteImages.BUDGET_BYTES / 2)
        RemoteImages.clear()
        assertEquals(0, RemoteImages.count)
        cache()
    }
}
