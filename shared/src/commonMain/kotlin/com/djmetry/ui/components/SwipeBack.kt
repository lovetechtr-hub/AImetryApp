package com.djmetry.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Слой поверх вкладки, который закрывается свайпом от левого края (iOS) / жестом «Назад» (Android 14+),
 * как в нативных приложениях: слой едет за пальцем, под ним видно то, что было до него. Отпустили рано —
 * вернулся на место; дотянули — закрылся. Без прогресса (Esc на десктопе, кнопка «Назад») — закрывается сразу.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SwipeBackLayer(enabled: Boolean = true, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shift = remember { Animatable(0f) } // доля ширины 0…1
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    androidx.compose.ui.backhandler.PredictiveBackHandler(enabled) { events ->
        var moved = false
        try {
            events.collect { e -> moved = true; shift.snapTo(swipeShift(e.progress)) }
            if (moved) shift.animateTo(1f, tween(SWIPE_FINISH_MS))
            back()
            shift.snapTo(0f) // слой мог остаться (обработчик не закрыл его) — не оставляем сдвинутым
        } catch (e: CancellationException) {
            // Палец отпустили раньше — слой плавно возвращается
            scope.launch { shift.animateTo(0f, tween(SWIPE_FINISH_MS)) }
            throw e
        }
    }
    Box(
        modifier.graphicsLayer {
            translationX = shift.value * size.width
            // Тень по левому краю, пока слой сдвинут
            shadowElevation = if (shift.value > 0f) 24.dp.toPx() else 0f
            shape = RectangleShape
        },
    ) { content() }
}

/** Прогресс жеста → сдвиг слоя: как у системы, с лёгким «отставанием» в начале. */
internal fun swipeShift(progress: Float): Float = progress.coerceIn(0f, 1f).let { it * (0.85f + 0.15f * it) }

internal const val SWIPE_FINISH_MS = 180
