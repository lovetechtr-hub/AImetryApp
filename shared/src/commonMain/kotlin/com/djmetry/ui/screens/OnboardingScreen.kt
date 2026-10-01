package com.djmetry.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch

private class OnboardingPage(
    val titleKey: String,
    val descKey: String,
    val illustration: @Composable (active: Boolean) -> Unit,
)

private val pages = listOf(
    OnboardingPage(Strings.OB1_TITLE, Strings.OB1_DESC) { RatingIllustration(it) },
    OnboardingPage(Strings.OB2_TITLE, Strings.OB2_DESC) { RadarsIllustration(it) },
    OnboardingPage(Strings.OB3_TITLE, Strings.OB3_DESC) { ArtistToolsIllustration(it) },
    OnboardingPage(Strings.OB4_TITLE, Strings.OB4_DESC) { BookingIllustration(it) },
)

/**
 * Онбординг DJMetry: 4 экрана (рейтинг, радары, инструменты артиста, букинг),
 * листаются свайпом или кнопкой. Сверху прогресс в стиле сторис.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = useI18n()
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex
    // «Назад» — на прошлую страницу, а не выход из приложения посреди знакомства
    androidx.compose.ui.backhandler.BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    // На планшете контент по центру и не шире 600dp (docs/RULES.md)
    Box(modifier.fillMaxSize().background(DJMetryColors.Background), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ProgressSegments(count = pages.size, current = pagerState.currentPage)

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(DJMetryLogo.HashMark, contentDescription = "DJMetry", modifier = Modifier.size(26.dp, 23.5.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onComplete) {
                Text(i18n.t(Strings.ONBOARDING_SKIP), color = DJMetryColors.Muted, fontSize = 14.sp)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize()) {
                // Иллюстрация занимает свободное место и обрезается на маленьких экранах,
                // а не наезжает на текст
                Box(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    page.illustration(pagerState.currentPage == index)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    i18n.t(page.titleKey),
                    color = DJMetryColors.Text,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    i18n.t(page.descKey),
                    color = DJMetryColors.Muted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        Button(
            onClick = {
                if (isLast) onComplete()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DJMetryColors.Accent,
                contentColor = DJMetryColors.Background,
            ),
        ) {
            Text(
                if (isLast) i18n.t(Strings.OB_START) else i18n.t(Strings.NEXT),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!isLast) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
    }
}

@Composable
private fun ProgressSegments(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            val color by animateColorAsState(
                if (index <= current) DJMetryColors.Accent else DJMetryColors.Border,
                label = "segment",
            )
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
