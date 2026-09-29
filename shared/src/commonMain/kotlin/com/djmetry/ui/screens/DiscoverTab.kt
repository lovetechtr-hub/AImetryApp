package com.djmetry.ui.screens

import com.djmetry.data.repository.RatingSource
import com.djmetry.data.repository.RatingRow
import com.djmetry.ui.components.shimmer
import com.djmetry.ui.components.SkeletonListRow
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.components.SkeletonCircle
import com.djmetry.ui.components.SkeletonBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalLayoutClass
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.ApiException
import com.djmetry.api.models.FollowedArtist
import com.djmetry.api.models.RankedArtist
import com.djmetry.data.repository.DeckSource
import com.djmetry.data.repository.VoteLimitException
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.readableWidth
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Orange = Color(0xFFFFB35B)

/** Действие со свайпа или кнопки. */
internal enum class SwipeAction { Skip, Follow, Vote }

/** Решение по жесту: вправо — подписка, влево — пропуск, вверх — голос, иначе вернуть карточку. */
internal fun swipeDecision(dx: Float, dy: Float, threshold: Float): SwipeAction? = when {
    dx > threshold -> SwipeAction.Follow
    dx < -threshold -> SwipeAction.Skip
    dy < -threshold * 1.2f && abs(dx) < threshold -> SwipeAction.Vote
    else -> null
}

/** Ключ текста ошибки действия. */
internal fun actionErrorKey(error: Throwable): String = when {
    error is VoteLimitException -> Strings.TOAST_VOTE_LIMIT
    error is ApiException && error.isUnauthorized -> Strings.TOAST_NEED_LOGIN
    else -> Strings.TOAST_FAILED
}

/** Клавиши на планшете с клавиатурой: ← мимо, ↑ голос, → следить. */
internal fun keyAction(key: Key): SwipeAction? = when (key) {
    Key.DirectionLeft -> SwipeAction.Skip
    Key.DirectionRight -> SwipeAction.Follow
    Key.DirectionUp -> SwipeAction.Vote
    else -> null
}

/** Состояние колоды — общее для самой колоды и боковых панелей планшета. */
@Stable
internal class DeckState(private val repo: DiscoverRepository, private val scope: CoroutineScope) {
    var source by mutableStateOf(DeckSource.Rising)
    var reload by mutableStateOf(0)
    val cards = mutableStateListOf<RankedArtist>()
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)

    suspend fun load() {
        loading = true
        failed = false
        repo.deck(source)
            .onSuccess { cards.clear(); cards.addAll(it) }
            .onFailure { failed = true }
        loading = false
    }

    /** Убирает карточку и выполняет действие; результат — текст-ключ для тоста через [onResult]. */
    fun act(artist: RankedArtist, action: SwipeAction, onResult: (success: Boolean, error: Throwable?) -> Unit) {
        cards.remove(artist)
        scope.launch {
            when (action) {
                SwipeAction.Skip -> Unit
                SwipeAction.Follow -> repo.follow(artist).fold({ onResult(true, null) }, { onResult(false, it) })
                SwipeAction.Vote -> repo.vote(artist.spotifyArtistId).fold(
                    { onResult(true, null) },
                    { onResult(false, it); cards.add(0, artist) },
                )
            }
        }
    }
}

@Composable
private fun rememberDeckState(): DeckState {
    val repo = LocalAppContainer.current.discover
    val scope = rememberCoroutineScope()
    val state = remember { DeckState(repo, scope) }
    LaunchedEffect(state.source, state.reload) { state.load() }
    return state
}

/** Вкладка «Открытия»: колода карточек + список подписок. На планшете — с боковыми панелями. */
@Composable
fun DiscoverTab(onOpenSearch: () -> Unit) {
    val i18n = useI18n()
    val layout = LocalLayoutClass.current
    var showFollowing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val deck = rememberDeckState()

    LaunchedEffect(toast) {
        if (toast != null) { delay(2400); toast = null }
    }
    val act = { artist: RankedArtist, action: SwipeAction ->
        deck.act(artist, action) { ok, error ->
            if (action == SwipeAction.Skip) return@act
            toast = when {
                !ok -> i18n.t(actionErrorKey(error ?: Exception()))
                action == SwipeAction.Follow -> i18n.tWithArgs(Strings.TOAST_FOLLOWED, arrayOf(artist.name))
                else -> i18n.tWithArgs(Strings.TOAST_VOTED, arrayOf(artist.name))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (layout.isTablet) 28.dp else 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DJMetryLogo.HashMark, "DJMetry", tint = DJMetryColors.Accent, modifier = Modifier.size(30.dp, 27.dp))
                Spacer(Modifier.weight(1f))
                Segmented(
                    left = i18n.t(Strings.SEG_DISCOVER),
                    right = i18n.t(Strings.SEG_FOLLOWING),
                    rightSelected = showFollowing,
                    onSelect = { showFollowing = it },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, i18n.t(Strings.TAB_SEARCH), tint = DJMetryColors.Text)
                }
            }
            when {
                showFollowing -> FollowingList(onToast = { toast = it })
                layout == LayoutClass.Expanded -> Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        DeckChips(deck)
                        DeckArea(deck, act, Modifier.weight(1f).widthIn(max = 520.dp))
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(
                        Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        VotesPanel(deck)
                        NextInDeckPanel(deck, act)
                        TopTenPanel()
                    }
                }
                layout == LayoutClass.Medium -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DeckChips(deck)
                    DeckArea(deck, act, Modifier.weight(1f).widthIn(max = 600.dp))
                    Row(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.weight(1f)) { VotesPanel(deck) }
                        Box(Modifier.weight(1f)) { NextInDeckPanel(deck, act) }
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    DeckChips(deck)
                    DeckArea(deck, act, Modifier.weight(1f))
                }
            }
        }
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp),
        ) {
            Text(
                toast.orEmpty(),
                color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp).shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Accent).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Segmented(left: String, right: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Panel).padding(4.dp)) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val selected = isRight == rightSelected
            Text(
                label,
                color = if (selected) DJMetryColors.Background else DJMetryColors.Muted,
                fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(11.dp))
                    .background(if (selected) DJMetryColors.Accent else Color.Transparent)
                    .clickable { onSelect(isRight) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DeckChips(deck: DeckState) {
    val i18n = useI18n()
    val chips = listOf(
        DeckSource.Rising to i18n.t(Strings.CHIP_RISING),
        DeckSource.Breakthrough to i18n.t(Strings.CHIP_BREAKTHROUGH),
        DeckSource.Stable to i18n.t(Strings.CHIP_STABLE),
        DeckSource.Top to "TOP 100",
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { (chipSource, label) ->
            val selected = chipSource == deck.source
            Text(
                label,
                color = if (selected) DJMetryColors.Background else DJMetryColors.Muted,
                fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (selected) DJMetryColors.Accent else DJMetryColors.Panel)
                    .border(1.dp, if (selected) DJMetryColors.Accent else Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                    .clickable { deck.source = chipSource }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Стопка карточек + кнопки. Стрелки на клавиатуре дублируют свайпы. */
@Composable
private fun DeckArea(deck: DeckState, act: (RankedArtist, SwipeAction) -> Unit, modifier: Modifier) {
    val i18n = useI18n()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = LocalBottomClearance.current)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val action = keyAction(event.key)
                if (event.type == KeyEventType.KeyDown && action != null && deck.cards.isNotEmpty()) {
                    act(deck.cards.first(), action); true
                } else false
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            when {
                deck.loading -> Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(28.dp))) {
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.size(120.dp, 24.dp), RoundedCornerShape(12.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(34.dp).shimmer(RoundedCornerShape(10.dp)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkeletonCircle(44.dp); Spacer(Modifier.width(12.dp)); SkeletonLine(0.5f, 12.dp)
                        }
                    }
                }
                deck.failed -> EmptyDeck(i18n.t(Strings.HOME_ERROR), null, i18n.t(Strings.HOME_RETRY)) { deck.reload++ }
                deck.cards.isEmpty() -> EmptyDeck(i18n.t(Strings.DECK_EMPTY_TITLE), i18n.t(Strings.DECK_EMPTY_TEXT), i18n.t(Strings.DECK_RELOAD)) { deck.reload++ }
                else -> {
                    // Рисуем снизу вверх: верхняя карточка — последняя
                    deck.cards.take(3).reversed().forEach { artist ->
                        val depth = deck.cards.indexOf(artist)
                        key(artist.spotifyArtistId) {
                            SwipeCard(artist, depth, onAction = { act(artist, it) })
                        }
                    }
                }
            }
        }
        if (!deck.loading && !deck.failed && deck.cards.isNotEmpty()) {
            val top = deck.cards.first()
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Filled.Close, i18n.t(Strings.ACTION_SKIP), DJMetryColors.LowScore, DJMetryColors.Panel) { act(top, SwipeAction.Skip) }
                RoundAction(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.ACTION_VOTE), Orange, DJMetryColors.Panel) { act(top, SwipeAction.Vote) }
                RoundAction(Icons.Filled.Favorite, i18n.t(Strings.ACTION_FOLLOW), DJMetryColors.Background, DJMetryColors.Accent, big = true) { act(top, SwipeAction.Follow) }
            }
            Text(i18n.t(Strings.DECK_HINT), color = DJMetryColors.Muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ───────── Боковые панели планшета ─────────

@Composable
private fun SidePanel(title: String, action: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            action?.let { Text(it, color = DJMetryColors.Accent, fontSize = 12.sp) }
        }
        content()
    }
}

/** Голоса пользователя: 3 слота. Имена берём из подписок, колоды и TOP — /vote/status отдаёт только ID. */
@Composable
private fun VotesPanel(deck: DeckState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.discover
    val scope = rememberCoroutineScope()
    val votes by repo.votes.collectAsState()
    val follows by repo.follows.collectAsState()
    SidePanel(i18n.t(Strings.YOUR_VOTES), "${votes.size}/${DiscoverRepository.MAX_VOTES}") {
        votes.forEach { id ->
            val followed = follows.firstOrNull { it.spotifyArtistId == id }
            val card = deck.cards.firstOrNull { it.spotifyArtistId == id }
            PanelRow(
                imageUrl = followed?.imageUrl ?: card?.imageUrl,
                title = followed?.name ?: card?.name ?: "…",
                subtitle = null,
                trailing = { Icon(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.ACTION_VOTE), tint = Orange, modifier = Modifier.clickable { scope.launch { repo.removeVote(id) } }) },
            )
        }
        repeat((DiscoverRepository.MAX_VOTES - votes.size).coerceAtLeast(0)) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).border(1.5.dp, DJMetryColors.Border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("↑ ${i18n.t(Strings.ACTION_VOTE)}", color = DJMetryColors.Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun NextInDeckPanel(deck: DeckState, act: (RankedArtist, SwipeAction) -> Unit) {
    val i18n = useI18n()
    val next = deck.cards.drop(1).take(3)
    if (next.isEmpty()) return
    SidePanel(i18n.t(Strings.NEXT_IN_DECK), null) {
        next.forEach { artist ->
            PanelRow(
                imageUrl = artist.imageUrl,
                title = artist.name,
                subtitle = artist.genres.firstOrNull(),
                trailing = {
                    artist.trend?.score7d?.takeIf { it > 0 }?.let { Text("+${formatDelta(it)}", color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        ?: artist.djmetryScore?.let { Text(formatDelta(it), color = DJMetryColors.Text, fontSize = 13.sp) }
                },
                onClick = { act(artist, SwipeAction.Follow) },
            )
        }
    }
}

@Composable
private fun TopTenPanel() {
    val container = LocalAppContainer.current
    // Тот же кэш, что у вкладки «Рейтинг» — без лишнего запроса; null — ещё грузится (скелетон, без рывка)
    val top by produceState<List<RatingRow>?>(null) {
        value = container.rating.load(RatingSource.Top100).getOrNull()?.take(5).orEmpty()
    }
    val rows = top
    if (rows != null && rows.isEmpty()) return
    val openArtist = LocalArtistNavigator.current
    SidePanel("DJMetry TOP 10", "TOP 100") {
        if (rows == null) {
            repeat(5) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(42.dp), RoundedCornerShape(12.dp))
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { SkeletonLine(listOf(0.7f, 0.55f, 0.62f, 0.5f, 0.66f)[i], 12.dp) }
                    SkeletonBox(Modifier.size(38.dp, 13.dp), RoundedCornerShape(5.dp))
                }
            }
        } else rows.forEach { row ->
            PanelRow(
                imageUrl = row.imageUrl,
                title = "${row.position}  ${row.name}",
                subtitle = null,
                trailing = { row.score?.let { Text(formatScore(it), color = DJMetryColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } },
                onClick = { row.spotifyArtistId?.let(openArtist) },
            )
        }
    }
}

@Composable
private fun PanelRow(imageUrl: String?, title: String, subtitle: String?, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(imageUrl, 42.dp, cornerRadius = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

@Composable
private fun SwipeCard(artist: RankedArtist, depth: Int, onAction: (SwipeAction) -> Unit) {
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val isTop = depth == 0
    val stackScale by animateFloatAsStateCompat(1f - depth * 0.05f)
    val stackShift by animateFloatAsStateCompat(depth * 14f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val coverSize = maxOf(maxWidth, maxHeight)
        val threshold = width * 0.28f
        val dx = offsetX.value
        val dy = offsetY.value

        fun fly(action: SwipeAction) {
            scope.launch {
                val target = when (action) {
                    SwipeAction.Follow -> Offset(width * 1.6f, dy - 80f)
                    SwipeAction.Skip -> Offset(-width * 1.6f, dy - 80f)
                    SwipeAction.Vote -> Offset(dx, -width * 2.2f)
                }
                launch { offsetX.animateTo(target.x, tween(260)) }
                offsetY.animateTo(target.y, tween(260))
                onAction(action)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dx
                    translationY = dy + stackShift.dp.toPx()
                    rotationZ = dx / 22f
                    scaleX = stackScale
                    scaleY = stackScale
                }
                .shadow(if (isTop) 26.dp else 10.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(30.dp))
                .background(DJMetryColors.PanelStrong)
                .then(
                    if (!isTop) Modifier else Modifier.pointerInput(artist.spotifyArtistId) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + drag.x)
                                    offsetY.snapTo(offsetY.value + drag.y)
                                }
                            },
                            onDragEnd = {
                                val action = swipeDecision(offsetX.value, offsetY.value, threshold)
                                if (action != null) fly(action) else scope.launch {
                                    launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                                    offsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                            },
                        )
                    }
                ),
        ) {
            CoverImage(artist.imageUrl, coverSize, cornerRadius = 0.dp, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.42f to Color.Transparent, 1f to DJMetryColors.Background.copy(alpha = 0.97f))))

            if (isTop) {
                Stamp(i18n.t(Strings.STAMP_FOLLOW), DJMetryColors.Accent, -14f, (dx / threshold).coerceIn(0f, 1f), Modifier.align(Alignment.TopStart).padding(22.dp))
                Stamp(i18n.t(Strings.STAMP_SKIP), DJMetryColors.LowScore, 14f, (-dx / threshold).coerceIn(0f, 1f), Modifier.align(Alignment.TopEnd).padding(22.dp))
                Stamp(i18n.t(Strings.STAMP_VOTE), Orange, 0f, (-dy / (threshold * 1.2f) - abs(dx) / (threshold * 2)).coerceIn(0f, 1f), Modifier.align(Alignment.Center))
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val delta = artist.trend?.score7d
                    val badge = when {
                        delta != null && delta > 0 -> i18n.tWithArgs(Strings.HOME_WEEK, arrayOf(formatDelta(delta)))
                        artist.position != null -> "#${artist.position} TOP 100"
                        else -> null
                    }
                    badge?.let { Tag(it, DJMetryColors.Accent, DJMetryColors.Accent.copy(alpha = 0.18f), Icons.AutoMirrored.Filled.TrendingUp) }
                    artist.genres.firstOrNull()?.let { Tag(it, DJMetryColors.Text, Color.White.copy(alpha = 0.12f), null) }
                }
                Text(
                    artist.name, color = DJMetryColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                )
                artist.djmetryScore?.let { score ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(score)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("DJMetry Score", color = DJMetryColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(i18n.t(Strings.SCORE_CAPTION), color = DJMetryColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun animateFloatAsStateCompat(target: Float): State<Float> =
    androidx.compose.animation.core.animateFloatAsState(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "stack")

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, alpha: Float, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
        modifier = modifier.graphicsLayer { this.alpha = alpha; rotationZ = rotation }
            .border(3.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun Tag(text: String, color: Color, background: Color, icon: ImageVector?) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ScoreRing(score: Double) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo((score / 100).toFloat().coerceIn(0f, 1f), tween(900)) }
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 4.dp.toPx()
            val arc = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(DJMetryColors.Border, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(DJMetryColors.Accent, -90f, 360f * progress.value, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${score.toInt()}", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color, background: Color, big: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 66.dp else 58.dp
    Box(
        Modifier.size(size)
            .shadow(if (big) 16.dp else 6.dp, CircleShape, ambientColor = if (big) DJMetryColors.Accent else Color.Black, spotColor = if (big) DJMetryColors.Accent else Color.Black)
            .clip(CircleShape).background(background)
            .border(1.dp, if (big) Color.Transparent else DJMetryColors.Border, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (big) 30.dp else 26.dp))
    }
}

@Composable
private fun EmptyDeck(title: String, text: String?, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(DJMetryLogo.HashMark, null, tint = DJMetryColors.Border, modifier = Modifier.size(70.dp, 63.dp))
        Text(title, color = DJMetryColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        text?.let { Text(it, color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        Button(
            onClick = onAction, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DJMetryColors.Accent, contentColor = DJMetryColors.Background),
        ) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun FollowingList(onToast: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.discover
    val scope = rememberCoroutineScope()
    val follows by repo.follows.collectAsState()
    val votes by repo.votes.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repo.refreshMine(); loaded = true }

    when {
        !loaded -> Column(Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(7) { SkeletonListRow(it, leading = false, trailing = false) } }
        follows.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
            Text(i18n.t(Strings.FOLLOWING_EMPTY), color = DJMetryColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
        else -> LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(follows, key = { it.spotifyArtistId }) { artist ->
                FollowRow(artist, voted = artist.spotifyArtistId in votes, onUnfollow = {
                    scope.launch { repo.unfollow(artist.spotifyArtistId).onFailure { onToast(i18n.t(actionErrorKey(it))) } }
                }, onToggleVote = {
                    scope.launch {
                        val result = if (artist.spotifyArtistId in votes) repo.removeVote(artist.spotifyArtistId) else repo.vote(artist.spotifyArtistId)
                        result.onFailure { onToast(i18n.t(actionErrorKey(it))) }
                    }
                })
            }
        }
    }
}

@Composable
private fun FollowRow(artist: FollowedArtist, voted: Boolean, onUnfollow: () -> Unit, onToggleVote: () -> Unit) {
    val i18n = useI18n()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Panel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(artist.imageUrl, 50.dp, cornerRadius = 14.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(artist.name ?: artist.spotifyArtistId, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (voted) Text(i18n.t(Strings.YOUR_VOTE), color = Orange, fontSize = 12.sp)
        }
        IconButton(onClick = onToggleVote) {
            Icon(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.ACTION_VOTE), tint = if (voted) Orange else DJMetryColors.Muted)
        }
        TextButton(onClick = onUnfollow) { Text(i18n.t(Strings.UNFOLLOW), color = DJMetryColors.Muted, fontSize = 13.sp) }
    }
}
