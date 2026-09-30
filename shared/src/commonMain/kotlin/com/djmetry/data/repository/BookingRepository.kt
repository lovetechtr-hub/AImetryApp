package com.djmetry.data.repository

import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.data.booking.BookingRole
import com.djmetry.data.booking.RequestForm
import com.djmetry.data.booking.clampTax
import com.djmetry.api.models.BookingEarnings
import com.djmetry.api.models.BookingPerformance
import com.djmetry.api.models.NewBookingRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Роли аккаунта во вкладке «Букинг»: заказчик — у всех, агентства — где owner/manager, артист — если проверен. */
data class BookingRoles(val companies: List<BookingCompany>, val artistId: String?) {
    val list: List<BookingRole> = buildList {
        add(BookingRole.Requester)
        if (companies.isNotEmpty()) add(BookingRole.Company)
        if (artistId != null) add(BookingRole.Artist)
    }

    /** С какой роли открыть: артист (его шоу — главное), потом агентство, иначе заказчик. */
    val default: BookingRole = when {
        artistId != null -> BookingRole.Artist
        companies.isNotEmpty() -> BookingRole.Company
        else -> BookingRole.Requester
    }
}

/**
 * Букинг (спека §18, docs/BACKEND_API.md → «Букинг»): заявки заказчика, инбокс агентства, шоу артиста.
 * Статусы проверяет бэкенд (VALID_TRANSITIONS); деньги и налоги меняются на сайте.
 */
class BookingRepository(private val api: BookingApi, private val docs: com.djmetry.api.endpoints.ArtistEditorApi? = null) {

    suspend fun roles(me: MeResponse): BookingRoles {
        val companies = api.myCompanies().getOrNull()?.companies.orEmpty().filter { it.my_role != null }
        return BookingRoles(companies, verifiedArtistId(me))
    }

    suspend fun requests(role: BookingRole, companyId: String?, artistId: String?): Result<List<BookingRequest>> = when (role) {
        BookingRole.Requester -> api.myRequests()
        BookingRole.Company -> companyId?.let { api.companyRequests(it) } ?: Result.success(com.djmetry.api.models.BookingRequestsResponse())
        BookingRole.Artist -> artistId?.let { artistRequests(it) } ?: Result.success(com.djmetry.api.models.BookingRequestsResponse())
    }.map { it.requests }

    /** Артисту бэкенд отдаёт агентство как `{id, name}` без фото — берём фото из его агентств. */
    private suspend fun artistRequests(artistId: String) = coroutineScope {
        val logos = async { api.artistCompanies(artistId).getOrNull()?.companies.orEmpty().mapNotNull { l -> l.company?.image_url?.let { l.company_id to it } }.toMap() }
        api.artistRequests(artistId).map { resp ->
            val m = logos.await()
            resp.copy(requests = resp.requests.map { r -> if (r.company_image_url == null) r.copy(company_image_url = (r.company?.id ?: r.booking_company_id)?.let(m::get)) else r })
        }
    }

    /** Открыть заявку: у агентства и артиста бэкенд заодно помечает её прочитанной. */
    suspend fun open(role: BookingRole, request: BookingRequest, artistId: String?): Result<BookingRequest> = when (role) {
        BookingRole.Company -> api.companyRequest(request.id).map { it.request }
        BookingRole.Artist -> artistId?.let { id -> api.artistRequest(id, request.id).map { it.request.copy(company_image_url = it.request.company_image_url ?: request.company_image_url) } } ?: Result.success(request)
        BookingRole.Requester -> Result.success(request)
    }

    suspend fun setCompanyStatus(id: String, status: String): Result<BookingRequest> = api.setCompanyStatus(id, status).map { it.request }

    suspend fun setArtistStatus(artistId: String, id: String, status: String, transport: String?): Result<BookingRequest> =
        api.setArtistStatus(artistId, id, status, transport).map { it.request }

    suspend fun cancel(id: String): Result<Unit> = api.cancelMyRequest(id)
    suspend fun restore(id: String): Result<Unit> = api.restoreMyRequest(id)

    // ── Кабинет (вариант A, design/booking/cabinet-variants.html) ──

    suspend fun earnings(role: BookingRole, companyId: String?, artistId: String?): Result<BookingEarnings> = when (role) {
        BookingRole.Company -> companyId?.let { api.companyEarnings(it) }
        BookingRole.Artist -> artistId?.let { api.artistEarnings(it) }
        BookingRole.Requester -> null
    } ?: Result.failure(IllegalStateException("no role"))

    suspend fun companyDetail(companyId: String) = api.companyDetail(companyId)
    suspend fun unlinkArtist(companyId: String, artistId: String) = api.unlinkArtist(companyId, artistId)
    suspend fun artistToken(companyId: String) = api.artistToken(companyId).map { it.artist_confirm_token }
    suspend fun members(companyId: String) = api.members(companyId).map { it.members }
    suspend fun invite(companyId: String, email: String) = api.inviteMember(companyId, email.trim())
    suspend fun updateMember(companyId: String, userId: String, acceptAll: Boolean, regions: List<String>) = api.updateMember(companyId, userId, acceptAll, regions)
    suspend fun removeMember(companyId: String, userId: String) = api.removeMember(companyId, userId)
    suspend fun leaveCompany(companyId: String) = api.removeMember(companyId, "me")

    /** Налоги агентства: своя ставка + «артист считает сам», затем ставки по артистам (если есть). */
    suspend fun saveTaxes(companyId: String, companyTax: Double, artistOwnTax: Boolean, artistRates: Map<String, Double>): Result<Unit> {
        api.setCompanyTax(companyId, clampTax(companyTax), artistOwnTax).onFailure { return Result.failure(it) }
        return if (artistRates.isEmpty()) Result.success(Unit) else api.setArtistTaxDefaults(companyId, artistRates.mapValues { clampTax(it.value) })
    }

    suspend fun performances(role: BookingRole, companyId: String?, artistId: String?): Result<List<BookingPerformance>> = when (role) {
        BookingRole.Company -> companyId?.let { api.companyPerformances(it) }
        BookingRole.Artist -> artistId?.let { api.artistPerformances(it) }
        BookingRole.Requester -> null
    }?.map { it.performances } ?: Result.success(emptyList())

    suspend fun artistCompanies(artistId: String) = api.artistCompanies(artistId).map { it.companies }
    suspend fun unlinkCompany(artistId: String, companyId: String) = api.unlinkCompany(artistId, companyId)
    suspend fun previewToken(token: String) = api.previewToken(token.trim())
    suspend fun confirmToken(token: String) = api.confirmToken(token.trim())
    suspend fun artistTax(artistId: String) = api.artistTaxDefault(artistId).map { it.default_artist_tax_percent }
    suspend fun setArtistTax(artistId: String, percent: Double) = api.setArtistTaxDefault(artistId, clampTax(percent)).map { it.default_artist_tax_percent }
    suspend fun downloadDoc(artistId: String, doc: com.djmetry.api.endpoints.BookingDoc) = api.downloadDoc(artistId, doc.path)

    // ── Новая заявка (вариант B) ──

    /** Агентства артиста для формы (публичный список). */
    suspend fun artistAgencies(artistId: String) = api.publicCompanies(artistId).map { it.companies }

    /** Страница агентства — подтверждённые артисты для галочек. */
    suspend fun companyPage(companyId: String) = api.companyPage(companyId)

    suspend fun createRequest(f: RequestForm, countryName: String): Result<BookingRequest> {
        val company = f.companyId ?: return Result.failure(IllegalStateException("no company"))
        return api.createRequest(
            NewBookingRequest(
                booking_company_id = company,
                spotify_artist_ids = f.artistIds.toList(),
                event_type = f.eventType.trim(),
                event_date = f.date,
                // Как на сайте: «Город, Страна» + ISO-код отдельно
                event_location = listOf(f.city.trim(), countryName.trim()).filter { it.isNotEmpty() }.joinToString(", "),
                event_country = f.country.trim().uppercase(),
                expected_attendees = f.guests.trim().toLong(),
                message = f.message.trim(),
            )
        ).map { it.request }
    }

    // ── Райдер и пресс-кит (те же эндпоинты, что в редакторе артиста) ──
    suspend fun hasDoc(artistId: String, doc: com.djmetry.api.endpoints.BookingDoc): Boolean =
        docs?.doc(artistId, doc)?.getOrNull()?.url != null
    suspend fun uploadDoc(artistId: String, doc: com.djmetry.api.endpoints.BookingDoc, file: com.djmetry.api.models.PickedFile): Result<Unit> =
        docs?.uploadDoc(artistId, doc, file)?.map { } ?: Result.failure(IllegalStateException("no docs api"))
    suspend fun deleteDoc(artistId: String, doc: com.djmetry.api.endpoints.BookingDoc): Result<Unit> =
        docs?.deleteDoc(artistId, doc)?.map { } ?: Result.failure(IllegalStateException("no docs api"))
}
