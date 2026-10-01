package com.djmetry.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Аудитория (спека §15, docs/BACKEND_API.md → «Аудитория»). Поля — как в ответах бэкенда
 * (djmetry-api/src/api/routes/audience.ts, src/db/modules/audience.ts). Всё необязательное — с дефолтом.
 */

/** Тело `POST /audience/preview`: фильтры — DSL бэкенда (массив правил или группа and/or). */
@Serializable
data class AudiencePreviewRequest(
    val filters: JsonElement = JsonArray(emptyList()),
    val page: Int = 1,
    val page_size: Int = 25,
    val spotify_artist_id: String? = null,
    val audience_scope: String? = null,
)

@Serializable
data class AudienceCountryCount(val country: String, val count: Int = 0)

@Serializable
data class AudiencePlatformCount(val platform: String, val count: Int = 0)

@Serializable
data class AudienceStats(
    val countries_top: List<AudienceCountryCount> = emptyList(),
    val platforms: List<AudiencePlatformCount> = emptyList(),
    /** Воронка по всем 5 сегментам — одним ответом; null — старый бэкенд. */
    val fan_segments: List<FanSegmentCount>? = null,
)

@Serializable
data class FanSegmentCount(val segment: String, val count: Int = 0)

/** Человек из аудитории. Email на тарифе Start бэкенд отдаёт null. */
@Serializable
data class AudiencePerson(
    val person_id: String,
    val display_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val is_user: Boolean = false,
    val is_artist: Boolean = false,
    val artist_name: String? = null,
    /** Метка для колонки «Источник»: платформа или источник визита. */
    val service: String? = null,
    val streaming_platforms: List<String> = emptyList(),
    /** ISO2 в верхнем регистре. */
    val country: String? = null,
    val age: Int? = null,
    val fan_score: Int = 0,
    /** super_fan | casual | cold | fading | former */
    val fan_segment: String = "cold",
    val last_seen_at: String? = null,
)

@Serializable
data class AudiencePreviewResponse(
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 25,
    val stats: AudienceStats = AudienceStats(),
    val items: List<AudiencePerson> = emptyList(),
)

/** Сохранённый сегмент: пресет (общий для всех) или свой. */
@Serializable
data class AudienceSegment(
    val id: String,
    val name: String = "",
    val filters: JsonElement = JsonArray(emptyList()),
    val is_preset: Boolean = false,
    val preset_key: String? = null,
)

@Serializable
data class AudienceSegmentsResponse(val segments: List<AudienceSegment> = emptyList())

@Serializable
data class AudienceSegmentEnvelope(val segment: AudienceSegment)

/** `POST /audience/segments` — имя, правила и область (как у превью). */
@Serializable
data class AudienceSegmentCreate(
    val name: String,
    val filters: JsonElement,
    val spotify_artist_id: String? = null,
    val audience_scope: String? = null,
)

/** Лид: оставил контакты на Bio, смарт-линке или странице тура. */
@Serializable
data class AudienceLead(
    val lead_id: String,
    val full_name: String? = null,
    val display_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val city: String? = null,
    val location: String? = null,
    /** bio_url | smart_link | tour */
    val source_type: String = "",
    val submitted_at: String? = null,
)

@Serializable
data class AudienceLeadsResponse(
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 25,
    val emails_hidden: Boolean = false,
    val items: List<AudienceLead> = emptyList(),
)

/** `POST /audience/export` → 202 с id задания; CSV — `GET /audience/export/:id?download=1` (409, пока не готов). */
@Serializable
data class AudienceExportRequest(
    val filters: JsonElement = JsonArray(emptyList()),
    val spotify_artist_id: String? = null,
    val audience_scope: String? = null,
)

@Serializable
data class AudienceExportJob(val id: String, val status: String = "")
