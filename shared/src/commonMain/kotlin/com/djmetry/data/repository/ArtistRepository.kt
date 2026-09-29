package com.djmetry.data.repository

import com.djmetry.api.endpoints.ArtistApi
import com.djmetry.api.endpoints.BookingApi
import com.djmetry.api.models.ArtistDetailsResponse
import com.djmetry.api.models.ArtistEvent
import com.djmetry.api.models.BookingCompany
import com.djmetry.api.models.DJMagRankingsResponse
import com.djmetry.api.models.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Место артиста в последнем рейтинге DJ Mag. [year] = null, если знаем только номер из карточки. */
data class DJMagEntry(val rank: Int, val year: Int?, val previousRank: Int?)

/** Всё для публичной карточки артиста — как пришло с бэкенда, без пересчётов. */
data class ArtistCard(
    val details: ArtistDetailsResponse,
    val tracks: List<Track> = emptyList(),
    val events: List<ArtistEvent> = emptyList(),
    val bookingCompanies: List<BookingCompany> = emptyList(),
    val djMag: DJMagEntry? = null,
) {
    /** «Забронировать» — только если у артиста есть агентство на платформе. */
    val hasBooking: Boolean get() = bookingCompanies.isNotEmpty()
}

/**
 * Публичная карточка артиста (docs/BACKEND_API.md → «Карточка артиста»).
 * Карточка обязательна; треки, концерты, букинг и DJ Mag — необязательны: упали — секции нет.
 */
class ArtistRepository(
    private val artistApi: ArtistApi,
    private val bookingApi: BookingApi,
) {
    private val djMagLock = Mutex()
    private var djMagLatest: DJMagRankingsResponse? = null

    suspend fun load(spotifyArtistId: String, lang: String? = null): Result<ArtistCard> = coroutineScope {
        val details = async { artistApi.details(spotifyArtistId, lang) }
        val tracks = async { artistApi.tracks(spotifyArtistId, limit = TRACKS).getOrNull()?.tracks.orEmpty() }
        val events = async { artistApi.events(spotifyArtistId).getOrNull()?.events.orEmpty().sortedBy { it.datetime } }
        val companies = async { bookingApi.publicCompanies(spotifyArtistId).getOrNull()?.companies.orEmpty() }
        val djMag = async { djMagRankings() }

        details.await().map { d ->
            ArtistCard(
                details = d,
                tracks = cardTracks(d.curatedTracks, tracks.await()),
                events = events.await(),
                bookingCompanies = companies.await(),
                djMag = djMagEntry(d, djMag.await()),
            )
        }
    }

    /** Рейтинг DJ Mag меняется раз в год — грузим один раз за сессию. */
    private suspend fun djMagRankings(): DJMagRankingsResponse? = djMagLock.withLock {
        djMagLatest ?: artistApi.djMagRankings().getOrNull()?.also { djMagLatest = it }
    }

    internal companion object {
        const val TRACKS = 5

        /**
         * «Музыка» на карточке — как на сайте (useArtistPageModel): сначала треки, выбранные артистом в редакторе
         * (`curatedTracks` из `GET /artists/spotify/:id`), затем добор топ-треками Spotify без повторов, всего [TRACKS].
         */
        internal fun cardTracks(curated: List<Track>, top: List<Track>): List<Track> {
            val result = curated.take(TRACKS).toMutableList()
            val seen = result.mapNotNull { it.spotifyTrackId }.toMutableSet()
            for (t in top) {
                if (result.size >= TRACKS) break
                val id = t.spotifyTrackId ?: continue
                if (seen.add(id)) result += t
            }
            return result
        }

        /** Ищем артиста в последнем рейтинге; нет там — берём номер из карточки без года. */
        fun djMagEntry(details: ArtistDetailsResponse, latest: DJMagRankingsResponse?): DJMagEntry? {
            val row = latest?.rankings?.firstOrNull { it.spotifyArtistId == details.spotifyArtistId }
            return when {
                row != null -> DJMagEntry(row.rank, latest.year, row.previousYearRank)
                details.djMagRank != null -> DJMagEntry(details.djMagRank, null, null)
                else -> null
            }
        }
    }
}
