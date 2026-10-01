package com.djmetry.ui

import com.djmetry.EdtScene
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
        "GET /api/artists/trends" to (ok to """{"category":"growing","count":2,"artists":[{"spotifyArtistId":"x1","name":"Anyma","aimetryScore":48.9,"genres":["melodic techno"],"position":27,"trend":{"score24h":-34.9,"score7d":-34.9,"growthRate":-34.9,"volatility":17.4}},{"spotifyArtistId":"x2","name":"Argy","aimetryScore":44.1}]}"""),
        "GET /api/me/follows" to (ok to """{"follows":[""" + listOf("CHRIS STASSY" to 412000, "Robin Schulz" to 8100000, "Diplo" to 6400000, "Major Lazer" to 12000000, "Kygo" to 9700000, "ATB" to 1900000)
            .mapIndexed { i, (n, f) -> """{"spotifyArtistId":"f$i","name":"$n","followers":$f,"createdAt":"2026-09-2${i}"}""" }.joinToString(",") + """],"count":6}"""),
        "GET /api/artists/spotify/f0" to (ok to """{"spotifyArtistId":"f0","name":"CHRIS STASSY","genres":["melodic house"],"country":"DE","aimetryScore":48.3,"position":27,"followers":412000}"""),
        "GET /api/vote/status" to (ok to """{"votes":["f0","f2"],"count":2}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int, minBytes: Int = 15_000, content: @androidx.compose.runtime.Composable () -> Unit) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        val density = 1.5f
        val scene = EdtScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { content() } }
            }
        }
        var t = 0L
        repeat(80) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/header-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > minBytes, "$name: пустой кадр")
    }

    @Test fun discoverSe() = shot("discover-se", 375, 667) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverProMax() = shot("discover-promax", 430, 932) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverTablet() = shot("discover-tablet", 820, 1180) { DiscoverTab(onOpenSearch = {}) }
    @Test fun discoverDesktop() = shot("discover-desktop", 1440, 900) { DiscoverTab(onOpenSearch = {}) }
    @Test fun followingPhone() = shot("following-phone", 430, 932) { DiscoverTab(onOpenSearch = {}, initialMode = 1) }
    @Test fun followingDesktop() = shot("following-desktop", 1440, 900) { DiscoverTab(onOpenSearch = {}, initialMode = 1) }
    @Test fun trendPills() = shot("trend-pills", 375, 170, minBytes = 4_000) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.background(com.djmetry.ui.theme.DJMetryColors.Background).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            com.djmetry.ui.screens.TrendPills(com.djmetry.api.models.TrendInfo(score24h = -34.9, score7d = -34.9, growthRate = -34.9))
            com.djmetry.ui.screens.TrendPills(com.djmetry.api.models.TrendInfo(score24h = 6.5, score7d = 6.5, growthRate = 6.5))
            com.djmetry.ui.screens.TrendLine(com.djmetry.api.models.TrendInfo(score24h = -34.8, score7d = -34.8, growthRate = -34.8))
        }
    }
    @Test fun ratingSe() = shot("rating-se", 375, 667) { RatingTab(rememberLazyListState()) }
    @Test fun ratingProMax() = shot("rating-promax", 430, 932) { RatingTab(rememberLazyListState()) }
    @Test fun ratingTablet() = shot("rating-tablet", 820, 1180) { RatingTab(rememberLazyListState()) }
    @Test fun ratingDesktop() = shot("rating-desktop", 1440, 900) { RatingTab(rememberLazyListState()) }
}
