package com.djmetry.data

import com.djmetry.api.createApiClient
import com.djmetry.api.endpoints.AudienceApi
import com.djmetry.api.models.AudienceCountryCount
import com.djmetry.api.models.AudienceSegment
import com.djmetry.data.analytics.*
import com.djmetry.data.repository.AudienceBlock
import com.djmetry.data.repository.AudienceBlockedException
import com.djmetry.data.repository.AudienceRepository
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AudienceTest {

    // ── Чистая логика ──

    @Test
    fun fanSegmentRuleOnEmptyFiltersIsPlainArray() {
        val f = withFanSegment(JsonArray(emptyList()), FanSegment.SuperFan)
        assertEquals("""[{"field":"fan_segment","operator":"eq","value":"super_fan"}]""", f.toString())
    }

    @Test
    fun fanSegmentRuleWrapsSegmentFiltersInAndGroup() {
        val seg = Json.parseToJsonElement("""[{"field":"streaming_platform","operator":"includes_any","value":["spotify"]}]""")
        val f = withFanSegment(seg, FanSegment.Fading).jsonObject
        assertEquals("and", f["op"]!!.jsonPrimitive.content)
        val rules = f["rules"]!!.jsonArray
        assertEquals(2, rules.size)
        assertEquals("fading", rules[1].jsonObject["value"]!!.jsonPrimitive.content)
        // Группа or остаётся целой — внутрь не лезем
        val or = Json.parseToJsonElement("""{"op":"or","rules":[]}""")
        assertEquals(or, withFanSegment(or, FanSegment.Cold).jsonObject["rules"]!!.jsonArray[0])
    }

    @Test
    fun noFanSegmentKeepsFiltersAsIs() {
        val seg = Json.parseToJsonElement("""[{"field":"country","operator":"eq","value":"DE"}]""")
        assertSame(seg, withFanSegment(seg, null))
    }

    @Test
    fun fanSegmentFromBackendKey() {
        assertEquals(FanSegment.SuperFan, FanSegment.of("super_fan"))
        assertEquals(FanSegment.Former, FanSegment.of("former"))
        assertEquals(FanSegment.Cold, FanSegment.of("что-то новое"))
    }

    @Test
    fun mapCountriesKeepOnlyKnownIsoAndShareFromMax() {
        val m = audienceMapCountries(listOf(AudienceCountryCount("es", 300), AudienceCountryCount("DE", 150), AudienceCountryCount("??", 50), AudienceCountryCount("US", 0)))
        assertEquals(listOf("ES", "DE"), m.map { it.iso })
        assertEquals(1.0, m[0].share); assertEquals(0.5, m[1].share)
    }

    @Test
    fun everyoneFirstThenPresetsThenOwn() {
        val list = listOf(
            AudienceSegment("mine", "Ибица", is_preset = false),
            AudienceSegment("sp", "Spotify fans", is_preset = true, preset_key = "spotify_fans"),
            AudienceSegment("all", "Everyone", is_preset = true, preset_key = "everyone"),
        )
        assertEquals(listOf("all", "sp", "mine"), orderSegments(list).map { it.id })
    }

    @Test
    fun presetBrandsAndInitials() {
        assertEquals("Apple Music", presetBrand("apple_music_fans"))
        assertNull(presetBrand("everyone"))
        assertEquals("AK", initials("Anna  Karenina Ivanova"))
        assertEquals("?", initials("  "))
        assertEquals("?", initials(null))
    }

    // ── Репозиторий ──

    private val requests = mutableListOf<Pair<String, String>>()

    private fun repo(handler: (path: String, body: String) -> Pair<HttpStatusCode, String>): AudienceRepository {
        val engine = MockEngine { req ->
            val body = (req.body as? TextContent)?.text.orEmpty()
            requests += req.url.encodedPath to body
            val (status, json) = handler(req.url.encodedPath, body)
            respond(json, status, headersOf(HttpHeaders.ContentType, if (json.startsWith("email")) "text/csv" else ContentType.Application.Json.toString()))
        }
        return AudienceRepository(AudienceApi(createApiClient(tokenProvider = { "t" }, languageProvider = { null }, engine = engine)))
    }

    private val counts = mapOf("super_fan" to 12, "casual" to 40, "cold" to 70, "fading" to 9, "former" to 5)

    @Test
    fun funnelFromFanSegmentsInOneRequest() = runTest {
        // Бэкенд отдаёт воронку в stats.fan_segments — один запрос вместо шести
        val r = repo { _, _ -> HttpStatusCode.OK to """{"total":136,"stats":{"countries_top":[],"platforms":[],"fan_segments":[
            {"segment":"super_fan","count":12},{"segment":"casual","count":40},{"segment":"cold","count":70},{"segment":"fading","count":9},{"segment":"former","count":5}]},"items":[]}""" }
        val o = r.overview(AudienceScope.Artist("a1")).getOrThrow()
        assertEquals(12, o.funnel[FanSegment.SuperFan]); assertEquals(5, o.funnel[FanSegment.Former])
        assertEquals(1, requests.size)
    }

    @Test
    fun overviewCountsFunnelFromFiveFilteredPreviews() = runTest {
        val r = repo { _, body ->
            val seg = counts.keys.firstOrNull { body.contains("\"$it\"") }
            val total = seg?.let { counts[it] } ?: 136
            HttpStatusCode.OK to """{"total":$total,"stats":{"countries_top":[{"country":"DE","count":50}],"platforms":[]},"items":[]}"""
        }
        val o = r.overview(AudienceScope.Artist("a1")).getOrThrow()
        assertEquals(136, o.total)
        assertEquals(12, o.funnel[FanSegment.SuperFan]); assertEquals(5, o.funnel[FanSegment.Former])
        assertEquals("DE", o.countries.single().country)
        assertEquals(6, requests.size, "общий итог + 5 плиток")
        assertTrue(requests.all { it.second.contains("\"spotify_artist_id\":\"a1\"") && it.second.contains("\"page_size\":1") })
    }

    @Test
    fun clearUserDataDropsOverviewOfPreviousAccount() = runTest {
        val r = repo { _, _ -> HttpStatusCode.OK to """{"total":1,"stats":{"countries_top":[],"platforms":[]},"items":[]}""" }
        r.overview(AudienceScope.BioOwner).getOrThrow()
        val first = requests.size
        r.clearUserData()
        r.overview(AudienceScope.BioOwner).getOrThrow()
        assertEquals(first * 2, requests.size, "после выхода — снова в сеть")
    }

    @Test
    fun bioOwnerScopeSendsAudienceScope() = runTest {
        val r = repo { _, _ -> HttpStatusCode.OK to """{"total":0}""" }
        r.people(AudienceScope.BioOwner, JsonArray(emptyList()), null, page = 2).getOrThrow()
        val body = requests.single().second
        assertTrue(body.contains("\"audience_scope\":\"bio_owner\"")); assertTrue(body.contains("\"page\":2"))
    }

    @Test
    fun unauthorizedMeansWebOnly() = runTest {
        // Бэкенд пока берёт пользователя только из cookie → 401 для приложения
        val r = repo { _, _ -> HttpStatusCode.Unauthorized to """{"error":"unauthorized"}""" }
        assertEquals(AudienceBlock.WebOnly, (r.overview(AudienceScope.BioOwner).exceptionOrNull() as AudienceBlockedException).block)
        assertEquals(AudienceBlock.WebOnly, (r.leads(LeadSource.All).exceptionOrNull() as AudienceBlockedException).block)
    }

    @Test
    fun noArtistAndNoPageMeansNoAccess() = runTest {
        val r = repo { _, _ -> HttpStatusCode.BadRequest to """{"error":"spotify_artist_id_required"}""" }
        assertEquals(AudienceBlock.NoAccess, (r.overview(AudienceScope.BioOwner).exceptionOrNull() as AudienceBlockedException).block)
    }

    @Test
    fun leadsSendSourceType() = runTest {
        val r = repo { _, _ -> HttpStatusCode.OK to """{"total":1,"emails_hidden":true,"items":[{"lead_id":"l1","source_type":"tour"}]}""" }
        val l = r.leads(LeadSource.Tour).getOrThrow()
        assertTrue(l.emails_hidden); assertEquals("tour", l.items.single().source_type)
    }

    @Test
    fun exportWaitsUntilCsvIsReady() = runTest {
        var polls = 0
        val r = repo { path, _ ->
            when {
                path.endsWith("/audience/export") -> HttpStatusCode.Accepted to """{"id":"job1","status":"pending"}"""
                ++polls < 3 -> HttpStatusCode.Conflict to """{"error":"export_not_ready"}"""
                else -> HttpStatusCode.OK to "email,fan_score\na@b.c,90"
            }
        }
        assertEquals("email,fan_score\na@b.c,90", r.exportCsv(AudienceScope.Artist("a1"), JsonArray(emptyList()), pause = 1).getOrThrow())
        assertEquals(3, polls)
    }
}
