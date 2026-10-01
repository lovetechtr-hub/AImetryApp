package com.djmetry.ui

import com.djmetry.EdtScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.data.analytics.AudienceScope
import com.djmetry.data.analytics.LeadSource
import com.djmetry.ui.analytics.AnalyticsScreen
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «Аудитория» (вариант B «Воронка фанов») на ширинах из RULES.md §3 — без падений и пустых кадров.
 * Картинки — build/screenshots/audience-*.png. Второй прогон — бэкенд отвечает 401 (как сейчас с токеном
 * приложения): вместо данных плашка «открыть на сайте», экран не падает.
 */
class AudienceRenderTest {
    private val me = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
    private val counts = mapOf("super_fan" to 143, "casual" to 437, "cold" to 488, "fading" to 128, "former" to 88)
    private val people = listOf(
        Triple("Anna K.", "ES", "super_fan") to 92, Triple("Marco Rossi", "IT", "super_fan") to 86, Triple("Lena S.", "DE", "casual") to 74,
        Triple("Tom W.", "GB", "casual") to 58, Triple(null, "TR", "cold") to 33, Triple("Mia L.", "FR", "fading") to 21,
    ).joinToString(",") { (p, score) ->
        """{"person_id":"${p.first ?: "x"}","display_name":${p.first?.let { "\"$it\"" } ?: "null"},"country":"${p.second}","fan_segment":"${p.third}","fan_score":$score,"service":"spotify","age":27}"""
    }
    private val preview = { total: Int -> """{"total":$total,"stats":{"countries_top":[{"country":"ES","count":312},{"country":"DE","count":241},{"country":"US","count":188},{"country":"GB","count":120},{"country":"IT","count":96}],"platforms":[]},"items":[$people]}""" }
    private val segments = """{"segments":[{"id":"aud_preset_everyone","name":"Everyone","filters":[],"is_preset":true,"preset_key":"everyone"},
        {"id":"aud_preset_spotify_fans","name":"Spotify fans","filters":[{"field":"streaming_platform","operator":"includes_any","value":["spotify"]}],"is_preset":true,"preset_key":"spotify_fans"},
        {"id":"aud_1","name":"Ибица, лето","filters":[],"is_preset":false}]}"""
    private val leads = """{"total":23,"emails_hidden":true,"items":[{"lead_id":"1","full_name":"Carlos M.","city":"Madrid","source_type":"smart_link"},
        {"lead_id":"2","full_name":"Julia N.","city":"Berlin","source_type":"bio_url"},{"lead_id":"3","full_name":"Sam T.","city":"London","source_type":"tour"}]}"""

    /** Свой сегмент с правилами — первым: открывается сразу, конструктор показывает 4 строки. */
    private val segmentsWithRules = """{"segments":[{"id":"aud_9","name":"Берлин, суперфаны","is_preset":false,"filters":[
        {"field":"country","operator":"in","value":["DE","AT"]},{"field":"fan_segment","operator":"eq","value":"super_fan"},
        {"field":"followers","operator":"gte","value":1000},{"field":"last_seen","operator":"between","value":{"from":"2026-09-01","to":"2026-10-01"}}]}]}"""

    private fun engine(unauthorized: Boolean, withRules: Boolean = false) = MockEngine { req ->
        val path = req.url.encodedPath
        val body = (req.body as? TextContent)?.text.orEmpty()
        val json = when {
            unauthorized && path.startsWith("/api/audience") -> return@MockEngine respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            path == "/api/audience/preview" -> preview(counts.entries.firstOrNull { body.contains("\"${it.key}\"") }?.value ?: 1284)
            path == "/api/audience/segments" -> if (withRules) segmentsWithRules else segments
            path == "/api/audience/leads" -> leads
            path == "/api/location/countries" -> """{"countries":[{"code":"ES","name":"Испания"},{"code":"DE","name":"Германия"},{"code":"US","name":"США"},{"code":"GB","name":"Великобритания"},{"code":"IT","name":"Италия"}]}"""
            else -> """{}"""
        }
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun shot(name: String, widthDp: Int, heightDp: Int, unauthorized: Boolean = false, density: Float = 1.5f, withRules: Boolean = false) {
        com.djmetry.desktop.DesktopMapRuntime.configure()
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, engine(unauthorized, withRules))
        // Данные — заранее в кэш: холодная JVM грузит дольше, чем идут кадры
        if (!unauthorized) kotlinx.coroutines.runBlocking {
            container.audience.overview(AudienceScope.Artist("a1"))
            container.audience.leads(LeadSource.All)
            container.settings.countries()
        }
        val scene = EdtScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { AnalyticsScreen(me, onBack = {}, initialAudience = true) } }
            }
        }
        var t = 0L
        repeat(160) { scene.render(t); t += 50_000_000L; Thread.sleep(50) }
        val out = File("build/screenshots/audience-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 20_000, "$name: пустой кадр")
    }

    @Test fun phone() = shot("phone", 375, 2000)
    @Test fun tabletPortrait() = shot("tablet", 820, 1500)
    @Test fun tabletLandscape() = shot("landscape", 1180, 1000)
    @Test fun desktop() = shot("desktop", 1560, 1000)
    // Конструктор сегмента (design/analytics/audience-filters-variants.html, вариант A): правила в две строки и в одну
    @Test fun filtersPhone() = shot("filters-phone", 375, 1300, withRules = true)
    @Test fun filtersDesktop() = shot("filters-desktop", 1560, 1000, withRules = true)
    @Test fun webOnlyWhenBackendRejectsToken() = shot("web-only", 375, 800, unauthorized = true)
}
