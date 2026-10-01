package com.djmetry.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Где на экране поля ввода (координаты окна): тап в поле клавиатуру не прячет — фокус просто переходит. */
class TextFieldRegistry {
    private val bounds = mutableMapOf<Any, Rect>()
    fun put(key: Any, rect: Rect) { bounds[key] = rect }
    fun remove(key: Any) { bounds.remove(key) }
    fun hits(point: Offset): Boolean = bounds.values.any { it.contains(point) }
}

val LocalTextFieldRegistry = staticCompositionLocalOf<TextFieldRegistry?> { null }

/** Поле ввода: регистрирует свою область, чтобы тап в него не закрывал клавиатуру. */
fun Modifier.textInput(): Modifier = composed {
    val registry = LocalTextFieldRegistry.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(registry, key) { onDispose { registry.remove(key) } }
    onGloballyPositioned { registry.put(key, it.boundsInWindow()) }
}

/** Сам жест вынесен из Compose — чистая проверка «это тап мимо полей», тестируется без экрана. */
internal fun isDismissTap(down: Offset, up: Offset, slop: Float, insideField: Boolean): Boolean =
    !insideField && (up - down).getDistance() < slop

/**
 * Тап мимо полей ввода — снять фокус и спрятать клавиатуру (iOS, Android; на десктопе — снять фокус).
 * Жест смотрим на ранней фазе и ничего не поглощаем: кнопки, строки и прокрутка работают как обычно.
 * Диалог — отдельное окно: оборачивать и его содержимое.
 */
@Composable
fun DismissKeyboardOnTap(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val registry = remember { TextFieldRegistry() }
    val origin = remember { arrayOf(Offset.Zero) }
    CompositionLocalProvider(LocalTextFieldRegistry provides registry) {
        Box(
            modifier
                .onGloballyPositioned { origin[0] = it.positionInWindow() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val inside = registry.hits(down.position + origin[0])
                        val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                        if (isDismissTap(down.position, up.position, viewConfiguration.touchSlop, inside)) {
                            focus.clearFocus()
                            keyboard?.hide()
                        }
                    }
                },
        ) { content() }
    }
}
