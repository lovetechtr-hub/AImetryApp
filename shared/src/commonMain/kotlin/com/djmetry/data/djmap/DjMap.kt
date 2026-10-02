package com.djmetry.data.djmap

import com.djmetry.api.models.MapEventPoint
import com.djmetry.api.models.MapVenue
import com.djmetry.api.models.MapVenueArtist
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Instant

/** Слой карты — как вкладки сайта; ключ — параметр `layer` ссылки `djmetry.com/map?layer=`. */
enum class MapLayer(val key: String) { Performances("performances"), Density("density"), Origins("origins"), Venues("venues") }

/** Тип площадки в фильтре: все / фестивали / клубы (`type=festival|club`). */
enum class VenueTypeFilter(val key: String?) { All(null), Festival("festival"), Club("club") }

/** Фильтры карты. [country] — ИМЯ страны (как у Bandsintown), не ISO. */
data class MapFilters(
    val genre: String? = null,
    val country: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: VenueTypeFilter = VenueTypeFilter.All,
) {
    val activeCount: Int get() = listOfNotNull(genre, country, from, to).size + if (type != VenueTypeFilter.All) 1 else 0
}

/**
 * Видимая область карты → область для API. Карта прокручивается по кругу: долготы выходят за ±180, а у области
 * через линию перемены дат запад больше востока. Такую область (и почти весь мир) грузим целиком — иначе
 * точки по одну сторону не приходили, а сравнение «уже загружено» никогда не совпадало (запрос на каждой остановке).
 */
fun normalizedBounds(west: Double, south: Double, east: Double, north: Double): Bounds {
    val s = south.coerceIn(-85.0, 85.0); val n = north.coerceIn(-85.0, 85.0)
    if (east - west >= 360.0 || west > east) return Bounds(-180.0, s, 180.0, n)
    // Сдвиг на целое число оборотов: запад — в [-180, 180)
    val turns = kotlin.math.floor((west + 180.0) / 360.0)
    val w = west - turns * 360.0; val e = east - turns * 360.0
    return if (e > 180.0) Bounds(-180.0, s, 180.0, n) else Bounds(w, s, e, n)
}

/**
 * Рамка тура по точкам: выбираем более короткий обход по долготе. Тур Токио → Лос-Анджелес кадрируется через
 * Тихий океан (восток может быть > 180 — MapLibre это понимает), а не через Европу и Африку.
 */
fun tourFrame(points: List<Pair<Double, Double>>): Bounds? {
    if (points.isEmpty()) return null
    val lats = points.map { it.first }
    val lngs = points.map { it.second }
    val shifted = lngs.map { if (it < 0) it + 360.0 else it }
    val direct = lngs.max() - lngs.min()
    val across = shifted.max() - shifted.min()
    return if (across < direct) Bounds(shifted.min(), lats.min(), shifted.max(), lats.max())
    else Bounds(lngs.min(), lats.min(), lngs.max(), lats.max())
}

/** Видимая область: запад, юг, восток, север (градусы). */
data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    /** bbox для API: `запад,юг,восток,север`. */
    val bbox: String get() = listOf(west, south, east, north).joinToString(",") { ((it * 1e4).roundToInt() / 1e4).toString() }

    /** Расширить на [factor] с каждой стороны — запас, чтобы небольшой сдвиг не перезапрашивал данные (как на сайте, +20%). */
    fun padded(factor: Double = 0.2): Bounds {
        val dx = (east - west) * factor; val dy = (north - south) * factor
        return Bounds((west - dx).coerceAtLeast(-180.0), (south - dy).coerceAtLeast(-85.0), (east + dx).coerceAtMost(180.0), (north + dy).coerceAtMost(85.0))
    }

    operator fun contains(o: Bounds): Boolean = o.west >= west && o.east <= east && o.south >= south && o.north <= north
}

private fun MutableList<Pair<String, String>>.opt(k: String, v: String?) { v?.trim()?.takeIf { it.isNotEmpty() }?.let { add(k to it) } }

/** Выступления всех DJ: без zoom — сервер отдаёт точки, кластеризуем сами (как сайт). */
fun MapFilters.performanceParams(bounds: Bounds?, limit: Int): List<Pair<String, String>> = buildList {
    opt("genre", genre); opt("country", country); opt("from", from?.toString()); opt("to", to?.toString())
    bounds?.let { add("bbox" to it.bbox) }
    add("limit" to limit.toString())
}

/** Все выступления одного DJ (без bbox). */
fun MapFilters.artistParams(artistId: String): List<Pair<String, String>> = buildList {
    add("artist_id" to artistId); opt("genre", genre); opt("country", country); opt("from", from?.toString()); opt("to", to?.toString())
    add("limit" to "3000")
}

fun MapFilters.topTouringParams(): List<Pair<String, String>> = buildList {
    add("limit" to "12"); opt("genre", genre); opt("country", country); opt("from", from?.toString()); opt("to", to?.toString())
}

fun MapFilters.venuesParams(bounds: Bounds?, zoom: Int, limit: Int): List<Pair<String, String>> = buildList {
    opt("type", type.key); opt("country", country)
    bounds?.let { add("bbox" to it.bbox) }
    add("zoom" to zoom.toString()); add("limit" to limit.toString())
}

/** «ТОП стран»: уровень детализации по зуму — как на сайте: z = round(zoom), < 4 страны, < 6 города, дальше площадки. */
enum class DensityLevel(val key: String) { Country("country"), City("city"), Venue("venue") }

fun densityLevel(zoom: Double): DensityLevel = zoom.roundToInt().let { z -> if (z < 4) DensityLevel.Country else if (z < 6) DensityLevel.City else DensityLevel.Venue }

fun MapFilters.densityParams(level: DensityLevel): List<Pair<String, String>> = buildList {
    add("level" to level.key)
    when (level) {
        DensityLevel.Country -> opt("genre", genre)
        DensityLevel.City -> { opt("genre", genre); add("limit" to "300") }
        DensityLevel.Venue -> add("limit" to "400") // жанр на уровне площадок бэкенд не применяет
    }
}

/** Топ-10 площадок для ленты «Фестивали и клубы». */
fun MapFilters.topVenuesParams(): List<Pair<String, String>> = buildList {
    add("level" to "venue"); opt("type", type.key); opt("country", country); add("limit" to "10")
}

// ── Время ──────────────────────────────────────────────────────────────────

/** Дата события: строка Bandsintown `2026-10-04T22:00:00` (без зоны — местное время площадки, считаем UTC). */
fun eventInstant(datetime: String?): Instant? {
    val s = datetime?.trim()?.takeIf { it.length >= 10 } ?: return null
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.take(19).let { if (it.length == 10) "${it}T00:00:00" else it }).toInstant(TimeZone.UTC) }.getOrNull()
}

/**
 * Режим «все диджеи»: одна точка на артиста — ближайшее предстоящее выступление, иначе самое свежее прошедшее
 * (как сайт). Порядок — как пришло с сервера (по дате).
 */
fun onePerArtist(points: List<MapEventPoint>, now: Instant): List<MapEventPoint> =
    points.groupBy { it.spotify_artist_id }.values.map { list ->
        val upcoming = list.filter { (eventInstant(it.datetime) ?: Instant.DISTANT_PAST) >= now }
        upcoming.minByOrNull { eventInstant(it.datetime)!! } ?: list.maxByOrNull { eventInstant(it.datetime) ?: Instant.DISTANT_PAST }!!
    }.let { chosen -> val ids = chosen.map { it.event_id }.toSet(); points.filter { it.event_id in ids } }

/** Остановка тура: город (или страна, или площадка) и точка события. */
data class TourStop(val city: String, val point: MapEventPoint)

/** Лента городов тура: по дате, одинаковые подряд идущие города схлопываются; меньше двух — ленты нет. */
fun tourStops(tour: List<MapEventPoint>): List<TourStop> {
    val stops = mutableListOf<TourStop>()
    tour.forEach { p ->
        val city = p.venue_city?.takeIf { it.isNotBlank() } ?: p.venue_country?.takeIf { it.isNotBlank() } ?: p.venue_name.orEmpty()
        if (stops.lastOrNull()?.city != city) stops += TourStop(city, p)
    }
    return if (stops.size >= 2) stops else emptyList()
}

// ── Цвета ──────────────────────────────────────────────────────────────────

private val GENRE_COLORS: List<Pair<Regex, Long>> = listOf(
    "tech house" to 0xFF22C1A6, "deep house" to 0xFF2BB3C0, "afro|amapiano" to 0xFFF2B134, "organic" to 0xFF84CC16,
    "melodic" to 0xFF8B5CF6, "prog" to 0xFF4AA8FF, "minimal" to 0xFF7C9CBF, "psy" to 0xFFA855F7, "trance" to 0xFFFF5C8A,
    "hard techno|hardgroove|schranz" to 0xFF4338CA, "techno" to 0xFF5A7BFF, "house" to 0xFFFF8F5E,
    "drum|d&b|dnb|jungle|breakbeat|break" to 0xFFEF4444, "dubstep|riddim" to 0xFFA3E635, "trap" to 0xFFFB7185,
    "big room|edm" to 0xFF2563EB, "electro" to 0xFF06B6D4, "hardstyle|hardcore|gabber|frenchcore" to 0xFFE11D48,
    "garage" to 0xFF34D399, "nu disco|disco|funk|boogie" to 0xFFFBBF24, "hip hop|rap" to 0xFFB45309,
    "ambient|downtempo|chill|lofi" to 0xFF5EEAD4, "synthwave|retrowave" to 0xFFF0ABFC, "indie|electronica|idm|dance" to 0xFF818CF8,
    "pop" to 0xFFF472B6, "bass" to 0xFF65A30D,
).map { (re, c) -> Regex(re) to c }

private val HASH_PALETTE = listOf(0xFF60A5FA, 0xFFF59E0B, 0xFF34D399, 0xFFF87171, 0xFFA78BFA, 0xFF22D3EE, 0xFFFB923C, 0xFF4ADE80, 0xFFE879F9, 0xFF94A3B8, 0xFFFACC15, 0xFF2DD4BF)

/**
 * Цвет жанра (ARGB) — точная копия `genreColor` сайта: первое совпадение семейства жанра, иначе стабильный цвет
 * из палитры по хешу строки. Им же красятся страны в «ТОП стран» (хеш ISO2) и рамки лидерборда.
 */
fun genreColor(value: String?): Long {
    val s = value?.trim()?.lowercase().orEmpty()
    if (s.isEmpty()) return 0xFF94A3B8
    GENRE_COLORS.firstOrNull { it.first.containsMatchIn(s) }?.let { return it.second }
    var h = 0L
    s.forEach { ch -> h = (h * 31 + ch.code) and 0xFFFFFFFFL }
    return HASH_PALETTE[(h % HASH_PALETTE.size).toInt()]
}

/** Доминирующий жанр страны в «Откуда диджеи»: больше всего DJ; нет жанров — null (страна не закрашивается). */
fun dominantGenre(genres: Map<String, Int>): String? = genres.filter { it.value > 0 }.maxByOrNull { it.value }?.key

/** Непрозрачность заливки страны: «ТОП стран» — 0.34…0.9, «Откуда диджеи» — 0.24…0.78 (как сайт). */
fun densityAlpha(count: Int, max: Int): Float = (0.34 + 0.56 * count / max.coerceAtLeast(1)).toFloat()
fun originAlpha(count: Int, max: Int): Float = ((0.24 + 0.54 * count / max.coerceAtLeast(1)) * 100).roundToInt() / 100f

// ── Площадки ───────────────────────────────────────────────────────────────

/** Вид площадки: топ-фестиваль, фестиваль, клуб (всё остальное, включая `venue`). */
enum class VenueKind(val ring: Long) { Top(0xFFFFB454), Festival(0xFF5EE6A8), Club(0xFF7DA7FF) }

fun venueKind(v: MapVenue): VenueKind = when {
    v.is_top -> VenueKind.Top
    v.venue_type == "festival" -> VenueKind.Festival
    else -> VenueKind.Club
}

/** Под фильтр «Клубы» не попадают фестивали и топы, под «Фестивали» — явные клубы (как сайт). */
fun conflictsWithType(v: MapVenue, filter: VenueTypeFilter): Boolean = when (filter) {
    VenueTypeFilter.All -> false
    VenueTypeFilter.Club -> v.is_top || v.venue_type == "festival"
    VenueTypeFilter.Festival -> v.venue_type == "club"
}

/** Адрес площадки → Google Maps (как сайт): с адресом — «имя, адрес, город, страна», без — координаты. */
fun googleMapsUrl(v: MapVenue): String {
    val q = if (!v.address.isNullOrBlank()) listOfNotNull(v.name, v.address, v.city, v.country).filter { it.isNotBlank() }.joinToString(", ")
    else "${v.lat},${v.lng}"
    return "https://www.google.com/maps/search/?api=1&query=${urlEncode(q)}" + (v.google_place_id?.let { "&query_place_id=${urlEncode(it)}" } ?: "")
}

internal fun urlEncode(s: String): String = buildString {
    s.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-_.!~*'()") append(c.toChar())
        else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
}

fun spotifyArtistUrl(id: String): String = "https://open.spotify.com/artist/$id"

/** Бейдж у первого предстоящего выступления в лайнапе: «Сейчас» (старт в ближайшие 6 ч) или «Ближайший». */
enum class LineupBadge { Now, Next }

data class LineupRow(val artist: MapVenueArtist, val badge: LineupBadge?)

/** «Кто играет»: без дублей, сначала предстоящие по дате, потом прошедшие от свежих; до 6 строк. */
fun lineup(artists: List<MapVenueArtist>, now: Instant): List<LineupRow> {
    val unique = artists.distinctBy { it.spotify_artist_id.ifBlank { it.slug ?: it.name } }
    val (up, past) = unique.partition { (eventInstant(it.datetime) ?: Instant.DISTANT_PAST) >= now }
    val sorted = (up.sortedBy { eventInstant(it.datetime) } + past.sortedByDescending { eventInstant(it.datetime) ?: Instant.DISTANT_PAST }).take(6)
    return sorted.mapIndexed { i, a ->
        val badge = if (i == 0 && up.isNotEmpty()) {
            val t = eventInstant(a.datetime)!!
            if ((t - now).inWholeHours < 6) LineupBadge.Now else LineupBadge.Next
        } else null
        LineupRow(a, badge)
    }
}

// ── Маршрут тура ───────────────────────────────────────────────────────────

/** Дуга маршрута: точки линии (долгота, широта), середина и курс самолётика (градусы по часовой от «носом вверх»). */
data class Arc(val line: List<Pair<Double, Double>>, val mid: Pair<Double, Double>, val headingDeg: Double)

private fun unmercator(x: Double, y: Double): Pair<Double, Double> {
    val lon = x * 360.0 - 180.0
    val lat = atan(kotlin.math.sinh(PI * (1 - 2 * y))) * 180.0 / PI
    return lon to lat
}

/**
 * Дуги между соседними выступлениями тура — как сайт: квадратичная кривая Безье в пикселях Web-Mercator текущего
 * зума, изгиб — перпендикуляр к хорде на min(длина·0.14, 140 px). Почти совпадающие точки пропускаются.
 */
fun tourArcs(tour: List<MapEventPoint>, zoom: Double, segments: Int = 64): List<Arc> {
    val world = 512.0 * 2.0.pow(zoom)
    return tour.zipWithNext().mapNotNull { (a, b) ->
        if (kotlin.math.abs(a.lat - b.lat) < 0.05 && kotlin.math.abs(a.lng - b.lng) < 0.05) return@mapNotNull null
        val (ax, ay) = mercator(a.lng, a.lat).let { it.first * world to it.second * world }
        val (bx, by) = mercator(b.lng, b.lat).let { it.first * world to it.second * world }
        val dx = bx - ax; val dy = by - ay; val len = hypot(dx, dy)
        val bend = min(len * 0.14, 140.0)
        val cx = (ax + bx) / 2 + (-dy / len) * bend
        val cy = (ay + by) / 2 + (dx / len) * bend
        fun at(t: Double): Pair<Double, Double> {
            val u = 1 - t
            return unmercator((u * u * ax + 2 * u * t * cx + t * t * bx) / world, (u * u * ay + 2 * u * t * cy + t * t * by) / world)
        }
        val heading = atan2(dx, -dy) * 180.0 / PI
        Arc((0..segments).map { at(it.toDouble() / segments) }, at(0.5), heading)
    }
}

/** Ссылка «Поделиться» — состояние карты как на сайте: `djmetry.com/map?layer=…&genre=…&artist_id=…`. */
fun mapShareUrl(base: String, layer: MapLayer, f: MapFilters, artistId: String?): String {
    val q = buildList {
        if (layer != MapLayer.Performances) add("layer" to layer.key)
        opt("genre", f.genre); opt("country", f.country); opt("from", f.from?.toString()); opt("to", f.to?.toString()); opt("type", f.type.key)
        opt("artist_id", artistId)
    }
    return "$base/map" + if (q.isEmpty()) "" else "?" + q.joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
}

