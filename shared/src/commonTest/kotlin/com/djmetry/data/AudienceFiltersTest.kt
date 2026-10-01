package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.DJMetryJson
import com.djmetry.api.endpoints.AudienceApi
import com.djmetry.data.analytics.*
import com.djmetry.data.repository.AudienceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import kotlin.test.*

/** Конструктор сегментов (вариант A): каталог, правила ↔ JSON бэкенда, сегменты пресетов, сохранение и удаление. */
class AudienceFiltersTest {
    private fun json(s: String) = DJMetryJson.parseToJsonElement(s)

    @Test
    fun catalogFromApiOrFallback() {
        val c = catalogFromApi(json("""{"fields":[
            {"field":"country","label_key":"x","operators":["includes_any","in"],"value_kind":"string_multi_free"},
            {"field":"fan_segment","operators":[{"operator":"eq"},{"operator":"in"}],"value_kind":"multi_enum","options":[{"value":"super_fan","label_key":"y"}]},
            {"field":"followers","operators":["gte"],"value_kind":"number"},
            {"field":"last_seen","operators":["between"],"value_kind":"date_range"},
            {"field":"smartlink_id","operators":["in"],"value_kind":"multi_enum","options_source":"smart_links"}]}"""))
        assertEquals(listOf(FilterValueKind.FreeMulti, FilterValueKind.Multi, FilterValueKind.Number, FilterValueKind.DateRange, FilterValueKind.FreeMulti), c.map { it.kind })
        assertEquals(listOf("eq", "in"), c[1].operators)
        assertEquals(listOf("super_fan"), c[1].options)
        assertEquals(FilterGroup.Profile, c[0].group)
        assertEquals(FALLBACK_FILTER_CATALOG, catalogFromApi(null))
        assertEquals(FALLBACK_FILTER_CATALOG, catalogFromApi(json("""{"fields":[]}""")))
        assertEquals(25, FALLBACK_FILTER_CATALOG.size)
    }

    @Test
    fun rulesToBackendJson() {
        val rules = listOf(
            AudienceRule("fan_segment", FilterOp.EQ, listOf("super_fan")),
            AudienceRule("country", FilterOp.IN, listOf("DE", "AT")),
            AudienceRule("followers", FilterOp.GTE, number = 1000),
            AudienceRule("last_seen", FilterOp.BETWEEN, from = "2026-09-01", to = "2026-10-01"),
            AudienceRule("lead_submitted_at", FilterOp.BEFORE, listOf("2026-05-01")),
            AudienceRule("country", FilterOp.ANY), // пустое — не отправляем
        )
        assertEquals(
            """[{"field":"fan_segment","operator":"eq","value":"super_fan"},{"field":"country","operator":"in","value":["DE","AT"]},""" +
                """{"field":"followers","operator":"gte","value":1000},{"field":"last_seen","operator":"between","value":{"from":"2026-09-01","to":"2026-10-01"}},""" +
                """{"field":"lead_submitted_at","operator":"before","value":"2026-05-01"}]""",
            rulesToFilters(rules).toString(),
        )
    }

    @Test
    fun segmentFiltersToRules() {
        val r = parseRules(json("""[{"field":"followers","operator":"gte","value":10000},{"field":"country","operator":"in","value":["DE"]},{"field":"last_seen","operator":"between","value":{"from":"a","to":"b"}}]"""))!!
        assertEquals(AudienceRule("followers", "gte", number = 10000), r[0])
        assertEquals(AudienceRule("country", "in", listOf("DE")), r[1])
        assertEquals(AudienceRule("last_seen", "between", from = "a", to = "b"), r[2])
        assertEquals(1, parseRules(json("""{"op":"and","rules":[{"field":"fan_segment","operator":"eq","value":"cold"}]}"""))!!.size)
        assertNull(parseRules(json("""{"op":"or","rules":[{"field":"fan_segment","operator":"eq","value":"cold"}]}""")), "«или» — только на сайте")
        assertEquals(emptyList(), parseRules(JsonArray(emptyList())))
        // Туда и обратно — то же самое
        assertEquals(r.filter { it.isComplete() }, parseRules(rulesToFilters(r)))
    }

    @Test
    fun presetsAndNames() {
        val today = LocalDate(2026, 10, 1)
        assertEquals(listOf(AudienceRule("followers", FilterOp.GTE, number = 10_000)), presetDefaultRules("influencers", today))
        assertEquals(listOf(AudienceRule("last_seen", FilterOp.BETWEEN, from = "2026-09-25", to = "2026-10-01")), presetDefaultRules("recent_fans", today))
        assertTrue(presetDefaultRules("everyone", today).isEmpty())
        assertEquals("Берлин суперфаны", normalizeSegmentName("  Берлин   суперфаны "))
        assertNull(normalizeSegmentName("   "))
        assertEquals("2026-01-01" to "2026-10-01", dateRangePreset(0, today))
        assertEquals(listOf(50L, 100L, 500L, 1_000L, 10_000L), numberPresets("followers", FilterOp.GTE))
        assertNull(numberPresets("followers", FilterOp.LT))
    }

    private fun backend() = FakeBackend(mapOf(
        "POST /api/audience/segments" to (HttpStatusCode.Created to """{"segment":{"id":"s9","name":"Берлин","filters":[{"field":"country","operator":"in","value":["DE"]}],"is_preset":false}}"""),
        "DELETE /api/audience/segments/s9" to (HttpStatusCode.NotFound to """{"error":"not_found"}"""),
    ))

    @Test
    fun saveAndDeleteSegment() = runTest {
        val b = backend()
        val repo = AudienceRepository(AudienceApi(b.client()))
        val scope = AudienceScope.Artist("a1")
        val filters = rulesToFilters(listOf(AudienceRule("country", FilterOp.IN, listOf("DE"))))
        assertEquals("s9", repo.createSegment(scope, "  Берлин ", filters).getOrThrow().id)
        assertEquals("""{"name":"Берлин","filters":[{"field":"country","operator":"in","value":["DE"]}],"spotify_artist_id":"a1"}""",
            (b.request("POST", "/api/audience/segments")!!.body as TextContent).text)
        assertTrue(repo.createSegment(scope, "   ", filters).isFailure)
        assertTrue(repo.deleteSegment("s9").isSuccess, "404 — уже удалён")
        // Каталога на бэкенде нет (404) — офлайн
        assertEquals(FALLBACK_FILTER_CATALOG, repo.filterCatalog(scope))
    }
}
