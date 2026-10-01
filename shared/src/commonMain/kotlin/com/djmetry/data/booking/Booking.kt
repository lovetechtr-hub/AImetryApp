package com.djmetry.data.booking

import kotlinx.datetime.LocalDate

/** Роли во вкладке «Букинг» (спека §18): заказчик, агентство (owner/manager), проверенный артист. */
enum class BookingRole { Requester, Company, Artist }

/** Статусы заявки бэкенда в порядке жизненного цикла. */
object BookingStatus {
    const val NEW = "new"
    const val IN_PROGRESS = "in_progress"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val PAID = "paid"
    const val ON_THE_WAY = "artist_on_the_way"
    const val AT_HOTEL = "artist_at_hotel"
    const val AT_VENUE = "artist_at_venue"
    const val FINISHED = "artist_finished_performance"
    const val COMPLETED = "completed"

    /** Путевые статусы артиста — по порядку. */
    val ARTIST_PATH = listOf(ON_THE_WAY, AT_HOTEL, AT_VENUE, FINISHED)
}

/** Транспорт «в пути». */
val TRAVEL_TRANSPORTS = listOf("plane", "train", "car", "ship")

/**
 * Стадия прогресс-бара (6 делений, как на сайте): до оплаты — 0, paid — 1, в пути — 2, отель — 3, площадка — 4,
 * выступил / завершено — 5. [isDeclined] — отдельно (красный бар).
 */
fun bookingStage(status: String): Int = when (status) {
    BookingStatus.PAID -> 1
    BookingStatus.ON_THE_WAY -> 2
    BookingStatus.AT_HOTEL -> 3
    BookingStatus.AT_VENUE -> 4
    BookingStatus.FINISHED, BookingStatus.COMPLETED -> 5
    else -> 0
}

fun isDeclined(status: String): Boolean = status == BookingStatus.DECLINED

/** Шоу принято, оплачено или в пути — для «живой» карточки артиста. */
fun isArtistActive(status: String): Boolean =
    status == BookingStatus.ACCEPTED || status == BookingStatus.PAID || status in BookingStatus.ARTIST_PATH.dropLast(1)

/** Группы фильтра в ленте. */
enum class BookingFilter(val statuses: Set<String>?) {
    All(null),
    New(setOf(BookingStatus.NEW)),
    Working(setOf(BookingStatus.IN_PROGRESS, BookingStatus.ACCEPTED)),
    Paid(setOf(BookingStatus.PAID)),
    OnTour(setOf(BookingStatus.ON_THE_WAY, BookingStatus.AT_HOTEL, BookingStatus.AT_VENUE)),
    Done(setOf(BookingStatus.FINISHED, BookingStatus.COMPLETED, BookingStatus.DECLINED));

    fun matches(status: String): Boolean = statuses == null || status in statuses
}

/** Сумма денег: «12 000 €», «10 000 USD» — группировка тысяч, валюта знаком, если знаем. */
fun moneyLabel(amount: Double?, currency: String?): String? {
    amount ?: return null
    val whole = kotlin.math.round(amount).toLong()
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    val sym = when (currency?.uppercase()) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; "RUB" -> "₽"; "TRY" -> "₺"; "UAH" -> "₴"; null -> ""; else -> currency.uppercase() }
    return if (sym.isEmpty()) grouped else "$grouped $sym"
}

/** Даты бэкенда: `YYYY-MM-DD HH:MM:SS` (SQLite, UTC, без T/Z) или ISO — день `YYYY-MM-DD`. */
fun bookingDay(value: String?): String? = value?.trim()?.take(10)?.takeIf { it.length == 10 }

/** Что открыть во вкладке «Букинг» по пушу или уведомлению: заявка (если известна) и чья она — агентства или артиста. */
data class BookingOpen(val requestId: String?, val companyId: String? = null, val artistId: String? = null)

/**
 * Ссылка на букинг: `/booking/requests/<id>` (запрошено у бэкенда), сейчас — `/dashboard#booking`,
 * `/dashboard/booking/my-requests`. Для уведомления `booking` id заявки и роль берём из `meta`.
 */
fun bookingLink(url: String?, baseUrl: String, type: String? = null, meta: kotlinx.serialization.json.JsonObject? = null): BookingOpen? {
    fun m(k: String) = (meta?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    val path = url?.trim()?.removePrefix(baseUrl).orEmpty()
    val fromUrl = path.takeIf { it.startsWith("/booking/requests/") }?.removePrefix("/booking/requests/")?.substringBefore('?')?.substringBefore('/')?.takeIf { it.isNotBlank() }
    val isBooking = type == "booking" || fromUrl != null || path.startsWith("/dashboard#booking") || path.startsWith("/dashboard/booking") || path.startsWith("/booking")
    if (!isBooking) return null
    return BookingOpen(fromUrl ?: m("request_id"), m("company_id"), m("spotify_artist_id"))
}


/** Кнопки агентства — ровно то, что разрешил сервер (`allowed_statuses`), в привычном порядке. Своей таблицы переходов нет. */
fun companyActionsFor(r: com.djmetry.api.models.BookingRequest): List<String> {
    val order = listOf(BookingStatus.DECLINED, BookingStatus.IN_PROGRESS, BookingStatus.ACCEPTED, BookingStatus.PAID)
    val allowed = r.allowed_statuses.orEmpty()
    return order.filter { it in allowed }
}

/**
 * Следующий путевой шаг артиста (в пути → отель → площадка → выступил) — первый из разрешённых сервером.
 * «Выступил» до дня события не предлагаем (бэкенд ответит `performance_before_event_date`).
 */
fun artistNextFor(r: com.djmetry.api.models.BookingRequest, today: LocalDate): String? {
    val allowed = r.allowed_statuses.orEmpty()
    val next = BookingStatus.ARTIST_PATH.firstOrNull { it in allowed } ?: return null
    if (next == BookingStatus.FINISHED) {
        val d = bookingDay(r.event_date)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        if (d > today) return null
    }
    return next
}
