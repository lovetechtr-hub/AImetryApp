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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.AudienceLead
import com.djmetry.api.models.AudiencePerson
import com.djmetry.api.models.AudienceSegment
import com.djmetry.config.AppConfig
import com.djmetry.data.analytics.*
import com.djmetry.data.repository.AudienceBlock
import com.djmetry.data.repository.AudienceBlockedException
import com.djmetry.data.repository.AudienceOverview
import com.djmetry.data.repository.AudienceRepository
import com.djmetry.files.platformFileSaver
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CountryFlag
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Сайт: там аудитория работает всегда (пока бэкенд не принимает токен приложения). */
internal const val AUDIENCE_WEB_URL = "${AppConfig.BASE_URL}/dashboard/music/analytics"

/** Цвет и ключ подписи плитки сегмента фанов. */
internal fun fanColor(fan: FanSegment): Color = when (fan) {
    FanSegment.SuperFan -> DJMetryColors.Accent
    FanSegment.Casual -> DJMetryColors.Accent2
    FanSegment.Cold -> DJMetryColors.Muted
    FanSegment.Fading -> DJMetryColors.MediumScore
    FanSegment.Former -> DJMetryColors.LowScore
}

internal fun fanLabel(fan: FanSegment): String = when (fan) {
    FanSegment.SuperFan -> Strings.AUD_SUPER
    FanSegment.Casual -> Strings.AUD_CASUAL
    FanSegment.Cold -> Strings.AUD_COLD
    FanSegment.Fading -> Strings.AUD_FADING
    FanSegment.Former -> Strings.AUD_FORMER
}

private fun fanIcon(fan: FanSegment): ImageVector = when (fan) {
    FanSegment.SuperFan -> Icons.Outlined.StarOutline
    FanSegment.Casual -> Icons.Outlined.HowToReg
    FanSegment.Cold -> Icons.Outlined.AcUnit
    FanSegment.Fading -> Icons.AutoMirrored.Outlined.TrendingDown
    FanSegment.Former -> Icons.Outlined.PersonOff
}

/** Колонки плиток воронки: в ряд, если каждой хватает ~110dp, иначе 3 + 2. */
internal fun funnelColumns(widthDp: Float): Int = if (widthDp >= 600f) 5 else 3

/**
 * «Аудитория», вариант B «Воронка фанов» (design/analytics/audience-variants.html): выбор сегмента и экспорт CSV,
 * пять плиток сегментов фанов (тап — люди этой плитки), список людей, лиды с фильтром источника и карта стран.
 * Телефон — колонка; планшет портрет — люди и лиды рядом, карта ниже; альбом и десктоп — три колонки.
 */
@Composable
internal fun AudienceContent(scope: AudienceScope, width: Float, countryName: (String) -> String) {
    val repo = LocalAppContainer.current.audience
    var segments by remember(scope) { mutableStateOf<List<AudienceSegment>>(emptyList()) }
    var segment by remember(scope) { mutableStateOf<AudienceSegment?>(null) }
    val filters: JsonElement = segment?.filters ?: JsonArray(emptyList())
    var overview by remember(scope) { mutableStateOf<AudienceOverview?>(null) }
    var overviewKey by remember(scope) { mutableStateOf<JsonElement?>(null) }
    var error by remember(scope) { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var fan by remember(scope) { mutableStateOf<FanSegment?>(FanSegment.SuperFan) }
    var emailsHidden by remember(scope) { mutableStateOf(false) }

    LaunchedEffect(scope, attempt) {
        repo.segments(scope).onSuccess { list -> segments = orderSegments(list); if (segment == null) segment = segments.firstOrNull() }
    }
    LaunchedEffect(scope, filters, attempt) {
        error = null
        repo.overview(scope, filters, refresh = attempt > 0)
            .onSuccess { overview = it; overviewKey = filters }
            .onFailure { error = it }
    }

    val blocked = (error as? AudienceBlockedException)?.block
    when {
        blocked == AudienceBlock.WebOnly -> AudienceMessage(Strings.AUD_WEB_ONLY, openSite = true)
        blocked == AudienceBlock.NoAccess -> AudienceMessage(Strings.AUD_NO_ACCESS)
        error != null && overview == null -> AudienceMessage(Strings.AN_ERROR, retry = { attempt++ })
        else -> LoadingCrossfade(loading = overview == null, skeleton = { AudienceSkeleton(width) }) {
            val o = overview ?: return@LoadingCrossfade
            val dim by animateFloatAsState(if (overviewKey == filters) 1f else 0.5f, label = "dim")
            Column(Modifier.alpha(dim), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Controls(segments, segment, o.total, width, emailsHidden, onSegment = { segment = it }) {
                    repo.exportCsv(scope, filters)
                }
                FunnelTiles(o, fan, funnelColumns(width)) { fan = if (fan == it) null else it }
                val people: @Composable (Modifier) -> Unit = { m -> PeopleCard(repo, scope, filters, fan, o, m) }
                val leads: @Composable (Modifier) -> Unit = { m -> LeadsCard(repo, m) { emailsHidden = it } }
                val map: @Composable (Modifier, Dp, Int) -> Unit = { m, h, rows -> CountriesCard(o, h, rows, countryName, m) }
                when {
                    width >= TWO_COLUMNS_MIN_DP -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        people(Modifier.weight(1.3f)); leads(Modifier.weight(1f)); map(Modifier.weight(1f), 170.dp, 5)
                    }
                    width >= KPI_ROW_MIN_DP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { people(Modifier.weight(1.2f)); leads(Modifier.weight(1f)) }
                        map(Modifier, 240.dp, 5)
                    }
                    else -> { people(Modifier); leads(Modifier); map(Modifier, 190.dp, 4) }
                }
            }
        }
    }
}

@Composable
private fun AudienceMessage(key: String, openSite: Boolean = false, retry: (() -> Unit)? = null) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Groups, null, tint = DJMetryColors.Muted, modifier = Modifier.size(36.dp))
        Text(i18n.t(key), color = DJMetryColors.Text, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (openSite) PrimaryButton(Icons.AutoMirrored.Outlined.OpenInNew, i18n.t(Strings.AUD_OPEN_SITE)) { uri.openUri(AUDIENCE_WEB_URL) }
        retry?.let { TextButton(onClick = it) { Text(i18n.t(Strings.AN_RETRY), color = DJMetryColors.Accent, fontWeight = FontWeight.SemiBold) } }
    }
}

// ── Сегмент и экспорт ───────────────────────────────────────────────────────

@Composable
private fun segmentTitle(s: AudienceSegment?): String {
    val i18n = useI18n()
    return when {
        s == null || s.preset_key == "everyone" -> i18n.t(Strings.AUD_EVERYONE)
        else -> presetBrand(s.preset_key) ?: s.name
    }
}

@Composable
private fun Controls(
    segments: List<AudienceSegment>, segment: AudienceSegment?, total: Int, width: Float, emailsHidden: Boolean,
    onSegment: (AudienceSegment) -> Unit, export: suspend () -> Result<String>,
) {
    val picker: @Composable (Modifier) -> Unit = { m -> SegmentPicker(segments, segment, total, onSegment, m) }
    val exportButton: @Composable () -> Unit = { ExportButton(export) }
    if (width >= KPI_ROW_MIN_DP) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            picker(Modifier.widthIn(min = 240.dp, max = 320.dp))
            Spacer(Modifier.weight(1f))
            if (emailsHidden && width >= TWO_COLUMNS_MIN_DP) EmailsHiddenBanner(Modifier.widthIn(max = 460.dp))
            exportButton()
        }
        if (emailsHidden && width < TWO_COLUMNS_MIN_DP) EmailsHiddenBanner(Modifier.fillMaxWidth())
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            picker(Modifier.weight(1f))
            exportButton()
        }
        if (emailsHidden) EmailsHiddenBanner(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SegmentPicker(segments: List<AudienceSegment>, segment: AudienceSegment?, total: Int, onSegment: (AudienceSegment) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel)
                .border(1.dp, DJMetryColors.Border, RoundedCornerShape(22.dp)).clickable(role = Role.DropdownList, enabled = segments.size > 1) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Group, null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Text(segmentTitle(segment), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(groupThousands(total), color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, tint = DJMetryColors.Muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            segments.forEach { s ->
                DropdownMenuItem(
                    text = { Text(segmentTitle(s), color = if (s.id == segment?.id) DJMetryColors.Accent else DJMetryColors.Text, fontWeight = if (s.id == segment?.id) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(if (s.is_preset) Icons.Outlined.Group else Icons.Outlined.BookmarkBorder, null, tint = DJMetryColors.Muted) },
                    onClick = { open = false; onSegment(s) },
                )
            }
        }
    }
}

@Composable
private fun ExportButton(export: suspend () -> Result<String>) {
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    val saver = remember { platformFileSaver() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(status) { if (status != null) { delay(2500); status = null } }
    PrimaryButton(if (status == null) Icons.Outlined.Download else Icons.Outlined.Check, status ?: i18n.t(Strings.AUD_EXPORT), busy = busy) {
        if (busy) return@PrimaryButton
        busy = true
        scope.launch {
            val saved = export().map { csv -> saver.save("djmetry-audience.csv", "text/csv", csv.encodeToByteArray()) }
            status = when {
                saved.isFailure -> i18n.t(Strings.AUD_EXPORT_FAIL)
                saved.getOrNull() == true -> i18n.t(Strings.AUD_EXPORT_OK)
                else -> null
            }
            busy = false
        }
    }
}

@Composable
private fun PrimaryButton(icon: ImageVector, text: String, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Accent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = DJMetryColors.Background, strokeWidth = 2.dp)
        else Icon(icon, null, tint = DJMetryColors.Background, modifier = Modifier.size(18.dp))
        Text(text, color = DJMetryColors.Background, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EmailsHiddenBanner(modifier: Modifier) {
    val i18n = useI18n()
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(DJMetryColors.MediumScore.copy(alpha = 0.1f))
            .border(1.dp, DJMetryColors.MediumScore.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Lock, null, tint = DJMetryColors.MediumScore, modifier = Modifier.size(20.dp))
        Text(i18n.t(Strings.AUD_EMAILS_HIDDEN), color = DJMetryColors.Text, fontSize = 12.5.sp)
    }
}

// ── Воронка ─────────────────────────────────────────────────────────────────

@Composable
private fun FunnelTiles(o: AudienceOverview, selected: FanSegment?, columns: Int, onTap: (FanSegment) -> Unit) {
    val i18n = useI18n()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FanSegment.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { fan ->
                    val on = fan == selected
                    val color = fanColor(fan)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) color.copy(alpha = 0.12f) else DJMetryColors.Panel)
                            .border(if (on) 1.5.dp else 1.dp, if (on) color else Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                            .clickable(role = Role.Tab) { onTap(fan) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(fanIcon(fan), null, tint = color, modifier = Modifier.size(14.dp))
                            AutoSizeText(i18n.t(fanLabel(fan)), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = color, minFontSize = 9.sp)
                        }
                        AutoSizeText(groupThousands(o.funnel[fan] ?: 0), TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 14.sp)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Люди ────────────────────────────────────────────────────────────────────

@Composable
private fun PeopleCard(repo: AudienceRepository, scope: AudienceScope, filters: JsonElement, fan: FanSegment?, o: AudienceOverview, modifier: Modifier) {
    val i18n = useI18n()
    val co = rememberCoroutineScope()
    val people = remember(scope, filters, fan) { mutableStateListOf<AudiencePerson>() }
    var total by remember(scope, filters, fan) { mutableStateOf(fan?.let { o.funnel[it] } ?: o.total) }
    var loading by remember(scope, filters, fan) { mutableStateOf(true) }
    var failed by remember(scope, filters, fan) { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var all by remember(scope, filters, fan) { mutableStateOf<com.djmetry.ui.components.PagedList<AudiencePerson>?>(null) }
    LaunchedEffect(scope, filters, fan, attempt) {
        loading = true; failed = false
        repo.people(scope, filters, fan, 1).onSuccess { people.clear(); people.addAll(it.items); total = it.total }.onFailure { failed = true }
        loading = false
    }
    val title = (fan?.let { i18n.t(fanLabel(it)) } ?: i18n.t(Strings.AUD_PEOPLE)) + " · " + groupThousands(total)
    AudienceCard(title, modifier, trailing = { Text("fan score", color = DJMetryColors.Muted, fontSize = 12.sp) }) {
        when {
            loading -> repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(44.dp)) }
            failed -> RetryNote { attempt++ }
            people.isEmpty() -> Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
            // В карточке — только первая страница; все — в ленивом списке во весь экран
            else -> people.forEach { PersonRow(it) }
        }
        if (!loading && !failed && people.size < total) ShowMore(total) {
            all = com.djmetry.ui.components.PagedList(people.toList(), total) { p -> repo.people(scope, filters, fan, p).map { it.items } }
        }
    }
    all?.let { list -> com.djmetry.ui.components.PagedListDialog(title, list, onClose = { all = null }) { PersonRow(it) } }
}

@Composable
private fun PersonRow(p: AudiencePerson) {
    val i18n = useI18n()
    val name = p.display_name ?: p.artist_name ?: p.email ?: i18n.t(Strings.AUD_ANON)
    val fan = FanSegment.of(p.fan_segment)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonAvatar(p.avatar_url, name)
        Column(Modifier.weight(1f)) {
            Text(name, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = p.service?.let(::platformTitle).orEmpty()
            if (sub.isNotEmpty()) Text(sub, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        p.country?.takeIf { it.length == 2 }?.let { CountryFlag(it, 20.dp) }
        Box(Modifier.size(10.dp).clip(CircleShape).background(fanColor(fan)))
        Text(p.fan_score.toString(), color = fanColor(fan), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 28.dp))
    }
}

/** Ключ платформы бэкенда → название бренда (не переводится). */
internal fun platformTitle(key: String): String = when (key.lowercase()) {
    "spotify" -> "Spotify"
    "apple_music" -> "Apple Music"
    "youtube_music" -> "YouTube Music"
    "youtube" -> "YouTube"
    "deezer" -> "Deezer"
    "soundcloud" -> "SoundCloud"
    "tidal" -> "Tidal"
    "beatport" -> "Beatport"
    "email_subscribe" -> "Email"
    "djmetry" -> "DJMetry"
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun PersonAvatar(url: String?, name: String, size: Dp = 38.dp) =
    CoverImage(url, size, cornerRadius = size / 2, placeholderColor = DJMetryColors.PanelStrong) {
        Text(initials(name), color = DJMetryColors.Text, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.8).sp, modifier = Modifier.align(Alignment.Center))
    }

// ── Лиды ────────────────────────────────────────────────────────────────────

@Composable
private fun LeadsCard(repo: AudienceRepository, modifier: Modifier, onEmailsHidden: (Boolean) -> Unit) {
    val i18n = useI18n()
    val co = rememberCoroutineScope()
    var source by remember { mutableStateOf(LeadSource.All) }
    val leads = remember(source) { mutableStateListOf<AudienceLead>() }
    var total by remember(source) { mutableStateOf(0) }
    var loading by remember(source) { mutableStateOf(true) }
    var failed by remember(source) { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var all by remember(source) { mutableStateOf<com.djmetry.ui.components.PagedList<AudienceLead>?>(null) }
    LaunchedEffect(source, attempt) {
        loading = true; failed = false
        repo.leads(source).onSuccess { leads.clear(); leads.addAll(it.items); total = it.total; onEmailsHidden(it.emails_hidden) }.onFailure { failed = true }
        loading = false
    }
    AudienceCard(i18n.t(Strings.AUD_LEADS), modifier, trailing = { Text(groupThousands(total), color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LeadSource.All to Strings.AUD_EVERYONE, LeadSource.Bio to Strings.AUD_BIO, LeadSource.SmartLink to Strings.AUD_SMART, LeadSource.Tour to Strings.AUD_TOUR).forEach { (s, key) ->
                val on = s == source
                Text(
                    i18n.t(key), color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Background)
                        .clickable(role = Role.Tab) { source = s }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        when {
            loading -> repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(44.dp)) }
            failed -> RetryNote { attempt++ }
            leads.isEmpty() -> Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
            else -> leads.forEach { LeadRow(it) }
        }
        if (!loading && !failed && leads.size < total) ShowMore(total) {
            all = com.djmetry.ui.components.PagedList(leads.toList(), total) { p -> repo.leads(source, p).map { it.items } }
        }
    }
    all?.let { list -> com.djmetry.ui.components.PagedListDialog(i18n.t(Strings.AUD_LEADS), list, onClose = { all = null }) { LeadRow(it) } }
}

@Composable
private fun LeadRow(l: AudienceLead) {
    val i18n = useI18n()
    val name = l.display_name ?: l.full_name ?: i18n.t(Strings.AUD_ANON)
    val (icon, key) = when (l.source_type) {
        "smart_link" -> Icons.Outlined.Link to Strings.AUD_SMART
        "tour" -> Icons.Outlined.Place to Strings.AUD_TOUR
        else -> Icons.Outlined.Badge to Strings.AUD_BIO
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonAvatar(l.avatar_url, name)
        Column(Modifier.weight(1f)) {
            Text(name, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(l.email, l.city ?: l.location).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.clip(CircleShape).border(1.dp, DJMetryColors.Border, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = DJMetryColors.Muted, modifier = Modifier.size(14.dp))
            Text(i18n.t(key), color = DJMetryColors.Text, fontSize = 11.5.sp, maxLines = 1)
        }
    }
}

// ── Карта ───────────────────────────────────────────────────────────────────

@Composable
private fun CountriesCard(o: AudienceOverview, mapHeight: Dp, rows: Int, countryName: (String) -> String, modifier: Modifier) {
    val i18n = useI18n()
    val map = remember(o) { audienceMapCountries(o.countries) }
    AudienceCard(i18n.t(Strings.AN_WHERE), modifier) {
        MapPreview(map, mapHeight, countryName)
        if (map.isEmpty()) Text(i18n.t(Strings.AN_EMPTY), color = DJMetryColors.Muted, fontSize = 13.sp)
        val max = map.maxOfOrNull { it.visits }?.coerceAtLeast(1) ?: 1
        map.take(rows).forEach { c ->
            BarRow(label = countryName(c.iso), value = groupThousands(c.visits), fraction = c.visits.toFloat() / max, color = GreenBar, flagIso = c.iso)
        }
    }
}

// ── Общее ───────────────────────────────────────────────────────────────────

@Composable
private fun AudienceCard(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
            Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun ShowMore(total: Int, onClick: () -> Unit) {
    val i18n = useI18n()
    Text(
        "${i18n.t(Strings.AUD_MORE)} · ${groupThousands(total)}", color = DJMetryColors.Accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
    )
}

/** Не загрузилось — не «пусто», а ошибка с «Повторить». */
@Composable
private fun RetryNote(onRetry: () -> Unit) {
    val i18n = useI18n()
    Text(
        "${i18n.t(Strings.HOME_ERROR)} · ${i18n.t(Strings.HOME_RETRY)}", color = DJMetryColors.Accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onRetry).padding(vertical = 10.dp),
    )
}

@Composable
private fun AudienceSkeleton(width: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBox(Modifier.fillMaxWidth(if (width >= KPI_ROW_MIN_DP) 0.3f else 1f).height(44.dp), RoundedCornerShape(22.dp))
        val cols = funnelColumns(width)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(cols) { SkeletonBox(Modifier.weight(1f).height(70.dp), RoundedCornerShape(18.dp)) } }
        SkeletonBox(Modifier.fillMaxWidth().height(320.dp), RoundedCornerShape(22.dp))
    }
}
