package com.djmetry.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Декодирует JPEG/PNG в ImageBitmap средствами платформы, уменьшая так, чтобы большая сторона была не больше [maxPx]
 * (аватару 44 dp не нужен битмап 640×640 — это 1,6 МБ памяти). null — если байты не картинка. Вызывать не на UI-потоке.
 */
expect fun decodeImageBitmap(bytes: ByteArray, maxPx: Int = Int.MAX_VALUE): ImageBitmap?
