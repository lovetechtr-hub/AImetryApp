package com.djmetry.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/** Декодирует JPEG/PNG в ImageBitmap средствами платформы. null — если байты не картинка. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
