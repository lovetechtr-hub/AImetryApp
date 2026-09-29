package com.djmetry.ui.djmap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.config.AppConfig
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ArtistSearchItem
import com.djmetry.data.analytics.COUNTRY_CENTROIDS
import com.djmetry.data.analytics.MapPalette
import com.djmetry.data.analytics.mapStyle
import com.djmetry.data.djmap.*
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.cleanCountryName
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.settings.SearchPickerDialog
import com.djmetry.ui.settings.isoToMillis
import com.djmetry.ui.settings.millisToIso
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.LocalDate
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import kotlin.math.floor

/** Открыть карту диджеев поверх экрана: `null` — все DJ, id — тур одного DJ (кнопка на карточке артиста). */
val LocalOpenDjMap = staticCompositionLocalOf<(String?) -> Unit> { {} }

/** Ширина, от которой карточки открываются у точки, а не шторкой снизу. */
private const val FLOATING_POPUP_MIN_DP = 700f

/**
 * Мировая карта диджеев (спека §16, вариант A «как на сайте»): полноэкранная карта MapLibre, сверху поиск DJ, фильтры
 * и тема, под ними лента лидеров слоя, снизу слои «Выступления · ТОП стран · Откуда диджеи · Фестивали и клубы».
 * Тап по точке — карточка как на сайте (на телефоне — мини-шторка, раскрывается свайпом вверх). [initialArtistId] — сразу тур
 * одного DJ; [onBack] — кнопка «назад» (оверлей); [onSwipes] — кнопка «к свайпам» (карта во весь экран на телефоне).
 */
@OptIn(FlowPreview::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DjMapScreen(initialArtistId: String? = null, onBack: (() -> Unit)? = null, onSwipes: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    // Маркеры — Compose-элементы поверх карты: обрезаем по её границам, чтобы не вылезали на шапку
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val compact = maxWidth.value < FLOATING_POPUP_MIN_DP
        val screenH = maxHeight
        val s = remember { DjMapState(container.djMap, scope, initialArtistId, compact) }
        CompositionLocalProvider(LocalMapUi provides if (s.light) MapUiColors.Light else MapUiColors.Dark) {
        Box(Modifier.fillMaxSize().background(MapUi.water))
        val countryNames by produceState(emptyMap<String, String>()) {
            value = container.settings.countries().getOrNull().orEmpty().associate { it.code.uppercase() to cleanCountryName(it.name) }
        }
        val countryName: (String) -> String = { iso -> countryNames[iso.uppercase()] ?: s.densityCountries.firstOrNull { it.country_code.equals(iso, true) }?.country ?: iso }

        // «Назад» сначала закрывает карточку, потом уже уходит с карты
        androidx.compose.ui.backhandler.BackHandler(enabled = s.popup != null) { s.popup = null; s.selectedCountry = null }
        LaunchedEffect(Unit) { s.loadCatalog() }
        LaunchedEffect(s.layer, s.filters, s.artistId) { s.loadStatic() }

        if (androidx.compose.ui.platform.LocalInspectionMode.current) {
            // Превью и тесты: нативный движок карты не поднимаем — только интерфейс поверх
            Box(Modifier.fillMaxSize().background(Color(0xFF101A2C)))
        } else MapCanvas(s, compact, screenH, countryName)

        TopBar(s, onBack, onSwipes, compact)
        BottomLayers(s, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp))
        if (s.layer == MapLayer.Origins) GenreLegend(s, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = LocalBottomClearance.current + 70.dp))
        if (!compact) Column(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = LocalBottomClearance.current + 8.dp).clip(RoundedCornerShape(14.dp))) {
            listOf(Icons.Filled.Add to 1.0, Icons.Filled.Remove to -1.0).forEach { (icon, d) ->
                Icon(icon, null, tint = MapUi.text, modifier = Modifier.size(44.dp).background(MapUi.glass).clickable(role = Role.Button) {
                    s.zoomDelta = d
                }.padding(11.dp))
            }
        }
        if (s.artistId != null && !s.loading && s.points.isEmpty()) EmptyTour(s, Modifier.align(Alignment.Center))
        // Карточка шторкой снизу — на телефонах
        if (compact) s.popup?.let { pop ->
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp).padding(bottom = LocalBottomClearance.current + 8.dp)) {
                MiniSheet(s, pop, countryName, fullMaxHeight = screenH * 0.6f)
            }
        }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MapCanvas(s: DjMapState, compact: Boolean, screenH: Dp, countryName: (String) -> String) {
    val i18n = useI18n()
    val lang = i18n.locale.code.substringBefore('-')
    val style = remember(lang, s.light) { BaseStyle.Json(mapStyle(lang, if (s.light) MapPalette.Light else MapPalette.Dark, streets = true)) }
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = Position(10.0, 25.0), zoom = 1.4),
    ) { DjMapLayers(s) }

    // Камера остановилась — догрузить область (с задержкой, как сайт: 250–300 мс)
    LaunchedEffect(mapState, s.layer, s.filters, s.artistId) {
        snapshotFlow { mapState.cameraPosition to mapState.isCameraMoving }
            .filter { !it.second }.debounce(300)
            .collect { (cam, _) ->
                val vb = mapState.getVisibleBounds()
                s.onCameraIdle(vb?.let { Bounds(it.southwest.longitude, it.southwest.latitude, it.northeast.longitude, it.northeast.latitude) }, cam.zoom)
            }
    }
    LaunchedEffect(s.zoomDelta) {
        val d = s.zoomDelta ?: return@LaunchedEffect
        runCatching { mapState.animateCamera(CameraUpdate(zoom = (mapState.cameraPosition.zoom + d).coerceIn(0.0, 18.0))) }
        s.zoomDelta = null
    }
    LaunchedEffect(s.flyTo) {
        val f = s.flyTo ?: return@LaunchedEffect
        runCatching { mapState.animateCamera(CameraUpdate(target = Position(f.lng, f.lat), zoom = f.zoom), CameraAnimation.Fly()) }
        s.flyTo = null
    }
    // Тур одного DJ: один раз кадрируем все выступления (как сайт: отступы сверху 120, снизу 150, не ближе зума 7)
    LaunchedEffect(s.artistId, s.points) {
        val pts = s.points.takeIf { s.artistId != null && it.isNotEmpty() } ?: return@LaunchedEffect
        if (pts.size == 1) { s.flyTo = FlyTo(pts[0].lat, pts[0].lng, maxOf(5.0, mapState.cameraPosition.zoom)); return@LaunchedEffect }
        runCatching {
            mapState.animateCameraToBounds(
                BoundingBox(pts.minOf { it.lng }, pts.minOf { it.lat }, pts.maxOf { it.lng }, pts.maxOf { it.lat }),
                fitPadding = DpPadding(left = 40.dp, top = 170.dp, right = 40.dp, bottom = 170.dp),
            )
            if (mapState.cameraPosition.zoom > 7) mapState.animateCamera(CameraUpdate(zoom = 7.0))
        }
    }

    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        state = mapState,
        // Атрибуция и центр карты — над слоями и нижней панелью навигации
        viewportInsets = PaddingValues(bottom = LocalBottomClearance.current + 64.dp),
        uiOptions = MapUiOptions.Standard,
        overlay = {
            DjMapMarkers(s)
            // Карточка у точки — на широких экранах; сверху или снизу от точки — чтобы помещалась
            if (!compact) s.popup?.let { pop ->
                val (lat, lng) = popupAnchor(pop)
                val below = (mapState.screenLocationFromPosition(Position(lng, lat))?.y?.value ?: 0f) < screenH.value * 0.45f
                PopupContent(
                    s, pop, countryName, maxHeight = minOf(480.dp, screenH * 0.6f),
                    modifier = Modifier.placedAt(Position(lng, lat), if (below) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(vertical = 30.dp).width(if (pop is MapPopup.Events && pop.points.size == 1) 340.dp else 380.dp),
                )
            }
            // Атрибуция OpenStreetMap / OpenFreeMap обязательна — компактной строкой, не перекрывая карту
            Text(
                "© OpenStreetMap · OpenFreeMap", color = MapUi.muted.copy(alpha = 0.75f), fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = LocalBottomClearance.current + 66.dp),
            )
        },
    )
}

private fun popupAnchor(p: MapPopup): Pair<Double, Double> = when (p) {
    is MapPopup.Events -> p.lat to p.lng
    is MapPopup.Venue -> (p.venue.lat ?: 0.0) to (p.venue.lng ?: 0.0)
    is MapPopup.VenuePick -> p.lat to p.lng
    is MapPopup.Country -> p.lat to p.lng
    is MapPopup.City -> p.city.lat to p.city.lng
}

@Composable
private fun PopupContent(s: DjMapState, pop: MapPopup, countryName: (String) -> String, maxHeight: Dp, modifier: Modifier) {
    val close = { s.popup = null; s.selectedCountry = null }
    when (pop) {
        is MapPopup.Events -> EventsPopup(pop.points, s.artistId != null, { id -> s.popup = null; s.selectArtist(id) }, close, maxHeight, modifier)
        is MapPopup.Venue -> VenuePopup(pop.venue, pop.densityHead, s, close, maxHeight, modifier)
        is MapPopup.VenuePick -> VenuePickPopup(pop.venues, { s.popup = MapPopup.Venue(it) }, close, maxHeight, modifier)
        is MapPopup.Country -> CountryPopup(pop, s, countryName, close, maxHeight, modifier)
        is MapPopup.City -> CityPopup(pop, close, maxHeight, modifier)
    }
}

@Composable
private fun Glass(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(22.dp), content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(MapUi.glass).border(1.dp, MapUi.hairline, shape), content = content)
}

@Composable
private fun RoundButton(icon: ImageVector, badge: Int? = null, onClick: () -> Unit) {
    Box {
        Glass(Modifier.size(46.dp).clickable(role = Role.Button, onClick = onClick), CircleShape.let { RoundedCornerShape(23.dp) }) {
            Icon(icon, null, tint = MapUi.text, modifier = Modifier.align(Alignment.Center).size(22.dp))
        }
        badge?.takeIf { it > 0 }?.let {
            Text(it.toString(), color = MapUi.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(MapUi.accent).padding(horizontal = 5.dp))
        }
    }
}

/** Верх: назад / «Все диджеи», поиск DJ, фильтры, тема, поделиться; ниже — лента лидеров слоя или города тура. */
@OptIn(FlowPreview::class)
@Composable
private fun TopBar(s: DjMapState, onBack: (() -> Unit)?, onSwipes: (() -> Unit)?, compact: Boolean) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val clipboard = LocalClipboardManager.current
    var filtersOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val results by produceState(emptyList<ArtistSearchItem>(), query) {
        value = if (query.trim().length < 2) emptyList() else { delay(250); container.artistApi.search(query.trim(), 8).getOrNull()?.artists.orEmpty() }
    }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onBack?.let { RoundButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = it) }
            onSwipes?.let { RoundButton(Icons.Outlined.Style, onClick = it) }
            if (s.artistId != null) {
                Glass(Modifier.height(46.dp).clickable(role = Role.Button) { s.selectArtist(null) }, RoundedCornerShape(23.dp)) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Public, null, tint = MapUi.accent, modifier = Modifier.size(18.dp))
                        Text(i18n.t(Strings.MAP_ALL_DJS), color = MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                s.points.firstOrNull()?.let { p ->
                    Text(p.artist_name, color = MapUi.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
            } else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Glass(Modifier.fillMaxWidth().widthIn(max = 560.dp).height(46.dp), RoundedCornerShape(23.dp)) {
                Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MapUi.muted, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (query.isEmpty()) Text(i18n.t(Strings.MAP_SEARCH_DJ), color = MapUi.muted, fontSize = 15.sp, maxLines = 1)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = MapUi.text, fontSize = 15.sp), cursorBrush = SolidColor(MapUi.accent), modifier = Modifier.fillMaxWidth())
                    }
                    if (query.isNotEmpty()) Icon(Icons.Filled.Close, null, tint = MapUi.muted, modifier = Modifier.size(20.dp).clickable { query = "" })
                }
            } }
            RoundButton(Icons.Outlined.FilterList, badge = s.filters.activeCount) { filtersOpen = true }
            if (!compact) RoundButton(if (s.light) Icons.Outlined.DarkMode else Icons.Outlined.LightMode) { s.light = !s.light }
            RoundButton(Icons.Outlined.Share) {
                clipboard.setText(AnnotatedString(mapShareUrl(AppConfig.BASE_URL, s.layer, s.filters, s.artistId))); copied = true
            }
        }
        if (results.isNotEmpty()) Glass(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Column(Modifier.padding(8.dp)) {
                results.forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { query = ""; s.selectArtist(a.spotifyArtistId) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverImage(a.imageUrl, 40.dp, cornerRadius = 20.dp) {}
                        Text(a.name, color = MapUi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // На телефоне карточка прячет ленту лидеров, но не ленту тура — по ней переключают города
        if (!(compact && s.popup != null && s.artistId == null)) Leaderboard(s, compact)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.onMap > 0) Chip(i18n.tWithArgs(Strings.MAP_ON_MAP, arrayOf(s.onMap)))
            AnimatedVisibility(s.loading, enter = fadeIn(), exit = fadeOut()) { Chip(i18n.t(Strings.MAP_UPDATING)) }
            AnimatedVisibility(copied, enter = fadeIn(), exit = fadeOut()) { Chip(i18n.t(Strings.MAP_LINK_COPIED), accent = true) }
        }
    }
    if (filtersOpen) FiltersDialog(s) { filtersOpen = false }
}

@Composable
private fun Chip(text: String, accent: Boolean = false) {
    Text(text, color = if (accent) MapUi.bg else MapUi.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(if (accent) MapUi.accent else MapUi.glass).border(1.dp, MapUi.hairline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

/** Лента лидеров слоя (свернуть — стрелкой): гастролирующие DJ, топ стран, топ площадок, города тура. */
@Composable
private fun Leaderboard(s: DjMapState, compact: Boolean) {
    val i18n = useI18n()
    // Лента тура — главная навигация по туру: раскрыта сразу и на телефоне
    var open by remember(s.artistId) { mutableStateOf(!compact || s.artistId != null) }
    val stops = remember(s.tour) { tourStops(s.tour) }
    val items: List<@Composable () -> Unit> = when {
        s.artistId != null -> stops.mapIndexed { i, st ->
            @Composable {
                TourStopItem(st.city, i, stops.size, active = s.activeStop == i) {
                    s.activeStop = i
                    s.popup = null
                    s.flyTo = FlyTo(st.point.lat, st.point.lng, maxOf(s.zoom, 6.0))
                }
            }
        }
        s.layer == MapLayer.Performances -> s.touring.mapIndexed { i, d ->
            @Composable {
                val ring = argb(genreColor(d.spotify_artist_id))
                LeaderItem(onClick = { s.selectArtist(d.spotify_artist_id) }) {
                    RankDot(i + 1, ring)
                    Box(Modifier.size(if (compact) 44.dp else 52.dp).clip(CircleShape).border(2.5.dp, ring, CircleShape)) {
                        CoverImage(d.image_url, if (compact) 44.dp else 52.dp, cornerRadius = 26.dp, placeholderColor = ring) {
                            Text(d.name.take(1), color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    Text(d.name, color = MapUi.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.events} ${i18n.t(Strings.MAP_SHOWS_SHORT)}", color = MapUi.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        s.layer == MapLayer.Density -> s.densityCountries.filter { it.country_code != null }.take(10).mapIndexed { i, c ->
            @Composable {
                val iso = c.country_code!!.uppercase()
                val color = argb(genreColor(iso.lowercase()))
                LeaderItem(onClick = {
                    s.selectedCountry = iso
                    COUNTRY_CENTROIDS[iso]?.let { (lat, lng) -> s.flyTo = FlyTo(lat, lng, 4.0) }
                }) {
                    RankDot(i + 1, color, filled = s.selectedCountry == iso)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        com.djmetry.ui.components.CountryFlag(iso, 18.dp)
                        Text(c.country, color = MapUi.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(c.count.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        s.layer == MapLayer.Venues -> s.topVenues.mapIndexed { i, v ->
            @Composable {
                val kind = venueKind(v)
                val f = if (s.topVenues.size > 1) i.toFloat() / (s.topVenues.size - 1) else 0f
                LeaderItem(onClick = {
                    if (v.lat != null && v.lng != null) { s.flyTo = FlyTo(v.lat, v.lng, maxOf(s.zoom, 14.0)); s.popup = MapPopup.Venue(v) }
                }) {
                    RankDot(i + 1, Color(red = (183 + 72 * f) / 255f, green = 166 / 255f, blue = (255 - 43 * f) / 255f))
                    Box {
                        Box(Modifier.size(if (compact) 44.dp else 52.dp).clip(CircleShape).background(MapUi.bg).border(2.5.dp, argb(kind.ring), CircleShape), contentAlignment = Alignment.Center) {
                            if (v.image_url != null) CoverImage(v.image_url, 52.dp, cornerRadius = 26.dp) {}
                            else Text(v.name.take(1).uppercase(), color = MapUi.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        if (kind == VenueKind.Top) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFB454), modifier = Modifier.align(Alignment.BottomCenter).size(16.dp))
                    }
                    Text(v.name, color = MapUi.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(i18n.t(when (kind) { VenueKind.Top -> Strings.MAP_VENUE_TOP; VenueKind.Festival -> Strings.MAP_VENUE_FESTIVAL; VenueKind.Club -> Strings.MAP_VENUE_CLUB }),
                        color = Color(0xFFFFB454), fontSize = 11.sp, maxLines = 1)
                    Text("${v.event_count} ${i18n.t(Strings.MAP_VENUE_EVENTS_SHORT)}", color = MapUi.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> emptyList()
    }
    if (items.size < 2) return
    val title = when {
        s.artistId != null -> Strings.MAP_TOUR
        s.layer == MapLayer.Venues -> Strings.MAP_TOP_VENUES
        s.layer == MapLayer.Density -> Strings.MAP_LAYER_DENSITY
        else -> Strings.MAP_TOP_DJS
    }
    Glass(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 1400.dp)) { Column {
        if (!open) Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i18n.t(title)} · ${items.size}", color = MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, tint = MapUi.muted)
        } else Row(verticalAlignment = Alignment.Top) {
            val selected = when {
                s.artistId != null -> s.activeStop
                s.layer == MapLayer.Density -> s.densityCountries.filter { it.country_code != null }.take(10).indexOfFirst { it.country_code.equals(s.selectedCountry, true) }
                else -> -1
            }
            CenteringRow(selected, if (s.artistId != null) 0.dp else 4.dp, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp)) {
                items.forEach { it() }
            }
            Icon(Icons.Outlined.ExpandLess, null, tint = MapUi.muted, modifier = Modifier.padding(8.dp).size(28.dp).clip(CircleShape).clickable { open = false }.padding(2.dp))
        }
        // Выбранный город тура — выступление прямо под лентой (как на сайте)
        if (open && s.artistId != null) stops.getOrNull(s.activeStop)?.let { st -> TourStopEvent(s, st) }
    } }
}

/** Под лентой тура: «Город, Страна», площадка, дата, «Билеты» и «Подробнее» (полная карточка). */
@Composable
private fun TourStopEvent(s: DjMapState, st: TourStop) {
    val i18n = useI18n()
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val p = st.point
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { s.popup = MapPopup.Events(listOf(p), p.lat, p.lng) }) {
            Text(listOfNotNull(p.venue_city, p.venue_country).filter { it.isNotBlank() }.joinToString(", "), color = MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOfNotNull(p.venue_name, eventDate(p.datetime)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, color = MapUi.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        p.url?.let { url ->
            Text(i18n.t(Strings.MAP_TICKETS), color = MapUi.bg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MapUi.accent).clickable(role = Role.Button) { uri.openUri(url) }.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

/** Цвет точки тура по положению: лаванда `#B7A6FF` → роза `#FFA6D4`, как на сайте. */
private fun stopColor(i: Int, count: Int): Color {
    val f = if (count > 1) i.toFloat() / (count - 1) else 0f
    return Color(red = (183 + 72 * f) / 255f, green = 166 / 255f, blue = (255 - 43 * f) / 255f)
}

/**
 * Город в ленте тура: точка на линии маршрута (отрезки к соседям — градиентом, как на сайте), название.
 * Выбранный город — зелёная точка со свечением; тап — перелёт к выступлению и его карточка.
 */
@Composable
private fun TourStopItem(city: String, i: Int, count: Int, active: Boolean, onClick: () -> Unit) {
    val dot = stopColor(i, count)
    val prev = stopColor(i - 1, count)
    val next = stopColor(i + 1, count)
    Column(
        Modifier.width(96.dp).drawBehind {
            val y = 4.dp.toPx() + 11.dp.toPx() // центр точки
            val w = 2.dp.toPx()
            if (i > 0) drawLine(Brush.horizontalGradient(listOf(prev, dot), 0f, size.width / 2), Offset(0f, y), Offset(size.width / 2, y), w)
            if (i < count - 1) drawLine(Brush.horizontalGradient(listOf(dot, next), size.width / 2, size.width), Offset(size.width / 2, y), Offset(size.width, y), w)
        }.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (active) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(MapUi.accent.copy(alpha = 0.3f)))
                Box(Modifier.size(14.dp).clip(CircleShape).background(MapUi.accent))
            } else Box(Modifier.size(16.dp).clip(CircleShape).background(MapUi.popup).border(2.5.dp, dot, CircleShape))
        }
        Text(city, color = if (active) MapUi.accent else MapUi.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LeaderItem(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.width(92.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
private fun RankDot(n: Int, color: Color, filled: Boolean = false) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (filled) color else MapUi.popup).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
        Text(n.toString(), color = if (filled) Color(0xFF0B1220) else MapUi.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Слои внизу — пилюли как на сайте; на узком экране прокручиваются, выбранная встаёт по центру. */
@Composable
private fun BottomLayers(s: DjMapState, modifier: Modifier) {
    val i18n = useI18n()
    val layers = listOf(MapLayer.Performances to Strings.MAP_LAYER_PERFORMANCES, MapLayer.Density to Strings.MAP_LAYER_DENSITY,
        MapLayer.Origins to Strings.MAP_LAYER_ORIGINS, MapLayer.Venues to Strings.MAP_LAYER_VENUES)
    Glass(modifier.padding(horizontal = 10.dp), RoundedCornerShape(26.dp)) {
        CenteringRow(selected = layers.indexOfFirst { it.first == s.layer }, spacing = 4.dp, modifier = Modifier.padding(4.dp)) {
            layers.forEach { (l, key) ->
                val on = s.layer == l && (s.artistId == null || l == MapLayer.Performances)
                Text(i18n.t(key), color = if (on) Color(0xFF04241A) else MapUi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (on) Color(0xFF34E0B0) else Color.Transparent)
                        .clickable(role = Role.Tab) { s.artistId = null; s.layer = l; s.selectedCountry = null }.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
}

/**
 * Горизонтальный ряд с прокруткой: выбранный элемент [selected] плавно встаёт по центру (слои, города тура, страны).
 * Позиции детей меряем после раскладки — ширина кнопок зависит от языка и размера шрифта.
 */
@Composable
internal fun CenteringRow(selected: Int, spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(0) }
    val bounds = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    LaunchedEffect(selected, viewport, bounds[selected]) {
        val (x, w) = bounds[selected] ?: return@LaunchedEffect
        if (viewport > 0) scroll.animateScrollTo((x + w / 2 - viewport / 2).coerceIn(0, scroll.maxValue))
    }
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll),
    ) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)) }
        val width = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                bounds[i] = x to p.width
                p.place(x, (height - p.height) / 2); x += p.width + gap
            }
        }
    }
}

/** «Жанры» на слое «Откуда диджеи»: цвета доминирующих жанров и сколько DJ; по умолчанию свёрнута. */
@Composable
private fun GenreLegend(s: DjMapState, modifier: Modifier) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    val legend = remember(s.origins) {
        s.origins.mapNotNull { o -> dominantGenre(o.genres)?.let { it to o.count } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }
    }
    if (legend.isEmpty()) return
    Glass(modifier.widthIn(max = 240.dp), RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Palette, null, tint = MapUi.accent, modifier = Modifier.size(16.dp))
                Text(i18n.t(Strings.MAP_GENRE_LEGEND).uppercase(), color = MapUi.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = MapUi.muted, modifier = Modifier.size(18.dp))
            }
            if (open) Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                legend.forEach { (g, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(argb(genreColor(g))))
                        Text(g, color = MapUi.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.toString(), color = MapUi.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** У DJ ещё нет выступлений на карте — как на сайте, с кнопкой «Все диджеи». */
@Composable
private fun EmptyTour(s: DjMapState, modifier: Modifier) {
    val i18n = useI18n()
    Glass(modifier.padding(24.dp).widthIn(max = 360.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Place, null, tint = MapUi.accent, modifier = Modifier.size(32.dp))
            Text(i18n.t(Strings.MAP_NO_PERFORMANCES_TITLE), color = MapUi.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(i18n.t(Strings.MAP_NO_PERFORMANCES_TEXT), color = MapUi.muted, fontSize = 13.5.sp, textAlign = TextAlign.Center)
            TextButton(onClick = { s.selectArtist(null) }) { Text(i18n.t(Strings.MAP_ALL_DJS), color = MapUi.accent, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Фильтры: жанр, страна (имя, как у Bandsintown), даты, тип площадки, тема; «Сбросить фильтры». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersDialog(s: DjMapState, onClose: () -> Unit) {
    val i18n = useI18n()
    var picker by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MapUi.popup).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(i18n.t(Strings.MAP_FILTERS), color = MapUi.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            FilterRow(i18n.t(Strings.MAP_GENRE), s.filters.genre ?: i18n.t(Strings.MAP_ALL_GENRES)) { picker = "genre" }
            FilterRow(i18n.t(Strings.MAP_COUNTRY), s.filters.country ?: i18n.t(Strings.MAP_ALL_COUNTRIES)) { picker = "country" }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { FilterRow(i18n.t(Strings.MAP_FROM), s.filters.from?.toString() ?: "—") { picker = "from" } }
                Box(Modifier.weight(1f)) { FilterRow(i18n.t(Strings.MAP_TO), s.filters.to?.toString() ?: "—") { picker = "to" } }
            }
            Text(i18n.t(Strings.MAP_VENUE_TYPE_LABEL), color = MapUi.muted, fontSize = 12.5.sp)
            Row(Modifier.clip(CircleShape).background(MapUi.bg).padding(3.dp)) {
                listOf(VenueTypeFilter.All to Strings.MAP_VENUES_ALL, VenueTypeFilter.Festival to Strings.MAP_FESTIVALS_SHORT, VenueTypeFilter.Club to Strings.MAP_CLUBS_ONLY).forEach { (t, key) ->
                    val on = s.filters.type == t
                    Text(i18n.t(key), color = if (on) MapUi.bg else MapUi.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) MapUi.accent else Color.Transparent).clickable { s.filters = s.filters.copy(type = t) }.padding(vertical = 9.dp))
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { s.light = !s.light }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.light) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, null, tint = MapUi.accent)
                Text(i18n.t(if (s.light) Strings.MAP_LIGHT_THEME else Strings.MAP_DARK_THEME), color = MapUi.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { s.filters = MapFilters() }, modifier = Modifier.weight(1f)) { Text(i18n.t(Strings.MAP_RESET_FILTERS), color = MapUi.muted) }
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(MapUi.accent).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Text(i18n.t(Strings.MAP_DONE), color = MapUi.bg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    when (picker) {
        "genre" -> SearchPickerDialog(
            title = i18n.t(Strings.MAP_GENRE), items = s.catalog.genres, label = { it },
            onPick = { s.filters = s.filters.copy(genre = it); picker = null }, onDismiss = { picker = null },
            extra = i18n.t(Strings.MAP_ALL_GENRES) to { s.filters = s.filters.copy(genre = null); picker = null },
        )
        "country" -> SearchPickerDialog(
            title = i18n.t(Strings.MAP_COUNTRY), items = s.catalog.countries, label = { it.country },
            onPick = { s.filters = s.filters.copy(country = it.country); picker = null }, onDismiss = { picker = null },
            extra = i18n.t(Strings.MAP_ALL_COUNTRIES) to { s.filters = s.filters.copy(country = null); picker = null },
        )
        "from", "to" -> {
            val current = if (picker == "from") s.filters.from else s.filters.to
            val state = rememberDatePickerState(initialSelectedDateMillis = current?.toString()?.let(::isoToMillis))
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val d = state.selectedDateMillis?.let { LocalDate.parse(millisToIso(it)) }
                        s.filters = if (picker == "from") s.filters.copy(from = d) else s.filters.copy(to = d); picker = null
                    }) { Text(i18n.t(Strings.MAP_DONE), color = MapUi.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { s.filters = if (picker == "from") s.filters.copy(from = null) else s.filters.copy(to = null); picker = null }) {
                        Text(i18n.t(Strings.MAP_RESET_FILTERS), color = MapUi.muted)
                    }
                },
            ) { DatePicker(state = state) }
        }
    }
}

@Composable
private fun FilterRow(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MapUi.bg).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = MapUi.muted, fontSize = 12.sp)
        Text(value, color = MapUi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
