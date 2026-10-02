package com.djmetry.ui

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.DjMapApi
import com.djmetry.data.djmap.Bounds
import com.djmetry.data.djmap.MapLayer
import com.djmetry.data.djmap.normalizedBounds
import com.djmetry.data.djmap.tourFrame
import com.djmetry.data.repository.DjMapRepository
import com.djmetry.ui.djmap.DjMapState
import com.djmetry.ui.djmap.MapPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

/** Карта DJ: рамка тура, область через линию перемены дат, переключение слоёв из тура. */
class DjMapFixesTest {
    @Test
    fun tourAcrossPacificIsFramedThroughPacific() {
        // Лос-Анджелес (-118) и Токио (139): через Тихий океан короче, чем через Европу
        val f = tourFrame(listOf(34.0 to -118.0, 35.7 to 139.7))!!
        assertEquals(139.7, f.west, 1e-9)
        assertEquals(242.0, f.east, 1e-9, "восток > 180 — MapLibre рисует через антимеридиан")
        // Европейский тур — обычная рамка
        val eu = tourFrame(listOf(52.5 to 13.4, 41.4 to 2.2))!!
        assertEquals(2.2, eu.west, 1e-9); assertEquals(13.4, eu.east, 1e-9)
        assertNull(tourFrame(emptyList()))
    }

    @Test
    fun wrappedOrCrossingViewportLoadsWholeWorld() {
        assertEquals(Bounds(-180.0, -10.0, 180.0, 10.0), normalizedBounds(170.0, -10.0, -170.0, 10.0), "запад > восток")
        assertEquals(Bounds(-180.0, -10.0, 180.0, 10.0), normalizedBounds(150.0, -10.0, 200.0, 10.0), "через 180")
        // Мир прокручен на оборот: та же область, что и без прокрутки
        val b = normalizedBounds(370.0, 40.0, 390.0, 55.0)
        assertEquals(10.0, b.west, 1e-9); assertEquals(30.0, b.east, 1e-9)
        assertEquals(Bounds(2.0, 40.0, 20.0, 55.0), normalizedBounds(2.0, 40.0, 20.0, 55.0))
    }

    @Test
    fun layerFromTourGoesToAllDjsAndClosesPopup() {
        val s = DjMapState(DjMapRepository(DjMapApi(FakeBackend(emptyMap()).client())), CoroutineScope(Dispatchers.Unconfined), "dj1", compact = true)
        s.popup = MapPopup.VenuePick(emptyList(), 0.0, 0.0)
        s.selectLayer(MapLayer.Venues)
        assertNull(s.artistId, "из тура — ко всем DJ")
        assertEquals(MapLayer.Venues, s.layer)
        assertNull(s.popup)
        assertTrue(s.points.isEmpty(), "точки тура не висят до загрузки области")
    }
}
