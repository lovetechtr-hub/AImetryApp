package com.djmetry.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Слой поверх вкладки забирает касания, мышь и колесо — до вкладки под ним они не доходят.
 * Не `clickable {}`: тот делал весь слой «кнопкой» для TalkBack/VoiceOver и ловил Tab на десктопе.
 */
fun Modifier.blockPointerBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

/** Курсор-«рука» над нажимаемым (десктоп; на телефоне без эффекта). */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)
