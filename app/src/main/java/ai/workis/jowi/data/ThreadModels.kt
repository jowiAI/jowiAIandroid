package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The order yazışma's three typed term cards (Django `order_terms.choices_for`,
 * 2026-10-02): the current choice, the options with their price, and the last
 * day the goods can be handed to cargo for the agreed date. Every fee and every
 * day is the server's — the app never computes one. Dates are ISO strings;
 * money is a JSON NUMBER (→ `WorkisMoney.text()`). Null for a carrier.
 */
@Serializable
data class TermChoices(
    val deliveryDate: TermChoiceDate? = null,
    val cargo: TermChoiceCargo? = null,
    val payment: TermChoicePayment? = null,
)

@Serializable
data class TermChoiceDate(
    val date: String? = null,
    /** The first day the picker offers — "ertesi gün" cannot arrive today. */
    val earliest: String? = null,
    val cargoMode: String? = null,
    val cargoLevel: String? = null,
    val cargoLevelLabel: String? = null,
    /** Holds for the date in force only. */
    val handover: String? = null,
    val handoverOk: Boolean? = null,
)

/** One cargo level: the Workis tariff (USD) and its last hand-over day. */
@Serializable
data class TermChoiceLevel(
    val code: String? = null,
    val label: String? = null,
    @Serializable(with = FlexDouble::class) val fee: Double? = null,
    val handover: String? = null,
    /** false = that day has passed. */
    val handoverOk: Boolean? = null,
)

@Serializable
data class TermChoiceCargo(
    val mode: String? = null, // workis_kargo | satici_teslim
    val level: String? = null,
    val payer: String? = null, // buyer | seller
    val currency: String? = null,
    val deliveryDate: String? = null,
    val levels: List<TermChoiceLevel>? = null,
)

@Serializable
data class TermChoicePayment(
    val mode: String? = null, // escrow | vade
    @Serializable(with = FlexDouble::class) val days: Double? = null,
    @Serializable(with = FlexDouble::class) val total: Double? = null,
    val currency: String? = null,
)

/**
 * A term card's stored `value` (camelCase): its shape differs per key — cargo
 * `{mode, serviceLevel, payer, fee, currency}`, delivery_date `{date}`, payment
 * `{mode, days}`, text terms `{text}`, `{}` for a carrier. A choice sheet
 * pre-fills from it; `display` is never parsed.
 */
fun OrderTerm.valueText(field: String): String? =
    ((value as? JsonObject)?.get(field) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

fun OrderTerm.valueInt(field: String): Int? = valueText(field)?.toDoubleOrNull()?.toInt()
