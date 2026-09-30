package com.djmetry.ui.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.data.booking.*
import com.djmetry.data.repository.BookingRoles
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.monthLabel
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** От этой ширины — лента слева, заявка справа (альбом, десктоп). */
internal const val BOOKING_DETAIL_MIN_DP = 900f

private val Orange = Color(0xFFFFB35B)
private val Violet = Color(0xFFB18CFF)

/**
 * Вкладка «Букинг», вариант A (design/booking/variants.html): роли вкладками (только доступные аккаунту, счётчик
 * непрочитанных), фильтры статусов, лента заявок с прогрессом 6 стадий и оплатой. Тап — заявка: этапы, деньги по роли,
 * действия. У артиста сверху «живая» карточка ближайшего шоу: этапы поездки и кнопка следующего статуса.
 * Заказчик видит только сумму и статус оплаты — без комиссий и налогов.
 */
@Composable
fun BookingTab(me: MeResponse?) {
    val container = LocalAppContainer.current
    val repo = container.booking
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    var roles by remember(me) { mutableStateOf<BookingRoles?>(null) }
    var role by remember(me) { mutableStateOf<BookingRole?>(null) }
    var companyIndex by remember { mutableStateOf(0) }
    var requests by remember { mutableStateOf<List<BookingRequest>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(BookingFilter.All) }
    var selected by remember { mutableStateOf<BookingRequest?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(me) { me?.let { m -> roles = repo.roles(m).also { role = role ?: it.default } } }
    val company = roles?.companies?.getOrNull(companyIndex)
    // Смена роли или агентства — чистый лист; «обновить» — поверх старых данных
    var loadedFor by remember { mutableStateOf<Pair<BookingRole?, String?>?>(null) }
    LaunchedEffect(role, company?.id, reload) {
        val r = role ?: return@LaunchedEffect
        val key = r to company?.id
        if (loadedFor != key) { requests = null; filter = BookingFilter.All; selected = null }
        failed = false
        repo.requests(r, company?.id, roles?.artistId).onSuccess { requests = it; loadedFor = key }.onFailure { failed = true; if (requests == null) requests = emptyList() }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2400); toast = null } }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val update: (BookingRequest) -> Unit = { r ->
        requests = requests?.map { if (it.id == r.id) r else it }
        if (selected?.id == r.id) selected = r
    }
    val act = BookingActions(
        open = { r ->
            selected = r
            val rl = role ?: return@BookingActions
            scope.launch { repo.open(rl, r, roles?.artistId).onSuccess { full -> update(full.copy(is_read = true)) } }
        },
        companyStatus = { r, status ->
            scope.launch { repo.setCompanyStatus(r.id, status).onSuccess(update).onFailure { toast = i18n.t(actionErrorKey(it)) } }
        },
        artistStatus = { r, status, transport ->
            val id = roles?.artistId
            if (id != null) scope.launch { repo.setArtistStatus(id, r.id, status, transport).onSuccess(update).onFailure { toast = i18n.t(actionErrorKey(it)) } }
        },
        cancel = { r -> scope.launch { repo.cancel(r.id).onSuccess { update(r.copy(deleted_by_requester = true)) }.onFailure { toast = i18n.t(actionErrorKey(it)) } } },
        restore = { r -> scope.launch { repo.restore(r.id).onSuccess { update(r.copy(deleted_by_requester = false)) }.onFailure { toast = i18n.t(actionErrorKey(it)) } } },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val wide = maxWidth.value >= BOOKING_DETAIL_MIN_DP
        val pad = if (wide) 28.dp else 16.dp
        val list = requests
        val shown = list?.filter { filter.matches(it.status) }
        val currentRole = role
        val live = if (currentRole == BookingRole.Artist) list?.let { liveShow(it, today) } else null
        // Широкий экран: справа сразу первая заявка, а не пустая панель
        LaunchedEffect(wide, shown?.firstOrNull()?.id) { if (wide && selected == null) shown?.firstOrNull()?.let { act.open(it) } }

        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = pad, end = if (wide) 0.dp else pad, top = 12.dp, bottom = LocalBottomClearance.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AutoSizeText(i18n.t(Strings.TAB_BOOKING), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 20.sp)
                        val rs = roles
                        if (rs == null) SkeletonBox(Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(16.dp))
                        else if (rs.list.size > 1) RoleTabs(rs.list, currentRole, unread = if (currentRole != null) list?.count { !it.is_read && currentRole != BookingRole.Requester } ?: 0 else 0) { role = it }
                        if (currentRole == BookingRole.Company && (roles?.companies?.size ?: 0) > 1) CompanyPicker(roles!!.companies.map { it.name }, companyIndex) { companyIndex = it }
                        if (live != null) LiveShowCard(live, today, act)
                        if (!list.isNullOrEmpty()) FilterChips(list, filter) { filter = it }
                    }
                }
                when {
                    list == null -> items(4) { SkeletonBox(Modifier.fillMaxWidth().height(118.dp), RoundedCornerShape(22.dp)) }
                    list.isEmpty() -> item {
                        Empty(i18n.t(if (failed) Strings.HOME_ERROR else when (currentRole) {
                            BookingRole.Company -> Strings.BK_EMPTY_COMPANY; BookingRole.Artist -> Strings.BK_EMPTY_ARTIST; else -> Strings.BK_EMPTY_REQUESTER
                        }), retry = if (failed) ({ reload++ }) else null)
                    }
                    shown.isNullOrEmpty() -> item { Empty(i18n.t(Strings.BK_EMPTY_FILTER)) }
                    else -> items(shown, key = { it.id }) { r ->
                        RequestCard(r, currentRole ?: BookingRole.Requester, roles?.artistId, selected = wide && selected?.id == r.id) { act.open(r) }
                    }
                }
            }
            if (wide) Box(Modifier.width(440.dp).fillMaxHeight().padding(top = 60.dp, end = pad, bottom = LocalBottomClearance.current + 16.dp)) {
                selected?.let { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
                    ) { RequestDetail(r, currentRole ?: BookingRole.Requester, roles?.artistId, today, act) }
                }
            }
        }

        // Телефон и узкий планшет: заявка шторкой снизу
        if (!wide) {
            val s = selected
            if (s != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(MutableInteractionSource(), null) { selected = null })
            AnimatedVisibility(s != null, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                val r = s ?: return@AnimatedVisibility
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.85f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(DJMetryColors.PanelStrong)
                        .clickable(MutableInteractionSource(), null) { }.verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = LocalBottomClearance.current + 12.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(CircleShape).background(Color(0xFF3A4B6A)))
                    Spacer(Modifier.height(12.dp))
                    RequestDetail(r, currentRole ?: BookingRole.Requester, roles?.artistId, today, act)
                }
            }
        }

        AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(toast.orEmpty(), color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Accent).padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

/** Действия над заявкой — общие для ленты, шторки и боковой панели. */
internal class BookingActions(
    val open: (BookingRequest) -> Unit,
    val companyStatus: (BookingRequest, String) -> Unit,
    val artistStatus: (BookingRequest, String, String?) -> Unit,
    val cancel: (BookingRequest) -> Unit,
    val restore: (BookingRequest) -> Unit,
)

/** «Живое» шоу артиста: ближайшее принятое/оплаченное/в пути, с сегодняшнего дня (или идущее прямо сейчас). */
internal fun liveShow(list: List<BookingRequest>, today: kotlinx.datetime.LocalDate): BookingRequest? =
    list.filter { isArtistActive(it.status) && !it.deleted_by_requester }
        .filter { r -> r.status in BookingStatus.ARTIST_PATH || bookingDay(r.event_date)?.let { it >= today.toString() } != false }
        // Уже в дороге — первым; иначе ближайшее по дате
        .minWithOrNull(compareBy<BookingRequest>({ it.status !in BookingStatus.ARTIST_PATH }, { bookingDay(it.event_date) ?: "9999" }))

// ── Шапка ──────────────────────────────────────────────────────────────────

@Composable
private fun RoleTabs(roles: List<BookingRole>, selected: BookingRole?, unread: Int, onSelect: (BookingRole) -> Unit) {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).padding(4.dp)) {
        roles.forEach { r ->
            val on = r == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) DJMetryColors.Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(r) }.padding(vertical = 9.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoSizeText(i18n.t(roleLabel(r)), TextStyle(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) DJMetryColors.Background else DJMetryColors.Muted, minFontSize = 10.sp, modifier = Modifier.weight(1f, fill = false))
                if (on && unread > 0) CountBadge(unread, Modifier.padding(start = 6.dp))
            }
        }
    }
}

internal fun roleLabel(r: BookingRole): String = when (r) {
    BookingRole.Requester -> Strings.BK_ROLE_REQUESTER
    BookingRole.Company -> Strings.BK_ROLE_COMPANY
    BookingRole.Artist -> Strings.BK_ROLE_ARTIST
}

@Composable
private fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    Text(
        if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier.clip(CircleShape).background(DJMetryColors.LowScore).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun CompanyPicker(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, CircleShape)
                .clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Business, null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Text(names.getOrNull(selected).orEmpty(), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, tint = DJMetryColors.Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            names.forEachIndexed { i, n ->
                DropdownMenuItem(text = { Text(n, color = if (i == selected) DJMetryColors.Accent else DJMetryColors.Text) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
private fun FilterChips(list: List<BookingRequest>, selected: BookingFilter, onSelect: (BookingFilter) -> Unit) {
    val i18n = useI18n()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookingFilter.entries.forEach { f ->
            val count = list.count { f.matches(it.status) }
            if (f != BookingFilter.All && count == 0) return@forEach
            val on = f == selected
            Text(
                "${i18n.t(filterLabel(f))} · $count", color = if (on) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 13.5.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Panel)
                    .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape).clickable(role = Role.Tab) { onSelect(f) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
}

private fun filterLabel(f: BookingFilter): String = when (f) {
    BookingFilter.All -> Strings.BK_F_ALL; BookingFilter.New -> Strings.BK_F_NEW; BookingFilter.Working -> Strings.BK_F_WORK
    BookingFilter.Paid -> Strings.BK_F_PAID; BookingFilter.OnTour -> Strings.BK_F_TOUR; BookingFilter.Done -> Strings.BK_F_DONE
}

// ── Карточка заявки ────────────────────────────────────────────────────────

/** Кого показать в заголовке: заказчику и агентству — артистов, артисту — агентство. */
private fun titleFor(r: BookingRequest, role: BookingRole): String = when (role) {
    BookingRole.Artist -> r.company?.name ?: r.company_name ?: "—"
    else -> r.artists.mapNotNull { it.name }.joinToString(", ").ifEmpty { r.company_name ?: "—" }
}

private fun imageFor(r: BookingRequest, role: BookingRole): String? =
    if (role == BookingRole.Artist) r.company_image_url else r.artists.firstOrNull()?.image_url ?: r.company_image_url

@Composable
internal fun eventDateLabel(date: String?): String {
    val d = bookingDay(date) ?: return ""
    val months = useI18n().t(Strings.MONTHS_SHORT)
    val parts = d.split('-')
    return "${parts[2].trimStart('0')} ${monthLabel(months, parts[1].toIntOrNull() ?: 0)}"
}

@Composable
private fun RequestCard(r: BookingRequest, role: BookingRole, artistId: String?, selected: Boolean, onClick: () -> Unit) {
    val i18n = useI18n()
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Panel).background(if (selected) DJMetryColors.Accent.copy(alpha = 0.07f) else Color.Transparent)
            .border(1.dp, if (selected) DJMetryColors.Accent else Color.White.copy(alpha = 0.05f), shape).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                CoverImage(imageFor(r, role), 44.dp, cornerRadius = 22.dp)
                if (!r.is_read && role != BookingRole.Requester) Box(Modifier.align(Alignment.TopEnd).size(11.dp).clip(CircleShape).background(DJMetryColors.Background).padding(2.dp).clip(CircleShape).background(DJMetryColors.LowScore))
            }
            Column(Modifier.weight(1f)) {
                Text(titleFor(r, role), color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(r.event_location?.takeIf { it.isNotBlank() }, eventDateLabel(r.event_date).takeIf { it.isNotEmpty() }, r.number?.let { "№$it" }).joinToString(" · ")
                Text(sub, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(r)
        }
        if (r.deleted_by_requester) Text(
            i18n.t(if (role == BookingRole.Requester) Strings.BK_CANCELLED_BY_YOU else Strings.BK_CANCELLED_BY_REQUESTER),
            color = DJMetryColors.LowScore, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        )
        StageBar(r.status)
        MoneyRow(r, role, artistId)
    }
}

@Composable
internal fun StatusPill(r: BookingRequest) {
    val i18n = useI18n()
    val (fg, icon) = statusStyle(r)
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(fg.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Text(i18n.t(statusLabel(r.status)), color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

internal fun statusLabel(status: String): String = when (status) {
    BookingStatus.NEW -> Strings.BK_ST_NEW
    BookingStatus.IN_PROGRESS -> Strings.BK_ST_IN_PROGRESS
    BookingStatus.ACCEPTED -> Strings.BK_ST_ACCEPTED
    BookingStatus.DECLINED -> Strings.BK_ST_DECLINED
    BookingStatus.PAID -> Strings.BK_ST_PAID
    BookingStatus.ON_THE_WAY -> Strings.BK_ST_ON_THE_WAY
    BookingStatus.AT_HOTEL -> Strings.BK_ST_AT_HOTEL
    BookingStatus.AT_VENUE -> Strings.BK_ST_AT_VENUE
    else -> Strings.BK_ST_FINISHED
}

internal fun transportIcon(t: String?): ImageVector = when (t) {
    "train" -> Icons.Outlined.Train
    "car" -> Icons.Outlined.DirectionsCar
    "ship" -> Icons.Outlined.DirectionsBoat
    else -> Icons.Outlined.Flight
}

internal fun transportLabel(t: String): String = when (t) {
    "train" -> Strings.BK_TR_TRAIN; "car" -> Strings.BK_TR_CAR; "ship" -> Strings.BK_TR_SHIP; else -> Strings.BK_TR_PLANE
}

private fun statusStyle(r: BookingRequest): Pair<Color, ImageVector> = when (r.status) {
    BookingStatus.NEW -> DJMetryColors.Accent2 to Icons.Outlined.FiberNew
    BookingStatus.IN_PROGRESS -> Violet to Icons.Outlined.Autorenew
    BookingStatus.ACCEPTED -> Violet to Icons.Outlined.TaskAlt
    BookingStatus.DECLINED -> DJMetryColors.LowScore to Icons.Outlined.Block
    BookingStatus.PAID -> DJMetryColors.Accent to Icons.Outlined.Payments
    BookingStatus.ON_THE_WAY -> Orange to transportIcon(r.travel_transport)
    BookingStatus.AT_HOTEL -> Orange to Icons.Outlined.Hotel
    BookingStatus.AT_VENUE -> Orange to Icons.Outlined.Stadium
    else -> DJMetryColors.Accent to Icons.Outlined.Celebration
}

/** 6 делений как на сайте; отклонённая — первое деление красным. */
@Composable
internal fun StageBar(status: String) {
    val stage = bookingStage(status)
    val declined = isDeclined(status)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(6) { i ->
            val color = when {
                declined -> if (i == 0) DJMetryColors.LowScore else DJMetryColors.Border
                i < stage -> DJMetryColors.Accent
                i == stage -> Orange
                else -> DJMetryColors.Border
            }
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun MoneyRow(r: BookingRequest, role: BookingRole, artistId: String?) {
    val i18n = useI18n()
    val amount = when (role) {
        BookingRole.Artist -> moneyLabel(ownFee(r, artistId), r.payment_currency)
        else -> moneyLabel(r.payment_amount, r.payment_currency)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(amount ?: "—", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        PaymentPill(r.payment_status, hasAmount = r.payment_amount != null)
    }
}

/** Гонорар этого артиста: его доля в `artists[]`, иначе общий по заявке. */
internal fun ownFee(r: BookingRequest, artistId: String?): Double? =
    r.artists.firstOrNull { it.spotify_artist_id == artistId }?.artist_fee_amount ?: r.artist_fee_amount

@Composable
private fun PaymentPill(status: String?, hasAmount: Boolean) {
    val i18n = useI18n()
    val (key, color) = when {
        !hasAmount && (status == null || status == "unpaid") -> Strings.BK_NO_PAYMENT to DJMetryColors.Muted
        status == "paid" -> Strings.BK_PAY_PAID to DJMetryColors.Accent
        status == "partially_paid" -> Strings.BK_PAY_PARTIAL to Orange
        else -> Strings.BK_PAY_UNPAID to DJMetryColors.LowScore
    }
    Text(i18n.t(key), color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun Empty(text: String, retry: (() -> Unit)? = null) {
    val i18n = useI18n()
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = DJMetryColors.Muted, modifier = Modifier.size(40.dp))
        Text(text, color = DJMetryColors.Text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        retry?.let {
            Text(i18n.t(Strings.HOME_RETRY), color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = it).padding(10.dp))
        }
    }
}
