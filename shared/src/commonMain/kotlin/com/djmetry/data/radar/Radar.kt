package com.djmetry.data.radar

import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.EventVenue
import com.djmetry.api.models.ReleaseRadarFeedArtist

/** Концерт артиста из подписок. [near] — в городе/стране пользователя (настройки Concert Radar или профиль). */
data class RadarConcert(
    val artistId: String,
    val artistName: String,
    val artistImage: String?,
    val event: ArtistEvent,
    val near: Boolean,
)

/** Где пользователь для «Рядом со мной»: город и страна ISO2 (effective из `/me/concert-alerts`). */
data class RadarLocation(val city: String?, val countryIso: String?, val countryNames: List<String> = emptyList()) {
    val known: Boolean get() = !city.isNullOrBlank() || !countryIso.isNullOrBlank()
}

private fun norm(s: String?): String = s.orEmpty().trim().lowercase()

/**
 * Концерт рядом — как на бэкенде (`eventMatchesUserLocation`): Bandsintown отдаёт город и страну текстом.
 * Город — равенство или вхождение; страна — ISO2 точно, полное название (в т.ч. на английском) — по вхождению.
 * Заданы и город, и страна — нужно совпадение обоих.
 */
fun eventNearUser(venue: EventVenue?, loc: RadarLocation): Boolean {
    if (venue == null || !loc.known) return false
    val vc = norm(venue.city); val vco = norm(venue.country)
    val uc = norm(loc.city)
    val tokens = (listOfNotNull(loc.countryIso) + loc.countryNames).map(::norm).filter { it.isNotEmpty() }.distinct()
    val cityOk = uc.isEmpty() || (vc.isNotEmpty() && (vc == uc || vc.contains(uc) || uc.contains(vc)))
    val countryOk = tokens.isEmpty() || tokens.any { t ->
        vco.isNotEmpty() && (t == vco || (t.length > 2 && vco.length > 2 && (vco.contains(t) || t.contains(vco))))
    }
    return when {
        uc.isNotEmpty() && tokens.isNotEmpty() -> cityOk && countryOk
        uc.isNotEmpty() -> cityOk
        else -> countryOk
    }
}

/** Дата концерта `YYYY-MM-DD` из `2026-10-12T23:00:00` — для сортировки и группировки. */
fun concertDay(c: RadarConcert): String = c.event.datetime.take(10)

/** Группы по месяцам (`YYYY-MM`) в порядке дат. */
fun groupByMonth(concerts: List<RadarConcert>): List<Pair<String, List<RadarConcert>>> =
    concerts.sortedBy { it.event.datetime }.groupBy { it.event.datetime.take(7) }.toList()

/**
 * Порядок «историй» (аватаров подписок): сначала с непрочитанным (новый релиз или концерт в колокольчике),
 * потом с концертом в ближайшие 30 дней, потом остальные по имени.
 */
fun storyOrder(artists: List<ReleaseRadarFeedArtist>, unread: Set<String>, soonConcert: Set<String>): List<ReleaseRadarFeedArtist> =
    artists.sortedWith(
        compareByDescending<ReleaseRadarFeedArtist> { it.spotify_artist_id in unread }
            .thenByDescending { it.spotify_artist_id in soonConcert }
            .thenBy { it.artist_name.lowercase() }
    )

/** Поиск артиста в подписках: без учёта регистра, по вхождению. */
fun matchesArtist(name: String, query: String): Boolean = query.isBlank() || name.lowercase().contains(query.trim().lowercase())

/** Есть ли у артиста «ещё» релизы сверх показанных — кнопка «Все (N)». */
fun hasMoreReleases(a: ReleaseRadarFeedArtist): Boolean = !a.showing_older && a.total_releases > a.latest.size

/** Релиз вышел за последние [days] дней (бейдж NEW). Частичные даты (`YYYY`, `YYYY-MM`) — не новые. */
fun isFreshRelease(releaseDate: String?, today: kotlinx.datetime.LocalDate, days: Int = 14): Boolean {
    val d = releaseDate?.takeIf { it.length == 10 }?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() } ?: return false
    val diff = today.toEpochDays() - d.toEpochDays()
    return diff in 0..days.toLong()
}

/** Что открыть по уведомлению о релизе: все релизы артиста с прокруткой к [albumId]. */
data class ReleaseOpen(val artistId: String, val albumId: String?, val artistName: String? = null, val artistImage: String? = null)

/**
 * Уведомление Release Radar: ссылка `/dashboard/music/release-radar?artist=<id>&album=<id>` и/или `meta`
 * (`spotify_artist_id`, `album_id`). null — это не релиз.
 */
fun releaseLink(url: String?, type: String? = null, meta: kotlinx.serialization.json.JsonObject? = null): ReleaseOpen? {
    fun m(k: String) = (meta?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    val query = url?.takeIf { "release-radar" in it }?.substringAfter('?', "")?.substringBefore('#')?.split('&')
        ?.mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap().orEmpty()
    if (type != "release_radar" && query.isEmpty()) return null
    val artist = query["artist"] ?: m("spotify_artist_id") ?: return null
    return ReleaseOpen(artist, query["album"] ?: m("album_id"), m("artist_name"), m("artist_image_url") ?: m("image_url"))
}
