package com.djmetry.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.djmetry.ui.artist.NAME_MIN_SP
import com.djmetry.ui.artist.RING_TEXT_WIDTH
import com.djmetry.ui.artist.fitArtistName
import com.djmetry.ui.artist.nameFits
import com.djmetry.ui.components.fitTextSize
import com.djmetry.ui.components.textFits
import kotlin.test.*

/**
 * Реальные ошибки со скриншотов iPhone 15 Pro Max с крупным системным шрифтом: «20.» вместо «20.43», «подпи»,
 * «Менедж…», цифры Score на обводке, «LovetechMu…». Меряем настоящей вёрсткой (Skia) при fontScale 1.0 и 1.3.
 */
class BigFontFitTest {
    private fun measurer(fontScale: Float) = TextMeasurer(createFontFamilyResolver(), Density(1f, fontScale), LayoutDirection.Ltr)

    /** Подбирается кегль, который реально помещается, и он не меньше [min]. */
    private fun assertFits(text: String, style: TextStyle, widthDp: Int, min: Float, lines: Int = 1) {
        listOf(1f, 1.3f).forEach { scale ->
            val m = measurer(scale)
            val sp = fitTextSize(style.fontSize.value, min, 0.5f, textFits(m, text, style, widthDp, lines))
            assertTrue(textFits(m, text, style, widthDp, lines)(sp), "«$text» не помещается в $widthDp dp при шрифте ×$scale (кегль $sp)")
        }
    }

    // Профиль: колонок столько, сколько влезает (metricColumns), ширина плитки минус поля 24
    private fun metricWidth(screen: Int): Int {
        val inner = screen - 32f
        val cols = com.djmetry.ui.profile.metricColumns(inner, 4)
        return ((inner - 10f * (cols - 1)) / cols).toInt() - 24
    }

    @Test
    fun profileMetricsFitOnPhones() = listOf(375, 430).forEach { w ->
        assertFits("20.43", TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold), metricWidth(w), 12f)
        assertFits("#1435", TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold), metricWidth(w), 12f)
        assertFits("подписчики", TextStyle(fontSize = 12.sp), metricWidth(w), 8f)
    }

    @Test
    fun narrowPhonesGetTwoByTwoMetrics() {
        assertEquals(2, com.djmetry.ui.profile.metricColumns(375f - 32f, 4), "iPhone SE — 2×2")
        assertEquals(4, com.djmetry.ui.profile.metricColumns(430f - 32f, 4), "iPhone 15 Pro Max — 4 в ряд")
        assertEquals(2, com.djmetry.ui.profile.metricColumns(300f, 2))
    }

    @Test
    fun scoreNumberStaysInsideRing() {
        assertFits("46.91", TextStyle(fontSize = (88 * 0.22f).sp, fontWeight = FontWeight.Bold), (88 * RING_TEXT_WIDTH).toInt(), 10f)
        assertFits("100.00", TextStyle(fontSize = (96 * 0.22f).sp, fontWeight = FontWeight.Bold), (96 * RING_TEXT_WIDTH).toInt(), 10f)
    }

    @Test
    fun onboardingRoleLabelFits() {
        assertFits("Менеджер", TextStyle(fontSize = 11.sp), 76, 8f)
        assertFits("Organizzatore", TextStyle(fontSize = 11.sp), 76, 8f)
    }

    @Test
    fun longArtistNamesFitWithSealOnPhones() = listOf(1f, 1.3f).forEach { scale ->
        val m = measurer(scale)
        // Профиль: герой шириной экран − поля 32 − поля героя 36; карточка: экран − 36
        listOf("LovetechMusic" to 430 - 32 - 36, "The Chainsmokers" to 430 - 36, "Charlotte de Witte" to 375 - 36).forEach { (name, px) ->
            val fit = fitArtistName(28f, NAME_MIN_SP, fits = nameFits(m, name, px, verified = true))
            assertTrue(nameFits(m, name, px, verified = true)(fit.sizeSp, fit.maxLines), "«$name» с печатью не помещается в $px dp при ×$scale: $fit")
        }
    }

    /** Таблица рейтинга: «100» и «1000» в колонке места не рвутся (была ошибка «10 / 0» на iPhone). */
    @Test
    fun rankColumnFitsFourDigits() {
        assertFits("100", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), com.djmetry.ui.rating.RANK_COLUMN.value.toInt(), 10f)
        assertFits("1000", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), com.djmetry.ui.rating.RANK_COLUMN.value.toInt(), 10f)
    }

    /** Подиум на iPhone SE: боковая колонка ~95 dp, имя до двух строк. */
    @Test
    fun podiumNamesFitOnNarrowPhone() {
        val side = ((375 - 32 - 24 - 20) / 3.15f).toInt()
        listOf("Swedish House Mafia", "Charlotte de Witte", "Calvin Harris").forEach {
            assertFits(it, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp), side, 10f, lines = 2)
        }
    }

    /** Капсула рейтингов на iPhone SE: 4 ячейки по ~83 dp, короткая подпись итогов на всех 12 языках помещается. */
    @Test
    fun ratingCapsuleFitsAllLanguagesOnIphoneSe() {
        val cell = (375 - 32 - 8) / 4
        com.djmetry.i18n.Locale.values().forEach { loc ->
            val label = com.djmetry.i18n.Translations.getTranslations(loc).getValue(com.djmetry.i18n.Strings.RT_YEAR_SHORT)
            assertFits(label, TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold), cell - 8, 9f)
        }
    }

    /**
     * Шапка экрана свайпов на iPhone SE: «Открытия · Подписки · Карта» делят место поровну.
     * 375 − поля 36 − логотип 30 − поиск 48 − отступы 16 − рамка 8 = 237 dp → по 79, минус поля вкладки 8 = 71 dp.
     */
    @Test
    fun discoverTabsFitOnIphoneSe() = com.djmetry.i18n.Locale.entries.forEach { loc ->
        val t = com.djmetry.i18n.Translations.getTranslations(loc)
        listOf(com.djmetry.i18n.Strings.SEG_DISCOVER, com.djmetry.i18n.Strings.SEG_FOLLOWING, com.djmetry.i18n.Strings.MAP_TAB).forEach { key ->
            assertFits(t.getValue(key), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), 71, 9f)
        }
    }

    /** Предел системного шрифта в приложении не выше того, при котором проверена вёрстка (×1.3). */
    @Test
    fun appFontScaleCapIsCovered() = assertTrue(com.djmetry.MAX_FONT_SCALE in 1f..1.3f)
}
