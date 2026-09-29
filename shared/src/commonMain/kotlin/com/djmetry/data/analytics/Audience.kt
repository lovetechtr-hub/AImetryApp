package com.djmetry.data.analytics

import com.djmetry.api.models.AudienceCountryCount
import com.djmetry.api.models.AudienceSegment
import kotlinx.serialization.json.*

/** Чья аудитория: карточка проверенного артиста или своя BIO-страница со смарт-линками. */
sealed interface AudienceScope {
    data class Artist(val spotifyArtistId: String) : AudienceScope
    data object BioOwner : AudienceScope

    val artistId: String? get() = (this as? Artist)?.spotifyArtistId
    val scopeParam: String? get() = if (this is BioOwner) "bio_owner" else null
    fun params(): List<Pair<String, String>> = when (this) {
        is Artist -> listOf("spotify_artist_id" to spotifyArtistId)
        BioOwner -> listOf("audience_scope" to "bio_owner")
    }
}

/** Сегменты фанов бэкенда (fan_segment) — плитки воронки, от самых горячих к ушедшим. */
enum class FanSegment(val key: String) {
    SuperFan("super_fan"), Casual("casual"), Cold("cold"), Fading("fading"), Former("former");

    companion object {
        fun of(key: String?): FanSegment = entries.firstOrNull { it.key == key } ?: Cold
    }
}

/** Источник лида для фильтра: null — все. */
enum class LeadSource(val key: String?) { All(null), Bio("bio_url"), SmartLink("smart_link"), Tour("tour") }

/**
 * Фильтры сегмента + условие «только этот сегмент фанов». Правила сегмента бэкенд принимает массивом (И)
 * или одной группой and/or — оборачиваем всё в группу `and`, чтобы не ломать его логику.
 */
fun withFanSegment(filters: JsonElement, fan: FanSegment?): JsonElement {
    if (fan == null) return filters
    val rule = buildJsonObject {
        put("field", "fan_segment"); put("operator", "eq"); put("value", fan.key)
    }
    val base: List<JsonElement> = when (filters) {
        is JsonArray -> filters
        is JsonObject -> listOf(filters)
        else -> emptyList()
    }
    if (base.isEmpty()) return JsonArray(listOf(rule))
    return buildJsonObject {
        put("op", "and")
        put("rules", JsonArray(base + rule))
    }
}

/** Страны аудитории для карты: только известные ISO2 с центром, доля — от самой большой. */
fun audienceMapCountries(rows: List<AudienceCountryCount>): List<MapCountry> {
    val byIso = rows.mapNotNull { r -> r.country.trim().uppercase().takeIf { it.length == 2 && r.count > 0 }?.let { it to r.count } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    val max = byIso.values.maxOrNull() ?: return emptyList()
    return byIso.mapNotNull { (iso, v) -> COUNTRY_CENTROIDS[iso]?.let { (lat, lon) -> MapCountry(iso, v, v.toDouble() / max, lat, lon) } }
        .sortedByDescending { it.visits }
}

/** Пресет «Все» — первым; остальные пресеты, затем свои сегменты по имени (так отдаёт бэкенд). */
fun orderSegments(list: List<AudienceSegment>): List<AudienceSegment> =
    list.sortedWith(compareByDescending<AudienceSegment> { it.preset_key == "everyone" }.thenByDescending { it.is_preset })

/** Название платформы у пресетов — бренд, не переводится; `null` — перевести ключом (Все, DJMetry) или взять имя. */
fun presetBrand(presetKey: String?): String? = when (presetKey) {
    "spotify_fans" -> "Spotify"
    "apple_music_fans" -> "Apple Music"
    "youtube_music_fans" -> "YouTube Music"
    "deezer_fans" -> "Deezer"
    "djmetry" -> "DJMetry"
    else -> null
}

/** Инициалы для аватара без фото: «Anna K.» → «AK», пусто → «?». */
fun initials(name: String?): String =
    name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifEmpty { null } ?: "?"
