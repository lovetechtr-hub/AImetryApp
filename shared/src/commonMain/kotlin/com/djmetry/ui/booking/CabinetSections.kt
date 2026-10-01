package com.djmetry.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.djmetry.LocalAppContainer
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.models.*
import com.djmetry.data.booking.*
import com.djmetry.files.platformFileSaver
import com.djmetry.files.platformPdfPicker
import com.djmetry.i18n.Strings
import com.djmetry.i18n.localizedCountryName
import com.djmetry.ui.analytics.CountriesMap
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CountryFlag
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.settings.*
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/** Раздел кабинета: заголовок (со стрелкой «назад» на телефоне) и содержимое. [say] — тост. */
@Composable
internal fun CabinetSectionPage(section: CabinetSection, s: CabinetState, onBack: (() -> Unit)?, say: (String) -> Unit) {
    val i18n = useI18n()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = it).padding(8.dp))
                Spacer(Modifier.width(4.dp))
            }
            AutoSizeText(i18n.t(sectionTitle(section)), TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 16.sp)
        }
        when (section) {
            CabinetSection.Artists -> ArtistsSection(s, say)
            CabinetSection.Team -> TeamSection(s, say)
            CabinetSection.Token -> TokenCard(s, say)
            CabinetSection.Taxes -> TaxesSection(s, say)
            CabinetSection.Map -> MapSection(s)
            CabinetSection.Companies -> CompaniesSection(s, say)
            CabinetSection.Files -> FilesSection(s, say)
            CabinetSection.ArtistTax -> ArtistTaxSection(s, say)
        }
    }
}

private val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.05f), CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
private fun Loading(rows: Int = 3) {
    repeat(rows) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), CardShape) }
}

@Composable
internal fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, primary: Boolean = true, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (primary) DJMetryColors.Accent else DJMetryColors.PanelStrong
    val fg = if (primary) DJMetryColors.Background else DJMetryColors.Text
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (!primary) Modifier.border(1.dp, DJMetryColors.Border, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Подтверждение опасного действия (отвязать, удалить, выйти). */
@Composable
internal fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val i18n = useI18n()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DJMetryColors.PanelStrong,
        text = { Text(text, color = DJMetryColors.Text, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = DJMetryColors.LowScore, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t(Strings.SET_CANCEL), color = DJMetryColors.Muted) } },
    )
}

// ── Артисты агентства ──

@Composable
private fun ArtistsSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    var unlink by remember { mutableStateOf<BookingCompanyArtist?>(null) }
    if (s.isOwner) TokenCard(s, say)
    val artists = s.detail?.artists
    when {
        artists == null -> Loading()
        artists.isEmpty() -> Hint(i18n.t(Strings.BC_NO_ARTISTS))
        else -> {
            // Треки из ответа агентства (`top_tracks`) — без отдельного запроса на каждого артиста
            val byArtist = s.detail?.top_tracks.orEmpty().groupBy { it.spotify_artist_id }
            artists.sortedBy { it.approved }.forEach { a ->
                AgencyArtistCard(a, s.isOwner, byArtist[a.spotify_artist_id]?.map { it.asTrack() }?.take(3)) { unlink = a }
            }
        }
    }
    unlink?.let { a ->
        ConfirmDialog(i18n.tWithArgs(Strings.BC_UNLINK_Q, arrayOf(a.name ?: "")), i18n.t(Strings.BC_UNLINK), onConfirm = {
            scope.launch {
                val id = s.companyId ?: return@launch
                repo.unlinkArtist(id, a.spotify_artist_id).onSuccess { s.reloadDetail(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) }
            }
        }, onDismiss = { unlink = null })
    }
}

@Composable
private fun AgencyArtistCard(a: BookingCompanyArtist, owner: Boolean, preset: List<Track>? = null, onUnlink: () -> Unit) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    val uri = LocalUriHandler.current
    val tracks by produceState(preset ?: container.artists.cachedTopTracks(a.spotify_artist_id), a.spotify_artist_id) {
        // Нет в ответе агентства (артист не из первых пяти) — свой запрос с кэшем
        if (preset.isNullOrEmpty()) value = container.artists.topTracks(a.spotify_artist_id).getOrNull().orEmpty()
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { openArtist(a.spotify_artist_id) }, verticalAlignment = Alignment.CenterVertically) {
                CoverImage(com.djmetry.ui.components.rememberArtistPhoto(a.spotify_artist_id, a.image_url), 46.dp, cornerRadius = 23.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name ?: a.spotify_artist_id, color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (!a.approved) i18n.t(Strings.BC_PENDING) else i18n.tWithArgs(Strings.BC_TAX_N, arrayOf(percentLabel(a.default_artist_tax_percent))),
                        color = if (!a.approved) Color(0xFFFFB35B) else DJMetryColors.Muted, fontSize = 12.5.sp,
                    )
                }
            }
            if (owner) Icon(Icons.Outlined.LinkOff, i18n.t(Strings.BC_UNLINK), tint = DJMetryColors.Muted,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onUnlink).padding(9.dp))
        }
        val t = tracks
        if (t == null) repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(30.dp), RoundedCornerShape(8.dp)) }
        else t.forEach { tr ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                    uri.openUri(tr.externalUrl ?: "https://open.spotify.com/track/${tr.spotifyTrackId}")
                }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(tr.albumImageUrl, 32.dp, cornerRadius = 8.dp)
                Spacer(Modifier.width(10.dp))
                Text(tr.name, color = DJMetryColors.Text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.PlayCircle, null, tint = DJMetryColors.Accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ── Токен «Добавить артиста» ──

@Composable
private fun TokenCard(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var token by remember(s.companyId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Key, null, tint = DJMetryColors.Accent)
            }
            Spacer(Modifier.width(10.dp))
            Text(i18n.t(Strings.BC_TOKEN_TITLE), color = DJMetryColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(i18n.t(Strings.BC_TOKEN_HINT), color = DJMetryColors.Muted, fontSize = 12.5.sp)
        if (!s.isOwner) { Text(i18n.t(Strings.BC_OWNER_ONLY), color = DJMetryColors.Muted, fontSize = 13.sp); return@Card }
        token?.let { t ->
            Text(
                t, color = DJMetryColors.Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Background).border(1.dp, DJMetryColors.Border, RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            token?.let { t -> PillButton(i18n.t(Strings.BC_COPY), Icons.Outlined.ContentCopy, modifier = Modifier.weight(1f)) { clipboard.setText(AnnotatedString(t)); say(i18n.t(Strings.BC_COPIED)) } }
            PillButton(i18n.t(if (token == null) Strings.BC_TOKEN_MAKE else Strings.BC_TOKEN_NEW), Icons.Outlined.Key, primary = token == null, enabled = !busy, modifier = Modifier.weight(1f)) {
                val id = s.companyId ?: return@PillButton
                if (busy) return@PillButton
                busy = true
                scope.launch { try { repo.artistToken(id).onSuccess { token = it }.onFailure { say(i18n.t(actionErrorKey(it))) } } finally { busy = false } }
            }
        }
    }
}

// ── Команда ──

@Composable
private fun TeamSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    val flight = com.djmetry.ui.components.rememberSingleFlight()
    var edit by remember { mutableStateOf<BookingMember?>(null) }
    var leave by remember { mutableStateOf(false) }
    var revoke by remember { mutableStateOf<BookingMember?>(null) }
    var email by remember { mutableStateOf("") }
    val members = s.members
    if (members == null) Loading() else SettingsGroup(null) {
        members.forEachIndexed { i, m ->
            val name = m.user_display_name ?: m.user_email ?: m.invited_email ?: "—"
            val sub = when {
                m.invite_status == "pending" -> i18n.t(Strings.BC_INVITED)
                m.role == "owner" -> i18n.t(Strings.BC_OWNER)
                else -> "${i18n.t(Strings.BC_MANAGER)} · " + (if (m.accept_all_requests) i18n.t(Strings.BC_ALL_REGIONS) else m.responsible_regions.joinToString(", ") { iso -> localizedCountryName(iso, i18n.locale.code) ?: iso }.ifEmpty { "—" })
            }
            val editable = s.isOwner && m.role == "manager" && m.user_id != null
            // Непринятое приглашение владелец может отозвать (по id строки участника)
            val revocable = s.isOwner && m.invite_status == "pending" && m.id != null
            SettingsRow(name, sub, if (m.role == "owner") Icons.Outlined.Badge else Icons.Outlined.Person, if (m.invite_status == "pending") RowTone.Orange else RowTone.Green,
                when { editable -> RowEnd.Chevron; revocable -> RowEnd.IconEnd(Icons.Outlined.Close, DJMetryColors.Muted); else -> RowEnd.None },
                divider = i < members.lastIndex, onClick = when { editable -> ({ edit = m }); revocable -> ({ revoke = m }); else -> null })
        }
    }
    if (s.isOwner) {
        SettingsField(email, { email = it }, i18n.t(Strings.BC_INVITE_EMAIL))
        PillButton(i18n.t(Strings.BC_INVITE), Icons.Outlined.PersonAdd, enabled = email.contains('@') && !flight.busy, modifier = Modifier.fillMaxWidth()) {
            val id = s.companyId ?: return@PillButton
            flight.run(scope) {
                repo.invite(id, email).onSuccess { email = ""; say(i18n.t(Strings.BC_INVITED)); s.reloadMembers(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) }
            }
        }
    } else PillButton(i18n.t(Strings.BC_LEAVE), Icons.Outlined.Logout, primary = false, modifier = Modifier.fillMaxWidth()) { leave = true }
    if (leave) ConfirmDialog(i18n.t(Strings.BC_LEAVE) + "?", i18n.t(Strings.BC_LEAVE), onConfirm = {
        scope.launch { s.companyId?.let { repo.leaveCompany(it).onSuccess { say(i18n.t(Strings.BC_SAVED)); s.left = true }.onFailure { e -> say(i18n.t(actionErrorKey(e))) } } }
    }, onDismiss = { leave = false })
    edit?.let { m -> MemberDialog(s, m, say) { edit = null } }
    revoke?.let { m ->
        ConfirmDialog(i18n.tWithArgs(Strings.BC_REVOKE_Q, arrayOf(m.invited_email ?: "")), i18n.t(Strings.BC_REVOKE), onConfirm = {
            scope.launch {
                val cid = s.companyId ?: return@launch
                repo.revokeInvite(cid, m.id ?: return@launch).onSuccess { s.reloadMembers(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) }
            }
        }, onDismiss = { revoke = null })
    }
}

@Composable
private fun MemberDialog(s: CabinetState, m: BookingMember, say: (String) -> Unit, onClose: () -> Unit) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val repo = container.booking
    val scope = rememberCoroutineScope()
    val flight = com.djmetry.ui.components.rememberSingleFlight()
    var all by remember { mutableStateOf(m.accept_all_requests) }
    var regions by remember { mutableStateOf(m.responsible_regions) }
    var picking by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val countries by produceState(emptyList<Country>()) { value = container.settings.countries().getOrNull().orEmpty() }
    val nameOf: (String) -> String = { iso -> countries.firstOrNull { it.code.equals(iso, true) }?.name ?: localizedCountryName(iso, i18n.locale.code) ?: iso }
    Dialog(onDismissRequest = onClose) { com.djmetry.ui.components.DismissKeyboardOnTap {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(m.user_display_name ?: m.user_email ?: "", color = DJMetryColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            SettingsGroup(null) { SettingsRow(i18n.t(Strings.BC_ALL_REQUESTS), null, Icons.Outlined.Inbox, end = RowEnd.Toggle(all) { all = it }, divider = false) }
            if (!all) {
                Text(i18n.t(Strings.BC_REGIONS), color = DJMetryColors.Muted, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    regions.forEach { iso ->
                        Row(
                            Modifier.clip(CircleShape).background(DJMetryColors.Accent.copy(alpha = 0.14f)).clickable { regions = regions - iso }.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CountryFlag(iso, 16.dp)
                            Text(nameOf(iso), color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Outlined.Close, null, tint = DJMetryColors.Accent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(
                        Modifier.clip(CircleShape).border(1.dp, DJMetryColors.Border, CircleShape).clickable { picking = true }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = DJMetryColors.Text, modifier = Modifier.size(16.dp))
                        Text(i18n.t(Strings.BC_ADD_REGION), color = DJMetryColors.Text, fontSize = 13.sp)
                    }
                }
            }
            PillButton(i18n.t(Strings.SET_SAVE), null, enabled = !flight.busy, modifier = Modifier.fillMaxWidth()) {
                val id = s.companyId ?: return@PillButton
                val uid = m.user_id ?: return@PillButton
                flight.run(scope) {
                    repo.updateMember(id, uid, all, if (all) emptyList() else regions)
                        .onSuccess { say(i18n.t(Strings.BC_SAVED)); s.reloadMembers(repo); onClose() }.onFailure { say(i18n.t(actionErrorKey(it))) }
                }
            }
            PillButton(i18n.t(Strings.BC_REMOVE_MEMBER), Icons.Outlined.PersonRemove, primary = false, modifier = Modifier.fillMaxWidth()) { remove = true }
        }
    } }
    if (picking) SearchPickerDialog(
        title = i18n.t(Strings.BC_REGIONS), items = countries.filter { c -> regions.none { it.equals(c.code, true) } }, label = { it.name }, flagIso = { it.code },
        onPick = { regions = regions + it.code.uppercase(); picking = false }, onDismiss = { picking = false },
    )
    if (remove) ConfirmDialog(i18n.t(Strings.BC_REMOVE_MEMBER) + "?", i18n.t(Strings.BC_REMOVE_MEMBER), onConfirm = {
        scope.launch {
            val id = s.companyId ?: return@launch
            repo.removeMember(id, m.user_id ?: return@launch).onSuccess { s.reloadMembers(repo); onClose() }.onFailure { say(i18n.t(actionErrorKey(it))) }
        }
    }, onDismiss = { remove = false })
}

// ── Налоги ──

/** Ставка 0…99 кнопками −/+ (шаг 1). */
@Composable
internal fun TaxStepper(value: Double, enabled: Boolean, onChange: (Double) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(DJMetryColors.Background).border(1.dp, DJMetryColors.Border, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Remove, null, tint = if (enabled) DJMetryColors.Text else DJMetryColors.Muted,
            modifier = Modifier.size(38.dp).clickable(enabled = enabled, role = Role.Button) { onChange(clampTax(kotlin.math.ceil(value) - 1)) }.padding(10.dp))
        Text(percentLabel(value), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
        Icon(Icons.Outlined.Add, null, tint = if (enabled) DJMetryColors.Text else DJMetryColors.Muted,
            modifier = Modifier.size(38.dp).clickable(enabled = enabled, role = Role.Button) { onChange(clampTax(kotlin.math.floor(value) + 1)) }.padding(10.dp))
    }
}

@Composable
private fun TaxesSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    val flight = com.djmetry.ui.components.rememberSingleFlight()
    val d = s.detail ?: run { Loading(2); return }
    var companyTax by remember(d) { mutableStateOf(d.company.default_company_tax_percent ?: 0.0) }
    var own by remember(d) { mutableStateOf(d.company.default_artist_calculates_own_tax == true) }
    val approved = d.artists.filter { it.approved }
    var rates by remember(d) { mutableStateOf(approved.associate { it.spotify_artist_id to (it.default_artist_tax_percent ?: 0.0) }) }
    Hint(i18n.t(Strings.BC_TAX_HINT))
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(i18n.t(Strings.BC_COMPANY_TAX), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TaxStepper(companyTax, s.isOwner) { companyTax = it }
        }
    }
    SettingsGroup(null) { SettingsRow(i18n.t(Strings.BC_OWN_TAX), null, Icons.Outlined.Person, end = RowEnd.Toggle(own) { own = it }, divider = false, enabled = s.isOwner) }
    if (approved.isNotEmpty()) {
        Text(i18n.t(Strings.BC_ARTIST_RATES), color = DJMetryColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Card {
            approved.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(com.djmetry.ui.components.rememberArtistPhoto(a.spotify_artist_id, a.image_url), 34.dp, cornerRadius = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(a.name ?: "", color = DJMetryColors.Text, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TaxStepper(rates[a.spotify_artist_id] ?: 0.0, s.isOwner) { v -> rates = rates + (a.spotify_artist_id to v) }
                }
            }
        }
    }
    if (s.isOwner) PillButton(i18n.t(Strings.SET_SAVE), null, enabled = !flight.busy, modifier = Modifier.fillMaxWidth()) {
        val id = s.companyId ?: return@PillButton
        flight.run(scope) {
            repo.saveTaxes(id, companyTax, own, rates).onSuccess { say(i18n.t(Strings.BC_SAVED)); s.reloadDetail(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) }
        }
    } else Hint(i18n.t(Strings.BC_OWNER_ONLY))
}

// ── Карта выступлений ──

@Composable
private fun MapSection(s: CabinetState) {
    val i18n = useI18n()
    val list = s.performances ?: run { Loading(2); return }
    if (list.isEmpty()) { Hint(i18n.t(Strings.BC_NO_SHOWS)); return }
    val countries = remember(list) { performanceCountries(list) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(260.dp).clip(CardShape).background(Color(0xFF070D18))) {
        CountriesMap(countries, interactive = true, widthDp = maxWidth.value, modifier = Modifier.fillMaxSize())
    }
    Text(i18n.tWithArgs(Strings.BC_SHOWS_TOTAL, arrayOf(list.size.toString(), countries.size.toString())), color = DJMetryColors.Muted, fontSize = 13.sp)
    SettingsGroup(null) {
        list.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                p.event_country?.takeIf { it.length == 2 }?.let { CountryFlag(it, 22.dp); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(p.event_location ?: p.event_country ?: "—", color = DJMetryColors.Text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(eventDateLabel(p.event_date).takeIf { it.isNotEmpty() }, p.artist_names ?: p.artists.mapNotNull { it.name }.joinToString(", ").ifEmpty { null }).joinToString(" · "),
                        color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                moneyLabel(p.payment_amount, p.payment_currency)?.let { Text(it, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            if (i < list.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(DJMetryColors.Border))
        }
    }
}

// ── Артист: агентства ──

@Composable
private fun CompaniesSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    val flight = com.djmetry.ui.components.rememberSingleFlight()
    var unlink by remember { mutableStateOf<ArtistCompanyLink?>(null) }
    var token by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    val list = s.companies
    when {
        list == null -> Loading(2)
        list.isEmpty() -> Hint(i18n.t(Strings.BC_NO_COMPANIES))
        else -> list.forEach { l ->
            val c = l.company
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(c?.image_url, 48.dp, cornerRadius = 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c?.name ?: "—", color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = if (!l.approved) i18n.t(Strings.BC_PENDING)
                        else listOfNotNull(c?.city, c?.country?.let { iso -> localizedCountryName(iso, i18n.locale.code) ?: iso }).joinToString(", ")
                        if (sub.isNotEmpty()) Text(sub, color = if (!l.approved) Color(0xFFFFB35B) else DJMetryColors.Muted, fontSize = 12.5.sp)
                    }
                    Icon(Icons.Outlined.LinkOff, i18n.t(Strings.BC_UNLINK), tint = DJMetryColors.Muted,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { unlink = l }.padding(9.dp))
                }
            }
        }
    }
    // Токен от агентства: проверить → «Присоединиться к …»
    Card {
        Text(i18n.t(Strings.BC_PASTE_TOKEN), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        SettingsField(token, { token = it.trim(); preview = null }, i18n.t(Strings.BC_S_TOKEN))
        val p = preview
        if (p == null) PillButton(i18n.t(Strings.BC_CHECK), Icons.Outlined.Key, primary = false, enabled = token.length >= 8 && !flight.busy, modifier = Modifier.fillMaxWidth()) {
            flight.run(scope) { repo.previewToken(token).onSuccess { preview = it.name ?: "" }.onFailure { say(i18n.t(actionErrorKey(it))) } }
        } else PillButton(i18n.tWithArgs(Strings.BC_JOIN, arrayOf(p)), Icons.Outlined.Apartment, enabled = !flight.busy, modifier = Modifier.fillMaxWidth()) {
            flight.run(scope) {
                repo.confirmToken(token).onSuccess { say(i18n.tWithArgs(Strings.BC_JOINED, arrayOf(it.name ?: p))); token = ""; preview = null; s.reloadCompanies(repo) }
                    .onFailure { say(i18n.t(actionErrorKey(it))) }
            }
        }
    }
    unlink?.let { l ->
        ConfirmDialog(i18n.tWithArgs(Strings.BC_UNLINK_COMPANY_Q, arrayOf(l.company?.name ?: "")), i18n.t(Strings.BC_UNLINK), onConfirm = {
            scope.launch {
                val id = s.artistId ?: return@launch
                repo.unlinkCompany(id, l.company_id).onSuccess { s.reloadCompanies(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) }
            }
        }, onDismiss = { unlink = null })
    }
}

// ── Артист: райдер и пресс-кит ──

@Composable
private fun FilesSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    Hint(i18n.t(Strings.BC_PDF_HINT))
    DocCard(s, BookingDoc.Rider, i18n.t(Strings.BC_RIDER), s.rider, { s.rider = it }, say)
    DocCard(s, BookingDoc.PressKit, i18n.t(Strings.BC_PRESS), s.pressKit, { s.pressKit = it }, say)
}

@Composable
private fun DocCard(s: CabinetState, doc: BookingDoc, title: String, has: Boolean?, set: (Boolean) -> Unit, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    val picker = remember { platformPdfPicker() }
    val saver = remember { platformFileSaver() }
    var busy by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val id = s.artistId ?: return
    val uri = LocalUriHandler.current
    val info = s.files?.let { if (doc == BookingDoc.Rider) it.rider else it.press_kit }
    val upload: () -> Unit = {
        scope.launch {
            busy = true
            picker.pick()?.let { f -> repo.uploadDoc(id, doc, f).onSuccess { set(true); s.reloadFiles(repo); say(i18n.t(Strings.BC_SAVED)) }.onFailure { say(i18n.t(actionErrorKey(it))) } }
            busy = false
        }
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp, 52.dp).clip(RoundedCornerShape(9.dp)).background(if (has == true) DJMetryColors.LowScore.copy(alpha = 0.15f) else DJMetryColors.Background),
                contentAlignment = Alignment.Center,
            ) { Icon(if (has == true) Icons.Outlined.PictureAsPdf else Icons.Outlined.UploadFile, null, tint = if (has == true) DJMetryColors.LowScore else DJMetryColors.Muted) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                if (has == null) SkeletonBox(Modifier.width(80.dp).height(12.dp), RoundedCornerShape(4.dp))
                // Имя файла, размер и дата — если бэкенд их отдаёт
                else Text(
                    info?.let { fileCaption(it) } ?: i18n.t(if (has) Strings.BC_UPLOADED else Strings.BC_NOT_UPLOADED),
                    color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (has == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(i18n.t(Strings.BC_DOWNLOAD), Icons.Outlined.Download, primary = false, enabled = !busy, modifier = Modifier.weight(1f)) {
                // Подписанная ссылка (час, без входа) — системный просмотрщик; старый бэкенд — скачать и сохранить
                val signed = info?.signed_url
                if (signed != null) runCatching { uri.openUri(if (signed.startsWith("http")) signed else com.djmetry.config.AppConfig.BASE_URL + signed) }
                else scope.launch {
                    busy = true
                    repo.downloadDoc(id, doc).onSuccess { bytes -> saver.save(info?.filename ?: "${doc.path}.pdf", "application/pdf", bytes) }.onFailure { say(i18n.t(actionErrorKey(it))) }
                    busy = false
                }
            }
            PillButton(i18n.t(Strings.BC_REPLACE), Icons.Outlined.UploadFile, primary = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = upload)
            Icon(Icons.Outlined.Delete, i18n.t(Strings.BC_DELETE), tint = DJMetryColors.LowScore,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, DJMetryColors.LowScore.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { delete = true }.padding(12.dp))
        } else if (has == false) PillButton(i18n.t(Strings.BC_UPLOAD), Icons.Outlined.UploadFile, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = upload)
    }
    if (delete) ConfirmDialog("${i18n.t(Strings.BC_DELETE)}: $title?", i18n.t(Strings.BC_DELETE), onConfirm = {
        scope.launch { repo.deleteDoc(id, doc).onSuccess { set(false); s.reloadFiles(repo) }.onFailure { say(i18n.t(actionErrorKey(it))) } }
    }, onDismiss = { delete = false })
}

// ── Артист: налог по умолчанию ──

@Composable
private fun ArtistTaxSection(s: CabinetState, say: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.booking
    val scope = rememberCoroutineScope()
    var v by remember(s.artistTax) { mutableStateOf(s.artistTax ?: 0.0) }
    Hint(i18n.t(Strings.BC_ARTIST_TAX_HINT))
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(i18n.t(Strings.BC_S_ARTIST_TAX), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TaxStepper(v, true) { v = it }
        }
    }
    PillButton(i18n.t(Strings.SET_SAVE), null, modifier = Modifier.fillMaxWidth()) {
        val id = s.artistId ?: return@PillButton
        scope.launch { repo.setArtistTax(id, v).onSuccess { s.artistTax = it ?: v; say(i18n.t(Strings.BC_SAVED)) }.onFailure { say(i18n.t(actionErrorKey(it))) } }
    }
}


/** «rider-artist.pdf · 805 КБ · 20 сен» — имя, размер и дата обновления файла. */
@Composable
private fun fileCaption(f: BookingFile): String {
    val size = f.size?.let { b -> if (b >= 1024 * 1024) "${(b * 10 / (1024 * 1024)) / 10.0} MB" else "${b / 1024} KB" }
    return listOfNotNull(f.filename, size, f.updated_at?.let { eventDateLabel(it).takeIf(String::isNotEmpty) }).joinToString(" · ")
}
