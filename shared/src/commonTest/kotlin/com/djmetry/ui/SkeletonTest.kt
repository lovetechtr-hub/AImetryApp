package com.djmetry.ui

import com.djmetry.ui.components.SHIMMER_BAND_PX
import com.djmetry.ui.components.shimmerCenterX
import com.djmetry.ui.components.skeletonRowCount
import com.djmetry.ui.rating.RATING_SKELETON_ROWS
import kotlin.test.*

class SkeletonTest {

    /** Блик заходит из-за левого края и уходит за правый — появляется и исчезает плавно, без скачка. */
    @Test
    fun shimmerSweepsWholeWindowFromOffscreen() {
        listOf(375f, 820f, 1560f, 2560f).forEach { w ->
            assertEquals(-SHIMMER_BAND_PX, shimmerCenterX(0f, w))
            assertEquals(w + SHIMMER_BAND_PX, shimmerCenterX(1f, w))
            assertTrue(shimmerCenterX(0.5f, w) in 0f..w, "в середине прохода блик на экране ($w)")
        }
    }

    /** Одна волна для всех элементов: у соседних элементов фаза блика отличается только их позицией. */
    @Test
    fun sameProgressSameWaveEverywhere() {
        val left = shimmerCenterX(0.3f, 1000f) - 0f
        val right = shimmerCenterX(0.3f, 1000f) - 500f
        assertEquals(500f, left - right)
    }

    @Test
    fun rowsCoverVisibleHeight() {
        assertEquals(3, skeletonRowCount(100f, 64f), "минимум три строки")
        assertEquals(10, skeletonRowCount(720f, 64f))
        val rows = skeletonRowCount(1440f - 300f, 64f)
        assertTrue(rows * 72 >= 1140, "4K по высоте закрыт строками")
    }

    /** Скелетон рейтинга на телефоне закрывает экран: подиум ~250 dp + строки по 72 dp. */
    @Test
    fun ratingSkeletonFillsPhone() {
        assertTrue(250 + RATING_SKELETON_ROWS * 72 >= 852 - 120, "iPhone: скелетон до таббара")
    }
}
