package com.djmetry.ui

import com.djmetry.api.models.FollowedArtist
import com.djmetry.api.models.TrendInfo
import com.djmetry.data.repository.FollowSort
import com.djmetry.data.repository.sortFollows
import com.djmetry.ui.screens.hasTrendMetrics
import com.djmetry.ui.screens.signedTrend
import kotlin.test.*

class TrendAndFollowsTest {
    @Test
    fun trendDeltaHasExplicitSignAndOneDecimal() {
        assertEquals("+6.5", signedTrend(6.5))
        assertEquals("−34.9", signedTrend(-34.94))
        assertEquals("+1.0", signedTrend(1.0))
        assertEquals("0.0", signedTrend(0.02))
    }

    @Test
    fun trendMetricsShownForFallingArtistsToo() {
        assertTrue(hasTrendMetrics(TrendInfo(score24h = -34.9, score7d = -34.9, growthRate = -34.9)))
        assertTrue(hasTrendMetrics(TrendInfo(growthRate = 0.0)))
        assertFalse(hasTrendMetrics(TrendInfo(volatility = 17.4)))
        assertFalse(hasTrendMetrics(null))
    }

    private val list = listOf(
        FollowedArtist("a", "kygo", followers = 9_700_000),
        FollowedArtist("b", "ATB", followers = 1_900_000),
        FollowedArtist("c", "Chris Stassy", followers = null),
    )

    @Test fun recentKeepsBackendOrder() = assertEquals(listOf("a", "b", "c"), sortFollows(list, FollowSort.Recent).map { it.spotifyArtistId })
    @Test fun byNameIgnoresCase() = assertEquals(listOf("b", "c", "a"), sortFollows(list, FollowSort.Name).map { it.spotifyArtistId })
    @Test fun popularByFollowersUnknownLast() = assertEquals(listOf("a", "b", "c"), sortFollows(list, FollowSort.Popular).map { it.spotifyArtistId })

    @Test
    fun trendLinePartsColoredBySign() {
        val parts = com.djmetry.ui.screens.trendParts(TrendInfo(score24h = -34.8, score7d = 0.01, growthRate = 6.5), "24ч", "7д", "рост")
        assertEquals(listOf("24ч", "7д", "рост"), parts.map { it.label })
        assertEquals(listOf(-1, 0, 1), parts.map { it.sign })
        assertEquals("+6.5%", parts.last().value)
    }

    @Test
    fun trendLineSkipsMissingValues() =
        assertEquals(listOf("7д"), com.djmetry.ui.screens.trendParts(TrendInfo(score7d = 1.2), "24ч", "7д", "рост").map { it.label })

    @Test
    fun followingCountShowsLimitWhenKnown() {
        assertEquals("24 / 250", com.djmetry.ui.profile.followingCountLabel(24, 250))
        assertEquals("24", com.djmetry.ui.profile.followingCountLabel(24, null))
        assertEquals("0", com.djmetry.ui.profile.followingCountLabel(0, 0))
    }

    @Test
    fun tourPlaneSitsInOneStopAtATime() {
        assertEquals(0.5f, com.djmetry.ui.djmap.planeInStop(0f, 0))
        assertNull(com.djmetry.ui.djmap.planeInStop(0f, 1))
        assertEquals(0.75f, com.djmetry.ui.djmap.planeInStop(1.25f, 1))
        assertEquals(0.0f, com.djmetry.ui.djmap.planeInStop(1.5f, 2), "на стыке — в следующей ячейке")
        assertNull(com.djmetry.ui.djmap.planeInStop(1.5f, 1))
    }

    @Test
    fun cityPulsesOnlyWhenPlaneIsNear() {
        assertEquals(1f, com.djmetry.ui.djmap.stopPulse(3f, 3))
        assertEquals(0f, com.djmetry.ui.djmap.stopPulse(3.5f, 3))
        assertTrue(com.djmetry.ui.djmap.stopPulse(3.1f, 3) in 0.6f..0.8f)
    }
}
