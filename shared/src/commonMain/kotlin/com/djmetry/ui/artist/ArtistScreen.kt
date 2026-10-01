package com.djmetry.ui.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.config.AppConfig
import com.djmetry.data.repository.ArtistCard
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Открыть карточку артиста по Spotify ID из любого экрана (рейтинг, поиск, профиль, TOP 10). */
val LocalArtistNavigator = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Публичная карточка артиста — вариант A «Постер». Одни секции, три раскладки:
 * телефон — фото на полэкрана и колонка секций; планшет — постер закреплён слева, секции справа;
 * десктоп — широкий баннер и три колонки (музыка · концерты · Score / YouTube / соцсети).
 */
@Composable
fun ArtistScreen(spotifyArtistId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val i18n = useI18n()
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val followFlight = com.djmetry.ui.components.rememberSingleFlight(); val voteFlight = com.djmetry.ui.components.rememberSingleFlight()
    val layout = LocalLayoutClass.current
    // Карточка во ViewModel: поворот и возврат не перезагружают её; другой артист — чистое состояние
    // (раньше форма заявки прошлого артиста оставалась поверх карточки нового)
    val vm = com.djmetry.ui.search.appViewModel<ArtistViewModel>()
    vm.bind(spotifyArtistId)
    var attempt by vm::attempt
    var toast by vm::toast
    // «Забукать» — форма заявки внутри приложения (вариант B), а не переход на сайт
    var booking by vm::booking

    val state = vm.card
    LaunchedEffect(spotifyArtistId, i18n.locale, attempt) {
        val key = Triple(spotifyArtistId, i18n.locale.code, attempt)
        if (vm.loadedFor == key && vm.card != null) return@LaunchedEffect
        vm.card = null
        vm.card = container.artists.load(spotifyArtistId, i18n.locale.code)
        vm.loadedFor = key
    }
    LaunchedEffect(Unit) { container.discover.refreshMine() }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    val follows by container.discover.follows.collectAsState()
    val votes by container.discover.votes.collectAsState()
    val following = follows.any { it.spotifyArtistId == spotifyArtistId }
    val voted = spotifyArtistId in votes

    Box(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val result = state
        val card = result?.getOrNull()
        LoadingCrossfade(loading = result == null, skeleton = { ArtistSkeleton(layout) }) { when {
            card == null -> ErrorState(onBack, onRetry = { attempt++ })
            else -> {
                val d = card.details
                val actions = ArtistCardActions(
                    onBack = onBack,
                    onFollow = {
                        followFlight.run(scope) {
                            val r = if (following) container.discover.unfollow(d.spotifyArtistId)
                            else container.discover.follow(d.spotifyArtistId, d.name, d.imageUrl)
                            r.onSuccess { if (!following) toast = i18n.tWithArgs(Strings.TOAST_FOLLOWED, arrayOf(d.name)) }
                                .onFailure { toast = i18n.t(actionErrorKey(it)) }
                        }
                    },
                    onVote = {
                        voteFlight.run(scope) {
                            val r = if (voted) container.discover.removeVote(d.spotifyArtistId) else container.discover.vote(d.spotifyArtistId, d.name, d.imageUrl)
                            r.onSuccess { if (!voted) toast = i18n.tWithArgs(Strings.TOAST_VOTED, arrayOf(d.name)) }
                                .onFailure { toast = i18n.t(actionErrorKey(it)) }
                        }
                    },
                    onShare = {
                        clipboard.setText(AnnotatedString(shareUrl(d)))
                        toast = i18n.t(Strings.ARTIST_LINK_COPIED)
                    },
                    onBook = { booking = d.spotifyArtistId },
                    openUrl = { uri.openUri(it) },
                )
                when (layout) {
                    LayoutClass.Compact -> PhoneLayout(card, following, voted, actions)
                    LayoutClass.Medium -> TabletLayout(card, following, voted, actions)
                    LayoutClass.Expanded -> DesktopLayout(card, following, voted, actions)
                }
            }
        } }
        booking?.let { id -> com.djmetry.ui.booking.BookingRequestScreen(id, onClose = { booking = null }, onSent = { toast = it }) }
        AnimatedVisibility(
            visible = toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp),
        ) {
            Text(
                toast.orEmpty(), color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Accent).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PhoneLayout(card: ArtistCard, following: Boolean, voted: Boolean, a: ArtistCardActions) {
    val i18n = useI18n()
    Column(Modifier.fillMaxSize().verticalScroll(com.djmetry.ui.search.appViewModel<ArtistViewModel>().scroll).padding(bottom = LocalBottomClearance.current)) {
        ArtistPoster(card, 470.dp, RectangleShape) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp)) {
                GlassButton(Icons.AutoMirrored.Filled.ArrowBack, i18n.t(Strings.ARTIST_BACK), a.onBack)
                Spacer(Modifier.weight(1f))
                GlassButton(Icons.Outlined.Share, i18n.t(Strings.ARTIST_COPY_LINK), a.onShare)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ArtistActionsRow(following, voted, a, showShare = false)
            if (card.hasBooking) BookArtistButton(a.onBook)
            Sections(card, a, tracks = 5, events = 5)
        }
    }
    // Подложка под статус-бар: при прокрутке текст не залезает под часы
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(DJMetryColors.Background.copy(alpha = 0.85f)))
}

@Composable
private fun TabletLayout(card: ArtistCard, following: Boolean, voted: Boolean, a: ArtistCardActions) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 16.dp)) {
        BackRow(a.onBack)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtistPoster(card, 440.dp, RoundedCornerShape(28.dp))
                ArtistActionsRow(following, voted, a)
                if (card.hasBooking) BookArtistButton(a.onBook)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { Sections(card, a, tracks = 5, events = 4) }
        }
    }
}

@Composable
private fun DesktopLayout(card: ArtistCard, following: Boolean, voted: Boolean, a: ArtistCardActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BackRow(a.onBack) { if (card.hasBooking) BookArtistButton(a.onBook, Modifier.width(220.dp)) }
        ArtistBanner(card, following, voted, a)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) { ArtistTracksCard(card.tracks, limit = 5) { t -> t.externalUrl?.let(a.openUrl) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.djmetry.ui.djmap.ArtistMapButton(card.details.spotifyArtistId)
                ArtistEventsCard(card.events, limit = 5, onOpen = a.openUrl)
            }
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ArtistScoreCard(card)
                YouTubeCard(card.details.youtube, a.openUrl)
                SocialsCard(socialLinks(card.details.socialMedia), a.openUrl, a.onShare)
            }
        }
    }
}

/** Секции в одну колонку (телефон, правая колонка планшета). */
@Composable
private fun Sections(card: ArtistCard, a: ArtistCardActions, tracks: Int, events: Int) {
    ArtistScoreCard(card)
    ArtistTracksCard(card.tracks, limit = tracks) { t -> t.externalUrl?.let(a.openUrl) }
    com.djmetry.ui.djmap.ArtistMapButton(card.details.spotifyArtistId)
    ArtistEventsCard(card.events, limit = events, onOpen = a.openUrl)
    YouTubeCard(card.details.youtube, a.openUrl)
    SocialsCard(socialLinks(card.details.socialMedia), a.openUrl, a.onShare)
}

@Composable
private fun ErrorState(onBack: () -> Unit, onRetry: () -> Unit) {
    val i18n = useI18n()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
        BackRow(onBack)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(i18n.t(Strings.HOME_ERROR), color = DJMetryColors.Muted, fontSize = 15.sp)
            TextButton(onClick = onRetry) { Text(i18n.t(Strings.HOME_RETRY), color = DJMetryColors.Accent) }
        }
    }
}

/** Открытая карточка артиста: данные, форма заявки, тост и прокрутка. Один экземпляр — на текущего артиста. */
internal class ArtistViewModel : androidx.lifecycle.ViewModel() {
    private var boundTo: String? = null
    var attempt by mutableStateOf(0)
    var toast by mutableStateOf<String?>(null)
    var booking by mutableStateOf<String?>(null)
    var card by mutableStateOf<Result<ArtistCard>?>(null)
    var loadedFor: Triple<String, String, Int>? = null
    var scroll = androidx.compose.foundation.ScrollState(0)
        private set

    /** Открыли другого артиста — всё с чистого листа (прокрутка наверх, без чужой формы заявки). */
    fun bind(id: String) {
        if (boundTo == id) return
        boundTo = id
        attempt = 0; toast = null; booking = null; card = null; loadedFor = null
        scroll = androidx.compose.foundation.ScrollState(0)
    }
}
