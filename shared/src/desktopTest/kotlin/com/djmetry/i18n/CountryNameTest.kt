package com.djmetry.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Названия стран на языке интерфейса — не английские из справочника («Türkiye» → «Турция»). */
class CountryNameTest {
    @Test fun russian() = assertEquals("Турция", localizedCountryName("TR", "ru"))
    @Test fun lowerCaseIso() = assertEquals("Германия", localizedCountryName("de", "ru"))
    @Test fun german() = assertEquals("Türkei", localizedCountryName("TR", "de"))
    @Test fun regionalTag() = assertEquals("Turquia", localizedCountryName("TR", "pt-BR"))
    @Test fun unknownIsNull() = assertNull(localizedCountryName("ZZ", "ru"))
}
