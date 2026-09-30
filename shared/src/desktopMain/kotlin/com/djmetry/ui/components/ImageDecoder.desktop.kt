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
    if (big <= maxPx) return@runCatching src.toComposeImageBitmap()
    // Уменьшаем один раз при загрузке: дальше в памяти и на отрисовке — маленький битмап
    val k = maxPx.toFloat() / big
    val w = (src.width * k).toInt().coerceAtLeast(1)
    val h = (src.height * k).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(src, Rect.makeWH(src.width.toFloat(), src.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
    surface.makeImageSnapshot().toComposeImageBitmap().also { surface.close(); src.close() }
}.getOrNull()
