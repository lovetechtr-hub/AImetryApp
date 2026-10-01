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
import com.djmetry.ui.screens.DiscoverTab
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «Открытия», вариант A (design/discover/deck-end-variants.html): финальная карточка подборки (итог, «Дальше» с превью,
 * «Пропущенные», «Подписки») и карточка TOP 10 с отметками «Вы следите» / «Ваш голос». build/screenshots/deckend-*.png
 */
class DeckEndRenderTest {
    private val ok = HttpStatusCode.OK
    private val trends = """{"category":"growing","count":3,"artists":[""" + listOf("Anyma", "Fisher", "Peggy Gou")
        .mapIndexed { i, n -> """{"spotifyArtistId":"t$i","name":"$n","aimetryScore":4$i.5,"genres":["techno"],"position":${20 + i}}""" }.joinToString(",") + "]}"
    private fun routes(top: String) = mapOf(
        "GET /api/artists/top100" to (ok to top),
        "GET /api/artists/trends" to (ok to trends),
        "GET /api/me/follows" to (ok to """{"follows":[{"spotifyArtistId":"a1","name":"Calvin Harris"}],"count":1}"""),
        "GET /api/vote/status" to (ok to """{"votes":["a1"],"count":1}"""),
    )
    private val emptyTop = """{"count":0,"artists":[]}"""
    private val top = """{"count":2,"artists":[{"spotify_artist_id":"a1","name":"Calvin Harris","score":51.46,"position":2,"genres":["edm"]},{"spotify_artist_id":"a2","name":"Tiësto","score":48.7,"position":3,"genres":["edm"]}]}"""

    private fun shot(name: String, widthDp: Int, heightDp: Int, top: String) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes(top)).engine)
        val density = 1.5f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { DiscoverTab(onOpenSearch = {}) } }
            }
        }
        var t = 0L
        repeat(90) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/deckend-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    // TOP 10 пуст (всё просмотрено) — финальная карточка с превью «Растут сейчас»
    @Test fun endPhone() = shot("end-phone", 390, 844, emptyTop)
    @Test fun endTablet() = shot("end-tablet", 820, 1180, emptyTop)
    @Test fun endDesktop() = shot("end-desktop", 1440, 900, emptyTop)
    // TOP 10: Calvin Harris — «Вы следите» и «Ваш голос», кнопки показывают состояние
    @Test fun badgesPhone() = shot("badges-phone", 390, 844, top)
    @Test fun badgesDesktop() = shot("badges-desktop", 1440, 900, top)
}
