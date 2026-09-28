package com.djmetry.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.SnapshotItem
import com.djmetry.api.models.Track
import com.djmetry.data.repository.ReleasePreview
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.components.RemoteImages
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.screens.formatDelta
import com.djmetry.ui.theme.DJMetryColors

private val PanelDeep = Color(0xFF0E1728)
private val Orange = Color(0xFFFFB35B)
private val GreenBg = Color(0xFF10302A)

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)).padding(16.dp),
        content = content,
    )
}

@Composable
internal fun CardHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) Text(action, color = DJMetryColors.Accent, fontSize = 12.5.sp, modifier = Modifier.clickable(onClick = onAction))
    }
}

/** Герой: фото на всю карточку, жанры, имя с печатью, локация. [topEnd] — место для колокольчика (телефон). */
@Composable
internal fun ArtistHero(artist: ArtistDetailsResponse, height: Dp, topEnd: (@Composable () -> Unit)? = null) {
    val bitmap by produceState(artist.imageUrl?.let(RemoteImages::cached), artist.imageUrl) {
        if (value == null && artist.imageUrl != null) value = RemoteImages.load(artist.imageUrl)
    }
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp)).background(DJMetryColors.PanelStrong)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to DJMetryColors.Background.copy(alpha = 0.96f))))
        if (topEnd != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(14.dp).size(44.dp).clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(DJMetryLogo.HashMark, "DJMetry", tint = DJMetryColors.Accent, modifier = Modifier.size(22.dp, 20.dp)) }
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) { topEnd() }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                artist.genres.take(2).forEach {
                    Text(it, color = Color.White, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(artist.name, color = DJMetryColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (artist.isVerified) Icon(Icons.Outlined.Verified, null, tint = DJMetryColors.Accent, modifier = Modifier.padding(start = 6.dp).size(22.dp))
            }
            val place = listOfNotNull(artist.city, artist.country).joinToString(", ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = DJMetryColors.Muted, modifier = Modifier.size(14.dp))
                Text(listOfNotNull(place.ifEmpty { null }, artist.position?.let { "DJMetry #$it" }).joinToString(" · "), color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/** Герой обычного пользователя (не артиста): аватар + имя + email. */
@Composable
internal fun UserHero(me: MeResponse, topEnd: (@Composable () -> Unit)? = null) {
    val name = me.user?.displayName ?: "DJMetry"
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(me.user?.avatarUrl, 72.dp, cornerRadius = 22.dp) {
                Text(name.take(1).uppercase(), color = DJMetryColors.Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(name, color = DJMetryColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                me.user?.email?.let { Text(it, color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            topEnd?.invoke()
        }
    }
}

/** 4 метрики с бэкенда: место, Score, голоса, подписчики DJMetry. */
@Composable
internal fun MetricsGrid(artist: ArtistDetailsResponse, columns: Int) {
    val i18n = useI18n()
    val metrics = listOf(
        (artist.position?.let { "#$it" } ?: "—") to i18n.t(Strings.PROFILE_PLACE),
        (artist.djmetryScore?.let(::formatDelta) ?: "—") to "Score",
        "${artist.votes ?: 0}" to i18n.t(Strings.PROFILE_VOTES),
        "${artist.followsCount ?: 0}" to i18n.t(Strings.PROFILE_FOLLOWERS),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { i, (value, label) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(PanelDeep).padding(12.dp)) {
                        Text(value, color = if (label == "Score") DJMetryColors.Accent else DJMetryColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(label, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookingButton(requests: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Accent).clickable(onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = DJMetryColors.Background, modifier = Modifier.size(20.dp))
        Text(i18n.t(Strings.TAB_BOOKING), color = DJMetryColors.Background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
        if (requests > 0) Text("$requests", color = DJMetryColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DJMetryColors.Background).padding(horizontal = 7.dp, vertical = 1.dp))
    }
}

@Composable
internal fun OpenPageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = DJMetryColors.Text, modifier = Modifier.size(18.dp))
        Text(i18n.t(Strings.PROFILE_PAGE), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Действия плиток кабинета. */
internal class TileActions(val bio: () -> Unit, val links: () -> Unit, val analytics: () -> Unit, val radars: () -> Unit)

@Composable
internal fun ServiceTiles(columns: Int, actions: TileActions) {
    val i18n = useI18n()
    val tiles = listOf(
        Tile(Icons.Outlined.AccountBox, i18n.t(Strings.TILE_BIO), i18n.t(Strings.TILE_BIO_SUB), actions.bio),
        Tile(Icons.Outlined.Link, i18n.t(Strings.TILE_LINKS), i18n.t(Strings.TILE_LINKS_SUB), actions.links),
        Tile(Icons.Outlined.BarChart, i18n.t(Strings.TILE_ANALYTICS), i18n.t(Strings.TILE_ANALYTICS_SUB), actions.analytics),
        Tile(Icons.Outlined.Radar, i18n.t(Strings.TILE_RADARS), i18n.t(Strings.TILE_RADARS_SUB), actions.radars),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Panel)
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)).clickable(onClick = tile.onClick).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(tile.icon, null, tint = DJMetryColors.Accent, modifier = Modifier.size(24.dp))
                        Text(tile.title, color = DJMetryColors.Text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tile.subtitle, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private class Tile(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

/** График Score из `/me/snapshots` (точки бэкенда, без пересчёта). */
@Composable
internal fun ScoreCard(history: List<SnapshotItem>, chartHeight: Dp = 90.dp) {
    val i18n = useI18n()
    if (history.size < 2) return
    val delta = history.last().score - history.first().score
    Card {
        CardHeader(i18n.t(Strings.SCORE_30D), (if (delta >= 0) "+" else "") + formatDelta(delta)) {}
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            val min = history.minOf { it.score }
            val max = history.maxOf { it.score }.let { if (it == min) min + 1 else it }
            val step = size.width / (history.size - 1)
            val points = history.mapIndexed { i, s -> Offset(i * step, size.height - ((s.score - min) / (max - min)).toFloat() * size.height * 0.9f - size.height * 0.05f) }
            val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(DJMetryColors.Accent.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, DJMetryColors.Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun TracksCard(tracks: List<Track>, onOpen: (Track) -> Unit) {
    val i18n = useI18n()
    if (tracks.isEmpty()) return
    Card {
        CardHeader(i18n.t(Strings.TRACKS), "Spotify") {}
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tracks.forEach { t ->
                Column(Modifier.width(96.dp).clickable { onOpen(t) }) {
                    CoverImage(t.albumImageUrl, 96.dp, cornerRadius = 14.dp)
                    Text(t.name, color = DJMetryColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Заявки букинга: статус и сумма — как пришли с бэкенда. */
@Composable
internal fun BookingCard(company: BookingCompany?, requests: List<BookingRequest>, onOpenAll: () -> Unit) {
    val i18n = useI18n()
    if (company == null) return
    Card {
        CardHeader("${i18n.t(Strings.TAB_BOOKING)} · ${company.name}", i18n.t(Strings.BOOKING_ALL), onOpenAll)
        if (requests.isEmpty()) {
            Text(listOfNotNull(company.city, company.country).joinToString(", "), color = DJMetryColors.Muted, fontSize = 13.sp)
        }
        requests.forEachIndexed { index, r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = bookingStatusKey(r.status)?.let(i18n.t) ?: r.status
                Text(status, color = DJMetryColors.Accent, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(GreenBg).padding(horizontal = 9.dp, vertical = 4.dp))
                Text(
                    listOfNotNull(r.number?.let { "№$it" }, r.event_date?.take(10), r.country ?: r.city).joinToString(" · "),
                    color = DJMetryColors.Text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                r.payment_amount?.let { Text(formatMoney(it, r.payment_currency), color = DJMetryColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
internal fun RadarCard(releases: List<ReleasePreview>, onOpen: () -> Unit) {
    val i18n = useI18n()
    if (releases.isEmpty()) return
    Card {
        CardHeader(i18n.t(Strings.TAB_RADARS), i18n.t(Strings.RADAR_OPEN), onOpen)
        releases.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(r.release.image_url, 42.dp, cornerRadius = 11.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.release.name, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.artistName} · Release Radar", color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
