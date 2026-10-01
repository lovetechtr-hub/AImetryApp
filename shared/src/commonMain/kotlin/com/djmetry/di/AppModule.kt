package com.djmetry.di

import com.djmetry.AppContainer
import com.djmetry.data.search.ArtistSearchRepository
import com.djmetry.ui.search.SearchViewModel
import org.koin.dsl.module

/**
 * Граф зависимостей приложения (Koin). Переходный этап: существующие репозитории живут в [AppContainer]
 * и отдаются отсюда как есть; новые классы и ViewModel объявляются только здесь и получают зависимости
 * через `get()`. Экран переезжает — его ViewModel добавляется сюда, `LocalAppContainer` в нём больше не нужен.
 */
fun appModule(c: AppContainer) = module {
    // Из контейнера (по мере переезда сюда переносятся и сами конструкторы)
    single { c.storage }
    single { c.artistApi }
    single { c.searchHistory }
    single { c.discover }
    single { c.radar }
    single { c.booking }
    single { c.rating }
    single { c.notifications }
    single { c.settings }
    single { c.artists }
    single { c.artistEditor }
    single { c.analytics }
    single { c.audience }
    single { c.djMap }
    /** Фон уровня приложения: подписка и голос из колоды не отменяются уходом с экрана. */
    single<kotlinx.coroutines.CoroutineScope> { c.appScope }

    // Новое — только через Koin
    single { ArtistSearchRepository(get()) }
    // ViewModel — factory: экземпляр хранит appViewModel(), Koin только собирает
    factory { SearchViewModel(get(), get()) }
    factory { p -> com.djmetry.ui.screens.DiscoverViewModel(get(), get(), p.getOrNull<Int>() ?: 0) }
    factory { com.djmetry.ui.radar.RadarViewModel() }
    factory { com.djmetry.ui.rating.RatingViewModel() }
    factory { com.djmetry.ui.artist.ArtistViewModel() }
    factory { com.djmetry.ui.radar.ArtistReleasesViewModel() }
    factory { com.djmetry.ui.profile.ProfileViewModel() }
    factory { p -> com.djmetry.ui.analytics.AnalyticsViewModel(p.getOrNull<Boolean>() ?: false) }
    factory { com.djmetry.ui.analytics.AudienceViewModel() }
    factory { com.djmetry.ui.search.ScreenStateViewModel() }
    factory { p -> com.djmetry.ui.djmap.DjMapViewModel(get(), p.getOrNull<String>(), p.getOrNull<Boolean>() ?: false) }
    factory { p -> com.djmetry.ui.booking.BookingViewModel(p.getOrNull()) }
}
