package com.djmetry.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.djmetry.i18n.Locale
import com.djmetry.i18n.Strings
import com.djmetry.i18n.Translations
import kotlin.test.*

/** Заголовок и подзаголовок входа помещаются на самом узком телефоне (iPhone SE) на всех 12 языках. */
class LoginTextFitTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
    private val sePx = 375 - 2 * 22 // форма входа: ширина окна минус поля (22 dp)

    private fun fits(text: String, style: TextStyle, lines: Int) =
        !measurer.measure(AnnotatedString(text), style, maxLines = lines, constraints = Constraints(maxWidth = sePx)).hasVisualOverflow

    @Test
    fun titleAndSubtitleFitOnIphoneSeInEveryLanguage() {
        val bad = Locale.values().flatMap { locale ->
            val t = Translations.getTranslations(locale)
            listOfNotNull(
                "${locale.code}: заголовок".takeUnless { fits(t.getValue(Strings.LOGIN_HUB_TITLE), TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold), 2) },
                "${locale.code}: подзаголовок".takeUnless { fits(t.getValue(Strings.LOGIN_HEADLINE), TextStyle(fontSize = 17.sp, lineHeight = 23.sp), 4) },
            )
        }
        assertTrue(bad.isEmpty(), "Не помещается: $bad")
    }
}
