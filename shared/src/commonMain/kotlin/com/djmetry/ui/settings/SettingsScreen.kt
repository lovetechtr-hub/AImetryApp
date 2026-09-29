package com.djmetry.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.endpoints.EmailToggle
import com.djmetry.api.models.MeResponse
import com.djmetry.data.repository.IN_APP_TYPES
import com.djmetry.data.repository.PUSH_TYPES
import com.djmetry.data.repository.SettingsState
import com.djmetry.data.repository.enabledCount
import com.djmetry.data.repository.showsGenres
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.components.SkeletonListRow
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Разделы настроек — отдельные страницы (телефон — стек, планшет — справа от списка, десктоп — по меню). */
enum class SettingsPage { Profile, Push, InApp, Concert, Release, Emails, Language, Genres, Account }

/** Меню десктопа: пункт → какие страницы показать вместе. */
enum class SettingsSection(val titleKey: String, val pages: List<SettingsPage>) {
    Profile(Strings.SET_GROUP_PROFILE, listOf(SettingsPage.Profile)),
    Notifications(Strings.SET_GROUP_NOTIFICATIONS, listOf(SettingsPage.Push, SettingsPage.InApp)),
    Radars(Strings.SET_GROUP_RADARS, listOf(SettingsPage.Concert, SettingsPage.Release)),
    Emails(Strings.SET_GROUP_EMAILS, listOf(SettingsPage.Emails)),
    Preferences(Strings.SET_GROUP_PREFS, listOf(SettingsPage.Language, SettingsPage.Genres)),
    Account(Strings.SET_GROUP_ACCOUNT, listOf(SettingsPage.Account)),
}

/** Страницы раздела с учётом гейтинга: жанры — только не-артистам. */
fun sectionPages(section: SettingsSection, verified: Boolean): List<SettingsPage> =
    section.pages.filter { it != SettingsPage.Genres || showsGenres(verified) }

/** Какую страницу открыть справа на планшете, если ничего не выбрано. */
val DEFAULT_TABLET_PAGE = SettingsPage.Push

/** Сообщение внизу экрана («Сохранено» / «Не удалось сохранить»). */
internal class SettingsFeedback {
    var message by mutableStateOf<String?>(null)
}

internal val LocalFeedback = compositionLocalOf { SettingsFeedback() }

/**
 * Настройки пользователя и артиста — вариант A «Сгруппированный список».
 * Телефон: список групп → страница раздела с «Назад». Планшет: список слева, раздел справа. Десктоп: меню разделов + содержимое.
 */
@Composable
fun SettingsScreen(me: MeResponse?, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = LocalAppContainer.current
    val layout = LocalLayoutClass.current
    val state by container.settings.state.collectAsState()
    val feedback = remember { SettingsFeedback() }
    LaunchedEffect(me) { me?.let { container.settings.load(it) } }
    LaunchedEffect(feedback.message) { if (feedback.message != null) { delay(2000); feedback.message = null } }

    CompositionLocalProvider(LocalFeedback provides feedback) {
        Box(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
            LoadingCrossfade(loading = state == null, skeleton = { SettingsSkeleton(layout) }) {
                val s = state ?: return@LoadingCrossfade
                when (layout) {
                    LayoutClass.Compact -> PhoneSettings(s, onBack, onLoggedOut)
                    LayoutClass.Medium -> TabletSettings(s, onBack, onLoggedOut)
                    LayoutClass.Expanded -> DesktopSettings(s, onBack, onLoggedOut)
                }
            }
            AnimatedVisibility(
                visible = feedback.message != null, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp),
            ) {
                Text(
                    feedback.message.orEmpty(), color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Accent).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ───────── Раскладки ─────────

@Composable
private fun PhoneSettings(s: SettingsState, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    val open = page
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopBar(if (open == null) onBack else { { page = null } })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (open == null) {
                PageTitle(useI18n().t(Strings.SETTINGS_TITLE))
                RootList(s, selected = null, onOpen = { page = it }, onLoggedOut = onLoggedOut)
            } else PageContent(open, s, onLoggedOut)
        }
    }
}

@Composable
private fun TabletSettings(s: SettingsState, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    var page by remember { mutableStateOf(DEFAULT_TABLET_PAGE) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp)) {
        TopBar(onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageTitle(useI18n().t(Strings.SETTINGS_TITLE))
                RootList(s, selected = page, onOpen = { page = it }, onLoggedOut = onLoggedOut)
            }
            Column(Modifier.weight(1f).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageContent(page, s, onLoggedOut)
            }
        }
    }
}

@Composable
private fun DesktopSettings(s: SettingsState, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val i18n = useI18n()
    var section by remember { mutableStateOf(SettingsSection.Notifications) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp)) {
        TopBar(onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageTitle(i18n.t(Strings.SETTINGS_TITLE))
                Spacer(Modifier.height(10.dp))
                SettingsSection.values().forEach { sec ->
                    if (sectionPages(sec, s.verified).isEmpty()) return@forEach
                    val on = sec == section
                    Text(
                        i18n.t(sec.titleKey), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (on) DJMetryColors.Accent else DJMetryColors.Text,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (on) DJMetryColors.Accent.copy(alpha = 0.12f) else DJMetryColors.Background)
                            .clickable(role = Role.Tab) { section = sec }.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                sectionPages(section, s.verified).forEach { PageContent(it, s, onLoggedOut) }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    val i18n = useI18n()
    Row(
        Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text, modifier = Modifier.size(20.dp))
        Text(i18n.t(Strings.ARTIST_BACK), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

// ───────── Главный список (телефон, планшет) ─────────

@Composable
private fun RootList(s: SettingsState, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit, onLoggedOut: () -> Unit) {
    val i18n = useI18n()
    val p = s.profile
    val notSet = i18n.t(Strings.SET_NOT_SET)
    val on = i18n.t(Strings.SET_ON)
    val off = i18n.t(Strings.SET_OFF)

    SettingsGroup(i18n.t(Strings.SET_GROUP_PROFILE)) {
        SettingsRow(i18n.t(Strings.SET_REGION), listOfNotNull(p?.city, p?.country).joinToString(", ").ifEmpty { notSet } + " · " + i18n.t(Strings.SET_REGION_HINT),
            Icons.Outlined.LocationOn, selected = selected == SettingsPage.Profile) { onOpen(SettingsPage.Profile) }
        SettingsRow(i18n.t(Strings.SET_BIRTH), p?.birthDate ?: notSet, Icons.Outlined.Cake, divider = !s.verified) { onOpen(SettingsPage.Profile) }
        if (s.verified) {
            val openEditor = com.djmetry.ui.editor.LocalOpenArtistEditor.current
            SettingsRow(i18n.t(Strings.ED_TITLE), i18n.t(Strings.ED_SUB), Icons.Outlined.Edit, divider = false) { openEditor() }
        }
    }
    SettingsGroup(i18n.t(Strings.SET_GROUP_NOTIFICATIONS)) {
        val push = s.push
        SettingsRow(
            i18n.t(Strings.SET_PUSH),
            push?.let { i18n.tWithArgs(Strings.SET_PUSH_SUMMARY, arrayOf(enabledCount(it.types, PUSH_TYPES), PUSH_TYPES.size)) },
            Icons.Outlined.NotificationsActive, end = RowEnd.Value(if (push?.enabled != false) on else off), selected = selected == SettingsPage.Push,
        ) { onOpen(SettingsPage.Push) }
        SettingsRow(i18n.t(Strings.SET_IN_APP), null, Icons.Outlined.Notifications, end = RowEnd.Value(if (s.inApp?.inAppEnabled != false) on else off), selected = selected == SettingsPage.InApp) { onOpen(SettingsPage.InApp) }
        ToggleRow(s, EmailToggle.SmartLink, "Smart Link", i18n.t(Strings.SET_SMART_LINK_SUB), Icons.Outlined.Link, RowTone.Green, divider = false)
    }
    SettingsGroup(i18n.t(Strings.SET_GROUP_RADARS)) {
        val c = s.concert
        SettingsRow("Concert Radar", c?.let { concertSummary(it.effectiveCity, it.effectiveCountry, it.concertAlertsFrequency) }, Icons.Outlined.Map, RowTone.Blue,
            RowEnd.Value(if (c?.concertAlertsEnabled == true) on else off), selected = selected == SettingsPage.Concert) { onOpen(SettingsPage.Concert) }
        SettingsRow("Release Radar", s.releaseRadar?.let { freqLabel(it.releaseRadarFrequency) }, Icons.Outlined.Album, RowTone.Blue,
            RowEnd.Value(if (s.releaseRadar?.releaseRadarEnabled == true) on else off), divider = false, selected = selected == SettingsPage.Release) { onOpen(SettingsPage.Release) }
    }
    SettingsGroup(i18n.t(Strings.SET_GROUP_EMAILS)) { EmailToggles(s) }
    SettingsGroup(i18n.t(Strings.SET_GROUP_PREFS)) {
        if (showsGenres(s.verified)) {
            SettingsRow(i18n.t(Strings.SET_GENRES), p?.music_genre_preferences?.takeIf { it.isNotEmpty() }?.joinToString() ?: notSet, Icons.Outlined.LibraryMusic,
                selected = selected == SettingsPage.Genres) { onOpen(SettingsPage.Genres) }
        }
        SettingsRow(i18n.t(Strings.SET_LANGUAGE), null, Icons.Outlined.Language, end = RowEnd.Value(i18n.locale.displayName), divider = false, selected = selected == SettingsPage.Language) { onOpen(SettingsPage.Language) }
    }
    SettingsGroup(i18n.t(Strings.SET_GROUP_ACCOUNT)) { AccountRows(onLoggedOut) }
}

@Composable
internal fun concertSummary(city: String?, country: String?, frequency: String?): String {
    val i18n = useI18n()
    val place = listOfNotNull(city, country).joinToString(", ")
    return listOf(place.ifEmpty { i18n.t(Strings.SET_NOT_SET) }, freqLabel(frequency)).joinToString(" · ")
}

@Composable
internal fun freqLabel(frequency: String?): String =
    useI18n().t(if (frequency == "weekly_digest") Strings.SET_WEEKLY_SUB else Strings.SET_FREQ_IMMEDIATE)

// ───────── Строки-переключатели ─────────

@Composable
internal fun ToggleRow(s: SettingsState, t: EmailToggle, title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: RowTone, divider: Boolean = true) {
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val i18n = useI18n()
    val value = s.toggles[t] ?: return
    SettingsRow(title, subtitle, icon, tone, RowEnd.Toggle(value) { v ->
        scope.launch { repo.setToggle(t, v).onFailure { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) } }
    }, divider = divider)
}

@Composable
private fun EmailToggles(s: SettingsState) {
    val i18n = useI18n()
    val rows = listOf(
        Triple(EmailToggle.TrackSupport, Strings.SET_TRACK_RADAR, Strings.SET_TRACK_RADAR_SUB),
        Triple(EmailToggle.Weekly, Strings.SET_WEEKLY, Strings.SET_WEEKLY_SUB),
        Triple(EmailToggle.Venues, Strings.SET_VENUES, Strings.SET_WEEKLY_SUB),
        Triple(EmailToggle.Ranking, Strings.SET_RANKING_DIGEST, Strings.SET_ARTISTS_ONLY),
        Triple(EmailToggle.Talents, Strings.SET_TALENTS_DIGEST, Strings.SET_ARTISTS_ONLY),
    ).filter { it.first in s.toggles }
    rows.forEachIndexed { i, (t, title, sub) ->
        ToggleRow(s, t, i18n.t(title), i18n.t(sub), Icons.Outlined.Email, RowTone.Orange, divider = i < rows.lastIndex)
    }
}

@Composable
private fun AccountRows(onLoggedOut: () -> Unit) {
    val i18n = useI18n()
    val auth = LocalAppContainer.current.auth
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    SettingsRow(i18n.t(Strings.HOME_LOGOUT), null, Icons.AutoMirrored.Filled.Logout, RowTone.Red, RowEnd.None, titleColor = DJMetryColors.LowScore) {
        scope.launch { auth.logout(); onLoggedOut() }
    }
    SettingsRow(i18n.t(Strings.SET_DELETE), i18n.t(Strings.SET_DELETE_SUB), Icons.Outlined.DeleteForever, RowTone.Red, RowEnd.None, divider = false, titleColor = DJMetryColors.LowScore) {
        confirm = true
    }
    if (confirm) {
        val feedback = LocalFeedback.current
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(i18n.t(Strings.SET_DELETE_TITLE)) },
            text = { Text(i18n.t(Strings.SET_DELETE_TEXT)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        auth.deleteAccount().onSuccess { onLoggedOut() }.onFailure { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
                    }
                }) { Text(i18n.t(Strings.SET_DELETE), color = DJMetryColors.LowScore) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(i18n.t(Strings.SET_CANCEL)) } },
            containerColor = DJMetryColors.Panel, titleContentColor = DJMetryColors.Text, textContentColor = DJMetryColors.Muted,
        )
    }
}

// ───────── Страницы ─────────

@Composable
private fun PageContent(page: SettingsPage, s: SettingsState, onLoggedOut: () -> Unit) {
    when (page) {
        SettingsPage.Profile -> ProfilePage(s)
        SettingsPage.Push -> PushPage(s)
        SettingsPage.InApp -> InAppPage(s)
        SettingsPage.Concert -> ConcertPage(s)
        SettingsPage.Release -> ReleasePage(s)
        SettingsPage.Emails -> { PageTitle(useI18n().t(Strings.SET_GROUP_EMAILS)); SettingsGroup(null) { EmailToggles(s) } }
        SettingsPage.Language -> LanguagePage()
        SettingsPage.Genres -> GenresPage(s)
        SettingsPage.Account -> { PageTitle(useI18n().t(Strings.SET_GROUP_ACCOUNT)); SettingsGroup(null) { AccountRows(onLoggedOut) } }
    }
}

@Composable
private fun PushPage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val push = s.push
    val fail: (Throwable) -> Unit = { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
    PageTitle(i18n.t(Strings.SET_PUSH))
    if (push == null) { Hint(i18n.t(Strings.HOME_ERROR)); return }
    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.SET_PUSH_MASTER), null, Icons.Outlined.NotificationsActive, end = RowEnd.Toggle(push.enabled) { v ->
            scope.launch { repo.setPushEnabled(v).onFailure(fail) }
        }, divider = false)
    }
    Hint(i18n.t(Strings.SET_PUSH_DEVICE_NOTE))
    SettingsGroup(i18n.t(Strings.SET_PUSH_TYPES_TITLE)) {
        PUSH_TYPES.forEachIndexed { idx, type ->
            SettingsRow(pushTypeLabel(type), null, null, end = RowEnd.Toggle(push.types[type] != false) { v ->
                scope.launch { repo.setPushType(type, v).onFailure(fail) }
            }, enabled = push.enabled, divider = idx < PUSH_TYPES.lastIndex)
        }
    }
}

/** Подпись типа push / колокольчика. Бренды (Smart Link, Track Radar, Talents) не переводим. */
@Composable
internal fun pushTypeLabel(type: String): String {
    val i18n = useI18n()
    return when (type) {
        "release_radar" -> "Release Radar"
        "pre_save" -> i18n.t(Strings.NOTIF_F_PRESAVE)
        "concert" -> i18n.t(Strings.NOTIF_F_CONCERTS)
        "smart_link" -> "Smart Link"
        "booking" -> i18n.t(Strings.NOTIF_F_BOOKING)
        "track_support" -> "Track Radar"
        "integration_health" -> i18n.t(Strings.PT_INTEGRATIONS)
        "verification" -> i18n.t(Strings.PT_VERIFICATION)
        "digest_top_clubs" -> i18n.t(Strings.PT_TOP_CLUBS)
        "digest_top_djs" -> i18n.t(Strings.PT_TOP_DJS)
        "digest_ranking" -> i18n.t(Strings.PT_RANKING)
        "digest_talents" -> i18n.t(Strings.PT_TALENTS)
        else -> type
    }
}

@Composable
private fun InAppPage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val fail: (Throwable) -> Unit = { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
    val n = s.inApp
    PageTitle(i18n.t(Strings.SET_IN_APP))
    if (n == null) { Hint(i18n.t(Strings.HOME_ERROR)); return }
    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.SET_IN_APP_MASTER), null, Icons.Outlined.Notifications, end = RowEnd.Toggle(n.inAppEnabled) { v ->
            scope.launch { repo.setInAppEnabled(v).onFailure(fail) }
        }, divider = false)
    }
    SettingsGroup(i18n.t(Strings.SET_IN_APP_TYPES_TITLE)) {
        IN_APP_TYPES.forEachIndexed { idx, type ->
            SettingsRow(pushTypeLabel(type), null, null, end = RowEnd.Toggle(n.types[type] != false) { v ->
                scope.launch { repo.setInAppType(type, v).onFailure(fail) }
            }, enabled = n.inAppEnabled, divider = idx < IN_APP_TYPES.lastIndex)
        }
    }
}

@Composable
private fun ReleasePage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val fail: (Throwable) -> Unit = { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
    val r = s.releaseRadar
    PageTitle("Release Radar")
    if (r == null) { Hint(i18n.t(Strings.HOME_ERROR)); return }
    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.SET_RELEASE_SUB), null, Icons.Outlined.Album, RowTone.Blue, RowEnd.Toggle(r.releaseRadarEnabled == true) { v ->
            scope.launch { repo.setReleaseRadar(enabled = v).onFailure(fail) }
        }, divider = false)
    }
    // Частота видна, только когда письма включены (спека)
    if (r.releaseRadarEnabled == true) {
        Hint(i18n.t(Strings.SET_FREQ_TITLE))
        Segmented(frequencyOptions(), r.releaseRadarFrequency ?: "immediate") { f -> scope.launch { repo.setReleaseRadar(frequency = f).onFailure(fail) } }
    }
}

@Composable
private fun frequencyOptions(): List<Pair<String, String>> {
    val i18n = useI18n()
    return listOf("immediate" to i18n.t(Strings.SET_FREQ_IMMEDIATE), "weekly_digest" to i18n.t(Strings.SET_WEEKLY_SUB))
}

@Composable
private fun ConcertPage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val c = s.concert
    PageTitle("Concert Radar")
    if (c == null) { Hint(i18n.t(Strings.HOME_ERROR)); return }
    var enabled by remember(c) { mutableStateOf(c.concertAlertsEnabled == true) }
    var freq by remember(c) { mutableStateOf(c.concertAlertsFrequency ?: "immediate") }
    var country by remember(c) { mutableStateOf(c.concertAlertCountry.orEmpty()) }
    var city by remember(c) { mutableStateOf(c.concertAlertCity.orEmpty()) }
    val hasLocation = !c.effectiveCity.isNullOrBlank() || !c.effectiveCountry.isNullOrBlank() || country.isNotBlank() || city.isNotBlank()

    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.SET_CONCERT_SUB), if (!hasLocation) i18n.t(Strings.SET_CONCERT_NEEDS_LOCATION) else null,
            Icons.Outlined.Map, RowTone.Blue, RowEnd.Toggle(enabled) { enabled = it }, enabled = hasLocation || enabled, divider = false)
    }
    Hint(i18n.t(Strings.SET_FREQ_TITLE))
    Segmented(frequencyOptions(), freq) { freq = it }
    SettingsGroup(null) {
        val source = if (c.concertAlertCity.isNullOrBlank() && c.concertAlertCountry.isNullOrBlank()) " · " + i18n.t(Strings.SET_FROM_PROFILE) else ""
        SettingsRow(i18n.t(Strings.SET_CONCERT_LOCATION), listOfNotNull(c.effectiveCity, c.effectiveCountry).joinToString(", ").ifEmpty { i18n.t(Strings.SET_NOT_SET) } + source,
            Icons.Outlined.LocationOn, RowTone.Blue, RowEnd.None, divider = false)
    }
    Hint(i18n.t(Strings.SET_CONCERT_OVERRIDE))
    CountryCityPicker(country, { country = it }, city, { city = it })
    PrimaryButton(i18n.t(Strings.SET_SAVE)) {
        scope.launch {
            repo.saveConcert(enabled, freq, country, city)
                .onSuccess { feedback.message = i18n.t(Strings.SET_SAVED) }
                .onFailure { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
        }
    }
}

@Composable
private fun ProfilePage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val p = s.profile
    var country by remember(p) { mutableStateOf(p?.country.orEmpty()) }
    var city by remember(p) { mutableStateOf(p?.city.orEmpty()) }
    var region by remember(p) { mutableStateOf(p?.region.orEmpty()) }
    var birth by remember(p) { mutableStateOf(p?.birthDate.orEmpty()) }
    val birthOk = com.djmetry.data.repository.isValidBirthDate(birth.trim(), currentYear())

    PageTitle(i18n.t(Strings.SET_GROUP_PROFILE))
    Hint(i18n.t(Strings.SET_REGION) + " · " + i18n.t(Strings.SET_REGION_HINT))
    CountryCityPicker(country, { country = it }, city, { city = it })
    // Регион — единственное поле со свободным вводом (спека)
    SettingsField(region, { region = it }, i18n.t(Strings.SET_REGION_FIELD))
    BirthDatePicker(birth) { birth = it }
    PrimaryButton(i18n.t(Strings.SET_SAVE), enabled = birthOk) {
        scope.launch {
            repo.saveProfile(country, city, region, birth)
                .onSuccess { feedback.message = i18n.t(Strings.SET_SAVED) }
                .onFailure { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
        }
    }
}

@Composable
private fun LanguagePage() {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    PageTitle(i18n.t(Strings.SET_LANGUAGE))
    SettingsGroup(null) {
        val all = com.djmetry.i18n.Locale.values()
        all.forEachIndexed { idx, loc ->
            val on = loc == i18n.locale
            SettingsRow(loc.displayName, null, null, end = if (on) RowEnd.Value("✓") else RowEnd.None, divider = idx < all.lastIndex, selected = on) {
                // Язык применяется сразу; на бэк — для писем и пушей (ошибка сети не мешает смене языка в приложении)
                i18n.setLocale(loc)
                scope.launch { repo.saveLanguage(loc.code) }
            }
        }
    }
}

@Composable
private fun GenresPage(s: SettingsState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val max = com.djmetry.data.repository.MAX_GENRES
    val all by produceState(emptyList<String>()) { value = repo.allGenres().getOrNull().orEmpty() }
    val saved = s.profile?.music_genre_preferences.orEmpty()
    var picked by remember(s.profile) { mutableStateOf(saved) }
    var picking by remember { mutableStateOf(false) }
    PageTitle(i18n.t(Strings.SET_GENRES))
    Hint(i18n.tWithArgs(Strings.SET_GENRES_HINT, arrayOf(max)) + " · ${picked.size}/$max")
    SettingsGroup(null) {
        // Порядок = приоритет: номер слева, удалить — справа
        picked.forEachIndexed { i, g ->
            SettingsRow("${i + 1}. $g", null, null, end = RowEnd.Value("✕"), divider = true) { picked = picked - g }
        }
        SettingsRow(i18n.t(Strings.SET_ADD_GENRE), null, Icons.Outlined.Add, end = RowEnd.Chevron, divider = false, enabled = picked.size < max) { picking = true }
    }
    if (picking) {
        SearchPickerDialog(
            title = i18n.t(Strings.SET_GENRES),
            items = all.filter { g -> picked.none { it.equals(g, ignoreCase = true) } },
            label = { it },
            onPick = { picked = com.djmetry.data.repository.addGenre(picked, it); picking = false },
            onDismiss = { picking = false },
        )
    }
    // Кнопка — только когда есть изменения (перестановка — тоже изменение)
    if (picked != saved) PrimaryButton(i18n.t(Strings.SET_SAVE)) {
        scope.launch {
            repo.saveGenres(picked)
                .onSuccess { feedback.message = i18n.t(Strings.SET_SAVED) }
                .onFailure { feedback.message = i18n.t(Strings.SET_ERROR_SAVE) }
        }
    }
}

/** Текущий год для проверки даты рождения (без платформенных дат в UI). */
internal fun currentYear(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

// ───────── Скелетон ─────────

@Composable
private fun SettingsSkeleton(layout: LayoutClass) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = if (layout == LayoutClass.Compact) 16.dp else 28.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.widthIn(max = if (layout == LayoutClass.Compact) 600.dp else 380.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(9) { SkeletonListRow(it, leading = false, cover = 32.dp) } }
        }
    }
}
