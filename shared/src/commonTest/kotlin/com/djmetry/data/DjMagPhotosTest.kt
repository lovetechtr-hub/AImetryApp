package com.djmetry.data

import com.djmetry.api.models.DJMagAllResponse
import com.djmetry.api.models.DJMagRanking
import com.djmetry.data.repository.RatingRepository
import kotlin.test.*

/** DJ Mag: бэкенд отдаёт фото только за 2020 и 2025 — для остальных лет берём фото того же артиста из других лет. */
class DjMagPhotosTest {
    @Test
    fun photosFromOtherYears() {
        val all = DJMagAllResponse(
            years = listOf(2017, 2020, 2025),
            rankings = mapOf(
                "2017" to listOf(DJMagRanking(1, "Martin Garrix", "g"), DJMagRanking(2, "Oldie", "o")),
                "2020" to listOf(DJMagRanking(1, "Martin Garrix", "g", imageUrl = "old.jpg")),
                "2025" to listOf(DJMagRanking(2, "Martin Garrix", "g", imageUrl = "new.jpg")),
            ),
        )
        val photos = RatingRepository.djMagPhotos(all)
        assertEquals("new.jpg", photos["g"], "самое свежее фото")
        val rows = RatingRepository.fromDjMag(all.rankings.getValue("2017"), photos)
        assertEquals(listOf("new.jpg", null), rows.map { it.imageUrl })
    }
}
