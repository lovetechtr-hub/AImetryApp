package com.djmetry.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.data.analytics.ActivityBucket
import com.djmetry.data.analytics.ActivityPoint
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.compactCount
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.Zoom
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.lineSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.Insets
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import kotlin.math.min

/** Цвета серий — одни на весь экран (график, легенда, подсказка). */
internal object SeriesColors {
    val Visits = DJMetryColors.Accent
    val Unique = DJMetryColors.Accent2
    val Clicks = Color(0xFFFFB35B)
    val Artists = Color(0xFFB18CFF)
}

/** Легенда: точка цвета + подпись. */
@Composable
internal fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Text(t, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

/**
 * График активности (Vico): визиты с градиентной заливкой, уникальные и клики линиями. Ведёшь пальцем или мышью —
 * подсказка с датой и значениями. Подписей оси X не больше, чем помещается ([maxLabels]).
 */
@Composable
internal fun ActivityChart(points: List<ActivityPoint>, bucket: ActivityBucket, height: Dp, maxLabels: Int, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    val months = i18n.t(Strings.MONTHS_SHORT)
    val labels = remember(points, bucket, months) { points.map { pointLabel(it.start, bucket, months) } }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        if (points.isNotEmpty()) producer.runTransaction {
            lineSeries {
                series(points.map { it.visits }); series(points.map { it.unique }); series(points.map { it.clicks })
            }
        }
    }
    val axisText = TextStyle(color = DJMetryColors.Muted, fontSize = 11.sp)
    val visitsLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(SeriesColors.Visits)),
        stroke = LineCartesianLayer.LineStroke.Continuous(2.6.dp, StrokeCap.Round),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(Brush.verticalGradient(listOf(SeriesColors.Visits.copy(alpha = 0.34f), Color.Transparent)))),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
    )
    val uniqueLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(SeriesColors.Unique)),
        stroke = LineCartesianLayer.LineStroke.Continuous(2.dp, StrokeCap.Round),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
    )
    val clicksLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(SeriesColors.Clicks)),
        stroke = LineCartesianLayer.LineStroke.Continuous(2.dp, StrokeCap.Round),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
    )
    val names = listOf(i18n.t(Strings.AN_VISITS), i18n.t(Strings.AN_UNIQUE), i18n.t(Strings.AN_CLICKS))
    val colors = listOf(SeriesColors.Visits, SeriesColors.Unique, SeriesColors.Clicks)
    val markerFormatter = remember(labels, names) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val t = targets.firstOrNull() as? LineCartesianLayerMarkerTarget ?: return@ValueFormatter ""
            buildAnnotatedString {
                append(labels.getOrNull(t.x.toInt()).orEmpty())
                t.points.forEachIndexed { i, p ->
                    append("\n")
                    withStyle(SpanStyle(color = colors.getOrElse(i) { DJMetryColors.Text }, fontWeight = FontWeight.Bold)) { append(groupThousands(p.entry.y.toInt())) }
                    append(" ${names.getOrElse(i) { "" }}")
                }
            }
        }
    }
    val step = labelStep(points.size, maxLabels)
    val (top, yStep) = remember(points) { niceAxis(points.maxOfOrNull { maxOf(it.visits, it.unique, it.clicks) } ?: 0) }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(visitsLine, uniqueLine, clicksLine),
                rangeProvider = remember(top) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = top) },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null, tick = null,
                label = rememberAxisLabelComponent(style = axisText),
                guideline = rememberAxisGuidelineComponent(fill = Fill(DJMetryColors.Border)),
                valueFormatter = remember { CartesianValueFormatter { _, v, _ -> compactCount(v.toLong()) } },
                itemPlacer = remember(yStep) { VerticalAxis.ItemPlacer.step({ yStep }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null, tick = null, guideline = null,
                label = rememberAxisLabelComponent(style = axisText),
                valueFormatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.toInt()).orEmpty() } },
                itemPlacer = remember(step) { HorizontalAxis.ItemPlacer.aligned(spacing = { step }) },
            ),
            marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent(
                    style = TextStyle(color = DJMetryColors.Text, fontSize = 12.sp), lineCount = 4,
                    padding = Insets(10.dp, 7.dp, 10.dp, 7.dp),
                    background = rememberShapeComponent(Fill(DJMetryColors.Background), RoundedCornerShape(12.dp), strokeFill = Fill(DJMetryColors.Border), strokeThickness = 1.dp),
                ),
                valueFormatter = markerFormatter,
                // Подсказка у точки, без резерва места над графиком (иначе сверху пустая полоса)
                labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
                guideline = rememberAxisGuidelineComponent(fill = Fill(DJMetryColors.Muted.copy(alpha = 0.4f))),
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Строка рейтинга с полоской: подпись, значение и доля от лидера. */
@Composable
internal fun BarRow(label: String, value: String, fraction: Float, color: Brush, flagIso: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        flagIso?.let { com.djmetry.ui.components.CountryFlag(it, 22.dp, Modifier.padding(end = 10.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DJMetryColors.Text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(value, color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(DJMetryColors.Background)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
            }
        }
    }
}

internal val GreenBar = Brush.horizontalGradient(listOf(Color(0xFF2FBF85), DJMetryColors.Accent))
internal val BlueBar = Brush.horizontalGradient(listOf(Color(0xFF3B5BA8), DJMetryColors.Accent2))

/** Кольцевая диаграмма долей (устройства): дуги с тонким зазором, в центре — доля лидера. */
@Composable
internal fun Donut(parts: List<Pair<Float, Color>>, center: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = min(this.size.width, this.size.height) * 0.13f
            val d = min(this.size.width, this.size.height) - stroke
            val topLeft = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(DJMetryColors.Background, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            var start = -90f
            val gap = if (parts.count { it.first > 0f } > 1) 3f else 0f
            parts.forEach { (f, c) ->
                val sweep = 360f * f
                if (sweep > gap) drawArc(c, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Text(center, color = DJMetryColors.Text, fontSize = (size.value / 6).sp, fontWeight = FontWeight.ExtraBold)
    }
}

internal val ShareColors = listOf(DJMetryColors.Accent, DJMetryColors.Accent2, SeriesColors.Artists, SeriesColors.Clicks, DJMetryColors.Muted)
