package com.twt.visitor.sdk.internal

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Tracks SDK-created containers so cleanup is not defeated by pages that keep writing site data. */
internal class VisitorSessionRegistry {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val lock = Any()
    val hasActiveSessions: Boolean get() = counts.values.any { it.get() > 0 }

    /** Registers a container per host so unrelated sessions do not block cleanup of other custom domains. */
    fun acquire(host: String = ""): Token {
        val key = normalize(host)
        synchronized(lock) {
            val count = counts[key] ?: AtomicInteger(0).also { counts[key] = it }
            count.incrementAndGet()
        }
        return Token(key)
    }

    fun hasActiveSessionsFor(host: String): Boolean {
        val key = normalize(host)
        return counts.any { (activeHost, count) ->
            count.get() > 0 && sameSiteBoundary(activeHost, key)
        }
    }

    inner class Token(private val host: String) {
        private val released = AtomicBoolean(false)
        fun release() {
            if (!released.compareAndSet(false, true)) return
            synchronized(lock) {
                counts[host]?.let { count ->
                    while (true) {
                        val current = count.get()
                        if (current == 0) break
                        if (count.compareAndSet(current, current - 1)) break
                    }
                    if (count.get() == 0) counts.remove(host, count)
                }
            }
        }
    }

    private fun normalize(host: String): String = host.trim().lowercase().trim('.').ifEmpty { "" }

    private fun sameSiteBoundary(first: String, second: String): Boolean {
        if (first.isEmpty() || second.isEmpty()) return first == second
        return first == second || first.endsWith(".$second") || second.endsWith(".$first")
    }
}

/** Process-wide shared registry; kept in the internal package so the public facade never leaks internal types. */
internal object VisitorSessionRegistryHolder {
    val shared = VisitorSessionRegistry()
}
