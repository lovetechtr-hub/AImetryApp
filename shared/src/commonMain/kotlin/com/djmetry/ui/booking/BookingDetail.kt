package com.djmetry.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.api.models.BookingRequest
import com.djmetry.data.booking.*
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors

private val Orange = Color(0xFFFFB35B)

/** Заявка целиком: кто · где · когда, статус и этапы, деньги по роли, действия роли. */
@Composable
internal fun RequestDetail(r: BookingRequest, role: BookingRole, artistId: String?, today: kotlinx.datetime.LocalDate, act: BookingActions) {
    val i18n = useI18n()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val image = imageFor(r, role)
            CoverImage(image, 52.dp, cornerRadius = 26.dp)
            Column(Modifier.weight(1f)) {
                val title = if (role == BookingRole.Artist) r.company?.name ?: r.company_name else r.artists.mapNotNull { it.name }.joinToString(", ")
                Text(listOfNotNull(title?.ifEmpty { null }, r.number?.let { "№$it" }).joinToString(" · "), color = DJMetryColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(r.event_location?.takeIf { it.isNotBlank() }, eventDateLabel(r.event_date).takeIf { it.isNotEmpty() }, r.event_time?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = DJMetryColors.Muted, fontSize = 13.sp)
            }
        }
        // Статус — отдельной строкой: длинные («Артист закончил выступление») не сжимают название
        StatusPill(r)
        StageBar(r.status)
        if (r.deleted_by_requester) Text(
            i18n.t(if (role == BookingRole.Requester) Strings.BK_CANCELLED_BY_YOU else Strings.BK_CANCELLED_BY_REQUESTER),
            color = DJMetryColors.LowScore, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )

        // Деньги: заказчику — только сумма и оплата; агентству — всё; артисту — его гонорар до/после налога
        Column {
            val pay = moneyLabel(r.payment_amount, r.payment_currency)
            val payStatus = i18n.t(when (r.payment_status) { "paid" -> Strings.BK_PAY_PAID; "partially_paid" -> Strings.BK_PAY_PARTIAL; else -> Strings.BK_PAY_UNPAID }) +
                (r.payment_percent?.takeIf { it in 0.1..99.9 }?.let { " · ${it.toInt()}%" } ?: "")
            when (role) {
                BookingRole.Requester -> {
                    Kv(Strings.BK_KV_PAYMENT, pay?.let { "$it · $payStatus" } ?: i18n.t(Strings.BK_NO_PAYMENT))
                    if (r.company_name == null) Kv(Strings.BK_KV_COMPANY, i18n.t(Strings.BK_COMPANY_OFF)) else Kv(Strings.BK_KV_COMPANY, r.company_name)
                }
                BookingRole.Company -> {
                    Kv(Strings.BK_KV_PAYMENT, pay?.let { "$it · $payStatus" } ?: i18n.t(Strings.BK_NO_PAYMENT))
                    moneyLabel(r.company_fee_amount, r.payment_currency)?.let { Kv(Strings.BK_KV_COMPANY_FEE, withTax(it, r.company_fee_amount_after_tax, r.payment_currency)) }
                    moneyLabel(r.artist_fee_amount, r.payment_currency)?.let { Kv(Strings.BK_KV_ARTIST_FEE, withTax(it, r.artist_fee_amount_after_tax, r.payment_currency)) }
                    r.requester_name?.let { Kv(Strings.BK_KV_REQUESTER, it) }
                }
                BookingRole.Artist -> {
                    val fee = ownFee(r, artistId)
                    moneyLabel(fee, r.payment_currency)?.let {
                        // Нетто — от бэкенда; считает налог сам — помечаем «до налога»
                        val value = when {
                            r.artist_fee_amount_after_tax != null -> withTax(it, r.artist_fee_amount_after_tax, r.payment_currency)
                            r.artist_calculates_own_tax == true -> "$it · ${i18n.t(Strings.BK_BEFORE_TAX)}"
                            else -> it
                        }
                        Kv(Strings.BK_KV_YOUR_FEE, value)
                    }
                    Kv(Strings.BK_KV_PAYMENT, payStatus)
                    (r.company?.name ?: r.company_name)?.let { Kv(Strings.BK_KV_COMPANY, it) }
                }
            }
            r.event_type?.takeIf { it.isNotBlank() }?.let { Kv(Strings.BK_KV_EVENT, it) }
            r.expected_attendees?.let { Kv(Strings.BK_KV_GUESTS, moneyLabel(it.toDouble(), null) ?: it.toString()) }
        }
        r.message?.takeIf { it.isNotBlank() && role != BookingRole.Requester }?.let {
            Text(i18n.t(Strings.BK_KV_MESSAGE), color = DJMetryColors.Muted, fontSize = 12.5.sp)
            Text(it, color = DJMetryColors.Text, fontSize = 14.sp, lineHeight = 19.sp)
        }

        // Действия роли
        when (role) {
            BookingRole.Company -> if (!r.deleted_by_requester) {
                val acts = companyActionsFor(r)
                // Главное действие — во всю ширину, остальные ниже парой: подписи не обрезаются даже в узкой панели
                val main = acts.lastOrNull()?.takeIf { it != BookingStatus.DECLINED }
                main?.let { s -> ActionButton(i18n.t(companyActionLabel(s)), companyActionIcon(s), primary = true) { act.companyStatus(r, s) } }
                val rest = acts.filter { it != main }
                if (rest.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rest.forEach { s ->
                        ActionButton(i18n.t(companyActionLabel(s)), companyActionIcon(s), danger = s == BookingStatus.DECLINED, modifier = Modifier.weight(1f)) { act.companyStatus(r, s) }
                    }
                }
                Text(i18n.t(Strings.BK_MONEY_ON_SITE), color = DJMetryColors.Muted, fontSize = 12.sp)
            }
            BookingRole.Artist -> if (!r.deleted_by_requester) ArtistNextButton(r, today, act)
            BookingRole.Requester -> if (r.status != BookingStatus.FINISHED) {
                var confirmCancel by remember(r.id) { mutableStateOf(false) }
                if (r.deleted_by_requester) ActionButton(i18n.t(Strings.BK_RESTORE), Icons.Outlined.Restore, primary = true) { act.restore(r) }
                else ActionButton(i18n.t(Strings.BK_CANCEL), Icons.Outlined.Close, danger = true) { confirmCancel = true }
                // Опасная кнопка — через подтверждение (вернуть можно, но агентство уже увидит отмену)
                if (confirmCancel) ConfirmDialog("${i18n.t(Strings.BK_CANCEL)}?", i18n.t(Strings.BK_CANCEL), onConfirm = { act.cancel(r) }, onDismiss = { confirmCancel = false })
            }
        }
    }
}

/** «1 800 € → 1 530 € после налога». Нетто считает бэкенд (`*_fee_amount_after_tax`); нет его — только брутто. */
@Composable
private fun withTax(gross: String, net: Double?, currency: String?): String {
    val n = moneyLabel(net ?: return gross, currency) ?: return gross
    return "$gross → $n ${useI18n().t(Strings.BK_AFTER_TAX)}"
}

@Composable
private fun Kv(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(useI18n().t(key), color = DJMetryColors.Muted, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = DJMetryColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
}

private fun companyActionLabel(s: String): String = when (s) {
    BookingStatus.IN_PROGRESS -> Strings.BK_ACT_TAKE
    BookingStatus.ACCEPTED -> Strings.BK_ACT_ACCEPT
    BookingStatus.DECLINED -> Strings.BK_ACT_DECLINE
    BookingStatus.PAID -> Strings.BK_ACT_PAID
    else -> Strings.BK_ACT_REOPEN
}

private fun companyActionIcon(s: String): ImageVector = when (s) {
    BookingStatus.IN_PROGRESS -> Icons.Outlined.Autorenew
    BookingStatus.ACCEPTED -> Icons.Outlined.Check
    BookingStatus.DECLINED -> Icons.Outlined.Close
    BookingStatus.PAID -> Icons.Outlined.Payments
    else -> Icons.Outlined.Replay
}

@Composable
internal fun ActionButton(text: String, icon: ImageVector, primary: Boolean = false, danger: Boolean = false, orange: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = when { orange -> Orange; primary -> DJMetryColors.Accent; else -> DJMetryColors.PanelStrong }
    val fg = when { orange -> Color(0xFF1B1206); primary -> DJMetryColors.Background; danger -> DJMetryColors.LowScore; else -> DJMetryColors.Text }
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(15.dp)).background(bg)
            .then(if (!primary && !orange) Modifier.border(1.dp, if (danger) DJMetryColors.LowScore.copy(alpha = 0.4f) else DJMetryColors.Border, RoundedCornerShape(15.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Артист: «живое» шоу и следующий этап ───────────────────────────────────

private fun nextLabel(s: String): String = when (s) {
    BookingStatus.ON_THE_WAY -> Strings.BK_NEXT_WAY
    BookingStatus.AT_HOTEL -> Strings.BK_NEXT_HOTEL
    BookingStatus.AT_VENUE -> Strings.BK_NEXT_VENUE
    else -> Strings.BK_NEXT_DONE
}

/** Кнопка следующего путевого статуса; «В пути» — сначала выбор транспорта. «Выступил» — только в день шоу. */
@Composable
internal fun ArtistNextButton(r: BookingRequest, today: kotlinx.datetime.LocalDate, act: BookingActions) {
    val i18n = useI18n()
    val next = artistNextFor(r, today)
    var pickTransport by remember { mutableStateOf(false) }
    when {
        next != null -> Box {
            ActionButton(i18n.t(nextLabel(next)), stepIcon(next, r.travel_transport ?: "plane"), orange = true) {
                if (next == BookingStatus.ON_THE_WAY) pickTransport = true else act.artistStatus(r, next, null)
            }
            DropdownMenu(pickTransport, onDismissRequest = { pickTransport = false }, containerColor = DJMetryColors.PanelStrong) {
                Text(i18n.t(Strings.BK_TRANSPORT_Q), color = DJMetryColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TRAVEL_TRANSPORTS.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(i18n.t(transportLabel(t)), color = DJMetryColors.Text, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(transportIcon(t), null, tint = Orange) },
                        onClick = { pickTransport = false; act.artistStatus(r, BookingStatus.ON_THE_WAY, t) },
                    )
                }
            }
        }
        r.status == BookingStatus.AT_VENUE -> Text(i18n.t(Strings.BK_DONE_ON_DAY), color = DJMetryColors.Muted, fontSize = 12.5.sp)
    }
}

private fun stepIcon(status: String, transport: String): ImageVector = when (status) {
    BookingStatus.ON_THE_WAY -> transportIcon(transport)
    BookingStatus.AT_HOTEL -> Icons.Outlined.Hotel
    BookingStatus.AT_VENUE -> Icons.Outlined.Stadium
    else -> Icons.Outlined.Celebration
}

/** Карточка ближайшего шоу артиста: дата, площадка, агентство, 4 этапа поездки и кнопка следующего. */
@Composable
internal fun LiveShowCard(r: BookingRequest, today: kotlinx.datetime.LocalDate, act: BookingActions) {
    val i18n = useI18n()
    val isToday = bookingDay(r.event_date) == today.toString()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Orange.copy(alpha = 0.18f), DJMetryColors.Accent.copy(alpha = 0.10f))))
            .border(1.dp, Orange.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).clickable(role = Role.Button) { act.open(r) }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverImage(r.company_image_url, 46.dp, cornerRadius = 23.dp)
            Column(Modifier.weight(1f)) {
                val whenText = (if (isToday) i18n.t(Strings.BK_TODAY) else "${i18n.t(Strings.BK_NEXT_SHOW)} · ${eventDateLabel(r.event_date)}") +
                    (r.event_time?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                Text(whenText.uppercase(), color = Orange, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.event_location?.ifBlank { null } ?: "—", color = DJMetryColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(r.company?.name ?: r.company_name, r.number?.let { "№$it" }).joinToString(" · "), color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Этапы: в пути — отель — площадка — выступил
        val done = BookingStatus.ARTIST_PATH.indexOf(r.status)
        Row(verticalAlignment = Alignment.Top) {
            BookingStatus.ARTIST_PATH.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.weight(1f).padding(top = 18.dp).height(3.dp).clip(CircleShape).background(if (i <= done) DJMetryColors.Accent else DJMetryColors.Border))
                val state = when { i < done || (i == done && s == BookingStatus.FINISHED) -> 2; i == done -> 1; else -> 0 }
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(if (state == 2 || state == 1) DJMetryColors.Accent else DJMetryColors.Background)
                            .border(2.dp, if (state == 0) DJMetryColors.Border else DJMetryColors.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(stepIcon(s, r.travel_transport ?: "plane"), null, tint = if (state == 0) DJMetryColors.Muted else DJMetryColors.Background, modifier = Modifier.size(19.dp)) }
                    Text(i18n.t(stepLabel(s)), color = if (state == 0) DJMetryColors.Muted else DJMetryColors.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        ArtistNextButton(r, today, act)
    }
}

private fun stepLabel(s: String): String = when (s) {
    BookingStatus.ON_THE_WAY -> Strings.BK_STEP_WAY
    BookingStatus.AT_HOTEL -> Strings.BK_STEP_HOTEL
    BookingStatus.AT_VENUE -> Strings.BK_STEP_VENUE
    else -> Strings.BK_STEP_DONE
}
