package com.djmetry.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.models.*
import com.djmetry.data.booking.*
import com.djmetry.data.repository.BookingRepository
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.monthLabel
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.settings.Segmented
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Данные кабинета одной роли: заработок, агентство с артистами, команда, выступления (агентство);
 * агентства, налог, райдер и пресс-кит, выступления (артист). Грузится параллельно при открытии роли.
 */
@Stable
internal class CabinetState(val role: BookingRole, val companyId: String?, val artistId: String?, val isOwner: Boolean) {
    var earnings by mutableStateOf<BookingEarnings?>(null)
    var earningsFailed by mutableStateOf(false)
    var detail by mutableStateOf<BookingCompanyDetail?>(null)
    var members by mutableStateOf<List<BookingMember>?>(null)
    var performances by mutableStateOf<List<BookingPerformance>?>(null)
    var companies by mutableStateOf<List<ArtistCompanyLink>?>(null)
    var artistTax by mutableStateOf<Double?>(null)
    var rider by mutableStateOf<Boolean?>(null)
    var pressKit by mutableStateOf<Boolean?>(null)

    suspend fun load(repo: BookingRepository) = coroutineScope {
        launch { repo.earnings(role, companyId, artistId).onSuccess { earnings = it }.onFailure { earningsFailed = true } }
        launch { performances = repo.performances(role, companyId, artistId).getOrNull().orEmpty() }
        if (role == BookingRole.Company && companyId != null) {
            launch { reloadDetail(repo) }
            launch { reloadMembers(repo) }
        }
        if (role == BookingRole.Artist && artistId != null) {
            launch { reloadCompanies(repo) }
            launch { artistTax = repo.artistTax(artistId).getOrNull() }
            launch { rider = repo.hasDoc(artistId, BookingDoc.Rider) }
            launch { pressKit = repo.hasDoc(artistId, BookingDoc.PressKit) }
        }
    }

    suspend fun reloadDetail(repo: BookingRepository) { companyId?.let { id -> repo.companyDetail(id).onSuccess { detail = it } } }
    suspend fun reloadMembers(repo: BookingRepository) { companyId?.let { id -> members = repo.members(id).getOrNull().orEmpty() } }
    suspend fun reloadCompanies(repo: BookingRepository) { artistId?.let { id -> companies = repo.artistCompanies(id).getOrNull().orEmpty() } }
}

/** Открыть вкладку «Букинг» (и заявку) — ставит MainShell; зовут список уведомлений и пуши. */
val LocalOpenBooking = staticCompositionLocalOf<(BookingOpen) -> Unit> { {} }

/** «Пульт» над лентой (вариант A, design/booking/cabinet-variants.html): заработок и плитки разделов. */
@Composable
internal fun CabinetHub(s: CabinetState, list: List<BookingRequest>?, today: LocalDate, columns: Int, onOpen: (CabinetSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!s.earningsFailed) EarningsCard(s.earnings, s.role, list.orEmpty(), s.artistId, today)
        val sections = cabinetSections(s.role).filter { it != CabinetSection.Token || s.isOwner }
        sections.chunked(columns).forEach { row ->
            // Высота ряда — по самой высокой плитке (крупный шрифт iOS не обрезает подписи)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sec -> Tile(sec, s, Modifier.weight(1f).fillMaxHeight()) { onOpen(sec) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Заработок ──

@Composable
internal fun EarningsCard(e: BookingEarnings?, role: BookingRole, list: List<BookingRequest>, artistId: String?, today: LocalDate) {
    val i18n = useI18n()
    val ranges = earningsRanges(role)
    var range by remember(role) { mutableStateOf(EarningsRange.Month) }
    var beforeTax by remember(role) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(DJMetryColors.Accent.copy(alpha = 0.13f), DJMetryColors.Panel, DJMetryColors.Panel)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Payments, null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(i18n.t(if (role == BookingRole.Company) Strings.BC_EARN_COMPANY else Strings.BC_EARN_ARTIST), color = DJMetryColors.Muted, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(i18n.t(Strings.BC_BEFORE_TAX), color = DJMetryColors.Muted, fontSize = 12.5.sp)
            Switch(
                beforeTax, { beforeTax = it }, Modifier.scale(0.72f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DJMetryColors.Background, checkedTrackColor = DJMetryColors.Accent, checkedBorderColor = DJMetryColors.Accent,
                    uncheckedThumbColor = DJMetryColors.Muted, uncheckedTrackColor = Color(0xFF2C3D5C), uncheckedBorderColor = Color(0xFF2C3D5C),
                ),
            )
        }
        if (e == null) {
            SkeletonBox(Modifier.width(160.dp).height(38.dp), RoundedCornerShape(10.dp))
        } else {
            val p = e.period(range)
            val (main, rest) = splitCurrencies(ownEarnings(p, role, beforeTax))
            AutoSizeText(
                main?.let { moneyLabel(it.second, it.first) } ?: "0",
                TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black), color = DJMetryColors.Text, minFontSize = 22.sp,
            )
            val tail = listOfNotNull(
                rest.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "+ ${moneyLabel(it.second, it.first)}" },
                i18n.tWithArgs(Strings.BC_REQUESTS_N, arrayOf((p?.total_requests ?: 0).toString())),
            ).joinToString(" · ")
            Text(tail, color = DJMetryColors.Muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(2.dp))
            Segmented(ranges.map { it.name to i18n.t(rangeLabel(it)) }, range.name) { range = EarningsRange.valueOf(it) }
            // Нет оплат за полгода — столбики не показываем (ровная линия ничего не говорит)
            val bars = monthlyBars(list, role, artistId, main?.first, today)
            if (bars.any { it > 0 }) MonthBars(bars, today)
        }
    }
}

private fun rangeLabel(r: EarningsRange) = when (r) {
    EarningsRange.Week -> Strings.BC_R_WEEK
    EarningsRange.Month -> Strings.BC_R_MONTH
    EarningsRange.Year -> Strings.BC_R_YEAR
    EarningsRange.AllTime -> Strings.BC_R_ALL
}

/** Столбики последних месяцев; текущий — ярким. Пусто — ровная линия, без «ложных» пиков. */
@Composable
private fun MonthBars(values: List<Double>, today: LocalDate) {
    val months = useI18n().t(Strings.MONTHS_SHORT)
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v / max).toFloat().coerceIn(0.06f, 1f)).clip(RoundedCornerShape(5.dp))
                            .background(if (last) DJMetryColors.Accent else DJMetryColors.Accent.copy(alpha = 0.25f))
                    )
                }
                val m = today.minus(values.lastIndex - i, DateTimeUnit.MONTH).month.ordinal + 1
                Text(monthLabel(months, m), color = if (last) DJMetryColors.Text else DJMetryColors.Muted, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// ── Плитки разделов ──

internal fun sectionIcon(s: CabinetSection): ImageVector = when (s) {
    CabinetSection.Artists -> Icons.Outlined.Groups
    CabinetSection.Team -> Icons.Outlined.Badge
    CabinetSection.Token -> Icons.Outlined.Key
    CabinetSection.Taxes, CabinetSection.ArtistTax -> Icons.Outlined.Percent
    CabinetSection.Map -> Icons.Outlined.Public
    CabinetSection.Companies -> Icons.Outlined.Apartment
    CabinetSection.Files -> Icons.Outlined.PictureAsPdf
}

internal fun sectionTitle(s: CabinetSection): String = when (s) {
    CabinetSection.Artists -> Strings.BC_S_ARTISTS
    CabinetSection.Team -> Strings.BC_S_TEAM
    CabinetSection.Token -> Strings.BC_S_TOKEN
    CabinetSection.Taxes -> Strings.BC_S_TAXES
    CabinetSection.Map -> Strings.BC_S_MAP
    CabinetSection.Companies -> Strings.BC_S_COMPANIES
    CabinetSection.Files -> Strings.BC_S_FILES
    CabinetSection.ArtistTax -> Strings.BC_S_ARTIST_TAX
}

/** Проценты без «.0»: 15 %, 12,5 %. */
internal fun percentLabel(v: Double?): String = v?.let { if (it % 1.0 == 0.0) "${it.toLong()}%" else "$it%".replace('.', ',') } ?: "—"

@Composable
private fun tileSubtitle(sec: CabinetSection, s: CabinetState): String? {
    val i18n = useI18n()
    return when (sec) {
        CabinetSection.Artists -> s.detail?.artists?.let { a ->
            val pending = a.count { !it.approved }
            listOfNotNull(a.count { it.approved }.toString(), if (pending > 0) i18n.tWithArgs(Strings.BC_SUB_PENDING, arrayOf(pending.toString())) else null).joinToString(" · ")
        }
        CabinetSection.Team -> s.members?.size?.toString()
        CabinetSection.Token -> i18n.t(Strings.BC_SUB_INVITE)
        CabinetSection.Taxes -> s.detail?.company?.let { c ->
            listOfNotNull(percentLabel(c.default_company_tax_percent ?: 0.0), if (c.default_artist_calculates_own_tax == true) i18n.t(Strings.BC_SUB_OWN_TAX) else null).joinToString(" · ")
        }
        CabinetSection.Map -> s.performances?.let { i18n.tWithArgs(Strings.BC_SUB_SHOWS, arrayOf(it.size.toString())) }
        CabinetSection.Companies -> s.companies?.let { l -> l.firstOrNull()?.company?.name ?: i18n.t(Strings.BC_SUB_NONE) }
        CabinetSection.Files -> if (s.rider == null || s.pressKit == null) null else {
            val n = listOf(s.rider, s.pressKit).count { it == true }
            if (n == 0) i18n.t(Strings.BC_SUB_NONE) else i18n.tWithArgs(Strings.BC_SUB_FILES, arrayOf(n.toString()))
        }
        CabinetSection.ArtistTax -> percentLabel(s.artistTax)
    }
}

@Composable
private fun Tile(sec: CabinetSection, s: CabinetState, modifier: Modifier, onClick: () -> Unit) {
    val i18n = useI18n()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.05f), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(DJMetryColors.Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(sectionIcon(sec), null, tint = DJMetryColors.Accent, modifier = Modifier.size(19.dp))
        }
        // Без AutoSizeText: он на BoxWithConstraints, а ряд меряет высоту через IntrinsicSize
        Text(i18n.t(sectionTitle(sec)), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = tileSubtitle(sec, s)
        if (sub == null) SkeletonBox(Modifier.width(48.dp).height(12.dp), RoundedCornerShape(4.dp))
        else Text(sub, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
