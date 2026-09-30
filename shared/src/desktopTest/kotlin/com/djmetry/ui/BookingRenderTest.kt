package com.djmetry.ui

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
import com.djmetry.ui.booking.BookingTab
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.theme.DJMetryTheme
import io.ktor.http.HttpStatusCode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Вкладка «Букинг» (вариант A): артист с «живым» шоу и агентство — телефон, планшет, десктоп. build/screenshots/booking-*.png */
class BookingRenderTest {
    private val ok = HttpStatusCode.OK
    private fun r(id: String, n: Int, artist: String, loc: String, date: String, status: String, amount: Int?, pay: String, read: Boolean = true) =
        """{"id":"$id","request_number":$n,"status":"$status","event_date":"$date","event_time":"23:00","event_location":"$loc","expected_attendees":1200,
           "payment_status":"$pay","payment_amount":${amount ?: "null"},"payment_currency":"EUR","company_fee_amount":${amount?.let { it * 15 / 100 } ?: "null"},"company_tax_percent":15,
           "artist_fee_amount":${amount?.let { it * 85 / 100 } ?: "null"},"requester_name":"Live Nation","is_read":$read,"company_name":"Booking Machine","company":{"id":"c1","name":"Booking Machine"},
           "artists":[{"spotify_artist_id":"a1","name":"$artist"}]}"""
    private val list = listOf(
        r("1", 24, "Calvin Harris", "Barcelona, Razzmatazz", "2026-10-12", "new", null, "unpaid", read = false),
        r("2", 23, "Anyma", "Amsterdam, Ziggo Dome", "2026-10-18", "in_progress", 12000, "unpaid"),
        r("3", 21, "Peggy Gou", "Berlin, Tempodrom", "2026-11-02", "paid", 8500, "partially_paid"),
        r("4", 20, "Alok", "Madrid, WiZink", "2026-11-15", "artist_on_the_way", 10000, "paid"),
    ).joinToString(",")
    private val routes = mapOf(
        "GET /api/booking/companies/my" to (ok to """{"companies":[{"id":"c1","name":"Booking Machine","my_role":"owner"}]}"""),
        "GET /api/booking/companies/c1/requests" to (ok to """{"requests":[$list]}"""),
        "GET /api/booking/artists/a1/requests" to (ok to """{"requests":[$list]}"""),
        "GET /api/booking/my-requests" to (ok to """{"requests":[]}"""),
        "GET /api/booking/requests/1" to (ok to """{"request":${r("1", 24, "Calvin Harris", "Barcelona, Razzmatazz", "2026-10-12", "new", null, "unpaid")}}"""),
    )

    private fun shot(name: String, widthDp: Int, heightDp: Int, me: MeResponse) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        val density = 1.5f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { BookingTab(me) } }
            }
        }
        var t = 0L
        repeat(90) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        val out = File("build/screenshots/booking-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    private val artist = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
    private val agency = MeResponse(isAuthed = true)

    @Test fun phoneArtist() = shot("phone-artist", 430, 932, artist)
    @Test fun phoneAgency() = shot("phone-agency", 430, 932, agency)
    @Test fun tablet() = shot("tablet", 820, 1180, agency)
    @Test fun desktop() = shot("desktop", 1440, 900, agency)
}
