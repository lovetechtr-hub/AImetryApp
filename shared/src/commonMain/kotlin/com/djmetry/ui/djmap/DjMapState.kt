package com.djmetry.ui.djmap

import androidx.compose.runtime.*
import com.djmetry.api.models.*
import com.djmetry.data.djmap.*
import com.djmetry.data.repository.DjMapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Что открыто во всплывающей карточке (макеты — как попапы сайта). */
sealed interface MapPopup {
    /** Одна точка — hero-карточка; несколько — список (до 12 + «Ещё N»). */
    data class Events(val points: List<MapEventPoint>, val lat: Double, val lng: Double) : MapPopup
    /** Площадка: [densityHead] — «Концерты здесь: N» на уровне площадок «ТОП стран». */
    data class Venue(val venue: MapVenue, val densityHead: Boolean = false) : MapPopup
    /** Выбор из площадок с одинаковыми координатами. */
    data class VenuePick(val venues: List<MapVenue>, val lat: Double, val lng: Double) : MapPopup
    /** Страна «ТОП стран» (играют там) или «Откуда диджеи» (родом оттуда). */
    data class Country(val iso: String, val origins: Boolean, val lat: Double, val lng: Double) : MapPopup
    data class City(val city: DensityCity) : MapPopup
}

/**
 * Состояние карты диджеев: слой, фильтры, режим одного DJ и загруженные данные. Считает бэкенд — здесь только
 * выбор запросов (как сайт) и перезапрос видимой области с запасом +20%.
 */
@Stable
class DjMapState(private val repo: DjMapRepository, private val scope: CoroutineScope, initialArtistId: String?, compact: Boolean) {
    var layer by mutableStateOf(MapLayer.Performances)
    var filters by mutableStateOf(MapFilters())
    var artistId by mutableStateOf(initialArtistId)
    var light by mutableStateOf(false)
    var zoom by mutableStateOf(1.4)
    var popup by mutableStateOf<MapPopup?>(null)
    private var pending by mutableStateOf(0)
    /** Идёт загрузка — пилюля «Обновление…». Счётчик, чтобы отменённый запрос не оставил её висеть. */
    val loading: Boolean get() = pending > 0
    var selectedCountry by mutableStateOf<String?>(null)
    /** Выбранный город в ленте тура (−1 — нет). */
    var activeStop by mutableStateOf(-1)
    /** Команда камере (лента лидеров, кластеры, тур) — выполняет экран. */
    var flyTo by mutableStateOf<FlyTo?>(null)
    /** Кнопки «+ / −» на широких экранах. */
    var zoomDelta by mutableStateOf<Double?>(null)

    var catalog by mutableStateOf(MapFiltersResponse())
    var points by mutableStateOf<List<MapEventPoint>>(emptyList())
    var tour by mutableStateOf<List<MapEventPoint>>(emptyList())
    var touring by mutableStateOf<List<MapTouringDj>>(emptyList())
    var venues by mutableStateOf<List<MapVenue>>(emptyList())
    var venueClusters by mutableStateOf<List<MapServerCluster>>(emptyList())
    var topVenues by mutableStateOf<List<MapVenue>>(emptyList())
    var densityCountries by mutableStateOf<List<DensityCountry>>(emptyList())
    var densityCities by mutableStateOf<List<DensityCity>>(emptyList())
    var densityVenues by mutableStateOf<List<MapVenue>>(emptyList())
    var origins by mutableStateOf<List<MapOrigin>>(emptyList())

    private suspend fun <T> track(block: suspend () -> T): T { pending++; try { return block() } finally { pending-- } }

    private val pointsLimit = if (compact) 1500 else 3000
    private val venuesLimit = if (compact) 400 else 800
    private var loadedBounds: Bounds? = null
    private var loadedKey: Any? = null
    private var viewportJob: Job? = null

    val densityLevel: DensityLevel get() = densityLevel(zoom)

    /** Точки на карте: у всех DJ — одна на артиста, у одного DJ — все выступления. */
    val shownPoints: List<MapEventPoint> by derivedStateOf { if (artistId == null) onePerArtist(points, Clock.System.now()) else points }

    /** «На карте: N» — как счётчик сайта. */
    val onMap: Int by derivedStateOf {
        when (layer) {
            MapLayer.Performances -> shownPoints.size
            MapLayer.Venues -> venueClusters.sumOf { it.count }.takeIf { it > 0 } ?: venues.size
            MapLayer.Density -> densityCountries.size
            MapLayer.Origins -> origins.count { dominantGenre(it.genres) != null }
        }
    }

    fun loadCatalog() = scope.launch { repo.filters().onSuccess { catalog = it } }

    /** Данные, не зависящие от области: лидерборды, страны, тур одного DJ. */
    fun loadStatic() = scope.launch {
        popup = null
        track { coroutineScope {
            when (layer) {
                MapLayer.Performances -> {
                    val id = artistId
                    if (id != null) {
                        val pts = async { repo.performances(filters.artistParams(id)) }
                        val t = async { repo.tour(id) }
                        points = pts.await().getOrNull()?.points.orEmpty()
                        tour = t.await().getOrNull()?.points.orEmpty()
                    } else {
                        tour = emptyList()
                        launch { touring = repo.topTouring(filters.topTouringParams()).getOrNull()?.djs.orEmpty() }
                    }
                }
                MapLayer.Density -> densityCountries = repo.density(filters.densityParams(DensityLevel.Country)).getOrNull()?.countries.orEmpty()
                MapLayer.Origins -> origins = repo.origins(filters.genre).getOrNull()?.origins.orEmpty()
                MapLayer.Venues -> topVenues = repo.density(filters.topVenuesParams()).getOrNull()?.venues.orEmpty().filterNot { conflictsWithType(it, filters.type) }
            }
        } }
        loadedBounds = null; loadedKey = null
    }

    /** Камера остановилась: догрузить данные области (если она вышла за загруженную) или уровня «ТОП стран». */
    fun onCameraIdle(bounds: Bounds?, newZoom: Double) {
        zoom = newZoom
        viewportJob?.cancel()
        viewportJob = scope.launch {
            when (layer) {
                MapLayer.Performances -> if (artistId == null) {
                    val key = filters
                    if (bounds != null && loadedKey == key && loadedBounds?.contains(bounds) == true) return@launch
                    val area = bounds?.padded()
                    track { repo.performances(filters.performanceParams(area, pointsLimit)) }.onSuccess { points = it.points; loadedBounds = area; loadedKey = key }
                }
                MapLayer.Venues -> {
                    val z = newZoom.toInt()
                    val key = filters to z
                    if (bounds != null && loadedKey == key && loadedBounds?.contains(bounds) == true) return@launch
                    val area = bounds?.padded()
                    track { repo.venues(filters.venuesParams(area, z, venuesLimit)) }.onSuccess { r ->
                        venueClusters = r.clusters; venues = r.points.filterNot { conflictsWithType(it, filters.type) }; loadedBounds = area; loadedKey = key
                    }
                }
                MapLayer.Density -> when (densityLevel) {
                    DensityLevel.Country -> Unit
                    DensityLevel.City -> if (densityCities.isEmpty() || loadedKey != filters to DensityLevel.City) {
                        track { repo.density(filters.densityParams(DensityLevel.City)) }.onSuccess { densityCities = it.cities; loadedKey = filters to DensityLevel.City }
                    }
                    DensityLevel.Venue -> if (densityVenues.isEmpty() || loadedKey != filters to DensityLevel.Venue) {
                        track { repo.density(filters.densityParams(DensityLevel.Venue)) }.onSuccess { r ->
                            densityVenues = r.venues.filterNot { conflictsWithType(it, filters.type) }; loadedKey = filters to DensityLevel.Venue
                        }
                    }
                }
                MapLayer.Origins -> Unit
            }
        }
    }

    fun selectArtist(id: String?) { artistId = id; layer = MapLayer.Performances; points = emptyList(); activeStop = -1 }

    suspend fun venueLineup(id: String) = repo.venueArtists(id)
    suspend fun countryArtists(iso: String, origins: Boolean) = if (origins) repo.originArtists(iso, filters.genre) else repo.topArtists(iso, filters.genre)
}
