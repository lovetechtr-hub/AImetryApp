package com.djmetry

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import javax.swing.SwingUtilities

/** Выполнить на EDT и вернуть результат (исключение — пробрасывается в тест). */
fun <T> onEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var r: Result<T>? = null
    SwingUtilities.invokeAndWait { r = runCatching(block) }
    return r!!.getOrThrow()
}

/**
 * Тестовая сцена, которую трогаем только с EDT — как окно приложения. Часть эффектов возобновляется на EDT,
 * и сцена, которую рисует поток теста, ловила «multithreaded access to SnapshotStateObserver» / «measure during
 * layout» — рендер-тесты падали через раз в полном прогоне.
 */
class EdtScene(width: Int, height: Int, density: Density = Density(1f), content: @Composable () -> Unit) {
    private val scene = onEdt { ImageComposeScene(width, height, density, content = content) }

    fun render(nanoTime: Long = 0): Image = onEdt { scene.render(nanoTime) }
    fun sendPointerEvent(type: PointerEventType, position: Offset) = onEdt { scene.sendPointerEvent(type, position) }
    fun close() = onEdt { scene.close() }
}
