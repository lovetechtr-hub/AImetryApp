package com.djmetry.ui.screens

import com.djmetry.ui.components.textInput
import com.djmetry.ui.components.SkeletonListRow
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// ───────── Поиск (центральная кнопка #) ─────────

@Composable
fun SearchTab() {
    val i18n = useI18n()
    val openArtist = LocalArtistNavigator.current
    // Состояние — во ViewModel: запрос и результаты не сбрасываются при смене вкладки и повороте
    val vm = com.djmetry.ui.search.appViewModel<com.djmetry.ui.search.SearchViewModel>()
    val state by vm.state.collectAsState()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val query = state.query

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Планшет и десктоп: история слева, «Популярно сейчас» справа (вариант A)
        val wide = maxWidth.value >= SEARCH_TWO_COLUMNS_DP
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(if (wide) Modifier.weight(1f) else Modifier.readableWidth()) {
                TabTitle(i18n.t(Strings.TAB_SEARCH))
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::onQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = DJMetryColors.Muted) },
                    placeholder = { Text(i18n.t(Strings.SEARCH_HINT), color = DJMetryColors.Muted) },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.submit(); focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DJMetryColors.Text, unfocusedTextColor = DJMetryColors.Text,
                        focusedContainerColor = DJMetryColors.Panel, unfocusedContainerColor = DJMetryColors.Panel,
                        focusedBorderColor = DJMetryColors.Accent, unfocusedBorderColor = DJMetryColors.Border,
                        cursorColor = DJMetryColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).textInput(),
                )
                Spacer(Modifier.height(12.dp))
                val list = state.results
                when {
                    // Пустое поле — история: запросы и «Вы открывали»
                    query.isBlank() -> com.djmetry.ui.search.SearchHistoryPanel(
                        com.djmetry.data.search.SearchScope.Artists, onPick = vm::useQuery, onInsert = vm::onQuery,
                        onOpenArtist = { a -> openArtist(a.id) },
                        modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current),
                    )
                    list == null -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(6) { SkeletonListRow(it, leading = false, cover = 48.dp, trailing = false) } }
                    state.failed -> Text(
                        "${i18n.t(Strings.HOME_ERROR)} · ${i18n.t(Strings.HOME_RETRY)}", color = DJMetryColors.Accent, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.retry() }.padding(16.dp),
                    )
                    list.isEmpty() -> Text(i18n.t(Strings.SEARCH_EMPTY), color = DJMetryColors.Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
                    else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomClearance.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.spotifyArtistId }) { artist ->
                            ArtistRow(
                                rank = null,
                                imageUrl = artist.imageUrl,
                                name = artist.name,
                                subtitle = artist.genres.firstOrNull() ?: artist.djTier?.let { "DJ Tier $it" },
                                trailing = null,
                                change = null,
                                onClick = { vm.onOpened(artist); openArtist(artist.spotifyArtistId) },
                            )
                        }
                    }
                }
            }
            if (wide) PopularNow(Modifier.weight(1f).padding(top = 78.dp, end = 24.dp)) { openArtist(it) }
        }
    }
}

/** От этой ширины поиск — в две колонки: история и «Популярно сейчас». */
internal const val SEARCH_TWO_COLUMNS_DP = 840f

/** «Популярно сейчас» — верх TOP рейтинга из общего кэша (без отдельного запроса). */
@Composable
private fun PopularNow(modifier: Modifier, onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val top by produceState<List<RankedArtist>?>(null) { value = container.rating.topArtists().getOrNull().orEmpty().take(8) }
    val list = top ?: return
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(useI18n().t(Strings.SEARCH_POPULAR).uppercase(), color = DJMetryColors.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
        list.forEach { a -> ArtistRow(null, a.imageUrl, a.name, a.genres.firstOrNull(), null, null) { onOpen(a.spotifyArtistId) } }
    }
}

// ───────── Букинг (скоро) ─────────


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
