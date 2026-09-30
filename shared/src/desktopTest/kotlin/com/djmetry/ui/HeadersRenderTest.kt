package com.djmetry.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.djmetry.ui.rating.RatingTab
import com.djmetry.ui.screens.DiscoverTab
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Новые шапки (вариант A, design/navigation/header-variants.html): «Открытия» и «Рейтинг» на iPhone SE, iPhone Pro Max,
 * iPad портрет и десктоп — без падений и пустых кадров. Картинки — build/screenshots/header-*.png.
 */
class HeadersRenderTest {
    private val ok = HttpStatusCode.OK
    private val top = """{"count":6,"artists":[""" + listOf("David Guetta" to 53.16, "Calvin Harris" to 51.46, "Tiësto" to 48.72, "Martin Garrix" to 48.32, "Alok" to 48.27, "Fred again.." to 48.07)
        .mapIndexed { i, (n, sc) -> """{"spotify_artist_id":"a$i","name":"$n","score":$sc,"position":${i + 1},"genres":["edm"]}""" }.joinToString(",") + "]}"
    private val routes = mapOf(
        "GET /api/artists/top100" to (ok to top),
        "GET /api/artists/available-limits" to (ok to """{"limits":["100","200","300","400","talents"]}"""),
        "GET /api/artists/available-genres" to (ok to """{"genres":["edm","techno"],"count":2}"""),
        "GET /api/artists/trends" to (ok to """{"category":"growing","count":2,"artists":[{"spotifyArtistId":"x1","name":"Anyma","aimetryScore":48.9},{"spotifyArtistId":"x2","name":"Argy","aimetryScore":44.1}]}"""),
        "GET /api/me/follows" to (ok to """{"follows":[{"spotifyArtistId":"f1","name":"F"}],"count":1}"""),
        "GET /api/vote/status" to (ok to """{"votes":[],"count":0}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        val density = 1.5f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { content() } }
            }
        }
        var t = 0L
        repeat(80) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/header-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    @Test fun discoverSe() = shot("discover-se", 375, 667) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverProMax() = shot("discover-promax", 430, 932) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverTablet() = shot("discover-tablet", 820, 1180) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverDesktop() = shot("discover-desktop", 1440, 900) { DiscoverTab(onOpenSearch = {}) }
    @Test fun ratingSe() = shot("rating-se", 375, 667) { RatingTab(rememberLazyListState()) }
    @Test fun ratingProMax() = shot("rating-promax", 430, 932) { RatingTab(rememberLazyListState()) }
    @Test fun ratingTablet() = shot("rating-tablet", 820, 1180) { RatingTab(rememberLazyListState()) }
    @Test fun ratingDesktop() = shot("rating-desktop", 1440, 900) { RatingTab(rememberLazyListState()) }
}
