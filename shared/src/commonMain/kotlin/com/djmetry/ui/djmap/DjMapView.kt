package com.djmetry.ui.djmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stadium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.MapVenue
import com.djmetry.data.analytics.COUNTRY_CENTROIDS
import com.djmetry.data.djmap.*
import com.djmetry.ui.analytics.rememberCountriesGeoJson
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Position
import kotlin.math.floor
import kotlin.math.sqrt

internal fun argb(c: Long): Color = Color(c.toInt())

/** Цвета маркеров — как на сайте: одиночный — мятная рамка, кластер — янтарная. */
internal val SingleRing = Color(0xFF34E0B0)
internal val ClusterRing = Color(0xFFFFCE6B)
internal val RouteColor = Color(0xFFC9A6FF)
internal val DensityVenueRing = Color(0xFFA78BFA)

/**
 * Слои карты внутри `rememberMapState { … }`: подсветка стран («ТОП стран» — цвет страны по ISO, «Откуда диджеи» —
 * цвет доминирующего жанра; под подписями базовой карты), обводка выбранной страны, дуги маршрута тура.
 */
@Composable
internal fun DjMapLayers(s: DjMapState) {
    val shapes = rememberCountriesGeoJson() ?: return
    val fills: Map<String, Color> = remember(s.layer, s.densityCountries, s.origins) {
        when (s.layer) {
            MapLayer.Density -> {
                val max = s.densityCountries.maxOfOrNull { it.count } ?: 1
                s.densityCountries.mapNotNull { c -> c.country_code?.uppercase()?.let { it to argb(genreColor(it.lowercase())).copy(alpha = densityAlpha(c.count, max)) } }.toMap()
            }
            MapLayer.Origins -> {
                val max = s.origins.maxOfOrNull { it.count } ?: 1
                s.origins.mapNotNull { o ->
                    val g = dominantGenre(o.genres) ?: return@mapNotNull null
                    o.country_code?.uppercase()?.let { it to argb(genreColor(g)).copy(alpha = originAlpha(o.count, max)) }
                }.toMap()
            }
            else -> emptyMap()
        }
    }
    val choropleth = s.layer == MapLayer.Origins || (s.layer == MapLayer.Density && s.densityLevel == DensityLevel.Country)
    val countries = rememberGeoJsonSource(GeoJsonData.JsonString(shapes))
    Anchor.Below("country-labels") {
        FillLayer(
            id = "dj-countries", source = countries, visible = choropleth && fills.isNotEmpty(),
            color = if (fills.isEmpty()) const(Color.Transparent) else switch(
                input = feature["iso"], cases = fills.map { (iso, c) -> case(iso, const(c)) }, fallback = const(Color.Transparent),
            ),
            onClick = { features ->
                val iso = features.firstOrNull()?.properties?.get("iso")?.jsonPrimitive?.content
                if (iso != null && iso in fills) {
                    val (lat, lng) = COUNTRY_CENTROIDS[iso] ?: (0.0 to 0.0)
                    s.selectedCountry = iso
                    s.popup = MapPopup.Country(iso, origins = s.layer == MapLayer.Origins, lat = lat, lng = lng)
                    ClickResult.Consume
                } else ClickResult.Pass
            },
        )
        LineLayer(
            id = "dj-country-selected", source = countries, visible = choropleth && s.selectedCountry != null,
            filter = feature["iso"].asString() eq const(s.selectedCountry ?: "—"),
            color = const(Color.White), width = const(2.6.dp),
        )
    }
    // Маршрут тура одного DJ: дуги пересчитываются при смене целого зума (изгиб — в пикселях, как на сайте)
    val z = floor(s.zoom)
    val arcsJson = remember(s.tour, z) {
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                tourArcs(s.tour, z).forEach { arc ->
                    addJsonObject {
                        put("type", "Feature"); putJsonObject("properties") {}
                        putJsonObject("geometry") { put("type", "LineString"); putJsonArray("coordinates") { arc.line.forEach { (lo, la) -> addJsonArray { add(lo); add(la) } } } }
                    }
                }
            }
        }.toString()
    }
    key(arcsJson) {
        val route = rememberGeoJsonSource(GeoJsonData.JsonString(arcsJson))
        LineLayer(id = "dj-route", source = route, visible = s.artistId != null, color = const(RouteColor), opacity = const(0.95f), width = const(2.6.dp))
    }
}

/**
 * Маркеры поверх карты (Compose-элементы, привязанные к координатам): фото DJ с числом в кластере, пузыри городов,
 * площадки, самолётики на дугах тура. Кластеры — сетка 62 px, пересборка только при смене целого зума.
 */
@Composable
internal fun MapOverlayScope.DjMapMarkers(s: DjMapState) {
    val z = floor(s.zoom)
    when (s.layer) {
        MapLayer.Performances -> {
            val clusters = remember(s.shownPoints, z) { cluster(s.shownPoints, z, { it.lat }, { it.lng }) }
            if (s.artistId != null) {
                remember(s.tour, z) { tourArcs(s.tour, z) }.forEach { arc ->
                    Icon(
                        Icons.Filled.Flight, null, tint = RouteColor,
                        modifier = Modifier.placedAt(Position(arc.mid.first, arc.mid.second)).size(22.dp).rotate(arc.headingDeg.toFloat()),
                    )
                }
            }
            clusters.forEach { c ->
                key(c.key) {
                    val p = c.first
                    PhotoMarker(
                        url = p.artist_image_url, name = p.artist_name, size = if (c.size > 1) 50.dp else 46.dp,
                        ring = if (c.size > 1) ClusterRing else SingleRing, count = c.size.takeIf { it > 1 },
                        modifier = Modifier.placedAt(Position(c.lon, c.lat)),
                    ) { s.popup = MapPopup.Events(c.items, c.lat, c.lon) }
                }
            }
        }
        MapLayer.Venues -> if (s.venueClusters.isNotEmpty()) {
            s.venueClusters.forEach { c ->
                CountBubble(c.count, if (c.top) Color(0xFFFFB454) else SingleRing, (34 + minOf(28, (kotlin.math.log2(c.count + 1.0) * 6).toInt())).dp,
                    Modifier.placedAt(Position(c.lng, c.lat))) { s.flyTo = FlyTo(c.lat, c.lng, minOf(s.zoom + 3, 16.0)) }
            }
        } else VenueMarkers(s, s.venues, ring = null, densityHead = false)
        MapLayer.Density -> when (s.densityLevel) {
            DensityLevel.Country -> Unit
            DensityLevel.City -> {
                val max = s.densityCities.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                s.densityCities.forEach { c ->
                    val color = argb(genreColor((c.country_code ?: c.country)?.lowercase()))
                    CountBubble(c.count, color, (18 + 30 * sqrt(c.count.toDouble() / max)).toInt().dp, Modifier.placedAt(Position(c.lng, c.lat))) {
                        s.popup = MapPopup.City(c)
                    }
                }
            }
            DensityLevel.Venue -> VenueMarkers(s, s.densityVenues, ring = DensityVenueRing, densityHead = true)
        }
        MapLayer.Origins -> Unit
    }
}

@Composable
private fun MapOverlayScope.VenueMarkers(s: DjMapState, venues: List<MapVenue>, ring: Color?, densityHead: Boolean) {
    val z = floor(s.zoom)
    val placed = remember(venues) { venues.filter { it.lat != null && it.lng != null } }
    val clusters = remember(placed, z) { cluster(placed, z, { it.lat!! }, { it.lng!! }) }
    clusters.forEach { c ->
        key(c.key) {
            if (c.size == 1) {
                val v = c.first
                VenueMarker(v, ring, Modifier.placedAt(Position(v.lng!!, v.lat!!))) { s.popup = MapPopup.Venue(v, densityHead) }
            } else {
                val spread = c.items.maxOf { it.lat!! } - c.items.minOf { it.lat!! } + c.items.maxOf { it.lng!! } - c.items.minOf { it.lng!! }
                CountBubble(c.size, if (c.items.any { it.is_top }) Color(0xFFFFB454) else ring ?: SingleRing, 40.dp, Modifier.placedAt(Position(c.lon, c.lat))) {
                    // Одинаковые координаты или максимальное приближение — выбор из списка, иначе приблизить
                    if (spread < 5e-4 || s.zoom >= 15.5) s.popup = MapPopup.VenuePick(c.items, c.lat, c.lon)
                    else s.flyTo = FlyTo(c.lat, c.lon, minOf(s.zoom + 3, 16.0))
                }
            }
        }
    }
}

/** Фото-маркер DJ: круг с фото (без фото — первая буква на цвете жанра), рамка, число в кластере. */
@Composable
internal fun PhotoMarker(url: String?, name: String, size: Dp, ring: Color, count: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape).border(2.dp, ring, CircleShape).clickable(role = Role.Button, onClick = onClick),
        ) {
            CoverImage(url, size, cornerRadius = size / 2, placeholderColor = argb(genreColor(name))) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.6).sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        count?.let {
            Text(
                it.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0xFF0B1220)).padding(horizontal = 5.dp, vertical = 1.dp).widthIn(min = 12.dp),
            )
        }
    }
}

/** Площадка: фото или значок (наушники — клуб, сцена — фестиваль, звезда — топ), рамка по виду. */
@Composable
internal fun VenueMarker(v: MapVenue, ringOverride: Color?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val kind = venueKind(v)
    val ring = ringOverride ?: argb(kind.ring)
    Box(
        modifier.size(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(DJMetryColors.Background).border(2.5.dp, ring, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (v.image_url != null) CoverImage(v.image_url, 48.dp, cornerRadius = 24.dp) {}
        else Icon(venueGlyph(kind), null, tint = ring, modifier = Modifier.size(22.dp))
    }
}

internal fun venueGlyph(kind: VenueKind) = when (kind) {
    VenueKind.Club -> Icons.Outlined.Headphones
    VenueKind.Festival -> Icons.Outlined.Stadium
    VenueKind.Top -> Icons.Outlined.Star
}

/** Круг с числом: пузырь города, серверный или клиентский кластер площадок. */
@Composable
internal fun CountBubble(count: Int, color: Color, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(size).shadow(6.dp, CircleShape).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(count.toString(), color = Color.White, fontSize = if (size < 28.dp) 10.sp else 12.sp, fontWeight = FontWeight.Bold) }
}

/** Команда камере: перелететь к точке с зумом. */
data class FlyTo(val lat: Double, val lng: Double, val zoom: Double)
