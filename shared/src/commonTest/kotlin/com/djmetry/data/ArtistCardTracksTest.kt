package com.djmetry.data

import com.djmetry.api.models.Track
import com.djmetry.data.repository.ArtistRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** «Музыка» на карточке артиста — как на сайте: выбранные в редакторе треки первыми, добор топом Spotify без повторов. */
class ArtistCardTracksTest {
    private fun t(id: String) = Track(spotifyTrackId = id, name = id)

    @Test
    fun curatedFirstThenTopWithoutDuplicates() {
        val curated = listOf(t("Triad"), t("Gladiator"), t("Elephant"))
        val top = listOf(t("Triad"), t("Elephant"), t("Julia"), t("Hello Robert"), t("Radio Edit"))
        assertEquals(listOf("Triad", "Gladiator", "Elephant", "Julia", "Hello Robert"), ArtistRepository.cardTracks(curated, top).map { it.name })
    }

    @Test
    fun fiveCuratedReplaceTop() {
        val curated = listOf("Triad", "Gladiator", "3363 & 1007", "Barcelona", "Elephant").map(::t)
        assertEquals(curated, ArtistRepository.cardTracks(curated, listOf(t("Julia"))), "5 выбранных — топ не нужен (реальный ответ прода для LovetechMusic)")
    }

    @Test
    fun noCuratedFallsBackToTop() {
        assertEquals(listOf("a", "b"), ArtistRepository.cardTracks(emptyList(), listOf(t("a"), t("b"))).map { it.name })
    }
}
