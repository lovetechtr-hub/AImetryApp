package com.djmetry.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 * Хранилище мелких полей экрана во ViewModel: открытая страница, черновики форм, выбранный раздел.
 * Как `rememberSaveable`, но переживает и смену вкладки, и поворот. Очищается вместе с ViewModel при выходе.
 */
class ScreenStateViewModel : ViewModel() {
    private class Entry(val inputs: List<Any?>, val state: MutableState<Any?>)
    private val entries = mutableMapOf<String, Entry>()

    /** Состояние поля [key]; сменились [inputs] (например, сохранённое значение с сервера) — заново из [init]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> state(key: String, inputs: List<Any?>, init: () -> T): MutableState<T> {
        val e = entries[key]
        if (e != null && e.inputs == inputs) return e.state as MutableState<T>
        return mutableStateOf<Any?>(init()).also { entries[key] = Entry(inputs, it) } as MutableState<T>
    }
}

/**
 * Поле экрана [screen] с ключом [key], которое живёт во ViewModel. [inputs] — как ключи `remember(...)`:
 * при их смене значение начинается заново из [init].
 */
@Composable
fun <T> rememberScreenState(screen: String, key: String, vararg inputs: Any?, init: () -> T): MutableState<T> =
    appViewModel<ScreenStateViewModel>(key = "screen:$screen").state(key, inputs.toList(), init)
