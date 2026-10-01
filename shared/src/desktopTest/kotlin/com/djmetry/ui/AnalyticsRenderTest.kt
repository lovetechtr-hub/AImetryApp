package com.djmetry.ui

import com.djmetry.EdtScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.ui.analytics.AnalyticsScreen
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Аналитика на ширинах из RULES.md §3: телефон, iPad портрет и альбом, десктоп — без падений и пустых кадров.
 * Картинки — build/screenshots/analytics-*.png (высокие, чтобы видеть всю прокрутку). Карта в режиме превью
 * (LocalInspectionMode) — серая заглушка MapLibre: нативный движок в тесте не поднимаем.
 */
class AnalyticsRenderTest {
    private val ok = HttpStatusCode.OK
    private val me = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
    private val network = """{"range":{"preset":"7d","from":"2026-09-22","to":"2026-09-29"},
        "totals":{"total_page_views":12480,"anonymous_page_views":9300,"total_click_events":5100,"total_clicks":3140,"djmetry_logged_page_views":3180,
          "verified_artist_page_views":640,"booking_company_page_views":310,"other_logged_page_views":2230},
        "by_segment":[{"segment":"anonymous","unique_viewers":7100,"page_views":9300},{"segment":"logged_in","unique_viewers":1400,"page_views":2200},
          {"segment":"verified_artist","unique_viewers":300,"page_views":640},{"segment":"booking_company","unique_viewers":120,"page_views":310},{"segment":"admin","unique_viewers":10,"page_views":30}],
        "click_breakdown":[{"content_type":"spotify","event_type":"outbound_click","count":1180},{"content_type":"beatport","event_type":"outbound_click","count":640},
          {"content_type":"instagram","event_type":"social_click","count":510},{"content_type":"soundcloud","event_type":"social_click","count":380},{"content_type":null,"event_type":"event_click","count":260}],
        "top_viewers":[{"viewer_user_id":"u1","segment":"booking_company","display_label":"Booking Machine","page_views":14,"last_seen_at":"2026-09-28 10:00:00"},
          {"viewer_user_id":"u2","segment":"verified_artist","display_label":"Anyma","page_views":6,"last_seen_at":"2026-09-27 10:00:00"}]}"""
    private val breakdown = """{"range":{"preset":"7d","from":"2026-09-22","to":"2026-09-29"},
        "countries":[{"country_code":"DE","visits":3120,"clicks":800,"ctr":25.6},{"country_code":"US","visits":2410,"clicks":500,"ctr":20.7},{"country_code":"GB","visits":1380,"clicks":300,"ctr":21.7},
          {"country_code":"NL","visits":960,"clicks":200,"ctr":20.8},{"country_code":"ES","visits":840,"clicks":150,"ctr":17.9},{"country_code":"BR","visits":610,"clicks":90,"ctr":14.8}],
        "cities":[{"city":"Berlin","country_code":"DE","visits":1900,"clicks":500,"ctr":26.3},{"city":"London","country_code":"GB","visits":1100,"clicks":250,"ctr":22.7}],
        "referrals":[{"referral":"instagram.com","visits":4100,"clicks":1100,"ctr":26.8,"ctr_display_label":"26.83%"},{"referral":"direct","visits":3900,"clicks":700,"ctr":17.9,"ctr_display_label":"17.95%"},{"referral":"t.co","visits":900,"clicks":150,"ctr":16.7,"ctr_display_label":"16.67%"}],
        "devices":[{"name":"mobile","visits":8860,"clicks":0,"ctr":0},{"name":"desktop","visits":2870,"clicks":0,"ctr":0},{"name":"tablet","visits":750,"clicks":0,"ctr":0}],
        "browsers":[{"name":"Chrome","visits":6000,"clicks":0,"ctr":0},{"name":"Safari","visits":5000,"clicks":0,"ctr":0}],"os":[{"name":"iOS","visits":6000,"clicks":0,"ctr":0},{"name":"Android","visits":4000,"clicks":0,"ctr":0}],
        "secret_link_leads":{"total":57}}"""
    private val routes = listOf("music-page/analytics/bio-network", "artist-catalog/analytics/network").associate { "GET /api/me/$it" to (ok to network) } +
        listOf("music-page", "artist-catalog").associate { "GET /api/me/$it/analytics/breakdown" to (ok to breakdown) } +
        listOf("music-page", "artist-catalog").associate { "GET /api/me/$it/analytics/geo-options" to (ok to """{"countries":[{"code":"DE","name":"Germany"}],"cities":[]}""") } +
        mapOf("GET /api/location/countries" to (ok to """{"countries":[{"code":"DE","name":"Германия"},{"code":"US","name":"США"},{"code":"GB","name":"Великобритания"},{"code":"NL","name":"Нидерланды"},{"code":"ES","name":"Испания"},{"code":"BR","name":"Бразилия"}]}"""))

    private fun shot(name: String, widthDp: Int, heightDp: Int, density: Float = 1.5f) {
        com.djmetry.desktop.DesktopMapRuntime.configure()
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        // Данные — заранее в кэш репозитория: холодная JVM первого теста грузит дольше, чем идут кадры
        kotlinx.coroutines.runBlocking {
            container.analytics.load(com.djmetry.data.analytics.AnalyticsSource.DJMetry, com.djmetry.data.analytics.AnalyticsQuery())
            container.settings.countries()
        }
        val scene = EdtScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { AnalyticsScreen(me, onBack = {}) } }
            }
        }
        var t = 0L
        repeat(160) { scene.render(t); t += 50_000_000L; Thread.sleep(50) }
        val out = File("build/screenshots/analytics-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 20_000, "$name: пустой кадр")
    }

    @Test fun phone() = shot("phone", 375, 2300)
    @Test fun tabletPortrait() = shot("tablet", 820, 1900)
    @Test fun tabletLandscape() = shot("landscape", 1180, 1100)
    @Test fun desktop() = shot("desktop", 1560, 1000)
}
