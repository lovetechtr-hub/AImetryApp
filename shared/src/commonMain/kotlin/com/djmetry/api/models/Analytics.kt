package com.djmetry.api.models

import kotlinx.serialization.Serializable

/**
 * Аналитика BIO-страницы и карточки артиста (спека §15). Поля — как в ответах бэкенда
 * (djmetry-api/src/api/types/{bio-internal-visitors, music-page-breakdown-analytics, analytics-geo-options}.ts).
 * Все числа с дефолтом 0: отсутствующее поле не ломает экран.
 */
@Serializable
data class AnalyticsRangeInfo(val preset: String? = null, val from: String? = null, val to: String? = null)

@Serializable
data class NetworkTotals(
    val total_page_views: Int = 0,
    val anonymous_page_views: Int = 0,
    /** Все события кроме page_view — «раздутые», для CTR не годятся. */
    val total_click_events: Int = 0,
    /** Только клики наружу (outbound, social, smart link, event) — ось CTR. */
    val total_clicks: Int? = null,
    val djmetry_logged_page_views: Int = 0,
    val verified_artist_page_views: Int = 0,
    val booking_company_page_views: Int = 0,
    val other_logged_page_views: Int = 0,
)

@Serializable
data class SegmentRow(val segment: String, val unique_viewers: Int = 0, val page_views: Int = 0)

@Serializable
data class ClickBreakdownRow(val content_type: String? = null, val event_type: String, val count: Int = 0)

@Serializable
data class TopViewer(
    val viewer_user_id: String,
    val segment: String,
    val display_label: String? = null,
    val linked_spotify_artist_id: String? = null,
    val booking_company_slug: String? = null,
    val page_views: Int = 0,
    val last_seen_at: String? = null,
)

/** `bio-network` (BIO) и `network` (карточка DJMetry): одна форма, у карточки ещё списки артистов и компаний. */
@Serializable
data class NetworkResponse(
    val range: AnalyticsRangeInfo? = null,
    val totals: NetworkTotals = NetworkTotals(),
    val by_segment: List<SegmentRow> = emptyList(),
    val click_breakdown: List<ClickBreakdownRow> = emptyList(),
    val top_viewers: List<TopViewer> = emptyList(),
)

/** Строка разбивки: страна, город, источник или устройство + визиты, клики, CTR (считает бэкенд). */
@Serializable
data class BreakdownRow(
    val country_code: String? = null,
    val city: String? = null,
    val referral: String? = null,
    val name: String? = null,
    val visits: Int = 0,
    val clicks: Int = 0,
    val ctr: Double = 0.0,
    val ctr_display_label: String? = null,
)

@Serializable
data class SecretLinkLeads(val total: Int = 0)

@Serializable
data class BreakdownResponse(
    val range: AnalyticsRangeInfo? = null,
    val countries: List<BreakdownRow> = emptyList(),
    val cities: List<BreakdownRow> = emptyList(),
    val referrals: List<BreakdownRow> = emptyList(),
    val devices: List<BreakdownRow> = emptyList(),
    val browsers: List<BreakdownRow> = emptyList(),
    val os: List<BreakdownRow> = emptyList(),
    val secret_link_leads: SecretLinkLeads = SecretLinkLeads(),
)

@Serializable
data class GeoCountryOption(val code: String, val name: String? = null)

@Serializable
data class GeoCityOption(val country_code: String, val city: String, val label: String? = null)

@Serializable
data class GeoOptionsResponse(
    val countries: List<GeoCountryOption> = emptyList(),
    val cities: List<GeoCityOption> = emptyList(),
)
