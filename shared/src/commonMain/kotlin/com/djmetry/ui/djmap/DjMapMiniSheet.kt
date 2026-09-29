package com.djmetry.ui.djmap

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.MapCountryArtist
import com.djmetry.api.models.MapEventPoint
import com.djmetry.api.models.MapVenue
import com.djmetry.data.djmap.*
import com.djmetry.data.repository.flagEmoji
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.PillBadge
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlin.time.Clock

/** На сколько dp потянуть шторку, чтобы раскрыть / свернуть. */
private const val DRAG_THRESHOLD = 40f

/**
 * Компактная карточка на телефоне (вариант A «мини-шторка»): 100–130 dp у низа экрана, карта и выбранный маркер видны.
 * Свайп вверх или тап — полная карточка (как на сайте); свайп вниз — свернуть, ещё раз — закрыть.
 */
@Composable
internal fun MiniSheet(s: DjMapState, pop: MapPopup, countryName: (String) -> String, fullMaxHeight: Dp) {
    var expanded by remember(pop) { mutableStateOf(pop is MapPopup.VenuePick) }
    var drag by remember { mutableStateOf(0f) }
    val close = { s.popup = null; s.selectedCountry = null }
    val gestures = Modifier.pointerInput(pop) {
        detectVerticalDragGestures(
            onDragStart = { drag = 0f },
            onVerticalDrag = { _, dy -> drag += dy / density },
            onDragEnd = {
                when {
                    drag < -DRAG_THRESHOLD -> expanded = true
                    drag > DRAG_THRESHOLD -> if (expanded && pop !is MapPopup.VenuePick) expanded = false else close()
                }
            },
        )
    }
    Column(Modifier.fillMaxWidth().then(gestures).animateContentSize()) {
        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) { Grabber() }
            Box(Modifier.shadow(24.dp, RoundedCornerShape(22.dp))) {
                when (pop) {
                    is MapPopup.Events -> EventsPopup(pop.points, s.artistId != null, { id -> s.popup = null; s.selectArtist(id) }, close, fullMaxHeight, Modifier.fillMaxWidth())
                    is MapPopup.Venue -> VenuePopup(pop.venue, pop.densityHead, s, close, fullMaxHeight, Modifier.fillMaxWidth())
                    is MapPopup.VenuePick -> VenuePickPopup(pop.venues, { s.popup = MapPopup.Venue(it) }, close, fullMaxHeight, Modifier.fillMaxWidth())
                    is MapPopup.Country -> CountryPopup(pop, s, countryName, close, fullMaxHeight, Modifier.fillMaxWidth())
                    is MapPopup.City -> CityPopup(pop, close, fullMaxHeight, Modifier.fillMaxWidth())
                }
            }
        } else Column(
            Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(PopupBg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .clickable(role = Role.Button) { expanded = true },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Grabber() }
            when (pop) {
                is MapPopup.Events -> if (pop.points.size == 1) MiniEvent(pop.points.single()) else MiniCluster(s, pop.points)
                is MapPopup.Venue -> MiniVenue(s, pop.venue)
                is MapPopup.Country -> MiniCountry(s, pop, countryName)
                is MapPopup.City -> MiniRow(title = listOfNotNull(pop.city.city, pop.city.country).joinToString(", "),
                    sub = useI18n().tWithArgs(Strings.MAP_DENSITY_COUNT, arrayOf(pop.city.count, pop.city.djs)), leading = { Text(flagEmoji(pop.city.country_code ?: ""), fontSize = 26.sp) })
                is MapPopup.VenuePick -> Unit
            }
        }
    }
}

@Composable
private fun Grabber() = Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFF2A3A58)))

@Composable
private fun Avatar(url: String?, name: String, size: Dp, radius: Dp = 14.dp) =
    CoverImage(url, size, cornerRadius = radius, placeholderColor = argb(genreColor(name))) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.6).sp, modifier = Modifier.align(Alignment.Center))
    }

@Composable
private fun TicketButton(url: String, compact: Boolean = false) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    if (compact) Icon(
        Icons.Outlined.ConfirmationNumber, i18n.t(Strings.MAP_TICKETS), tint = DJMetryColors.Background,
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Accent).clickable(role = Role.Button) { uri.openUri(url) }.padding(9.dp),
    ) else Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Accent).clickable(role = Role.Button) { uri.openUri(url) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.ConfirmationNumber, null, tint = DJMetryColors.Background, modifier = Modifier.size(18.dp))
        Text(i18n.t(Strings.MAP_TICKETS), color = DJMetryColors.Background, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun eventPlaceShort(p: MapEventPoint) = listOfNotNull(p.venue_name, p.venue_city).filter { it.isNotBlank() }.joinToString(" · ")

/** Одно выступление: фото, имя, место и дата — одной строкой каждое, «Билеты». */
@Composable
private fun MiniEvent(p: MapEventPoint) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(p.artist_image_url, p.artist_name, 58.dp)
        Column(Modifier.weight(1f)) {
            Text(p.artist_name, color = DJMetryColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            eventPlaceShort(p).takeIf { it.isNotEmpty() }?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            eventDate(p.datetime)?.let { Text(it, color = DJMetryColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
        }
        p.url?.let { TicketButton(it) }
    }
}

/** Кластер: «Выступлений здесь: N» и лента мини-карточек; тап по карточке — это выступление. */
@Composable
private fun MiniCluster(s: DjMapState, points: List<MapEventPoint>) {
    val i18n = useI18n()
    Text(i18n.tWithArgs(Strings.MAP_PLAYED_HERE, arrayOf(points.size)), color = DJMetryColors.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        points.take(20).forEach { p ->
            Row(
                Modifier.width(250.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0E1728))
                    .clickable(role = Role.Button) { s.popup = MapPopup.Events(listOf(p), p.lat, p.lng) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(p.artist_image_url, p.artist_name, 46.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.artist_name, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.venue_name, eventDate(p.datetime)).joinToString(" · "), color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                p.url?.let { TicketButton(it, compact = true) }
            }
        }
    }
}

/** Площадка: вид, название, «Событий · Диджеев», адрес → Google Maps; ниже аватарки тех, кто играет, и ближайший. */
@Composable
private fun MiniVenue(s: DjMapState, v: MapVenue) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val kind = venueKind(v)
    val rows by produceState<List<LineupRow>?>(null, v.id) { value = lineup(s.venueLineup(v.id).getOrNull()?.artists.orEmpty(), Clock.System.now()) }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B2742)), contentAlignment = Alignment.Center) {
            if (v.image_url != null) CoverImage(v.image_url, 52.dp, cornerRadius = 14.dp) {}
            else Icon(venueGlyph(kind), null, tint = argb(kind.ring), modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val (label, bg) = when (kind) {
                VenueKind.Top -> Strings.MAP_VENUE_TOP to Color(0xFFFFB454)
                VenueKind.Festival -> Strings.MAP_VENUE_FESTIVAL to DJMetryColors.Accent
                VenueKind.Club -> Strings.MAP_VENUE_CLUB to DJMetryColors.Accent2
            }
            PillBadge(i18n.t(label).uppercase(), Color(0xFF0B1220), bg, fontSize = 10.sp, height = 20.dp)
            Text(v.name, color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${i18n.tWithArgs(Strings.MAP_VENUE_EVENTS, arrayOf(v.event_count))} · ${i18n.tWithArgs(Strings.MAP_VENUE_ARTISTS, arrayOf(v.artist_count))}",
                color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1)
        }
        Icon(Icons.Outlined.Place, null, tint = DJMetryColors.Accent,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { uri.openUri(googleMapsUrl(v)) }.padding(8.dp))
    }
    val list = rows.orEmpty()
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp).heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.take(4).forEach { Avatar(it.artist.image_url, it.artist.name, 30.dp, radius = 15.dp) }
        if (list.isNotEmpty()) {
            val names = list.take(2).joinToString(", ") { it.artist.name } + if (v.artist_count > 2) " +${v.artist_count - 2}" else ""
            val next = list.firstOrNull { it.badge != null }?.let { r -> eventDate(r.artist.datetime)?.let { " · ${i18n.t(Strings.MAP_VENUE_NEXT).lowercase()} $it" } }.orEmpty()
            Text(names + next, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Страна: флаг, название, цифры; «Топ диджеи ›» и их аватарки. */
@Composable
private fun MiniCountry(s: DjMapState, pop: MapPopup.Country, countryName: (String) -> String) {
    val i18n = useI18n()
    val density = s.densityCountries.firstOrNull { it.country_code.equals(pop.iso, true) }
    val origin = s.origins.firstOrNull { it.country_code.equals(pop.iso, true) }
    val artists by produceState<List<MapCountryArtist>?>(null, pop) { value = s.countryArtists(pop.iso, pop.origins).getOrNull()?.artists.orEmpty() }
    val sub = if (pop.origins) origin?.let { o -> listOfNotNull(dominantGenre(o.genres), i18n.tWithArgs(Strings.MAP_DJS_FROM, arrayOf(o.count))).joinToString(" · ") }
    else density?.let { i18n.tWithArgs(Strings.MAP_DENSITY_COUNT, arrayOf(it.count, it.djs)) }
    MiniRow(countryName(pop.iso), sub.orEmpty(), leading = { Text(flagEmoji(pop.iso), fontSize = 26.sp) }) {
        PillBadge(i18n.t(if (pop.origins) Strings.MAP_ORIGIN_DJS else Strings.MAP_TOP_DJS).uppercase() + " ›", Color(0xFF0B1220), DJMetryColors.Accent, fontSize = 10.sp, height = 22.dp)
    }
    val list = artists.orEmpty()
    if (list.isNotEmpty()) Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.take(6).forEach { Avatar(it.image_url, it.name, 40.dp, radius = 12.dp) }
    }
}

@Composable
private fun MiniRow(title: String, sub: String, leading: @Composable () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = DJMetryColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
