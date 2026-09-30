package com.djmetry.data.local

import kotlin.time.Clock

/** Вкладка и открытая карточка артиста — что восстановить после перезапуска. */
data class NavState(val tab: String, val artistId: String?)

/**
 * Память навигации. iOS выгружает приложение из фона (например, пока пользователь в Instagram) — при возврате
 * оно стартует заново. Если с ухода прошло меньше [MAX_AGE_MS], возвращаем на ту же вкладку и карточку без сплэша.
 */
object NavMemory {
    const val MAX_AGE_MS = 30 * 60 * 1000L
    private var storage: SessionStorage? = null
    private var current: NavState? = null

    fun attach(s: SessionStorage) { storage = s }

    /** Экран сменился — запоминаем сразу (Android может убить процесс без предупреждения). */
    fun update(state: NavState) { current = state; persist() }

    /** Уход в фон — обновляем время, чтобы отсчёт шёл от него. */
    fun persist() { current?.let { storage?.saveNavState(encode(Clock.System.now().toEpochMilliseconds(), it)) } }

    fun forget() { current = null; storage?.saveNavState(null) }

    /** Восстановить, если сохранено недавно. */
    fun restore(): NavState? = decode(storage?.getNavState(), Clock.System.now().toEpochMilliseconds())

    fun encode(now: Long, s: NavState): String = "$now|${s.tab}|${s.artistId.orEmpty()}"

    fun decode(raw: String?, now: Long): NavState? {
        val p = raw?.split('|')?.takeIf { it.size == 3 } ?: return null
        val at = p[0].toLongOrNull() ?: return null
        if (now - at !in 0..MAX_AGE_MS || p[1].isBlank()) return null
        return NavState(p[1], p[2].ifBlank { null })
    }
}
