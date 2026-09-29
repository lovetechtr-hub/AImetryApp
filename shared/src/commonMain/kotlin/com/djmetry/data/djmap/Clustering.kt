package com.djmetry.data.djmap

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Размер ячейки сетки кластеров — как на сайте (62 px). */
const val CLUSTER_CELL_DP = 62.0

/** Ширина мира в dp при зуме 0 (тайлы MapLibre — 512). */
private const val WORLD_DP = 512.0

/** Точка Web-Mercator в долях мира: x, y ∈ [0, 1). */
internal fun mercator(lon: Double, lat: Double): Pair<Double, Double> {
    val x = (lon + 180.0) / 360.0
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val rad = clampedLat * PI / 180.0
    val y = (1.0 - ln(tan(PI / 4 + rad / 2)) / PI) / 2.0
    return x to y
}

/** Группа точек в одной ячейке: [items] в исходном порядке, центр — среднее координат. */
data class Cluster<T>(val key: Pair<Long, Long>, val items: List<T>, val lat: Double, val lon: Double) {
    val size: Int get() = items.size
    val first: T get() = items.first()
}

/**
 * Кластеризация как на сайте: пиксельная сетка [cellDp] в нормализованной Web-Mercator. Ключ ячейки зависит только
 * от зума (не от сдвига карты) — при перемещении маркеры не «прыгают», перегруппировка только при смене зума.
 * Зум округляется вниз до целого, чтобы мелкие изменения зума не пересобирали кластеры на каждом кадре.
 */
fun <T> cluster(items: List<T>, zoom: Double, lat: (T) -> Double, lon: (T) -> Double, cellDp: Double = CLUSTER_CELL_DP): List<Cluster<T>> {
    val worldDp = WORLD_DP * 2.0.pow(floor(zoom).coerceAtLeast(0.0))
    val groups = LinkedHashMap<Pair<Long, Long>, MutableList<T>>()
    items.forEach { item ->
        val (x, y) = mercator(lon(item), lat(item))
        val key = floor(x * worldDp / cellDp).toLong() to floor(y * worldDp / cellDp).toLong()
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }
    return groups.map { (key, list) -> Cluster(key, list, list.sumOf(lat) / list.size, list.sumOf(lon) / list.size) }
}
