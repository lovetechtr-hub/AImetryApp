package com.djmetry.ui

import com.djmetry.ui.settings.releaseFrequency
import kotlin.test.Test
import kotlin.test.assertEquals

/** Частота Release Radar: дефолт бэкенда — сводка раз в неделю; «сразу» — только если выбрано явно. */
class ReleaseRadarUiTest {
    @Test fun immediateStays() = assertEquals("immediate", releaseFrequency("immediate"))
    @Test fun weeklyStays() = assertEquals("weekly_digest", releaseFrequency("weekly_digest"))
    @Test fun missingIsWeekly() = assertEquals("weekly_digest", releaseFrequency(null))
    @Test fun unknownIsWeekly() = assertEquals("weekly_digest", releaseFrequency("daily"))
}
