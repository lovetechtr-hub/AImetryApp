package com.djmetry.ui

import com.djmetry.EdtScene
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.djmetry.ui.artist.ArtistSkeleton
import com.djmetry.ui.components.ShimmerProvider
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.profile.ProfileSkeleton
import com.djmetry.ui.rating.PodiumSkeleton
import com.djmetry.ui.rating.RatingRowSkeleton
import com.djmetry.ui.theme.DJMetryColors
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Скелетоны рисуются без сети на ширинах телефона и десктопа; PNG — для глаз (build/skeletons).
 * Отключён: ImageComposeScene с бесконечной анимацией блика зависает после нескольких кадров (как ProfileScreenshotTest) —
 * задача в журнале. Запуск вручную: убрать @Ignore и `./gradlew :shared:desktopTest --tests '*SkeletonRenderTest'`.
 */
@Ignore
class SkeletonRenderTest {
    private val out = File("build/skeletons").apply { mkdirs() }

    private fun render(name: String, w: Int, h: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = EdtScene(w, h, Density(1f)) {
            ShimmerProvider { Box(Modifier.fillMaxSize().background(DJMetryColors.Background)) { content() } }
        }
        try {
            val img = scene.render(700_000_000L) // середина прохода блика
            val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 1000, "$name: пустая картинка")
        } finally { scene.close() }
    }

    @Test fun ratingPhone() = render("rating-phone", 390, 844) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { PodiumSkeleton(); repeat(6) { RatingRowSkeleton(it, wide = false) } }
    }

    @Test fun ratingDesktop() = render("rating-desktop", 1100, 800) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { PodiumSkeleton(); repeat(6) { RatingRowSkeleton(it, wide = true) } }
    }

    @Test fun artistPhone() = render("artist-phone", 390, 844) { ArtistSkeleton(LayoutClass.Compact) }
    @Test fun artistDesktop() = render("artist-desktop", 1440, 900) { ArtistSkeleton(LayoutClass.Expanded) }
    @Test fun profilePhone() = render("profile-phone", 390, 844) { ProfileSkeleton(LayoutClass.Compact) }
    @Test fun profileTablet() = render("profile-tablet", 820, 1180) { ProfileSkeleton(LayoutClass.Medium) }
}
