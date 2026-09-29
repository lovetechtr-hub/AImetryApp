package com.djmetry.ui.djmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.MapCountryArtist
import com.djmetry.api.models.MapEventPoint
import com.djmetry.api.models.MapVenue
import com.djmetry.data.djmap.*
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.artist.monthLabel
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.PillBadge
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlin.time.Clock

private val TopColor = Color(0xFFFFB454)

/** «28 сен 2026» — дата события в местном времени площадки. */
@Composable
internal fun eventDate(datetime: String?): String? {
    val i18n = useI18n()
    val d = datetime?.take(10)?.split('-')?.takeIf { it.size == 3 } ?: return null
    val m = d[1].toIntOrNull() ?: return null
    return "${d[2].trimStart('0')} ${monthLabel(i18n.t(Strings.MONTHS_SHORT), m)} ${d[0]}"
}

/** Каркас карточки: фон, скругление, крестик, прокрутка при нехватке места. */
@Composable
internal fun PopupCard(onClose: () -> Unit, maxHeight: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.heightIn(max = maxHeight).clip(RoundedCornerShape(22.dp)).background(MapUi.popup).border(1.dp, MapUi.hairline, RoundedCornerShape(22.dp))) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        Icon(
            Icons.Filled.Close, null, tint = MapUi.text,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClick = onClose).padding(6.dp),
        )
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (primary) MapUi.accent else MapUi.secondary)
            .then(if (primary) Modifier else Modifier.border(1.dp, MapUi.border, RoundedCornerShape(12.dp)))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) MapUi.bg else MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun SectionTitle(text: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(text.uppercase(), color = MapUi.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        count?.let { PillBadge(it.toString(), MapUi.bg, MapUi.accent, fontSize = 11.sp, height = 22.dp) }
    }
}

private fun place(p: MapEventPoint) = listOfNotNull(p.venue_name, p.venue_city, p.venue_country).filter { it.isNotBlank() }.joinToString(" · ")

/** Событие: одна точка — hero-карточка с большим фото; кластер — список (до 12 + «Ещё N»). Как `buildEventPopup` сайта. */
@Composable
internal fun EventsPopup(points: List<MapEventPoint>, singleDj: Boolean, onlyThisDj: (String) -> Unit, onClose: () -> Unit, maxHeight: Dp, modifier: Modifier) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val openArtist = LocalArtistNavigator.current
    if (points.size == 1) {
        val p = points.single()
        Box(modifier.heightIn(max = maxHeight).clip(RoundedCornerShape(22.dp)).background(MapUi.popup).border(1.dp, MapUi.hairline, RoundedCornerShape(22.dp))) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(210.dp).clickable(role = Role.Button) { openArtist(p.spotify_artist_id) }) {
                    CoverImage(p.artist_image_url, 400.dp, Modifier.matchParentSize(), cornerRadius = 0.dp, placeholderColor = argb(genreColor(p.artist_name))) {
                        Text(p.artist_name.take(1), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                    }
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to MapUi.popup)))
                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.genres.take(3).forEach { g ->
                                Text(g.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    modifier = Modifier.clip(CircleShape).background(Color(0xFF1C5A47).copy(alpha = 0.9f)).border(1.dp, MapUi.accent.copy(alpha = 0.5f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Text(p.artist_name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(DJMetryColors.Accent2.copy(alpha = 0.7f)))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    place(p).takeIf { it.isNotEmpty() }?.let { IconLine(Icons.Outlined.Place, it, MapUi.text) }
                    eventDate(p.datetime)?.let { IconLine(Icons.Outlined.CalendarMonth, it, MapUi.accent, bold = true) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        p.url?.let { ActionButton(i18n.t(Strings.MAP_TICKETS), true, Modifier.weight(1f)) { uri.openUri(it) } }
                        if (!singleDj) ActionButton(i18n.t(Strings.MAP_ONLY_THIS_DJ), false, Modifier.weight(1f)) { onlyThisDj(p.spotify_artist_id) }
                    }
                }
            }
            Icon(
                Icons.Filled.Close, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable(role = Role.Button, onClick = onClose).padding(6.dp),
            )
        }
        return
    }
    PopupCard(onClose, maxHeight, modifier) {
        points.take(12).forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MapUi.hairline)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { openArtist(p.spotify_artist_id) }.padding(end = 30.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverImage(p.artist_image_url, 54.dp, cornerRadius = 14.dp, placeholderColor = argb(genreColor(p.artist_name))) {
                    Text(p.artist_name.take(1), color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                }
                Text(p.artist_name, color = MapUi.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            p.genres.take(3).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = MapUi.accent, fontSize = 13.sp) }
            place(p).takeIf { it.isNotEmpty() }?.let { Text(it, color = MapUi.muted, fontSize = 13.5.sp) }
            eventDate(p.datetime)?.let { Text(it, color = MapUi.muted, fontSize = 13.5.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.url?.let { ActionButton(i18n.t(Strings.MAP_TICKETS), true) { uri.openUri(it) } }
                if (!singleDj) ActionButton(i18n.t(Strings.MAP_ONLY_THIS_DJ), false) { onlyThisDj(p.spotify_artist_id) }
            }
        }
        if (points.size > 12) Text(i18n.tWithArgs(Strings.MAP_MORE_EVENTS, arrayOf(points.size - 12)), color = MapUi.muted, fontSize = 13.sp)
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MapUi.accent, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, color = color, fontSize = 15.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Площадка: фото или значок с бейджем вида, адрес → Google Maps, сайт, «Событий / Диджеев», «Кто играет». */
@Composable
internal fun VenuePopup(v: MapVenue, densityHead: Boolean, s: DjMapState, onClose: () -> Unit, maxHeight: Dp, modifier: Modifier) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val openArtist = LocalArtistNavigator.current
    val kind = venueKind(v)
    val lineup by produceState<List<LineupRow>?>(null, v.id) { value = lineup(s.venueLineup(v.id).getOrNull()?.artists.orEmpty(), Clock.System.now()) }
    PopupCard(onClose, maxHeight, modifier) {
        if (densityHead) Text(i18n.tWithArgs(Strings.MAP_DENSITY_VENUE_HEAD, arrayOf(v.event_count)).uppercase(), color = MapUi.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Box(Modifier.fillMaxWidth().height(if (v.image_url != null) 150.dp else 130.dp).clip(RoundedCornerShape(16.dp)).background(MapUi.inner), contentAlignment = Alignment.Center) {
            if (v.image_url != null) CoverImage(v.image_url, 400.dp, Modifier.matchParentSize(), cornerRadius = 16.dp) {}
            else Icon(venueGlyph(kind), null, tint = argb(kind.ring), modifier = Modifier.size(48.dp))
            val (label, bg) = when (kind) {
                VenueKind.Top -> Strings.MAP_VENUE_TOP to TopColor
                VenueKind.Festival -> Strings.MAP_VENUE_FESTIVAL to MapUi.accent
                VenueKind.Club -> Strings.MAP_VENUE_CLUB to DJMetryColors.Accent2
            }
            Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { PillBadge(i18n.t(label).uppercase(), Color(0xFF0B1220), bg, fontSize = 12.sp, height = 28.dp) }
            v.image_attribution?.takeIf { v.image_url != null }?.let {
                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Text(v.name, color = MapUi.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        val address = v.address ?: listOfNotNull(v.city, v.country).joinToString(", ")
        if (address.isNotBlank()) Row(Modifier.clickable(role = Role.Button) { uri.openUri(googleMapsUrl(v)) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Place, null, tint = MapUi.accent, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Text(address, color = MapUi.accent, fontSize = 14.sp, textDecoration = TextDecoration.Underline)
        }
        v.website?.takeIf { it.startsWith("http") }?.let { site ->
            val host = site.substringAfter("://").substringBefore('/').removePrefix("www.")
            Text("${i18n.t(Strings.MAP_WEBSITE)}: $host", color = DJMetryColors.Accent2, fontSize = 13.5.sp, modifier = Modifier.clickable(role = Role.Button) { uri.openUri(site) })
        }
        v.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MapUi.muted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(i18n.tWithArgs(Strings.MAP_VENUE_EVENTS, arrayOf(v.event_count)), color = MapUi.muted, fontSize = 14.sp)
            Text(i18n.tWithArgs(Strings.MAP_VENUE_ARTISTS, arrayOf(v.artist_count)), color = MapUi.muted, fontSize = 14.sp)
        }
        val rows = lineup
        when {
            rows == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = MapUi.accent, strokeWidth = 2.dp) }
            rows.isNotEmpty() -> {
                HorizontalDivider(color = MapUi.hairline)
                SectionTitle(i18n.t(Strings.MAP_VENUE_PLAYING))
                rows.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverImage(r.artist.image_url, 56.dp, Modifier.clickable(role = Role.Button) { openArtist(r.artist.spotify_artist_id) }, cornerRadius = 14.dp, placeholderColor = argb(genreColor(r.artist.name))) {
                            Text(r.artist.name.take(1), color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(r.artist.name, color = MapUi.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                    .clickable(role = Role.Button) { openArtist(r.artist.spotify_artist_id) })
                                r.badge?.let { b -> PillBadge(i18n.t(if (b == LineupBadge.Now) Strings.MAP_VENUE_NOW else Strings.MAP_VENUE_NEXT).uppercase(), Color(0xFF0B1220), MapUi.accent, fontSize = 10.5.sp, height = 22.dp) }
                            }
                            eventDate(r.artist.datetime)?.let { Text(it, color = MapUi.muted, fontSize = 13.sp) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                r.artist.ticket_url?.let { ActionButton(i18n.t(Strings.MAP_TICKETS), true) { uri.openUri(it) } }
                                ActionButton(i18n.t(Strings.MAP_MUSIC), false) { uri.openUri(spotifyArtistUrl(r.artist.spotify_artist_id)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Площадки в одной точке — выбор из списка. */
@Composable
internal fun VenuePickPopup(venues: List<MapVenue>, onPick: (MapVenue) -> Unit, onClose: () -> Unit, maxHeight: Dp, modifier: Modifier) {
    PopupCard(onClose, maxHeight, modifier) {
        Spacer(Modifier.height(18.dp))
        venues.forEach { v ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPick(v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(venueGlyph(venueKind(v)), null, tint = argb(venueKind(v).ring), modifier = Modifier.size(20.dp))
                Text(v.name, color = MapUi.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.event_count.toString(), color = MapUi.muted, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Страна: «ТОП стран» — «#1», «N эвентов · M диджеев», «Топ диджеи» (играют там); «Откуда диджеи» — жанр, «Диджеев отсюда: N»,
 * «Диджеи отсюда» (родом). Список загружается при каждом открытии, как на сайте.
 */
@Composable
internal fun CountryPopup(pop: MapPopup.Country, s: DjMapState, countryName: (String) -> String, onClose: () -> Unit, maxHeight: Dp, modifier: Modifier) {
    val i18n = useI18n()
    val density = s.densityCountries.firstOrNull { it.country_code.equals(pop.iso, true) }
    val origin = s.origins.firstOrNull { it.country_code.equals(pop.iso, true) }
    val isTop = !pop.origins && density != null && density.count == s.densityCountries.maxOfOrNull { it.count }
    val artists by produceState<Pair<List<MapCountryArtist>, Int>?>(null, pop) {
        value = s.countryArtists(pop.iso, pop.origins).getOrNull()?.let { it.artists to it.total } ?: (emptyList<MapCountryArtist>() to 0)
    }
    PopupCard(onClose, maxHeight, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 30.dp)) {
            com.djmetry.ui.components.CountryFlag(pop.iso, 26.dp)
            Text(countryName(pop.iso), color = MapUi.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (isTop) PillBadge("#1", Color(0xFF4A1B0C), TopColor, fontSize = 11.sp, height = 22.dp)
        }
        if (pop.origins) {
            origin?.let { o ->
                dominantGenre(o.genres)?.let { g ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(argb(genreColor(g))))
                        Text(g.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }, color = MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(i18n.tWithArgs(Strings.MAP_DJS_FROM, arrayOf(o.count)), color = MapUi.muted, fontSize = 13.5.sp)
            }
        } else density?.let { Text(i18n.tWithArgs(Strings.MAP_DENSITY_COUNT, arrayOf(it.count, it.djs)), color = MapUi.muted, fontSize = 13.5.sp) }
        HorizontalDivider(color = MapUi.hairline)
        val list = artists
        SectionTitle(i18n.t(if (pop.origins) Strings.MAP_ORIGIN_DJS else Strings.MAP_TOP_DJS), list?.let { maxOf(it.second, it.first.size) }?.takeIf { it > 0 })
        when {
            list == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = MapUi.accent, strokeWidth = 2.dp) }
            list.first.isEmpty() -> Text(i18n.t(Strings.MAP_EMPTY), color = MapUi.text, fontSize = 13.5.sp)
            else -> list.first.forEach { a -> CountryArtistRow(a) }
        }
    }
}

@Composable
private fun CountryArtistRow(a: MapCountryArtist) {
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val openArtist = LocalArtistNavigator.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CoverImage(a.image_url, 48.dp, Modifier.clickable(role = Role.Button) { openArtist(a.spotify_artist_id) }, cornerRadius = 12.dp, placeholderColor = argb(genreColor(a.name))) {
            Text(a.name.take(1), color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.name, color = MapUi.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(role = Role.Button) { openArtist(a.spotify_artist_id) })
            listOfNotNull(a.city, eventDate(a.datetime)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, color = MapUi.muted, fontSize = 12.5.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.ticket_url?.let { ActionButton(i18n.t(Strings.MAP_TICKETS), true) { uri.openUri(it) } }
                ActionButton(i18n.t(Strings.MAP_MUSIC), false) { uri.openUri(a.spotify_url ?: spotifyArtistUrl(a.spotify_artist_id)) }
            }
        }
    }
}

/** Город «ТОП стран»: «Город, Страна» и «N эвентов · M диджеев». */
@Composable
internal fun CityPopup(pop: MapPopup.City, onClose: () -> Unit, maxHeight: Dp, modifier: Modifier) {
    val i18n = useI18n()
    PopupCard(onClose, maxHeight, modifier) {
        Text(listOfNotNull(pop.city.city, pop.city.country).joinToString(", "), color = MapUi.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 30.dp))
        Text(i18n.tWithArgs(Strings.MAP_DENSITY_COUNT, arrayOf(pop.city.count, pop.city.djs)), color = MapUi.muted, fontSize = 13.5.sp)
    }
}
