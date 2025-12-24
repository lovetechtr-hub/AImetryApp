package com.aimetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimetry.i18n.Strings
import com.aimetry.ui.i18n.useI18n
import com.aimetry.ui.theme.AimetryColors

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: String = "🎵"
)

@Composable
fun OnboardingScreen(
    page: OnboardingPage,
    currentPage: Int,
    totalPages: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AimetryColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верхняя часть с кнопкой пропустить
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = i18n.t(Strings.ONBOARDING_SKIP),
                        color = AimetryColors.Muted
                    )
                }
            }
            
            // Центральная часть с контентом
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Иконка
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AimetryColors.Accent,
                                    AimetryColors.Accent2
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = page.icon,
                        fontSize = 64.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Заголовок
                Text(
                    text = page.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AimetryColors.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Описание
                Text(
                    text = page.description,
                    fontSize = 16.sp,
                    color = AimetryColors.Muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Нижняя часть с индикатором и кнопкой
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Индикатор страниц
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    repeat(totalPages) { index ->
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (index == currentPage) 32.dp else 8.dp,
                                    height = 8.dp
                                )
                                .background(
                                    color = if (index == currentPage) {
                                        AimetryColors.Accent
                                    } else {
                                        AimetryColors.Border
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
                
                // Кнопка "Далее"
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AimetryColors.Accent
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (currentPage == totalPages - 1) {
                            i18n.t(Strings.SIGN_IN)
                        } else {
                            i18n.t(Strings.NEXT)
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

