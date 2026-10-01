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

    // Новое — только через Koin
    single { ArtistSearchRepository(get()) }
    // ViewModel — factory: экземпляр хранит appViewModel(), Koin только собирает
    factory { SearchViewModel(get(), get()) }
}
