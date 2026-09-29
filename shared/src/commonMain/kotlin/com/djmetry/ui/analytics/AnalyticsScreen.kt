package com.djmetry.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.BreakdownRow
import com.djmetry.api.models.GeoOptionsResponse
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.NetworkResponse
import com.djmetry.data.analytics.*
import com.djmetry.data.repository.AnalyticsBlock
import com.djmetry.data.repository.AnalyticsBlockedException
import com.djmetry.data.repository.AnalyticsReport
import com.djmetry.data.repository.availableSources
import com.djmetry.data.repository.flagEmoji
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.settings.PageTitle
import com.djmetry.ui.settings.SearchPickerDialog
import com.djmetry.ui.settings.isoToMillis
import com.djmetry.ui.settings.millisToIso
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Открыть аналитику из профиля (ставит MainShell). */
val LocalOpenAnalytics = staticCompositionLocalOf<() -> Unit> { {} }

/** Ширина контента, от которой экран раскладывается в две колонки (планшет альбом, десктоп). */
internal const val TWO_COLUMNS_MIN_DP = 1000f

/** Ширина, от которой плитки KPI идут в один ряд, а не 2×2. */
internal const val KPI_ROW_MIN_DP = 600f

/**
 * Аналитика (спека §15, вариант «A + карта из B»): источник и период сверху, плитки KPI, график активности (Vico),
 * карта стран (MapLibre), клики, визиты DJMetry-юзеров, устройства и источники трафика. Одна реализация —
 * раскладка по реальной ширине: телефон — колонка, планшет портрет — широкая колонка, альбом и десктоп — две колонки.
 * У BIO и карточки DJMetry фильтры независимы, как на сайте.
 */
@Composable
fun AnalyticsScreen(me: MeResponse?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.analytics
    val sources = remember(me) { me?.let { availableSources(it) } ?: listOf(AnalyticsSource.Bio) }
    var source by remember(sources) { mutableStateOf(sources.first()) }
    val queries = remember { mutableStateMapOf<AnalyticsSource, AnalyticsQuery>() }
    val query = queries[source] ?: AnalyticsQuery()
    var report by remember { mutableStateOf<AnalyticsReport?>(null) }
    var reportKey by remember { mutableStateOf<Pair<AnalyticsSource, AnalyticsQuery>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var geo by remember { mutableStateOf<GeoOptionsResponse?>(null) }

    LaunchedEffect(source, query, attempt) {
        error = null
        repo.load(source, query, refresh = attempt > 0)
            .onSuccess { report = it; reportKey = source to query }
            .onFailure { error = it; if (reportKey?.first != source) report = null }
    }
    LaunchedEffect(source, query.period) { geo = repo.geoOptions(source, query).getOrNull() }

    // Названия стран — из справочника локаций (на языке пользователя), иначе из geo-options, иначе код
    val countryNames by produceState(emptyMap<String, String>()) {
        value = container.settings.countries().getOrNull().orEmpty().associate { it.code.uppercase() to it.name }
    }
    val countryName: (String) -> String = { iso -> countryNames[iso] ?: geo?.countries?.firstOrNull { it.code.equals(iso, true) }?.name ?: iso }
    val update: (AnalyticsQuery) -> Unit = { queries[source] = it }

    Box(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth.value
            val twoColumns = width >= TWO_COLUMNS_MIN_DP
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = if (width < KPI_ROW_MIN_DP) 16.dp else 28.dp).padding(bottom = LocalBottomClearance.current + 16.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = if (twoColumns) 1320.dp else 760.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header(onBack, sources, source, onSource = { source = it }, wide = width >= KPI_ROW_MIN_DP)
                Filters(query, geo, countryName, update)
                val blocked = (error as? AnalyticsBlockedException)?.block
                when {
                    blocked != null -> Message(i18n = if (blocked == AnalyticsBlock.NoMusicPage) Strings.AN_NO_PAGE else Strings.AN_NOT_VERIFIED)
                    error != null && report == null -> Message(Strings.AN_ERROR, retry = { attempt++ })
                    else -> LoadingCrossfade(loading = report == null, skeleton = { AnalyticsSkeleton(width) }) {
                        val r = report ?: return@LoadingCrossfade
                        // Пока грузится новый период — старые данные приглушены
                        val dim by animateFloatAsState(if (reportKey == source to query) 1f else 0.5f, label = "dim")
                        Box(Modifier.alpha(dim)) { Content(r, width, countryName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, sources: List<AnalyticsSource>, source: AnalyticsSource, onSource: (AnalyticsSource) -> Unit, wide: Boolean) {
    val i18n = useI18n()
    val switcher: @Composable (Modifier) -> Unit = { m ->
        if (sources.size > 1) Row(
            m.clip(CircleShape).background(DJMetryColors.Panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sources.forEach { s ->
                val on = s == source
                Text(
                    i18n.t(if (s == AnalyticsSource.Bio) Strings.AN_SRC_BIO else Strings.AN_SRC_DJMETRY),
                    color = if (on) DJMetryColors.Background else DJMetryColors.Muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = !wide).clip(CircleShape).background(if (on) DJMetryColors.Accent else Color.Transparent)
                        .clickable(role = Role.Tab) { onSource(s) }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, i18n.t(Strings.ARTIST_BACK), tint = DJMetryColors.Text,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).padding(9.dp),
        )
        Box(Modifier.weight(1f).padding(start = 6.dp)) { PageTitle(i18n.t(Strings.AN_TITLE)) }
        if (wide) switcher(Modifier.widthIn(max = 420.dp))
    }
    if (!wide) switcher(Modifier.fillMaxWidth())
}

/** Период (пресеты + свои даты) и гео (страна → город). Чипы прокручиваются, если не помещаются. */
@Composable
private fun Filters(query: AnalyticsQuery, geo: GeoOptionsResponse?, countryName: (String) -> String, update: (AnalyticsQuery) -> Unit) {
    val i18n = useI18n()
    var customOpen by remember { mutableStateOf(false) }
    var countryOpen by remember { mutableStateOf(false) }
    var cityOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RangePreset.entries.forEach { p ->
            Chip(i18n.t(rangeKey(p)), on = query.period == AnalyticsPeriod.Preset(p)) { update(query.copy(period = AnalyticsPeriod.Preset(p))) }
        }
        val custom = query.period as? AnalyticsPeriod.Custom
        Chip(custom?.let { "${it.from} – ${it.to}" } ?: i18n.t(Strings.AN_R_CUSTOM), on = custom != null, icon = Icons.Outlined.CalendarMonth) { customOpen = true }
        Chip(query.country?.let { "${flagEmoji(it)} ${countryName(it)}" } ?: i18n.t(Strings.AN_ALL_COUNTRIES), on = query.country != null, icon = Icons.Outlined.Public) { countryOpen = true }
        if (query.country != null) Chip(query.city ?: i18n.t(Strings.AN_ALL_CITIES), on = query.city != null, icon = Icons.Outlined.LocationCity) { cityOpen = true }
    }
    if (customOpen) CustomRangeDialog(query.period as? AnalyticsPeriod.Custom, onDismiss = { customOpen = false }) { f, t ->
        update(query.copy(period = AnalyticsPeriod.Custom(f, t))); customOpen = false
    }
    if (countryOpen) SearchPickerDialog(
        title = i18n.t(Strings.AN_ALL_COUNTRIES), items = geo?.countries.orEmpty(), label = { countryName(it.code.uppercase()) }, leading = { flagEmoji(it.code) },
        onPick = { update(query.copy(country = it.code.uppercase(), city = null)); countryOpen = false }, onDismiss = { countryOpen = false },
        extra = i18n.t(Strings.AN_ALL_COUNTRIES) to { update(query.copy(country = null, city = null)); countryOpen = false },
    )
    if (cityOpen) SearchPickerDialog(
        title = i18n.t(Strings.AN_ALL_CITIES), items = geo?.cities.orEmpty().filter { it.country_code.equals(query.country, true) }, label = { it.label ?: it.city },
        onPick = { update(query.copy(city = it.city)); cityOpen = false }, onDismiss = { cityOpen = false },
        extra = i18n.t(Strings.AN_ALL_CITIES) to { update(query.copy(city = null)); cityOpen = false },
    )
}

@Composable
private fun Chip(text: String, on: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.16f) else DJMetryColors.Panel)
            .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, tint = if (on) DJMetryColors.Accent else DJMetryColors.Muted, modifier = Modifier.size(16.dp)) }
        Text(text, color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** Свои даты: системный выбор диапазона; правила бэка (начало ≤ конец, ≤ 732 дней) проверяем до запроса. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(current: AnalyticsPeriod.Custom?, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit) {
    val i18n = useI18n()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = current?.from?.toString()?.let(::isoToMillis),
        initialSelectedEndDateMillis = current?.to?.toString()?.let(::isoToMillis),
        yearRange = 2000..today.year,
    )
    val from = state.selectedStartDateMillis?.let { LocalDate.parse(millisToIso(it)) }
    val to = state.selectedEndDateMillis?.let { LocalDate.parse(millisToIso(it)) }
    val problem = if (from != null && to != null) validateCustomRange(from, to) else null
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (from != null && to != null) onApply(from, to) }, enabled = from != null && to != null && problem == null) {
                Text(i18n.t(Strings.AN_APPLY), color = DJMetryColors.Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
    ) {
        Column {
            DateRangePicker(state = state, modifier = Modifier.weight(1f, fill = false), showModeToggle = false)
            problem?.let {
                Text(
                    i18n.t(if (it == CustomRangeProblem.TooLong) Strings.AN_ERR_LONG else Strings.AN_ERR_ORDER),
                    color = DJMetryColors.LowScore, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Message(i18n: String, retry: (() -> Unit)? = null) {
    val t = useI18n()
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Insights, null, tint = DJMetryColors.Muted, modifier = Modifier.size(36.dp))
        Text(t.t(i18n), color = DJMetryColors.Text, fontSize = 15.sp, textAlign = TextAlign.Center)
        retry?.let { TextButton(onClick = it) { Text(t.t(Strings.AN_RETRY), color = DJMetryColors.Accent, fontWeight = FontWeight.SemiBold) } }
    }
}

// ── Содержимое ──────────────────────────────────────────────────────────────

@Composable
private fun Content(r: AnalyticsReport, width: Float, countryName: (String) -> String) {
    val k = remember(r) { kpis(r.network, r.breakdown) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val (from, to) = remember(r) { periodBounds(r.network.range?.from, r.network.range?.to, today) }
    var bucket by remember(from, to) { mutableStateOf(defaultBucket(from, to)) }
    val points = remember(r, bucket) { buildActivity(from, to, k.visits, k.clicks, k.unique, bucket) }
    val map = remember(r) { mapCountries(r.breakdown?.countries.orEmpty()) }
    val gap = 14.dp

    val activity: @Composable (Dp, Int) -> Unit = { h, labels -> ActivityCard(points, bucket, { bucket = it }, h, labels, k.visits == 0) }
    val mapCard: @Composable (Dp, Int) -> Unit = { h, rows -> MapCard(map, r.breakdown, h, rows, countryName) }

    when {
        width >= TWO_COLUMNS_MIN_DP -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KpiRow(k, countryName, withTopCountry = true)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    activity(270.dp, 8)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Box(Modifier.weight(1f)) { ClicksCard(r.network) }
                        Box(Modifier.weight(1f)) { DevicesCard(r.breakdown) }
                    }
                    SourcesCard(r.breakdown)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    mapCard(260.dp, 6)
                    NetworkCard(r.network)
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KpiRow(k, countryName, withTopCountry = false, columns = if (width >= KPI_ROW_MIN_DP) 4 else 2)
            activity(if (width >= KPI_ROW_MIN_DP) 240.dp else 200.dp, if (width >= KPI_ROW_MIN_DP) 7 else 4)
            mapCard(if (width >= KPI_ROW_MIN_DP) 260.dp else 190.dp, 3)
            ClicksCard(r.network)
            NetworkCard(r.network)
            DevicesCard(r.breakdown)
            SourcesCard(r.breakdown)
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
            Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun KpiRow(k: AnalyticsKpis, countryName: (String) -> String, withTopCountry: Boolean, columns: Int = 5) {
    val i18n = useI18n()
    val tiles = buildList {
        add(Triple(Icons.Outlined.Visibility, Strings.AN_VISITS, groupThousands(k.visits)))
        add(Triple(Icons.Outlined.AdsClick, Strings.AN_CLICKS, groupThousands(k.clicks) + (k.ctr?.let { "  ·  ${ctrLabel(it)}" } ?: "")))
        add(Triple(Icons.Outlined.Group, Strings.AN_UNIQUE, groupThousands(k.unique)))
        add(Triple(Icons.Outlined.LockOpen, Strings.AN_LEADS, groupThousands(k.leads)))
        if (withTopCountry) add(Triple(Icons.Outlined.Public, Strings.AN_TOP_COUNTRY, k.topCountry?.let { "${flagEmoji(it)} ${countryName(it)}" } ?: "—"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, key, value) -> KpiTile(icon, i18n.t(key), value, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun KpiTile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = DJMetryColors.Muted, modifier = Modifier.size(14.dp))
            Text(label, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AutoSizeText(value, TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 14.sp)
    }
}

@Composable
private fun ActivityCard(points: List<ActivityPoint>, bucket: ActivityBucket, onBucket: (ActivityBucket) -> Unit, height: Dp, maxLabels: Int, empty: Boolean) {
    val i18n = useI18n()
    AnalyticsCard(i18n.t(Strings.AN_ACTIVITY), trailing = {
        Row(Modifier.clip(CircleShape).background(DJMetryColors.Background).padding(3.dp)) {
            listOf(ActivityBucket.Day to Strings.AN_DAY, ActivityBucket.Week to Strings.AN_WEEK, ActivityBucket.Month to Strings.AN_MONTH).forEach { (b, key) ->
                val on = b == bucket
                Text(
                    i18n.t(key), color = if (on) DJMetryColors.Text else DJMetryColors.Muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Panel else Color.Transparent).clickable(role = Role.Tab) { onBucket(b) }.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }) {
        Legend(listOf(SeriesColors.Visits to i18n.t(Strings.AN_VISITS), SeriesColors.Unique to i18n.t(Strings.AN_UNIQUE), SeriesColors.Clicks to i18n.t(Strings.AN_CLICKS)))
        if (empty) Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 14.sp)
        } else ActivityChart(points, bucket, height, maxLabels)
    }
}

@Composable
private fun MapCard(map: List<MapCountry>, breakdown: com.djmetry.api.models.BreakdownResponse?, mapHeight: Dp, rows: Int, countryName: (String) -> String) {
    val i18n = useI18n()
    var cities by remember { mutableStateOf(false) }
    AnalyticsCard(i18n.t(Strings.AN_WHERE), trailing = {
        Row(Modifier.clip(CircleShape).background(DJMetryColors.Background).padding(3.dp)) {
            listOf(false to Strings.AN_COUNTRIES, true to Strings.AN_CITIES).forEach { (c, key) ->
                val on = c == cities
                Text(
                    i18n.t(key),
                    color = if (on) DJMetryColors.Text else DJMetryColors.Muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Panel else Color.Transparent).clickable(role = Role.Tab) { cities = c }.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }) {
        MapPreview(map, mapHeight, countryName)
        val list: List<BreakdownRow> = if (cities) breakdown?.cities.orEmpty() else breakdown?.countries.orEmpty()
        if (list.isEmpty()) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        val max = list.maxOfOrNull { it.visits }?.coerceAtLeast(1) ?: 1
        list.take(rows).forEach { row ->
            val iso = row.country_code?.uppercase()
            BarRow(
                label = if (cities) row.city.orEmpty() else iso?.let(countryName).orEmpty(),
                value = groupThousands(row.visits), fraction = row.visits.toFloat() / max, color = GreenBar,
                leading = iso?.let { flagEmoji(it) },
            )
        }
    }
}

@Composable
private fun ClicksCard(network: NetworkResponse) {
    val i18n = useI18n()
    val rows = remember(network) { groupedClicks(network.click_breakdown) }
    AnalyticsCard(i18n.t(Strings.AN_CLICKS_BY)) {
        if (rows.isEmpty()) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        rows.take(5).forEach { (label, count) ->
            BarRow(
                label = when (label) { is ClickLabel.Text -> label.text; is ClickLabel.Key -> i18n.t(label.key) },
                value = groupThousands(count), fraction = count.toFloat() / max, color = BlueBar,
            )
        }
    }
}

/** Кто из DJMetry смотрел страницу: гости, артисты, букинг-компании, остальные юзеры — одной полосой + топ зрителей. */
@Composable
private fun NetworkCard(network: NetworkResponse) {
    val i18n = useI18n()
    val s = remember(network) { networkSplit(network) }
    val parts = listOf(
        Triple(Strings.AN_NET_ANON, s.anonymous, DJMetryColors.Muted.copy(alpha = 0.55f)),
        Triple(Strings.AN_NET_OTHER, s.otherLogged, DJMetryColors.Accent),
        Triple(Strings.AN_NET_ARTISTS, s.artists, SeriesColors.Artists),
        Triple(Strings.AN_NET_BOOKING, s.booking, SeriesColors.Clicks),
    )
    AnalyticsCard(i18n.t(Strings.AN_NETWORK)) {
        if (s.total == 0) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        else Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.second > 0 }.forEach { (_, v, c) -> Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(c)) }
        }
        parts.forEach { (key, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                Text(i18n.t(key), color = DJMetryColors.Text, fontSize = 13.5.sp, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(groupThousands(v), color = DJMetryColors.Muted, fontSize = 13.sp)
            }
        }
        if (network.top_viewers.isNotEmpty()) {
            Text(i18n.t(Strings.AN_TOP_VIEWERS).uppercase(), color = DJMetryColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
            network.top_viewers.take(3).forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v.segment == "booking_company") Icons.Outlined.Business else Icons.Outlined.MusicNote, null, tint = if (v.segment == "booking_company") SeriesColors.Clicks else SeriesColors.Artists, modifier = Modifier.size(16.dp))
                    Text(v.display_label ?: "—", color = DJMetryColors.Text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text(groupThousands(v.page_views), color = DJMetryColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DevicesCard(breakdown: com.djmetry.api.models.BreakdownResponse?) {
    val i18n = useI18n()
    var tab by remember { mutableStateOf(0) }
    val rows = when (tab) { 0 -> breakdown?.devices; 1 -> breakdown?.browsers; else -> breakdown?.os }.orEmpty()
    val data = remember(rows) { shares(rows) }
    AnalyticsCard(i18n.t(listOf(Strings.AN_DEVICES, Strings.AN_BROWSERS, Strings.AN_OS)[tab]), trailing = {
        Row(Modifier.clip(CircleShape).background(DJMetryColors.Background).padding(3.dp)) {
            listOf(Icons.Outlined.Devices, Icons.Outlined.Language, Icons.Outlined.Memory).forEachIndexed { i, icon ->
                Icon(
                    icon, null, tint = if (i == tab) DJMetryColors.Text else DJMetryColors.Muted,
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(if (i == tab) DJMetryColors.Panel else Color.Transparent).clickable(role = Role.Tab) { tab = i }.padding(7.dp),
                )
            }
        }
    }) {
        if (data.isEmpty()) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(data.mapIndexed { i, s -> s.fraction.toFloat() to ShareColors[i % ShareColors.size] }, ctrLabel(data.first().fraction * 100), 104.dp)
            Column(Modifier.weight(1f)) {
                data.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(ShareColors[i % ShareColors.size]))
                        Text(deviceKey(s.label)?.let { i18n.t(it) } ?: s.label, color = DJMetryColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(ctrLabel(s.fraction * 100), color = DJMetryColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Источники трафика: реферер или utm_source; «direct» — прямые заходы. CTR посчитан бэкендом. */
@Composable
private fun SourcesCard(breakdown: com.djmetry.api.models.BreakdownResponse?) {
    val i18n = useI18n()
    val rows = breakdown?.referrals.orEmpty().take(5)
    AnalyticsCard(i18n.t(Strings.AN_SOURCES)) {
        if (rows.isEmpty()) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        val max = rows.maxOfOrNull { it.visits }?.coerceAtLeast(1) ?: 1
        rows.forEach { r ->
            BarRow(
                label = r.referral?.takeUnless { it == "direct" } ?: i18n.t(Strings.AN_DIRECT),
                value = "${groupThousands(r.visits)} · ${r.ctr_display_label ?: ctrLabel(r.ctr)}", fraction = r.visits.toFloat() / max, color = GreenBar,
            )
        }
    }
}

/** Скелетон той же раскладки: плитки, график, карта, списки. */
@Composable
private fun AnalyticsSkeleton(width: Float) {
    val columns = if (width >= KPI_ROW_MIN_DP) 4 else 2
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4 / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(columns) { SkeletonBox(Modifier.weight(1f).height(68.dp), RoundedCornerShape(18.dp)) } }
        }
        SkeletonCard(if (width >= KPI_ROW_MIN_DP) 280.dp else 240.dp)
        SkeletonCard(if (width >= KPI_ROW_MIN_DP) 300.dp else 230.dp)
        repeat(2) { SkeletonCard(160.dp) }
    }
}

@Composable
private fun SkeletonCard(height: Dp) {
    Column(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonLine(0.35f, 14.dp)
        SkeletonBox(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(14.dp))
    }
}
