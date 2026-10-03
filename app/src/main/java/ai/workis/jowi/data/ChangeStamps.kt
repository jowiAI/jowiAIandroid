package ai.workis.jowi.data

import ai.workis.jowi.Graph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

/** `GET changes/` (2026-10-03): the newest change per scope, ISO or "" — compared, never parsed. */
@Serializable
data class ChangeStampsReply(val success: Boolean? = null, val stamps: Map<String, String?>? = null)

/**
 * The partner's change stamps (`GET changes/`, API_CONTRACT "The list row chooses its
 * VARIANT… · GET changes/"): `orders` = orders, terms, contract, payments, invoices,
 * shipments, the order yazışma · `quotes` = invitations, quotes, the quote-stage yazışma ·
 * `cargo` = own shipments + open jobs · `lists` = purchase lists.
 *
 * ONE reading for the whole app: a screen asks [tick] on appear and every 20 s while it is
 * in the foreground ([ai.workis.jowi.ui.components.ChangesWatch]); the scopes whose stamp
 * moved since the previous reading come back and the reading is replaced. A push names
 * scopes the same way ([pushed]); the push never carries the record. Signing out forgets
 * the reading ([reset]) so the next account's first tick is a baseline, not a change.
 */
object ChangeStamps {
    private var last: Map<String, String?>? = null

    /** `scopes[]` of a push — the watchers on screen re-read as if the stamp had moved. */
    val pushes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 4)

    /** The scopes that moved since the last reading; empty on the first reading or when the call fails. */
    suspend fun tick(): Set<String> {
        val r = runCatching { Graph.api.changes() }.getOrNull() ?: return emptySet()
        val now = r.stamps ?: return emptySet()
        val before = last
        last = now
        if (before == null) return emptySet()
        return now.keys.filterTo(HashSet()) { k -> (now[k] ?: "") != (before[k] ?: "") }
    }

    fun pushed(scopes: Collection<String>) {
        if (scopes.isNotEmpty()) pushes.tryEmit(scopes.toSet())
    }

    fun reset() { last = null }
}
