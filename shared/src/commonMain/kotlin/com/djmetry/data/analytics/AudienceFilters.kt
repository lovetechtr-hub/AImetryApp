package com.djmetry.data.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.*

/**
 * Конструктор сегментов аудитории (спека §15 «Конструктор сегментов», design/analytics/audience-filters-variants.html,
 * вариант A). Правило = поле · оператор · значение; все правила — по «И»; набор правил = сегмент. Каталог полей —
 * `GET /audience/filter-catalog`, при недоступности — [FALLBACK_FILTER_CATALOG] (как офлайн-каталог сайта).
 */

/** Как редактируется значение поля. */
enum class FilterValueKind {
    /** Целое ≥ 0 (для некоторых полей — пресеты). */
    Number,
    /** Список значений из вариантов каталога. */
    Multi,
    /** Список значений, можно ввести своё (страны, теги, источники). */
    FreeMulti,
    /** Одна строка. */
    Text,
    /** Дата (до/после) или диапазон дат. */
    DateRange,
}

/** Группа поля в выпадашке — как на сайте. */
enum class FilterGroup { Acquisition, Profile, Behavior, DJMetry, Other }

data class FilterField(
    val field: String,
    val operators: List<String>,
    val kind: FilterValueKind,
    val options: List<String> = emptyList(),
    /** `smart_links` — варианты из смарт-линков владельца (в приложении — свободный ввод). */
    val optionsSource: String? = null,
) {
    val group: FilterGroup get() = filterGroup(this.field)
}

/** Правило фильтра. Значение — по виду поля: [values] (списки, строка, дата «до/после»), [number], [from]/[to] (диапазон). */
data class AudienceRule(
    val field: String,
    val operator: String,
    val values: List<String> = emptyList(),
    val number: Long? = null,
    val from: String? = null,
    val to: String? = null,
)

/** Операторы бэкенда. */
object FilterOp {
    const val EQ = "eq"; const val NE = "ne"; const val IN = "in"; const val NOT_IN = "not_in"
    const val ANY = "includes_any"; const val NONE = "excludes_all"
    const val GT = "gt"; const val GTE = "gte"; const val LT = "lt"; const val LTE = "lte"
    const val BEFORE = "before"; const val AFTER = "after"; const val BETWEEN = "between"
    val NUMERIC = listOf(EQ, NE, GT, GTE, LT, LTE)
}

fun filterGroup(field: String): FilterGroup = when (field) {
    "streaming_platform", "subscription_type", "smartlink_id", "email_marketing", "registration_type" -> FilterGroup.Acquisition
    "country", "artist_tags", "followers", "followers_bucket" -> FilterGroup.Profile
    "last_seen", "last_seen_days", "engagement_score", "listening_habits" -> FilterGroup.Behavior
    "djmetry_source", "djmetry_device", "djmetry_visits", "djmetry_clicks" -> FilterGroup.DJMetry
    else -> FilterGroup.Other
}

/** Офлайн-каталог — держать в синхроне с бэкендом (audience-filter-catalog.ts). */
val FALLBACK_FILTER_CATALOG: List<FilterField> = listOf(
    FilterField("streaming_platform", listOf(FilterOp.ANY, FilterOp.NONE, FilterOp.EQ, FilterOp.IN), FilterValueKind.Multi,
        listOf("spotify", "apple_music", "youtube_music", "youtube", "youtube_subscribe", "deezer", "soundcloud", "tidal", "amazon_music", "beatport", "bandcamp", "email_subscribe")),
    FilterField("subscription_type", listOf(FilterOp.ANY, FilterOp.IN, FilterOp.EQ), FilterValueKind.Multi, listOf("paid", "free", "not_available")),
    FilterField("smartlink_id", listOf(FilterOp.ANY, FilterOp.IN, FilterOp.NOT_IN, FilterOp.EQ), FilterValueKind.FreeMulti, optionsSource = "smart_links"),
    FilterField("email_marketing", listOf(FilterOp.ANY, FilterOp.IN, FilterOp.EQ), FilterValueKind.Multi, listOf("subscribed", "unsubscribed")),
    FilterField("registration_type", listOf(FilterOp.IN, FilterOp.EQ), FilterValueKind.FreeMulti, listOf("email", "google", "spotify", "apple")),
    FilterField("country", listOf(FilterOp.ANY, FilterOp.IN, FilterOp.EQ), FilterValueKind.FreeMulti),
    FilterField("artist_tags", listOf(FilterOp.IN, FilterOp.ANY), FilterValueKind.FreeMulti),
    FilterField("followers", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("followers_bucket", listOf(FilterOp.EQ, FilterOp.IN), FilterValueKind.Multi, listOf("0_1k", "1k_10k", "10k_100k", "100k_1m", "1m_plus")),
    FilterField("last_seen", listOf(FilterOp.BEFORE, FilterOp.AFTER, FilterOp.BETWEEN), FilterValueKind.DateRange),
    FilterField("last_seen_days", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("engagement_score", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("listening_habits", listOf(FilterOp.ANY, FilterOp.IN, FilterOp.EQ), FilterValueKind.FreeMulti, listOf("spotify_top_artists", "spotify_recent_tracks")),
    FilterField("djmetry_source", listOf(FilterOp.IN, FilterOp.ANY, FilterOp.EQ), FilterValueKind.FreeMulti, listOf("direct", "google", "facebook", "instagram", "tiktok", "twitter", "email")),
    FilterField("djmetry_device", listOf(FilterOp.IN, FilterOp.EQ), FilterValueKind.Multi, listOf("mobile", "desktop", "tablet")),
    FilterField("djmetry_visits", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("djmetry_clicks", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("fan_segment", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY), FilterValueKind.Multi, listOf("super_fan", "casual", "fading", "former", "cold")),
    FilterField("fan_score", FilterOp.NUMERIC, FilterValueKind.Number),
    FilterField("lead_status", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY), FilterValueKind.Multi, listOf("submitted", "not_submitted")),
    FilterField("lead_source_type", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY), FilterValueKind.Multi, listOf("bio_url", "smart_link", "tour")),
    FilterField("lead_source_id", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY), FilterValueKind.FreeMulti, optionsSource = "smart_links"),
    FilterField("lead_submitted_at", listOf(FilterOp.BEFORE, FilterOp.AFTER, FilterOp.BETWEEN), FilterValueKind.DateRange),
    FilterField("lead_fields_present", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY, FilterOp.NONE), FilterValueKind.Multi, listOf("has_email", "has_phone", "has_city")),
    FilterField("consent_version", listOf(FilterOp.EQ, FilterOp.IN, FilterOp.ANY), FilterValueKind.FreeMulti),
)

/** Ответ `/audience/filter-catalog` → поля приложения. Пусто или не разобрать — офлайн-каталог. */
fun catalogFromApi(json: JsonElement?): List<FilterField> {
    val fields = (json as? JsonObject)?.get("fields") as? JsonArray ?: return FALLBACK_FILTER_CATALOG
    val parsed = fields.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val field = o["field"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val ops = (o["operators"] as? JsonArray)?.mapNotNull { op ->
            (op as? JsonPrimitive)?.contentOrNull ?: (op as? JsonObject)?.get("operator")?.jsonPrimitive?.contentOrNull
        }.orEmpty().ifEmpty { return@mapNotNull null }
        val options = (o["options"] as? JsonArray)?.mapNotNull { op ->
            (op as? JsonPrimitive)?.contentOrNull ?: (op as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull
        }.orEmpty()
        val kind = when (o["value_kind"]?.jsonPrimitive?.contentOrNull) {
            "number" -> FilterValueKind.Number
            "date_range" -> FilterValueKind.DateRange
            "single_string", "text" -> FilterValueKind.Text
            "multi_enum" -> if (options.isEmpty()) FilterValueKind.FreeMulti else FilterValueKind.Multi
            else -> FilterValueKind.FreeMulti
        }
        // Страна в каталоге — свободный список; в приложении — выбор из справочника стран
        FilterField(field, ops, kind, options, o["options_source"]?.jsonPrimitive?.contentOrNull)
    }
    return parsed.ifEmpty { FALLBACK_FILTER_CATALOG }
}

/** Новое правило: первый оператор поля, пустое значение. */
fun defaultRule(f: FilterField): AudienceRule = AudienceRule(f.field, f.operators.first())

/** Правило готово к отправке (пустые бэкенд не примет). */
fun AudienceRule.isComplete(): Boolean = when {
    operator == FilterOp.BETWEEN -> from != null && to != null
    operator == FilterOp.BEFORE || operator == FilterOp.AFTER -> values.firstOrNull()?.isNotBlank() == true
    number != null -> true
    else -> values.any { it.isNotBlank() }
}

/** Правило → JSON бэкенда `{field, operator, value}`. */
fun AudienceRule.toJson(): JsonObject = buildJsonObject {
    put("field", field); put("operator", operator)
    when {
        operator == FilterOp.BETWEEN -> put("value", buildJsonObject { put("from", from); put("to", to) })
        number != null -> put("value", number)
        operator == FilterOp.BEFORE || operator == FilterOp.AFTER -> put("value", values.first())
        // «равно / не равно» с одним значением — строкой, как сайт; списки — массивом
        (operator == FilterOp.EQ || operator == FilterOp.NE) && values.size == 1 -> put("value", values.first())
        else -> put("value", JsonArray(values.map(::JsonPrimitive)))
    }
}

/** Черновик → фильтры для превью/экспорта/сохранения: только готовые правила, массивом (И). */
fun rulesToFilters(rules: List<AudienceRule>): JsonArray = JsonArray(rules.filter { it.isComplete() }.map { it.toJson() })

/** JSON правила → [AudienceRule]; не правило — null. */
private fun ruleFromJson(o: JsonObject): AudienceRule? {
    val field = o["field"]?.jsonPrimitive?.contentOrNull ?: return null
    val op = o["operator"]?.jsonPrimitive?.contentOrNull ?: return null
    return when (val v = o["value"]) {
        is JsonObject -> AudienceRule(field, op, from = v["from"]?.jsonPrimitive?.contentOrNull, to = v["to"]?.jsonPrimitive?.contentOrNull)
        is JsonArray -> AudienceRule(field, op, values = v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
        is JsonPrimitive -> v.longOrNull?.takeIf { !v.isString }?.let { AudienceRule(field, op, number = it) }
            ?: v.doubleOrNull?.takeIf { !v.isString }?.let { AudienceRule(field, op, number = it.toLong()) }
            ?: AudienceRule(field, op, values = listOfNotNull(v.contentOrNull))
        else -> AudienceRule(field, op)
    }
}

/**
 * Фильтры сегмента → строки конструктора. Массив правил или группа `and` с правилами; `or` и вложенные группы
 * конструктор не показывает (null — фильтры сегмента применяются как есть, без правки).
 */
fun parseRules(filters: JsonElement?): List<AudienceRule>? {
    val items: List<JsonElement> = when (filters) {
        null, JsonNull -> return emptyList()
        is JsonArray -> filters
        is JsonObject -> when {
            filters["field"] != null -> listOf(filters)
            filters["op"]?.jsonPrimitive?.contentOrNull?.lowercase() in setOf(null, "and") -> (filters["rules"] as? JsonArray) ?: return emptyList()
            else -> return null
        }
        else -> return emptyList()
    }
    return items.map { (it as? JsonObject)?.let(::ruleFromJson) ?: return null }
}

/** Пресет без фильтров в ответе — дефолт, как на сайте (`PRESET_DEFAULT_FILTERS`). */
fun presetDefaultRules(presetKey: String?, today: LocalDate): List<AudienceRule> = when (presetKey?.lowercase()?.trim()) {
    "djmetry" -> listOf(AudienceRule("djmetry_visits", FilterOp.GTE, number = 1))
    "spotify_fans" -> listOf(AudienceRule("streaming_platform", FilterOp.ANY, listOf("spotify")))
    "deezer_fans" -> listOf(AudienceRule("streaming_platform", FilterOp.ANY, listOf("deezer")))
    "youtube_music_fans" -> listOf(AudienceRule("streaming_platform", FilterOp.ANY, listOf("youtube_music")))
    "youtube_subscribers" -> listOf(AudienceRule("streaming_platform", FilterOp.ANY, listOf("youtube_subscribe")))
    "apple_music_fans" -> listOf(AudienceRule("streaming_platform", FilterOp.ANY, listOf("apple_music")))
    "influencers" -> listOf(AudienceRule("followers", FilterOp.GTE, number = 10_000))
    "recent_fans" -> listOf(AudienceRule("last_seen", FilterOp.BETWEEN, from = today.minus(6, DateTimeUnit.DAY).toString(), to = today.toString()))
    "top_fans", "super_fans" -> listOf(AudienceRule("fan_segment", FilterOp.EQ, listOf("super_fan")))
    "repeat_fans", "casual_fans" -> listOf(AudienceRule("fan_segment", FilterOp.EQ, listOf("casual")))
    "slipping_away", "fading_fans" -> listOf(AudienceRule("fan_segment", FilterOp.EQ, listOf("fading")))
    "past_fans", "former_fans" -> listOf(AudienceRule("fan_segment", FilterOp.EQ, listOf("former")))
    "cold_audience", "cold_fans" -> listOf(AudienceRule("fan_segment", FilterOp.EQ, listOf("cold")))
    "premium_streamers" -> listOf(AudienceRule("subscription_type", FilterOp.IN, listOf("paid")))
    else -> emptyList()
}

/** Имя сегмента: обрезать края и схлопнуть пробелы; пусто — null (не сохраняем). */
fun normalizeSegmentName(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

/** Пресеты чисел (как на сайте): фолловеры при «больше» — от 50; вовлечённость — 1…10. */
fun numberPresets(field: String, operator: String): List<Long>? = when {
    field == "followers" && (operator == FilterOp.GT || operator == FilterOp.GTE) -> listOf(50, 100, 500, 1_000, 10_000)
    field == "engagement_score" -> (1L..10L).toList()
    else -> null
}

/** Быстрые диапазоны дат для «в диапазоне»: дней назад; 0 — с начала года. */
val DATE_RANGE_PRESETS: List<Int> = listOf(7, 30, 90, 180, 0)

fun dateRangePreset(days: Int, today: LocalDate): Pair<String, String> =
    (if (days == 0) LocalDate(today.year, 1, 1) else today.minus(days, DateTimeUnit.DAY)).toString() to today.toString()
