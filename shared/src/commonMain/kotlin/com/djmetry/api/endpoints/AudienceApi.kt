package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import com.djmetry.data.analytics.AudienceScope
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Аудитория (`/api/audience/…`, docs/BACKEND_API.md → «Аудитория»). Область — карточка артиста
 * (`spotify_artist_id`) или своя BIO-страница и смарт-линки (`audience_scope=bio_owner`).
 */
class AudienceApi(private val http: HttpClient) {

    suspend fun preview(scope: AudienceScope, filters: kotlinx.serialization.json.JsonElement, page: Int, pageSize: Int): Result<AudiencePreviewResponse> = apiCall {
        http.post("audience/preview") {
            contentType(ContentType.Application.Json)
            setBody(AudiencePreviewRequest(filters, page, pageSize, scope.artistId, scope.scopeParam))
        }
    }

    suspend fun segments(scope: AudienceScope): Result<AudienceSegmentsResponse> = apiCall {
        http.get("audience/segments") { scope.params().forEach { (k, v) -> parameter(k, v) } }
    }

    /** Каталог полей и операторов конструктора фильтров (форма — catalogFromApi). */
    suspend fun filterCatalog(scope: AudienceScope): Result<kotlinx.serialization.json.JsonObject> = apiCall {
        http.get("audience/filter-catalog") { scope.params().forEach { (k, v) -> parameter(k, v) } }
    }

    /** «Сохранить как новый сегмент»: текущие правила конструктора под именем. */
    suspend fun createSegment(scope: AudienceScope, name: String, filters: kotlinx.serialization.json.JsonElement): Result<AudienceSegmentEnvelope> = apiCall {
        http.post("audience/segments") {
            contentType(ContentType.Application.Json)
            setBody(AudienceSegmentCreate(name, filters, scope.artistId, scope.scopeParam))
        }
    }

    suspend fun deleteSegment(id: String): Result<Unit> =
        apiCall<Unit> { http.delete("audience/segments/$id") } // бэкенд отвечает 204 без тела

    /** [sourceType]: bio_url | smart_link | tour; null — все. */
    suspend fun leads(sourceType: String?, page: Int, pageSize: Int): Result<AudienceLeadsResponse> = apiCall {
        http.get("audience/leads") {
            parameter("page", page); parameter("page_size", pageSize)
            sourceType?.let { parameter("source_type", it) }
        }
    }

    suspend fun startExport(scope: AudienceScope, filters: kotlinx.serialization.json.JsonElement): Result<AudienceExportJob> = apiCall {
        http.post("audience/export") {
            contentType(ContentType.Application.Json)
            setBody(AudienceExportRequest(filters, scope.artistId, scope.scopeParam))
        }
    }

    /** Готовый CSV; 409 `export_not_ready` — задание ещё считается. */
    suspend fun downloadExport(jobId: String): Result<String> = apiCall {
        http.get("audience/export/$jobId") { parameter("download", 1) }
    }
}
