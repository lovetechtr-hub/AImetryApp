package com.djmetry.ui

import com.djmetry.data.booking.RequestForm
import com.djmetry.data.booking.isFilled
import kotlin.test.*

/** Навигация: заполненная заявка не теряется случайным «Назад». */
class NavigationFixesTest {
    @Test
    fun filledRequestAsksBeforeClosing() {
        assertFalse(RequestForm(companyId = "c1", artistIds = setOf("a1")).isFilled(), "агентство и артист подставлены сами — закрываем без вопроса")
        assertTrue(RequestForm(message = "Привет").isFilled())
        assertTrue(RequestForm(date = "2026-12-31").isFilled())
        assertTrue(RequestForm(guests = "300").isFilled())
    }
}
