package com.djmetry.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.ui.djmap.DjMapScreen
import com.djmetry.data.djmap.topTouringParams
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Карта диджеев на ширинах из RULES.md §3 — интерфейс поверх карты (поиск, фильтры, лента лидеров, слои) без падений.
 * Карта — заглушка (LocalInspectionMode): нативный MapLibre в тестах не поднимаем; живая карта проверена на Android.
 * Кадры — build/screenshots/djmap-*.png.
 */
class DjMapRenderTest {
    private val ok = HttpStatusCode.OK
    private val djs = listOf("John Summit" to 33, "Steve Aoki" to 32, "Madeon" to 30, "Vintage Culture" to 28, "Alan Walker" to 27, "Jackie Hollander" to 25)
    private val routes = mapOf(
        "GET /api/map/filters" to (ok to """{"countries":[{"country":"United States","count":1025}],"genres":["techno","house"]}"""),
        "GET /api/map/top-touring" to (ok to """{"djs":[${djs.mapIndexed { i, (n, e) -> """{"spotify_artist_id":"id$i","name":"$n","events":$e,"cities":${e / 2},"countries":${e / 4},"genres":["house"]}""" }.joinToString(",")}],"total":6}"""),
        "GET /api/map/performances" to (ok to """{"clusters":[],"points":[],"total":0}"""),
        "GET /api/location/countries" to (ok to """{"countries":[]}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int) {
        val density = 1.5f
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        // Прогрев кэша: холодная JVM грузит дольше, чем идут кадры
        kotlinx.coroutines.runBlocking {
            container.djMap.filters()
            container.djMap.topTouring(com.djmetry.data.djmap.MapFilters().topTouringParams())
        }
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { DjMapScreen() } }
            }
        }
        var t = 0L
        repeat(80) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/djmap-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 10_000, "$name: пустой кадр")
    }

    @Test fun phoneSE() = shot("phone", 375, 812)
    @Test fun tabletPortrait() = shot("tablet", 820, 1180)
    @Test fun tabletLandscape() = shot("landscape", 1180, 820)
    @Test fun desktop() = shot("desktop", 1560, 1000)
}
