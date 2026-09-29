package com.djmetry.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.theme.DJMetryColors

/** Цвет значка строки: зелёный — профиль и уведомления, оранжевый — письма, синий — радары, красный — опасное. */
enum class RowTone(val fg: Color, val bg: Color) {
    Green(Color(0xFF5EE6A8), Color(0xFF10302A)),
    Orange(Color(0xFFFFB35B), Color(0xFF3A2A12)),
    Blue(Color(0xFF7DA7FF), Color(0xFF16233F)),
    Red(Color(0xFFFF6B6B), Color(0xFF3A1414)),
}

/** Что справа в строке. */
sealed interface RowEnd {
    data class Toggle(val on: Boolean, val onChange: (Boolean) -> Unit) : RowEnd
    data class Value(val text: String) : RowEnd
    data object Chevron : RowEnd
    data object None : RowEnd
}

/** Группа строк: подпись капсом над карточкой, строки с разделителями. */
@Composable
internal fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        title?.let {
            Text(it.uppercase(), color = DJMetryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}

/**
 * Строка настроек: значок, заголовок (+ подпись), справа — переключатель, значение со стрелкой или стрелка.
 * Переключатель срабатывает и по тапу на всю строку. [selected] — подсветка выбранного раздела на планшете.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: RowTone = RowTone.Green,
    end: RowEnd = RowEnd.Chevron,
    divider: Boolean = true,
    enabled: Boolean = true,
    selected: Boolean = false,
    titleColor: Color = DJMetryColors.Text,
    onClick: (() -> Unit)? = null,
) {
    val click: (() -> Unit)? = when {
        !enabled -> null
        end is RowEnd.Toggle -> { { end.onChange(!end.on) } }
        else -> onClick
    }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (selected) Modifier.background(DJMetryColors.Accent.copy(alpha = 0.12f)) else Modifier)
                .then(if (click != null) Modifier.clickable(role = if (end is RowEnd.Toggle) Role.Switch else Role.Button, onClick = click) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tone.bg), contentAlignment = Alignment.Center) {
                    Icon(it, null, tint = tone.fg, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                AutoSizeText(title, TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = if (enabled) titleColor else DJMetryColors.Muted, minFontSize = 11.sp)
                subtitle?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            }
            when (end) {
                is RowEnd.Toggle -> Switch(
                    checked = end.on, onCheckedChange = if (enabled) end.onChange else null, enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DJMetryColors.Background, checkedTrackColor = DJMetryColors.Accent, checkedBorderColor = DJMetryColors.Accent,
                        uncheckedThumbColor = DJMetryColors.Muted, uncheckedTrackColor = Color(0xFF2C3D5C), uncheckedBorderColor = Color(0xFF2C3D5C),
                    ),
                )
                is RowEnd.Value -> {
                    Text(end.text, color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DJMetryColors.Muted)
                }
                RowEnd.Chevron -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DJMetryColors.Muted)
                RowEnd.None -> Unit
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = if (icon != null) 58.dp else 14.dp).height(1.dp).background(DJMetryColors.Border))
    }
}

/** Заголовок страницы настроек. */
@Composable
internal fun PageTitle(text: String) {
    AutoSizeText(text, TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 18.sp)
}

/** Подсказка серым. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = DJMetryColors.Muted, fontSize = 12.5.sp, modifier = modifier.padding(horizontal = 6.dp))
}

/** Поле ввода в стиле карточек. */
@Composable
internal fun SettingsField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false, supporting: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, isError = isError,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DJMetryColors.Text, unfocusedTextColor = DJMetryColors.Text,
            focusedContainerColor = DJMetryColors.Panel, unfocusedContainerColor = DJMetryColors.Panel,
            focusedBorderColor = DJMetryColors.Accent, unfocusedBorderColor = DJMetryColors.Border,
            focusedLabelColor = DJMetryColors.Accent, unfocusedLabelColor = DJMetryColors.Muted, cursorColor = DJMetryColors.Accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Выбор из двух-трёх вариантов (частота писем). */
@Composable
internal fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(DJMetryColors.Panel).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (on) DJMetryColors.Accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                AutoSizeText(label, TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = if (on) DJMetryColors.Background else DJMetryColors.Text, minFontSize = 10.sp)
            }
        }
    }
}

/** Главная кнопка формы («Сохранить»). */
@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) DJMetryColors.Accent else DJMetryColors.PanelStrong)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) DJMetryColors.Background else DJMetryColors.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}
