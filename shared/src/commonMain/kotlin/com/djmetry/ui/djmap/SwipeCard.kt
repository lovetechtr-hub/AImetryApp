package com.djmetry.ui.djmap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Куда уходит карточка после свайпа. */
internal enum class SwipeResult { Stay, Up, Down }

/**
 * Решение по окончании свайпа: далеко или быстро — срабатывает, иначе карточка возвращается.
 * [offsetDp] — смещение (вниз > 0), [velocityDp] — скорость dp/с (вниз > 0).
 */
internal fun swipeResult(offsetDp: Float, velocityDp: Float, distanceDp: Float = 90f, flingDp: Float = 900f): SwipeResult = when {
    offsetDp > distanceDp || velocityDp > flingDp -> SwipeResult.Down
    offsetDp < -distanceDp * 0.6f || velocityDp < -flingDp -> SwipeResult.Up
    else -> SwipeResult.Stay
}

/**
 * Карточка, которую тянут пальцем, мышью или касанием на сенсорном мониторе: едет за жестом, вниз — бледнеет
 * и чуть уменьшается, отпускаешь — пружинит на место или срабатывает [onDown] / [onUp]. Внутренняя прокрутка
 * не мешает: вниз от верха списка тянется уже сама карточка (nested scroll).
 */
@Composable
internal fun SwipeCard(onDown: () -> Unit, onUp: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dp = { px: Float -> px / density.density }

    fun settle(velocityPx: Float) {
        scope.launch {
            when (swipeResult(dp(offset.value), dp(velocityPx))) {
                SwipeResult.Down -> {
                    offset.animateTo(offset.value + with(density) { 420.dp.toPx() }, tween(180))
                    onDown()
                    offset.snapTo(0f)
                }
                SwipeResult.Up -> if (onUp != null) {
                    onUp(); offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                } else offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                SwipeResult.Stay -> offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    // Вверх тянется туже (вытянуть наверх — только раскрыть), вниз — один к одному
    fun drag(dy: Float) = scope.launch { offset.snapTo(offset.value + if (offset.value + dy < 0) dy * 0.35f else dy) }

    val nested = remember {
        object : NestedScrollConnection {
            // Карточка уже сдвинута вниз, а жест вверх — сначала возвращаем карточку, потом крутим список
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (offset.value > 0f && available.y < 0f) { drag(available.y); available.copy(x = 0f) } else Offset.Zero
            // Список упёрся в верх и жест вниз — тянем карточку
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f && source == NestedScrollSource.UserInput) { drag(available.y); available.copy(x = 0f) } else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity =
                if (abs(offset.value) > 1f) { settle(available.y); available } else Velocity.Zero
        }
    }
    Box(
        modifier
            // Жест — в координатах до сдвига (иначе палец и карточка едут вместе и смещение «не набирается»)
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dy -> tracker.addPosition(change.uptimeMillis, change.position); drag(dy) },
                    onDragEnd = { settle(tracker.calculateVelocity().y) },
                    onDragCancel = { settle(0f) },
                )
            }
            .nestedScroll(nested)
            .graphicsLayer {
                translationY = offset.value
                val down = (offset.value / (420f * density.density)).coerceIn(0f, 1f)
                alpha = 1f - down * 0.7f
                scaleX = 1f - down * 0.06f; scaleY = scaleX
            },
    ) { content() }
}
