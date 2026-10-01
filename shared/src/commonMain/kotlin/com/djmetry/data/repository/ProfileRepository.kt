package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.endpoints.RadarApi
import com.djmetry.api.endpoints.UserApi
import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.BookingRequest
import com.djmetry.api.models.MeResponse
import com.djmetry.api.models.ReleaseRadarRelease
import com.djmetry.api.models.SnapshotItem
import com.djmetry.api.models.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ReleasePreview(val release: ReleaseRadarRelease, val artistName: String, val spotifyArtistId: String? = null)

/** Всё для экрана «Профиль»: что пришло с бэкенда, без пересчётов на клиенте. */
data class ProfileDashboard(
    val me: MeResponse,
    val artist: ArtistDetailsResponse? = null,
    val scoreHistory: List<SnapshotItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val bookingCompanies: List<BookingCompany> = emptyList(),
    val bookingRequests: List<BookingRequest> = emptyList(),
    val releases: List<ReleasePreview> = emptyList(),
) {
    // По верификации, а не по загрузке карточки: сбой сети не превращает артиста в фаната
    val isArtist: Boolean get() = verifiedArtistId(me) != null
    /** Артист, а карточка не загрузилась — «Ошибка · Повторить» вместо профиля фаната. */
    val artistFailed: Boolean get() = isArtist && artist == null
    /** Кнопку «Букинг» показываем, только если у артиста есть привязанная компания. */
    val hasBooking: Boolean get() = bookingCompanies.isNotEmpty()
}

/** Верифицированный артист аккаунта, если есть (docs/BACKEND_API.md → «Кабинет»). */
internal fun verifiedArtistId(me: MeResponse): String? =
    me.artistVerification?.takeIf { it.isVerified }?.verifiedSpotifyArtistId

/**
 * Собирает дашборд из нескольких эндпоинтов параллельно. Каждый источник необязателен:
 * упал букинг или радар — экран показывает остальное.
 */
class ProfileRepository(
    private val userApi: UserApi,
    private val artistApi: ArtistApi,
    private val bookingApi: BookingApi,
    private val radarApi: RadarApi,
    /** Feed Радара с его кэшем (15 мин) — дашборд не качает тяжёлый feed второй раз. */
    private val radar: RadarRepository? = null,
) {
    suspend fun load(me: MeResponse, lang: String? = null): ProfileDashboard = coroutineScope {
        val releases = async {
            (radar?.feed()?.getOrNull() ?: radarApi.releaseFeed().getOrNull()?.artists).orEmpty()
                .flatMap { artist -> artist.latest.map { ReleasePreview(it, artist.artist_name, artist.spotify_artist_id) } }
                .sortedByDescending { it.release.release_date }
                .take(RELEASES_PREVIEW)
        }
        val artistId = verifiedArtistId(me)
            ?: return@coroutineScope ProfileDashboard(me = me, releases = releases.await())

        val artist = async { artistApi.details(artistId, lang).getOrNull() }
        val history = async { userApi.getSnapshots(30).getOrNull()?.snapshots.orEmpty() }
        val tracks = async { artistApi.tracks(artistId, limit = 3).getOrNull()?.tracks.orEmpty() }
        val companies = async { bookingApi.publicCompanies(artistId).getOrNull()?.companies.orEmpty() }
        // Для превью нужны 3 — не весь список заявок
        val requests = async { bookingApi.artistRequests(artistId, limit = 20).getOrNull()?.requests.orEmpty() }

        ProfileDashboard(
            me = me,
            artist = artist.await(),
            scoreHistory = history.await(),
            tracks = tracks.await(),
            bookingCompanies = companies.await(),
            bookingRequests = requests.await().take(REQUESTS_PREVIEW),
            releases = releases.await(),
        )
    }

    private companion object {
        const val RELEASES_PREVIEW = 3
        const val REQUESTS_PREVIEW = 3
    }
}
