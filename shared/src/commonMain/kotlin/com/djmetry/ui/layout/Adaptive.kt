package com.djmetry.ui.layout

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Класс раскладки по ширине окна (docs/RULES.md).
 * Считаем по окну, а не по устройству: iPad в Split View получает телефонную раскладку.
 */
enum class LayoutClass {
    /** < 600 dp — телефон: один столбец, таббар снизу. */
    Compact,
    /** 600–839 dp — планшет портрет: навигация слева, панели под колодой. */
    Medium,
    /** ≥ 840 dp — планшет альбом: навигация слева, панель справа. */
    Expanded;

    val isTablet: Boolean get() = this != Compact
}

fun layoutClassFor(widthDp: Float): LayoutClass = when {
    widthDp < 600f -> LayoutClass.Compact
    widthDp < 840f -> LayoutClass.Medium
    else -> LayoutClass.Expanded
}

val LocalLayoutClass = compositionLocalOf { LayoutClass.Compact }

/** Отступ снизу у контента вкладок: на телефоне под парящим таббаром, на планшете таббар слева. */
val LocalBottomClearance = compositionLocalOf<Dp> { 110.dp }

/** Максимальная ширина списков и текста: шире строки читать неудобно (планшет в альбоме, десктоп, 4K). */
val ReadableMaxWidth: Dp = 760.dp

/** Ширина колонки контента в окне [windowWidthDp]: на телефоне — всё окно, на широких экранах — не шире [ReadableMaxWidth]. */
fun readableWidthDp(windowWidthDp: Float): Float = minOf(windowWidthDp, ReadableMaxWidth.value)

/** Колонка контента по центру, не шире [ReadableMaxWidth]; на телефоне занимает всю ширину. */
fun Modifier.readableWidth(): Modifier =
    fillMaxHeight().fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadableMaxWidth).fillMaxWidth()
