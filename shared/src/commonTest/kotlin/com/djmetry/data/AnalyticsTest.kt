package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.AnalyticsApi
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.BreakdownRow
import com.djmetry.api.models.MeResponse
import com.djmetry.data.analytics.*
import com.djmetry.data.repository.AnalyticsBlock
import com.djmetry.data.repository.AnalyticsBlockedException
import com.djmetry.data.repository.AnalyticsRepository
import com.djmetry.data.repository.availableSources
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/** Аналитика (спека §15). Формы ответов — djmetry-api/src/api/types, распределение — musicAnalyticsActivitySeries.ts сайта. */
class AnalyticsTest {
    private val ok = HttpStatusCode.OK
    private val network = """{"range":{"preset":"7d","from":"2026-09-22","to":"2026-09-29"},
        "totals":{"total_page_views":1250,"anonymous_page_views":900,"total_click_events":700,"total_clicks":310,"djmetry_logged_page_views":350,
          "verified_artist_page_views":120,"booking_company_page_views":30,"other_logged_page_views":200},
        "by_segment":[{"segment":"anonymous","unique_viewers":600,"page_views":900},{"segment":"logged_in","unique_viewers":90,"page_views":190},
          {"segment":"verified_artist","unique_viewers":40,"page_views":120},{"segment":"booking_company","unique_viewers":10,"page_views":30},{"segment":"admin","unique_viewers":1,"page_views":10}],
        "click_breakdown":[{"content_type":"spotify","event_type":"outbound_click","count":150},{"content_type":null,"event_type":"social_click","count":40}],
        "top_viewers":[{"viewer_user_id":"u9","segment":"booking_company","display_label":"Agency","booking_company_slug":"agency","page_views":7,"last_seen_at":"2026-09-28 10:00:00"}]}"""
    private val breakdown = """{"range":{"preset":"7d","from":"2026-09-22","to":"2026-09-29"},
        "countries":[{"country_code":"DE","visits":500,"clicks":120,"ctr":24.0,"ctr_display_label":"24%","ctrDisplayLabel":"24%"},{"country_code":"US","visits":125,"clicks":10,"ctr":8.0},{"country_code":"XX","visits":3,"clicks":0,"ctr":0}],
        "cities":[{"city":"Berlin","country_code":"DE","visits":300,"clicks":80,"ctr":26.67}],
        "referrals":[{"referral":"instagram.com","visits":400,"clicks":90,"ctr":22.5}],
        "devices":[{"name":"mobile","visits":900,"clicks":0,"ctr":0},{"name":"desktop","visits":300,"clicks":0,"ctr":0},{"name":"unknown","visits":50,"clicks":0,"ctr":0}],
        "browsers":[],"os":[],"secret_link_leads":{"total":12,"event_type":"email_collected","target_type":"secret_link"}}"""
    private val routes = mapOf(
        "GET /api/me/music-page/analytics/bio-network" to (ok to network),
        "GET /api/me/music-page/analytics/breakdown" to (ok to breakdown),
        "GET /api/me/artist-catalog/analytics/network" to (HttpStatusCode.Forbidden to """{"error":"catalog_analytics_unavailable","message":"x"}"""),
        "GET /api/me/music-page/analytics/geo-options" to (ok to """{"countries":[{"code":"DE","name":"Germany"}],"cities":[{"country_code":"DE","city":"Berlin","label":"Berlin"}],"range":{"preset":"7d","from":"a","to":"b"}}"""),
    )
    private fun repo(b: FakeBackend) = AnalyticsRepository(AnalyticsApi(b.client()))
    private fun params(b: FakeBackend, path: String) = b.request("GET", path)!!.url.parameters

    @Test
    fun distributeKeepsExactSumAndRisingShape() {
        assertEquals(listOf(0, 0, 0), distributeShaped(0, 3))
        assertEquals(emptyList(), distributeShaped(10, 0))
        for ((total, n) in listOf(1250 to 8, 7 to 30, 99_999 to 366, 1 to 1)) assertEquals(total, distributeShaped(total, n).sum(), "сумма $total/$n")
        val d = distributeShaped(1000, 10)
        assertTrue(d.first() < d.last(), "кривая слегка растёт, как на сайте")
        // Веса как у сайта 0.35+0.65·(i+1)/n, остаток — наибольшим дробным частям
        assertEquals(listOf(3, 3, 4), distributeShaped(10, 3))
        val small = distributeShaped(4, 8)
        assertEquals(4, small.sum()); assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), small, "малые числа — рост к концу, а не ступенька вниз (было 1,1,1,1,0,0,0,0)")
    }

    @Test
    fun activityDaysWeeksMonths() {
        val from = LocalDate(2026, 9, 22); val to = LocalDate(2026, 9, 29)
        val days = buildActivity(from, to, 1250, 310, 741, ActivityBucket.Day)
        assertEquals(8, days.size, "7d = 8 календарных дней включительно")
        assertEquals(1250, days.sumOf { it.visits }); assertEquals(310, days.sumOf { it.clicks }); assertEquals(741, days.sumOf { it.unique })
        val weeks = buildActivity(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), 3000, 0, 0, ActivityBucket.Week)
        assertEquals(listOf(1, 8, 15, 22, 29), weeks.map { it.start.day }, "блоки по 7 дней от начала, последний неполный")
        assertEquals(3000, weeks.sumOf { it.visits })
        val months = buildActivity(LocalDate(2026, 1, 15), LocalDate(2026, 9, 29), 900, 90, 9, ActivityBucket.Month)
        assertEquals(9, months.size); assertEquals(LocalDate(2026, 1, 1), months.first().start)
        assertEquals(emptyList(), buildActivity(to, from, 1, 1, 1, ActivityBucket.Day))
    }

    @Test
    fun boundsAndBucketDefaults() {
        val today = LocalDate(2026, 9, 29)
        assertEquals(LocalDate(2026, 9, 23) to today, periodBounds(null, null, today), "нет range — сегодня−6 … сегодня, как на сайте")
        assertEquals(LocalDate(2026, 1, 1) to today, periodBounds("2026-01-01", "2026-09-29T00:00:00Z", today))
        assertEquals(ActivityBucket.Day, defaultBucket(LocalDate(2026, 8, 30), today))
        assertEquals(ActivityBucket.Week, defaultBucket(LocalDate(2026, 3, 1), today))
        assertEquals(ActivityBucket.Month, defaultBucket(LocalDate(2000, 1, 1), today), "«Всё» — с 2000 года, только месяцы")
    }

    @Test
    fun queryParamsPresetCustomGeo() {
        assertEquals(listOf("range" to "7d"), AnalyticsQuery().params(), "по умолчанию 7 дней, как на сайте")
        val custom = AnalyticsQuery(AnalyticsPeriod.Custom(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1)), country = "de", city = " Berlin ")
        assertEquals(listOf("range" to "custom", "from_date" to "2026-01-01", "to_date" to "2026-02-01", "country_code" to "DE", "city" to "Berlin"), custom.params())
        assertEquals(listOf("range" to "custom", "from_date" to "2026-01-01", "to_date" to "2026-02-01"), custom.params(withGeo = false), "geo-options — только даты")
        assertEquals(listOf("range" to "30d"), AnalyticsQuery(AnalyticsPeriod.Preset(RangePreset.D30), city = "Berlin").params(), "город без страны не шлём")
        assertEquals(CustomRangeProblem.FromAfterTo, validateCustomRange(LocalDate(2026, 2, 2), LocalDate(2026, 2, 1)))
        assertEquals(CustomRangeProblem.TooLong, validateCustomRange(LocalDate(2024, 1, 1), LocalDate(2026, 1, 3)))
        assertNull(validateCustomRange(LocalDate(2024, 1, 1), LocalDate(2026, 1, 1)), "732 дня включительно — можно")
    }

    @Test
    fun loadsNetworkAndBreakdownWithSameQuery() = runTest {
        val b = FakeBackend(routes)
        val q = AnalyticsQuery(AnalyticsPeriod.Preset(RangePreset.D90), country = "DE")
        val r = repo(b).load(AnalyticsSource.Bio, q).getOrThrow()
        assertEquals("90d", params(b, "/api/me/music-page/analytics/bio-network")["range"])
        assertEquals("DE", params(b, "/api/me/music-page/analytics/breakdown")["country_code"])
        val k = kpis(r.network, r.breakdown)
        assertEquals(AnalyticsKpis(visits = 1250, clicks = 310, unique = 741, leads = 12, topCountry = "DE"), k)
        assertEquals(24.8, k.ctr!!, 0.001)
        assertEquals("agency", r.network.top_viewers.single().booking_company_slug)
        assertNull(r.network.top_viewers.single().linked_spotify_artist_id, "ключ может отсутствовать")
    }

    @Test
    fun cachedUntilRefresh() = runTest {
        val b = FakeBackend(routes)
        val repo = repo(b)
        repo.load(AnalyticsSource.Bio, AnalyticsQuery()); repo.load(AnalyticsSource.Bio, AnalyticsQuery())
        assertEquals(2, b.requests.size, "второй раз — из кэша")
        repo.load(AnalyticsSource.Bio, AnalyticsQuery(), refresh = true)
        assertEquals(4, b.requests.size)
    }

    @Test
    fun clearUserDataDropsCacheOfPreviousAccount() = runTest {
        val b = FakeBackend(routes)
        val repo = repo(b)
        repo.load(AnalyticsSource.Bio, AnalyticsQuery())
        repo.clearUserData()
        repo.load(AnalyticsSource.Bio, AnalyticsQuery())
        assertEquals(4, b.requests.size, "после выхода — снова в сеть, а не отчёт прошлого пользователя")
    }

    @Test
    fun clicksFallBackToAllEventsAndBreakdownIsOptional() = runTest {
        val noClicks = network.replace("\"total_clicks\":310,", "")
        val b = FakeBackend(routes + ("GET /api/me/music-page/analytics/bio-network" to (ok to noClicks)) +
            ("GET /api/me/music-page/analytics/breakdown" to (HttpStatusCode.InternalServerError to "{}")))
        val r = repo(b).load(AnalyticsSource.Bio, AnalyticsQuery()).getOrThrow()
        assertNull(r.breakdown, "упала разбивка — итоги всё равно показываем")
        assertEquals(700, kpis(r.network, r.breakdown).clicks)
        assertNull(kpis(r.network, r.breakdown).topCountry)
    }

    @Test
    fun forbiddenBecomesClearState() = runTest {
        val b = FakeBackend(routes + ("GET /api/me/music-page/analytics/bio-network" to (HttpStatusCode.Forbidden to """{"error":"no_music_page","message":"x"}""")))
        val e1 = repo(b).load(AnalyticsSource.Bio, AnalyticsQuery()).exceptionOrNull()
        assertEquals(AnalyticsBlock.NoMusicPage, (e1 as AnalyticsBlockedException).block)
        val e2 = repo(FakeBackend(routes)).load(AnalyticsSource.DJMetry, AnalyticsQuery()).exceptionOrNull()
        assertEquals(AnalyticsBlock.NotVerified, (e2 as AnalyticsBlockedException).block)
    }

    @Test
    fun geoOptionsSendOnlyDatesAndIgnoreGeoInCache() = runTest {
        val b = FakeBackend(routes)
        val repo = repo(b)
        repo.geoOptions(AnalyticsSource.Bio, AnalyticsQuery(country = "DE", city = "Berlin")).getOrThrow()
        assertNull(params(b, "/api/me/music-page/analytics/geo-options")["country_code"])
        repo.geoOptions(AnalyticsSource.Bio, AnalyticsQuery(country = "US"))
        assertEquals(1, b.requests.size, "смена страны не перезапрашивает варианты")
    }

    @Test
    fun catalogOnlyForVerifiedArtist() {
        assertEquals(listOf(AnalyticsSource.Bio), availableSources(MeResponse(isAuthed = true)))
        val artist = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = "a1"))
        assertEquals(listOf(AnalyticsSource.DJMetry, AnalyticsSource.Bio), availableSources(artist), "проверенному — сначала карточка, как на сайте")
    }

    @Test
    fun mapCountriesAndBubbles() {
        val rows = listOf(BreakdownRow(country_code = "de", visits = 400), BreakdownRow(country_code = "US", visits = 100), BreakdownRow(country_code = "DE", visits = 100),
            BreakdownRow(country_code = "ZZ", visits = 50), BreakdownRow(country_code = "FR", visits = 0))
        val m = mapCountries(rows)
        assertEquals(listOf("DE", "US"), m.map { it.iso }, "склеиваем регистр, без координат и без визитов — не на карту")
        assertEquals(1.0, m[0].share); assertEquals(0.2, m[1].share)
        val features = Json.parseToJsonElement(bubblesGeoJson(m)).jsonObject["features"]!!.jsonArray
        val de = features[0].jsonObject
        assertEquals("DE", de["properties"]!!.jsonObject["iso"]!!.jsonPrimitive.content)
        val coords = de["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }
        assertEquals(COUNTRY_CENTROIDS["DE"]!!.second, coords[0], "GeoJSON: сначала долгота")
        assertEquals(kotlin.math.sqrt(0.2), features[1].jsonObject["properties"]!!.jsonObject["k"]!!.jsonPrimitive.content.toDouble(), 1e-9, "площадь пузыря ∝ визитам")
        assertTrue(choroplethAlpha(1.0) > choroplethAlpha(0.1)); assertEquals(choroplethAlpha(5.0), choroplethAlpha(1.0))
    }

    @Test
    fun centroidsCoverWebCountries() {
        for (iso in listOf("DE", "US", "GB", "AZ", "MC", "BH", "HK", "RU", "UA", "BR", "JP", "AU")) assertNotNull(COUNTRY_CENTROIDS[iso], iso)
        assertTrue(COUNTRY_CENTROIDS.values.all { (lat, lon) -> lat in -90.0..90.0 && lon in -180.0..180.0 })
    }

    @Test
    fun sharesPutUnknownLastAndGroupRest() {
        val s = shares(listOf(BreakdownRow(name = "unknown", visits = 500), BreakdownRow(name = "mobile", visits = 300), BreakdownRow(name = "desktop", visits = 200)), limit = 2)
        assertEquals(listOf("mobile", "desktop", "other"), s.map { it.label })
        assertEquals(1.0, s.sumOf { it.fraction }, 1e-9)
        assertEquals(emptyList(), shares(emptyList()))
    }

    @Test
    fun mapStyleIsDarkLocalizedAndFree() {
        val st = Json.parseToJsonElement(analyticsMapStyle("ru")).jsonObject
        assertEquals(8, st["version"]!!.jsonPrimitive.content.toInt())
        assertTrue(st["sources"]!!.jsonObject["omt"]!!.jsonObject["url"]!!.jsonPrimitive.content.startsWith("https://tiles.openfreemap.org"))
        val layers = st["layers"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("land", "water", "borders", "country-labels", "city-labels"), layers)
        assertTrue(analyticsMapStyle("ru").contains("\"name:ru\""), "подписи на языке приложения")
    }
}
