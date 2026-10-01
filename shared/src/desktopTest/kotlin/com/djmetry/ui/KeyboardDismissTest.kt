package com.djmetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.djmetry.EdtScene
import com.djmetry.ui.components.DismissKeyboardOnTap
import com.djmetry.ui.components.isDismissTap
import com.djmetry.ui.components.textInput
import java.io.File
import kotlin.test.*

/** Тап мимо поля ввода прячет клавиатуру (снимает фокус); тап в поле и прокрутка — нет. */
class KeyboardDismissTest {
    // Сценой десктопа это не проверить: Compose Desktop сам снимает фокус по клику мимо поля (и с обёрткой,
    // и без неё), а на iOS/Android — нет, ради них обёртка и сделана. Поэтому проверяем решение и области полей.

    @Test
    fun dragIsNotATap() {
        assertFalse(isDismissTap(Offset(0f, 0f), Offset(0f, 80f), slop = 18f, insideField = false), "прокрутка не прячет клавиатуру")
        assertTrue(isDismissTap(Offset(0f, 0f), Offset(3f, 2f), slop = 18f, insideField = false))
        assertFalse(isDismissTap(Offset(0f, 0f), Offset(0f, 0f), slop = 18f, insideField = true))
    }

    /** Сторож: каждое поле ввода экранов помечено `textInput()` — иначе тап по нему прятал бы клавиатуру. */
    @Test
    fun everyTextFieldIsRegistered() {
        val missing = File("src/commonMain/kotlin/com/djmetry/ui").walk().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            Regex("""(BasicTextField|OutlinedTextField)\(""").findAll(text).mapNotNull { m ->
                // Вызов поля — до закрывающей скобки верхнего уровня
                var depth = 0; var i = m.range.last
                while (i < text.length) { if (text[i] == '(') depth++ else if (text[i] == ')') { depth--; if (depth == 0) break }; i++ }
                val call = text.substring(m.range.first, i + 1)
                if ("textInput()" in call) null else "${f.name}: ${call.lineSequence().first().trim()}"
            }
        }.toList()
        assertTrue(missing.isEmpty(), "поля без textInput():\n" + missing.joinToString("\n"))
    }
}
