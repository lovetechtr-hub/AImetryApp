package com.djmetry.i18n

import com.djmetry.FakeSessionStorage
import kotlin.test.Test
import kotlin.test.assertEquals

/** Подстановка аргументов в строки (раньше первый аргумент заменял все `%s`: «3 из 3 типов»). */
class FormatTemplateTest {
    @Test fun plainPlaceholdersGoInOrder() = assertEquals("3 из 7 типов", formatTemplate("%s из %s типов", 3, 7))
    @Test fun positionalByNumber() = assertEquals("7 / 3", formatTemplate("%2\$s / %1\$s", 3, 7))
    @Test fun numbersAndPercent() = assertEquals("5 шт · 10%", formatTemplate("%d шт · 10%%", 5))
    @Test fun missingArgumentStaysVisible() = assertEquals("1 и %s", formatTemplate("%s и %s", 1))
    @Test fun plainPercentUntouched() = assertEquals("0–99%.", formatTemplate("0–99%.", 1))

    @Test
    fun pushSummaryInEveryLanguageShowsBothNumbers() {
        val m = LocalizationManager(FakeSessionStorage())
        Locale.entries.forEach { l ->
            m.setLocale(l)
            val s = m.getString(Strings.SET_PUSH_SUMMARY, 3, 7)
            assertEquals(true, s.contains("3") && s.contains("7"), "$l: $s")
        }
    }
}
