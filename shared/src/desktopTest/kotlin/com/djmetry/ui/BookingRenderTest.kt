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
import com.djmetry.ui.booking.BookingRequestScreen
import com.djmetry.data.booking.CabinetSection
import androidx.compose.runtime.Composable
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
        "GET /api/booking/companies/c1/earnings" to (ok to """{"week":{"by_currency_company_fee":{"EUR":1800},"by_currency_company_fee_after_tax":{"EUR":1530},"total_requests":1},
            "month":{"by_currency_company_fee":{"EUR":3075,"USD":1500},"by_currency_company_fee_after_tax":{"EUR":2614,"USD":1275},"total_requests":3},"year":{"total_requests":9,"by_currency_company_fee_after_tax":{"EUR":21400}}}"""),
        "GET /api/booking/artists/a1/earnings" to (ok to """{"month":{"by_currency_artist_fee":{"EUR":17225},"by_currency_artist_fee_after_tax":{"EUR":14641},"total_requests":2},
            "all_time":{"by_currency_artist_fee_after_tax":{"EUR":48900},"total_requests":14}}"""),
        "GET /api/booking/companies/c1" to (ok to """{"company":{"id":"c1","name":"Booking Machine","city":"Berlin","country":"DE","is_verified":1,"default_company_tax_percent":10,"default_artist_calculates_own_tax":false},
            "artists":[{"spotify_artist_id":"a1","name":"Calvin Harris","approved":1,"default_artist_tax_percent":15},{"spotify_artist_id":"a2","name":"Anyma","approved":1,"default_artist_tax_percent":10},
            {"spotify_artist_id":"a3","name":"Nora V.","approved":0}],"myRole":"owner"}"""),
        "GET /api/booking/companies/c1/members" to (ok to """{"count":3,"members":[{"id":1,"user_id":"u1","user_display_name":"Anna K.","role":"owner","invite_status":"accepted","accept_all_requests":true},
            {"id":2,"user_id":"u2","user_display_name":"Mark R.","role":"manager","invite_status":"accepted","accept_all_requests":false,"responsible_regions":["DE","AT"]},
            {"id":3,"invited_email":"ivan@example.com","role":"manager","invite_status":"pending"}]}"""),
        "GET /api/booking/companies/c1/performances" to (ok to """{"total":4,"performances":[{"id":"p1","event_country":"DE","event_date":"2026-06-24","event_location":"Berlin, Germany","payment_amount":9000,"payment_currency":"USD","artist_names":"Calvin Harris"},
            {"id":"p2","event_country":"ES","event_date":"2026-06-17","event_location":"Madrid, Spain","payment_amount":8000,"payment_currency":"EUR","artist_names":"Anyma"},
            {"id":"p3","event_country":"TR","event_date":"2026-05-02","event_location":"Antalya, Türkiye","artist_names":"Anyma"}]}"""),
        "GET /api/booking/artists/a1/performances" to (ok to """{"total":0,"performances":[]}"""),
        "GET /api/booking/artists/a1/companies" to (ok to """{"companies":[{"company_id":"c1","approved":true,"company":{"id":"c1","name":"Booking Machine","city":"Berlin","country":"DE"}}]}"""),
        "GET /api/booking/artists/a1/tax-default" to (ok to """{"default_artist_tax_percent":15}"""),
        "GET /api/booking/artists/a1/rider" to (ok to """{"rider_url":"booking/a1/rider.pdf"}"""),
        "GET /api/booking/artists/a1/press-kit" to (HttpStatusCode.NoContent to ""),
        "GET /api/artists/spotify/a1/tracks" to (ok to """{"tracks":[{"name":"Solar Echoes"},{"name":"Nightline (Extended)"},{"name":"Afterglow"}]}"""),
        "GET /api/artists/spotify/a2/tracks" to (ok to """{"tracks":[{"name":"Hypnotized"},{"name":"Syren"}]}"""),
        "GET /api/artists/spotify/a3/tracks" to (ok to """{"tracks":[]}"""),
        "GET /api/booking/artists/public/a1/booking" to (ok to """{"count":2,"companies":[{"id":"c1","name":"Booking Machine","city":"Berlin"},{"id":"c2","name":"Wave Agency"}]}"""),
        "GET /api/booking/companies/by-slug/c1" to (ok to """{"id":"c1","name":"Booking Machine","artists":[{"spotify_artist_id":"a1","name":"Calvin Harris"},{"spotify_artist_id":"a2","name":"Anyma"},{"spotify_artist_id":"a3","name":"Nora V."}]}"""),
        "GET /api/location/countries" to (ok to """{"countries":[{"code":"DE","name":"Germany"}]}"""),
        "GET /api/booking/requests/1" to (ok to """{"request":${r("1", 24, "Calvin Harris", "Barcelona, Razzmatazz", "2026-10-12", "new", null, "unpaid")}}"""),
    )

    /** Под нагрузкой общего прогона одна из параллельных загрузок изредка срывается — одна повторная сцена с нуля. */
    private fun shot(name: String, widthDp: Int, heightDp: Int, me: MeResponse?, section: CabinetSection? = null, content: (@Composable () -> Unit)? = null) {
        if (!runCatching { shotOnce(name, widthDp, heightDp, me, section, content) }.isSuccess) shotOnce(name, widthDp, heightDp, me, section, content)
    }

    private fun shotOnce(name: String, widthDp: Int, heightDp: Int, me: MeResponse?, section: CabinetSection? = null, content: (@Composable () -> Unit)? = null) {
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, FakeBackend(routes).engine)
        val density = 1.5f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(widthDp.toFloat())) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { content?.invoke() ?: BookingTab(me, section) } }
            }
        }
        var t = 0L
        repeat(90) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
        // Под нагрузкой (полный прогон) сеть фейка отвечает медленнее — ждём содержимое, а не скелет, до ~20 с
        var bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        var tries = 0
        while (bytes.size <= 15_000 && tries++ < 65) {
            repeat(10) { scene.render(t); t += 50_000_000L; Thread.sleep(30) }
            bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/screenshots/booking-$name.png").apply { parentFile.mkdirs() }
        out.writeBytes(bytes)
        scene.close()
        assertTrue(out.length() > 15_000, "$name: пустой кадр")
    }

    private val artist = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
    private val agency = MeResponse(isAuthed = true)

    @Test fun phoneArtist() = shot("phone-artist", 430, 932, artist)
    @Test fun phoneAgency() = shot("phone-agency", 430, 932, agency)
    @Test fun tablet() = shot("tablet", 820, 1180, agency)
    @Test fun desktop() = shot("desktop", 1440, 900, agency)

    // Кабинет (вариант A): разделы на телефоне — на весь экран, на десктопе — панелью справа
    @Test fun sectionArtists() = shot("cab-artists", 430, 1400, agency, CabinetSection.Artists)
    @Test fun sectionTeam() = shot("cab-team", 430, 932, agency, CabinetSection.Team)
    @Test fun sectionTaxes() = shot("cab-taxes", 430, 932, agency, CabinetSection.Taxes)
    @Test fun sectionMap() = shot("cab-map", 430, 932, agency, CabinetSection.Map)
    @Test fun sectionCompanies() = shot("cab-companies", 430, 932, artist, CabinetSection.Companies)
    @Test fun sectionFiles() = shot("cab-files", 430, 932, artist, CabinetSection.Files)
    @Test fun desktopSection() = shot("cab-desktop", 1440, 900, agency, CabinetSection.Team)
    @Test fun tabletSection() = shot("cab-tablet", 1180, 820, agency, CabinetSection.Taxes)

    // «Оставить заявку» (вариант B): телефон — на весь экран, десктоп — окном
    @Test fun requestPhone() = shot("request-phone", 430, 932, null) { BookingRequestScreen("a1", {}, {}) }
    @Test fun requestDesktop() = shot("request-desktop", 1440, 900, null) { BookingRequestScreen("a1", {}, {}) }

    @Test
    fun wideAutoSelectDoesNotMarkRead() {
        // Ревью: на широком экране первая заявка открывалась сама и уходила на сервер как «прочитанная»
        val backend = FakeBackend(routes)
        val container = AppContainer(FakeSessionStorage().apply { saveLocale("ru") }, backend.engine)
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalLayoutClass provides layoutClassFor(1440f)) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { BookingTab(agency) } }
            }
        }
        var t = 0L
        repeat(120) { scene.render(t); t += 50_000_000L; Thread.sleep(25) }
        scene.close()
        assertTrue(backend.requests.any { it.url.encodedPath.startsWith("/api/booking/") }, "лента загружалась")
        assertTrue(backend.requests.none { it.url.encodedPath.endsWith("/read") }, "без тапа — без «прочитано»")
    }
}
