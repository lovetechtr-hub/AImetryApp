package com.djmetry.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Кэш ответов со сроком жизни и потолком размера — один на все репозитории.
 * Просроченное не отдаётся и выкидывается при записи; при переполнении уходит самое старое.
 * Сохраняется только успех: ошибка (нет сети) не «застревает» в кэше.
 */
class TtlCache<K : Any, V : Any>(
    private val ttl: Duration,
    private val maxEntries: Int,
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val lock = Mutex()
    private val entries = mutableMapOf<K, Pair<Instant, V>>()

    suspend fun get(key: K): V? {
        val now = clock()
        return lock.withLock { entries[key]?.takeIf { now - it.first < ttl }?.second }
    }

    suspend fun put(key: K, value: V) {
        val now = clock()
        lock.withLock {
            entries.entries.removeAll { now - it.value.first >= ttl }
            while (entries.size >= maxEntries) entries.remove(entries.minBy { it.value.first }.key)
            entries[key] = now to value
        }
    }

    /** Свежее из кэша, иначе [load]; успех запоминается. [refresh] — мимо кэша. */
    suspend fun getOrLoad(key: K, refresh: Boolean = false, load: suspend () -> Result<V>): Result<V> {
        if (!refresh) get(key)?.let { return Result.success(it) }
        return load().onSuccess { put(key, it) }
    }

    suspend fun remove(key: K) { lock.withLock { entries.remove(key) } }

    suspend fun clear() = lock.withLock { entries.clear() }

    suspend fun size(): Int = lock.withLock { entries.size }
}
