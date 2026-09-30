package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.EmailToggle
import com.djmetry.api.endpoints.SettingsApi
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.UserProfile
import com.djmetry.data.repository.*
import com.djmetry.ui.settings.SettingsPage
import com.djmetry.ui.settings.SettingsSection
import com.djmetry.ui.settings.sectionPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Настройки: формы запросов — как в бэкенде (djmetry-api/src/api/user.ts), ответы — реальные формы. */
class SettingsRepositoryTest {

    private val artist = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"),
        user = UserProfile(country = "ES", city = "Barcelona"))
    private val fan = MeResponse(isAuthed = true, user = UserProfile(country = "ES"))

    private val ok = HttpStatusCode.OK
    private val routes = mapOf(
        "GET /api/me/push-preferences" to (ok to """{"enabled":true,"types":{"release_radar":true,"pre_save":false}}"""),
        "PUT /api/me/push-preferences" to (ok to """{"enabled":true,"types":{"release_radar":true,"pre_save":true}}"""),
        "GET /api/me/notifications-settings" to (ok to """{"inAppEnabled":true,"in_app_enabled":true,"types":{"booking":false}}"""),
        "PUT /api/me/notifications-settings" to (ok to """{"inAppEnabled":false,"in_app_enabled":false,"types":{"booking":false}}"""),
        "GET /api/me/release-radar" to (ok to """{"releaseRadarEnabled":true,"releaseRadarFrequency":"weekly_digest"}"""),
        "PUT /api/me/release-radar" to (ok to """{"releaseRadarEnabled":true,"releaseRadarFrequency":"immediate"}"""),
        "GET /api/me/concert-alerts" to (ok to """{"concertAlertsEnabled":true,"concertAlertsFrequency":"immediate","concertAlertCountry":null,"concertAlertCity":null,"profileCountry":"ES","profileCity":"Barcelona","effectiveCountry":"ES","effectiveCity":"Barcelona","needsLocationHint":false}"""),
        "PUT /api/me/concert-alerts" to (ok to """{"concertAlertsEnabled":true,"concertAlertsFrequency":"weekly_digest","concertAlertCountry":"DE","concertAlertCity":"Berlin","effectiveCountry":"DE","effectiveCity":"Berlin","needsLocationHint":false}"""),
        "GET /api/me/smart-link-notifications" to (ok to """{"smartLinkNotificationsEnabled":true,"smart_link_notifications_enabled":true}"""),
        "PUT /api/me/smart-link-notifications" to (ok to """{"smartLinkNotificationsEnabled":false}"""),
        "GET /api/me/track-support-emails" to (ok to """{"trackSupportEmailsEnabled":true}"""),
        "GET /api/me/weekly-digest" to (ok to """{"weeklyDigestEnabled":true}"""),
        "GET /api/me/venues-digest" to (ok to """{"venuesDigestEnabled":false}"""),
        "GET /api/me/ranking-digest" to (ok to """{"rankingDigestEnabled":true}"""),
        "GET /api/me/talents-digest" to (ok to """{"talentsDigestEnabled":false}"""),
        "PATCH /api/me/settings/profile" to (ok to """{"success":true,"profile":{"country":"DE","city":"Berlin"}}"""),
        "PUT /api/me/settings/language" to (ok to """{"success":true,"language":"de"}"""),
        "POST /api/me/settings/music-genres" to (ok to """{"success":true,"genres":["techno"]}"""),
        "GET /api/location/cities" to (ok to """{"country":"ES","count":1,"total":19,"cities":[{"name":"Barcelona","population":1621537}]}"""),
    )

    private fun repo(b: FakeBackend) = SettingsRepository(SettingsApi(b.client()))
    private fun body(b: FakeBackend, method: String, path: String) = (b.request(method, path)!!.body as TextContent).text

    @Test
    fun artistLoadsAllSectionsIncludingArtistDigests() = runTest {
        val b = FakeBackend(routes)
        val s = repo(b).load(artist)
        assertTrue(s.verified)
        assertEquals(1, enabledCount(s.push!!.types, listOf("release_radar", "pre_save")), "pre_save выключен")
        assertEquals(false, s.inApp!!.types["booking"])
        assertEquals("weekly_digest", s.releaseRadar!!.releaseRadarFrequency)
        assertEquals("Barcelona", s.concert!!.effectiveCity)
        assertEquals(setOf(EmailToggle.SmartLink, EmailToggle.TrackSupport, EmailToggle.Weekly, EmailToggle.Venues, EmailToggle.Ranking, EmailToggle.Talents), s.toggles.keys)
        assertEquals(false, s.toggles[EmailToggle.Venues])
    }

    @Test
    fun fanDoesNotRequestArtistOnlyDigests() = runTest {
        val b = FakeBackend(routes)
        val s = repo(b).load(fan)
        assertFalse(s.verified)
        assertNull(b.request("GET", "/api/me/ranking-digest"), "дайджест рейтинга — только артистам")
        assertNull(b.request("GET", "/api/me/talents-digest"))
        assertFalse(EmailToggle.Ranking in s.toggles)
    }

    @Test
    fun oneBrokenSectionDoesNotBreakScreen() = runTest {
        val b = FakeBackend(routes - "GET /api/me/push-preferences" + ("GET /api/me/weekly-digest" to (HttpStatusCode.InternalServerError to "{}")))
        val s = repo(b).load(artist)
        assertNull(s.push, "push не загрузился — секция пустая")
        assertFalse(EmailToggle.Weekly in s.toggles)
        assertNotNull(s.concert, "остальное на месте")
    }

    @Test
    fun toggleSendsExactBackendField() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.setToggle(EmailToggle.SmartLink, false).getOrThrow()
        assertEquals("""{"smartLinkNotificationsEnabled":false}""", body(b, "PUT", "/api/me/smart-link-notifications"))
        assertEquals(false, r.state.value!!.toggles[EmailToggle.SmartLink])
    }

    @Test
    fun failedToggleRollsBack() = runTest {
        val b = FakeBackend(routes + ("PUT /api/me/weekly-digest" to (HttpStatusCode.InternalServerError to """{"error":"x"}""")))
        val r = repo(b); r.load(artist)
        assertTrue(r.setToggle(EmailToggle.Weekly, false).isFailure)
        assertEquals(true, r.state.value!!.toggles[EmailToggle.Weekly], "ошибка — вернули как было")
    }

    @Test
    fun pushTypeAndInAppPatchesAreMinimal() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.setPushType("pre_save", true).getOrThrow()
        assertEquals("""{"types":{"pre_save":true}}""", body(b, "PUT", "/api/me/push-preferences"))
        assertEquals(true, r.state.value!!.push!!.types["pre_save"])
        r.setInAppEnabled(false).getOrThrow()
        assertEquals("""{"inAppEnabled":false}""", body(b, "PUT", "/api/me/notifications-settings"))
        assertEquals(false, r.state.value!!.inApp!!.inAppEnabled)
    }

    @Test
    fun releaseRadarFrequencyOnly() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.setReleaseRadar(frequency = "immediate").getOrThrow()
        assertEquals("""{"releaseRadarFrequency":"immediate"}""", body(b, "PUT", "/api/me/release-radar"))
    }

    @Test
    fun concertSaveSendsAllFieldsEmptyMeansFromProfile() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.saveConcert(true, "weekly_digest", " DE ", "Berlin").getOrThrow()
        assertEquals("""{"concertAlertsEnabled":true,"concertAlertsFrequency":"weekly_digest","concertAlertCountry":"DE","concertAlertCity":"Berlin"}""", body(b, "PUT", "/api/me/concert-alerts"))
        assertEquals("Berlin", r.state.value!!.concert!!.effectiveCity)
        r.saveConcert(false, "immediate", "", "").getOrThrow()
        assertTrue(body(b, "PUT", "/api/me/concert-alerts").contains(""""concertAlertCountry":"""""), "пустая строка = брать из профиля")
    }

    @Test
    /** Пустое поле — `null` (очистить): пустая строка даты у бэкенда — 400 `invalid_birth_date`, и регион не сохранялся. */
    fun profileSaveUsesSettingsProfileAndClearsWithNull() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.saveProfile("DE", "Berlin", "", "").getOrThrow()
        assertEquals("""{"country":"DE","city":"Berlin","region":null,"birth_date":null}""", body(b, "PATCH", "/api/me/settings/profile"))
        assertEquals("Berlin", r.state.value!!.profile!!.city)
        assertNull(r.state.value!!.profile!!.birthDate, "пусто — очищено")
        assertEquals(2, b.requests.count { it.url.encodedPath == "/api/me/concert-alerts" }, "Concert Radar перечитан — локация из профиля")
    }

    @Test
    fun languageAndGenres() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(fan)
        r.saveLanguage("de").getOrThrow()
        assertEquals("""{"language":"de"}""", body(b, "PUT", "/api/me/settings/language"))
        r.saveGenres(listOf(" techno ", "house", "Techno", "edm", "deep   house", "dnb", "ambient")).getOrThrow()
        assertEquals("""{"genres":["techno","house","edm","deep house","dnb"]}""", body(b, "POST", "/api/me/settings/music-genres"), "trim, пробелы, дубли без учёта регистра, не больше 5")
    }

    @Test
    fun citiesSearchParams() = runTest {
        val b = FakeBackend(routes)
        val cities = repo(b).cities("ES", "Barc").getOrThrow()
        assertEquals("Barcelona", cities.single().name)
        val req = b.request("GET", "/api/location/cities")!!
        assertEquals("ES", req.url.parameters["country"]); assertEquals("Barc", req.url.parameters["q"])
    }

    @Test
    fun gatingAndHelpers() {
        assertTrue(showsGenres(false)); assertFalse(showsGenres(true))
        assertEquals(4, visibleToggles(false).size); assertEquals(6, visibleToggles(true).size)
        assertEquals(12, PUSH_TYPES.size)
        assertEquals(12, enabledCount(emptyMap(), PUSH_TYPES), "нет типа — включён")
        assertEquals(listOf(SettingsPage.Language), sectionPages(SettingsSection.Preferences, verified = true), "артисту — без жанров")
        assertEquals(listOf(SettingsPage.Language, SettingsPage.Genres), sectionPages(SettingsSection.Preferences, verified = false))
    }

    @Test
    fun birthDateValidation() {
        assertTrue(isValidBirthDate("", 2026), "пусто — очистить")
        assertTrue(isValidBirthDate("1990-05-17", 2026))
        assertTrue(isValidBirthDate("2000-02-29", 2026), "високосный")
        assertFalse(isValidBirthDate("2001-02-29", 2026))
        assertFalse(isValidBirthDate("1899-12-31", 2026))
        assertFalse(isValidBirthDate("2027-01-01", 2026))
        assertFalse(isValidBirthDate("17.05.1990", 2026))
        assertFalse(isValidBirthDate("1990-13-01", 2026))
    }

    /** Строгость ввода (спека): жанры из списка, ≤5, порядок = приоритет, дедуп без учёта регистра. */
    @Test
    fun genreRules() {
        assertEquals("deep house", normalizeGenre("  deep    house "))
        assertEquals(listOf("techno"), addGenre(emptyList(), " techno "))
        assertEquals(listOf("techno"), addGenre(listOf("techno"), "TECHNO"), "дубль без учёта регистра")
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals(five, addGenre(five, "f"), "шестой не добавляется")
        assertEquals(listOf("house", "techno"), addGenre(listOf("house"), "techno"), "новый — в конец (приоритет по порядку)")
        assertEquals(emptyList(), addGenre(emptyList(), "   "))
    }

    @Test
    fun countryFlags() {
        // Без эмодзи (docs/RULES.md): флаг — картинка, как на сайте
        assertEquals("https://flagcdn.com/w80/es.png", com.djmetry.ui.components.flagUrl("ES"))
        assertEquals("https://flagcdn.com/w80/us.png", com.djmetry.ui.components.flagUrl(" us "))
        assertNull(com.djmetry.ui.components.flagUrl("__other__")); assertNull(com.djmetry.ui.components.flagUrl("E")); assertNull(com.djmetry.ui.components.flagUrl(null))
    }

    /** Календарь отдаёт миллисекунды — на бэк уходит строго YYYY-MM-DD без времени и таймзоны. */
    @Test
    fun calendarDateRoundTrip() {
        val ms = com.djmetry.ui.settings.isoToMillis("1990-05-17")!!
        assertEquals("1990-05-17", com.djmetry.ui.settings.millisToIso(ms))
        assertEquals("1900-01-01", com.djmetry.ui.settings.millisToIso(com.djmetry.ui.settings.isoToMillis("1900-01-01")!!))
        assertNull(com.djmetry.ui.settings.isoToMillis("17.05.1990"))
    }
}
