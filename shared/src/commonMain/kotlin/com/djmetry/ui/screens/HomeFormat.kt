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

/** Изменение тренда одной десятой с явным знаком: «+6.5», «−34.9», «0.0» (минус — типографский). */
internal fun signedTrend(value: Double): String {
    val r = kotlin.math.round(value * 10) / 10
    val abs = kotlin.math.abs(r)
    val body = if (abs == abs.toLong().toDouble()) "${abs.toLong()}.0" else abs.toString()
    return when { r > 0 -> "+$body"; r < 0 -> "\u2212$body"; else -> body }
}

/** Есть ли у артиста что показать в метриках тренда (24ч, 7д, рост). */
internal fun hasTrendMetrics(t: com.djmetry.api.models.TrendInfo?): Boolean =
    t != null && (t.score24h != null || t.score7d != null || t.growthRate != null)
