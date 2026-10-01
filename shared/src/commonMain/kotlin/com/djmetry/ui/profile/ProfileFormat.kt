package com.djmetry.ui.profile

import com.djmetry.data.repository.NotificationFilter
import com.djmetry.i18n.Strings
import kotlin.time.Instant

/** Подпись чипа фильтра колокольчика. */
internal fun filterLabelKey(filter: NotificationFilter): String = when (filter) {
    NotificationFilter.All -> Strings.NOTIF_F_ALL
    NotificationFilter.Booking -> Strings.NOTIF_F_BOOKING
    NotificationFilter.Releases -> Strings.NOTIF_F_RELEASES
    NotificationFilter.PreSave -> Strings.NOTIF_F_PRESAVE
    NotificationFilter.Concerts -> Strings.NOTIF_F_CONCERTS
}

/** Вид уведомления по типу бэкенда. Названия радаров — бренды, не переводятся. */
internal enum class NotificationKind(val label: String?, val labelKey: String?) {
    Booking(null, Strings.NOTIF_TYPE_BOOKING),
    Release("RELEASE RADAR", null),
    PreSave("PRE-SAVE", null),
    Concert("CONCERT RADAR", null),
    Other("DJMETRY", null),
}

internal fun notificationKind(type: String): NotificationKind = when (type) {
    "booking" -> NotificationKind.Booking
    "release_radar" -> NotificationKind.Release
    "pre_save" -> NotificationKind.PreSave
    "concert" -> NotificationKind.Concert
    else -> NotificationKind.Other
}

/** Ключ статуса заявки букинга; неизвестный статус показываем как есть. */
internal fun bookingStatusKey(status: String): String? = when (status) {
    "new" -> Strings.BS_NEW
    "in_progress" -> Strings.BS_IN_PROGRESS
    "accepted" -> Strings.BS_ACCEPTED
    "declined" -> Strings.BS_DECLINED
    "paid" -> Strings.BS_PAID
    "artist_on_the_way" -> Strings.BS_ON_THE_WAY
    "artist_at_hotel" -> Strings.BS_AT_HOTEL
    "artist_at_venue" -> Strings.BS_AT_VENUE
    "artist_finished_performance" -> Strings.BS_FINISHED
    "completed" -> Strings.BS_COMPLETED
    else -> null
}

/** «сейчас» / «5 мин» / «2 ч» / «3 д»: ключ строки и число. Бэкенд отдаёт ISO или «YYYY-MM-DD HH:MM:SS» (UTC). */
internal fun relativeTime(createdAt: String?, now: Instant): Pair<String, Long?>? {
    val instant = parseBackendInstant(createdAt) ?: return null
    val minutes = (now - instant).inWholeMinutes.coerceAtLeast(0)
    return when {
        minutes < 1 -> Strings.TIME_NOW to null
        minutes < 60 -> Strings.TIME_MIN to minutes
        minutes < 24 * 60 -> Strings.TIME_HOURS to minutes / 60
        else -> Strings.TIME_DAYS to minutes / (24 * 60)
    }
}

internal fun parseBackendInstant(value: String?): Instant? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val iso = raw.replace(' ', 'T').let { if (it.endsWith("Z") || it.contains('+') || Regex("-\\d\\d:\\d\\d$").containsMatchIn(it)) it else it + "Z" }
    return runCatching { Instant.parse(iso) }.getOrNull()
}

/** 10000.0 + "USD" → "10 000 $". Сумму считает бэкенд, клиент только форматирует. */
internal fun formatMoney(amount: Double, currency: String?): String {
    val whole = kotlin.math.round(amount).toLong()
    val grouped = whole.toString().reversed().chunked(3).joinToString("\u00A0").reversed()
    val symbol = when (currency?.uppercase()) {
        "USD", null -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "RUB" -> "₽"
        "TRY" -> "₺"
        else -> currency.uppercase()
    }
    return "$grouped\u00A0$symbol"
}

/** Абсолютный адрес для перехода из уведомления: относительные пути бэкенда — на сайт. */
/**
 * Ссылка уведомления ведёт на страницу артиста сайта (`/artist/<id>` или `https://djmetry.com/artist/<id>`) —
 * вернуть id, чтобы открыть карточку в приложении, а не в браузере. Иначе null.
 */
internal fun artistIdFromUrl(url: String?, baseUrl: String): String? {
    val path = url?.trim()?.removePrefix(baseUrl)?.takeIf { it.startsWith("/artist/") } ?: return null
    return path.removePrefix("/artist/").substringBefore('?').substringBefore('#').substringBefore('/').takeIf { it.isNotBlank() }
}

/**
 * Артист из ссылки пуша / уведомления: страница артиста или Release Radar (`…/release-radar?artist=<id>&album=…`).
 */
internal fun pushArtistId(url: String?, baseUrl: String): String? =
    artistIdFromUrl(url, baseUrl) ?: url?.takeIf { "release-radar" in it }?.substringAfter("?", "")?.split('&')
        ?.firstOrNull { it.startsWith("artist=") }?.removePrefix("artist=")?.substringBefore('#')?.takeIf { it.isNotBlank() }

internal fun notificationTarget(url: String?, baseUrl: String): String? = when {
    url.isNullOrBlank() -> null
    url.startsWith("/") && !url.startsWith("//") -> baseUrl + url
    url.startsWith("https://") && trustedLinkHost(url, baseUrl) -> url
    else -> null
}

/** Ссылки бэкенда — сайт и Spotify. Остальное не открываем: «пуш» может подсунуть любое приложение на телефоне. */
private val TRUSTED_LINK_HOSTS = setOf("open.spotify.com", "spotify.com")

private fun trustedLinkHost(url: String, baseUrl: String): Boolean {
    val host = runCatching { io.ktor.http.Url(url).host.lowercase() }.getOrNull() ?: return false
    val own = runCatching { io.ktor.http.Url(baseUrl).host.lowercase() }.getOrNull() ?: return false
    return host == own || host.endsWith(".$own") || host in TRUSTED_LINK_HOSTS || TRUSTED_LINK_HOSTS.any { host.endsWith(".$it") }
}


/** Концерт из уведомления: артист и событие (`event_id`) — Радар, «Концерты», прокрутка к нему. */
data class ConcertOpen(val artistId: String?, val eventId: String?)

/** Перейти по уведомлению (строка колокольчика) — ставит MainShell, тот же путь, что у пуша. */
val LocalOpenNotification = androidx.compose.runtime.staticCompositionLocalOf<(NotificationRoute) -> Unit> { {} }

/** Куда ведёт уведомление (пуш или строка колокольчика). */
sealed interface NotificationRoute {
    data class Release(val open: com.djmetry.data.radar.ReleaseOpen) : NotificationRoute
    data class Concert(val open: ConcertOpen) : NotificationRoute
    data class Booking(val open: com.djmetry.data.booking.BookingOpen) : NotificationRoute
    data class Artist(val id: String) : NotificationRoute
    data class Web(val url: String) : NotificationRoute
}

/**
 * Один разбор для пушей и колокольчика: по `type` и meta (`album_id`, `event_id`, `request_id`…), затем по ссылке.
 * Релиз → страница релизов артиста; концерт → Радар; букинг → заявка; иначе карточка артиста или сайт.
 */
internal fun routeNotification(url: String?, type: String?, meta: kotlinx.serialization.json.JsonObject?, baseUrl: String): NotificationRoute? {
    fun m(k: String) = (meta?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    com.djmetry.data.radar.releaseLink(url, type, meta)?.let { return NotificationRoute.Release(it) }
    if (type == "concert" || (type == null && m("event_id") != null)) {
        val artist = m("spotify_artist_id") ?: artistIdFromUrl(url, baseUrl)
        if (artist != null || m("event_id") != null) return NotificationRoute.Concert(ConcertOpen(artist, m("event_id")))
    }
    com.djmetry.data.booking.bookingLink(url, baseUrl, type, meta)?.let { return NotificationRoute.Booking(it) }
    pushArtistId(url, baseUrl)?.let { return NotificationRoute.Artist(it) }
    return notificationTarget(url, baseUrl)?.let { NotificationRoute.Web(it) }
}
