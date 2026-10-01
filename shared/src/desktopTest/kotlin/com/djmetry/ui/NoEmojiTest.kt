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
        val shared = File("src")
        assertTrue(shared.exists(), "тест запускается из модуля shared")
        // Всё приложение: общий код, Android, десктоп, iOS (Swift) и скрипты сборки
        val roots = listOf(shared, File("../androidApp/src"), File("../desktopApp/src"), File("../iosApp"), File(".."))
        val exts = setOf("kt", "swift", "xml", "sh")
        val found = roots.filter { it.exists() }.flatMap { r ->
            if (r.path == "..") r.listFiles().orEmpty().filter { it.isFile && it.extension in exts }.asSequence()
            else r.walk().filter { it.isFile && it.extension in exts && "/build/" !in it.path }
        }.distinct().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*") || code.startsWith("<!--") || code.startsWith("#")) return@mapIndexedNotNull null
                // appendInlineContent(…, "✓") — служебный альтернативный текст встроенной печати, не рисуется
                if ("appendInlineContent" in line) return@mapIndexedNotNull null
                val bad = code.codePoints().toArray().filter(::isEmoji)
                if (bad.isEmpty()) null else "${f.path}:${i + 1}: ${bad.joinToString(" ") { String(Character.toChars(it)) }}"
            }
        }
        assertTrue(found.isEmpty(), "эмодзи в коде (используйте иконки Material):\n" + found.joinToString("\n"))
    }

    /**
     * Стрелки-символы вместо иконок (↑ ↓ › ‹) в строках интерфейса: у них нет подписи для экранного диктора
     * и они выглядят по-разному на платформах. Только код экранов; подсказки клавиш (← ↑ →) в переводах разрешены.
     */
    @Test
    fun noArrowGlyphsInScreens() {
        val arrows = setOf(0x2191, 0x2193, 0x203A, 0x2039)
        val found = File("src/commonMain/kotlin/com/djmetry/ui").walk().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                val literals = Regex(""""([^"\\]|\\.)*"""").findAll(code.substringBefore(" //")).joinToString("") { it.value }
                if (literals.codePoints().anyMatch { it in arrows }) "${f.path}:${i + 1}" else null
            }
        }.toList()
        assertTrue(found.isEmpty(), "символы-стрелки вместо иконок:\n" + found.joinToString("\n"))
    }

    /** RULES §3: однострочная подпись — с многоточием (крупный шрифт иначе режет текст без признака обрезки). */
    @Test
    fun singleLineTextHasEllipsis() {
        val found = File("src/commonMain/kotlin/com/djmetry/ui").walk().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val l = line.replace("AutoSizeText(", "")
                if (Regex("(^|[^A-Za-z])Text\\(").containsMatchIn(l) && Regex("maxLines = 1(?![0-9])").containsMatchIn(l) && "overflow" !in l) "${f.path}:${i + 1}" else null
            }
        }.toList()
        assertTrue(found.isEmpty(), "maxLines = 1 без overflow = TextOverflow.Ellipsis:\n" + found.joinToString("\n"))
    }
}
