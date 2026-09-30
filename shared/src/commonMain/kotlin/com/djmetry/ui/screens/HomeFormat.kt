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

/** Части строки тренда под Score (телефон): подпись, значение со знаком, знак (−1, 0, 1) — для цвета. */
internal data class TrendPart(val label: String, val value: String, val sign: Int)

internal fun trendParts(t: com.djmetry.api.models.TrendInfo, label24h: String, label7d: String, labelGrowth: String): List<TrendPart> {
    fun sign(v: Double) = when { v > 0.05 -> 1; v < -0.05 -> -1; else -> 0 }
    return listOfNotNull(
        t.score24h?.let { TrendPart(label24h, signedTrend(it), sign(it)) },
        t.score7d?.let { TrendPart(label7d, signedTrend(it), sign(it)) },
        t.growthRate?.let { TrendPart(labelGrowth, signedTrend(it) + "%", sign(it)) },
    )
}
