package com.djmetry.data

import com.djmetry.data.djmap.cluster
import com.djmetry.data.djmap.mercator
import kotlin.test.*

class ClusteringTest {
    private data class P(val name: String, val lat: Double, val lon: Double)
    private val berlin = P("berlin", 52.52, 13.40)
    private val potsdam = P("potsdam", 52.39, 13.06)
    private val paris = P("paris", 48.85, 2.35)
    private val nyc = P("nyc", 40.71, -74.0)

    @Test
    fun mercatorBounds() {
        assertEquals(0.5 to 0.5, mercator(0.0, 0.0))
        val (x, y) = mercator(180.0, 89.0)
        assertEquals(1.0, x); assertEquals(0.0, y, 1e-6, "широта обрезается на ±85°")
    }

    @Test
    fun nearbyPointsMergeFarOnesDoNot() {
        val c = cluster(listOf(berlin, potsdam, paris, nyc), zoom = 3.0, lat = { it.lat }, lon = { it.lon })
        assertEquals(3, c.size)
        val de = c.first { it.first == berlin }
        assertEquals(listOf(berlin, potsdam), de.items, "порядок точек сохраняется")
        assertEquals((52.52 + 52.39) / 2, de.lat, 1e-9)
    }

    @Test
    fun zoomInSplitsClusters() {
        val far = cluster(listOf(berlin, potsdam, paris), zoom = 1.0, lat = { it.lat }, lon = { it.lon })
        val near = cluster(listOf(berlin, potsdam, paris), zoom = 9.0, lat = { it.lat }, lon = { it.lon })
        assertTrue(far.size < near.size); assertEquals(3, near.size)
    }

    @Test
    fun fractionalZoomDoesNotRegroup() {
        val a = cluster(listOf(berlin, potsdam, paris), zoom = 4.1, lat = { it.lat }, lon = { it.lon }).map { it.key }
        val b = cluster(listOf(berlin, potsdam, paris), zoom = 4.9, lat = { it.lat }, lon = { it.lon }).map { it.key }
        assertEquals(a, b, "ключ ячейки зависит только от целого зума")
    }
}

/** Свайп карточки карты: далеко или быстро — срабатывает, иначе возвращается на место. */
class SwipeCardTest {
    @Test
    fun decisions() {
        assertEquals(com.djmetry.ui.djmap.SwipeResult.Stay, com.djmetry.ui.djmap.swipeResult(30f, 100f), "чуть потянул — пружинит обратно")
        assertEquals(com.djmetry.ui.djmap.SwipeResult.Down, com.djmetry.ui.djmap.swipeResult(120f, 0f), "далеко вниз — свернуть/закрыть")
        assertEquals(com.djmetry.ui.djmap.SwipeResult.Down, com.djmetry.ui.djmap.swipeResult(20f, 1500f), "быстрый смах вниз срабатывает и на коротком пути")
        assertEquals(com.djmetry.ui.djmap.SwipeResult.Up, com.djmetry.ui.djmap.swipeResult(-60f, 0f), "вверх — раскрыть (порог меньше: вверх тянется туже)")
        assertEquals(com.djmetry.ui.djmap.SwipeResult.Up, com.djmetry.ui.djmap.swipeResult(-5f, -1200f))
    }
}
