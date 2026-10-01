package com.djmetry.desktop

import com.djmetry.EdtScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.screens.MainShell
import com.djmetry.ui.screens.MainTab
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Скриншот-тесты профиля (вариант A) на трёх ширинах из RULES.md §3: телефон, iPad портрет, десктоп.
 * Данные — тестовый бэкенд с формами прода; картинки — build/screenshots/.
 */
@Ignore // Программная отрисовка ImageComposeScene с фото из сети идёт > 40 мин — переделать на лёгкий рендер (журнал RULES.md)
@OptIn(ExperimentalComposeUiApi::class)
class ProfileScreenshotTest {

    private val id = "5QnualJTEdYTH1bUfyziGn"
    private val img = "https://i.scdn.co/image/"
    private val me = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = id))

    private val routes = mapOf(
        "GET /api/artists/spotify/$id" to (HttpStatusCode.OK to """{"spotifyArtistId":"$id","name":"LovetechMusic","imageUrl":"${img}ab6761610000e5eb9fa9bac4a3e25c98c7da3aef","aimetryScore":20.43,"position":1435,"votes":1,"followsCount":6,"city":"Barcelona","country":"ES","isVerified":true,"genres":["melodic house","melodic techno"]}"""),
        "GET /api/me/snapshots" to (HttpStatusCode.OK to """{"snapshots":[{"date":"1","score":18.6},{"date":"2","score":18.9},{"date":"3","score":18.7},{"date":"4","score":19.4},{"date":"5","score":19.2},{"date":"6","score":19.9},{"date":"7","score":20.43}]}"""),
        "GET /api/artists/spotify/$id/tracks" to (HttpStatusCode.OK to """{"tracks":[{"name":"Elephant","albumImageUrl":"${img}ab67616d0000b2733de69e3a941af9cfd4dae9e8"},{"name":"Triad","albumImageUrl":"${img}ab67616d0000b273804af853bfe15591b99a13a5"},{"name":"Julia","albumImageUrl":"${img}ab67616d0000b273993c0a57a6cf933c590419ad"}]}"""),
        "GET /api/booking/artists/public/$id/booking" to (HttpStatusCode.OK to """{"count":1,"companies":[{"id":"c1","name":"Booking Machine","country":"TR","city":"Antalya"}]}"""),
        "GET /api/booking/artists/$id/requests" to (HttpStatusCode.OK to """{"requests":[{"id":"r2","number":2,"status":"artist_finished_performance","event_date":"2026-06-24","country":"ES","payment_amount":10000,"payment_currency":"USD"},{"id":"r1","number":1,"status":"artist_finished_performance","event_date":"2026-06-17","country":"TR","payment_amount":10000,"payment_currency":"USD"}]}"""),
        "GET /api/me/release-radar/feed" to (HttpStatusCode.OK to """{"artists":[{"spotify_artist_id":"m","artist_name":"Marshmello","latest":[{"album_id":"a1","name":"Happier","release_date":"2026-09-20","image_url":"${img}ab67616d00001e02dd0a40eecd4b13e4c59988da"}]},{"spotify_artist_id":"f","artist_name":"FISHER","latest":[{"album_id":"a2","name":"Losing It","release_date":"2026-09-25","image_url":"${img}ab67616d00001e029367c1ee2eec0bf3a04b4868"}]}]}"""),
        "GET /api/me/notifications/unread-count" to (HttpStatusCode.OK to """{"unread_total":3}"""),
        "GET /api/me/notifications" to (HttpStatusCode.OK to """{"unread_total":3,"items":[
            {"id":"n1","type":"booking","read":false,"created_at":"2026-09-28T10:00:00Z","title":"Заявка №2: выступление завершено","body":"Booking Machine · Barcelona"},
            {"id":"n2","type":"release_radar","read":false,"created_at":"2026-09-28T13:00:00Z","title":"Happier","body":"Marshmello, Bastille","image_url":"${img}ab67616d00001e02dd0a40eecd4b13e4c59988da"},
            {"id":"n3","type":"pre_save","read":false,"created_at":"2026-09-28T08:00:00Z","title":"Elephant: 24 новых pre-save","body":"djm.fm/elephant"},
            {"id":"n4","type":"concert","read":true,"created_at":"2026-09-27T12:00:00Z","title":"Martin Garrix рядом","body":"EDC Orlando · 6 нояб"}]}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int, tapDp: Offset? = null) {
        val density = 2f
        val storage = FakeSessionStorage().apply { saveLocale("ru") }
        val container = AppContainer(storage, FakeBackend(routes).engine)
        val scene = EdtScene(widthDp * density.toInt(), heightDp * density.toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container) {
                DJMetryTheme {
                    I18nProvider(localizationManager = container.localization) {
                        MainShell(me = me, onLoggedOut = {}, initialTab = MainTab.Profile)
                    }
                }
            }
        }
        var t = 0L
        fun frames(n: Int) = repeat(n) { scene.render(t); t += 50_000_000L; Thread.sleep(60) }
        frames(80) // данные + картинки из сети
        tapDp?.let {
            val p = Offset(it.x * density, it.y * density)
            scene.sendPointerEvent(PointerEventType.Press, p)
            scene.sendPointerEvent(PointerEventType.Release, p)
            frames(40)
        }
        val image = scene.render(t)
        val out = File("build/screenshots/$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 20_000, "$name: пустой кадр")
    }

    @Test fun phone() = shot("profile-phone", 390, 844)
    @Test fun phoneNotificationsSheet() = shot("profile-phone-notifications", 390, 844, tapDp = Offset(390f - 16 - 14 - 25, 8f + 14 + 22))
    @Test fun tabletPortrait() = shot("profile-tablet", 820, 1180)
    @Test fun tabletNotificationsPopover() = shot("profile-tablet-notifications", 820, 1180, tapDp = Offset(820f - 24 - 25, 16f + 22))
    @Test fun desktop() = shot("profile-desktop", 1440, 900)
}
