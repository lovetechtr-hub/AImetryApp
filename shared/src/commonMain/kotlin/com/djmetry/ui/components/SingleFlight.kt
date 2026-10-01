package com.djmetry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Одно действие за раз: второй тап, пока первое в сети, ничего не делает. Флаг ставится сразу, до запуска
 * корутины (а не внутри неё), и снимается в finally — даже если экран закрыли посреди запроса.
 */
@Stable
class SingleFlight {
    var busy by mutableStateOf(false)
        private set

    fun run(scope: CoroutineScope, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { block() } finally { busy = false } }
    }
}

@Composable
fun rememberSingleFlight(): SingleFlight = remember { SingleFlight() }
