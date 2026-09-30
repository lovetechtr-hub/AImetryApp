package com.djmetry.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.radar.RadarTab
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Вкладка «Радар» (вариант A) на телефоне, планшете и десктопе — без падений и пустых кадров. build/screenshots/radar-*.png */
class RadarRenderTest {
    private val ok = HttpStatusCode.OK
    private val names = listOf("Alan Walker", "Alok", "BLOND:ISH", "Anyma", "Peggy Gou", "Fisher")
    private val feed = """{"artists":[""" + names.mapIndexed { i, n ->
        val rel = (0 until 5).joinToString(",") { j -> """{"album_id":"$i-$j","name":"${listOf("Golden Bird", "Pain", "VIRAL (Remix)", "FATE", "World of Walker")[j]}","album_type":"${if (j == 4) "album" else "single"}","release_date":"2026-0${9 - j}-2${j}"}""" }
        """{"spotify_artist_id":"a$i","artist_name":"$n","total_releases":${if (i == 0) 11 else 5},"latest":[$rel],"genres":["house"]}"""
    }.joinToString(",") + "]}"
    private val routes = mapOf(
        "GET /api/me/release-radar/feed" to (ok to feed),
        "GET /api/me/notifications" to (ok to """{"items":[{"id":"1","type":"release_radar","read":false,"meta":{"spotify_artist_id":"a2"}}]}"""),
        "GET /api/me/concert-alerts" to (ok to """{"effectiveCity":"Barcelona","effectiveCountry":"ES"}"""),
        "GET /api/artists/a0/events" to (ok to """{"events":[{"eventId":"e1","datetime":"2026-10-12T23:00:00","venue":{"name":"Razzmatazz","city":"Barcelona","country":"Spain"},"offers":[{"url":"https://t"}]}]}"""),
        "GET /api/artists/a3/events" to (ok to """{"events":[{"eventId":"e2","datetime":"2026-10-18T22:00:00","venue":{"name":"Ziggo Dome","city":"Amsterdam","country":"Netherlands"}},{"eventId":"e3","datetime":"2026-11-02T22:00:00","venue":{"name":"Tempodrom","city":"Berlin","country":"Germany"}}]}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        val density = 1.5f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { RadarTab() } }
            }
        }
        var t = 0L
        repeat(100) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/radar-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    @Test fun phone() = shot("phone", 430, 932)
    @Test fun tablet() = shot("tablet", 820, 1180)
    @Test fun desktop() = shot("desktop", 1440, 900)
}
