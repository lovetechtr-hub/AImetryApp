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
        "GET /api/map/dj/id1/tour" to (ok to """{"spotifyArtistId":"id1","points":[{"event_id":"e0","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-05T22:00:00","venue_name":"Club 0","venue_city":"Orlando","venue_country":"US","lat":28.5,"lng":-81.4},{"event_id":"e1","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-06T22:00:00","venue_name":"Club 1","venue_city":"Austin","venue_country":"US","lat":30.3,"lng":-97.7},{"event_id":"e2","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-07T22:00:00","venue_name":"Club 2","venue_city":"Irving","venue_country":"US","lat":32.8,"lng":-96.9},{"event_id":"e3","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-08T22:00:00","venue_name":"Club 3","venue_city":"Houston","venue_country":"US","lat":29.8,"lng":-95.4},{"event_id":"e4","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-09T22:00:00","venue_name":"Club 4","venue_city":"Nashville","venue_country":"US","lat":36.2,"lng":-86.8},{"event_id":"e5","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-10T22:00:00","venue_name":"Club 5","venue_city":"Boston","venue_country":"US","lat":42.4,"lng":-71.1},{"event_id":"e6","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-11T22:00:00","venue_name":"Club 6","venue_city":"New York","venue_country":"US","lat":40.7,"lng":-74.0},{"event_id":"e7","spotify_artist_id":"id1","artist_name":"Steve Aoki","datetime":"2026-10-12T22:00:00","venue_name":"Club 7","venue_city":"Toronto","venue_country":"CA","lat":43.7,"lng":-79.4}],"total":8}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int, artistId: String? = null) {
        val density = 1.5f
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        // Прогрев кэша: холодная JVM грузит дольше, чем идут кадры
        kotlinx.coroutines.runBlocking {
            container.djMap.filters()
            container.djMap.topTouring(com.djmetry.data.djmap.MapFilters().topTouringParams())
        }
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { DjMapScreen(initialArtistId = artistId) } }
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
    /** Тур одного DJ на десктопе: лента городов с самолётиком (на телефоне самолёта нет). */
    @Test fun desktopTour() = shot("desktop-tour", 1560, 1000, artistId = "id1")
}
