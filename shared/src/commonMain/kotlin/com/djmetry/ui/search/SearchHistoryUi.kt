package com.djmetry.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.data.search.RecentArtist
import com.djmetry.data.search.SearchHistoryRules
import com.djmetry.data.search.SearchScope
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors

/**
 * История поиска, вариант A (design/search/history-variants.html): список недавних запросов под полем
 * (тап — искать, стрелка — подставить в поле, крестик — удалить, «Очистить») и у главного поиска — лента
 * «Вы открывали». [compact] — Радар, карта, выбор страны и города: только список, не больше [max] строк.
 * [typed] — что уже набрано: показываем совпадения из истории.
 */
@Composable
fun SearchHistoryPanel(
    scope: SearchScope,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    typed: String = "",
    onInsert: ((String) -> Unit)? = null,
    onOpenArtist: ((RecentArtist) -> Unit)? = null,
    compact: Boolean = false,
    max: Int = if (compact) 5 else SearchHistoryRules.MAX_QUERIES,
    title: String? = null,
) {
    val i18n = useI18n()
    val history = LocalAppContainer.current.searchHistory
    val data by history.data.collectAsState()
    val queries = SearchHistoryRules.matching(data.queries[scope.key].orEmpty(), typed).take(max)
    val artists = if (scope.withArtists && onOpenArtist != null && typed.isBlank()) data.artists else emptyList()
    if (queries.isEmpty() && artists.isEmpty()) return

    Column(modifier) {
        if (queries.isNotEmpty()) {
            SectionHeader(title ?: i18n.t(if (compact) Strings.SH_RECENT_PICKS else Strings.SH_RECENT), clear = typed.isBlank()) { history.clear(scope) }
            queries.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPick(q) }
                        .padding(start = 4.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, tint = DJMetryColors.Muted, modifier = Modifier.size(20.dp))
                    Text(q, color = DJMetryColors.Text, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                    // Стрелка «подставить»: дописать запрос, а не искать сразу (как в браузере и Spotify)
                    onInsert?.let { insert ->
                        IconButton(onClick = { insert(q) }) {
                            Icon(Icons.AutoMirrored.Filled.CallMade, i18n.t(Strings.SH_INSERT), tint = DJMetryColors.Muted, modifier = Modifier.size(18.dp).rotate(-90f))
                        }
                    }
                    IconButton(onClick = { history.remove(scope, q) }) {
                        Icon(Icons.Filled.Close, i18n.t(Strings.SH_REMOVE), tint = DJMetryColors.Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (artists.isNotEmpty() && onOpenArtist != null) {
            if (queries.isNotEmpty()) Spacer(Modifier.height(18.dp))
            SectionHeader(i18n.t(Strings.SH_OPENED), clear = false) {}
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(artists, key = { it.id }) { a ->
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onOpenArtist(a) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CoverImage(a.imageUrl, 64.dp, cornerRadius = 32.dp)
                        Text(a.name, color = DJMetryColors.Text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, clear: Boolean, onClear: () -> Unit) {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = DJMetryColors.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
        if (clear) Text(
            i18n.t(Strings.SH_CLEAR), color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClear).padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}
