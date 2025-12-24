package com.aimetry.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimetry.ui.theme.AimetryColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = rememberInfiniteTransition(label = "splash_alpha")
    val alpha by alphaAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000) // Показываем splash 2 секунды
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AimetryColors.PurpleGradientStart,
                        AimetryColors.PurpleGradientEnd,
                        AimetryColors.Background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AImetry",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = AimetryColors.Text.copy(alpha = alpha),
            modifier = Modifier.padding(16.dp)
        )
    }
}

