package com.djmetry.ui.search

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.djmetry.LocalAppContainer
import org.koin.core.parameter.ParametersDefinition

/**
 * ViewModel экрана: создаёт Koin контейнера (`factory { … }` в [com.djmetry.di.appModule]), хранит — хранилище
 * ViewModel контейнера. Оно живёт с процессом (на Android контейнер в Application — поворот и смена темы не
 * сбрасывают экран, смена вкладки тоже) и очищается при выходе из аккаунта. [key] — несколько экземпляров
 * одного класса (например, по id артиста).
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, noinline parameters: ParametersDefinition? = null): VM {
    val container = LocalAppContainer.current
    return viewModel(
        viewModelStoreOwner = container.viewModels,
        key = key ?: VM::class.qualifiedName,
        factory = viewModelFactory { initializer { container.koin.koin.get<VM>(parameters = parameters) } },
    )
}
