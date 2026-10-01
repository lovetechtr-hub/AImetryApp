package com.djmetry.api

import com.djmetry.files.safeFileName
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ревью, пакет 6: имена файлов с сервера и строки лога. */
class PlatformSafetyTest {
    @Test
    fun fileNameCannotLeaveFolder() {
        assertEquals("passwd", safeFileName("../../etc/passwd"))
        assertEquals("rider.pdf", safeFileName("C:\\x\\rider.pdf"))
        assertEquals("file", safeFileName("../"))
        assertEquals("hidden", safeFileName(".hidden"))
        assertEquals("ab.pdf", safeFileName("a\u0000b.pdf"))
        assertEquals(120, safeFileName("x".repeat(500)).length)
    }

    @Test
    fun logLinesDropQuery() {
        assertEquals("REQUEST: https://djmetry.com/api/booking/confirm-by-token/preview?…",
            stripQuery("REQUEST: https://djmetry.com/api/booking/confirm-by-token/preview?token=secret"))
        assertEquals("METHOD: GET https://djmetry.com/api/me", stripQuery("METHOD: GET https://djmetry.com/api/me"))
    }
}
