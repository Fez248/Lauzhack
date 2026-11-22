package tea.server.vault

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import tea.server.password.PwnedClient

/**
 * Remembers HIBP answers for an hour so a demo room full of learners reaching for the same obvious
 * password costs one request, not thirty.
 *
 * Keyed by the full SHA-1 the caller passes, which never leaves this process — only its first five
 * characters go to HIBP, inside [tea.server.password.HibpClient]. Unavailable answers (null) are not
 * cached, so the next attempt tries again.
 */
class CachingPwnedClient(
    private val delegate: PwnedClient,
    private val clock: Clock,
    private val ttl: Duration = 1.hours,
    private val maxEntries: Int = 10_000,
) : PwnedClient {

    private data class Entry(val count: Int, val at: Instant)

    private val cache = ConcurrentHashMap<String, Entry>()

    override suspend fun pwnedCount(sha1Hex: String): Int? {
        val key = sha1Hex.uppercase()
        val now = clock.now()
        cache[key]?.let { if (now - it.at < ttl) return it.count else cache.remove(key) }
        val fresh = delegate.pwnedCount(sha1Hex) ?: return null
        if (cache.size >= maxEntries) cache.clear()
        cache[key] = Entry(fresh, now)
        return fresh
    }
}
