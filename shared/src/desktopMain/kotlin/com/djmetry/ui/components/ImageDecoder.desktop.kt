package com.djmetry.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun decodeImageBitmap(bytes: ByteArray, maxPx: Int): ImageBitmap? = runCatching {
    val src = Image.makeFromEncoded(bytes)
    val big = maxOf(src.width, src.height)
    // Всегда рисуем в растровую поверхность здесь, в фоне: makeFromEncoded ленив — без этого маленькое фото
    // распаковывалось при каждой отрисовке в потоке рендера (подтормаживание при прокрутке списков на iPhone).
    // Большое заодно уменьшаем один раз: дальше в памяти и на отрисовке — маленький битмап
    val k = minOf(1f, maxPx.toFloat() / big)
    val w = (src.width * k).toInt().coerceAtLeast(1)
    val h = (src.height * k).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(w, h)
    // Нативную память Skia освобождаем и при ошибке отрисовки, а не только при успехе
    try {
        surface.canvas.drawImageRect(src, Rect.makeWH(src.width.toFloat(), src.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
        surface.makeImageSnapshot().toComposeImageBitmap() // снимок отдаём Compose — его не закрываем
    } finally {
        surface.close(); src.close()
    }
}.getOrNull()
