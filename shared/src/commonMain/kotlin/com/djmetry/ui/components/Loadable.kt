package com.djmetry.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors

/**
 * Загрузка справочника/списка для экрана: значение, «не удалось» и повтор.
 * Сбой — не пустой список: человек видит «Ошибка · Повторить», а не «ничего нет».
 */
@Stable
class Loadable<T> internal constructor(private val retryAction: () -> Unit) {
    var value by mutableStateOf<T?>(null)
        internal set
    var failed by mutableStateOf(false)
        internal set
    val loading: Boolean get() = value == null && !failed
    fun retry() = retryAction()
}

/** Разбор результата загрузки — отдельно от Compose, чтобы проверять тестами. */
internal fun <T> Loadable<T>.apply(result: Result<T>) {
    result.onSuccess { value = it; failed = false }.onFailure { failed = true }
}

/**
 * Грузит [block] при входе и при смене [keys]; [Loadable.retry] — ещё раз.
 * Прежнее значение при повторе не стирается: ошибка повтора не прячет то, что уже было на экране.
 */
@Composable
fun <T> rememberLoadable(vararg keys: Any?, block: suspend () -> Result<T>): Loadable<T> {
    var attempt by remember { mutableIntStateOf(0) }
    val state = remember(*keys) { Loadable<T> { attempt++ } }
    LaunchedEffect(state, attempt) {
        state.failed = false
        state.apply(block())
    }
    return state
}

/** «Ошибка · Повторить» — одна строка, как в постраничных списках. */
@Composable
fun LoadFailedRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    Text(
        "${i18n.t(Strings.HOME_ERROR)} · ${i18n.t(Strings.HOME_RETRY)}", color = DJMetryColors.Accent, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onRetry).padding(14.dp),
    )
}
