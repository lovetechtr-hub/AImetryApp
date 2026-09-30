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

/**
 * Следующий путевой статус артиста: оплачено → в пути → отель → площадка → выступил.
 * «Выступил» — только если дата события уже наступила (правило бэкенда). null — дальше некуда или ещё рано.
 */
fun nextArtistStatus(status: String, eventDate: String?, today: LocalDate): String? {
    val next = when (status) {
        // Бэкенд (VALID_TRANSITIONS): «в пути» — из in_progress, accepted, paid
        BookingStatus.IN_PROGRESS, BookingStatus.ACCEPTED, BookingStatus.PAID -> BookingStatus.ON_THE_WAY
        BookingStatus.ON_THE_WAY -> BookingStatus.AT_HOTEL
        BookingStatus.AT_HOTEL -> BookingStatus.AT_VENUE
        BookingStatus.AT_VENUE -> BookingStatus.FINISHED
        else -> null
    } ?: return null
    if (next == BookingStatus.FINISHED) {
        val d = eventDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        if (d > today) return null
    }
    return next
}

/**
 * Быстрые действия агентства в приложении (подмножество VALID_TRANSITIONS бэкенда, requests.ts:1280):
 * новая — в работу / принять / отклонить; в работе — принять / отклонить; принята — оплачено / отклонить;
 * отклонена — вернуть в работу. Деньги и налоги — на сайте (менять может только owner).
 */
fun companyActions(status: String): List<String> = when (status) {
    BookingStatus.NEW -> listOf(BookingStatus.DECLINED, BookingStatus.IN_PROGRESS, BookingStatus.ACCEPTED)
    BookingStatus.IN_PROGRESS -> listOf(BookingStatus.DECLINED, BookingStatus.ACCEPTED)
    BookingStatus.ACCEPTED -> listOf(BookingStatus.DECLINED, BookingStatus.PAID)
    BookingStatus.DECLINED -> listOf(BookingStatus.IN_PROGRESS)
    else -> emptyList()
}

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
