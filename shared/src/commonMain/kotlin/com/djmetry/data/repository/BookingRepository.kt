package com.djmetry.data.repository

import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.data.booking.BookingRole

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
        BookingRole.Artist -> artistId?.let { api.artistRequests(it) } ?: Result.success(com.djmetry.api.models.BookingRequestsResponse())
    }.map { it.requests }

    /** Открыть заявку: у агентства и артиста бэкенд заодно помечает её прочитанной. */
    suspend fun open(role: BookingRole, request: BookingRequest, artistId: String?): Result<BookingRequest> = when (role) {
        BookingRole.Company -> api.companyRequest(request.id).map { it.request }
        BookingRole.Artist -> artistId?.let { id -> api.artistRequest(id, request.id).map { it.request } } ?: Result.success(request)
        BookingRole.Requester -> Result.success(request)
    }

    suspend fun setCompanyStatus(id: String, status: String): Result<BookingRequest> = api.setCompanyStatus(id, status).map { it.request }

    suspend fun setArtistStatus(artistId: String, id: String, status: String, transport: String?): Result<BookingRequest> =
        api.setArtistStatus(artistId, id, status, transport).map { it.request }

    suspend fun cancel(id: String): Result<Unit> = api.cancelMyRequest(id)
    suspend fun restore(id: String): Result<Unit> = api.restoreMyRequest(id)
}
