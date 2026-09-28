package com.djmetry.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingContainer(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScreen(onComplete = onComplete, modifier = modifier.fillMaxSize())
}
