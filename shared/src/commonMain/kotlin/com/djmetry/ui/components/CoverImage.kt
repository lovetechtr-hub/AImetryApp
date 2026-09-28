package com.djmetry.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djmetry.ui.theme.DJMetryColors
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.math.abs

/** Простой загрузчик картинок по URL с кешем в памяти (обложки и фото артистов). */
internal object RemoteImages {
    private val client by lazy { HttpClient() }
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun cached(url: String): ImageBitmap? = cache[url]

    suspend fun load(url: String): ImageBitmap? =
        cache[url] ?: runCatching { decodeImageBitmap(client.get(url).body<ByteArray>()) }
            .getOrNull()
            ?.also { cache[url] = it }
}

/**
 * Обложка / фото в стиле DJMetry: квадрат со скруглением, тонкая светлая рамка,
 * тень и блик сверху. [tilt] — поворот по оси Y в градусах для 3D-эффекта.
 * Пока картинка грузится (или без сети), показывается [placeholder].
 */
@Composable
fun CoverImage(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    tilt: Float = 0f,
    placeholderColor: Color = DJMetryColors.PanelStrong,
    placeholder: @Composable BoxScope.() -> Unit = {},
) {
    val bitmap by produceState(url?.let(RemoteImages::cached), url) {
        if (value == null && url != null) value = RemoteImages.load(url)
    }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationY = tilt
                rotationX = abs(tilt) * 0.4f
                cameraDistance = 14f * density
            }
            .shadow(elevation = 10.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(placeholderColor)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            placeholder()
        }
        // Блик сверху-слева и затемнение снизу — «объём» как у обложек на сайте
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.26f),
                    0.38f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.28f),
                )
            )
        )
    }
}
