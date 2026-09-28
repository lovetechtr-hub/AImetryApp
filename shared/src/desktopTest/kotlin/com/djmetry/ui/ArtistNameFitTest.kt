package com.djmetry.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.djmetry.ui.artist.NAME_MIN_SP
import com.djmetry.ui.artist.fitArtistName
import com.djmetry.ui.artist.nameFits
import kotlin.test.*

/** Настоящая вёрстка текста (Skia) на ширинах реальных устройств: имя не обрезается и не рвётся посреди слова. */
class ArtistNameFitTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    // Постер на iPad-портрете: колонка 320 dp − поля 36 − печать (36*0.7 + 8)
    private val tabletPx = 320 - 36 - 33

    @Test
    fun longNameFitsTabletPosterWithoutMidWordBreak() {
        val fit = fitArtistName(36f, NAME_MIN_SP, fits = nameFits(measurer, "Charlotte de Witte", tabletPx))
        assertTrue(nameFits(measurer, "Charlotte de Witte", tabletPx)(fit.sizeSp, fit.maxLines), "подобранный кегль действительно помещается: $fit")
        assertTrue(fit.sizeSp > NAME_MIN_SP, "не свалились в минимальный кегль: $fit")
    }

    @Test
    fun shortNameKeepsFullSizeOnOneLine() {
        assertEquals(1, fitArtistName(36f, NAME_MIN_SP, fits = nameFits(measurer, "Alok", tabletPx)).maxLines)
        assertEquals(36f, fitArtistName(36f, NAME_MIN_SP, fits = nameFits(measurer, "Alok", tabletPx)).sizeSp)
    }

    @Test
    fun veryLongSingleWordShrinksInsteadOfBreaking() {
        val name = "Swedishhousemafiaforever"
        val fit = fitArtistName(36f, NAME_MIN_SP, fits = nameFits(measurer, name, tabletPx))
        assertTrue(fit.sizeSp < 36f, "длинное слово уменьшает кегль: $fit")
    }
}
