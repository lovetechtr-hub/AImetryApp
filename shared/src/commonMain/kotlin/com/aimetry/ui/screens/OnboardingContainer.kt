package com.aimetry.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aimetry.i18n.Strings
import com.aimetry.ui.i18n.useI18n

@Composable
fun OnboardingContainer(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    var currentPage by remember { mutableStateOf(0) }
    
    // Создаем страницы динамически, не используя remember
    val pages = listOf(
        OnboardingPage(
            title = i18n.t(Strings.ONBOARDING_PAGE_1_TITLE),
            description = i18n.t(Strings.ONBOARDING_PAGE_1_DESC),
            icon = "🎵"
        ),
        OnboardingPage(
            title = i18n.t(Strings.ONBOARDING_PAGE_2_TITLE),
            description = i18n.t(Strings.ONBOARDING_PAGE_2_DESC),
            icon = "📊"
        ),
        OnboardingPage(
            title = i18n.t(Strings.ONBOARDING_PAGE_3_TITLE),
            description = i18n.t(Strings.ONBOARDING_PAGE_3_DESC),
            icon = "✨"
        )
    )
    
    OnboardingScreen(
        page = pages[currentPage],
        currentPage = currentPage,
        totalPages = pages.size,
        onNext = {
            if (currentPage < pages.size - 1) {
                currentPage++
            } else {
                onComplete()
            }
        },
        onSkip = {
            onComplete()
        },
        modifier = modifier.fillMaxSize()
    )
}

