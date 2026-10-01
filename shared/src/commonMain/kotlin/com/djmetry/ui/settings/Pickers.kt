package com.djmetry.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.djmetry.api.models.Country
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Особый код «Другая страна» — ручной ввод, как `__other__` на сайте. */
const val OTHER_COUNTRY = "__other__"

/**
 * Список с поиском в диалоге: страны, жанры. Только выбор из списка — свободного ввода нет.
 * [extra] — дополнительный пункт в конце (например «Другая страна»).
 */
@Composable
internal fun <T> SearchPickerDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    /** ISO2 страны — слева флаг (картинкой). */
    flagIso: (T) -> String? = { null },
    extra: Pair<String, () -> Unit>? = null,
    /** Своя история выбора: недавние — сверху, пока поиск пуст. Ключ элемента — код страны или подпись. */
    history: com.djmetry.data.search.SearchScope? = null,
    historyKey: (T) -> String = { flagIso(it) ?: label(it) },
    /** Откуда список: пока грузится — заглушки, не загрузился — «Ошибка · Повторить» вместо пустоты. */
    load: com.djmetry.ui.components.Loadable<*>? = null,
) {
    val i18n = useI18n()
    val historyRepo = com.djmetry.LocalAppContainer.current.searchHistory
    val historyData by historyRepo.data.collectAsState()
    var query by remember { mutableStateOf("") }
    val recent = remember(historyData, items, history) {
        val keys = history?.let { historyData.queries[it.key] }.orEmpty()
        keys.mapNotNull { k -> items.firstOrNull { historyKey(it).equals(k, ignoreCase = true) } }.take(5)
    }
    val shown = remember(query, items) {
        if (query.isBlank()) items else items.filter { label(it).contains(query.trim(), ignoreCase = true) }
    }
    val pick: (T) -> Unit = { item -> history?.let { historyRepo.record(it, historyKey(item)) }; onPick(item) }
    Dialog(onDismissRequest = onDismiss) { com.djmetry.ui.components.DismissKeyboardOnTap {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SettingsField(query, { query = it }, i18n.t(Strings.SEARCH_HINT))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                // Недавно выбранные — сверху, пока ничего не набрано
                if (query.isBlank() && recent.isNotEmpty()) {
                    item { Text(i18n.t(Strings.SH_RECENT_PICKS).uppercase(), color = DJMetryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp)) }
                    items(recent) { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick(item) }.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.History, null, tint = DJMetryColors.Muted, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                            flagIso(item)?.let { com.djmetry.ui.components.CountryFlag(it, 24.dp, Modifier.padding(end = 12.dp)) }
                            Text(label(item), color = DJMetryColors.Text, fontSize = 15.sp)
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = DJMetryColors.Border) }
                }
                if (items.isEmpty() && load?.failed == true) item { com.djmetry.ui.components.LoadFailedRow(load::retry) }
                if (items.isEmpty() && load?.loading == true) item { repeat(4) { com.djmetry.ui.components.SkeletonBox(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(40.dp)) } }
                items(shown.take(300)) { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick(item) }.padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        flagIso(item)?.let { com.djmetry.ui.components.CountryFlag(it, 24.dp, Modifier.padding(end = 12.dp)) }
                        Text(label(item), color = DJMetryColors.Text, fontSize = 15.sp)
                    }
                }
                extra?.let { (text, action) ->
                    item {
                        Text(
                            text, color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) }
        }
    } }
}

/**
 * Город страны [iso] из справочника бэкенда (`/location/cities`): сразу — крупные города, при наборе — поиск
 * (с паузой ввода). Своего города нет в списке — пункт «Использовать «…»» с набранным текстом.
 */
@Composable
internal fun CityPickerDialog(iso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val i18n = useI18n()
    val repo = com.djmetry.LocalAppContainer.current.settings
    // История городов — своя для каждой страны: ключ «DE:Berlin»
    val history = com.djmetry.LocalAppContainer.current.searchHistory
    val historyData by history.data.collectAsState()
    val recent = historyData.queries[com.djmetry.data.search.SearchScope.City.key].orEmpty()
        .filter { it.startsWith("$iso:", ignoreCase = true) }.map { it.substringAfter(':') }.take(5)
    val pickCity: (String) -> Unit = { name -> history.record(com.djmetry.data.search.SearchScope.City, "$iso:$name"); onPick(name) }
    var query by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val cities by produceState<List<String>?>(null, iso, query) {
        if (query.isNotBlank()) kotlinx.coroutines.delay(300)
        repo.cities(iso, query.trim().ifEmpty { null })
            .onSuccess { failed = false; value = it.map { c -> c.name }.distinct() }
            .onFailure { failed = true; value = emptyList() }
    }
    Dialog(onDismissRequest = onDismiss) { com.djmetry.ui.components.DismissKeyboardOnTap {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(i18n.t(Strings.SET_CITY), color = DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SettingsField(query, { query = it.take(200) }, i18n.t(Strings.SEARCH_HINT))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                val list = cities
                if (query.isBlank() && recent.isNotEmpty()) {
                    item { Text(i18n.t(Strings.SH_RECENT_PICKS).uppercase(), color = DJMetryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp)) }
                    items(recent, key = { "recent-$it" }) { name ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pickCity(name) }.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.material.icons.Icons.Filled.History, null, tint = DJMetryColors.Muted, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                            Text(name, color = DJMetryColors.Text, fontSize = 15.sp)
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = DJMetryColors.Border) }
                }
                if (list == null) items(5) { com.djmetry.ui.components.SkeletonListRow(it, leading = false, trailing = false) }
                else items(list, key = { it }) { name ->
                    Text(
                        name, color = DJMetryColors.Text, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pickCity(name) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
                if (failed) item { Text(i18n.t(Strings.HOME_ERROR), color = DJMetryColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(10.dp)) }
                // Своего города нет в справочнике — как на сайте, можно указать как есть
                val typed = query.trim()
                if (typed.length >= 2 && list?.none { it.equals(typed, ignoreCase = true) } != false) item {
                    Text(
                        i18n.tWithArgs(Strings.BR_USE_CITY, arrayOf(typed)), color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pickCity(typed) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) }
        }
    } }
}

/**
 * Страна (строго из справочника, ISO2 + флаг; «Другая» — ручной ввод) и город (подсказки
 * `/location/cities` только после выбора страны; можно ввести своё — как на сайте).
 */
@Composable
internal fun CountryCityPicker(country: String, onCountry: (String) -> Unit, city: String, onCity: (String) -> Unit, allowOther: Boolean = true) {
    val i18n = useI18n()
    val repo = com.djmetry.LocalAppContainer.current.settings
    val countriesLoad = com.djmetry.ui.components.rememberLoadable { repo.countries() }
    val countries = countriesLoad.value.orEmpty()
    var picking by remember { mutableStateOf(false) }
    val known = countries.firstOrNull { it.code.equals(country, ignoreCase = true) }
    var other by remember(country, countries) { mutableStateOf(allowOther && country.isNotBlank() && countries.isNotEmpty() && known == null) }

    SettingsGroup(null) {
        SettingsRow(
            i18n.t(Strings.SET_COUNTRY),
            when {
                known != null -> known.name
                other -> country.ifBlank { i18n.t(Strings.SET_OTHER_COUNTRY) }
                else -> i18n.t(Strings.SET_NOT_SET)
            },
            end = RowEnd.Chevron, divider = false,
        ) { picking = true }
    }
    if (other) SettingsField(country, onCountry, i18n.t(Strings.SET_OTHER_COUNTRY))
    if (picking) {
        SearchPickerDialog(
            history = com.djmetry.data.search.SearchScope.Country,
            title = i18n.t(Strings.SET_COUNTRY), items = countries, label = { it.name }, flagIso = { it.code }, load = countriesLoad,
            onPick = { other = false; onCountry(it.code); if (!it.code.equals(country, true)) onCity(""); picking = false },
            onDismiss = { picking = false },
            extra = if (allowOther) i18n.t(Strings.SET_OTHER_COUNTRY) to { other = true; onCountry(""); onCity(""); picking = false } else null,
        )
    }

    // Город: без страны — недоступен; подсказки из справочника по ISO-коду с задержкой ввода
    val iso = known?.code
    val cities by produceState(emptyList<String>(), iso, city) {
        value = if (iso != null && city.trim().length >= 2) { kotlinx.coroutines.delay(300); repo.cities(iso, city.trim()).getOrNull().orEmpty().map { it.name }.take(6) } else emptyList()
    }
    SettingsField(
        city, onCity, i18n.t(Strings.SET_CITY),
        enabled = known != null || other,
        supporting = if (known == null && !other) i18n.t(Strings.SET_SELECT_COUNTRY_FIRST) else null,
    )
    if (cities.isNotEmpty() && cities.none { it.equals(city.trim(), true) }) {
        SettingsGroup(null) {
            cities.forEachIndexed { i, name -> SettingsRow(name, null, null, end = RowEnd.None, divider = i < cities.lastIndex) { onCity(name) } }
        }
    }
}

/** Дата «YYYY-MM-DD» ↔ миллисекунды UTC для системного календаря. */
internal fun isoToMillis(iso: String): Long? = runCatching { LocalDate.parse(iso).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }.getOrNull()

internal fun millisToIso(millis: Long): String = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()

/** Дата рождения — строго системный календарь 1900…текущий год, на бэк уходит YYYY-MM-DD без времени. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthDatePicker(value: String, onChange: (String) -> Unit) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.SET_BIRTH), value.ifEmpty { i18n.t(Strings.SET_NOT_SET) }, end = RowEnd.Chevron, divider = false) { open = true }
    }
    if (value.isNotEmpty()) {
        Text(i18n.t(Strings.SET_CLEAR), color = DJMetryColors.Accent, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange("") }.padding(6.dp))
    }
    if (open) {
        val maxYear = currentYear()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = isoToMillis(value),
            yearRange = 1900..maxYear,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onChange(millisToIso(it)) }; open = false }) { Text(i18n.t(Strings.SET_SAVE), color = DJMetryColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
        ) { DatePicker(state = state) }
    }
}
