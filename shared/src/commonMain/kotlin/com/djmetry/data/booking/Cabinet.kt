package com.djmetry.data.booking

import com.djmetry.api.models.BookingEarnings
import com.djmetry.api.models.BookingPerformance
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.EarningsPeriod
import com.djmetry.data.analytics.COUNTRY_CENTROIDS
import com.djmetry.data.analytics.MapCountry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.DateTimeUnit

/** Период карточки заработка. «Всё время» — только у артиста (у агентства бэкенд его не считает). */
enum class EarningsRange { Week, Month, Year, AllTime }

fun earningsRanges(role: BookingRole): List<EarningsRange> =
    if (role == BookingRole.Artist) EarningsRange.entries else listOf(EarningsRange.Week, EarningsRange.Month, EarningsRange.Year)

fun BookingEarnings.period(range: EarningsRange): EarningsPeriod? = when (range) {
    EarningsRange.Week -> week
    EarningsRange.Month -> month
    EarningsRange.Year -> year
    EarningsRange.AllTime -> all_time
}

/** Своя доля по валютам — как на сайте: агентству комиссия, артисту гонорар; по умолчанию после налога. */
fun ownEarnings(p: EarningsPeriod?, role: BookingRole, beforeTax: Boolean): Map<String, Double> {
    p ?: return emptyMap()
    val m = when (role) {
        BookingRole.Company -> if (beforeTax) p.by_currency_company_fee else p.by_currency_company_fee_after_tax
        else -> if (beforeTax) p.by_currency_artist_fee else p.by_currency_artist_fee_after_tax
    }
    return m.filterValues { it > 0 }
}

/** Главная валюта — с наибольшей суммой; остальные идут строкой «+ 500 €». */
fun splitCurrencies(m: Map<String, Double>): Pair<Pair<String, Double>?, List<Pair<String, Double>>> {
    val sorted = m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    return sorted.firstOrNull() to sorted.drop(1)
}

/**
 * Столбики последних [months] месяцев (последний — текущий): своя доля по оплаченным заявкам в [currency]
 * по месяцу события. Бэкенд помесячный ряд пока не отдаёт (пожелание в BACKEND_API.md) — считаем по ленте.
 */
fun monthlyBars(list: List<BookingRequest>, role: BookingRole, artistId: String?, currency: String?, today: LocalDate, months: Int = 6): List<Double> {
    val keys = (months - 1 downTo 0).map { back -> today.minus(back, DateTimeUnit.MONTH).toString().take(7) }
    val sums = DoubleArray(months)
    list.forEach { r ->
        if (r.deleted_by_requester || r.payment_status !in setOf("paid", "partially_paid")) return@forEach
        if (currency != null && !r.payment_currency.equals(currency, ignoreCase = true) && r.payment_currency != null) return@forEach
        val i = keys.indexOf(bookingDay(r.event_date)?.take(7) ?: return@forEach).takeIf { it >= 0 } ?: return@forEach
        val fee = when (role) {
            BookingRole.Company -> r.company_fee_amount
            else -> r.artists.firstOrNull { it.spotify_artist_id == artistId }?.artist_fee_amount ?: r.artist_fee_amount
        } ?: return@forEach
        val part = if (r.payment_status == "partially_paid") (r.payment_percent ?: 100.0) / 100.0 else 1.0
        sums[i] += fee * part
    }
    return sums.toList()
}

/** Точки карты выступлений: по стране (координат бэкенд не отдаёт), размер — число шоу. */
fun performanceCountries(list: List<BookingPerformance>): List<MapCountry> {
    val counts = list.mapNotNull { it.event_country?.trim()?.uppercase()?.takeIf { c -> c.length == 2 } }.groupingBy { it }.eachCount()
    val max = counts.values.maxOrNull() ?: return emptyList()
    return counts.entries.sortedByDescending { it.value }.mapNotNull { (iso, n) ->
        COUNTRY_CENTROIDS[iso]?.let { (lat, lon) -> MapCountry(iso, n, n.toDouble() / max, lat, lon) }
    }
}

/** Налог 0…99 (как `taxPercentSchema` бэкенда). */
fun clampTax(v: Double): Double = v.coerceIn(0.0, 99.0)

/** Разделы кабинета (плитки «пульта»). */
enum class CabinetSection { Artists, Team, Token, Taxes, Map, Companies, Files, ArtistTax }

fun cabinetSections(role: BookingRole): List<CabinetSection> = when (role) {
    BookingRole.Company -> listOf(CabinetSection.Artists, CabinetSection.Team, CabinetSection.Token, CabinetSection.Taxes, CabinetSection.Map)
    BookingRole.Artist -> listOf(CabinetSection.Companies, CabinetSection.Files, CabinetSection.ArtistTax, CabinetSection.Map)
    BookingRole.Requester -> emptyList()
}

// ── Новая заявка ──

/** Поля формы «Оставить заявку» — порядок проверки как на сайте. */
enum class RequestField { Artists, EventType, Date, Place, Guests, Message }

data class RequestForm(
    val companyId: String? = null,
    val artistIds: Set<String> = emptySet(),
    val eventType: String = "",
    val date: String = "",
    val country: String = "",
    val city: String = "",
    val guests: String = "",
    val message: String = "",
)

/** Лимиты сайта: тип до 200 символов, сообщение до 1000, гостей 1…50 000 000. */
const val REQUEST_TYPE_MAX = 200
const val REQUEST_MESSAGE_MAX = 1000
const val REQUEST_GUESTS_MAX = 50_000_000L

fun requestFormErrors(f: RequestForm, today: LocalDate): Set<RequestField> = buildSet {
    if (f.artistIds.isEmpty()) add(RequestField.Artists)
    if (f.eventType.isBlank() || f.eventType.length > REQUEST_TYPE_MAX) add(RequestField.EventType)
    if (f.date.length != 10 || f.date < today.toString()) add(RequestField.Date)
    if (f.country.isBlank() || f.city.isBlank()) add(RequestField.Place)
    if ((f.guests.trim().toLongOrNull() ?: 0) !in 1..REQUEST_GUESTS_MAX) add(RequestField.Guests)
    if (f.message.isBlank() || f.message.length > REQUEST_MESSAGE_MAX) add(RequestField.Message)
}


/** Столбики из помесячного ряда бэкенда (`months[]`): своя доля в главной валюте, последние [n] месяцев. */
fun serverBars(months: List<com.djmetry.api.models.EarningsMonth>, role: BookingRole, beforeTax: Boolean, currency: String?, n: Int = 6): List<Double> =
    months.takeLast(n).map { m -> ownIn(ownEarnings(m.asPeriod(), role, beforeTax), currency) }

/** Изменение за месяц к прошлому календарному, в целых процентах; нет базы — null. */
fun monthDelta(e: com.djmetry.api.models.BookingEarnings, role: BookingRole, beforeTax: Boolean, currency: String?): Int? {
    val prev = e.previous ?: return null
    fun sum(p: EarningsPeriod?) = ownIn(ownEarnings(p, role, beforeTax), currency)
    val before = sum(prev.asPeriod()).takeIf { it > 0 } ?: return null
    return kotlin.math.round((sum(e.month) - before) / before * 100).toInt()
}

/** Сумма в валюте [currency]; месяц без неё — 0, а не сумма других валют (5000 $ не рисуем как 5000 €). */
internal fun ownIn(own: Map<String, Double>, currency: String?): Double =
    if (currency != null) own[currency] ?: 0.0 else own.values.sum()
