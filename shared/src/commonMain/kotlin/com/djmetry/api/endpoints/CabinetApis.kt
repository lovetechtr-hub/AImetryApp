package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.contentType

/** Колокольчик (in-app уведомления). Нативные пуши (Firebase) придут позже и откроют этот же список. */
class NotificationsApi(private val http: HttpClient) {
    suspend fun unreadCount(): Result<UnreadCount> = apiCall { http.get("me/notifications/unread-count") }

    /** [type] — фильтр на стороне бэкенда: release_radar | pre_save | booking | concert; null — все. */
    suspend fun list(type: String? = null, cursor: String? = null, limit: Int = 15): Result<NotificationsPage> =
        apiCall {
            http.get("me/notifications") {
                type?.let { parameter("type", it) }
                cursor?.let { parameter("cursor", it) }
                parameter("limit", limit)
            }
        }

    suspend fun markRead(ids: List<String>): Result<SuccessResponse> =
        apiCall { http.post("me/notifications/read") { setBody(MarkReadRequest(ids = ids)) } }

    suspend fun markAllRead(type: String? = null): Result<SuccessResponse> =
        apiCall { http.post("me/notifications/read") { setBody(MarkReadRequest(all = true, type = type)) } }
}

/** Букинг артиста. Статусы и допустимые переходы считает бэкенд — клиент только показывает. */
class BookingApi(private val http: HttpClient) {
    suspend fun publicCompanies(spotifyArtistId: String): Result<BookingCompaniesResponse> =
        apiCall { http.get("booking/artists/public/$spotifyArtistId/booking") }

    suspend fun artistRequests(spotifyArtistId: String, limit: Int? = null): Result<BookingRequestsResponse> =
        apiCall { http.get("booking/artists/$spotifyArtistId/requests") { limit?.let { parameter("limit", it) } } }

    // ── Вкладка «Букинг»: заказчик ──
    suspend fun myRequests(): Result<BookingRequestsResponse> = apiCall { http.get("booking/my-requests") { parameter("limit", 200) } }

    /** Мягкая отмена заказчиком — заявка остаётся видна агентству как недействительная. */
    suspend fun cancelMyRequest(id: String): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/my-requests/$id") }.map { }

    suspend fun restoreMyRequest(id: String): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> { http.post("booking/my-requests/$id/restore") }.map { }

    // ── Агентство ──
    suspend fun myCompanies(): Result<BookingCompaniesResponse> = apiCall { http.get("booking/companies/my") }

    suspend fun companyRequests(companyId: String): Result<BookingRequestsResponse> =
        apiCall { http.get("booking/companies/$companyId/requests") { parameter("limit", 200) } }

    /** Открыть заявку (бэкенд помечает прочитанной). */
    suspend fun companyRequest(id: String): Result<BookingRequestEnvelope> = apiCall { http.get("booking/requests/$id") }

    /** Смена статуса агентством (переходы — VALID_TRANSITIONS бэкенда). */
    suspend fun setCompanyStatus(id: String, status: String): Result<BookingRequestEnvelope> = apiCall {
        http.patch("booking/requests/$id") { contentType(io.ktor.http.ContentType.Application.Json); setBody(BookingStatusPatch(status)) }
    }

    // ── Артист ──
    suspend fun artistRequest(spotifyArtistId: String, id: String): Result<BookingRequestEnvelope> =
        apiCall { http.get("booking/artists/$spotifyArtistId/requests/$id") }

    /** Путевой статус артиста (+ транспорт для «в пути»). */
    suspend fun setArtistStatus(spotifyArtistId: String, id: String, status: String, transport: String?): Result<BookingRequestEnvelope> = apiCall {
        http.patch("booking/artists/$spotifyArtistId/requests/$id") {
            contentType(io.ktor.http.ContentType.Application.Json); setBody(BookingStatusPatch(status, transport))
        }
    }

    // ── Кабинет агентства ──
    suspend fun companyEarnings(companyId: String): Result<BookingEarnings> = apiCall { http.get("booking/companies/$companyId/earnings") { parameter("months", EARNINGS_MONTHS) } }

    /** Роли аккаунта одним запросом: агентства, артист, заказчик — с непрочитанным и этапами. */
    suspend fun overview(): Result<BookingOverview> = apiCall { http.get("booking/me/overview") }

    /** «Прочитано» для моей роли (у агентства и артиста — раздельно). */
    suspend fun markRead(id: String, role: String): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> {
        http.post("booking/requests/$id/read") { json(); setBody(ReadBody(role)) }
    }.map { }

    /** Отозвать непринятое приглашение менеджера (по id строки участника). */
    suspend fun revokeInvite(companyId: String, memberId: Long): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/companies/$companyId/members/invite/$memberId") }.map { }

    /** Райдер и пресс-кит: имя, размер, дата и подписанная ссылка. */
    suspend fun files(artistId: String): Result<BookingFiles> = apiCall { http.get("booking/artists/$artistId/files") }
    suspend fun companyDetail(companyId: String): Result<BookingCompanyDetail> = apiCall { http.get("booking/companies/$companyId") }
    suspend fun unlinkArtist(companyId: String, artistId: String): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/companies/$companyId/artists/$artistId") }.map { }
    suspend fun artistToken(companyId: String): Result<ArtistConfirmToken> = apiCall {
        http.post("booking/companies/$companyId/artist-confirm-token") { json(); setBody(kotlinx.serialization.json.JsonObject(emptyMap())) }
    }
    suspend fun members(companyId: String): Result<BookingMembersResponse> = apiCall { http.get("booking/companies/$companyId/members") }
    suspend fun inviteMember(companyId: String, email: String): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.post("booking/companies/$companyId/members") { json(); setBody(InviteMemberBody(email)) } }.map { }
    suspend fun updateMember(companyId: String, userId: String, acceptAll: Boolean, regions: List<String>): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.patch("booking/companies/$companyId/members/$userId") { json(); setBody(MemberPatch(acceptAll, regions)) } }.map { }
    /** [userId] = `me` — выйти из агентства самому. */
    suspend fun removeMember(companyId: String, userId: String): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/companies/$companyId/members/$userId") }.map { }
    suspend fun setCompanyTax(companyId: String, percent: Double, artistOwnTax: Boolean): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.put("booking/companies/$companyId") { json(); setBody(CompanyTaxPatch(percent, artistOwnTax)) } }.map { }
    suspend fun setArtistTaxDefaults(companyId: String, rates: Map<String, Double>): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> {
        http.put("booking/companies/$companyId/artist-tax-defaults") { json(); setBody(ArtistTaxDefaultsBody(rates.map { (id, p) -> ArtistTaxDefault(id, p) })) }
    }.map { }
    suspend fun companyPerformances(companyId: String): Result<BookingPerformancesResponse> =
        apiCall { http.get("booking/companies/$companyId/performances") { parameter("limit", 200) } }

    // ── Кабинет артиста ──
    suspend fun artistEarnings(artistId: String): Result<BookingEarnings> = apiCall { http.get("booking/artists/$artistId/earnings") { parameter("months", EARNINGS_MONTHS) } }
    suspend fun artistCompanies(artistId: String): Result<ArtistCompaniesResponse> = apiCall { http.get("booking/artists/$artistId/companies") }
    suspend fun unlinkCompany(artistId: String, companyId: String): Result<Unit> =
        apiCall<kotlinx.serialization.json.JsonObject> { http.delete("booking/artists/$artistId/companies/$companyId") }.map { }
    suspend fun previewToken(token: String): Result<TokenCompany> = apiCall { http.get("booking/confirm-by-token/preview") { parameter("token", token) } }
    suspend fun confirmToken(token: String): Result<TokenCompany> = apiCall { http.post("booking/confirm-by-token") { json(); setBody(TokenBody(token)) } }
    suspend fun artistTaxDefault(artistId: String): Result<ArtistTaxDefaultResponse> = apiCall { http.get("booking/artists/$artistId/tax-default") }
    suspend fun setArtistTaxDefault(artistId: String, percent: Double): Result<ArtistTaxDefaultResponse> =
        apiCall { http.put("booking/artists/$artistId/tax-default") { json(); setBody(ArtistTaxDefaultBody(percent)) } }
    suspend fun artistPerformances(artistId: String): Result<BookingPerformancesResponse> =
        apiCall { http.get("booking/artists/$artistId/performances") { parameter("limit", 200) } }
    /** PDF райдера или пресс-кита потоком (нужна авторизация — браузер без Bearer не скачает). */
    suspend fun downloadDoc(artistId: String, path: String): Result<ByteArray> = apiCall { http.get("booking/artists/$artistId/$path/download") }

    // ── Заказчик: новая заявка ──
    suspend fun companyPage(slugOrId: String): Result<BookingCompanyPage> = apiCall { http.get("booking/companies/by-slug/$slugOrId") }
    suspend fun createRequest(body: NewBookingRequest): Result<BookingRequestEnvelope> = apiCall { http.post("booking/requests") { json(); setBody(body) } }
}

private fun HttpRequestBuilder.json() = contentType(io.ktor.http.ContentType.Application.Json)

/** Сколько месяцев ряда заработка просить (столбики карточки). */
const val EARNINGS_MONTHS = 6

@kotlinx.serialization.Serializable
internal data class BookingStatusPatch(val status: String, val travel_transport: String? = null)

/** Радары: превью Release Radar для дашборда. */
class RadarApi(private val http: HttpClient) {
    /** Подписки с непрочитанным и ближайшим концертом — «истории» Радара. */
    suspend fun radarArtists(): Result<RadarArtistsResponse> = apiCall { http.get("me/radar/artists") }

    /** Предстоящие концерты всех подписок, по дате (до 200 за страницу). */
    suspend fun concerts(offset: Int, limit: Int = 200): Result<MeConcertsResponse> =
        apiCall { http.get("me/concerts") { parameter("limit", limit); parameter("offset", offset) } }

    /** Погасить «новое»: релизы, концерты или всё; [artistId] — только этого артиста. */
    suspend fun seen(kind: String, artistId: String?): Result<Unit> = apiCall<kotlinx.serialization.json.JsonObject> {
        http.post("me/radar/seen") { contentType(io.ktor.http.ContentType.Application.Json); setBody(RadarSeenBody(kind, artistId)) }
    }.map { }

    suspend fun releaseFeed(perArtist: Int = 5): Result<ReleaseRadarFeed> =
        apiCall { http.get("me/release-radar/feed") { parameter("per_artist", perArtist) } }

    /** Все релизы артиста (с 2026-01-01): поиск, даты, сортировка `date_desc` | `date_asc` | `name`, страницы. */
    suspend fun artistReleases(spotifyArtistId: String, q: String?, sort: String, offset: Int, limit: Int = 24): Result<ArtistReleasesResponse> =
        apiCall {
            http.get("me/release-radar/artist/$spotifyArtistId/releases") {
                parameter("limit", limit); parameter("offset", offset); parameter("sort", sort)
                q?.takeIf { it.isNotBlank() }?.let { parameter("q", it.trim()) }
            }
        }
}
