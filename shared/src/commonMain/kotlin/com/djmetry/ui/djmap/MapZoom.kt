package com.djmetry.ui.djmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.Channel
import org.maplibre.compose.map.MapState
import org.maplibre.compose.camera.CameraUpdate

/**
 * Кнопки +/− карты. Нажатия — в очередь, а не в одно состояние: второе быстрое «+» раньше терялось
 * (то же значение пока шла анимация первого), а сброс состояния обрывал анимацию.
 */
class MapZoomRequests {
    internal val steps = Channel<Double>(Channel.UNLIMITED)
    fun zoom(step: Double) { steps.trySend(step) }
}

/** Шаги, накопившиеся за время анимации, — одним движением; зум в пределах [min]…[max]. */
internal fun nextZoom(current: Double, steps: List<Double>, min: Double, max: Double): Double =
    (current + steps.sum()).coerceIn(min, max)

/** Применяет нажатия +/− к камере карты, по одному плавному шагу за раз. */
@Composable
internal fun BindZoomRequests(requests: MapZoomRequests, map: MapState, min: Double = 0.0, max: Double = 18.0) {
    LaunchedEffect(requests, map) {
        for (first in requests.steps) {
            val batch = mutableListOf(first)
            while (true) batch += requests.steps.tryReceive().getOrNull() ?: break
            runCatching { map.animateCamera(CameraUpdate(zoom = nextZoom(map.cameraPosition.zoom, batch, min, max))) }
        }
    }
}
