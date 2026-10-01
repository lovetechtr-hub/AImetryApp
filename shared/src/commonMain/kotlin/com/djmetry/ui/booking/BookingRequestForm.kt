package com.djmetry.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingCompanyPage
import com.djmetry.api.models.Country
import com.djmetry.data.booking.*
import com.djmetry.i18n.Strings
import com.djmetry.i18n.localizedCountryName
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CountryFlag
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.settings.SearchPickerDialog
import com.djmetry.ui.settings.isoToMillis
import com.djmetry.ui.settings.millisToIso
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** От этой ширины форма — окном по центру, уже — на весь экран. */
internal const val REQUEST_DIALOG_MIN_DP = 700f

/** Быстрые варианты типа события — заполняют поле, текст можно поправить (на сайте тип — свободная строка). */
internal val EVENT_TYPE_KEYS = listOf(Strings.BR_T_CLUB, Strings.BR_T_FEST, Strings.BR_T_PRIVATE, Strings.BR_T_CORP, Strings.BR_T_WEDDING)

/**
 * «Оставить заявку», вариант B (design/booking/cabinet-variants.html): одна страница — агентство, артисты кружками,
 * тип события чипами, дата, гости, страна и город, сообщение. Поля и лимиты — как в форме сайта; ошибки подсвечиваются.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BookingRequestScreen(artistId: String, onClose: () -> Unit, onSent: (String) -> Unit) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val repo = container.booking
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var agencies by remember { mutableStateOf<List<BookingCompany>?>(null) }
    var page by remember { mutableStateOf<BookingCompanyPage?>(null) }
    var form by remember { mutableStateOf(RequestForm(artistIds = setOf(artistId))) }
    var showErrors by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCountry by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val countries by produceState(emptyList<Country>()) { value = container.settings.countries().getOrNull().orEmpty() }

    LaunchedEffect(artistId) {
        val list = repo.artistAgencies(artistId).getOrNull().orEmpty()
        agencies = list
        list.firstOrNull()?.let { form = form.copy(companyId = it.id) }
    }
    LaunchedEffect(form.companyId) {
        val id = form.companyId ?: return@LaunchedEffect
        // Ростер агентства теперь приходит в списке — без второго запроса; старый бэкенд — страница агентства
        val a = agencies?.firstOrNull { it.id == id }
        if (a != null && a.artists.isNotEmpty()) { page = BookingCompanyPage(a.id, a.name, a.slug, a.image_url, a.city, a.country, artists = a.artists); return@LaunchedEffect }
        page = null
        page = repo.companyPage(id).getOrNull()
    }
    androidx.compose.ui.backhandler.BackHandler(onBack = onClose)

    val errors = requestFormErrors(form, today).takeIf { showErrors }.orEmpty()
    val countryName = countries.firstOrNull { it.code.equals(form.country, true) }?.name ?: localizedCountryName(form.country, i18n.locale.code) ?: form.country
    val send: () -> Unit = {
        showErrors = true
        if (requestFormErrors(form, today).isEmpty()) scope.launch {
            sending = true; error = null
            repo.createRequest(form, countryName).onSuccess { onSent(i18n.t(Strings.BR_SENT)); onClose() }.onFailure { error = i18n.t(actionErrorKey(it)) }
            sending = false
        } else error = i18n.t(Strings.BR_FIX)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dialog = maxWidth.value >= REQUEST_DIALOG_MIN_DP
        if (dialog) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(MutableInteractionSource(), null, onClick = onClose))
        Column(
            (if (dialog) Modifier.align(Alignment.Center).width(560.dp).heightIn(max = maxHeight * 0.92f).clip(RoundedCornerShape(26.dp))
                .border(1.dp, DJMetryColors.Border, RoundedCornerShape(26.dp))
            else Modifier.fillMaxSize()).background(DJMetryColors.Background).clickable(MutableInteractionSource(), null) { }
                .then(if (dialog) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (dialog) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).padding(10.dp))
                AutoSizeText(i18n.t(Strings.BR_TITLE), TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 15.sp)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val list = agencies
                when {
                    list == null -> repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(16.dp)) }
                    list.isEmpty() -> Text(i18n.t(Strings.BR_NO_AGENCY), color = DJMetryColors.Muted, fontSize = 14.sp)
                    else -> {
                        AgencyPicker(list, form.companyId) { form = form.copy(companyId = it, artistIds = setOf(artistId)) }
                        Label(i18n.t(Strings.BR_ARTISTS), RequestField.Artists in errors)
                        ArtistCircles(page, form.artistIds) { id -> form = form.copy(artistIds = if (id in form.artistIds) form.artistIds - id else form.artistIds + id) }

                        Label(i18n.t(Strings.BR_TYPE), RequestField.EventType in errors)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EVENT_TYPE_KEYS.forEach { k ->
                                val label = i18n.t(k)
                                val on = form.eventType == label
                                Text(
                                    label, color = if (on) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Panel)
                                        .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape)
                                        .clickable(role = Role.RadioButton) { form = form.copy(eventType = label) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                        FormField(form.eventType, { form = form.copy(eventType = it.take(REQUEST_TYPE_MAX)) }, i18n.t(Strings.BR_TYPE), RequestField.EventType in errors)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldButton(i18n.t(Strings.BR_DATE), form.date.takeIf { it.isNotEmpty() }?.let { eventDateLabel(it) + " " + it.take(4) }, Icons.Outlined.Event,
                                RequestField.Date in errors, Modifier.weight(1f)) { pickDate = true }
                            FormField(form.guests, { v -> form = form.copy(guests = v.filter(Char::isDigit).take(8)) }, i18n.t(Strings.BR_GUESTS),
                                RequestField.Guests in errors, Modifier.weight(1f), keyboard = KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldButton(i18n.t(Strings.SET_COUNTRY), form.country.takeIf { it.isNotEmpty() }?.let { countryName }, null,
                                RequestField.Place in errors && form.country.isBlank(), Modifier.weight(1f), flag = form.country.takeIf { it.length == 2 }) { pickCountry = true }
                            FormField(form.city, { form = form.copy(city = it.take(200)) }, i18n.t(Strings.SET_CITY), RequestField.Place in errors && form.city.isBlank(), Modifier.weight(1f))
                        }
                        FormField(form.message, { form = form.copy(message = it.take(REQUEST_MESSAGE_MAX)) }, i18n.t(Strings.BR_MESSAGE), RequestField.Message in errors,
                            singleLine = false, placeholder = i18n.t(Strings.BR_MESSAGE_HINT), counter = "${form.message.length}/$REQUEST_MESSAGE_MAX")
                    }
                }
            }
            if (!agencies.isNullOrEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = DJMetryColors.LowScore, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                PillButton(i18n.t(Strings.BR_SEND), Icons.AutoMirrored.Outlined.Send, enabled = !sending, modifier = Modifier.fillMaxWidth().height(52.dp), onClick = send)
            }
        }
    }

    if (pickCountry) SearchPickerDialog(
        title = i18n.t(Strings.SET_COUNTRY), items = countries, label = { it.name }, flagIso = { it.code },
        onPick = { form = form.copy(country = it.code.uppercase()); pickCountry = false }, onDismiss = { pickCountry = false },
    )
    if (pickDate) {
        val todayMillis = remember { isoToMillis(today.toString()) ?: 0L }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = isoToMillis(form.date),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { form = form.copy(date = millisToIso(it)) }; pickDate = false }) { Text(i18n.t(Strings.SET_SAVE), color = DJMetryColors.Accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Label(text: String, error: Boolean) {
    Text(text, color = if (error) DJMetryColors.LowScore else DJMetryColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AgencyPicker(list: List<BookingCompany>, selected: String?, onPick: (String) -> Unit) {
    val i18n = useI18n()
    var open by remember { mutableStateOf(false) }
    val c = list.firstOrNull { it.id == selected } ?: list.first()
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                .clickable(enabled = list.size > 1, role = Role.Button) { open = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(c.image_url, 44.dp, cornerRadius = 13.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = DJMetryColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(i18n.t(Strings.BR_AGENCY), c.city).joinToString(" · "), color = DJMetryColors.Muted, fontSize = 12.5.sp)
            }
            if (list.size > 1) Icon(Icons.Outlined.ExpandMore, null, tint = DJMetryColors.Muted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            list.forEach { a -> DropdownMenuItem(text = { Text(a.name, color = if (a.id == c.id) DJMetryColors.Accent else DJMetryColors.Text) }, onClick = { open = false; onPick(a.id) }) }
        }
    }
}

/** Артисты агентства кружками: выбранные — с зелёной обводкой и галочкой. */
@Composable
private fun ArtistCircles(page: BookingCompanyPage?, selected: Set<String>, onToggle: (String) -> Unit) {
    if (page == null) { SkeletonBox(Modifier.fillMaxWidth().height(90.dp), RoundedCornerShape(16.dp)); return }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        page.artists.forEach { a ->
            val on = a.spotify_artist_id in selected
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Checkbox) { onToggle(a.spotify_artist_id) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    CoverImage(a.image_url, 62.dp, cornerRadius = 31.dp,
                        modifier = Modifier.alpha(if (on) 1f else 0.55f).border(3.dp, if (on) DJMetryColors.Accent else Color.Transparent, CircleShape))
                    if (on) Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(DJMetryColors.Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = DJMetryColors.Background, modifier = Modifier.size(15.dp))
                    }
                }
                Text(a.name ?: "", color = DJMetryColors.Text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
private fun FormField(
    value: String, onChange: (String) -> Unit, label: String, error: Boolean, modifier: Modifier = Modifier,
    singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, placeholder: String? = null, counter: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine, isError = error, minLines = if (singleLine) 1 else 3,
        label = { Text(label) }, placeholder = placeholder?.let { { Text(it, color = DJMetryColors.Muted) } },
        supportingText = counter?.let { { Text(it, color = DJMetryColors.Muted) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DJMetryColors.Text, unfocusedTextColor = DJMetryColors.Text,
            focusedContainerColor = DJMetryColors.Panel, unfocusedContainerColor = DJMetryColors.Panel,
            focusedBorderColor = DJMetryColors.Accent, unfocusedBorderColor = DJMetryColors.Border,
            focusedLabelColor = DJMetryColors.Accent, unfocusedLabelColor = DJMetryColors.Muted, cursorColor = DJMetryColors.Accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Поле-кнопка (дата, страна): подпись сверху, значение с иконкой или флагом. */
@Composable
private fun FieldButton(label: String, value: String?, icon: ImageVector?, error: Boolean, modifier: Modifier = Modifier, flag: String? = null, onClick: () -> Unit) {
    Column(
        modifier.padding(top = 8.dp).height(56.dp).clip(FieldShape).background(DJMetryColors.Panel)
            .border(1.dp, if (error) DJMetryColors.LowScore else DJMetryColors.Border, FieldShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (error) DJMetryColors.LowScore else DJMetryColors.Muted, fontSize = 11.5.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                flag != null -> { CountryFlag(flag, 18.dp); Spacer(Modifier.width(6.dp)) }
                icon != null -> { Icon(icon, null, tint = DJMetryColors.Accent, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)) }
            }
            Text(value ?: "—", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
