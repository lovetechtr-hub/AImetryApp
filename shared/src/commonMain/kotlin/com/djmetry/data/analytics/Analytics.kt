package com.djmetry.data.analytics

import com.djmetry.api.models.BreakdownResponse
import com.djmetry.api.models.BreakdownRow
import com.djmetry.api.models.NetworkResponse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Источник аналитики (спека §15). BIO — любому владельцу BIO-страницы; DJMetry (карточка в каталоге) — только
 * проверенному артисту. «Аудитория» — отдельная задача (журнал docs/RULES.md).
 */
enum class AnalyticsSource(internal val base: String, internal val networkPath: String) {
    Bio("me/music-page/analytics", "bio-network"),
    DJMetry("me/artist-catalog/analytics", "network"),
}

/** Пресеты периода — считает бэкенд (дни в UTC), клиент шлёт только ключ. */
enum class RangePreset(val key: String) { All("all"), D7("7d"), D30("30d"), D90("90d"), D180("180d"), Ytd("ytd") }

sealed interface AnalyticsPeriod {
    data class Preset(val preset: RangePreset) : AnalyticsPeriod
    /** Свои даты: `range=custom&from_date&to_date`, не длиннее [MAX_CUSTOM_DAYS]. */
    data class Custom(val from: LocalDate, val to: LocalDate) : AnalyticsPeriod
}

/** Бэкенд отклоняет свой период длиннее 732 дней (`range_too_long`). */
const val MAX_CUSTOM_DAYS = 732

/** Период по умолчанию — 7 дней, как на сайте. */
data class AnalyticsQuery(
    val period: AnalyticsPeriod = AnalyticsPeriod.Preset(RangePreset.D7),
    /** ISO2 страны или null — все страны. */
    val country: String? = null,
    /** Город — только вместе со страной (иначе `city_requires_country_code`). */
    val city: String? = null,
)

/** Query-параметры запроса аналитики. [withGeo] = false — для `geo-options` (он читает только даты). */
fun AnalyticsQuery.params(withGeo: Boolean = true): List<Pair<String, String>> = buildList {
    when (val p = period) {
        is AnalyticsPeriod.Preset -> add("range" to p.preset.key)
        is AnalyticsPeriod.Custom -> {
            add("range" to "custom"); add("from_date" to p.from.toString()); add("to_date" to p.to.toString())
        }
    }
    val c = country?.trim()?.uppercase()?.takeIf { it.length == 2 }
    if (withGeo && c != null) {
        add("country_code" to c)
        city?.trim()?.takeIf { it.isNotEmpty() }?.let { add("city" to it.take(120)) }
    }
}

enum class CustomRangeProblem { FromAfterTo, TooLong }

/** Проверка своего периода до запроса — те же правила, что на бэке. */
fun validateCustomRange(from: LocalDate, to: LocalDate): CustomRangeProblem? = when {
    from > to -> CustomRangeProblem.FromAfterTo
    from.daysUntil(to) + 1 > MAX_CUSTOM_DAYS -> CustomRangeProblem.TooLong
    else -> null
}

// ── График активности ───────────────────────────────────────────────────────

enum class ActivityBucket { Day, Week, Month }

data class ActivityPoint(val start: LocalDate, val visits: Int, val clicks: Int, val unique: Int)

/**
 * Итог периода, разложенный на [n] точек слегка растущей кривой; сумма сохраняется точно.
 * Порт `distributeShaped` сайта (musicAnalyticsActivitySeries.ts): вес `0.35 + 0.65·(i+1)/n`,
 * округление вниз, остаток по +1 с начала. График строится из итогов, а не из timeseries —
 * продуктовое решение «единая правда = totals», чтобы цифры совпадали с сайтом.
 */
fun distributeShaped(total: Int, n: Int): List<Int> {
    if (n <= 0) return emptyList()
    if (total <= 0) return List(n) { 0 }
    val w = List(n) { i -> 0.35 + 0.65 * (i + 1) / n }
    val sum = w.sum()
    val ints = w.map { floor(it / sum * total).toInt() }.toMutableList()
    var rem = total - ints.sum()
    var i = 0
    while (rem > 0) { ints[i % n]++; rem--; i++ }
    return ints
}

/**
 * Точки графика за период [from]…[to] (из `range` ответа). День — каждый день; неделя — блоки по 7 дней от начала
 * периода (последний может быть неполным); месяц — календарные месяцы. Как `buildBioSyntheticSeries` сайта.
 */
fun buildActivity(from: LocalDate, to: LocalDate, visits: Int, clicks: Int, unique: Int, bucket: ActivityBucket): List<ActivityPoint> {
    if (from > to) return emptyList()
    return when (bucket) {
        ActivityBucket.Month -> {
            val months = generateSequence(LocalDate(from.year, from.month, 1)) { it.plus(1, DateTimeUnit.MONTH) }
                .takeWhile { it <= LocalDate(to.year, to.month, 1) }.toList()
            zipPoints(months, visits, clicks, unique)
        }
        ActivityBucket.Day, ActivityBucket.Week -> {
            val days = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
            val daily = zipPoints(days, visits, clicks, unique)
            if (bucket == ActivityBucket.Day) daily
            else daily.chunked(7).map { b -> ActivityPoint(b.first().start, b.sumOf { it.visits }, b.sumOf { it.clicks }, b.sumOf { it.unique }) }
        }
    }
}

private fun zipPoints(starts: List<LocalDate>, visits: Int, clicks: Int, unique: Int): List<ActivityPoint> {
    val v = distributeShaped(visits, starts.size); val c = distributeShaped(clicks, starts.size); val u = distributeShaped(unique, starts.size)
    return starts.mapIndexed { i, d -> ActivityPoint(d, v[i], c[i], u[i]) }
}

/** Границы периода из ответа (`range.from/to`); нет — последние 7 дней до [today], как на сайте. */
fun periodBounds(from: String?, to: String?, today: LocalDate): Pair<LocalDate, LocalDate> {
    val f = from?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val t = to?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (f != null && t != null && f <= t) f to t else today.minus(6, DateTimeUnit.DAY) to today
}

/** Разбивка по умолчанию: до 45 дней — дни, до года — недели, дальше — месяцы. */
fun defaultBucket(from: LocalDate, to: LocalDate): ActivityBucket = when (from.daysUntil(to) + 1) {
    in 0..45 -> ActivityBucket.Day
    in 46..366 -> ActivityBucket.Week
    else -> ActivityBucket.Month
}

// ── KPI ─────────────────────────────────────────────────────────────────────

/**
 * Плитки сверху. Клики — `total_clicks` (ось CTR), без него — все события. Уникальные — сумма по сегментам.
 * «Партнёрские клики» бэкенд не отдаёт ни в одном эндпоинте — плитку не показываем (задача бэкенду в журнале).
 */
data class AnalyticsKpis(val visits: Int, val clicks: Int, val unique: Int, val leads: Int, val topCountry: String?) {
    /** CTR в процентах: клики / визиты; визитов нет — null. */
    val ctr: Double? get() = if (visits > 0) clicks * 100.0 / visits else null
}

fun kpis(network: NetworkResponse, breakdown: BreakdownResponse?): AnalyticsKpis = AnalyticsKpis(
    visits = network.totals.total_page_views,
    clicks = network.totals.total_clicks ?: network.totals.total_click_events,
    unique = network.by_segment.sumOf { it.unique_viewers },
    leads = breakdown?.secret_link_leads?.total ?: 0,
    topCountry = breakdown?.countries?.firstOrNull()?.country_code?.uppercase(),
)

/** Визиты DJMetry-юзеров (раздел «Сеть»): кто из залогиненных смотрел страницу. */
data class NetworkSplit(val anonymous: Int, val artists: Int, val booking: Int, val otherLogged: Int) {
    val total: Int get() = anonymous + artists + booking + otherLogged
}

fun networkSplit(network: NetworkResponse): NetworkSplit = with(network.totals) {
    NetworkSplit(anonymous_page_views, verified_artist_page_views, booking_company_page_views, other_logged_page_views)
}

/** Доли строк разбивки (устройства, браузеры, ОС) по визитам; «unknown» — в конец. */
data class Share(val label: String, val visits: Int, val fraction: Double)

fun shares(rows: List<BreakdownRow>, limit: Int = 4): List<Share> {
    val total = rows.sumOf { it.visits }.takeIf { it > 0 } ?: return emptyList()
    val sorted = rows.filter { it.visits > 0 }.sortedWith(compareBy<BreakdownRow> { (it.name ?: "").equals("unknown", true) }.thenByDescending { it.visits })
    val head = sorted.take(limit).map { Share(it.name ?: "unknown", it.visits, it.visits.toDouble() / total) }
    val rest = sorted.drop(limit).sumOf { it.visits }
    return if (rest > 0) head + Share("other", rest, rest.toDouble() / total) else head
}

// ── Карта ───────────────────────────────────────────────────────────────────

/** Страна на карте: [share] — доля от самой посещаемой (0…1), подсветка и размер пузыря. */
data class MapCountry(val iso: String, val visits: Int, val share: Double, val lat: Double, val lon: Double)

/** Страны из разбивки, которые можно поставить на карту (есть центр), по убыванию визитов. */
fun mapCountries(rows: List<BreakdownRow>): List<MapCountry> {
    val byIso = rows.mapNotNull { r -> r.country_code?.uppercase()?.takeIf { it.length == 2 && r.visits > 0 }?.let { it to r.visits } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    val max = byIso.values.maxOrNull() ?: return emptyList()
    return byIso.mapNotNull { (iso, v) -> COUNTRY_CENTROIDS[iso]?.let { (lat, lon) -> MapCountry(iso, v, v.toDouble() / max, lat, lon) } }
        .sortedByDescending { it.visits }
}

/** Пузыри стран — GeoJSON точек; `k` = √доли (площадь пузыря пропорциональна визитам). */
fun bubblesGeoJson(countries: List<MapCountry>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        countries.forEach { c ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") { put("iso", c.iso); put("v", c.visits); put("k", sqrt(c.share)) }
                putJsonObject("geometry") { put("type", "Point"); putJsonArray("coordinates") { add(c.lon); add(c.lat) } }
            }
        }
    }
}.toString()

/** Непрозрачность зелёной подсветки страны: от едва заметной у редких до яркой у лидера. */
fun choroplethAlpha(share: Double): Float = (0.14 + 0.5 * share.coerceIn(0.0, 1.0)).toFloat()

/** Бесплатные векторные тайлы и шрифты OpenFreeMap — без ключей и лимитов. */
internal const val OPENFREEMAP_TILES = "https://tiles.openfreemap.org/planet"
internal const val OPENFREEMAP_GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

/** Палитра подложки карты: тёмная (по умолчанию, цвета DJMetry) и светлая — как переключатель темы на сайте. */
data class MapPalette(val land: String, val water: String, val border: String, val road: String, val label: String, val cityLabel: String, val halo: String) {
    companion object {
        val Dark = MapPalette("#101A2C", "#070D18", "#24354F", "#1A2740", "#6F84A8", "#9AB0D5", "#070D18")
        val Light = MapPalette("#F4F1EA", "#CFE0EC", "#C9C2B4", "#FFFFFF", "#7C8595", "#4B5563", "#F4F1EA")
    }
}

/** Тёмный стиль карты аналитики (без улиц — только страны). */
fun analyticsMapStyle(lang: String): String = mapStyle(lang)

/**
 * Стиль карты в цветах DJMetry поверх тайлов OpenFreeMap (схема OpenMapTiles): суша-фон, вода, границы стран,
 * [streets] — дороги при приближении (карта диджеев до уровня площадки), подписи стран и городов на языке [lang]
 * (нет перевода — латиница). Слои с данными (подсветка, пузыри, маркеры) добавляются поверх.
 */
fun mapStyle(lang: String, palette: MapPalette = MapPalette.Dark, streets: Boolean = false): String = buildJsonObject {
    put("version", 8)
    put("name", "DJMetry")
    put("glyphs", OPENFREEMAP_GLYPHS)
    putJsonObject("sources") { putJsonObject("omt") { put("type", "vector"); put("url", OPENFREEMAP_TILES) } }
    val label = buildJsonArray {
        add("coalesce"); addJsonArray { add("get"); add("name:$lang") }; addJsonArray { add("get"); add("name:latin") }; addJsonArray { add("get"); add("name") }
    }
    putJsonArray("layers") {
        addJsonObject { put("id", "land"); put("type", "background"); putJsonObject("paint") { put("background-color", palette.land) } }
        addJsonObject {
            put("id", "water"); put("type", "fill"); put("source", "omt"); put("source-layer", "water")
            putJsonObject("paint") { put("fill-color", palette.water) }
        }
        if (streets) addJsonObject {
            put("id", "roads"); put("type", "line"); put("source", "omt"); put("source-layer", "transportation"); put("minzoom", 7)
            putJsonObject("paint") {
                put("line-color", palette.road)
                putJsonArray("line-width") { add("interpolate"); addJsonArray { add("linear") }; addJsonArray { add("zoom") }; add(7); add(0.4); add(16); add(6) }
            }
        }
        addJsonObject {
            put("id", "borders"); put("type", "line"); put("source", "omt"); put("source-layer", "boundary")
            put("filter", filterEq("admin_level", 2))
            putJsonObject("paint") { put("line-color", palette.border); put("line-width", 0.7) }
        }
        addJsonObject {
            put("id", "country-labels"); put("type", "symbol"); put("source", "omt"); put("source-layer", "place"); put("minzoom", 2)
            put("filter", filterEqStr("class", "country"))
            putJsonObject("layout") { put("text-field", label); putJsonArray("text-font") { add("Noto Sans Regular") }; put("text-size", 11) }
            putJsonObject("paint") { put("text-color", palette.label); put("text-halo-color", palette.halo); put("text-halo-width", 1) }
        }
        addJsonObject {
            put("id", "city-labels"); put("type", "symbol"); put("source", "omt"); put("source-layer", "place"); put("minzoom", 4)
            put("filter", filterEqStr("class", "city"))
            putJsonObject("layout") { put("text-field", label); putJsonArray("text-font") { add("Noto Sans Regular") }; put("text-size", 10) }
            putJsonObject("paint") { put("text-color", palette.cityLabel); put("text-halo-color", palette.halo); put("text-halo-width", 1) }
        }
    }
}.toString()

private fun filterEq(key: String, value: Int): JsonArray = buildJsonArray { add("=="); addJsonArray { add("get"); add(key) }; add(JsonPrimitive(value)) }
private fun filterEqStr(key: String, value: String): JsonArray = buildJsonArray { add("=="); addJsonArray { add("get"); add(key) }; add(value) }
