package ai.workis.jowi.ui.components

import java.util.Locale

/**
 * A product name printed ALL CAPS by its catalogue ("TÜPSÜZ MODEL & TEK KAPAKLI 70X70X23")
 * reads as a bigger font than its neighbour "Islak Alarm Vanası" at the very same size
 * (ledger 76, 2026-10-28). Django folds such headings to Turkish title case at ingest
 * (`catalog_ingest.title_words`); this is the same rule, word by word, for display (iOS
 * `WorkisTitleCase`): a code or a model number (digits, hyphens, no vowel — "70X70X23",
 * "DN25", "RCW-S", "NPT") stays as printed, "ve / ile / için / veya" stay small after the
 * first word, a Latin catalogue word keeps its i ("Sprinkler"), the rest take Turkish
 * casing (I → ı, İ → i). A name that is not all capitals is returned untouched — the
 * seller's own casing is the record. Buyer-typed text is never passed through here.
 */
object WorkisTitleCase {
    private val vowels = "aeıioöuüAEIİOÖUÜ".toSet()
    private val small = setOf("ve", "ile", "için", "veya")
    private val latinI = setOf(
        "sprinkler", "sprinklers", "upright", "sidewall", "pendent", "fitting", "fittings", "switch",
        "grid", "pipe", "mini", "micro", "link", "fire", "viking", "kit", "kits", "lift", "drive",
        "rigid", "flexible", "inlet", "outlet", "filter", "sight", "siamese", "spring", "ring", "rings",
        "liquid", "hydrant", "insert", "mirage", "wire", "light", "listed", "rising", "vinyl", "air",
        "line", "nipple", "union", "nitrogen", "mild", "inch", "tip", "tips", "bits", "bit", "disc",
        "disk", "drill", "extinguisher", "cabinet", "nozzle", "mist", "pilot", "hybrid",
    )
    private val tr = Locale.forLanguageTag("tr-TR")

    /** "family · variant" — the server splits a display name at the FIRST " · "; only the family is recased. */
    fun product(full: String): Pair<String, String?> {
        val at = full.indexOf(" · ")
        if (at < 0) return fromCaps(full) to null
        return fromCaps(full.substring(0, at)) to full.substring(at + 3)
    }

    /** [product] joined back into one line. */
    fun productLine(full: String): String {
        val (head, tail) = product(full)
        return if (tail == null) head else "$head · $tail"
    }

    fun fromCaps(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val t = words.joinToString(" ")
        if (t.length <= 3 || t != t.uppercase(tr)) return t
        return words.mapIndexed { i, w ->
            if (w.any(::isNumber) || '-' in w || w.none { it in vowels }) return@mapIndexed w
            val low = w.lowercase(tr)
            if (i > 0 && low in small) return@mapIndexed low
            val lat = low.replace('ı', 'i')
            if (lat in latinI) return@mapIndexed lat.replaceFirstChar { it.uppercase() }
            low.take(1).uppercase(tr) + low.drop(1)
        }.joinToString(" ")
    }

    private fun isNumber(c: Char): Boolean = c.isDigit() ||
        c.category == CharCategory.LETTER_NUMBER || c.category == CharCategory.OTHER_NUMBER
}
