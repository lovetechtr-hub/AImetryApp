package com.aimetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimetry.i18n.Strings
import com.aimetry.ui.i18n.useI18n
import com.aimetry.ui.theme.*

@Composable
fun LoginTypeScreen(
    onSelectUserLogin: () -> Unit,
    onSelectArtistLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AimetryColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = i18n.t(Strings.APP_NAME),
                style = MaterialTheme.typography.displayLarge,
                color = AimetryColors.Text,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = i18n.t(Strings.LOGIN_TYPE_TITLE),
                style = MaterialTheme.typography.headlineMedium,
                color = AimetryColors.Muted
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Кнопка входа как пользователь
            Button(
                onClick = onSelectUserLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AimetryColors.Panel,
                    contentColor = AimetryColors.Text
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = i18n.t(Strings.LOGIN_AS_USER),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            // Кнопка входа как артист
            Button(
                onClick = onSelectArtistLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AimetryColors.Panel,
                    contentColor = AimetryColors.Text
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = i18n.t(Strings.LOGIN_AS_ARTIST),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

