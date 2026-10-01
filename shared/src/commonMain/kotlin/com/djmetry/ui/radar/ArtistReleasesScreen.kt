package com.djmetry.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ReleaseRadarFeedArtist
import com.djmetry.api.models.ReleaseRadarRelease
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Сортировка всех релизов артиста — ключи бэкенда. */
internal enum class ReleaseSort(val key: String, val label: String) {
    New("date_desc", Strings.RADAR_SORT_NEW), Old("date_asc", Strings.RADAR_SORT_OLD), Name("name", Strings.RADAR_SORT_NAME)
}

/**
 * «Все релизы артиста» (как «Показать все» на сайте): поиск по названию, сортировка, сетка обложек,
 * догрузка по 24 при прокрутке (`/me/release-radar/artist/:id/releases`).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ArtistReleasesScreen(artist: ReleaseRadarFeedArtist, highlight: String? = null, onBack: () -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.radar
    val openArtist = LocalArtistNavigator.current
    androidx.compose.ui.backhandler.BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ReleaseSort.New) }
    val pages = remember(query, sort) { ReleasePages { offset -> repo.artistReleases(artist.spotify_artist_id, query, sort.key, offset) } }
    val releases = pages.items
    val total = pages.total
    val grid = rememberLazyGridState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    LaunchedEffect(pages) {
        delay(if (query.isEmpty()) 0 else 300) // поиск — после паузы в наборе
        pages.loadNext()
    }
    // Открыли из уведомления: догружаем страницы (тем же загрузчиком), пока не найдём релиз, и плавно подводим к нему
    var flash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlight, pages) {
        val id = highlight ?: return@LaunchedEffect
        val i = pages.loadUntil(id) ?: return@LaunchedEffect
        delay(250)
        // +1 — шапка; релиз — не у самого края, а в верхней трети экрана
        grid.animateScrollToItem(i + 1, scrollOffset = -(grid.layoutInfo.viewportSize.height / 3))
        flash = id
        delay(4000)
        flash = null
    }
    // Догрузка: до конца сетки меньше 6 карточек. Пересчёт и после каждой страницы — если она целиком влезла
    // в экран, следующая грузится сразу, а не ждёт прокрутки
    LaunchedEffect(pages) {
        snapshotFlow {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (pages.total != null && last >= grid.layoutInfo.totalItemsCount - 6) to pages.items.size
        }.collect { (nearEnd, _) -> if (nearEnd) pages.loadNext() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val card = if (maxWidth.value >= 600f) 150.dp else 104.dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(card), state = grid,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomClearance.current + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, i18n.t(Strings.ARTIST_BACK), tint = DJMetryColors.Text,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).padding(8.dp),
                        )
                        CoverImage(artist.artist_image_url, 48.dp, cornerRadius = 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(artist.artist_name, color = DJMetryColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            total?.let { Text(i18n.tWithArgs(Strings.RADAR_ALL, arrayOf(it)), color = DJMetryColors.Muted, fontSize = 13.sp) }
                        }
                        Icon(
                            Icons.Outlined.Person, null, tint = DJMetryColors.Text,
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(DJMetryColors.Panel).clickable(role = Role.Button) { openArtist(artist.spotify_artist_id) }.padding(10.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { SearchField(query, i18n.t(Strings.RADAR_SEARCH_TITLE)) { query = it } }
                        SortButton(sort) { sort = it }
                    }
                }
            }
            when {
                total == null -> items(9) { SkeletonBox(Modifier.size(card), RoundedCornerShape(14.dp)) }
                releases.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(i18n.t(Strings.RADAR_NO_RELEASES), color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                else -> items(releases, key = { it.album_id }) { r ->
                    val on = flash == r.album_id
                    val glow by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, androidx.compose.animation.core.tween(500), label = "flash")
                    ReleaseCard(r, today, card, Modifier.border(3.dp * glow, DJMetryColors.Accent.copy(alpha = glow), RoundedCornerShape(14.dp)).padding(3.dp * glow))
                }
            }
        }
    }
}

@Composable
private fun SortButton(sort: ReleaseSort, onSort: (ReleaseSort) -> Unit) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Outlined.Sort, i18n.t(sort.label), tint = DJMetryColors.Text,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(DJMetryColors.Panel).clickable(role = Role.DropdownList) { open = true }.padding(12.dp),
        )
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            ReleaseSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(i18n.t(s.label), color = if (s == sort) DJMetryColors.Accent else DJMetryColors.Text, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onSort(s) },
                )
            }
        }
    }
}
