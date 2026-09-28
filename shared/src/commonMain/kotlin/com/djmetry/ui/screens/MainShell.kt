package com.djmetry.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.djmetry.api.models.MeResponse
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.components.LocalOverlay
import com.djmetry.ui.components.OverlayController
import com.djmetry.ui.profile.ProfileTab
import com.djmetry.ui.artist.ArtistScreen
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.layoutClassFor
import com.djmetry.ui.theme.DJMetryColors

enum class MainTab(val titleKey: String, val icon: ImageVector) {
    Discover(Strings.TAB_DISCOVER, Icons.Outlined.Style),
    Rating(Strings.TAB_RATING, Icons.Outlined.EmojiEvents),
    Radars(Strings.TAB_RADARS, Icons.Outlined.Radar),
    Booking(Strings.TAB_BOOKING, Icons.Outlined.CalendarMonth),
    Profile(Strings.TAB_PROFILE, Icons.Outlined.Person),
}

/** Главная оболочка после входа: колода «Открытия» (вариант B) + парящий таббар из варианта A. Поиск — из шапки «Открытий». */
@Composable
fun MainShell(
    me: MeResponse?,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: MainTab = MainTab.Discover, // deep-link из уведомлений, скриншот-тесты
) {
    var tab by remember { mutableStateOf(initialTab) }
    var searchOpen by remember { mutableStateOf(false) }
    var artistId by remember { mutableStateOf<String?>(null) } // открытая карточка артиста поверх вкладки
    val ratingList = rememberLazyListState()
    val overlay = remember { OverlayController() }

    BoxWithConstraints(modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val layout = layoutClassFor(maxWidth.value)
        val content: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = if (searchOpen) null else tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "tab",
            ) { current ->
                when (current) {
                    null -> Box {
                        SearchTab()
                        IconButton(
                            onClick = { searchOpen = false },
                            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text) }
                    }
                    MainTab.Discover -> DiscoverTab(onOpenSearch = { searchOpen = true })
                    MainTab.Rating -> RatingTab(ratingList)
                    MainTab.Radars -> RadarsTab()
                    MainTab.Booking -> BookingTab()
                    MainTab.Profile -> ProfileTab(me, onLoggedOut, onOpenRadars = { tab = MainTab.Radars })
                }
            }
            // Карточка поверх вкладки: вкладка (поиск, прокрутка рейтинга) сохраняет состояние, «Назад» возвращает к ней
            artistId?.let { id ->
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    ArtistScreen(id, onBack = { artistId = null })
                }
            }
        } }
        val select = { t: MainTab -> tab = t; searchOpen = false; artistId = null }

        CompositionLocalProvider(
            LocalOverlay provides overlay,
            LocalArtistNavigator provides { id: String -> artistId = id },
            LocalLayoutClass provides layout,
            LocalBottomClearance provides if (layout.isTablet) 24.dp else 110.dp,
        ) {
            if (layout.isTablet) {
                // Планшет: та же «таблетка», но вертикально слева
                Row(Modifier.fillMaxSize()) {
                    FloatingTabBar(tab, select, vertical = true, modifier = Modifier.align(Alignment.CenterVertically))
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    content()
                    FloatingTabBar(tab, select, vertical = false, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            overlay.content?.invoke()
        }
    }
}

/** Парящий таббар (как в варианте A): только иконки, по центру — главная кнопка # («Открытия»). */
@Composable
private fun FloatingTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, vertical: Boolean, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    val shape = RoundedCornerShape(34.dp)
    val items: @Composable () -> Unit = {
        listOf(MainTab.Rating, MainTab.Radars).forEach { t -> TabIcon(t.icon, i18n.t(t.titleKey), selected == t) { onSelect(t) } }
        HashButton(i18n.t(MainTab.Discover.titleKey), selected == MainTab.Discover) { onSelect(MainTab.Discover) }
        listOf(MainTab.Booking, MainTab.Profile).forEach { t -> TabIcon(t.icon, i18n.t(t.titleKey), selected == t) { onSelect(t) } }
    }
    val surface = Modifier
        .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(DJMetryColors.Panel.copy(alpha = 0.96f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    if (vertical) {
        Column(
            modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)).padding(18.dp).width(76.dp).then(surface).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { items() }
    } else {
        Row(
            modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().height(68.dp).then(surface),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { items() }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "tabScale")
    Column(
        Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Tab, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) DJMetryColors.Accent else Color(0xFF6F84A8),
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(if (selected) DJMetryColors.Accent else Color.Transparent))
    }
}

/** Главная кнопка: зелёный круг со знаком #, при выборе знак слегка поворачивается. */
@Composable
private fun HashButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (selected) 0f else -12f, spring(Spring.DampingRatioMediumBouncy), label = "hash")
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, spring(Spring.DampingRatioMediumBouncy), label = "hashScale")
    Box(
        Modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, CircleShape, ambientColor = DJMetryColors.Accent, spotColor = DJMetryColors.Accent)
            .clip(CircleShape)
            .background(DJMetryColors.Accent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            DJMetryLogo.HashMark, contentDescription = label, tint = DJMetryColors.Background,
            modifier = Modifier.size(28.dp, 25.5.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}
