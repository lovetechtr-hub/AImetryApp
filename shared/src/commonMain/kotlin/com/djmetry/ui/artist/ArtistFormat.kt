package com.djmetry.ui.artist

import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.SocialMedia
import com.djmetry.config.AppConfig
import com.djmetry.data.repository.DJMagEntry
import com.djmetry.ui.components.SocialIcon
import kotlin.math.round

/** 15 384 355 → "15.4M", 7 169 896 963 → "7.17B", 12 345 → "12.3K". Без платформенного форматирования. */
internal fun compactCount(value: Long): String {
    fun trim(v: Double, digits: Int): String {
        val factor = if (digits == 2) 100.0 else 10.0
        val text = (round(v * factor) / factor).toString()
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }
    return when {
        value >= 1_000_000_000 -> trim(value / 1e9, 2) + "B"
        value >= 1_000_000 -> trim(value / 1e6, 1) + "M"
        value >= 1_000 -> trim(value / 1e3, 1) + "K"
        else -> value.toString()
    }
}

/** 198 815 мс → "3:18". */
internal fun trackDuration(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** День и месяц (1..12) концерта из локального времени площадки "2026-10-16T20:00:00". */
internal data class EventDay(val day: Int, val month: Int)

internal fun eventDay(datetime: String): EventDay? {
    val parts = datetime.take(10).split('-')
    if (parts.size != 3) return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return if (month in 1..12 && day in 1..31) EventDay(day, month) else null
}

/** Название месяца из строки MONTHS_SHORT (12 сокращений через пробел). */
internal fun monthLabel(monthsShort: String, month: Int): String =
    monthsShort.split(' ').getOrNull(month - 1).orEmpty()

/** Куда вести по «Билеты»: первая ссылка из offers, иначе страница события. */
internal fun ticketUrl(event: ArtistEvent): String? =
    event.offers.firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) } ?: event.url

/** Строка под площадкой: город и страна. */
internal fun eventPlace(event: ArtistEvent): String =
    listOfNotNull(event.venue?.city, event.venue?.country).filter { it.isNotBlank() }.joinToString(", ")

/** Ссылка «Поделиться»: канонический URL с бэкенда или страница по Spotify ID. */
internal fun shareUrl(details: ArtistDetailsResponse): String =
    details.canonicalUrl?.takeIf { it.startsWith("http") } ?: AppConfig.artistUrl(details.spotifyArtistId)

/** "#2 · 2025" или "#2", если год неизвестен. */
internal fun djMagLabel(entry: DJMagEntry): String = "#${entry.rank}" + (entry.year?.let { " · $it" } ?: "")

enum class SocialKind(val icon: SocialIcon) {
    Instagram(SocialIcon.Instagram), TikTok(SocialIcon.TikTok), X(SocialIcon.X), SoundCloud(SocialIcon.SoundCloud),
    Facebook(SocialIcon.Facebook), Telegram(SocialIcon.Telegram), AppleMusic(SocialIcon.AppleMusic), Beatport(SocialIcon.Beatport),
}

data class SocialLink(val kind: SocialKind, val label: String, val url: String)

/**
 * Соцсети из карточки. Бэкенд отдаёт то хэндл ("@martingarrix"), то полный URL — приводим к ссылке.
 * Порядок фиксированный, пустые поля пропускаем.
 */
internal fun socialLinks(social: SocialMedia?): List<SocialLink> {
    if (social == null) return emptyList()
    fun handle(kind: SocialKind, raw: String?, base: String, at: Boolean): SocialLink? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http")) {
            val tail = value.trimEnd('/').substringAfterLast('/').removePrefix("@")
            return SocialLink(kind, if (at) "@$tail" else tail, value)
        }
        val name = value.removePrefix("@")
        return SocialLink(kind, if (at) "@$name" else name, base + (if (kind == SocialKind.TikTok) "@" else "") + name)
    }
    fun url(kind: SocialKind, raw: String?, label: String): SocialLink? =
        raw?.trim()?.takeIf { it.startsWith("http") }?.let { SocialLink(kind, label, it) }
    return listOfNotNull(
        handle(SocialKind.Instagram, social.instagram, "https://instagram.com/", at = true),
        handle(SocialKind.TikTok, social.tiktok, "https://www.tiktok.com/", at = true),
        handle(SocialKind.X, social.twitter, "https://x.com/", at = true),
        handle(SocialKind.SoundCloud, social.soundcloud, "https://soundcloud.com/", at = false),
        handle(SocialKind.Facebook, social.facebook, "https://facebook.com/", at = false),
        handle(SocialKind.Telegram, social.telegram, "https://t.me/", at = true),
        url(SocialKind.AppleMusic, social.appleMusicUrl, "Apple Music"),
        url(SocialKind.Beatport, social.beatportUrl, "Beatport"),
    )
}
