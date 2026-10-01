package com.djmetry.data.repository

import com.djmetry.api.ApiException
import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.ArtistEditorApi
import com.djmetry.api.endpoints.BookingDoc
import com.djmetry.api.endpoints.SocialField
import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.BookingDocResponse
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.PickedFile
import com.djmetry.api.models.SocialMedia
import com.djmetry.api.models.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val MAX_ARTIST_TRACKS = 5
const val MAX_PDF_BYTES = 10 * 1024 * 1024

// ───────── Строгость ввода (как на сайте: socialsValidation.ts, parseSpotifyUrl) ─────────

/** Какие домены разрешены в поле соцсети (как `ALLOWED_HOSTS` сайта). */
val SOCIAL_HOSTS: Map<SocialField, List<String>> = mapOf(
    SocialField.Instagram to listOf("instagram.com"),
    SocialField.Facebook to listOf("facebook.com", "fb.com", "fb.me", "m.facebook.com"),
    SocialField.TikTok to listOf("tiktok.com", "vm.tiktok.com"),
    SocialField.Twitter to listOf("twitter.com", "x.com"),
    SocialField.SoundCloud to listOf("soundcloud.com"),
    SocialField.YouTube to listOf("youtube.com", "youtu.be", "m.youtube.com"),
    SocialField.AppleMusic to listOf("music.apple.com"),
    SocialField.Beatport to listOf("beatport.com"),
    SocialField.Telegram to listOf("t.me", "telegram.me", "telegram.org"),
)

/** Хост ссылки без www; не ссылка — null. */
internal fun hostOf(url: String): String? =
    Regex("""^https?://([^/?#:]+)""", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1)?.lowercase()?.removePrefix("www.")

/**
 * Поля с ошибкой: ссылка ведёт не на свою соцсеть; у Apple Music и Beatport — не полная ссылка.
 * Ник без ссылки (например «martingarrix») разрешён для остальных. Пустые поля не проверяются.
 */
fun validateSocials(values: Map<SocialField, String>): Set<SocialField> = values.mapNotNull { (field, raw) ->
    val v = raw.trim()
    when {
        v.isEmpty() -> null
        v.startsWith("http://", true) || v.startsWith("https://", true) -> {
            val host = hostOf(v)
            field.takeIf { host == null || SOCIAL_HOSTS.getValue(field).none { it == host } }
        }
        field == SocialField.AppleMusic || field == SocialField.Beatport -> field
        else -> null
    }
}.toSet()

/** Соцсети артиста из карточки → значения полей редактора. */
fun socialsFrom(s: SocialMedia?, youtubeUrl: String?): Map<SocialField, String> = mapOf(
    SocialField.Instagram to s?.instagram.orEmpty(), SocialField.Facebook to s?.facebook.orEmpty(),
    SocialField.TikTok to s?.tiktok.orEmpty(), SocialField.Twitter to s?.twitter.orEmpty(),
    SocialField.SoundCloud to s?.soundcloud.orEmpty(), SocialField.YouTube to youtubeUrl.orEmpty(),
    SocialField.AppleMusic to s?.appleMusicUrl.orEmpty(), SocialField.Beatport to s?.beatportUrl.orEmpty(),
    SocialField.Telegram to s?.telegram.orEmpty(),
)

/** Разбор ссылки на трек: только трек Spotify; альбом, артист и плейлист — отдельные ошибки. */
sealed interface TrackLink {
    data class Ok(val id: String) : TrackLink
    data object Album : TrackLink
    data object Artist : TrackLink
    data object Playlist : TrackLink
    data object Invalid : TrackLink
}

fun parseSpotifyTrack(input: String): TrackLink {
    val v = input.trim()
    val m = Regex("""^(?:https?://open\.spotify\.com/(?:intl-[a-z]{2}/)?|spotify:)(track|album|artist|playlist)[/:]([A-Za-z0-9]{22})(?:[/?#].*)?$""").find(v)
        ?: return TrackLink.Invalid
    return when (m.groupValues[1]) {
        "track" -> TrackLink.Ok(m.groupValues[2])
        "album" -> TrackLink.Album
        "artist" -> TrackLink.Artist
        else -> TrackLink.Playlist
    }
}

/** Файл райдера / пресс-кита: только PDF (расширение или MIME, и сигнатура «%PDF»), не больше 10 МБ. */
enum class PdfProblem { NotPdf, TooBig, Empty }

fun validatePdf(file: PickedFile): PdfProblem? {
    if (file.bytes.isEmpty()) return PdfProblem.Empty
    val declared = file.mime == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)
    val magic = file.bytes.size >= 4 && file.bytes[0] == '%'.code.toByte() && file.bytes[1] == 'P'.code.toByte() &&
        file.bytes[2] == 'D'.code.toByte() && file.bytes[3] == 'F'.code.toByte()
    if (!declared || !magic) return PdfProblem.NotPdf
    if (file.bytes.size > MAX_PDF_BYTES) return PdfProblem.TooBig
    return null
}

/** Переставить трек на позицию выше/ниже; за краями — без изменений. */
fun moveTrack(ids: List<String>, id: String, delta: Int): List<String> {
    val i = ids.indexOf(id); val j = i + delta
    if (i < 0 || j !in ids.indices) return ids
    return ids.toMutableList().also { it.removeAt(i); it.add(j, id) }
}

// ───────── Состояние и репозиторий ─────────

/** Ошибка ввода — запрос не отправлялся (строгость: мусор на бэк не уходит). */
class InputException(val code: String) : Exception(code)

data class ArtistEditorState(
    val artistId: String,
    val details: ArtistDetailsResponse,
    val curated: List<Track>,
    /** Топ Spotify — показываем, пока своих треков нет (спека: fallback). */
    val topTracks: List<Track>,
    val rider: BookingDocResponse? = null,
    val pressKit: BookingDocResponse? = null,
)

/**
 * Редактор проверенного артиста (спека §14). Гейтинг — только `artistVerification.isVerified`, редактируется свой id.
 * Всё строго: неверный ввод отсекается до запроса ([InputException]).
 */
class ArtistEditorRepository(private val api: ArtistEditorApi, private val artistApi: ArtistApi) : UserScoped {
    private val _state = MutableStateFlow<ArtistEditorState?>(null)
    val state: StateFlow<ArtistEditorState?> = _state.asStateFlow()

    override suspend fun clearUserData() { _state.value = null }

    suspend fun load(me: MeResponse, lang: String? = null): Result<ArtistEditorState> = coroutineScope {
        val id = verifiedArtistId(me) ?: return@coroutineScope Result.failure(InputException("artist_verification_required"))
        val details = async { artistApi.details(id, lang) }
        // Свои треки: сбой сети — ошибка экрана, а не «пусто» (иначе фолбэк из Spotify и неверный лимит в addTrack)
        val curated = async { api.tracks().map { it.tracks }.recoverCatching { e -> if ((e as? com.djmetry.api.ApiException)?.status == 404) emptyList() else throw e } }
        val top = async { artistApi.tracks(id, limit = MAX_ARTIST_TRACKS).getOrNull()?.tracks.orEmpty() }
        // Ошибка сети — не «файл не загружен»: иначе артист перезальёт райдер, который на месте
        val rider = async { api.doc(id, BookingDoc.Rider) }
        val press = async { api.doc(id, BookingDoc.PressKit) }
        details.await().mapCatching { d -> ArtistEditorState(id, d, curated.await().getOrThrow(), top.await(), rider.await().getOrThrow(), press.await().getOrThrow()) }
            .onSuccess { _state.value = it }
    }

    suspend fun saveSocials(values: Map<SocialField, String>): Result<Unit> {
        val bad = validateSocials(values)
        if (bad.isNotEmpty()) return Result.failure(InputException("invalid_social:" + bad.joinToString(",") { it.key }))
        return api.saveSocials(values).map {
            _state.update { s -> s?.copy(details = s.details.copy(socialMedia = SocialMedia(
                instagram = values[SocialField.Instagram]?.trim()?.ifEmpty { null }, facebook = values[SocialField.Facebook]?.trim()?.ifEmpty { null },
                tiktok = values[SocialField.TikTok]?.trim()?.ifEmpty { null }, twitter = values[SocialField.Twitter]?.trim()?.ifEmpty { null },
                soundcloud = values[SocialField.SoundCloud]?.trim()?.ifEmpty { null }, telegram = values[SocialField.Telegram]?.trim()?.ifEmpty { null },
                appleMusicUrl = values[SocialField.AppleMusic]?.trim()?.ifEmpty { null }, beatportUrl = values[SocialField.Beatport]?.trim()?.ifEmpty { null },
            ), youtube = values[SocialField.YouTube]?.trim().let { url ->
                // YouTube живёт отдельно от socialMedia: без этого поле после сохранения показывало старую ссылку
                if (url.isNullOrEmpty()) s.details.youtube?.copy(url = null) else (s.details.youtube ?: com.djmetry.api.models.YouTubeData()).copy(url = url)
            })) }
            Unit
        }
    }

    /** Жанры артиста: из списка, ≤5, порядок = приоритет, дубли без учёта регистра — те же правила, что у фаната. */
    suspend fun saveGenres(genres: List<String>): Result<Unit> {
        val list = genres.fold(emptyList<String>()) { acc, g -> addGenre(acc, g) }
        return api.saveGenres(list).map { _state.update { s -> s?.copy(details = s.details.copy(genres = list)) }; Unit }
    }

    /** Страна обязательна (ISO2 из справочника); город и регион — пусто = очистить. */
    suspend fun saveLocation(country: String, city: String, region: String): Result<Unit> {
        val c = country.trim().uppercase()
        if (!Regex("^[A-Z]{2}$").matches(c)) return Result.failure(InputException("country_required"))
        return api.saveLocation(c, city, region).map {
            _state.update { s -> s?.copy(details = s.details.copy(country = c, city = city.trim().ifEmpty { null }, region = region.trim().ifEmpty { null })) }
            Unit
        }
    }

    /** Добавить трек по ссылке (автосохранение): только трек Spotify, не больше 5, без дублей. */
    suspend fun addTrack(input: String): Result<Unit> {
        val link = parseSpotifyTrack(input)
        if (link !is TrackLink.Ok) return Result.failure(InputException(when (link) {
            TrackLink.Album -> "track_is_album"; TrackLink.Artist -> "track_is_artist"; TrackLink.Playlist -> "track_is_playlist"; else -> "invalid_track_url"
        }))
        val current = _state.value?.curated.orEmpty()
        if (current.size >= MAX_ARTIST_TRACKS) return Result.failure(InputException("track_limit"))
        if (current.any { it.spotifyTrackId == link.id }) return Result.failure(InputException("track_duplicate"))
        return api.addTrack("https://open.spotify.com/track/${link.id}").map { r -> _state.update { it?.copy(curated = r.tracks) }; Unit }
    }

    suspend fun removeTrack(id: String): Result<Unit> =
        api.removeTrack(id).map { r -> _state.update { it?.copy(curated = r.tracks) }; Unit }

    /** Порядок треков — сразу на экране, при ошибке — откат. */
    suspend fun moveTrack(id: String, delta: Int): Result<Unit> {
        val before = _state.value ?: return Result.failure(IllegalStateException("not loaded"))
        val ids = moveTrack(before.curated.mapNotNull { it.spotifyTrackId }, id, delta)
        if (ids == before.curated.mapNotNull { it.spotifyTrackId }) return Result.success(Unit)
        _state.value = before.copy(curated = ids.mapNotNull { i -> before.curated.firstOrNull { it.spotifyTrackId == i } })
        return api.reorderTracks(ids).map { Unit }.onFailure { _state.value = before }
    }

    /** Райдер / пресс-кит: только PDF ≤10 МБ — проверяем до загрузки. */
    suspend fun uploadDoc(doc: BookingDoc, file: PickedFile): Result<Unit> {
        validatePdf(file)?.let { return Result.failure(InputException("pdf_" + it.name.lowercase())) }
        val id = _state.value?.artistId ?: return Result.failure(IllegalStateException("not loaded"))
        return api.uploadDoc(id, doc, file).map { r ->
            _state.update { s -> if (doc == BookingDoc.Rider) s?.copy(rider = r) else s?.copy(pressKit = r) }
            Unit
        }
    }

    suspend fun deleteDoc(doc: BookingDoc): Result<Unit> {
        val id = _state.value?.artistId ?: return Result.failure(IllegalStateException("not loaded"))
        return api.deleteDoc(id, doc).map { _state.update { s -> if (doc == BookingDoc.Rider) s?.copy(rider = null) else s?.copy(pressKit = null) }; Unit }
    }
}

/** Код ошибки редактора для текста: ввод или ответ бэка (`track_not_yours`, `track_not_found`, …). */
fun editorErrorCode(e: Throwable): String = when (e) {
    is InputException -> e.code.substringBefore(':')
    is ApiException -> e.code ?: "failed"
    else -> "network"
}
