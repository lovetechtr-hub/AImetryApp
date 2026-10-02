package com.djmetry.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Слой поверх вкладки: касания, мышь и колесо не доходят до вкладки под ним. Блокирует сам факт обработчика
 * в точке касания (соседние слои ниже Compose уже не проверяет), события НЕ поглощаются: иначе содержимое
 * слоя теряло их — клики отменялись, а карта получала отмену посреди щипка (зум «глючил», колесо не работало).
 * Не `clickable {}`: тот делал весь слой «кнопкой» для TalkBack/VoiceOver и ловил Tab на десктопе.
 */
fun Modifier.blockPointerBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Курсор-«рука» над нажимаемым (десктоп; на телефоне без эффекта). */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)
