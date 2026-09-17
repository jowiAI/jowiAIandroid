package ai.workis.jowi.data

import kotlinx.serialization.json.JsonObject

/**
 * Where the user is, for Jowi (Django `WORKIS_PARTNER_API` "Ask Jowi — where
 * the user is", 2026-09-17). Jowi lives in its own tab, so the screens Jowi
 * knows write themselves here on appear — the screen id, what is open on it,
 * and the labels the APP draws — and the Jowi tab sends the last snapshot
 * with every question. Tab roots clear it, so a stale screen never rides
 * along. Nothing is read from a URL.
 */
object ScreenContext {
    data class Snapshot(
        /** seller.products · seller.product_preview · seller.product · seller.settings · seller.company */
        val screen: String,
        /** ≤ 8 short keys: {tab} on products, {product, brand, notices[], web[]} on a product. */
        val state: JsonObject?,
        /** ≤ 60 labels, as drawn. */
        val visible: List<String>,
        /** Concierge: the seller whose catalogue is open (console seats). */
        val seller: String? = null,
    )

    @Volatile
    var current: Snapshot? = null
        private set

    fun set(screen: String, state: JsonObject? = null, visible: List<String>, seller: String? = null) {
        val labels = visible.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(60)
        current = Snapshot(screen, state?.takeIf { it.isNotEmpty() }, labels, seller?.takeIf { it.isNotEmpty() })
    }

    fun clear() { current = null }
}
