package com.djmetry.ui.screens

/** Прирост Score: 6.4512 → "6.45", 12.0 → "12". */
internal fun formatDelta(value: Double): String {
    val text = formatScore(value)
    return text.removeSuffix(".00")
}

/** 53.1649 → "53.16" без платформенного форматирования. */
internal fun formatScore(score: Double): String {
    val hundredths = kotlin.math.round(score * 100).toLong()
    val whole = hundredths / 100
    val frac = (kotlin.math.abs(hundredths) % 100).toString().padStart(2, '0')
    return "$whole.$frac"
}
