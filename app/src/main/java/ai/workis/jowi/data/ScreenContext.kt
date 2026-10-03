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

    /**
     * Which PAGE the asker came from — `lists` · `quotes` · `orders`; the server
     * picks its snapshot by it. It was hard-coded to "lists", so a seller asking
     * from Teklifler was answered from the buyer's lists (live run 2026-09-19).
     */
    @Volatile
    var page: String? = null
        private set

    /**
     * The purchase list open on screen, if any — sent as `ctxList` when the
     * asker keeps the scope on that list, so "bu listede kaç kalem var?" is
     * answered from THAT list (the web's list page does the same).
     */
    @Volatile
    var openListId: String? = null
        private set
    @Volatile
    var openListTitle: String? = null
        private set
    /** The open list is still a DRAFT — Jowi's picks may join it (`listId`, 2026-10-02). */
    @Volatile
    var openListDraft: Boolean = false
        private set

    /** A page without a list open: Listeler, Teklifler, Siparişler. */
    fun setPage(page: String) { this.page = page; openListId = null; openListTitle = null; openListDraft = false }

    /** The list detail: its page is `lists` and the list rides as the scope. */
    fun openList(id: String, title: String?, draft: Boolean = false) {
        page = "lists"; openListId = id; openListTitle = title?.takeIf { it.isNotEmpty() }; openListDraft = draft
    }

    fun set(screen: String, state: JsonObject? = null, visible: List<VisibleItem>, seller: String? = null) {
        val labels = visible.map { it.copy(label = it.label.trim()) }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.label }
            .take(60)
        current = Snapshot(screen, state?.takeIf { it.isNotEmpty() }, labels, seller?.takeIf { it.isNotEmpty() })
    }

    fun clear() { current = null; page = null; openListId = null; openListTitle = null; openListDraft = false }
}
