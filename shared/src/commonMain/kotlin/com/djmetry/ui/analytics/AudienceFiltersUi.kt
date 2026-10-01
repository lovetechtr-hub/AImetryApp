package com.djmetry.ui.analytics

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.Country
import com.djmetry.data.analytics.*
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.settings.SearchPickerDialog
import com.djmetry.ui.settings.isoToMillis
import com.djmetry.ui.settings.millisToIso
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** От этой ширины правило — одной строкой [поле] [оператор] [значения] ×; уже — в две строки. */
internal const val FILTER_ROW_INLINE_DP = 640f

// ── Подписи ─────────────────────────────────────────────────────────────────

internal fun filterFieldKey(field: String): String? = when (field) {
    "streaming_platform" -> Strings.AF_F_PLATFORM
    "subscription_type" -> Strings.AF_F_SUBSCRIPTION
    "smartlink_id" -> Strings.AF_F_SMARTLINK
    "email_marketing" -> Strings.AF_F_EMAIL
    "registration_type" -> Strings.AF_F_REGISTRATION
    "country" -> Strings.AF_F_COUNTRY
    "artist_tags" -> Strings.AF_F_TAGS
    "followers" -> Strings.AF_F_FOLLOWERS
    "followers_bucket" -> Strings.AF_F_FOLLOWERS_BUCKET
    "last_seen" -> Strings.AF_F_LAST_SEEN
    "last_seen_days" -> Strings.AF_F_LAST_SEEN_DAYS
    "engagement_score" -> Strings.AF_F_ENGAGEMENT
    "listening_habits" -> Strings.AF_F_HABITS
    "djmetry_source" -> Strings.AF_F_DJ_SOURCE
    "djmetry_device" -> Strings.AF_F_DJ_DEVICE
    "djmetry_visits" -> Strings.AF_F_DJ_VISITS
    "djmetry_clicks" -> Strings.AF_F_DJ_CLICKS
    "fan_segment" -> Strings.AF_F_FAN_SEGMENT
    "fan_score" -> Strings.AF_F_FAN_SCORE
    "lead_status" -> Strings.AF_F_LEAD_STATUS
    "lead_source_type" -> Strings.AF_F_LEAD_SOURCE_TYPE
    "lead_source_id" -> Strings.AF_F_LEAD_SOURCE
    "lead_submitted_at" -> Strings.AF_F_LEAD_DATE
    "lead_fields_present" -> Strings.AF_F_LEAD_FIELDS
    "consent_version" -> Strings.AF_F_CONSENT
    else -> null
}

internal fun filterOpKey(op: String): String? = when (op) {
    FilterOp.EQ -> Strings.AF_OP_EQ
    FilterOp.NE -> Strings.AF_OP_NE
    FilterOp.IN -> Strings.AF_OP_IN
    FilterOp.NOT_IN -> Strings.AF_OP_NOT_IN
    FilterOp.ANY -> Strings.AF_OP_ANY
    FilterOp.NONE -> Strings.AF_OP_NONE
    FilterOp.GT -> Strings.AF_OP_GT
    FilterOp.GTE -> Strings.AF_OP_GTE
    FilterOp.LT -> Strings.AF_OP_LT
    FilterOp.LTE -> Strings.AF_OP_LTE
    FilterOp.BEFORE -> Strings.AF_OP_BEFORE
    FilterOp.AFTER -> Strings.AF_OP_AFTER
    FilterOp.BETWEEN -> Strings.AF_OP_BETWEEN
    else -> null
}

private fun groupKey(g: FilterGroup): String = when (g) {
    FilterGroup.Acquisition -> Strings.AF_G_ACQUISITION
    FilterGroup.Profile -> Strings.AF_G_PROFILE
    FilterGroup.Behavior -> Strings.AF_G_BEHAVIOR
    FilterGroup.DJMetry -> Strings.AF_G_DJMETRY
    FilterGroup.Other -> Strings.AF_G_OTHER
}

/** Корзины фолловеров — числа, не переводятся. */
private val FOLLOWER_BUCKETS = mapOf("0_1k" to "0–1K", "1k_10k" to "1K–10K", "10k_100k" to "10K–100K", "100k_1m" to "100K–1M", "1m_plus" to "1M+")

/** Ключ без перевода — читаемо: «not_available» → «Not available». */
internal fun humanize(v: String): String = v.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
internal fun fieldLabel(field: String): String = filterFieldKey(field)?.let { useI18n().t(it) } ?: humanize(field)

@Composable
internal fun opLabel(op: String): String = filterOpKey(op)?.let { useI18n().t(it) } ?: op

/** Подпись значения: страны — по-русски/на языке приложения, платформы и бренды — как есть, остальное — переводом. */
@Composable
internal fun valueLabel(field: String, v: String, countryName: (String) -> String): String {
    val i18n = useI18n()
    val key = when (field) {
        "fan_segment" -> fanLabel(FanSegment.of(v))
        "lead_source_type" -> when (v) { "bio_url" -> Strings.AUD_BIO; "smart_link" -> Strings.AUD_SMART; "tour" -> Strings.AUD_TOUR; else -> null }
        else -> when (v) {
            "paid" -> Strings.AF_V_PAID; "free" -> Strings.AF_V_FREE; "not_available" -> Strings.AF_V_NOT_AVAILABLE
            "subscribed" -> Strings.AF_V_SUBSCRIBED; "unsubscribed" -> Strings.AF_V_UNSUBSCRIBED
            "spotify_top_artists" -> Strings.AF_V_TOP_ARTISTS; "spotify_recent_tracks" -> Strings.AF_V_RECENT_TRACKS
            "direct" -> Strings.AF_V_DIRECT; "mobile" -> Strings.AF_V_MOBILE; "desktop" -> Strings.AF_V_DESKTOP; "tablet" -> Strings.AF_V_TABLET
            "submitted" -> Strings.AF_V_SUBMITTED; "not_submitted" -> Strings.AF_V_NOT_SUBMITTED
            "has_email" -> Strings.AF_V_HAS_EMAIL; "has_phone" -> Strings.AF_V_HAS_PHONE; "has_city" -> Strings.AF_V_HAS_CITY
            else -> null
        }
    }
    return when {
        key != null -> i18n.t(key)
        field == "country" -> countryName(v.uppercase())
        field == "streaming_platform" || field == "djmetry_source" || field == "registration_type" -> platformTitle(v)
        field == "followers_bucket" -> FOLLOWER_BUCKETS[v] ?: humanize(v)
        else -> humanize(v)
    }
}

// ── Карточка фильтров ──────────────────────────────────────────────────────

/**
 * Конструктор сегмента, вариант A «Строки-правила» (design/analytics/audience-filters-variants.html): заголовок с числом
 * правил и «Сбросить», строки [поле] [оператор] [значения] ×, ошибка бэкенда подсвечивает строку, внизу «+ Фильтр» и
 * «Сохранить как сегмент». [complex] — у сегмента группа or/вложенные условия: правится только на сайте.
 */
@Composable
internal fun FiltersCard(
    catalog: List<FilterField>, rules: List<AudienceRule>, onRules: (List<AudienceRule>) -> Unit,
    matching: Int?, invalidField: String?, complex: Boolean, width: Float,
    countryName: (String) -> String, onSave: () -> Unit,
) {
    val i18n = useI18n()
    val inline = width >= FILTER_ROW_INLINE_DP
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.07f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FilterAlt, null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(i18n.t(Strings.AF_TITLE) + if (rules.isNotEmpty()) " · ${rules.size}" else "", color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            matching?.let {
                Text(i18n.tWithArgs(Strings.AF_MATCH, arrayOf(groupThousands(it))), color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
            }
            if (rules.isNotEmpty() || complex) Text(
                i18n.t(Strings.AF_RESET), color = DJMetryColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onRules(emptyList()) }.padding(4.dp),
            )
        }
        if (complex) Text(i18n.t(Strings.AF_COMPLEX), color = DJMetryColors.Muted, fontSize = 13.sp)
        else {
            // Стабильный ключ строки: удаление из середины не переносит открытое меню и набранный текст к соседу
            val keys = remember { mutableListOf<Long>() }
            val counter = remember { longArrayOf(0) }
            while (keys.size < rules.size) keys.add(counter[0]++)
            while (keys.size > rules.size) keys.removeAt(keys.lastIndex)
            rules.forEachIndexed { i, r ->
                val field = catalog.firstOrNull { it.field == r.field } ?: FALLBACK_FILTER_CATALOG.firstOrNull { it.field == r.field }
                if (field != null) key(keys[i]) {
                    RuleRow(
                        r, field, catalog, inline, invalid = invalidField == r.field, countryName,
                        onChange = { nr -> onRules(rules.toMutableList().also { it[i] = nr }) },
                        onRemove = { keys.removeAt(i); onRules(rules.filterIndexed { k, _ -> k != i }) },
                    )
                }
            }
            if (invalidField != null) Text(i18n.t(Strings.AF_INVALID), color = DJMetryColors.LowScore, fontSize = 12.5.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!complex) FilterButton(Icons.Outlined.Add, i18n.t(Strings.AF_ADD), Modifier.weight(1f)) {
                catalog.firstOrNull()?.let { onRules(rules + defaultRule(it)) }
            }
            FilterButton(Icons.Outlined.BookmarkAdd, i18n.t(Strings.AF_SAVE), Modifier.weight(1f), enabled = rules.any { it.isComplete() }, onClick = onSave)
        }
    }
}

@Composable
private fun FilterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(DJMetryColors.PanelStrong).border(1.dp, DJMetryColors.Border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) DJMetryColors.Text else DJMetryColors.Muted
        Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Строка правила ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleRow(
    r: AudienceRule, field: FilterField, catalog: List<FilterField>, inline: Boolean, invalid: Boolean,
    countryName: (String) -> String, onChange: (AudienceRule) -> Unit, onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val fieldPicker: @Composable (Modifier) -> Unit = { m ->
        FieldPicker(field, catalog, m) { f -> if (f.field != r.field) onChange(defaultRule(f)) }
    }
    val opPicker: @Composable (Modifier) -> Unit = { m ->
        OpPicker(r.operator, field.operators, m) { op ->
            // Смена «до/после» ↔ «в диапазоне» или числа ↔ списка — значение сбрасываем
            val sameShape = (op == FilterOp.BETWEEN) == (r.operator == FilterOp.BETWEEN)
            onChange(if (sameShape) r.copy(operator = op) else AudienceRule(r.field, op))
        }
    }
    val remove: @Composable () -> Unit = {
        Icon(Icons.Outlined.Close, null, tint = DJMetryColors.Muted,
            modifier = Modifier.minimumInteractiveComponentSize().size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).padding(6.dp))
    }
    val values: @Composable (Modifier) -> Unit = { m -> ValueEditor(r, field, countryName, m, onChange) }
    val border = if (invalid) DJMetryColors.LowScore else DJMetryColors.Border
    if (inline) Row(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Background.copy(alpha = 0.5f)).border(1.dp, border, shape).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fieldPicker(Modifier.width(210.dp)); opPicker(Modifier.width(170.dp)); values(Modifier.weight(1f)); remove()
    } else Column(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Background.copy(alpha = 0.5f)).border(1.dp, border, shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fieldPicker(Modifier.weight(1.3f)); opPicker(Modifier.weight(1f)); remove()
        }
        values(Modifier.fillMaxWidth())
    }
}

/** Кнопка-выпадашка в стиле полей. */
@Composable
private fun SelectBox(text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, RoundedCornerShape(12.dp))
            .clickable(role = Role.DropdownList, onClick = onClick).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = DJMetryColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ExpandMore, null, tint = DJMetryColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/** Поле — с группами (Привлечение / Профиль / Поведение / DJ Metry / Другое), как на сайте. */
@Composable
private fun FieldPicker(current: FilterField, catalog: List<FilterField>, modifier: Modifier, onPick: (FilterField) -> Unit) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SelectBox(fieldLabel(current.field), Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong, modifier = Modifier.heightIn(max = 440.dp)) {
            FilterGroup.entries.forEach { g ->
                val fields = catalog.filter { it.group == g }
                if (fields.isEmpty()) return@forEach
                Text(i18n.t(groupKey(g)).uppercase(), color = DJMetryColors.Muted, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                fields.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(fieldLabel(f.field), color = if (f.field == current.field) DJMetryColors.Accent else DJMetryColors.Text) },
                        onClick = { open = false; onPick(f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpPicker(current: String, ops: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SelectBox(opLabel(current), Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            ops.forEach { op -> DropdownMenuItem(text = { Text(opLabel(op), color = if (op == current) DJMetryColors.Accent else DJMetryColors.Text) }, onClick = { open = false; onPick(op) }) }
        }
    }
}

// ── Значения ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueEditor(r: AudienceRule, field: FilterField, countryName: (String) -> String, modifier: Modifier, onChange: (AudienceRule) -> Unit) {
    when (field.kind) {
        FilterValueKind.Number -> NumberValue(r, modifier, onChange)
        FilterValueKind.DateRange -> DateValue(r, modifier, onChange)
        FilterValueKind.Text -> InlineInput(r.values.firstOrNull().orEmpty(), modifier, keyboard = KeyboardType.Text, submitOnType = true) { onChange(r.copy(values = listOf(it).filter(String::isNotBlank))) }
        FilterValueKind.Multi, FilterValueKind.FreeMulti -> FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.values.forEach { v ->
                Row(
                    Modifier.height(30.dp).clip(CircleShape).background(DJMetryColors.Accent2.copy(alpha = 0.16f))
                        .clickable(role = Role.Button) { onChange(r.copy(values = r.values - v)) }.padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(valueLabel(field.field, v, countryName), color = DJMetryColors.Accent2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.Close, null, tint = DJMetryColors.Accent2, modifier = Modifier.size(14.dp))
                }
            }
            AddValue(r, field, countryName) { v -> if (v.isNotBlank() && v !in r.values) onChange(r.copy(values = r.values + v.trim())) }
        }
    }
}

/** «+ Добавить»: страна — справочник с поиском; варианты каталога — меню; свободный ввод — поле с «+». */
@Composable
private fun AddValue(r: AudienceRule, field: FilterField, countryName: (String) -> String, onAdd: (String) -> Unit) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val left = field.options.filter { it !in r.values }
    if (typing) { InlineInput("", Modifier.width(170.dp), placeholder = i18n.t(Strings.AF_ENTER)) { onAdd(it); typing = false }; return }
    Box {
        Row(
            Modifier.height(30.dp).clip(CircleShape).border(1.dp, DJMetryColors.Accent.copy(alpha = 0.5f), CircleShape)
                .clickable(role = Role.Button) {
                    when {
                        field.field == "country" || left.isNotEmpty() -> open = true
                        else -> typing = true
                    }
                }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Add, null, tint = DJMetryColors.Accent, modifier = Modifier.size(15.dp))
            Text(i18n.t(Strings.AF_ADD_VALUE), color = DJMetryColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        if (field.field != "country") DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong, modifier = Modifier.heightIn(max = 380.dp)) {
            left.forEach { v -> DropdownMenuItem(text = { Text(valueLabel(field.field, v, countryName), color = DJMetryColors.Text) }, onClick = { open = false; onAdd(v) }) }
            if (field.kind == FilterValueKind.FreeMulti) DropdownMenuItem(
                text = { Text(i18n.t(Strings.AF_CUSTOM_VALUE), color = DJMetryColors.Accent) },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = DJMetryColors.Accent) },
                onClick = { open = false; typing = true },
            )
        }
    }
    if (open && field.field == "country") {
        val settings = LocalAppContainer.current.settings
        val countries by produceState(emptyList<Country>()) { value = settings.countries().getOrNull().orEmpty() }
        SearchPickerDialog(
            title = fieldLabel("country"), items = countries.filter { c -> r.values.none { it.equals(c.code, true) } }, label = { it.name }, flagIso = { it.code },
            onPick = { onAdd(it.code.uppercase()); open = false }, onDismiss = { open = false },
        )
    }
}

/** Небольшое поле ввода в строке правила. [submitOnType] — значение уходит при каждом вводе (текст), иначе по «+»/Enter. */
@Composable
private fun InlineInput(initial: String, modifier: Modifier, keyboard: KeyboardType = KeyboardType.Text, placeholder: String? = null, submitOnType: Boolean = false, onSubmit: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty() && placeholder != null) Text(placeholder, color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                text, { t -> text = if (keyboard == KeyboardType.Number) t.filter(Char::isDigit).take(9) else t; if (submitOnType) onSubmit(text) },
                singleLine = true, textStyle = TextStyle(color = DJMetryColors.Text, fontSize = 13.5.sp), cursorBrush = SolidColor(DJMetryColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!submitOnType && text.isNotBlank()) onSubmit(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!submitOnType) Icon(Icons.Outlined.Check, null, tint = DJMetryColors.Accent,
            modifier = Modifier.minimumInteractiveComponentSize().size(28.dp).clip(CircleShape).clickable(role = Role.Button) { if (text.isNotBlank()) onSubmit(text) }.padding(5.dp))
    }
}

/** Число: пресеты (фолловеры при «больше», вовлечённость 1…10) или ввод целого ≥ 0. */
@Composable
private fun NumberValue(r: AudienceRule, modifier: Modifier, onChange: (AudienceRule) -> Unit) {
    val presets = numberPresets(r.field, r.operator)
    if (presets != null) {
        var open by remember { mutableStateOf(false) }
        Box(modifier.wrapContentWidth(Alignment.Start)) {
            SelectBox(r.number?.let { groupThousands(it.toInt()) } ?: "—", Modifier.width(140.dp)) { open = true }
            DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
                presets.forEach { p -> DropdownMenuItem(text = { Text(groupThousands(p.toInt()), color = DJMetryColors.Text) }, onClick = { open = false; onChange(r.copy(number = p, values = emptyList())) }) }
            }
        }
    } else InlineInput(r.number?.toString().orEmpty(), modifier.widthIn(max = 160.dp), keyboard = KeyboardType.Number, submitOnType = true) {
        onChange(r.copy(number = it.toLongOrNull(), values = emptyList()))
    }
}

/** Дата: «до/после» — одна дата; «в диапазоне» — 7/30/90/180 дней, с начала года или свои даты. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DateValue(r: AudienceRule, modifier: Modifier, onChange: (AudienceRule) -> Unit) {
    val i18n = useI18n()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var picking by remember { mutableStateOf<String?>(null) } // "single" | "from" | "to"
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r.operator == FilterOp.BETWEEN) {
            DATE_RANGE_PRESETS.forEach { d ->
                val (f, t) = dateRangePreset(d, today)
                val on = r.from == f && r.to == t
                Text(
                    if (d == 0) i18n.t(Strings.AF_YTD) else i18n.tWithArgs(Strings.AF_DAYS, arrayOf(d.toString())),
                    color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Panel)
                        .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape)
                        .clickable(role = Role.RadioButton) { onChange(r.copy(from = f, to = t)) }.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
            DateChip(i18n.t(Strings.AF_FROM), r.from) { picking = "from" }
            DateChip(i18n.t(Strings.AF_TO), r.to) { picking = "to" }
        } else DateChip(i18n.t(Strings.AF_DATE), r.values.firstOrNull()) { picking = "single" }
    }
    picking?.let { which ->
        val initial = when (which) { "from" -> r.from; "to" -> r.to; else -> r.values.firstOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let(::isoToMillis))
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(::millisToIso)?.let { d ->
                        onChange(when (which) { "from" -> r.copy(from = d); "to" -> r.copy(to = d); else -> r.copy(values = listOf(d)) })
                    }
                    picking = null
                }) { Text(i18n.t(Strings.SET_SAVE), color = DJMetryColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateChip(label: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.Event, null, tint = DJMetryColors.Accent, modifier = Modifier.size(15.dp))
        Text("$label: ${value ?: "—"}", color = DJMetryColors.Text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Сохранение и удаление ──────────────────────────────────────────────────

/** «Сохранить как сегмент»: имя (обрезка пробелов; пустое — нельзя), ошибка — текстом. */
@Composable
internal fun SaveSegmentDialog(rulesCount: Int, matching: Int?, error: String?, busy: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val i18n = useI18n()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DJMetryColors.PanelStrong,
        title = { Text(i18n.t(Strings.AF_SAVE_TITLE), color = DJMetryColors.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.djmetry.ui.settings.SettingsField(name, { name = it.take(80) }, i18n.t(Strings.AF_NAME))
                Text(
                    i18n.tWithArgs(Strings.AF_SAVE_HINT, arrayOf(rulesCount.toString(), matching?.let { groupThousands(it) } ?: "—")),
                    color = DJMetryColors.Muted, fontSize = 13.sp,
                )
                error?.let { Text(it, color = DJMetryColors.LowScore, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && normalizeSegmentName(name) != null, onClick = { onSave(name) }) {
                Text(i18n.t(Strings.SET_SAVE), color = DJMetryColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
    )
}
