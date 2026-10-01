package com.djmetry.api.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// ───────── Кабинет букинга: агентство и артист (docs/BACKEND_API.md → «Букинг: кабинет») ─────────

/** SQLite отдаёт флаги то `1/0`, то `true/false` — принимаем оба. */
internal object FlexBoolSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val p = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive ?: return decoder.decodeBoolean()
        return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: p.content.equals("true", ignoreCase = true)
    }
}

/** Один период заработка: суммы по валютам (`{"USD": 5000}`), доли агентства и артиста, до и после налога. */
@Serializable
data class EarningsPeriod(
    val by_currency: Map<String, Double> = emptyMap(),
    val by_currency_company_fee: Map<String, Double> = emptyMap(),
    val by_currency_artist_fee: Map<String, Double> = emptyMap(),
    val by_currency_company_fee_after_tax: Map<String, Double> = emptyMap(),
    val by_currency_artist_fee_after_tax: Map<String, Double> = emptyMap(),
    val total_requests: Int = 0,
)

/**
 * `GET /booking/companies/:id/earnings` и `GET /booking/artists/:id/earnings`. Периоды календарные (UTC):
 * неделя — с понедельника, месяц — с 1-го, год — с 1 января; `all_time` — только у артиста.
 */
/** Месяц ряда заработка (`months[]`, `previous`): `month` = YYYY-MM, суммы — как у периода. */
@Serializable
data class EarningsMonth(
    val month: String = "",
    val by_currency: Map<String, Double> = emptyMap(),
    val by_currency_company_fee: Map<String, Double> = emptyMap(),
    val by_currency_artist_fee: Map<String, Double> = emptyMap(),
    val by_currency_company_fee_after_tax: Map<String, Double> = emptyMap(),
    val by_currency_artist_fee_after_tax: Map<String, Double> = emptyMap(),
    val total_requests: Int = 0,
) {
    fun asPeriod() = EarningsPeriod(by_currency, by_currency_company_fee, by_currency_artist_fee, by_currency_company_fee_after_tax, by_currency_artist_fee_after_tax, total_requests)
}

@Serializable
data class BookingEarnings(
    val week: EarningsPeriod? = null,
    val month: EarningsPeriod? = null,
    val year: EarningsPeriod? = null,
    val all_time: EarningsPeriod? = null,
    /** Помесячный ряд (по возрастанию, пустые месяцы — нули) — столбики карточки. */
    val months: List<EarningsMonth> = emptyList(),
    /** Прошлый календарный месяц — для «+N% к прошлому». */
    val previous: EarningsMonth? = null,
    val company_tax_percent_applied: Double? = null,
    val artist_tax_percent_applied: Double? = null,
)

@Serializable
data class CompanyTopTrack(
    val spotify_track_id: String? = null,
    val name: String = "",
    val spotify_artist_id: String? = null,
    val album_image_url: String? = null,
    val preview_url: String? = null,
    val external_url: String? = null,
) {
    fun asTrack() = Track(spotifyTrackId = spotify_track_id, name = name, albumImageUrl = album_image_url, externalUrl = external_url, previewUrl = preview_url)
}

/** Агентство целиком (сериализатор `serializeCompany`). */
@Serializable
data class BookingCompanyInfo(
    val id: String,
    val name: String = "",
    val slug: String? = null,
    val description: String? = null,
    val country: String? = null,
    val city: String? = null,
    val image_url: String? = null,
    @Serializable(with = FlexBoolSerializer::class) val is_verified: Boolean = false,
    val moderation_status: String? = null,
    val default_company_tax_percent: Double? = null,
    val default_artist_tax_percent: Double? = null,
    val default_artist_calculates_own_tax: Boolean? = null,
)

/** Артист агентства. `approved` — 0, пока артист не подтвердил токен. */
@Serializable
data class BookingCompanyArtist(
    val spotify_artist_id: String,
    val name: String? = null,
    val image_url: String? = null,
    @Serializable(with = FlexBoolSerializer::class) val approved: Boolean = true,
    val default_artist_tax_percent: Double? = null,
)

/** `GET /booking/companies/:id` — для члена агентства: все связи с артистами и `myRole`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookingCompanyDetail(
    val company: BookingCompanyInfo,
    val artists: List<BookingCompanyArtist> = emptyList(),
    /** Топ-треки ростера (до 5 артистов × 6 треков) — одним ответом, без запроса на каждого артиста. */
    val top_tracks: List<CompanyTopTrack> = emptyList(),
    @JsonNames("myRole", "my_role") val myRole: String? = null,
)

/** `GET /booking/companies/by-slug/:slugOrId` — публичная страница агентства: только подтверждённые артисты. */
@Serializable
data class BookingCompanyPage(
    val id: String,
    val name: String = "",
    val slug: String? = null,
    val image_url: String? = null,
    val city: String? = null,
    val country: String? = null,
    val description: String? = null,
    val description_i18n: Map<String, String>? = null,
    val artists: List<BookingCompanyArtist> = emptyList(),
)

/** `POST /booking/companies/:id/artist-confirm-token` — одноразовый токен на 7 дней (копия уходит владельцу на почту). */
@Serializable
data class ArtistConfirmToken(val artist_confirm_token: String)

@Serializable
data class BookingMember(
    val id: Long? = null,
    val user_id: String? = null,
    val user_email: String? = null,
    val user_display_name: String? = null,
    /** owner | manager */
    val role: String = "manager",
    val invited_email: String? = null,
    /** accepted | pending */
    val invite_status: String = "accepted",
    val responsible_regions: List<String> = emptyList(),
    val accept_all_requests: Boolean = true,
)

@Serializable
data class BookingMembersResponse(val members: List<BookingMember> = emptyList())

@Serializable
internal data class InviteMemberBody(val invited_email: String)

@Serializable
internal data class MemberPatch(val accept_all_requests: Boolean, val responsible_regions: List<String>)

/** `PUT /booking/companies/:id` — только налоговые поля (остальное меняется на сайте, с модерацией). */
@Serializable
internal data class CompanyTaxPatch(val default_company_tax_percent: Double, val default_artist_calculates_own_tax: Boolean)

@Serializable
internal data class ArtistTaxDefault(val spotify_artist_id: String, val default_artist_tax_percent: Double)

@Serializable
internal data class ArtistTaxDefaultsBody(val artist_tax_defaults: List<ArtistTaxDefault>)

/** Связь артиста с агентством: `GET /booking/artists/:id/companies`. */
@Serializable
data class ArtistCompanyLink(
    val company_id: String,
    @Serializable(with = FlexBoolSerializer::class) val approved: Boolean = false,
    val company: BookingCompanyInfo? = null,
)

@Serializable
data class ArtistCompaniesResponse(val companies: List<ArtistCompanyLink> = emptyList())

/** Превью и подтверждение токена агентства артистом. У превью сервер шлёт `company.name`, сайт ждёт `company_name` — берём оба. */
@Serializable
data class TokenCompany(val company: BookingCompanyRef? = null, val company_name: String? = null, val under_moderation: Boolean = false) {
    val name: String? get() = company?.name ?: company_name
}

@Serializable
internal data class TokenBody(val token: String)

/** `GET|PUT /booking/artists/:id/tax-default`. */
@Serializable
data class ArtistTaxDefaultResponse(val default_artist_tax_percent: Double? = null)

@Serializable
internal data class ArtistTaxDefaultBody(val default_artist_tax_percent: Double)

/** Прошедшее выступление (`artist_finished_performance`) для карты. Координат нет — точка по стране. */
@Serializable
data class BookingPerformance(
    val id: String,
    val event_country: String? = null,
    val event_date: String? = null,
    val event_location: String? = null,
    val payment_amount: Double? = null,
    val payment_currency: String? = null,
    val artist_names: String? = null,
    val artists: List<BookingRequestArtist> = emptyList(),
)

@Serializable
data class BookingPerformancesResponse(val total: Int = 0, val performances: List<BookingPerformance> = emptyList())

/** `POST /booking/requests` — заявка заказчика (поля как в форме сайта; email и имя бэкенд берёт из профиля). */
@Serializable
data class NewBookingRequest(
    val booking_company_id: String,
    val spotify_artist_ids: List<String>,
    val event_type: String,
    /** club | festival | private | corporate | wedding | other — неверный ключ: 400 `invalid_event_type_key`. */
    val event_type_key: String,
    val event_date: String,
    val event_location: String,
    val event_country: String,
    val expected_attendees: Long,
    val message: String,
)


// ───────── Агрегат вкладки: `GET /booking/me/overview` ─────────

@Serializable
data class BookingRoleStats(val total: Int = 0, val unread: Int = 0, val stages: Map<String, Int> = emptyMap())

@Serializable
data class OverviewCompany(
    val company_id: String,
    val name: String = "",
    val slug: String? = null,
    val image_url: String? = null,
    val my_role: String? = null,
    val total: Int = 0,
    val unread: Int = 0,
    val stages: Map<String, Int> = emptyMap(),
)

@Serializable
data class OverviewArtist(
    val spotify_artist_id: String,
    val name: String? = null,
    val image_url: String? = null,
    val total: Int = 0,
    val unread: Int = 0,
    val stages: Map<String, Int> = emptyMap(),
)

@Serializable
data class BookingOverview(
    val as_company: List<OverviewCompany> = emptyList(),
    val as_artist: OverviewArtist? = null,
    val as_requester: BookingRoleStats? = null,
    val total_unread: Int = 0,
)

@Serializable
internal data class ReadBody(val role: String)

// ───────── Файлы артиста: `GET /booking/artists/:id/files` ─────────

/** Райдер или пресс-кит: подписанная ссылка (1 ч, без авторизации) — открыть системным просмотрщиком. */
@Serializable
data class BookingFile(
    val download_url: String? = null,
    val signed_url: String? = null,
    val signed_url_expires_at: String? = null,
    val filename: String? = null,
    val content_type: String? = null,
    val size: Long? = null,
    val updated_at: String? = null,
)

@Serializable
data class BookingFiles(val spotify_artist_id: String? = null, val rider: BookingFile? = null, val press_kit: BookingFile? = null)
