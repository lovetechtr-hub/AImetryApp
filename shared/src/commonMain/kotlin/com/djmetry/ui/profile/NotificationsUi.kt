package com.djmetry.ui.profile

import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.components.SkeletonBox
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.AppNotification
import com.djmetry.config.AppConfig
import com.djmetry.data.repository.NotificationFilter
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.LocalOverlay
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val Orange = Color(0xFFFFB35B)
private val Blue = Color(0xFF7DA7FF)

/** Колокольчик со счётчиком непрочитанных. [glass] — полупрозрачный фон поверх фото. */
/** Шестерёнка «Настройки» — той же формы, что колокольчик, стоит рядом с ним. */
@Composable
fun SettingsButton(onClick: () -> Unit, glass: Boolean = false, size: Dp = 44.dp) {
    val i18n = useI18n()
    Box(Modifier.size(size + 6.dp)) {
        Box(
            Modifier.size(size).align(Alignment.BottomStart).clip(CircleShape)
                .background(if (glass) DJMetryColors.Background.copy(alpha = 0.55f) else DJMetryColors.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClickLabel = i18n.t(Strings.SETTINGS_TITLE), onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Settings, i18n.t(Strings.SETTINGS_TITLE), tint = DJMetryColors.Text, modifier = Modifier.size(size * 0.5f)) }
    }
}

@Composable
fun NotificationBell(unread: Int, onClick: () -> Unit, glass: Boolean = false, size: Dp = 44.dp) {
    val i18n = useI18n()
    Box(Modifier.size(size + 6.dp)) {
        Box(
            Modifier.size(size).align(Alignment.BottomStart).clip(CircleShape)
                .background(if (glass) DJMetryColors.Background.copy(alpha = 0.55f) else DJMetryColors.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClickLabel = i18n.t(Strings.NOTIF_TITLE), onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Notifications, i18n.t(Strings.NOTIF_TITLE), tint = DJMetryColors.Text, modifier = Modifier.size(size * 0.5f)) }
        if (unread > 0) {
            com.djmetry.ui.components.CountBadge(unread, fg = Color.White, bg = DJMetryColors.LowScore, size = 20.dp, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Открывает уведомления: телефон — шторка снизу, планшет и десктоп — поповер у колокольчика. */
@Composable
fun rememberNotificationsOpener(layout: LayoutClass): () -> Unit {
    val overlay = LocalOverlay.current
    return remember(layout) {
        {
            overlay.show {
                if (layout == LayoutClass.Compact) NotificationsSheet(onDismiss = overlay::dismiss)
                else NotificationsPopover(onDismiss = overlay::dismiss)
            }
        }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.(visible: MutableTransitionState<Boolean>) -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        content(visible)
    }
}

@Composable
private fun NotificationsSheet(onDismiss: () -> Unit) = Scrim(onDismiss) { visible ->
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(DJMetryColors.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(CircleShape).background(Color(0xFF2C3D5C)))
            Spacer(Modifier.height(14.dp))
            NotificationsPanel(Modifier.weight(1f), onNavigate = onDismiss)
        }
    }
}

@Composable
private fun NotificationsPopover(onDismiss: () -> Unit) = Scrim(onDismiss) { visible ->
    AnimatedVisibility(
        visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 70.dp, end = 24.dp),
    ) {
        Column(
            Modifier.width(400.dp).heightIn(max = 560.dp)
                .shadow(30.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) { NotificationsPanel(Modifier.weight(1f, fill = false), onNavigate = onDismiss) }
    }
}

/** Карточка «Уведомления» в правой колонке десктопа — тот же список, всегда на экране. */
@Composable
fun NotificationsCard(modifier: Modifier = Modifier, maxItems: Int = 4) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)).padding(16.dp)
    ) { NotificationsPanel(maxItems = maxItems) }
}

/** Заголовок, «Прочитать все», чипы фильтра и список — общий для шторки, поповера и карточки. */
@Composable
fun NotificationsPanel(modifier: Modifier = Modifier, maxItems: Int? = null, onNavigate: () -> Unit = {}) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.notifications
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val items by repo.items.collectAsState()
    val filter by repo.filter.collectAsState()
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(filter) {
        loading = true
        repo.load(filter)
        loading = false
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(i18n.t(Strings.NOTIF_TITLE), color = DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (items.any { !it.read }) {
                Text(
                    i18n.t(Strings.NOTIF_READ_ALL), color = DJMetryColors.Accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { scope.launch { repo.markAllRead() } },
                )
            }
        }
        LazyRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(NotificationFilter.entries) { f ->
                val selected = f == filter
                Text(
                    i18n.t(filterLabelKey(f)),
                    color = if (selected) DJMetryColors.Background else DJMetryColors.Muted,
                    fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (selected) DJMetryColors.Accent else Color(0xFF0E1728))
                        .clickable { scope.launch { repo.load(f) } }.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        when {
            loading && items.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                repeat(4) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(44.dp), RoundedCornerShape(12.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SkeletonLine(0.3f, 9.dp); SkeletonLine(listOf(0.75f, 0.6f, 0.7f, 0.55f)[i], 12.dp); SkeletonLine(0.5f, 9.dp)
                        }
                    }
                }
            }
            items.isEmpty() -> Text(
                i18n.t(Strings.NOTIF_EMPTY), color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            else -> {
                val shown = maxItems?.let { items.take(it) } ?: items
                val onOpen = { n: AppNotification ->
                    scope.launch { repo.markRead(n) }
                    notificationTarget(n.url, AppConfig.BASE_URL)?.let { uri.openUri(it); onNavigate() }
                    Unit
                }
                if (maxItems != null) Column { shown.forEach { NotificationRow(it, onOpen) } }
                else LazyColumn { items(shown, key = { it.id }) { NotificationRow(it, onOpen) } }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onOpen: (AppNotification) -> Unit) {
    val i18n = useI18n()
    val kind = notificationKind(n.type)
    val (icon, tint, bg) = kindStyle(kind)
    val time = relativeTime(n.created_at, Clock.System.now())?.let { (key, arg) -> if (arg == null) i18n.t(key) else i18n.tWithArgs(key, arrayOf(arg)) }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n.image_url != null) CoverImage(n.image_url, 44.dp, cornerRadius = 12.dp)
        else Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                (kind.label ?: i18n.t(kind.labelKey!!)) + (time?.let { " · $it" } ?: ""),
                color = DJMetryColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(n.title, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            n.body?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (!n.read) Box(Modifier.size(9.dp).clip(CircleShape).background(DJMetryColors.Accent))
    }
}

private fun kindStyle(kind: NotificationKind): Triple<ImageVector, Color, Color> = when (kind) {
    NotificationKind.Booking -> Triple(Icons.Outlined.CalendarMonth, DJMetryColors.Accent, Color(0xFF10302A))
    NotificationKind.PreSave -> Triple(Icons.Outlined.Bookmark, Blue, Color(0xFF15264A))
    NotificationKind.Concert -> Triple(Icons.Outlined.LocationOn, Orange, Color(0xFF3A2A12))
    NotificationKind.Release, NotificationKind.Other -> Triple(Icons.Outlined.Notifications, DJMetryColors.Accent, Color(0xFF10302A))
}
