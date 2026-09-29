package com.djmetry.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djmetry.ui.theme.DJMetryColors

/**
 * Ссылка на флаг страны (как на сайте — flagcdn.com, 80 px): «ES» → …/w80/es.png. Не ISO2 — null.
 * В проекте без эмодзи (docs/RULES.md): флаги — картинками, остальное — иконками Material.
 */
fun flagUrl(iso2: String?): String? {
    val c = iso2?.trim()?.lowercase() ?: return null
    return if (c.length == 2 && c.all { it in 'a'..'z' }) "https://flagcdn.com/w80/$c.png" else null
}

/** Флаг страны 4:3 со скруглением и тонкой рамкой; пока грузится или нет флага — пустая плашка того же размера. */
@Composable
fun CountryFlag(iso2: String?, width: Dp = 22.dp, modifier: Modifier = Modifier) {
    val photo by rememberRemoteImage(flagUrl(iso2))
    val shape = RoundedCornerShape(width * 0.14f)
    Box(modifier.size(width, width * 0.75f).clip(shape).background(DJMetryColors.PanelStrong).border(0.5.dp, Color.White.copy(alpha = 0.15f), shape)) {
        photo.bitmap?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

/** Имя страны из справочника без хвостов ISO 3166: «Russian Federation (the)» → «Russian Federation». */
fun cleanCountryName(name: String): String = name.replace(Regex("""\s*\((the|The)\)\s*$"""), "").trim()
