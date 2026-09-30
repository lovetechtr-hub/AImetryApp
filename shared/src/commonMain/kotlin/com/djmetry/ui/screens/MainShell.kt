package com.djmetry.ui.screens

import com.djmetry.ui.editor.LocalOpenArtistEditor
import com.djmetry.ui.editor.ArtistEditorScreen
import com.djmetry.ui.analytics.AnalyticsScreen
import com.djmetry.ui.analytics.LocalOpenAnalytics
import com.djmetry.ui.djmap.DjMapScreen
import com.djmetry.ui.djmap.LocalOpenDjMap
import com.djmetry.ui.settings.SettingsScreen
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
import com.djmetry.ui.rating.RatingTab
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
    initialTab: MainTab = MainTab.Discover, // deep-link из уведомлений, скриншот-тесты, восстановление после выгрузки
    initialArtistId: String? = null,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var bookingOpen by remember { mutableStateOf<com.djmetry.data.booking.BookingOpen?>(null) }
    var releaseOpen by remember { mutableStateOf<com.djmetry.data.radar.ReleaseOpen?>(null) }
    var concertOpen by remember { mutableStateOf<com.djmetry.ui.profile.ConcertOpen?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var artistId by remember { mutableStateOf(initialArtistId) } // открытая карточка артиста поверх вкладки
    LaunchedEffect(tab, artistId) { com.djmetry.data.local.NavMemory.update(com.djmetry.data.local.NavState(tab.name, artistId)) }
    var settingsOpen by remember { mutableStateOf(false) } // настройки поверх профиля
    var settingsPage by remember { mutableStateOf<com.djmetry.ui.settings.SettingsPage?>(null) } // открыть сразу на странице
    var editorOpen by remember { mutableStateOf(false) } // редактор артиста поверх профиля / настроек
    var analyticsOpen by remember { mutableStateOf(false) } // аналитика поверх профиля
    var discoverReset by remember { mutableStateOf(0) }
    var followingKey by remember { mutableStateOf(0) } // «Подписки» из профиля — вкладка «Открытия» в режиме подписок
    var discoverMapFull by remember { mutableStateOf(false) } // вкладка «Карта» на телефоне — во весь экран
    var mapArtist by remember { mutableStateOf<String?>(null) } // карта диджеев поверх вкладки: "" — все DJ, id — тур одного DJ
    var mapReturnArtist by remember { mutableStateOf<String?>(null) } // карточка, с которой открыли карту — вернуть по «назад»
    val ratingList = rememberLazyListState()
    val overlay = remember { OverlayController() }

    // Уведомление (пуш или колокольчик) → экран: релиз — в списке релизов артиста, концерт — в Радаре,
    // заявка — во вкладке «Букинг», артист — карточка, остальное — сайт
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val go: (com.djmetry.ui.profile.NotificationRoute) -> Unit = { r ->
        val clear = { artistId = null; searchOpen = false; settingsOpen = false; editorOpen = false; analyticsOpen = false; mapArtist = null }
        when (r) {
            is com.djmetry.ui.profile.NotificationRoute.Release -> { clear(); tab = MainTab.Radars; releaseOpen = r.open }
            is com.djmetry.ui.profile.NotificationRoute.Concert -> { clear(); tab = MainTab.Radars; concertOpen = r.open }
            is com.djmetry.ui.profile.NotificationRoute.Booking -> { clear(); tab = MainTab.Booking; bookingOpen = r.open }
            is com.djmetry.ui.profile.NotificationRoute.Artist -> artistId = r.id
            is com.djmetry.ui.profile.NotificationRoute.Web -> runCatching { uriHandler.openUri(r.url) }
        }
    }
    val opened by com.djmetry.push.PushTokens.opened.collectAsState()
    LaunchedEffect(opened) {
        val p = opened ?: return@LaunchedEffect
        com.djmetry.push.PushTokens.consumeOpened()
        com.djmetry.ui.profile.routeNotification(p.url, p.type, p.meta, com.djmetry.config.AppConfig.BASE_URL)?.let(go)
    }

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
                    MainTab.Discover -> DiscoverTab(onOpenSearch = { searchOpen = true }, resetKey = discoverReset, onMapFullScreen = { discoverMapFull = it }, openFollowingKey = followingKey)
                    MainTab.Rating -> RatingTab(ratingList)
                    MainTab.Radars -> com.djmetry.ui.radar.RadarTab(openRelease = releaseOpen, onOpened = { releaseOpen = null }, openConcert = concertOpen, onConcertOpened = { concertOpen = null })
                    MainTab.Booking -> com.djmetry.ui.booking.BookingTab(me, openRequest = bookingOpen, onOpened = { bookingOpen = null })
                    MainTab.Profile -> ProfileTab(me, onLoggedOut, onOpenRadars = { tab = MainTab.Radars }, onOpenSettings = { settingsOpen = true })
                }
            }
            // Подложка под статус-бар: прокручиваемое содержимое (капсула рейтинга и т. п.) не заезжает под часы и батарею
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(DJMetryColors.Background.copy(alpha = 0.96f)))
            // Карточка поверх вкладки: вкладка (поиск, прокрутка рейтинга) сохраняет состояние, «Назад» возвращает к ней
            if (settingsOpen) {
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    SettingsScreen(me, onBack = { settingsOpen = false; settingsPage = null }, onLoggedOut = { settingsOpen = false; onLoggedOut() }, initialPage = settingsPage)
                }
            }
            if (editorOpen) {
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    ArtistEditorScreen(me, onBack = { editorOpen = false })
                }
            }
            if (analyticsOpen) {
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    AnalyticsScreen(me, onBack = { analyticsOpen = false })
                }
            }
            mapArtist?.let { id ->
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    DjMapScreen(initialArtistId = id.ifEmpty { null }, onBack = { mapArtist = null; artistId = mapReturnArtist; mapReturnArtist = null })
                }
            }
            artistId?.let { id ->
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    ArtistScreen(id, onBack = { artistId = null })
                }
            }
        } }
        // Повторный тап по «#» на экране свайпов — вернуться с карты к колоде
        val select = { t: MainTab -> if (t == tab && t == MainTab.Discover) discoverReset++; tab = t; searchOpen = false; artistId = null; settingsOpen = false; editorOpen = false; analyticsOpen = false; mapArtist = null; mapReturnArtist = null }

        val nothingOnTop = artistId == null && !settingsOpen && !editorOpen && !analyticsOpen && !searchOpen
        val hideTabBar = !layout.isTablet && nothingOnTop && (mapArtist != null || (tab == MainTab.Discover && discoverMapFull))

        CompositionLocalProvider(
            LocalOverlay provides overlay,
            LocalArtistNavigator provides { id: String -> artistId = id },
            LocalOpenArtistEditor provides { editorOpen = true },
            LocalOpenAnalytics provides { analyticsOpen = true },
            com.djmetry.ui.profile.LocalOpenNotification provides go,
            com.djmetry.ui.radar.LocalOpenRelease provides { r -> select(MainTab.Radars); releaseOpen = r },
            com.djmetry.ui.settings.LocalOpenSettings provides { page -> settingsPage = page; settingsOpen = true },
            com.djmetry.ui.booking.LocalOpenBooking provides { o -> select(MainTab.Booking); bookingOpen = o },
            com.djmetry.ui.profile.LocalOpenFollowing provides { select(MainTab.Discover); followingKey++ },
            LocalOpenDjMap provides { id -> mapReturnArtist = artistId; artistId = null; mapArtist = id ?: "" },
            LocalLayoutClass provides layout,
            // Телефон: на карте таббар не нужен — назад кнопкой «к свайпам» / «назад» и системным «Назад»; карта получает ~90 dp
            LocalBottomClearance provides when {
                layout.isTablet -> 24.dp
                hideTabBar -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                else -> 110.dp
            },
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
                    if (!hideTabBar) FloatingTabBar(tab, select, vertical = false, modifier = Modifier.align(Alignment.BottomCenter))
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
