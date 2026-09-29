package com.djmetry.ui.editor

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.endpoints.SocialField
import com.djmetry.api.models.MeResponse
import com.djmetry.data.repository.*
import com.djmetry.files.platformPdfPicker
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.LoadingCrossfade
import com.djmetry.ui.components.SkeletonListRow
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.settings.*
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Открыть редактор артиста из профиля или настроек (ставит MainShell). */
val LocalOpenArtistEditor = staticCompositionLocalOf<() -> Unit> { {} }

/** Разделы редактора. «Тема карточки» на мобильном не делаем (спека §14). */
enum class EditorSection(val titleKey: String) {
    Socials(Strings.ED_SOCIALS), Genres(Strings.ED_GENRES), Location(Strings.ED_LOCATION), Tracks(Strings.ED_TRACKS), Docs(Strings.ED_DOCS),
}

/** Текст ошибки редактора по коду: ввод (до запроса) или ответ бэкенда. */
fun editorErrorKey(code: String): String = when (code) {
    "track_is_album" -> Strings.ED_ERR_ALBUM
    "track_is_artist" -> Strings.ED_ERR_ARTIST
    "track_is_playlist" -> Strings.ED_ERR_PLAYLIST
    "invalid_track_url" -> Strings.ED_ERR_INVALID
    "track_limit" -> Strings.ED_ERR_LIMIT
    "track_duplicate" -> Strings.ED_ERR_DUP
    "track_not_yours" -> Strings.ED_ERR_NOT_YOURS
    "track_not_found" -> Strings.ED_ERR_NOT_FOUND
    "spotify_rate_limited" -> Strings.ED_ERR_RATE
    "pdf_notpdf" -> Strings.ED_ERR_NOT_PDF
    "pdf_toobig" -> Strings.ED_ERR_TOO_BIG
    "pdf_empty" -> Strings.ED_ERR_EMPTY
    "country_required" -> Strings.ED_COUNTRY_REQUIRED
    "invalid_social" -> Strings.ED_SOCIAL_INVALID
    "artist_verification_required" -> Strings.ED_NOT_ARTIST
    else -> Strings.SET_ERROR_SAVE
}

private class Feedback { var message by mutableStateOf<String?>(null) }

/**
 * Редактор проверенного артиста (спека §14): соцсети, жанры, локация, треки, райдер и пресс-кит. Всё строго —
 * неверный ввод отсекается до запроса. Телефон — список → раздел; планшет — список слева; десктоп — меню разделов.
 */
@Composable
fun ArtistEditorScreen(me: MeResponse?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val i18n = useI18n()
    val layout = LocalLayoutClass.current
    val state by container.artistEditor.state.collectAsState()
    var failed by remember { mutableStateOf<String?>(null) }
    val feedback = remember { Feedback() }
    LaunchedEffect(me) {
        me?.let { container.artistEditor.load(it, i18n.locale.code).onFailure { e -> failed = editorErrorCode(e) } }
    }
    LaunchedEffect(feedback.message) { if (feedback.message != null) { delay(2200); feedback.message = null } }
    val say: (Result<*>, Boolean) -> Unit = { r, okMessage ->
        r.onSuccess { if (okMessage) feedback.message = i18n.t(Strings.SET_SAVED) }
         .onFailure { e -> feedback.message = editorMessage(i18n.t(editorErrorKey(editorErrorCode(e)))) }
    }

    Box(Modifier.fillMaxSize().background(DJMetryColors.Background)) {
        val err = failed
        when {
            err != null -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
                BackBar(onBack)
                Hint(i18n.t(editorErrorKey(err)))
            }
            else -> LoadingCrossfade(loading = state == null, skeleton = { EditorSkeleton() }) {
                val s = state ?: return@LoadingCrossfade
                when (layout) {
                    LayoutClass.Compact -> PhoneEditor(s, onBack, say)
                    LayoutClass.Medium, LayoutClass.Expanded -> WideEditor(s, onBack, say, sidebar = layout == LayoutClass.Expanded)
                }
            }
        }
        AnimatedVisibility(
            visible = feedback.message != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp),
        ) {
            Text(feedback.message.orEmpty(), color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Accent).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

/** Подставить максимум треков в текст ошибки с %s. */
private fun editorMessage(text: String): String = text.replace("%s", MAX_ARTIST_TRACKS.toString())

@Composable
private fun BackBar(onBack: () -> Unit) {
    val i18n = useI18n()
    Row(
        Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DJMetryColors.Text, modifier = Modifier.size(20.dp))
        Text(i18n.t(Strings.ARTIST_BACK), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PhoneEditor(s: ArtistEditorState, onBack: () -> Unit, say: (Result<*>, Boolean) -> Unit) {
    var open by remember { mutableStateOf<EditorSection?>(null) }
    val i18n = useI18n()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        BackBar(if (open == null) onBack else { { open = null } })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sec = open
            if (sec == null) {
                PageTitle(i18n.t(Strings.ED_TITLE))
                SectionList(s, null) { open = it }
            } else SectionContent(sec, s, say)
        }
    }
}

@Composable
private fun WideEditor(s: ArtistEditorState, onBack: () -> Unit, say: (Result<*>, Boolean) -> Unit, sidebar: Boolean) {
    var open by remember { mutableStateOf(EditorSection.Socials) }
    val i18n = useI18n()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = if (sidebar) 28.dp else 24.dp)) {
        BackBar(onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(if (sidebar) 280.dp else 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageTitle(i18n.t(Strings.ED_TITLE))
                SectionList(s, open) { open = it }
            }
            Column(Modifier.weight(1f).widthIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionContent(open, s, say)
            }
        }
    }
}

/** Список разделов с кратким состоянием каждого. */
@Composable
private fun SectionList(s: ArtistEditorState, selected: EditorSection?, onOpen: (EditorSection) -> Unit) {
    val i18n = useI18n()
    val d = s.details
    val filledSocials = socialsFrom(d.socialMedia, d.youtube?.url).values.count { it.isNotBlank() }
    SettingsGroup(null) {
        SettingsRow(i18n.t(Strings.ED_SOCIALS), "$filledSocials / ${SocialField.values().size}", Icons.Outlined.Share, selected = selected == EditorSection.Socials) { onOpen(EditorSection.Socials) }
        SettingsRow(i18n.t(Strings.ED_GENRES), d.genres.take(MAX_GENRES).joinToString().ifEmpty { i18n.t(Strings.SET_NOT_SET) }, Icons.Outlined.LibraryMusic, selected = selected == EditorSection.Genres) { onOpen(EditorSection.Genres) }
        SettingsRow(i18n.t(Strings.ED_LOCATION), listOfNotNull(d.city, d.country).joinToString(", ").ifEmpty { i18n.t(Strings.SET_NOT_SET) }, Icons.Outlined.LocationOn, selected = selected == EditorSection.Location) { onOpen(EditorSection.Location) }
        SettingsRow(i18n.t(Strings.ED_TRACKS), "${s.curated.size} / $MAX_ARTIST_TRACKS", Icons.Outlined.Album, RowTone.Blue, selected = selected == EditorSection.Tracks) { onOpen(EditorSection.Tracks) }
        SettingsRow(i18n.t(Strings.ED_DOCS),
            "${i18n.t(Strings.ED_RIDER)}: ${i18n.t(if (s.rider != null) Strings.ED_UPLOADED else Strings.ED_NOT_UPLOADED)} · ${i18n.t(Strings.ED_PRESS)}: ${i18n.t(if (s.pressKit != null) Strings.ED_UPLOADED else Strings.ED_NOT_UPLOADED)}",
            Icons.Outlined.Description, RowTone.Orange, divider = false, selected = selected == EditorSection.Docs) { onOpen(EditorSection.Docs) }
    }
}

@Composable
private fun SectionContent(section: EditorSection, s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    when (section) {
        EditorSection.Socials -> SocialsSection(s, say)
        EditorSection.Genres -> GenresSection(s, say)
        EditorSection.Location -> LocationSection(s, say)
        EditorSection.Tracks -> TracksSection(s, say)
        EditorSection.Docs -> DocsSection(s, say)
    }
}

// ───────── Соцсети ─────────

private val SOCIAL_LABELS = mapOf(
    SocialField.Instagram to "Instagram", SocialField.Facebook to "Facebook", SocialField.TikTok to "TikTok", SocialField.Twitter to "X (Twitter)",
    SocialField.SoundCloud to "SoundCloud", SocialField.YouTube to "YouTube", SocialField.AppleMusic to "Apple Music", SocialField.Beatport to "Beatport",
    SocialField.Telegram to "Telegram",
)

@Composable
private fun SocialsSection(s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.artistEditor
    val scope = rememberCoroutineScope()
    val saved = remember(s.details) { socialsFrom(s.details.socialMedia, s.details.youtube?.url) }
    var values by remember(saved) { mutableStateOf(saved) }
    val bad = validateSocials(values)
    PageTitle(i18n.t(Strings.ED_SOCIALS))
    Hint(i18n.t(Strings.ED_SOCIALS_HINT))
    SocialField.values().forEach { f ->
        SettingsField(values[f].orEmpty(), { v -> values = values + (f to v) }, SOCIAL_LABELS.getValue(f),
            isError = f in bad, supporting = if (f in bad) i18n.t(Strings.ED_SOCIAL_INVALID) else null)
    }
    if (values != saved) PrimaryButton(i18n.t(Strings.SET_SAVE), enabled = bad.isEmpty()) {
        scope.launch { say(repo.saveSocials(values), true) }
    }
}

// ───────── Жанры ─────────

@Composable
private fun GenresSection(s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val saved = remember(s.details) { s.details.genres.take(MAX_GENRES) }
    var picked by remember(saved) { mutableStateOf(saved) }
    var picking by remember { mutableStateOf(false) }
    val all by produceState(emptyList<String>()) { value = container.settings.allGenres().getOrNull().orEmpty() }
    PageTitle(i18n.t(Strings.ED_GENRES))
    Hint(i18n.tWithArgs(Strings.SET_GENRES_HINT, arrayOf(MAX_GENRES)) + " · ${picked.size}/$MAX_GENRES")
    SettingsGroup(null) {
        picked.forEachIndexed { i, g -> SettingsRow("${i + 1}. $g", null, null, end = RowEnd.Value("✕")) { picked = picked - g } }
        SettingsRow(i18n.t(Strings.SET_ADD_GENRE), null, Icons.Outlined.Add, end = RowEnd.Chevron, divider = false, enabled = picked.size < MAX_GENRES) { picking = true }
    }
    if (picking) SearchPickerDialog(
        title = i18n.t(Strings.ED_GENRES), items = all.filter { g -> picked.none { it.equals(g, ignoreCase = true) } }, label = { it },
        onPick = { picked = addGenre(picked, it); picking = false }, onDismiss = { picking = false },
    )
    // Для артиста перестановка того же набора — не изменение (спека: сравнение sorted)
    if (picked.sorted() != saved.sorted()) PrimaryButton(i18n.t(Strings.SET_SAVE)) {
        scope.launch { say(container.artistEditor.saveGenres(picked), true) }
    }
}

// ───────── Локация ─────────

@Composable
private fun LocationSection(s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.artistEditor
    val scope = rememberCoroutineScope()
    var country by remember(s.details) { mutableStateOf(s.details.country.orEmpty()) }
    var city by remember(s.details) { mutableStateOf(s.details.city.orEmpty()) }
    var region by remember(s.details) { mutableStateOf(s.details.region.orEmpty()) }
    PageTitle(i18n.t(Strings.ED_LOCATION))
    CountryCityPicker(country, { country = it }, city, { city = it }, allowOther = false)
    SettingsField(region, { region = it }, i18n.t(Strings.SET_REGION_FIELD))
    val ok = Regex("^[A-Za-z]{2}$").matches(country.trim())
    if (!ok) Hint(i18n.t(Strings.ED_COUNTRY_REQUIRED))
    PrimaryButton(i18n.t(Strings.SET_SAVE), enabled = ok) { scope.launch { say(repo.saveLocation(country, city, region), true) } }
}

// ───────── Треки ─────────

@Composable
private fun TracksSection(s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.artistEditor
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val link = if (url.isBlank()) null else parseSpotifyTrack(url)
    PageTitle(i18n.t(Strings.ED_TRACKS))
    Hint(i18n.tWithArgs(Strings.ED_TRACKS_HINT, arrayOf(MAX_ARTIST_TRACKS)))
    if (s.curated.isEmpty()) {
        Hint(i18n.t(Strings.ED_TRACKS_TOP))
        SettingsGroup(null) { s.topTracks.forEachIndexed { i, t -> TrackRow(t.name, t.albumImageUrl, divider = i < s.topTracks.lastIndex) {} } }
    } else SettingsGroup(null) {
        s.curated.forEachIndexed { i, t ->
            val id = t.spotifyTrackId ?: return@forEachIndexed
            TrackRow("${i + 1}. ${t.name}", t.albumImageUrl, divider = i < s.curated.lastIndex) {
                // Порядок = место на странице; стрелки вместо перетаскивания — надёжно на всех устройствах
                TextButton(onClick = { scope.launch { say(repo.moveTrack(id, -1), false) } }, enabled = i > 0) { Text("↑", color = DJMetryColors.Text) }
                TextButton(onClick = { scope.launch { say(repo.moveTrack(id, +1), false) } }, enabled = i < s.curated.lastIndex) { Text("↓", color = DJMetryColors.Text) }
                TextButton(onClick = { scope.launch { say(repo.removeTrack(id), false) } }) { Text("✕", color = DJMetryColors.LowScore) }
            }
        }
    }
    if (s.curated.size < MAX_ARTIST_TRACKS) {
        val err = when (link) {
            null, is TrackLink.Ok -> null
            TrackLink.Album -> Strings.ED_ERR_ALBUM
            TrackLink.Artist -> Strings.ED_ERR_ARTIST
            TrackLink.Playlist -> Strings.ED_ERR_PLAYLIST
            TrackLink.Invalid -> Strings.ED_ERR_INVALID
        }
        SettingsField(url, { url = it }, i18n.t(Strings.ED_TRACK_URL), isError = err != null, supporting = err?.let { i18n.t(it) })
        // Автосохранение: трек добавляется сразу, отдельной кнопки «Сохранить» нет
        PrimaryButton(i18n.t(Strings.ED_ADD), enabled = link is TrackLink.Ok && !busy) {
            scope.launch { busy = true; repo.addTrack(url).also { say(it, false) }.onSuccess { url = "" }; busy = false }
        }
    }
}

@Composable
private fun TrackRow(title: String, cover: String?, divider: Boolean, actions: @Composable RowScope.() -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(cover, 40.dp, cornerRadius = 10.dp)
            Text(title, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            actions()
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(DJMetryColors.Border))
    }
}

// ───────── Райдер и пресс-кит ─────────

@Composable
private fun DocsSection(s: ArtistEditorState, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    PageTitle(i18n.t(Strings.ED_DOCS))
    Hint(i18n.t(Strings.ED_PDF_ONLY))
    DocBlock(BookingDoc.Rider, i18n.t(Strings.ED_RIDER), s.rider?.url, say)
    DocBlock(BookingDoc.PressKit, i18n.t(Strings.ED_PRESS), s.pressKit?.url, say)
}

@Composable
private fun DocBlock(doc: BookingDoc, title: String, url: String?, say: (Result<*>, Boolean) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.artistEditor
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val picker = remember { platformPdfPicker() }
    var busy by remember { mutableStateOf(false) }
    val upload: () -> Unit = {
        scope.launch {
            busy = true
            picker.pick()?.let { file -> say(repo.uploadDoc(doc, file), true) }
            busy = false
        }
    }
    SettingsGroup(title) {
        SettingsRow(title, i18n.t(if (url != null) Strings.ED_UPLOADED else Strings.ED_NOT_UPLOADED), Icons.Outlined.Description, RowTone.Orange, RowEnd.None, divider = true)
        if (url != null) {
            SettingsRow(i18n.t(Strings.ED_OPEN), null, Icons.Outlined.OpenInNew, end = RowEnd.None) { uri.openUri(url) }
            SettingsRow(i18n.t(Strings.ED_REPLACE), null, Icons.Outlined.UploadFile, end = RowEnd.None, enabled = !busy, onClick = upload)
            SettingsRow(i18n.t(Strings.ED_DELETE), null, Icons.Outlined.Delete, RowTone.Red, RowEnd.None, divider = false, titleColor = DJMetryColors.LowScore) {
                scope.launch { say(repo.deleteDoc(doc), true) }
            }
        } else SettingsRow(i18n.t(Strings.ED_UPLOAD), null, Icons.Outlined.UploadFile, end = RowEnd.None, divider = false, enabled = !busy, onClick = upload)
    }
}

@Composable
private fun EditorSkeleton() {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { SkeletonListRow(it, leading = false, cover = 32.dp) }
    }
}
