package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.endpoints.SocialField
import com.djmetry.api.models.ArtistVerification
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.PickedFile
import com.djmetry.data.repository.*
import com.djmetry.i18n.Strings
import com.djmetry.ui.editor.editorErrorKey
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Редактор артиста (спека §14): формы запросов сверены с djmetry-api/src/api/user.ts и booking. */
class ArtistEditorRepositoryTest {
    private val id = "5QnualJTEdYTH1bUfyziGn"
    private val artist = MeResponse(isAuthed = true, artistVerification = ArtistVerification(isVerified = true, verifiedSpotifyArtistId = id))
    private val fan = MeResponse(isAuthed = true)
    private val ok = HttpStatusCode.OK
    private fun track(n: Int) = """{"id":"t${n.toString().padStart(21, '0')}","spotifyTrackId":"t${n.toString().padStart(21, '0')}","name":"Track $n","albumImageUrl":"x"}"""
    private val three = """{"tracks":[${track(1)},${track(2)},${track(3)}]}"""
    private val routes = mapOf(
        "GET /api/artists/spotify/$id" to (ok to """{"spotifyArtistId":"$id","name":"LovetechMusic","genres":["melodic house"],"country":"ES","city":"Barcelona",
            "socialMedia":{"instagram":"lovetech","soundcloud":"https://soundcloud.com/lovetech"},"youtube":{"url":"https://www.youtube.com/@lovetech"}}"""),
        "GET /api/me/artist/tracks" to (ok to three),
        "GET /api/artists/spotify/$id/tracks" to (ok to """{"tracks":[{"spotifyTrackId":"top1","name":"Top"}]}"""),
        "GET /api/booking/artists/$id/rider" to (HttpStatusCode.NotFound to """{"error":"not_found"}"""),
        "GET /api/booking/artists/$id/press-kit" to (ok to """{"press_kit_url":"https://s/p.pdf","press_kit_download_url":"https://s/p.pdf?dl=1"}"""),
        "POST /api/me/artist/socials" to (ok to """{"success":true}"""),
        "POST /api/me/artist/genres" to (ok to """{"success":true}"""),
        "POST /api/me/artist/update-location" to (ok to """{"success":true}"""),
        "POST /api/me/artist/tracks" to (ok to """{"success":true,"tracks":[${track(1)},${track(2)},${track(3)},${track(4)}]}"""),
        "PUT /api/me/artist/tracks/reorder" to (ok to """{"success":true,"tracks":[]}"""),
        "PUT /api/booking/artists/$id/rider" to (ok to """{"rider_url":"https://s/r.pdf","rider_download_url":"https://s/r.pdf?dl=1"}"""),
    )
    private fun repo(b: FakeBackend) = b.client().let { ArtistEditorRepository(ArtistEditorApi(it), ArtistApi(it)) }
    private fun body(b: FakeBackend, m: String, p: String) = (b.request(m, p)!!.body as TextContent).text
    private val pdf = PickedFile("rider.pdf", "application/pdf", "%PDF-1.7 test".encodeToByteArray())

    @Test
    fun onlyVerifiedArtistCanOpen() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b).load(fan)
        assertEquals("artist_verification_required", editorErrorCode(r.exceptionOrNull()!!))
        assertTrue(b.requests.isEmpty(), "фанату — без запросов")
    }

    @Test
    fun loadsEverythingRiderMissingIsNotError() = runTest {
        val s = repo(FakeBackend(routes)).load(artist).getOrThrow()
        assertEquals(3, s.curated.size); assertEquals("Top", s.topTracks.single().name)
        assertNull(s.rider, "404 — «не загружен», не ошибка")
        assertEquals("https://s/p.pdf", s.pressKit?.url)
        assertEquals("https://www.youtube.com/@lovetech", socialsFrom(s.details.socialMedia, s.details.youtube?.url)[SocialField.YouTube])
    }

    @Test
    fun socialsSendAllNineFieldsEmptyAsExplicitNull() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.saveSocials(mapOf(SocialField.Instagram to " @lovetech ", SocialField.Beatport to "https://www.beatport.com/artist/x/1")).getOrThrow()
        val sent = body(b, "POST", "/api/me/artist/socials")
        assertEquals("""{"instagram":"@lovetech","facebook":null,"tiktok":null,"twitter":null,"soundcloud":null,"youtube":null,"appleMusicUrl":null,"beatportUrl":"https://www.beatport.com/artist/x/1","telegram":null}""", sent,
            "бэк меняет только пришедшие поля — очищенное шлём явным null")
    }

    @Test
    fun invalidSocialNotSent() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        assertTrue(r.saveSocials(mapOf(SocialField.Facebook to "https://instagram.com/lovetech")).isFailure)
        assertNull(b.request("POST", "/api/me/artist/socials"))
    }

    @Test
    fun socialRules() {
        assertEquals(emptySet(), validateSocials(mapOf(
            SocialField.Instagram to "https://www.instagram.com/x", SocialField.Twitter to "https://x.com/x", SocialField.YouTube to "https://youtu.be/abc",
            SocialField.TikTok to "@x", SocialField.Telegram to "https://t.me/x", SocialField.AppleMusic to "https://music.apple.com/us/artist/1", SocialField.SoundCloud to "",
        )))
        assertEquals(setOf(SocialField.Facebook), validateSocials(mapOf(SocialField.Facebook to "https://instagram.com/x")))
        assertEquals(setOf(SocialField.AppleMusic, SocialField.Beatport), validateSocials(mapOf(SocialField.AppleMusic to "lovetech", SocialField.Beatport to "lovetech")), "Apple Music и Beatport — только полная ссылка")
        assertEquals(setOf(SocialField.Instagram), validateSocials(mapOf(SocialField.Instagram to "https://instagram.com.evil.io/x")))
    }

    @Test
    fun trackLinkParsingIsStrict() {
        val tid = "4uLU6hMCjMI75M1A2tKUQC"
        assertEquals(TrackLink.Ok(tid), parseSpotifyTrack("https://open.spotify.com/track/$tid?si=abc"))
        assertEquals(TrackLink.Ok(tid), parseSpotifyTrack("https://open.spotify.com/intl-ru/track/$tid"))
        assertEquals(TrackLink.Ok(tid), parseSpotifyTrack("spotify:track:$tid"))
        assertEquals(TrackLink.Album, parseSpotifyTrack("https://open.spotify.com/album/$tid"))
        assertEquals(TrackLink.Artist, parseSpotifyTrack("https://open.spotify.com/artist/$tid"))
        assertEquals(TrackLink.Playlist, parseSpotifyTrack("https://open.spotify.com/playlist/$tid"))
        assertEquals(TrackLink.Invalid, parseSpotifyTrack("https://soundcloud.com/x/y"))
        assertEquals(TrackLink.Invalid, parseSpotifyTrack("https://open.spotify.com/track/short"))
    }

    @Test
    fun addTrackNormalizesUrlAndRespectsLimitAndDuplicates() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        val tid = "4uLU6hMCjMI75M1A2tKUQC"
        r.addTrack("spotify:track:$tid").getOrThrow()
        assertEquals("""{"trackUrl":"https://open.spotify.com/track/$tid"}""", body(b, "POST", "/api/me/artist/tracks"))
        assertEquals(4, r.state.value!!.curated.size)
        assertEquals("track_duplicate", editorErrorCode(r.addTrack("https://open.spotify.com/track/${"t" + "1".padStart(21, '0')}").exceptionOrNull()!!))
        assertEquals("track_is_album", editorErrorCode(r.addTrack("https://open.spotify.com/album/$tid").exceptionOrNull()!!))
    }

    @Test
    fun fiveTracksMax() = runTest {
        val five = """{"tracks":[${(1..5).joinToString(",") { track(it) }}]}"""
        val b = FakeBackend(routes + ("GET /api/me/artist/tracks" to (ok to five)))
        val r = repo(b); r.load(artist)
        assertEquals("track_limit", editorErrorCode(r.addTrack("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC").exceptionOrNull()!!))
        assertNull(b.request("POST", "/api/me/artist/tracks"), "шестой не отправляется")
    }

    @Test
    fun backendTrackErrorsAreMapped() = runTest {
        val b = FakeBackend(routes + ("POST /api/me/artist/tracks" to (HttpStatusCode.Forbidden to """{"error":"track_not_yours"}""")))
        val r = repo(b); r.load(artist)
        val code = editorErrorCode(r.addTrack("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC").exceptionOrNull()!!)
        assertEquals("track_not_yours", code)
        assertEquals(Strings.ED_ERR_NOT_YOURS, editorErrorKey(code))
    }

    @Test
    fun reorderSendsIdsAndRollsBackOnError() = runTest {
        val t1 = "t" + "1".padStart(21, '0'); val t2 = "t" + "2".padStart(21, '0'); val t3 = "t" + "3".padStart(21, '0')
        assertEquals(listOf(t2, t1, t3), moveTrack(listOf(t1, t2, t3), t2, -1))
        assertEquals(listOf(t1, t2, t3), moveTrack(listOf(t1, t2, t3), t1, -1), "за краем — без изменений")
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.moveTrack(t3, -1).getOrThrow()
        assertEquals("""{"trackIds":["$t1","$t3","$t2"],"track_ids":["$t1","$t3","$t2"]}""", body(b, "PUT", "/api/me/artist/tracks/reorder"))
        val bad = FakeBackend(routes + ("PUT /api/me/artist/tracks/reorder" to (HttpStatusCode.InternalServerError to "{}")))
        val r2 = repo(bad); r2.load(artist)
        assertTrue(r2.moveTrack(t1, 1).isFailure)
        assertEquals(listOf(t1, t2, t3), r2.state.value!!.curated.mapNotNull { it.spotifyTrackId }, "ошибка — порядок вернулся")
    }

    @Test
    fun locationCountryRequiredCityClearedWithNull() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        assertEquals("country_required", editorErrorCode(r.saveLocation("", "Berlin", "").exceptionOrNull()!!))
        assertNull(b.request("POST", "/api/me/artist/update-location"))
        r.saveLocation("de", "", " ").getOrThrow()
        assertEquals("""{"country":"DE","city":null,"region":null}""", body(b, "POST", "/api/me/artist/update-location"), "пустое — явный null (пустая строка не проходит min(1))")
    }

    @Test
    fun genresDedupedMaxFive() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        r.saveGenres(listOf("techno", "Techno", "house", "  deep   house", "edm", "trance", "dnb")).getOrThrow()
        assertEquals("""{"genres":["techno","house","deep house","edm","trance"]}""", body(b, "POST", "/api/me/artist/genres"))
    }

    @Test
    fun pdfRules() {
        assertNull(validatePdf(pdf))
        assertEquals(PdfProblem.NotPdf, validatePdf(PickedFile("rider.pdf", "application/pdf", "PK zip".encodeToByteArray())), "подмена расширения")
        assertEquals(PdfProblem.NotPdf, validatePdf(PickedFile("rider.docx", null, "%PDF".encodeToByteArray())))
        assertEquals(PdfProblem.TooBig, validatePdf(PickedFile("big.pdf", "application/pdf", ByteArray(MAX_PDF_BYTES + 1).also { "%PDF".encodeToByteArray().copyInto(it) })))
        assertEquals(PdfProblem.Empty, validatePdf(PickedFile("e.pdf", "application/pdf", ByteArray(0))))
    }

    @Test
    fun uploadIsMultipartPutAndInvalidNotSent() = runTest {
        val b = FakeBackend(routes)
        val r = repo(b); r.load(artist)
        assertTrue(r.uploadDoc(BookingDoc.Rider, PickedFile("x.pdf", null, "nope".encodeToByteArray())).isFailure)
        assertNull(b.request("PUT", "/api/booking/artists/$id/rider"))
        r.uploadDoc(BookingDoc.Rider, pdf).getOrThrow()
        val req = b.request("PUT", "/api/booking/artists/$id/rider")!!
        assertTrue(req.body.contentType.toString().startsWith("multipart/form-data"), "файл — multipart, поле file")
        assertEquals("https://s/r.pdf", r.state.value!!.rider?.url)
    }
}
