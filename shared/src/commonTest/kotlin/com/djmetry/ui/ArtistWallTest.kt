package com.djmetry.ui

import com.djmetry.ui.screens.wallGrid
import kotlin.test.*

/** Стена артистов на экране входа: на любом экране закрывает всю область и крутится без конца. */
class ArtistWallTest {

    // стена = 58% высоты экрана
    private val screens = mapOf(
        "iPhone SE" to (375f to 667f * 0.58f),
        "iPhone 15" to (393f to 852f * 0.58f),
        "iPad портрет" to (820f to 1180f * 0.58f),
        "iPad альбом" to (1180f to 820f * 0.58f),
        "десктоп (скриншот)" to (1560f to 980f * 0.58f),
        "десктоп 4K" to (2560f to 1440f * 0.58f),
    )

    @Test
    fun coversWidthWithMarginForRotation() = screens.forEach { (name, size) ->
        val g = wallGrid(size.first, size.second, count = 18)
        assertTrue(g.gridWidthDp >= size.first * 1.3f, "$name: сетка ${g.gridWidthDp} уже экрана с запасом")
    }

    @Test
    fun onePassIsTallerThanWallSoItNeverEnds() = screens.forEach { (name, size) ->
        val g = wallGrid(size.first, size.second, count = 18)
        // После сдвига на целый проход вторая копия должна закрыть стену вместе с запасом сверху
        assertTrue(g.setHeightDp >= size.second + g.topOverscanDp, "$name: проход ${g.setHeightDp} ниже стены ${size.second}")
    }

    @Test
    fun coverSizeDoesNotBlowUpOnWideScreens() = screens.forEach { (name, size) ->
        val g = wallGrid(size.first, size.second, count = 18)
        assertTrue(g.cellDp in 110f..150f, "$name: обложка ${g.cellDp} dp")
    }

    @Test
    fun imagesCycleAndNeighboursDiffer() {
        val g = wallGrid(1560f, 568f, count = 18)
        val all = (0 until g.rowsPerSet).flatMap { r -> (0 until g.columns).map { c -> g.imageIndex(r, c, 18) } }
        assertTrue(all.all { it in 0 until 18 })
        assertEquals((0 until 18).toSet(), all.toSet(), "используются все обложки")
        for (r in 1 until g.rowsPerSet) for (c in 0 until g.columns)
            assertNotEquals(g.imageIndex(r - 1, c, 18), g.imageIndex(r, c, 18), "одна и та же обложка друг под другом")
        assertEquals(0, wallGrid(390f, 460f, count = 0).imageIndex(3, 2, 0))
    }
}
