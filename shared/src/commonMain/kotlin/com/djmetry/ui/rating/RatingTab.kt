package com.djmetry.ui.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.data.repository.RatingChange
import com.djmetry.data.repository.RatingRow
import com.djmetry.data.repository.RatingSource
import com.djmetry.data.repository.podiumSplit
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.ArtistName
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.artist.compactCount
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.readableWidth
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.screens.formatDelta
import com.djmetry.ui.screens.formatScore
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch

private val Gold = Color(0xFFF5C542)
private val Silver = Color(0xFFC0C7D6)
private val Bronze = Color(0xFFCD8B4E)

/** Ширина колонки места: «1000» при крупном шрифте подбирает кегль, но колонка одна и та же — строки ровные. */
internal val RANK_COLUMN = 40.dp

/** Подпись под местом: «▲2», «▼3», «+6.45»; null — ничего не изменилось. */
internal fun changeLabel(change: RatingChange?): String? = when (change) {
    null -> null
    is RatingChange.Places -> if (change.delta > 0) "▲${change.delta}" else "▼${-change.delta}"
    is RatingChange.Growth -> (if (change.score >= 0) "+" else "") + formatDelta(change.score)
}

internal fun changeIsUp(change: RatingChange?): Boolean = when (change) {
    is RatingChange.Places -> change.delta > 0
    is RatingChange.Growth -> change.score >= 0
    null -> true
}

/**
 * Таблица рейтинга — вариант A «Чистый список» + подиум из B. База для всех рейтингов. DJ Mag в строках не показываем.
 * Телефон и планшет-портрет: подиум и список. Альбом и десктоп: подиум, таблица с колонками и карточка выбранного справа.
 */
@Composable
fun RatingTab(listState: LazyListState) {
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    var source by remember { mutableStateOf(RatingSource.Top100) }
    var attempt by remember { mutableStateOf(0) }
    val state by produceState<Result<List<RatingRow>>?>(null, source, attempt) {
        value = null
        value = container.rating.load(source, refresh = attempt > 0)
    }
    val expanded = LocalLayoutClass.current == LayoutClass.Expanded
    var selected by remember(source) { mutableStateOf<RatingRow?>(null) }
    val onRow: (RatingRow) -> Unit = { row ->
        if (expanded) selected = row else row.spotifyArtistId?.let(openArtist)
    }

    val header: @Composable () -> Unit = {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val i18n = useI18n()
            AutoSizeText(i18n.t(Strings.TAB_RATING), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 20.sp)
            SourceTabs(source) { source = it }
        }
    }

    val rows = state?.getOrNull()
    if (expanded) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).widthIn(max = 980.dp)) {
                RatingList(listState, rows, state, header, wide = true, onRow = onRow, onRetry = { attempt++ })
            }
            val pick = selected ?: rows?.firstOrNull()
            Box(Modifier.width(340.dp).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                when {
                    pick != null -> DetailPanel(pick)
                    state == null -> DetailPanelSkeleton()
                }
            }
        }
    } else {
        Box(Modifier.readableWidth()) {
            RatingList(listState, rows, state, header, wide = false, onRow = onRow, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun RatingList(
    listState: LazyListState,
    rows: List<RatingRow>?,
    state: Result<List<RatingRow>>?,
    header: @Composable () -> Unit,
    wide: Boolean,
    onRow: (RatingRow) -> Unit,
    onRetry: () -> Unit,
) {
    val i18n = useI18n()
    val split = rows?.let(::podiumSplit)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = if (wide) 0.dp else 16.dp, end = if (wide) 0.dp else 16.dp, bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") { header() }
        when {
            state == null -> {
                // Скелетон повторяет форму таблицы: подиум и строки, общий блик
                item(key = "podium-skeleton") { PodiumSkeleton() }
                items(RATING_SKELETON_ROWS, key = { "skeleton-$it" }) { RatingRowSkeleton(it, wide) }
            }
            rows == null -> item(key = "error") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(i18n.t(Strings.HOME_ERROR), color = DJMetryColors.Muted, fontSize = 15.sp)
                    TextButton(onClick = onRetry) { Text(i18n.t(Strings.HOME_RETRY), color = DJMetryColors.Accent) }
                }
            }
            else -> {
                if (split!!.podium.isNotEmpty()) item(key = "podium") { Podium(split.podium, onRow) }
                if (wide) item(key = "table-head") { TableHeader() }
                items(split.rest, key = { "${it.position}-${it.spotifyArtistId ?: it.name}" }) { row ->
                    if (wide) TableRow(row) { onRow(row) } else ListRow(row) { onRow(row) }
                }
            }
        }
    }
}

/** Вкладки подборок: одна строка, без переносов; на узком экране — горизонтальная прокрутка. */
@Composable
private fun SourceTabs(selected: RatingSource, onSelect: (RatingSource) -> Unit) {
    val i18n = useI18n()
    val labels = listOf(
        RatingSource.Top100 to "TOP 100",
        RatingSource.Rising to i18n.t(Strings.CHIP_RISING),
        RatingSource.Breakthrough to i18n.t(Strings.CHIP_BREAKTHROUGH),
        RatingSource.DJMag to "DJ Mag",
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (src, label) ->
            val on = src == selected
            Text(
                label, maxLines = 1, softWrap = false,
                color = if (on) DJMetryColors.Background else DJMetryColors.Text,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent else DJMetryColors.Panel)
                    .clickable(role = Role.Tab) { onSelect(src) }.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Подиум: 2-е, 1-е, 3-е; фото в рамке золото / серебро / бронза, номер на рамке. */
@Composable
private fun Podium(podium: List<RatingRow>, onRow: (RatingRow) -> Unit) {
    val colors = listOf(Silver, Gold, Bronze)
    val heights = listOf(52.dp, 70.dp, 40.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp)).padding(start = 12.dp, end = 12.dp, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
    ) {
        podium.forEachIndexed { i, row ->
            val first = i == 1
            Column(
                Modifier.weight(if (first) 1.15f else 1f).clip(RoundedCornerShape(16.dp)).clickable { onRow(row) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    val size: Dp = if (first) 88.dp else 70.dp
                    Box(Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(if (first) 26.dp else 22.dp)).border(3.dp, colors[i], RoundedCornerShape(if (first) 26.dp else 22.dp))) {
                        CoverImage(row.imageUrl, size, cornerRadius = if (first) 26.dp else 22.dp)
                    }
                    Text(
                        "${row.position}", color = DJMetryColors.Background, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(CircleShape).background(colors[i]).padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
                AutoSizeText(
                    row.name, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp), color = DJMetryColors.Text,
                    minFontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
                row.score?.let { Text(formatScore(it), color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.padding(top = 8.dp).fillMaxWidth().height(heights[i]).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Brush.verticalGradient(listOf(colors[i].copy(alpha = 0.28f), Color.Transparent)))
                )
            }
        }
    }
}

/** Колонка места: номер (кегль подбирается — «100» и «1000» не рвутся) и сдвиг под ним. */
@Composable
private fun RankCell(row: RatingRow) {
    Column(Modifier.width(RANK_COLUMN), horizontalAlignment = Alignment.End) {
        AutoSizeText("${row.position}", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 10.sp, textAlign = TextAlign.End)
        ChangeText(row.change, 10.5f)
    }
}

@Composable
private fun ChangeText(change: RatingChange?, sizeSp: Float) {
    val label = changeLabel(change)
    if (label == null) Text("—", color = DJMetryColors.Muted, fontSize = sizeSp.sp, maxLines = 1)
    else Text(label, color = if (changeIsUp(change)) DJMetryColors.Accent else DJMetryColors.LowScore, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** Строка варианта A: место + сдвиг, фото, имя, жанр, Score справа. */
@Composable
private fun ListRow(row: RatingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCell(row)
        Spacer(Modifier.width(12.dp))
        CoverImage(row.imageUrl, 46.dp, cornerRadius = 13.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AutoSizeText(row.name, TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp)
            Subtitle(row)
        }
        row.score?.let { AutoSizeText(formatScore(it), TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp) }
    }
}

/** Под именем — только жанр, во всю ширину (DJ Mag в таблице не показываем — отдельная вкладка). */
@Composable
private fun Subtitle(row: RatingRow) {
    row.genre?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

// ───────── Таблица (альбом, десктоп) ─────────

@Composable
private fun TableHeader() {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = TextStyle(fontSize = 12.sp, color = DJMetryColors.Muted)
        Text("#", style = style, modifier = Modifier.width(RANK_COLUMN + 12.dp))
        Text(i18n.t(Strings.RATING_ARTIST), style = style, modifier = Modifier.weight(1f))
        Text(i18n.t(Strings.RATING_GENRE), style = style, modifier = Modifier.width(200.dp))
        Text("Score", style = style, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TableRow(row: RatingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Panel).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCell(row)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(row.imageUrl, 40.dp, cornerRadius = 11.dp)
            AutoSizeText(row.name, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Text(row.genre ?: "—", color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
        Text(row.score?.let(::formatScore) ?: "—", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
    }
}

/** Карточка выбранного артиста справа (альбом, десктоп): фото, место, Score, подписчики, «Следить», «Открыть карточку». */
@Composable
private fun DetailPanel(row: RatingRow) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    val scope = rememberCoroutineScope()
    val follows by container.discover.follows.collectAsState()
    val following = follows.any { it.spotifyArtistId == row.spotifyArtistId }
    var message by remember(row) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            CoverImage(row.imageUrl, 312.dp, cornerRadius = 18.dp)
            Text(
                "#${row.position}", color = DJMetryColors.Background, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(DJMetryColors.Accent).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        ArtistName(row.name, verified = false, size = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(row.score?.let(::formatScore) ?: "—", "Score", DJMetryColors.Accent, Modifier.weight(1f))
            Metric(row.followers?.let(::compactCount) ?: row.djMagRank?.let { "#$it" } ?: "—", if (row.followers != null) "Spotify" else "DJ Mag", DJMetryColors.Text, Modifier.weight(1f))
        }
        val id = row.spotifyArtistId
        if (id != null) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (following) DJMetryColors.PanelStrong else DJMetryColors.Accent)
                    .clickable {
                        scope.launch {
                            val r = if (following) container.discover.unfollow(id) else container.discover.follow(id, row.name, row.imageUrl)
                            message = r.exceptionOrNull()?.let { i18n.t(actionErrorKey(it)) }
                        }
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = if (following) DJMetryColors.Accent else DJMetryColors.Background, modifier = Modifier.size(18.dp))
                Text(i18n.t(if (following) Strings.ARTIST_FOLLOWING else Strings.ACTION_FOLLOW), color = if (following) DJMetryColors.Text else DJMetryColors.Background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                i18n.t(Strings.RATING_OPEN_CARD), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, DJMetryColors.Border, RoundedCornerShape(14.dp)).clickable { openArtist(id) }.padding(12.dp),
            )
        }
        message?.let { Text(it, color = DJMetryColors.LowScore, fontSize = 12.sp) }
    }
}

@Composable
private fun Metric(value: String, caption: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(DJMetryColors.PanelStrong).padding(horizontal = 12.dp, vertical = 9.dp)) {
        AutoSizeText(value, TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = color, minFontSize = 11.sp)
        AutoSizeText(caption, TextStyle(fontSize = 11.5.sp), color = DJMetryColors.Muted, minFontSize = 8.sp)
    }
}
