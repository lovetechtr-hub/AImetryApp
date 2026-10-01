package com.djmetry.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.math.abs

/**
 * Загрузчик картинок по URL с кешем в памяти (обложки и фото артистов).
 * - Декодирование и уменьшение — на [Dispatchers.Default], не в UI-потоке (раньше списки дёргались на каждой новой строке).
 * - Размер — ступенью ([bucket]) под место на экране: аватару 44 dp — 256 px, а не 640×640.
 * - Один и тот же URL одновременно качается один раз; не больше [PARALLEL] загрузок сразу; таймаут — без вечного шиммера.
 * - Кеш — LRU с бюджетом по байтам: без лимита Радар и рейтинг набирали сотни МБ, и iOS выгружал приложение в фоне.
 */
internal object RemoteImages {
    private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 } } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Semaphore(PARALLEL)
    private val inflightLock = Mutex()
    private val inflight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    /** Порядок вставки = порядок использования: при чтении запись переносится в конец. Трогаем только с UI-потока. */
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private var bytes = 0L

    const val PARALLEL = 6
    /** ~96 МБ декодированных пикселей: при 256 px на фото — сотни аватаров. */
    const val BUDGET_BYTES = 96L * 1024 * 1024

    /** Ступени размера: одно фото хранится в 1–2 вариантах, а не в десятке. */
    fun bucket(px: Int): Int = when {
        px <= 128 -> 128
        px <= 256 -> 256
        px <= 512 -> 512
        else -> 1024
    }

    // data-URL — сотни КБ: в ключ кэша кладём отпечаток, а не всю строку
    private fun key(url: String, px: Int) = (if (url.startsWith("data:", ignoreCase = true)) "data#${url.length}#${url.hashCode()}" else url) + "@${bucket(px)}"
    private fun size(b: ImageBitmap) = b.width.toLong() * b.height * 4

    /** Готовое фото этого или большего размера (большее уменьшится при отрисовке). */
    fun cached(url: String, px: Int = 1024): ImageBitmap? {
        var b = bucket(px)
        while (b <= 1024) { take("$url@$b")?.let { return it }; b *= 2 }
        return null
    }

    private fun take(k: String): ImageBitmap? = cache.remove(k)?.also { cache[k] = it }

    /** Для тестов: положить картинку в кэш (под наибольшую ступень — подходит любому размеру). */
    internal fun put(url: String, bitmap: ImageBitmap) = putKey("$url@1024", bitmap)

    private fun putKey(k: String, bitmap: ImageBitmap) {
        cache.remove(k)?.let { bytes -= size(it) }
        cache[k] = bitmap
        bytes += size(bitmap)
        trimTo(BUDGET_BYTES)
    }

    /** Выбросить самые давние, пока не уложимся в [limit] байт. */
    fun trimTo(limit: Long) {
        val it = cache.entries.iterator()
        while (bytes > limit && it.hasNext()) { bytes -= size(it.next().value); it.remove() }
    }

    /** Нехватка памяти (iOS memory warning) — всё; уход в фон — до половины бюджета. */
    fun clear() = trimTo(0)
    fun onBackground() = trimTo(BUDGET_BYTES / 2)

    internal val totalBytes: Long get() = bytes
    internal val count: Int get() = cache.size

    /**
     * Байты картинки. Фото агентства сайт хранит прямо в базе как `data:image/…;base64,…` (браузер показывает
     * такое сам) — декодируем на месте; относительный путь — от адреса сайта; остальное — по сети.
     */
    private suspend fun imageBytes(url: String): ByteArray = dataUrlBytes(url)
        ?: client.get(if (url.startsWith("/") && !url.startsWith("//")) com.djmetry.config.AppConfig.BASE_URL + url else url).body()

    suspend fun load(url: String, px: Int = 1024): ImageBitmap? {
        cached(url, px)?.let { return it }
        val k = key(url, px)
        val job = inflightLock.withLock {
            inflight.getOrPut(k) {
                lateinit var self: kotlinx.coroutines.Deferred<ImageBitmap?>
                self = scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        gate.withPermit { runCatching { decodeImageBitmap(imageBytes(url), bucket(px)) }.getOrNull() }
                    } finally {
                        // Запись снимает сама загрузка: ушли с экрана посреди загрузки — готовый битмап не застрянет в карте
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { inflightLock.withLock { if (inflight[k] === self) inflight.remove(k) } }
                    }
                }
                self.also { it.start() }
            }
        }
        val bmp = job.await()
        // В кеш — на потоке вызывающего (UI), как и чтение
        bmp?.let { if (cache[k] == null) putKey(k, it) }
        return bmp
    }
}

/** Фото по URL: грузится, загружено, не загрузилось или ссылки нет. */
internal sealed interface Photo {
    data object Loading : Photo
    data object Failed : Photo
    data object None : Photo
    data class Ready(val bitmap: ImageBitmap) : Photo
}

internal val Photo.bitmap: ImageBitmap? get() = (this as? Photo.Ready)?.bitmap

/** Что показать сразу для [url], без сети: из кэша, «грузится» или «нет ссылки». */
internal fun photoNow(url: String?, px: Int = 1024): Photo = when {
    url.isNullOrBlank() -> Photo.None
    else -> RemoteImages.cached(url, px)?.let { Photo.Ready(it) } ?: Photo.Loading
}

/**
 * Фото по URL для любого места (обложки, постер, герой профиля). При смене [url] состояние СРАЗУ
 * сбрасывается на новое (кэш или «грузится») и только потом грузится — старое фото не «прилипает»
 * к переиспользованному элементу (была ошибка: подиум рейтинга менял имена, а фото оставались прежними).
 */
@Composable
internal fun rememberRemoteImage(url: String?, px: Int = 1024): State<Photo> {
    val u = url?.takeIf { it.isNotBlank() }
    return produceState(photoNow(u, px), u, RemoteImages.bucket(px)) {
        value = photoNow(u, px)
        if (value == Photo.Loading && u != null) value = RemoteImages.load(u, px)?.let { Photo.Ready(it) } ?: Photo.Failed
    }
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
    placeholder: (@Composable BoxScope.() -> Unit)? = null,
) {
    // Битмап — под размер на экране (с учётом плотности), а не полный 640×640
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 1.6f).roundToPx() }
    val photo by rememberRemoteImage(url, px)
    val bitmap = photo.bitmap
    // Пока фото грузится — общий блик скелетона; не загрузилось — обычная заглушка
    val loading = photo == Photo.Loading
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
            .then(if (loading) Modifier.shimmer(shape) else Modifier.background(placeholderColor))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else if (!loading) {
            // Нет фото — не пустой квадрат: своя заглушка экрана или силуэт
            // Круглая — фото человека (силуэт), квадратная — обложка (нота)
            placeholder?.invoke(this) ?: Icon(
                if (cornerRadius * 2 >= size) Icons.Outlined.Person else Icons.Outlined.MusicNote, null, tint = DJMetryColors.Muted.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxSize(0.5f),
            )
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

/** `data:image/png;base64,AAAA` → байты; не data-URL или битый base64 — null. */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
internal fun dataUrlBytes(url: String): ByteArray? {
    if (!url.startsWith("data:", ignoreCase = true)) return null
    val comma = url.indexOf(',').takeIf { it > 0 } ?: return null
    if (!url.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
    return runCatching { kotlin.io.encoding.Base64.Default.decode(url.substring(comma + 1).filterNot { it.isWhitespace() }) }.getOrNull()
}
