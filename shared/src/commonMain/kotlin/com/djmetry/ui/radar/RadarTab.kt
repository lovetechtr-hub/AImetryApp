package com.djmetry.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.djmetry.api.models.ReleaseRadarFeedArtist
import com.djmetry.api.models.ReleaseRadarRelease
import com.djmetry.data.radar.*
import com.djmetry.i18n.Strings
import com.djmetry.i18n.localizedCountryName
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.artist.eventDay
import com.djmetry.ui.artist.monthLabel
import com.djmetry.ui.artist.ticketUrl
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.djmap.LocalOpenDjMap
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Ширина, от которой релизы и концерты стоят рядом (альбом, десктоп). */
internal const val RADAR_TWO_COLUMNS_DP = 1000f

private val Orange = Color(0xFFFFB35B)

/**
 * Вкладка «Радар», вариант A (design/radars/variants.html): поиск артиста в подписках, «истории» — аватары подписок
 * (кольцо — непрочитанный релиз/концерт или концерт в ближайшие 30 дней; тап — вся страница по этому артисту),
 * «Релизы · Концерты». Релизы — секции по артистам с каруселью обложек и «Все · N» (все релизы артиста).
 * Концерты — по месяцам, «Рядом со мной» (город Concert Radar), «На карте» и «Билеты».
 * Широко — релизы слева, концерты справа.
 */
/** Открыть релиз из уведомления (все релизы артиста с прокруткой к нему) — ставит MainShell. */
val LocalOpenRelease = androidx.compose.runtime.staticCompositionLocalOf<(com.djmetry.data.radar.ReleaseOpen) -> Unit> { {} }

@Composable
fun RadarTab(
    openRelease: com.djmetry.data.radar.ReleaseOpen? = null, onOpened: () -> Unit = {},
    openConcert: com.djmetry.ui.profile.ConcertOpen? = null, onConcertOpened: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val repo = container.radar
    val i18n = useI18n()
    var feed by remember { mutableStateOf<List<ReleaseRadarFeedArtist>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var unread by remember { mutableStateOf(emptySet<String>()) }
    val seenScope = rememberCoroutineScope()
    var concerts by remember { mutableStateOf<List<RadarConcert>?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var location by remember { mutableStateOf<RadarLocation?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var showConcerts by remember { mutableStateOf(false) }
    var nearOnly by remember { mutableStateOf(false) }
    var allReleasesOf by remember { mutableStateOf<ReleaseRadarFeedArtist?>(null) }

    LaunchedEffect(attempt) {
        failed = false
        repo.feed(refresh = attempt > 0).onSuccess { feed = it }.onFailure { failed = true }
    }
    LaunchedEffect(Unit) { unread = repo.unreadArtists() }
    // Фото артистов: в ленте релизов бывает пусто — берём из подписок
    val follows by container.discover.follows.collectAsState()
    LaunchedEffect(Unit) { if (follows.isEmpty()) container.discover.refreshMine() }
    val photos = remember(follows) { follows.associate { it.spotifyArtistId to it.imageUrl } }
    // Город пользователя — сразу, параллельно с лентой (раньше ждал её); сменили город в настройках — перечитываем
    val settingsConcert by container.settings.state.collectAsState()
    val cityKey = settingsConcert?.concert?.let { it.effectiveCity to it.effectiveCountry }
    LaunchedEffect(cityKey) {
        location = repo.location { iso -> listOfNotNull(localizedCountryName(iso, "en"), localizedCountryName(iso, i18n.locale.code)) }
    }
    LaunchedEffect(feed, location) {
        val artists = feed ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        // Один список с бэкенда; старый бэкенд — обход подписок
        concerts = repo.serverConcerts() ?: repo.concerts(artists, loc) { done, total -> progress = done to total }
    }

    // Уведомление о релизе: все релизы этого артиста, прокрутка к нужному (артист из ленты, иначе — из уведомления)
    var highlight by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openRelease, feed, failed) {
        val o = openRelease ?: return@LaunchedEffect
        if (feed == null && !failed) return@LaunchedEffect
        allReleasesOf = feed?.firstOrNull { it.spotify_artist_id == o.artistId }
            ?: ReleaseRadarFeedArtist(o.artistId, o.artistName.orEmpty(), o.artistImage ?: photos[o.artistId])
        highlight = o.albumId
        onOpened()
    }

    allReleasesOf?.let { a ->
        LaunchedEffect(a.spotify_artist_id) { if (a.spotify_artist_id in unread) { unread = unread - a.spotify_artist_id; repo.markSeen("release", a.spotify_artist_id) } }
        ArtistReleasesScreen(a, highlight) { allReleasesOf = null; highlight = null }
        return
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val soon = remember(concerts) {
        val limit = today.toEpochDays() + 30
        concerts.orEmpty().filter { c ->
            runCatching { kotlinx.datetime.LocalDate.parse(c.event.datetime.take(10)).toEpochDays() <= limit }.getOrDefault(false)
        }.map { it.artistId }.toSet()
    }
    val artists = feed.orEmpty()
    val stories = remember(artists, unread, soon) { storyOrder(artists, unread, soon) }
        .filter { matchesArtist(it.artist_name, query) }
    val releaseArtists = artists.filter { it.latest.isNotEmpty() && matchesArtist(it.artist_name, query) && (selected == null || it.spotify_artist_id == selected) }
    val shownConcerts = concerts?.filter { c ->
        matchesArtist(c.artistName, query) && (selected == null || c.artistId == selected) && (!nearOnly || c.near)
    }

    // Уведомление о концерте: «Концерты», ждём загрузку, подводим к событию (или ближайшему концерту артиста) и подсвечиваем
    val phoneList = androidx.compose.foundation.lazy.rememberLazyListState()
    val panelList = androidx.compose.foundation.lazy.rememberLazyListState()
    var concertFlash by remember { mutableStateOf<String?>(null) }
    var wideNow by remember { mutableStateOf(false) }
    LaunchedEffect(openConcert, shownConcerts) {
        val o = openConcert ?: return@LaunchedEffect
        showConcerts = true; nearOnly = false; selected = null; query = ""
        val list = shownConcerts ?: return@LaunchedEffect
        onConcertOpened()
        val target = list.firstOrNull { o.eventId != null && it.event.eventId == o.eventId } ?: list.firstOrNull { it.artistId == o.artistId } ?: return@LaunchedEffect
        val index = concertIndex(list, target, headItems = if (wideNow) 1 else 3)
        delay(300)
        val state = if (wideNow) panelList else phoneList
        state.animateScrollToItem(index, -(state.layoutInfo.viewportSize.height / 3))
        concertFlash = target.event.eventId
        delay(4000)
        concertFlash = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val wide = maxWidth.value >= RADAR_TWO_COLUMNS_DP
        SideEffect { wideNow = wide }
        val pad = if (wide) 28.dp else 16.dp
        val header: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AutoSizeText(i18n.t(Strings.TAB_RADARS), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 20.sp)
                SearchField(query, i18n.t(Strings.RADAR_SEARCH)) { query = it }
                if (feed == null && !failed) StoriesSkeleton()
                else if (stories.isNotEmpty()) Stories(stories, unread, soon, selected, photos) { id ->
                    selected = if (selected == id) null else id
                    // Открыли «историю» — «новое» у артиста погашено
                    if (id in unread) { unread = unread - id; seenScope.launch { repo.markSeen("all", id) } }
                }
            }
        }
        val concertsInto: (LazyListScope, Boolean) -> Unit = { lazy, inPanel ->
            concertItems(lazy, shownConcerts, progress, location, nearOnly, onNear = { nearOnly = !nearOnly }, anyConcerts = !concerts.isNullOrEmpty(), panel = inPanel, photos = photos, flash = concertFlash)
        }

        when {
            failed && feed == null -> Column(Modifier.fillMaxSize().padding(horizontal = pad).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                header()
                Message(i18n.t(Strings.HOME_ERROR), action = i18n.t(Strings.HOME_RETRY)) { attempt++ }
            }
            feed != null && artists.isEmpty() -> Column(Modifier.fillMaxSize().padding(horizontal = pad).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                header()
                Message(i18n.t(Strings.RADAR_EMPTY))
            }
            wide -> Row(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(start = pad, end = pad, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { header() }
                    releaseItems(this, feed == null, releaseArtists, today, cardSize = 140.dp) { allReleasesOf = it }
                }
                LazyColumn(
                    Modifier.width(400.dp).fillMaxHeight(), state = panelList,
                    contentPadding = PaddingValues(top = 52.dp, bottom = LocalBottomClearance.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { concertsInto(this, true) }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), state = phoneList,
                contentPadding = PaddingValues(start = pad, end = pad, top = 12.dp, bottom = LocalBottomClearance.current + 16.dp),
                // Концерты — плотнее (8 dp), релизы — 16 dp
                verticalArrangement = Arrangement.spacedBy(if (showConcerts) 8.dp else 16.dp),
            ) {
                item { header() }
                item {
                    Box(Modifier.padding(vertical = if (showConcerts) 8.dp else 0.dp)) {
                        Segments(showConcerts, releases = releaseArtists.size, concertsCount = shownConcerts?.size) { showConcerts = it }
                    }
                }
                if (!showConcerts) releaseItems(this, feed == null, releaseArtists, today, cardSize = 118.dp) { allReleasesOf = it }
                else concertsInto(this, false)
            }
        }
    }
}

// ── Шапка: поиск, «истории», сегменты ──────────────────────────────────────

@Composable
internal fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(DJMetryColors.Panel)
            .border(1.dp, DJMetryColors.Border, RoundedCornerShape(23.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = DJMetryColors.Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = DJMetryColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true, cursorBrush = SolidColor(DJMetryColors.Accent),
                textStyle = TextStyle(color = DJMetryColors.Text, fontSize = 15.sp), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Outlined.Close, null, tint = DJMetryColors.Muted,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") }.padding(4.dp),
        )
    }
}

@Composable
private fun Stories(artists: List<ReleaseRadarFeedArtist>, unread: Set<String>, soon: Set<String>, selected: String?, photos: Map<String, String?>, onTap: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { it.spotify_artist_id }) { a ->
            val lit = a.spotify_artist_id in unread || a.spotify_artist_id in soon
            val on = selected == a.spotify_artist_id
            Column(
                Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onTap(a.spotify_artist_id) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Кольцо: выбран — сплошное зелёное, есть новое — градиент, иначе тонкое
                val ring: Brush = when {
                    on -> SolidColor(DJMetryColors.Accent)
                    lit -> Brush.linearGradient(listOf(DJMetryColors.Accent, DJMetryColors.Accent2))
                    else -> SolidColor(DJMetryColors.Border)
                }
                Box(Modifier.size(64.dp).clip(CircleShape).background(ring).padding(if (lit || on) 3.dp else 1.5.dp).clip(CircleShape).background(DJMetryColors.Background).padding(2.dp)) {
                    CoverImage(a.artist_image_url ?: photos[a.spotify_artist_id], 56.dp, cornerRadius = 28.dp)
                }
                Text(
                    a.artist_name, color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontSize = 11.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StoriesSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(5) { SkeletonBox(Modifier.size(64.dp), CircleShape) } }
}

@Composable
private fun Segments(showConcerts: Boolean, releases: Int, concertsCount: Int?, onSelect: (Boolean) -> Unit) {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).padding(4.dp)) {
        listOf(
            Triple(false, Icons.Outlined.Album, "${i18n.t(Strings.RADAR_RELEASES)} · $releases"),
            Triple(true, Icons.Outlined.Event, i18n.t(Strings.RADAR_CONCERTS) + (concertsCount?.let { " · $it" } ?: "")),
        ).forEach { (value, icon, label) ->
            val on = value == showConcerts
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) DJMetryColors.Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (on) DJMetryColors.Background else DJMetryColors.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                AutoSizeText(label, TextStyle(fontSize = 14.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) DJMetryColors.Background else DJMetryColors.Muted, minFontSize = 10.sp)
            }
        }
    }
}

// ── Релизы ─────────────────────────────────────────────────────────────────

private fun releaseItems(
    scope: androidx.compose.foundation.lazy.LazyListScope, loading: Boolean, artists: List<ReleaseRadarFeedArtist>,
    today: kotlinx.datetime.LocalDate, cardSize: Dp, onAll: (ReleaseRadarFeedArtist) -> Unit,
) = with(scope) {
    if (loading) {
        items(3) { ReleaseSectionSkeleton(cardSize) }
        return@with
    }
    if (artists.isEmpty()) item { EmptyNote(Strings.RADAR_NO_RELEASES) }
    items(artists, key = { "rel-" + it.spotify_artist_id }) { a -> ReleaseSection(a, today, cardSize) { onAll(a) } }
}

@Composable
private fun ReleaseSection(a: ReleaseRadarFeedArtist, today: kotlinx.datetime.LocalDate, cardSize: Dp, onAll: () -> Unit) {
    val i18n = useI18n()
    val openArtist = LocalArtistNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { openArtist(a.spotify_artist_id) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoverImage(a.artist_image_url, 34.dp, cornerRadius = 17.dp)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(a.artist_name, color = DJMetryColors.Text, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (a.showing_older) Text(i18n.t(Strings.RADAR_NO_NEW), color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (hasMoreReleases(a)) Text(
                i18n.tWithArgs(Strings.RADAR_ALL, arrayOf(a.total_releases)), color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAll).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(a.latest, key = { it.album_id }) { r -> ReleaseCard(r, today, cardSize) }
        }
    }
}

/** Карточка релиза: обложка, название, тип и дата, NEW — за последние 2 недели. Тап — релиз в Spotify. */
@Composable
internal fun ReleaseCard(r: ReleaseRadarRelease, today: kotlinx.datetime.LocalDate, size: Dp, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    Column(
        modifier.width(size).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, enabled = r.url != null) { r.url?.let { runCatching { uri.openUri(it) } } },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            CoverImage(r.image_url, size, cornerRadius = 14.dp)
            // NEW — на обложке, чтобы строка «тип · дата» помещалась в узкую карточку
            if (isFreshRelease(r.release_date, today)) Text(
                "NEW", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = DJMetryColors.Background,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(DJMetryColors.Accent).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(r.name, color = DJMetryColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val album = r.album_type == "album"
            Text(
                i18n.t(releaseTypeKey(r.album_type)).uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                color = if (album) DJMetryColors.Accent else DJMetryColors.Accent2,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background((if (album) DJMetryColors.Accent else DJMetryColors.Accent2).copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Text(releaseDateLabel(r.release_date), color = DJMetryColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun releaseTypeKey(type: String?): String = when (type) {
    "album" -> Strings.RADAR_ALBUM
    "compilation" -> Strings.RADAR_COMPILATION
    else -> Strings.RADAR_SINGLE
}

/** «6 авг», «6 авг 2025», «авг 2026», «2026» — даты Spotify бывают неполными; текущий год не пишем. */
@Composable
internal fun releaseDateLabel(date: String?): String {
    val months = useI18n().t(Strings.MONTHS_SHORT)
    val thisYear = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).year.toString() }
    val parts = date.orEmpty().split('-')
    val year = parts.getOrNull(0).orEmpty()
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    return when {
        month != null && day != null -> "$day ${monthLabel(months, month)}" + if (year == thisYear) "" else " $year"
        month != null -> "${monthLabel(months, month)} $year"
        else -> year
    }
}

@Composable
private fun ReleaseSectionSkeleton(cardSize: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(Modifier.size(34.dp), CircleShape); SkeletonBox(Modifier.size(140.dp, 16.dp), RoundedCornerShape(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBox(Modifier.size(cardSize), RoundedCornerShape(14.dp)) } }
    }
}

// ── Концерты ───────────────────────────────────────────────────────────────

/**
 * Концерты — отдельными элементами ленивого списка (шапка, месяц, строка): у подписок бывает 1000+ концертов,
 * одним блоком Compose собирал их все сразу, и прокрутка вставала.
 */
/** Позиция концерта в ленивом списке: [headItems] элементов сверху, затем по месяцу — заголовок и строки (как в [concertItems]). */
internal fun concertIndex(list: List<RadarConcert>, target: RadarConcert, headItems: Int): Int {
    var i = headItems
    groupByMonth(list).forEach { (_, rows) ->
        i++ // заголовок месяца
        val k = rows.indexOf(target)
        if (k >= 0) return i + k
        i += rows.size
    }
    return headItems
}

private fun concertItems(
    scope: LazyListScope, concerts: List<RadarConcert>?, progress: Pair<Int, Int>, location: RadarLocation?, nearOnly: Boolean,
    onNear: () -> Unit, anyConcerts: Boolean, panel: Boolean, photos: Map<String, String?> = emptyMap(), flash: String? = null,
) = with(scope) {
    item(key = "concerts-head") {
        val i18n = useI18n()
        val openMap = LocalOpenDjMap.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (panel) Text(i18n.t(Strings.RADAR_SOON), color = DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Города нет — не «мёртвая» кнопка: открываем настройку Concert Radar
                val openSettings = com.djmetry.ui.settings.LocalOpenSettings.current
                val setCity = { openSettings(com.djmetry.ui.settings.SettingsPage.Concert) }
                Chip(Icons.Outlined.NearMe, i18n.t(Strings.RADAR_NEAR), on = nearOnly, enabled = true, onClick = if (location?.known == false) setCity else onNear)
                Spacer(Modifier.weight(1f))
                Chip(Icons.Outlined.Map, i18n.t(Strings.RADAR_MAP), on = false, onClick = { openMap(null) })
            }
            // Города нет — понятная карточка с кнопкой: переход в Concert Radar, где задаются страна и город
            if (location != null && !location.known) {
                val openSettings = com.djmetry.ui.settings.LocalOpenSettings.current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Accent.copy(alpha = 0.08f))
                        .border(1.dp, DJMetryColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.LocationOn, null, tint = DJMetryColors.Accent, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(i18n.t(Strings.RADAR_CITY_MSG), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            i18n.t(Strings.RADAR_CITY_BTN), color = DJMetryColors.Background, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Accent)
                                .clickable(role = Role.Button) { openSettings(com.djmetry.ui.settings.SettingsPage.Concert) }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
    when {
        concerts == null -> item(key = "concerts-loading") {
            val i18n = useI18n()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress.second > 0) Text(i18n.tWithArgs(Strings.RADAR_SEARCHING, arrayOf(progress.first, progress.second)), color = DJMetryColors.Muted, fontSize = 13.sp)
                repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(20.dp)) }
            }
        }
        concerts.isEmpty() -> item(key = "concerts-empty") { EmptyNote(if (nearOnly && anyConcerts) Strings.RADAR_NO_NEAR else Strings.RADAR_NO_CONCERTS) }
        else -> groupByMonth(concerts).forEach { (ym, list) ->
            item(key = "month-$ym") {
                val months = useI18n().t(Strings.MONTHS_SHORT)
                val month = ym.substringAfter('-').toIntOrNull() ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("${monthLabel(months, month).uppercase()} ${ym.take(4)}", color = DJMetryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                    Box(Modifier.padding(start = 10.dp).weight(1f).height(1.dp).background(DJMetryColors.Border))
                }
            }
            itemsIndexed(list, key = { i, c -> "c-$ym-$i-${c.artistId}-${c.event.datetime}" }) { _, c ->
                // Открыли из уведомления — рамка на несколько секунд
                val glow by androidx.compose.animation.core.animateFloatAsState(if (flash == c.event.eventId) 1f else 0f, androidx.compose.animation.core.tween(500), label = "flash")
                Box(Modifier.border(3.dp * glow, DJMetryColors.Accent.copy(alpha = glow), RoundedCornerShape(22.dp))) {
                    ConcertRow(c, compact = false, photo = c.artistImage ?: photos[c.artistId])
                }
            }
        }
    }
}

/** Концерт: дата, артист (тап — карточка), город · площадка, «Рядом», «На карте» (тур артиста), «Билеты». */
@Composable
private fun ConcertRow(c: RadarConcert, compact: Boolean, photo: String?) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val openArtist = LocalArtistNavigator.current
    val openMap = LocalOpenDjMap.current
    val day = eventDay(c.event.datetime)
    val months = i18n.t(Strings.MONTHS_SHORT)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)).clickable(role = Role.Button) { openArtist(c.artistId) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(48.dp).clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Background).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day?.day?.toString() ?: "—", color = DJMetryColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)
            Text(day?.let { monthLabel(months, it.month).uppercase() }.orEmpty(), color = Orange, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
        }
        CoverImage(photo, 42.dp, cornerRadius = 21.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.artistName, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.near) Text(
                    i18n.t(Strings.RADAR_NEAR_BADGE), color = Orange, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Orange.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            val place = listOfNotNull(c.event.venue?.city, c.event.venue?.name).joinToString(" · ")
            Text(place, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SquareButton(Icons.Outlined.Map, i18n.t(Strings.RADAR_MAP), DJMetryColors.Accent2.copy(alpha = 0.14f), DJMetryColors.Accent2) { openMap(c.artistId) }
        if (!compact) ticketUrl(c.event)?.let { url ->
            SquareButton(Icons.Outlined.ConfirmationNumber, i18n.t(Strings.MAP_TICKETS), DJMetryColors.Accent, DJMetryColors.Background) { runCatching { uri.openUri(url) } }
        }
    }
}

@Composable
private fun SquareButton(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Panel)
            .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val color = when { !enabled -> DJMetryColors.Muted.copy(alpha = 0.5f); on -> DJMetryColors.Accent; else -> DJMetryColors.Text }
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun EmptyNote(key: String) {
    Text(useI18n().t(key), color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.Radar, null, tint = DJMetryColors.Muted, modifier = Modifier.size(40.dp))
        Text(text, color = DJMetryColors.Text, fontSize = 15.sp, textAlign = TextAlign.Center)
        action?.let {
            Text(it, color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onAction).padding(10.dp))
        }
    }
}
