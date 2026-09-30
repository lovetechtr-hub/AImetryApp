package com.djmetry.data.repository

import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.data.booking.BookingRole
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
class BookingRepository(private val api: BookingApi) {

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
}
