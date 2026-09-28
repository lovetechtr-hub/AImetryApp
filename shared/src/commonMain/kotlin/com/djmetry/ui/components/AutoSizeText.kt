package com.djmetry.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Кегль, при котором текст помещается: от [maxSp] вниз шагом [step] до [minSp].
 * [fits] — помещается ли текст этим кеглем (меряет UI). Ничего не подошло — [minSp] (дальше многоточие).
 */
fun fitTextSize(maxSp: Float, minSp: Float, step: Float = 1f, fits: (sp: Float) -> Boolean): Float {
    var sp = maxSp
    while (sp >= minSp) {
        if (fits(sp)) return sp
        sp -= step
    }
    return minSp
}

/**
 * Помещается ли [text] кеглем `sp` в [maxLines] строк шириной [maxWidthPx]; самое длинное слово — целиком в строку
 * (иначе «подписчики» рвалось бы на «подпи-счики»). Учитывает системный размер шрифта через density измерителя.
 */
fun textFits(measurer: TextMeasurer, text: String, style: TextStyle, maxWidthPx: Int, maxLines: Int): (Float) -> Boolean = { sp ->
    val sized = scaled(style, sp)
    val whole = measurer.measure(AnnotatedString(text), sized, maxLines = maxLines, constraints = Constraints(maxWidth = maxWidthPx))
    val longest = text.split(' ', '\n').maxByOrNull { it.length }.orEmpty()
    !whole.hasVisualOverflow && measurer.measure(AnnotatedString(longest), sized, maxLines = 1).size.width <= maxWidthPx
}

/** Стиль с новым кеглем; межстрочный интервал в sp — пропорционально. */
private fun scaled(style: TextStyle, sp: Float): TextStyle {
    val base = style.fontSize.value
    val lh = style.lineHeight
    return style.copy(
        fontSize = sp.sp,
        lineHeight = if (lh.isSp && base > 0f) (lh.value * sp / base).sp else lh,
    )
}

/**
 * Текст, который всегда помещается в отведённую ширину: уменьшает кегль от `style.fontSize` до [minFontSize].
 * Нужен везде, где место фиксировано (плитки, кнопки, кольца, бейджи): на телефоне с крупным системным шрифтом
 * обычный Text обрезается («20.» вместо «20.43», «подпи» вместо «подписчики»).
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 9.sp,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val colored = if (color != Color.Unspecified) style.copy(color = color) else style
        if (!constraints.hasBoundedWidth || !style.fontSize.isSp) {
            Text(text, style = colored, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
            return@BoxWithConstraints
        }
        val widthPx = constraints.maxWidth
        val sp = remember(text, style, widthPx, maxLines, measurer) {
            fitTextSize(style.fontSize.value, minFontSize.value, 0.5f, textFits(measurer, text, style, widthPx, maxLines))
        }
        Text(text, style = scaled(colored, sp), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}
