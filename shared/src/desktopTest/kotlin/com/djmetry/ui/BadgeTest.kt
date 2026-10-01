package com.djmetry.ui

import com.djmetry.EdtScene
import com.djmetry.ui.components.CAP_HEIGHT_RATIO
import com.djmetry.ui.components.badgeTextTop
import com.djmetry.ui.components.countLabel
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.djmetry.ui.components.CountBadge
import com.djmetry.ui.components.PillBadge
import org.jetbrains.skia.Bitmap
import kotlin.test.*

/**
 * Кривые бейджи на iPhone («2» проседала в круге, «DJMetry #1435» прижат к низу): центровали по рамке строки,
 * а у iOS-шрифта (SF) свой воздух сверху и снизу. Теперь центр — по базовой линии и высоте заглавных/цифр,
 * это не зависит от отступов строки конкретного шрифта.
 * ⚠️ На десктопе старый вариант тоже рисовался ровно — сдвиг был только на iOS; эти тесты сторожат новую
 * геометрию, а сам сдвиг проверяется глазами на iPhone.
 */
class BadgeTest {

    @Test
    fun capsAndDigitsSitExactlyInTheMiddle() {
        val h = 22; val cap = 12f * CAP_HEIGHT_RATIO; val baseline = 11 // замер TextMeasurer для 12 sp
        val top = badgeTextTop(h, cap, baseline)
        val capTop = top + baseline - cap
        val capBottom = top + baseline.toFloat()
        assertEquals(h / 2f, (capTop + capBottom) / 2f, 0.51f, "середина цифры = середина плашки")
    }

    /** Реальная высота заглавных системного шрифта (Skia, тот же движок, что на iPhone) — в пределах коэффициента. */
    @Test
    fun capHeightRatioMatchesSystemFont() {
        val typeface = FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD) ?: return
        val ratio = Font(typeface, 100f).metrics.capHeight / 100f
        assertTrue(ratio in (CAP_HEIGHT_RATIO - 0.06f)..(CAP_HEIGHT_RATIO + 0.06f), "капхайт шрифта $ratio далеко от $CAP_HEIGHT_RATIO")
    }

    @Test
    fun counts() {
        assertEquals("2", countLabel(2)); assertEquals("99", countLabel(99)); assertEquals("99+", countLabel(150)); assertEquals("0", countLabel(-3))
    }

    /** Рисуем бейдж настоящим движком (как на iPhone) и ищем строки с пикселями текста: их середина = середина плашки. */
    private fun inkCenterOffset(content: @androidx.compose.runtime.Composable () -> Unit): Float {
        val scene = EdtScene(120, 60, Density(2f)) { Box { content() } }
        try {
            val img = scene.render(0)
            val bmp = Bitmap.makeFromImage(img)
            // Плашка — чёрная, текст — белый, фон сцены прозрачный
            val badgeRows = (0 until bmp.height).filter { y -> (0 until bmp.width).any { x -> val c = bmp.getColor(x, y); (c ushr 24) > 200 } }
            val inkRows = (0 until bmp.height).filter { y -> (0 until bmp.width).any { x -> val c = bmp.getColor(x, y); (c and 0xFF) > 200 && (c ushr 24) > 200 } }
            val badgeMid = (badgeRows.first() + badgeRows.last()) / 2f
            val inkMid = (inkRows.first() + inkRows.last()) / 2f
            return inkMid - badgeMid
        } finally { scene.close() }
    }

    @Test
    fun renderedDigitIsCenteredInCircle() {
        val off = inkCenterOffset { CountBadge(2, fg = Color.White, bg = Color.Black) }
        assertTrue(kotlin.math.abs(off) <= 1.5f, "цифра смещена на $off px от центра круга")
    }

    @Test
    fun renderedPillTextIsCentered() {
        val off = inkCenterOffset { PillBadge("DJMETRY #1435", Color.White, Color.Black) }
        assertTrue(kotlin.math.abs(off) <= 1.5f, "подпись смещена на $off px от центра плашки")
    }
}
