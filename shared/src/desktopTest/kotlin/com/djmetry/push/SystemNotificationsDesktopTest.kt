package com.djmetry.push

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Десктоп: системных пушей нет — страница настроек показывает подсказку, а не плашку «выключено в телефоне». */
class SystemNotificationsDesktopTest {
    @Test fun desktopIsUnsupported() = runTest { assertEquals(SystemNotifications.Unsupported, systemNotificationsState()) }
}
