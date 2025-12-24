package com.aimetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimetry.i18n.Strings
import com.aimetry.ui.i18n.useI18n
import com.aimetry.ui.theme.*

@Composable
fun MainScreen(
    userName: String?,
    collectionCount: Int,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${i18n.t(Strings.WELCOME)}, ${userName ?: "User"}!",
                style = MaterialTheme.typography.headlineMedium,
                color = AimetryColors.Text,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = AimetryColors.Panel
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = i18n.t(Strings.YOUR_COLLECTION),
                        style = MaterialTheme.typography.titleMedium,
                        color = AimetryColors.Text
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "$collectionCount ${i18n.t(Strings.ARTISTS_COUNT)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AimetryColors.Accent
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onSearchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AimetryColors.Accent
                )
            ) {
                Text(
                    text = i18n.t(Strings.FIND_ARTISTS),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

