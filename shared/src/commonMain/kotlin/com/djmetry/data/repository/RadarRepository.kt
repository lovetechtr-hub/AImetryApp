package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.api.endpoints.RadarApi
import com.djmetry.api.endpoints.SettingsApi
import com.djmetry.api.models.ArtistReleasesResponse
import com.djmetry.api.models.ReleaseRadarFeedArtist
import com.djmetry.data.radar.RadarConcert
import com.djmetry.data.radar.RadarLocation
import com.djmetry.data.radar.eventNearUser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Вкладка «Радар» (docs/BACKEND_API.md → «Радары»): релизы подписок — лента бэкенда; концертов по подпискам
 * у бэкенда нет — собираем `/artists/:id/events` по каждой подписке, по 4 одновременно (как сайт), с кэшем.
 * «Новое» — непрочитанные уведомления release_radar / concert этого артиста (флаг `has_new_releases`
 * значит лишь «есть релизы с 2026-01-01»).
 */
class RadarRepository(
    private val radarApi: RadarApi,
    private val artistApi: ArtistApi,
    private val notificationsApi: NotificationsApi,
    private val settingsApi: SettingsApi,
    private val now: () -> kotlin.time.Instant = { Clock.System.now() },
) {
    private val lock = Mutex()
    private var feedCache: Pair<kotlin.time.Instant, List<ReleaseRadarFeedArtist>>? = null
    private val eventsCache = mutableMapOf<String, Pair<kotlin.time.Instant, List<com.djmetry.api.models.ArtistEvent>>>()

    suspend fun feed(refresh: Boolean = false): Result<List<ReleaseRadarFeedArtist>> {
        if (!refresh) lock.withLock { feedCache?.takeIf { now() - it.first < CACHE_TTL }?.second }?.let { return Result.success(it) }
        return radarApi.releaseFeed(PER_ARTIST).map { it.artists }.onSuccess { list -> lock.withLock { feedCache = now() to list } }
    }

    /** Артисты с непрочитанным: агрегат бэкенда (`/me/radar/artists`), на старом бэкенде — по уведомлениям. */
    suspend fun unreadArtists(): Set<String> =
        radarApi.radarArtists().getOrNull()?.artists?.filter { it.unread_releases + it.unread_concerts > 0 }?.map { it.spotify_artist_id }?.toSet()
            ?: unreadFromNotifications()

    /** Погасить «новое» у артиста (открыли его релизы или «историю»). */
    suspend fun markSeen(kind: String, artistId: String?): Result<Unit> = radarApi.seen(kind, artistId)

    /**
     * Концерты подписок одним списком бэкенда (`/me/concerts`, страницы по 200) — вместо запроса на каждую подписку.
     * null — эндпоинта ещё нет (старый бэкенд), тогда [concerts] с обходом подписок.
     */
    suspend fun serverConcerts(): List<RadarConcert>? {
        val all = mutableListOf<com.djmetry.api.models.MeConcert>()
        var offset = 0
        while (true) {
            val page = radarApi.concerts(offset).getOrNull() ?: return if (offset == 0) null else all.map(::toConcert)
            all += page.concerts
            offset += page.concerts.size
            if (page.concerts.isEmpty() || offset >= page.total || offset >= MAX_CONCERTS) break
        }
        return all.map(::toConcert)
    }

    private fun toConcert(c: com.djmetry.api.models.MeConcert) = RadarConcert(
        c.spotify_artist_id, c.artist_name, c.artist_image_url,
        com.djmetry.api.models.ArtistEvent(
            eventId = c.event_id, datetime = c.datetime, title = c.title, url = c.url,
            venue = com.djmetry.api.models.EventVenue(c.venue_name, c.city, c.region, c.country, c.lat, c.lng),
        ),
        c.near,
    )

    private suspend fun unreadFromNotifications(): Set<String> = coroutineScope {
        listOf("release_radar", "concert").map { type ->
            async { notificationsApi.list(type = type, limit = 50).getOrNull()?.items.orEmpty() }
        }.awaitAll().flatten().filter { !it.read }
            .mapNotNull { n -> n.meta?.get("spotify_artist_id")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }.toSet()
    }

    /** Город и страна для «Рядом со мной»: Concert Radar (или профиль). [countryNames] — названия страны для сверки. */
    suspend fun location(countryNames: (String) -> List<String>): RadarLocation {
        val s = settingsApi.concertAlerts().getOrNull()
        val iso = s?.effectiveCountry?.takeIf { it.isNotBlank() }
        return RadarLocation(s?.effectiveCity, iso, iso?.let(countryNames).orEmpty())
    }

    /**
     * Концерты подписок, ближайшие первыми. [onProgress] — сколько артистов уже проверено (для «Ищем концерты… 23 из 67»).
     * Упавший запрос одного артиста не ломает список.
     */
    suspend fun concerts(
        artists: List<ReleaseRadarFeedArtist>, loc: RadarLocation, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RadarConcert> = coroutineScope {
        val gate = Semaphore(CONCURRENCY)
        var done = 0
        val today = now().toString().take(10)
        artists.map { a ->
            async {
                val events = gate.withPermit { events(a.spotify_artist_id) }
                lock.withLock { done++; onProgress(done, artists.size) }
                events.filter { it.datetime.take(10) >= today }.map { e ->
                    RadarConcert(a.spotify_artist_id, a.artist_name, a.artist_image_url, e, eventNearUser(e.venue, loc))
                }
            }
        }.awaitAll().flatten().sortedBy { it.event.datetime }
    }

    private suspend fun events(id: String): List<com.djmetry.api.models.ArtistEvent> {
        lock.withLock { eventsCache[id]?.takeIf { now() - it.first < CACHE_TTL }?.second }?.let { return it }
        val list = artistApi.events(id).getOrNull()?.events ?: return emptyList()
        lock.withLock { eventsCache[id] = now() to list }
        return list
    }

    suspend fun artistReleases(id: String, q: String?, sort: String, offset: Int): Result<ArtistReleasesResponse> =
        radarApi.artistReleases(id, q, sort, offset, RELEASES_PAGE)

    companion object {
        const val PER_ARTIST = 5
        const val CONCURRENCY = 4
        const val RELEASES_PAGE = 24
        /** Потолок концертов в Радаре (страниц по 200). */
        const val MAX_CONCERTS = 2000
        val CACHE_TTL = 15.minutes
    }
}
