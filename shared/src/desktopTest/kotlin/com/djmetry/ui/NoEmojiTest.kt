package com.djmetry.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Правило docs/RULES.md: в проекте нет эмодзи — только иконки Material. Проверяем все исходники и переводы
 * (кроме комментариев): эмодзи, флаги-регионы и символы-заменители иконок (✓ ✕ ▾ 🔎 …).
 */
class NoEmojiTest {
    private fun isEmoji(cp: Int) = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp == 0x25BE || cp == 0x25BC

    @Test
    fun noEmojiInSources() {
        val root = File("src/commonMain/kotlin")
        assertTrue(root.exists(), "тест запускается из модуля shared")
        val found = root.walk().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                // appendInlineContent(…, "✓") — служебный альтернативный текст встроенной печати, не рисуется
                if ("appendInlineContent" in line) return@mapIndexedNotNull null
                val bad = code.codePoints().toArray().filter(::isEmoji)
                if (bad.isEmpty()) null else "${f.path}:${i + 1}: ${bad.joinToString(" ") { String(Character.toChars(it)) }}"
            }
        }.toList()
        assertTrue(found.isEmpty(), "эмодзи в коде (используйте иконки Material):\n" + found.joinToString("\n"))
    }
}
