package com.djmetry.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.djmetry.data.analytics.MapCountry
import com.djmetry.data.analytics.analyticsMapStyle
import com.djmetry.data.analytics.bubblesGeoJson
import com.djmetry.data.analytics.choroplethAlpha
import com.djmetry.resources.Res
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * Зум, при котором мир (512 dp при зуме 0) целиком входит по ширине [widthDp]; не меньше 0.
 * Телефон 343 dp → 0, десктопная карточка 470 dp → 0, полный экран планшета 1180 dp → ≈1.2.
 */
internal fun worldZoom(widthDp: Float): Double = kotlin.math.log2((widthDp / 512f).toDouble()).coerceAtLeast(0.0)

/** Суша без визитов и цвет подсветки страны. */
private val LandColor = Color(0xFF101A2C)

/** Контуры стран (Natural Earth 110m, ~170 КБ) — читаем один раз на запуск. */
private var countriesCache: String? = null

@Composable
internal fun rememberCountriesGeoJson(): String? {
    var json by remember { mutableStateOf(countriesCache) }
    LaunchedEffect(Unit) {
        if (json == null) json = runCatching { Res.readBytes("files/countries.geojson").decodeToString() }.getOrNull()?.also { countriesCache = it }
    }
    return json
}

/**
 * Карта «Где слушают» (MapLibre): тёмные векторные тайлы OpenFreeMap, страны подсвечены по визитам,
 * светящиеся пузыри по странам (площадь ∝ визитам). [interactive] = false — превью в карточке: жесты не доходят
 * до карты (не мешают прокрутке страницы), тап открывает полноэкранную карту.
 */
@Composable
internal fun CountriesMap(countries: List<MapCountry>, interactive: Boolean, widthDp: Float, modifier: Modifier = Modifier, onSelect: (MapCountry?) -> Unit = {}) {
    // Превью и тесты: нативный движок карты не поднимаем
    if (androidx.compose.ui.platform.LocalInspectionMode.current) { Box(modifier.background(Color(0xFF070D18))); return }
    val i18n = useI18n()
    val shapes = rememberCountriesGeoJson()
    val bubbles = remember(countries) { bubblesGeoJson(countries) }
    val style = remember(i18n.locale) { BaseStyle.Json(analyticsMapStyle(i18n.locale.code.substringBefore('-'))) }
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = Position(longitude = 12.0, latitude = 28.0), zoom = worldZoom(widthDp)),
    ) {
        if (shapes != null) {
            val countriesSource = rememberGeoJsonSource(GeoJsonData.JsonString(shapes))
            val fill = if (countries.isEmpty()) const(LandColor) else switch(
                input = feature["iso"],
                cases = countries.map { c -> case(c.iso, const(lerp(LandColor, DJMetryColors.Accent, choroplethAlpha(c.share)))) },
                fallback = const(LandColor),
            )
            FillLayer(id = "countries", source = countriesSource, color = fill, opacity = const(0.9f))
        }
        key(bubbles) {
            val points = rememberGeoJsonSource(GeoJsonData.JsonString(bubbles))
            val k = feature["k"].asNumber()
            // Свечение: большой размытый круг
            CircleLayer(
                id = "bubbles-glow", source = points, color = const(DJMetryColors.Accent), opacity = const(0.35f), blur = const(1f),
                radius = interpolate(linear(), k, 0 to const(8.dp), 1 to const(if (interactive) 46.dp else 26.dp)),
            )
            // Ядро пузыря; тап — выбрать страну
            CircleLayer(
                id = "bubbles", source = points, color = const(DJMetryColors.Accent), opacity = const(0.9f),
                strokeColor = const(DJMetryColors.Background), strokeWidth = const(1.5.dp),
                radius = interpolate(linear(), k, 0 to const(3.dp), 1 to const(if (interactive) 14.dp else 8.dp)),
                onClick = { features ->
                    val iso = features.firstOrNull()?.properties?.get("iso")?.jsonPrimitive?.content
                    onSelect(countries.firstOrNull { it.iso == iso }); ClickResult.Consume
                },
            )
        }
    }
    MaplibreMap(
        modifier = modifier.background(Color(0xFF070D18)),
        state = mapState,
        uiOptions = if (interactive) MapUiOptions.Standard else MapUiOptions.None,
        // Атрибуция OpenStreetMap / OpenFreeMap обязательна: в превью — строкой, на полном экране — кнопкой «i»
        overlay = {
            if (interactive) include(MapOverlay.AttributionOnly)
            else Text(
                "© OpenStreetMap · OpenFreeMap", color = DJMetryColors.Muted.copy(alpha = 0.7f), fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        },
    )
}

/** Карточка карты: превью + кнопка «развернуть»; полноэкранная карта с выбором страны. */
@Composable
internal fun MapPreview(countries: List<MapCountry>, height: Dp, countryName: (String) -> String) {
    var full by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { full = true }) {
        CountriesMap(countries, interactive = false, widthDp = maxWidth.value, modifier = Modifier.fillMaxSize())
        Icon(
            Icons.Outlined.OpenInFull, null, tint = DJMetryColors.Text,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.8f)).padding(8.dp),
        )
    }
    if (full) FullMap(countries, countryName) { full = false }
}

@Composable
private fun FullMap(countries: List<MapCountry>, countryName: (String) -> String, onClose: () -> Unit) {
    var selected by remember { mutableStateOf(countries.firstOrNull()) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
            CountriesMap(countries, interactive = true, widthDp = maxWidth.value, modifier = Modifier.fillMaxSize(), onSelect = { selected = it ?: selected })
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top,
            ) {
                selected?.let { c ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Background.copy(alpha = 0.88f))
                            .border(1.dp, DJMetryColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.djmetry.ui.components.CountryFlag(c.iso, 22.dp)
                            Text(countryName(c.iso), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${groupThousands(c.visits)} · ${ctrLabel(c.visits * 100.0 / countries.sumOf { it.visits }.coerceAtLeast(1))}", color = DJMetryColors.Muted, fontSize = 12.5.sp)
                    }
                } ?: Spacer(Modifier)
                Icon(
                    Icons.Filled.Close, null, tint = DJMetryColors.Text,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(DJMetryColors.Panel).clickable(role = Role.Button, onClick = onClose).padding(11.dp),
                )
            }
        }
    }
}
