package com.djmetry.ui.components

import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djmetry.ui.theme.DJMetryColors

/** Цвета скелетона: основа чуть светлее панели, блик — мягкий холодный. */
object SkeletonColors {
    val Base = Color(0xFF1A2740)
    val Highlight = Color(0xFF2A3B5C)
}

/** Ширина полосы блика и длительность прохода по экрану. */
internal const val SHIMMER_BAND_PX = 420f
internal const val SHIMMER_PERIOD_MS = 1400

/**
 * Где сейчас центр блика по X в координатах всего экрана: проход слева направо с заходом за края на полосу,
 * чтобы блик появлялся и исчезал плавно. [progress] 0…1, [screenWidthPx] — ширина окна.
 */
fun shimmerCenterX(progress: Float, screenWidthPx: Float, bandPx: Float = SHIMMER_BAND_PX): Float =
    -bandPx + (screenWidthPx + 2 * bandPx) * progress

/** Прогресс блика и ширина окна — одни на всё приложение, все скелетоны переливаются одной волной. */
private class ShimmerState(val progress: State<Float>, val windowWidthPx: Float, val users: MutableIntState)

private val LocalShimmer = staticCompositionLocalOf { ShimmerState(mutableFloatStateOf(0f), 1200f, mutableIntStateOf(0)) }

/**
 * Ставится один раз в корне приложения. Блик идёт, только пока на экране есть хоть один скелетон:
 * раньше бесконечная анимация крутила кадры всегда — батарея и процессор впустую.
 */
@Composable
fun ShimmerProvider(content: @Composable () -> Unit) {
    val progress = remember { mutableFloatStateOf(0f) }
    val users = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { users.intValue > 0 }.collectLatest { active ->
            if (!active) return@collectLatest
            val start = withFrameMillis { it }
            while (true) withFrameMillis { t -> progress.floatValue = shimmerProgress(t - start) }
        }
    }
    BoxWithConstraints {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 1200f
        CompositionLocalProvider(LocalShimmer provides remember(width) { ShimmerState(progress, width, users) }, content = content)
    }
}

/** Доля прохода блика 0…1 через [elapsedMs] от начала. */
internal fun shimmerProgress(elapsedMs: Long): Float = (elapsedMs % SHIMMER_PERIOD_MS).toFloat() / SHIMMER_PERIOD_MS

/**
 * Фон-скелетон с общим бликом. Блик считается в координатах экрана (positionInRoot), поэтому соседние
 * плашки, строки и обложки переливаются единой волной, а не каждая сама по себе. Анимация — только в фазе
 * рисования, без перекомпоновки.
 */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    val shimmer = LocalShimmer.current
    val originX = remember { floatArrayOf(0f) }
    DisposableEffect(shimmer) {
        shimmer.users.intValue++
        onDispose { shimmer.users.intValue-- }
    }
    this
        .clip(shape)
        .onGloballyPositioned { originX[0] = it.positionInRoot().x }
        .drawWithCache {
            onDrawBehind {
                drawRect(SkeletonColors.Base)
                val center = shimmerCenterX(shimmer.progress.value, shimmer.windowWidthPx) - originX[0]
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to SkeletonColors.Highlight,
                        1f to Color.Transparent,
                        start = Offset(center - SHIMMER_BAND_PX / 2, 0f),
                        end = Offset(center + SHIMMER_BAND_PX / 2, size.height * 0.35f),
                    )
                )
            }
        }
}

/** Прямоугольник-скелетон. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    Box(modifier.shimmer(shape))
}

/** Строка текста-скелетон: высота строки, ширина — доля. */
@Composable
fun SkeletonLine(widthFraction: Float, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(widthFraction).height(height).shimmer(RoundedCornerShape(height / 2)))
}

/** Круг-скелетон (аватар, кольцо). */
@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).shimmer(CircleShape))
}

/** Сколько строк-скелетонов нужно, чтобы закрыть видимую высоту (минимум 3) — список не «кончается» во время загрузки. */
fun skeletonRowCount(visibleHeightDp: Float, rowHeightDp: Float, spacingDp: Float = 8f): Int =
    maxOf(3, kotlin.math.ceil(visibleHeightDp / (rowHeightDp + spacingDp)).toInt())

/**
 * Строка списка-скелетон: [leading] — место под номер, обложка, две строки текста, значение справа.
 * Ширины строк чуть разные ([seed]) — список выглядит живым, а не штампованным.
 */
@Composable
fun SkeletonListRow(seed: Int, leading: Boolean = true, cover: Dp = 46.dp, trailing: Boolean = true, modifier: Modifier = Modifier) {
    val nameWidth = listOf(0.62f, 0.48f, 0.7f, 0.55f, 0.66f)[seed % 5]
    val subWidth = listOf(0.34f, 0.42f, 0.28f, 0.38f, 0.3f)[seed % 5]
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (leading) {
            Column(Modifier.width(40.dp), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                SkeletonBox(Modifier.size(20.dp, 14.dp), RoundedCornerShape(5.dp))
                Spacer(Modifier.height(5.dp))
                SkeletonBox(Modifier.size(14.dp, 8.dp), RoundedCornerShape(4.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        SkeletonBox(Modifier.size(cover), RoundedCornerShape(13.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonLine(nameWidth, 13.dp)
            SkeletonLine(subWidth, 10.dp)
        }
        if (trailing) SkeletonBox(Modifier.size(48.dp, 16.dp), RoundedCornerShape(6.dp))
    }
}

/** Карточка-скелетон (панель с заголовком и строками) — секции карточки артиста, профиля. */
@Composable
fun SkeletonCard(lines: Int = 3, modifier: Modifier = Modifier, header: Boolean = true) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header) SkeletonLine(0.38f, 14.dp)
        repeat(lines) { i ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(42.dp), RoundedCornerShape(11.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonLine(listOf(0.7f, 0.55f, 0.62f)[i % 3], 12.dp)
                    SkeletonLine(listOf(0.4f, 0.3f, 0.45f)[i % 3], 9.dp)
                }
            }
        }
    }
}

/**
 * Плавная смена скелетона на готовый экран (без прыжка): пока [loading] — [skeleton], потом [content] с растворением.
 */
@Composable
fun LoadingCrossfade(loading: Boolean, skeleton: @Composable () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.animation.Crossfade(targetState = loading, animationSpec = tween(260), label = "skeleton") { isLoading ->
        if (isLoading) skeleton() else content()
    }
}
