package com.djmetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djmetry.api.models.ArtistSearchItem
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.*

@Composable
fun ArtistSearchScreen(
    onSearch: (String) -> Unit,
    searchResults: List<ArtistSearchItem>,
    isLoading: Boolean,
    onArtistClick: (String) -> Unit,
    onClaimArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    var query by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DJMetryColors.Background)
    ) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isNotBlank()) {
                    onSearch(it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(i18n.t(Strings.SEARCH_ARTISTS)) },
            colors = textFieldColors(),
            singleLine = true
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DJMetryColors.Accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(searchResults) { artist ->
                    ArtistSearchItemCard(
                        artist = artist,
                        onClick = { onArtistClick(artist.spotifyArtistId) },
                        onClaimClick = { onClaimArtist(artist.spotifyArtistId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistSearchItemCard(
    artist: ArtistSearchItem,
    onClick: () -> Unit,
    onClaimClick: () -> Unit
) {
    val i18n = useI18n()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DJMetryColors.Panel
        ),
        shape = MaterialTheme.shapes.large,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = DJMetryColors.Text,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${artist.followers} ${i18n.t(Strings.FOLLOWERS)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DJMetryColors.Muted
                )
                
                if (artist.djTier != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${i18n.t(Strings.DJ_TIER)} ${artist.djTier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DJMetryColors.Accent
                    )
                }
            }
            
            Button(
                onClick = onClaimClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DJMetryColors.Accent
                )
            ) {
                Text(i18n.t(Strings.ADD), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DJMetryColors.Text,
    unfocusedTextColor = DJMetryColors.Text,
    focusedBorderColor = DJMetryColors.Accent,
    unfocusedBorderColor = DJMetryColors.Border,
    cursorColor = DJMetryColors.Accent,
)
