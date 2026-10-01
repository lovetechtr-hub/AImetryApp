package com.djmetry.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Высота заглавных букв и цифр относительно кегля у системных шрифтов (SF Pro ≈ 0.705, Roboto ≈ 0.711).
 * По ней центрируем текст в бейдже: у шрифта свой воздух сверху и снизу, и «по рамке строки» цифра проседает вниз.
 */
const val CAP_HEIGHT_RATIO = 0.71f

/** Высота плашки-бейджа — одна на всё приложение. */
val BADGE_HEIGHT: Dp = 26.dp

/**
 * Где поставить верх текста, чтобы заглавные и цифры оказались ровно посередине плашки высотой [heightPx]:
 * базовая линия — на [heightPx]/2 + капхайт/2.
 */
fun badgeTextTop(heightPx: Int, capPx: Float, baselinePx: Int): Int = (heightPx / 2f + capPx / 2f - baselinePx).roundToInt()

/** 0…99 как есть, больше — «99+». */
fun countLabel(count: Int): String = if (count > 99) "99+" else count.coerceAtLeast(0).toString()

/**
 * Бейдж с оптически центрированным текстом. Высота — не меньше [minHeight] и растёт с системным шрифтом,
 * ширина — текст + поля, но не меньше высоты (одна цифра — ровный круг).
 */
@Composable
private fun CenteredBadge(text: String, fg: Color, bg: Color, minHeight: Dp, padH: Dp, fontSize: TextUnit, modifier: Modifier) {
    Layout(
        content = { Text(text, style = TextStyle(color = fg, fontSize = fontSize, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) },
        modifier = modifier.clip(CircleShape).background(bg),
    ) { measurables, _ ->
        val p = measurables.first().measure(Constraints())
        val cap = fontSize.toPx() * CAP_HEIGHT_RATIO
        val h = max(minHeight.roundToPx(), (cap * 2.3f).roundToInt())
        val w = max(h, p.width + padH.roundToPx() * 2)
        val baseline = p[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: p.height
        layout(w, h) { p.place((w - p.width) / 2, badgeTextTop(h, cap, baseline)) }
    }
}

/**
 * Плашка-«таблетка» с подписью: «DJMetry #1435», «DJ Mag #2 · 2025», «OAC», место в карточке.
 * Фиксированная минимальная высота, полностью скруглённые края, текст оптически по центру.
 */
@Composable
fun PillBadge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp, height: Dp = BADGE_HEIGHT) =
    CenteredBadge(text, fg, bg, height, 11.dp, fontSize, modifier)

/** Круглый счётчик: «2», «14», «99+». Одна цифра — круг, больше — «таблетка» той же высоты. */
@Composable
fun CountBadge(count: Int, fg: Color, bg: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, fontSize: TextUnit = 12.sp) =
    CenteredBadge(countLabel(count), fg, bg, size, 6.dp, fontSize, modifier)
