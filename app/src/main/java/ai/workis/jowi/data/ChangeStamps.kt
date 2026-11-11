package ai.workis.jowi.data

import ai.workis.jowi.Graph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** `GET changes/` (2026-10-03): the newest change per scope, ISO or "" — compared, never parsed. */
@Serializable
data class ChangeStampsReply(val success: Boolean? = null, val stamps: Map<String, String?>? = null)

/**
 * The partner's change stamps (`GET changes/`, API_CONTRACT "The list row chooses its
 * VARIANT… · GET changes/"): `orders` = orders, terms, contract, payments, invoices,
 * shipments, the order yazışma · `quotes` = invitations, quotes, the quote-stage yazışma ·
 * `cargo` = own shipments + open jobs · `lists` = purchase lists (and their rows) ·
 * `catalog` = the seller's catalogue.
 *
 * ONE call serves every watcher on screen ([read], shared for a few seconds), but EACH
 * watcher compares the stamps of ITS scopes with what IT saw last
 * ([ai.workis.jowi.ui.components.ChangesWatch]). The reading used to be compared once for
 * the whole app: the first watcher to tick took the difference and replaced the reading, so
 * a watcher of another scope ticking a moment later saw nothing — with two watchers on a
 * screen (or the Panel's four under an open page) a change was simply lost. A push names
 * scopes the same way ([pushed]); the push never carries the record. Signing out forgets
 * the shared reading ([reset]).
 */
object ChangeStamps {
    private var cached: Map<String, String?>? = null
    private var cachedAt = 0L
    private val gate = Mutex()

    /** `scopes[]` of a push — the watchers on screen re-read as if the stamp had moved. */
    val pushes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 4)

    /** The stamps as the server has them now (a reading younger than 5 s is shared); null when the call fails. */
    suspend fun read(): Map<String, String?>? = gate.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        cached?.takeIf { now - cachedAt < 5_000 }?.let { return@withLock it }
        val stamps = runCatching { Graph.api.changes() }.getOrNull()?.stamps ?: return@withLock null
        cached = stamps
        cachedAt = now
        stamps
    }

    fun pushed(scopes: Collection<String>) {
        if (scopes.isNotEmpty()) pushes.tryEmit(scopes.toSet())
    }

    fun reset() { cached = null }
}
