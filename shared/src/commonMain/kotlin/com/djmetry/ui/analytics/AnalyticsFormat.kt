package com.djmetry.ui.analytics

import com.djmetry.api.models.ClickBreakdownRow
import com.djmetry.data.analytics.ActivityBucket
import com.djmetry.data.analytics.RangePreset
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.monthLabel
import kotlinx.datetime.LocalDate
import kotlin.math.round

/** 12480 → "12 480" (узкий неразрывный пробел — число не переносится). */
internal fun groupThousands(value: Int): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    return if (value < 0) "−$grouped" else grouped
}

/** CTR с одним знаком: 24.8 → "24.8%", 10.0 → "10%". */
internal fun ctrLabel(ctr: Double): String {
    val v = round(ctr * 10) / 10
    return (if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()) + "%"
}

/** Подпись точки графика: день и неделя — «22 сен», месяц — «сен 2026». */
internal fun pointLabel(date: LocalDate, bucket: ActivityBucket, monthsShort: String): String {
    val month = monthLabel(monthsShort, date.month.ordinal + 1)
    return if (bucket == ActivityBucket.Month) "$month ${date.year}" else "${date.day} $month"
}

/** Каждая какая подпись на оси X, чтобы их было не больше [maxLabels]. */
internal fun labelStep(points: Int, maxLabels: Int): Int = if (points <= maxLabels) 1 else (points + maxLabels - 1) / maxLabels

internal fun rangeKey(p: RangePreset): String = when (p) {
    RangePreset.All -> Strings.AN_R_ALL
    RangePreset.D7 -> Strings.AN_R_7
    RangePreset.D30 -> Strings.AN_R_30
    RangePreset.D90 -> Strings.AN_R_90
    RangePreset.D180 -> Strings.AN_R_180
    RangePreset.Ytd -> Strings.AN_R_YTD
}

/** Ключ перевода для типа устройства; незнакомое — null (показываем как есть). */
internal fun deviceKey(name: String): String? = when (name.lowercase()) {
    "mobile", "phone", "smartphone" -> Strings.AN_DEV_MOBILE
    "desktop", "computer" -> Strings.AN_DEV_DESKTOP
    "tablet" -> Strings.AN_DEV_TABLET
    "unknown" -> Strings.AN_UNKNOWN
    "other" -> Strings.AN_OTHER
    else -> null
}

/** Подпись клика: площадка (`content_type`: spotify → Spotify, apple_music → Apple Music), без неё — тип события. */
internal sealed interface ClickLabel {
    data class Text(val text: String) : ClickLabel
    data class Key(val key: String) : ClickLabel
}

internal fun clickLabel(row: ClickBreakdownRow): ClickLabel {
    val content = row.content_type?.trim()?.takeIf { it.isNotEmpty() }
    if (content != null) return ClickLabel.Text(
        when (content.lowercase()) {
            "youtube" -> "YouTube"; "soundcloud" -> "SoundCloud"; "tiktok" -> "TikTok"
            else -> content.split('_', '-', ' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    )
    return ClickLabel.Key(
        when (row.event_type) {
            "outbound_click" -> Strings.AN_EV_OUTBOUND
            "social_click" -> Strings.AN_EV_SOCIAL
            "smart_link_click" -> Strings.AN_EV_SMART
            "event_click" -> Strings.AN_EV_EVENT
            else -> Strings.AN_OTHER
        }
    )
}

/** Клики по площадкам: склеиваем одинаковые подписи, по убыванию. */
internal fun groupedClicks(rows: List<ClickBreakdownRow>): List<Pair<ClickLabel, Int>> =
    rows.filter { it.count > 0 }.groupBy { clickLabel(it) }.map { (k, v) -> k to v.sumOf { it.count } }.sortedByDescending { it.second }

/**
 * «Круглая» шкала оси Y: шаг 1, 2, 2.5 или 5 × 10ⁿ, не больше [maxTicks] делений до верха.
 * 2229 → (2500, 500): подписи 0 · 500 · 1K · 1.5K · 2K · 2.5K вместо 0 · 733 · 1.5K · 2.2K.
 */
internal fun niceAxis(max: Int, maxTicks: Int = 5): Pair<Double, Double> {
    if (max <= 0) return 4.0 to 1.0
    var magnitude = 1.0
    while (magnitude * 10 <= max) magnitude *= 10
    val steps = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.filter { it >= 1.0 }
    val step = steps.first { kotlin.math.ceil(max / it) <= maxTicks }
    return kotlin.math.ceil(max / step) * step to step
}
