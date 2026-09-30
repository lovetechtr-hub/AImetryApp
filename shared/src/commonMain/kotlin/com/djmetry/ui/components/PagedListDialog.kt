package com.djmetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Состояние постраничной загрузки: одна загрузка за раз (повторный тап не дублирует страницу), ошибка — отдельно от «пусто». */
@Stable
internal class PagedList<T>(first: List<T>, val total: Int, private var page: Int = 1, private val load: suspend (Int) -> Result<List<T>>) {
    val items = mutableStateListOf<T>().apply { addAll(first) }
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    val hasMore: Boolean get() = items.size < total

    suspend fun next() {
        if (loading || !hasMore) return
        loading = true; failed = false
        load(page + 1).onSuccess { list -> page++; items.addAll(list); if (list.isEmpty()) page = Int.MAX_VALUE - 1 }.onFailure { failed = true }
        loading = false
    }
}

/**
 * Полный список во весь экран: ленивый (сотни строк не собираются разом), следующая страница — за 5 строк до конца,
 * ошибка — строкой «Повторить». Для длинных списков внутри экранов с обычной прокруткой (Аудитория: люди, лиды).
 */
@Composable
internal fun <T> PagedListDialog(title: String, list: PagedList<T>, onClose: () -> Unit, row: @Composable (T) -> Unit) {
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
            if (!list.failed && last >= list.items.size - 5) list.next()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(DJMetryColors.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = DJMetryColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Filled.Close, null, tint = DJMetryColors.Text,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(DJMetryColors.Panel).clickable(role = Role.Button, onClick = onClose).padding(11.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                itemsIndexed(list.items) { _, item -> row(item) }
                when {
                    list.loading -> item { repeat(3) { SkeletonBox(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp)) } }
                    list.failed -> item {
                        Text(
                            "${i18n.t(Strings.HOME_ERROR)} · ${i18n.t(Strings.HOME_RETRY)}", color = DJMetryColors.Accent, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { scope.launch { list.next() } }.padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}
