package com.djmetry.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.EdtScene
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.data.search.RecentArtist
import com.djmetry.data.search.SearchScope
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.screens.SearchTab
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Поиск с историей (вариант A): телефон, планшет, десктоп — build/screenshots/search-history-*.png */
class SearchHistoryRenderTest {
    private val top = """{"count":2,"artists":[{"spotify_artist_id":"t1","name":"David Guetta","score":53,"position":1,"genres":["edm"]},{"spotify_artist_id":"t2","name":"Calvin Harris","score":51,"position":2,"genres":["edm"]}]}"""

    private fun shot(name: String, w: Int, h: Int) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(mapOf("GET /api/artists/top100" to (HttpStatusCode.OK to top))).engine)
        listOf("afterlife", "peggy gou", "fisher", "melodic techno berlin", "anyma").forEach { container.searchHistory.record(SearchScope.Artists, it) }
        listOf("Charlotte de Witte", "Peggy Gou", "FISHER", "Anyma").forEachIndexed { i, n -> container.searchHistory.recordArtist(RecentArtist("a$i", n)) }
        val density = 1.5f
        val scene = EdtScene((w * density).toInt(), (h * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(w.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { SearchTab() } }
            }
        }
        var t = 0L
        repeat(60) { scene.render(t); t += 50_000_000L; Thread.sleep(20) }
        val out = File("build/screenshots/search-history-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    @Test fun phone() = shot("phone", 390, 844)
    @Test fun desktop() = shot("desktop", 1280, 820)
}
