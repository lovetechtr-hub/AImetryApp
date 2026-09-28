package com.djmetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.ArtistSearchItem
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.RankedArtist
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.readableWidth
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun TabTitle(text: String) {
    Text(
        text, color = DJMetryColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
    )
}

// ───────── Рейтинг: DJMetry TOP 100 ─────────

@Composable
fun RatingTab(listState: LazyListState) {
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    val top by produceState<List<RankedArtist>?>(null) {
        value = container.artistApi.topN(1).getOrNull()?.artists.orEmpty()
    }
    Column(Modifier.readableWidth()) {
        TabTitle("DJMetry TOP 100")
        val artists = top
        if (artists == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DJMetryColors.Accent) }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomClearance.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(artists, key = { _, a -> a.spotifyArtistId }) { index, artist ->
                    ArtistRow(
                        rank = artist.position ?: index + 1,
                        imageUrl = artist.imageUrl,
                        name = artist.name,
                        subtitle = artist.genres.firstOrNull(),
                        trailing = artist.djmetryScore?.let(::formatScore),
                        change = artist.rankChange,
                        onClick = { openArtist(artist.spotifyArtistId) },
                    )
                }
            }
        }
    }
}

// ───────── Поиск (центральная кнопка #) ─────────

@Composable
fun SearchTab() {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ArtistSearchItem>?>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(350) // дебаунс ввода
        results = null
        results = container.artistApi.search(query.trim(), limit = 20).getOrNull()?.artists.orEmpty()
    }

    Column(Modifier.readableWidth()) {
        TabTitle(i18n.t(Strings.TAB_SEARCH))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = DJMetryColors.Muted) },
            placeholder = { Text(i18n.t(Strings.SEARCH_HINT), color = DJMetryColors.Muted) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DJMetryColors.Text, unfocusedTextColor = DJMetryColors.Text,
                focusedContainerColor = DJMetryColors.Panel, unfocusedContainerColor = DJMetryColors.Panel,
                focusedBorderColor = DJMetryColors.Accent, unfocusedBorderColor = DJMetryColors.Border,
                cursorColor = DJMetryColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        val list = results
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DJMetryColors.Accent) }
            list.isEmpty() && query.isNotBlank() -> Text(i18n.t(Strings.SEARCH_EMPTY), color = DJMetryColors.Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomClearance.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.spotifyArtistId }) { artist ->
                    ArtistRow(
                        rank = null,
                        imageUrl = artist.imageUrl,
                        name = artist.name,
                        subtitle = artist.genres.firstOrNull() ?: artist.djTier?.let { "DJ Tier $it" },
                        trailing = null,
                        change = null,
                        onClick = { openArtist(artist.spotifyArtistId) },
                    )
                }
            }
        }
    }
}

// ───────── Радары (скоро) ─────────

@Composable
fun RadarsTab() {
    val i18n = useI18n()
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.Center,
    ) {
        RadarsIllustration(active = true)
        Spacer(Modifier.height(24.dp))
        Text(i18n.t(Strings.RADARS_SOON_TITLE), color = DJMetryColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(i18n.t(Strings.RADARS_SOON_TEXT), color = DJMetryColors.Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ───────── Букинг (скоро) ─────────

@Composable
fun BookingTab() {
    val i18n = useI18n()
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.Center,
    ) {
        BookingIllustration(active = true)
        Spacer(Modifier.height(24.dp))
        Text(i18n.t(Strings.BOOKING_SOON_TITLE), color = DJMetryColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(i18n.t(Strings.BOOKING_SOON_TEXT), color = DJMetryColors.Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ArtistRow(
    rank: Int?,
    imageUrl: String?,
    name: String,
    subtitle: String?,
    trailing: String?,
    change: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Panel).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text("$rank", color = if (rank <= 3) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
        }
        CoverImage(imageUrl, 48.dp, cornerRadius = 13.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Text(it, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        when (change) {
            "up" -> Icon(Icons.Filled.ArrowDropUp, null, tint = DJMetryColors.Accent)
            "down" -> Icon(Icons.Filled.ArrowDropDown, null, tint = DJMetryColors.LowScore)
            else -> if (trailing != null) Spacer(Modifier.width(24.dp))
        }
    }
}
