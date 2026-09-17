package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One control the screen draws. A bare label borrows the look of the same
 * label on the web screen map; a declared `style` wins over it, which is how
 * an app screen that differs from the web says so (Django fix 2026-09-17 —
 * without the look, "the orange button at the top right" matched nothing).
 * Unknown styles are dropped server-side, never guessed.
 */
@Serializable
data class VisibleItem(val label: String, val style: String? = null, val icon: String? = null)

/** primary · secondary · glass · outline · destructive · chip — the six the API takes. */
object VisStyle {
    const val PRIMARY = "primary"
    const val SECONDARY = "secondary"
    const val GLASS = "glass"
    const val OUTLINE = "outline"
    const val DESTRUCTIVE = "destructive"
    const val CHIP = "chip"
}

/** A bare label: the web screen map supplies the look. */
fun vis(label: String) = VisibleItem(label)

/** A label whose look on this screen is ours to declare. */
fun vis(label: String, style: String) = VisibleItem(label, style)

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
        /** ≤ 60 controls, as drawn — label plus the look where it differs from the web. */
        val visible: List<VisibleItem>,
        /** Concierge: the seller whose catalogue is open (console seats). */
        val seller: String? = null,
    )

    @Volatile
    var current: Snapshot? = null
        private set

    fun set(screen: String, state: JsonObject? = null, visible: List<VisibleItem>, seller: String? = null) {
        val labels = visible.map { it.copy(label = it.label.trim()) }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.label }
            .take(60)
        current = Snapshot(screen, state?.takeIf { it.isNotEmpty() }, labels, seller?.takeIf { it.isNotEmpty() })
    }

    fun clear() { current = null }
}
