package com.djmetry.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay

/**
 * Реальные артисты и треки для иллюстраций онбординга.
 * Фото и обложки грузятся со Spotify CDN — так же их показывает djmetry.com.
 * Score — из DJMetry TOP 100 на 2026-09-28.
 */
internal object OnboardingShowcase {
    data class Artist(val name: String, val initials: String, val score: String, val photo: String)

    val topArtists = listOf(
        Artist("David Guetta", "DG", "53.16", "https://i.scdn.co/image/ab67616100005174f150017ca69c8793503c2d4f"),
        Artist("Calvin Harris", "CH", "51.46", "https://i.scdn.co/image/ab676161000051748ebba5e60113b48de8c11f6b"),
        Artist("Tiësto", "T", "48.72", "https://i.scdn.co/image/ab67616100005174f4973f7346a2f35e56b63d57"),
    )
    const val GARRIX_PHOTO = "https://i.scdn.co/image/ab67616100005174f5b8ee60f1f4ee3453aba40b"
    const val HAPPIER_COVER = "https://i.scdn.co/image/ab67616d00001e02dd0a40eecd4b13e4c59988da"
    const val LOSING_IT_COVER = "https://i.scdn.co/image/ab67616d00001e029367c1ee2eec0bf3a04b4868"
}

private val StageHeight = 270.dp
private val PanelDeep = Color(0xFF0E1728)
private val LineColor = Color(0xFF2C3D5C)
private val PillGreenBg = Color(0xFF10302A)
private val PanelShape = RoundedCornerShape(18.dp)

// ───────────────────────── 1 · Рейтинг ─────────────────────────

@Composable
internal fun RatingIllustration(active: Boolean) {
    val i18n = useI18n()
    // Чуть выше остальных: кольцо Score заходит только на нижний край карточки, не на цифры
    Box(Modifier.fillMaxWidth().height(StageHeight + 36.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption("DJMetry TOP 100", Modifier.weight(1f))
                LiveBadge()
            }
            Spacer(Modifier.height(6.dp))
            OnboardingShowcase.topArtists.forEachIndexed { index, artist ->
                val shown = appear(active, 100 + index * 150)
                Row(
                    Modifier.appearing(shown).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (index == 0) DJMetryColors.Accent else DJMetryColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.width(16.dp),
                    )
                    CoverImage(artist.photo, 38.dp, cornerRadius = 10.dp) { Initials(artist.initials) }
                    Spacer(Modifier.width(10.dp))
                    OneLine(artist.name, Modifier.weight(1f), fontSize = 14.sp)
                    Text(artist.score, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        ScoreRing(active, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp).floating(rotation = 6f))
        Chip(
            Icons.AutoMirrored.Filled.TrendingUp,
            i18n.t(Strings.OB_RISING),
            Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 28.dp).floating(rotation = -5f, phaseMs = 800),
        )
    }
}

@Composable
private fun ScoreRing(active: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(0.53f, tween(1400, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier
            .size(104.dp)
            .shadow(16.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(PanelDeep)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 7.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(DJMetryColors.Border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                DJMetryColors.Accent, -90f, 360f * progress.value, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("53", color = DJMetryColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("SCORE", color = DJMetryColors.Muted, fontSize = 9.sp)
        }
    }
}

// ───────────────────────── 2 · Радары ─────────────────────────

@Composable
internal fun RadarsIllustration(active: Boolean) {
    val i18n = useI18n()
    Box(Modifier.fillMaxWidth().height(StageHeight)) {
        // «Хвост» стопки уведомлений
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp)
                .graphicsLayer { alpha = 0.4f }.clip(PanelShape).background(DJMetryColors.Panel)
        )
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotificationCard(
                cover = OnboardingShowcase.HAPPIER_COVER,
                label = "RELEASE RADAR · ${i18n.t(Strings.OB_NOW)}",
                title = "Happier",
                subtitle = "Marshmello, Bastille",
                modifier = Modifier.appearing(appear(active, 200)),
            )
            NotificationCard(
                cover = OnboardingShowcase.GARRIX_PHOTO,
                label = "CONCERT RADAR · ${i18n.t(Strings.OB_HOURS_AGO)}",
                title = "Martin Garrix",
                subtitle = i18n.t(Strings.OB_CONCERT_WHEN),
                modifier = Modifier.appearing(appear(active, 450)),
            )
        }
        FollowedArtists(Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp))
        Chip(
            Icons.Filled.NotificationsActive,
            i18n.t(Strings.OB_FIRST_TO_KNOW),
            Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp).floating(rotation = 4f),
        )
    }
}

@Composable
private fun NotificationCard(cover: String, label: String, title: String, subtitle: String, modifier: Modifier) {
    Panel(modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(cover, 54.dp, tilt = -16f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                OneLine(label, color = DJMetryColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                OneLine(title, Modifier.padding(top = 3.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                OneLine(subtitle, color = DJMetryColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FollowedArtists(modifier: Modifier) {
    val artists = OnboardingShowcase.topArtists
    Row(modifier) {
        listOf(artists[0], artists[2]).forEachIndexed { index, artist ->
            Box(
                Modifier.offset(x = (-10 * index).dp).clip(RoundedCornerShape(12.dp))
                    .background(DJMetryColors.Background).padding(2.dp)
            ) {
                CoverImage(artist.photo, 34.dp, cornerRadius = 10.dp) { Initials(artist.initials) }
            }
        }
        Box(
            Modifier.offset(x = (-20).dp).clip(RoundedCornerShape(12.dp))
                .background(DJMetryColors.Background).padding(2.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(DJMetryColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("+86", color = DJMetryColors.Background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ───────────────────────── 3 · Инструменты артиста ─────────────────────────

@Composable
internal fun ArtistToolsIllustration(active: Boolean) {
    val i18n = useI18n()
    Box(Modifier.fillMaxWidth().height(StageHeight)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                CoverImage(OnboardingShowcase.LOSING_IT_COVER, 76.dp, cornerRadius = 14.dp, tilt = -16f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    OneLine("SMART LINK · djm.fm/losing-it", color = DJMetryColors.Muted, fontSize = 10.5.sp)
                    OneLine("Losing It", Modifier.padding(top = 3.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    OneLine("FISHER", color = DJMetryColors.Muted, fontSize = 12.sp)
                    OneLine(
                        i18n.t(Strings.OB_PRESAVE),
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(7.dp)).background(PillGreenBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = DJMetryColors.Accent,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("12 480", color = DJMetryColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        buildAnnotatedString {
                            append("${i18n.t(Strings.OB_CLICKS)} · ")
                            withStyle(SpanStyle(color = DJMetryColors.Accent)) { append("+34%") }
                        },
                        color = DJMetryColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                GrowthBars(active)
            }
        }
        Panel(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 4.dp).fillMaxWidth()
                .floating(rotation = -3f),
            color = PanelDeep,
            padding = 12.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(OnboardingShowcase.LOSING_IT_COVER, 38.dp, cornerRadius = 10.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Headphones, null, tint = DJMetryColors.Accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        OneLine("TRACK RADAR", color = DJMetryColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OneLine(i18n.tWithArgs(Strings.OB_TRACK_PLAYED, arrayOf("Losing It")), Modifier.padding(top = 2.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GrowthBars(active: Boolean) {
    val heights = listOf(0.3f, 0.45f, 0.4f, 0.62f, 0.78f, 1f)
    Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { index, h ->
            val grown = appear(active, 150 + index * 80)
            Box(
                Modifier.width(7.dp).fillMaxHeight(h * (0.2f + 0.8f * grown))
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index == heights.lastIndex) DJMetryColors.Accent else DJMetryColors.Border)
            )
        }
    }
}

// ───────────────────────── 4 · Букинг ─────────────────────────

@Composable
internal fun BookingIllustration(active: Boolean) {
    val i18n = useI18n()
    Box(Modifier.fillMaxWidth().height(StageHeight)) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Role(i18n.t(Strings.OB_ROLE_ARTIST)) {
                    CoverImage(OnboardingShowcase.topArtists[1].photo, 48.dp, tilt = 14f) { Initials("CH") }
                }
                DashedLink(Modifier.weight(1f).padding(top = 24.dp))
                Role(i18n.t(Strings.OB_ROLE_MANAGER)) {
                    CoverImage(null, 48.dp, placeholderColor = DJMetryColors.Accent2) {
                        Icon(Icons.Filled.Work, null, tint = DJMetryColors.Background, modifier = Modifier.size(22.dp))
                    }
                }
                DashedLink(Modifier.weight(1f).padding(top = 24.dp))
                Role(i18n.t(Strings.OB_ROLE_CUSTOMER)) {
                    CoverImage(null, 48.dp, tilt = -16f, placeholderColor = DJMetryColors.MediumScore) {
                        Icon(Icons.Filled.Storefront, null, tint = DJMetryColors.Background, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneLine("Calvin Harris · Barcelona", Modifier.weight(1f), color = DJMetryColors.Muted, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(i18n.t(Strings.OB_PAID), color = DJMetryColors.Accent, fontSize = 12.sp, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                BookingStatus(i18n.t(Strings.OB_STATUS_ACCEPTED), StatusState.Done, appear(active, 150))
                BookingStatus(i18n.t(Strings.OB_STATUS_ON_THE_WAY), StatusState.Done, appear(active, 350))
                BookingStatus(i18n.t(Strings.OB_STATUS_ON_STAGE), StatusState.Current, appear(active, 550))
                BookingStatus(i18n.t(Strings.OB_STATUS_DONE), StatusState.Pending, appear(active, 750))
            }
        }
        Chip(
            Icons.Filled.VerifiedUser,
            i18n.t(Strings.OB_TOKEN_LINK),
            Modifier.align(Alignment.BottomEnd).floating(rotation = -4f),
        )
    }
}

private enum class StatusState { Done, Current, Pending }

@Composable
private fun BookingStatus(text: String, state: StatusState, shown: Float) {
    Row(Modifier.appearing(shown).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StatusState.Done -> Icon(Icons.Outlined.CheckCircle, null, tint = DJMetryColors.Accent, modifier = Modifier.size(17.dp))
                StatusState.Current -> PulseDot()
                StatusState.Pending -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = LineColor, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        OneLine(
            text,
            color = if (state == StatusState.Pending) DJMetryColors.Muted else DJMetryColors.Text,
            fontSize = 13.sp,
            fontWeight = if (state == StatusState.Current) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PulseDot() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse",
    )
    Box(
        Modifier.size(12.dp).drawBehind {
            drawCircle(DJMetryColors.Accent.copy(alpha = 0.5f * (1f - pulse)), radius = size.minDimension / 2 + 7.dp.toPx() * pulse)
        }.clip(CircleShape).background(DJMetryColors.Accent)
    )
}

@Composable
private fun Role(label: String, avatar: @Composable () -> Unit) {
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        avatar()
        Spacer(Modifier.height(6.dp))
        OneLine(label, color = DJMetryColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DashedLink(modifier: Modifier) {
    Canvas(modifier.height(2.dp)) {
        drawLine(
            LineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

// ───────────────────────── Общие элементы ─────────────────────────

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    color: Color = DJMetryColors.Panel,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(12.dp, PanelShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(PanelShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.07f), PanelShape)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun Chip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(DJMetryColors.Accent)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DJMetryColors.Background, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = DJMetryColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LiveBadge() {
    val blink by rememberInfiniteTransition(label = "live").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "live",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = blink }.clip(CircleShape).background(DJMetryColors.Accent))
        Spacer(Modifier.width(5.dp))
        Text("LIVE", color = DJMetryColors.Accent, fontSize = 12.sp)
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) =
    OneLine(text, modifier, color = DJMetryColors.Muted, fontSize = 12.sp)

@Composable
private fun BoxScope.Initials(text: String) {
    Text(text, color = DJMetryColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
}

/** Однострочный текст с многоточием — длинные имена и переводы не ломают карточки. */
@Composable
private fun OneLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DJMetryColors.Text,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
) = Text(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** 0 → 1 с задержкой каждый раз, когда страница становится активной. */
@Composable
private fun appear(active: Boolean, delayMs: Int): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            delay(delayMs.toLong())
            progress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }
    return progress.value
}

private fun Modifier.appearing(progress: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 14.dp.toPx()
}

/** Лёгкое «парение» вверх-вниз с наклоном [rotation]. */
@Composable
private fun Modifier.floating(rotation: Float = 0f, phaseMs: Int = 0): Modifier {
    val lift by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(phaseMs)),
        label = "float",
    )
    return graphicsLayer {
        translationY = -6.dp.toPx() * lift
        rotationZ = rotation
    }
}
