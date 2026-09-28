package com.djmetry.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HASH_START_SCALE = 1.3f
private const val WORD_HIDDEN_SHIFT = 0.81f
private val HashMoveEasing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)
private val WordSlideEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)

/**
 * Сплэш DJMetry: знак «#» появляется крупно в центре, уходит на своё место
 * в логотипе, а надпись «DJMetry» выезжает из-под него.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hashAlpha = remember { Animatable(0f) }
    val hashMove = remember { Animatable(0f) }
    val wordSlide = remember { Animatable(0f) }
    val loading = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { loading.animateTo(1f, tween(2400, easing = FastOutSlowInEasing)) }
        launch { hashAlpha.animateTo(1f, tween(260)) }
        launch { hashMove.animateTo(1f, tween(520, delayMillis = 780, easing = HashMoveEasing)) }
        launch { wordSlide.animateTo(1f, tween(730, delayMillis = 1090, easing = WordSlideEasing)) }
        delay(2600)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DJMetryColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(DJMetryLogo.ASPECT_RATIO)
        ) {
            // Надпись видна только правее знака — так она «выезжает из-под» него
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(left = size.width * DJMetryLogo.HASH_END_FRACTION) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                Image(
                    imageVector = DJMetryLogo.Wordmark,
                    contentDescription = "DJMetry",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -WORD_HIDDEN_SHIFT * size.width * (1f - wordSlide.value)
                        }
                )
            }

            Image(
                imageVector = DJMetryLogo.Hash,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val remaining = 1f - hashMove.value
                        alpha = hashAlpha.value
                        transformOrigin = TransformOrigin(
                            DJMetryLogo.HASH_CENTER_X_FRACTION,
                            DJMetryLogo.HASH_CENTER_Y_FRACTION
                        )
                        val scale = 1f + (HASH_START_SCALE - 1f) * remaining
                        scaleX = scale
                        scaleY = scale
                        // Старт: центр знака совпадает с центром экрана
                        translationX = (0.5f - DJMetryLogo.HASH_CENTER_X_FRACTION) * size.width * remaining
                        translationY = (0.5f - DJMetryLogo.HASH_CENTER_Y_FRACTION) * size.height * remaining
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .width(120.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DJMetryColors.Border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(loading.value)
                    .background(DJMetryColors.Accent)
            )
        }
    }
}
