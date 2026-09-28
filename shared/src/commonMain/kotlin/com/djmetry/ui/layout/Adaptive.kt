package com.djmetry.ui.layout

import androidx.compose.runtime.compositionLocalOf
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
