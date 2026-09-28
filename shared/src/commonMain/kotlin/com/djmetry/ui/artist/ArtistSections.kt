package com.djmetry.ui.artist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.Track
import com.djmetry.api.models.YouTubeData
import com.djmetry.data.repository.ArtistCard
import com.djmetry.data.repository.DJMagEntry
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.RemoteImages
import com.djmetry.ui.components.SocialIcon
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.profile.Card
import com.djmetry.ui.profile.CardHeader
import com.djmetry.ui.screens.formatDelta
import com.djmetry.ui.screens.formatScore
import com.djmetry.ui.theme.DJMetryColors

private val Orange = Color(0xFFFFB35B)
private val OrangeBg = Color(0xFF3A2A12)
private val GreenBg = Color(0xFF10302A)
private val PanelDeep = Color(0xFF0E1728)

/** Действия карточки: подписка, голос, поделиться, назад. Держит экран, секции только вызывают. */
internal class ArtistCardActions(
    val onBack: () -> Unit,
    val onFollow: () -> Unit,
    val onVote: () -> Unit,
    val onShare: () -> Unit,
    val onBook: () -> Unit,
    val openUrl: (String) -> Unit,
)

@Composable
private fun rememberPhoto(url: String?) = produceState(url?.let(RemoteImages::cached), url) {
    if (value == null && url != null) value = RemoteImages.load(url)
}

/** Полупрозрачная круглая кнопка поверх фото. */
@Composable
internal fun GlassButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = DJMetryColors.Text, modifier = Modifier.size(21.dp)) }
}

@Composable
internal fun RankBadges(position: Int?, djMag: DJMagEntry?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        position?.let { Badge("DJMetry #$it", DJMetryColors.Background, DJMetryColors.Accent) }
        djMag?.let { Badge("DJ Mag ${djMagLabel(it)}", Orange, OrangeBg) }
    }
}

@Composable
private fun Badge(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
internal fun GenreChips(genres: List<String>, max: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        genres.take(max).forEach {
            Text(it, color = Color.White, fontSize = 11.5.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp))
        }
    }
}

@Composable
internal fun ArtistName(name: String, verified: Boolean, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = DJMetryColors.Text, fontSize = size, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (verified) Icon(Icons.Outlined.Verified, null, tint = DJMetryColors.Accent, modifier = Modifier.padding(start = 8.dp).size(size.value.dp * 0.7f))
    }
}

/**
 * Постер (вариант A): фото на всю ширину, снизу градиент, поверх — места в рейтингах, имя, жанры.
 * [topBar] — «назад» и «поделиться» (телефон и планшет).
 */
@Composable
internal fun ArtistPoster(card: ArtistCard, height: Dp, shape: Shape, topBar: (@Composable () -> Unit)? = null) {
    val d = card.details
    val photo by rememberPhoto(d.imageUrl)
    Box(Modifier.fillMaxWidth().height(height).clip(shape).background(DJMetryColors.PanelStrong)) {
        photo?.let { Image(it, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.matchParentSize()) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to DJMetryColors.Background.copy(alpha = 0.3f), 0.25f to Color.Transparent,
            0.65f to DJMetryColors.Background.copy(alpha = 0.6f), 1f to DJMetryColors.Background,
        )))
        topBar?.let { Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { it() } }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RankBadges(d.position, card.djMag)
            ArtistName(d.name, d.isVerified, 36.sp)
            GenreChips(d.genres)
        }
    }
}

/** Десктоп: широкий баннер — слева имя и действия, справа фото, растворяющееся в фоне. */
@Composable
internal fun ArtistBanner(card: ArtistCard, following: Boolean, voted: Boolean, actions: ArtistCardActions) {
    val d = card.details
    val photo by rememberPhoto(d.imageUrl)
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).clip(shape).background(PanelDeep).border(1.dp, Color.White.copy(alpha = 0.07f), shape)) {
        photo?.let {
            Image(it, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.55f))
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0.45f to PanelDeep, 0.7f to PanelDeep.copy(alpha = 0.4f), 1f to Color.Transparent)))
        Column(
            Modifier.fillMaxHeight().widthIn(max = minOf(600.dp, maxWidth * 0.6f)).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            RankBadges(d.position, card.djMag)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArtistName(d.name, d.isVerified, 48.sp)
                GenreChips(d.genres)
            }
            ArtistActionsRow(following, voted, actions, Modifier.widthIn(max = 460.dp))
        }
    }
}

/** Следить (главная) · Голос · Поделиться. */
@Composable
internal fun ArtistActionsRow(following: Boolean, voted: Boolean, actions: ArtistCardActions, modifier: Modifier = Modifier, showShare: Boolean = true) {
    val i18n = useI18n()
    Row(modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            icon = if (following) Icons.Filled.Check else Icons.Filled.Favorite,
            text = i18n.t(if (following) Strings.ARTIST_FOLLOWING else Strings.ACTION_FOLLOW),
            primary = !following, onClick = actions.onFollow, modifier = Modifier.weight(1.3f),
        )
        ActionButton(
            icon = Icons.Filled.KeyboardDoubleArrowUp,
            text = i18n.t(if (voted) Strings.YOUR_VOTE else Strings.ACTION_VOTE),
            primary = false, onClick = actions.onVote, modifier = Modifier.weight(1f), tint = Orange, highlighted = voted,
        )
        if (showShare) ActionButton(Icons.Outlined.Share, null, primary = false, onClick = actions.onShare, modifier = Modifier.width(52.dp), label = i18n.t(Strings.ARTIST_COPY_LINK))
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector, text: String?, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color? = null, highlighted: Boolean = false, label: String? = text,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when { primary -> DJMetryColors.Accent; highlighted -> OrangeBg; else -> DJMetryColors.Panel }
    val fg = if (primary) DJMetryColors.Background else DJMetryColors.Text
    Row(
        modifier.fillMaxHeight().clip(shape).background(bg).border(1.dp, if (primary) Color.Transparent else DJMetryColors.Border, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint ?: fg, modifier = Modifier.size(20.dp))
        if (text != null) Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/** «Забронировать» — только если у артиста есть агентство ([ArtistCard.hasBooking]). */
@Composable
internal fun BookArtistButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(shape).border(1.5.dp, DJMetryColors.Accent, shape).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = DJMetryColors.Accent, modifier = Modifier.size(20.dp))
        Text(i18n.t(Strings.ARTIST_BOOK), color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Score: кольцо + место в мире, DJ Mag, Spotify, популярность. Всё посчитано бэкендом. */
@Composable
internal fun ArtistScoreCard(card: ArtistCard) {
    val i18n = useI18n()
    val d = card.details
    val delta = d.trend?.score24h
    Card {
        CardHeader("DJMetry Score", delta?.let { "${i18n.t(Strings.ARTIST_24H)} ${if (it >= 0) "+" else ""}${formatDelta(it)}" }, onAction = {})
        // Узкая колонка (планшет-портрет): кольцо сверху, 4 плитки на всю ширину — иначе цифры обрезаются
        BoxWithConstraints {
            val metrics: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric(d.position?.let { "#$it" } ?: "—", i18n.t(Strings.ARTIST_WORLD), Modifier.weight(1f))
                        Metric(card.djMag?.let { "#${it.rank}" } ?: "—", "DJ Mag" + (card.djMag?.year?.let { " $it" } ?: ""), Modifier.weight(1f), Orange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric(d.followers?.let(::compactCount) ?: "—", "Spotify", Modifier.weight(1f))
                        Metric(d.popularity?.toString() ?: "—", i18n.t(Strings.ARTIST_POPULARITY), Modifier.weight(1f))
                    }
                }
            }
            if (maxWidth < NARROW_SCORE) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreRing(d.djmetryScore, 88.dp)
                    metrics(Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(d.djmetryScore, 88.dp)
                    metrics(Modifier.weight(1f).padding(start = 16.dp))
                }
            }
        }
    }
}

private val NARROW_SCORE = 320.dp

@Composable
private fun Metric(value: String, caption: String, modifier: Modifier, color: Color = DJMetryColors.Text) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(DJMetryColors.PanelStrong).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(caption, color = DJMetryColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ScoreRing(score: Double?, size: Dp) {
    val fraction = ((score ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.toPx() * 0.085f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(DJMetryColors.Border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(DJMetryColors.Accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.let(::formatScore) ?: "—", color = DJMetryColors.Text, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
            Text("SCORE", color = DJMetryColors.Muted, fontSize = (size.value * 0.1f).sp)
        }
    }
}

/** Топ-треки Spotify: обложка, полоса популярности, длительность. Тап — открыть трек. */
@Composable
internal fun ArtistTracksCard(tracks: List<Track>, limit: Int, onOpen: (Track) -> Unit) {
    val i18n = useI18n()
    if (tracks.isEmpty()) return
    Card {
        CardHeader(i18n.t(Strings.ARTIST_MUSIC), "Spotify", onAction = {})
        tracks.take(limit).forEachIndexed { index, t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(t) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = DJMetryColors.Muted, fontSize = 13.sp, modifier = Modifier.width(18.dp))
                CoverImage(t.albumImageUrl, 44.dp, cornerRadius = 11.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.name, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    t.popularity?.let { p ->
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(DJMetryColors.Border)) {
                            Box(Modifier.fillMaxWidth(p.coerceIn(0, 100) / 100f).fillMaxHeight().background(DJMetryColors.Accent))
                        }
                    }
                }
                t.durationMs?.let { Text(trackDuration(it), color = DJMetryColors.Muted, fontSize = 12.sp) }
                Icon(Icons.Filled.PlayArrow, null, tint = DJMetryColors.Accent, modifier = Modifier.padding(start = 8.dp).size(22.dp))
            }
        }
    }
}

/** Концерты (Bandsintown через бэкенд): дата, площадка, город, «Билеты». */
@Composable
internal fun ArtistEventsCard(events: List<ArtistEvent>, limit: Int, onOpen: (String) -> Unit) {
    val i18n = useI18n()
    val months = i18n.t(Strings.MONTHS_SHORT)
    Card {
        CardHeader(i18n.t(Strings.HOME_CONCERTS))
        if (events.isEmpty()) {
            Text(i18n.t(Strings.ARTIST_NO_EVENTS), color = DJMetryColors.Muted, fontSize = 13.sp)
            return@Card
        }
        events.take(limit).forEachIndexed { index, e ->
            val first = index == 0
            val day = eventDay(e.datetime)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.size(48.dp, 52.dp).clip(RoundedCornerShape(13.dp)).background(if (first) GreenBg else DJMetryColors.PanelStrong),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(day?.day?.toString() ?: "—", color = if (first) DJMetryColors.Accent else DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(day?.let { monthLabel(months, it.month) }.orEmpty(), color = if (first) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 10.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(e.venue?.name ?: e.title.orEmpty(), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val place = eventPlace(e) + if (first) " · ${i18n.t(Strings.ARTIST_NEAREST)}" else ""
                    Text(place, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ticketUrl(e)?.let { url ->
                    Text(i18n.t(Strings.HOME_TICKETS), color = DJMetryColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(GreenBg).clickable { onOpen(url) }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
internal fun YouTubeCard(youtube: YouTubeData?, onOpen: (String) -> Unit) {
    val i18n = useI18n()
    val yt = youtube?.takeIf { it.subscribers != null || it.views != null } ?: return
    Card(Modifier.then(yt.url?.let { url -> Modifier.clickable { onOpen(url) } } ?: Modifier)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SocialIcon.YouTube.vector, null, tint = SocialIcon.YouTube.color, modifier = Modifier.padding(end = 8.dp).size(20.dp))
            Text("YouTube", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (yt.isOAC) Badge("OAC", DJMetryColors.LowScore, Color(0xFF3A1414))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            yt.subscribers?.let { BigStat(compactCount(it), i18n.t(Strings.ARTIST_SUBSCRIBERS)) }
            yt.views?.let { BigStat(compactCount(it), i18n.t(Strings.ARTIST_VIEWS)) }
        }
    }
}

@Composable
private fun BigStat(value: String, caption: String) {
    Column {
        Text(value, color = DJMetryColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = DJMetryColors.Muted, fontSize = 12.sp)
    }
}

/** Соцсети сеткой 2×N + «Ссылка» — копирует адрес страницы. */
@Composable
internal fun SocialsCard(links: List<SocialLink>, onOpen: (String) -> Unit, onCopyLink: () -> Unit) {
    val i18n = useI18n()
    Card {
        CardHeader(i18n.t(Strings.ARTIST_SOCIALS), i18n.t(Strings.ARTIST_COPY_LINK), onCopyLink)
        links.chunked(2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { link ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.PanelStrong).clickable { onOpen(link.url) }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(DJMetryColors.Background), contentAlignment = Alignment.Center) {
                            Icon(link.kind.icon.vector, link.kind.name, tint = link.kind.icon.color, modifier = Modifier.size(16.dp))
                        }
                        Text(link.label, color = DJMetryColors.Text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** «← Назад» для планшета и десктопа, где нет стеклянной кнопки на фото. */
@Composable
internal fun BackRow(onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text, modifier = Modifier.size(20.dp))
            Text(i18n.t(Strings.ARTIST_BACK), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
