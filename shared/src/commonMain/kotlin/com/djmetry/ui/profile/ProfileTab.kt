package com.djmetry.ui.profile

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.MeResponse
import com.djmetry.config.AppConfig
import com.djmetry.data.repository.ProfileDashboard
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch

/** Переходы с экрана профиля. Нативные BIO / Smart Links / аналитика / букинг — следующие задачи, пока веб-кабинет. */
internal class ProfileActions(
    val openUrl: (String) -> Unit,
    val openRadars: () -> Unit,
    private val openArtist: (String) -> Unit,
) {
    val tiles = TileActions(
        bio = { openUrl("${AppConfig.BASE_URL}/dashboard/music/page") },
        links = { openUrl("${AppConfig.BASE_URL}/dashboard/music/smart-links") },
        analytics = { openUrl("${AppConfig.BASE_URL}/dashboard/music/analytics") },
        radars = openRadars,
    )
    val booking = { openUrl("${AppConfig.BASE_URL}/dashboard#booking-artist") }
    fun artistPage(spotifyArtistId: String) = openArtist(spotifyArtistId)
}

/**
 * Профиль / дашборд (вариант A «Герой и плитки»). Одни и те же секции, три раскладки:
 * телефон — колонка, колокольчик на фото и шторка; планшет — две колонки, колокольчик в шапке и поповер;
 * десктоп — герой + график, плитки, треки и правая колонка «Букинг / Уведомления / заявки / радары».
 */
@Composable
fun ProfileTab(me: MeResponse?, onLoggedOut: () -> Unit, onOpenRadars: () -> Unit, onOpenSettings: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val layout = LocalLayoutClass.current
    val unread by container.notifications.unread.collectAsState()
    val openNotifications = rememberNotificationsOpener(layout)
    val openArtist = LocalArtistNavigator.current
    val actions = remember(uri, openArtist) { ProfileActions(openUrl = uri::openUri, openRadars = onOpenRadars, openArtist = openArtist) }

    LaunchedEffect(Unit) { container.notifications.refreshUnread() }
    val dashboard by produceState<ProfileDashboard?>(null, me, i18n.locale) {
        value = me?.let { container.profile.load(it, i18n.locale.code) }
    }

    // Шестерёнка «Настройки» + колокольчик — рядом (макет design/settings/entry.html)
    val bell: @Composable (Boolean) -> Unit = { glass ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SettingsButton(onOpenSettings, glass = glass); NotificationBell(unread, openNotifications, glass = glass) }
    }
    val settingsRow: @Composable () -> Unit = { SettingsEntryRow(onOpenSettings) }
    val footer: @Composable () -> Unit = { settingsRow(); AccountFooter(onLoggedOut) }

    // Пока грузится — скелетон той же раскладки, потом плавное появление
    LoadingCrossfade(loading = dashboard == null, skeleton = { ProfileSkeleton(layout) }) {
        val data = dashboard ?: return@LoadingCrossfade
        when (layout) {
            LayoutClass.Compact -> PhoneLayout(data, actions, bell, footer)
            LayoutClass.Medium -> TabletLayout(data, actions, bell, footer)
            LayoutClass.Expanded -> DesktopLayout(data, actions, bell, footer)
        }
    }
}

@Composable
private fun PhoneLayout(d: ProfileDashboard, a: ProfileActions, bell: @Composable (Boolean) -> Unit, footer: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (d.artist != null) {
            ArtistHero(d.artist, 320.dp, topEnd = { bell(true) })
            MetricsGrid(d.artist, columns = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.hasBooking) BookingButton(d.bookingRequests.size, a.booking, Modifier.weight(1.2f))
                OpenPageButton({ a.artistPage(d.artist.spotifyArtistId) }, Modifier.weight(1f))
            }
        } else UserHero(d.me, topEnd = { bell(false) })
        ServiceTiles(columns = 2, actions = a.tiles)
        ScoreCard(d.scoreHistory)
        BookingCard(d.bookingCompanies.firstOrNull(), d.bookingRequests, a.booking)
        RadarCard(d.releases, a.openRadars)
        footer()
    }
}

@Composable
private fun TabletLayout(d: ProfileDashboard, a: ProfileActions, bell: @Composable (Boolean) -> Unit, footer: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header(bell, isArtist = d.isArtist)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.9f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (d.artist != null) {
                    ArtistHero(d.artist, 340.dp)
                    MetricsGrid(d.artist, columns = 2)
                    if (d.hasBooking) BookingButton(d.bookingRequests.size, a.booking, Modifier.fillMaxWidth())
                    OpenPageButton({ a.artistPage(d.artist.spotifyArtistId) }, Modifier.fillMaxWidth())
                } else UserHero(d.me)
                footer()
            }
            Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ServiceTiles(columns = 2, actions = a.tiles)
                ScoreCard(d.scoreHistory)
                BookingCard(d.bookingCompanies.firstOrNull(), d.bookingRequests, a.booking)
                RadarCard(d.releases, a.openRadars)
            }
        }
    }
}

@Composable
private fun DesktopLayout(d: ProfileDashboard, a: ProfileActions, bell: @Composable (Boolean) -> Unit, footer: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp, vertical = 18.dp)) {
        Header(bell, isArtist = d.isArtist, avatarUrl = d.artist?.imageUrl ?: d.me.user?.avatarUrl)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (d.artist != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(Modifier.width(300.dp)) { ArtistHero(d.artist, 300.dp) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            MetricsGrid(d.artist, columns = 4)
                            ScoreCard(d.scoreHistory, chartHeight = 120.dp)
                            OpenPageButton({ a.artistPage(d.artist.spotifyArtistId) }, Modifier.fillMaxWidth())
                        }
                    }
                } else UserHero(d.me)
                ServiceTiles(columns = 4, actions = a.tiles)
                TracksCard(d.tracks) { t -> t.externalUrl?.let(a.openUrl) }
                footer()
            }
            Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (d.hasBooking) BookingButton(d.bookingRequests.size, a.booking, Modifier.fillMaxWidth())
                NotificationsCard()
                BookingCard(d.bookingCompanies.firstOrNull(), d.bookingRequests, a.booking)
                RadarCard(d.releases, a.openRadars)
            }
        }
    }
}

@Composable
private fun Header(bell: @Composable (Boolean) -> Unit, isArtist: Boolean, avatarUrl: String? = null) {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(i18n.t(if (isArtist) Strings.PROFILE_TITLE else Strings.TAB_PROFILE), color = DJMetryColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        bell(false)
        if (avatarUrl != null) {
            Spacer(Modifier.width(12.dp))
            CoverImage(avatarUrl, 44.dp, cornerRadius = 22.dp)
        }
    }
}

@Composable
private fun AccountFooter(onLoggedOut: () -> Unit) {
    val i18n = useI18n()
    val auth = LocalAppContainer.current.auth
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = { scope.launch { auth.logout(); onLoggedOut() } },
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DJMetryColors.LowScore),
    ) { Text(i18n.t(Strings.HOME_LOGOUT), fontWeight = FontWeight.SemiBold) }
}

/** Строка «Настройки» внизу профиля (над «Выйти») — запасной вход для тех, кто долистал. */
@Composable
private fun SettingsEntryRow(onClick: () -> Unit) {
    val i18n = useI18n()
    com.djmetry.ui.settings.SettingsGroup(null) {
        com.djmetry.ui.settings.SettingsRow(
            i18n.t(Strings.SETTINGS_TITLE), i18n.t(Strings.SET_GROUP_NOTIFICATIONS) + ", " + i18n.t(Strings.SET_GROUP_RADARS).lowercase() + ", " + i18n.t(Strings.SET_GROUP_EMAILS).lowercase(),
            androidx.compose.material.icons.Icons.Outlined.Settings, divider = false, onClick = onClick,
        )
    }
}
