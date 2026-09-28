package com.djmetry.ui

import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.EventOffer
import com.djmetry.api.models.EventVenue
import com.djmetry.api.models.SocialMedia
import com.djmetry.data.repository.DJMagEntry
import com.djmetry.ui.artist.*
import kotlin.test.*

class ArtistFormatTest {

    @Test
    fun compactCounts() {
        assertEquals("15.4M", compactCount(15_384_355))
        assertEquals("7.17B", compactCount(7_169_896_963))
        assertEquals("2B", compactCount(2_000_000_000))
        assertEquals("20B", compactCount(20_000_000_000), "нули в целой части не срезаем")
        assertEquals("12.3K", compactCount(12_345))
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("999", compactCount(999))
        assertEquals("0", compactCount(0))
    }

    @Test
    fun durations() {
        assertEquals("3:18", trackDuration(198_815))
        assertEquals("0:05", trackDuration(5_400))
        assertEquals("12:00", trackDuration(720_000))
    }

    @Test
    fun eventDates() {
        assertEquals(EventDay(16, 10), eventDay("2026-10-16T20:00:00"))
        assertEquals(EventDay(6, 11), eventDay("2026-11-06"))
        assertNull(eventDay("soon"))
        assertNull(eventDay("2026-13-01T00:00:00"))
        val ru = "янв фев мар апр мая июн июл авг сен окт ноя дек"
        assertEquals("окт", monthLabel(ru, 10))
        assertEquals("", monthLabel(ru, 13))
    }

    @Test
    fun ticketsPreferOfferThenEventPage() {
        val e = ArtistEvent("1", "2026-10-16T20:00:00", url = "https://bit/e/1", venue = EventVenue("Palacio", "Mexico City", country = "Mexico"))
        assertEquals("https://tix/1", ticketUrl(e.copy(offers = listOf(EventOffer(url = ""), EventOffer(url = "https://tix/1")))))
        assertEquals("https://bit/e/1", ticketUrl(e))
        assertNull(ticketUrl(e.copy(url = null)))
        assertEquals("Mexico City, Mexico", eventPlace(e))
        assertEquals("", eventPlace(e.copy(venue = null)))
    }

    @Test
    fun shareLinkAndDjMagLabel() {
        val d = ArtistDetailsResponse(spotifyArtistId = "60d", name = "Martin Garrix")
        assertEquals("https://djmetry.com/artist/60d", shareUrl(d))
        assertEquals("https://djmetry.com/artist/martin-garrix", shareUrl(d.copy(canonicalUrl = "https://djmetry.com/artist/martin-garrix")))
        assertEquals("https://djmetry.com/artist/60d", shareUrl(d.copy(canonicalUrl = "/artist/martin-garrix")), "относительный canonical не годится для шеринга")
        assertEquals("#2 · 2025", djMagLabel(DJMagEntry(2, 2025, 1)))
        assertEquals("#17", djMagLabel(DJMagEntry(17, null, null)))
    }

    @Test
    fun socialsFromHandlesAndUrls() {
        val links = socialLinks(SocialMedia(
            instagram = "@martingarrix", tiktok = "martingarrix", twitter = "https://twitter.com/MartinGarrix/",
            soundcloud = "martingarrix", facebook = " ", appleMusicUrl = "https://music.apple.com/us/artist/430932944", beatportUrl = "beatport",
        ))
        assertEquals(listOf(SocialKind.Instagram, SocialKind.TikTok, SocialKind.X, SocialKind.SoundCloud, SocialKind.AppleMusic), links.map { it.kind })
        assertEquals(SocialLink(SocialKind.Instagram, "@martingarrix", "https://instagram.com/martingarrix"), links[0])
        assertEquals("https://www.tiktok.com/@martingarrix", links[1].url)
        assertEquals(SocialLink(SocialKind.X, "@MartinGarrix", "https://twitter.com/MartinGarrix/"), links[2])
        assertEquals("martingarrix", links[3].label)
        assertEquals("Apple Music", links[4].label)
        assertTrue(socialLinks(null).isEmpty())
    }

    /** Знаки соцсетей взяты из SVG сайта: у каждой соцсети карточки есть знак, пути разбираются. */
    @Test
    fun everySocialHasBrandIcon() {
        SocialKind.values().forEach { kind ->
            assertEquals(24f, kind.icon.vector.viewportWidth, kind.name)
        }
        com.djmetry.ui.components.SocialIcon.values().forEach { icon ->
            assertTrue(icon.vector.root.iterator().hasNext(), "${icon.name}: пустой путь")
        }
    }

    @Test
    fun shortNameStaysBigOnOneLine() {
        assertEquals(NameFit(36f, 1), fitArtistName(36f, 20f) { _, _ -> true })
    }

    @Test
    fun nameShrinksOnOneLineButNotBelowSeventyPercent() {
        // помещается в одну строку только с 28 sp
        assertEquals(NameFit(28f, 1), fitArtistName(36f, 20f) { sp, lines -> lines == 1 && sp <= 28f })
        // в одну строку нужно 22 sp (< 70% от 36) — лучше две строки крупнее
        assertEquals(NameFit(32f, 2), fitArtistName(36f, 20f) { sp, lines -> if (lines == 1) sp <= 22f else sp <= 32f })
    }

    @Test
    fun nothingFitsFallsBackToMinimumTwoLines() {
        assertEquals(NameFit(20f, 2), fitArtistName(36f, 20f) { _, _ -> false })
    }
}
