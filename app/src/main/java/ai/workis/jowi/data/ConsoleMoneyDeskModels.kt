package ai.workis.jowi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// Ledger 128 — the three money desks of the console: Bölge K/Z (`console/region-pnl/`),
// Abonelikler (`console/subscriptions/`), Pazar (`console/market/`). Shapes from Django
// `workis/services/console_desks.py`; money here is a JSON NUMBER (→ WorkisMoney.text()).
// Every field nullable: a `dormant: true` answer comes nearly empty.

/** A value the server may send as text or as a number (`qty`, `vatPct`) — printed as it came, never computed with. */
object MktLooseText : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MktLooseText", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val p = json.decodeJsonElement() as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (p.isString) return p.content
        return p.content.toDoubleOrNull()?.let { WorkisMoney.qty(it) } ?: p.content
    }
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** The writing doors' answer: `{success, message?, error?}` (+ `id` on a cost, `number` on a paid invoice). */
@Serializable
data class MoneyDeskResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    @Serializable(with = FlexString::class) val id: String? = null,
    @Serializable(with = FlexString::class) val number: String? = null,
)

// ── Bölge K/Z ────────────────────────────────────────────────────────────────────────────

@Serializable
data class ConsoleRegionPnl(
    val dormant: Boolean? = null,
    val canWrite: Boolean? = null,
    val isLead: Boolean? = null,
    /** The open month, first day ("2026-11-01"). */
    val period: String? = null,
    val params: PnlParams? = null,
    val costKinds: List<String>? = null,
    /** What every kind is called (a line also carries its own `kindLabel`). */
    val kindLabels: Map<String, String>? = null,
    val regions: List<PnlRegion>? = null,
)

/** The pool parameters — a raw record, snake_case keys. */
@Serializable
data class PnlParams(
    @SerialName("pool_share") @Serializable(with = FlexDouble::class) val poolShare: Double? = null,
    @SerialName("lead_share") @Serializable(with = FlexDouble::class) val leadShare: Double? = null,
    @SerialName("attr_hq") @Serializable(with = FlexDouble::class) val attrHq: Double? = null,
)

@Serializable
data class PnlRegion(
    @Serializable(with = FlexString::class) val id: String? = null,
    val region: String? = null,
    val companyName: String? = null,
    val affiliations: List<String>? = null,
    @Serializable(with = FlexDouble::class) val revenue: Double? = null,
    @Serializable(with = FlexDouble::class) val cost: Double? = null,
    @Serializable(with = FlexDouble::class) val net: Double? = null,
    val soFar: PnlSoFar? = null,
    val entries: List<PnlEntry>? = null,
    val assets: List<PnlAsset>? = null,
    val closes: List<PnlClose>? = null,
)

@Serializable
data class PnlSoFar(
    @Serializable(with = FlexDouble::class) val net: Double? = null,
    @Serializable(with = FlexDouble::class) val carryIn: Double? = null,
    @Serializable(with = FlexDouble::class) val distributable: Double? = null,
    @Serializable(with = FlexDouble::class) val carryOut: Double? = null,
    @Serializable(with = FlexDouble::class) val leadShare: Double? = null,
    @Serializable(with = FlexDouble::class) val platformShare: Double? = null,
    @Serializable(with = FlexDouble::class) val leadRate: Double? = null,
)

/** One line — the same shape in the open month and in every closed month. */
@Serializable
data class PnlEntry(
    @Serializable(with = FlexString::class) val id: String? = null,
    val period: String? = null,
    val kind: String? = null,
    val kindLabel: String? = null,
    @Serializable(with = FlexBool::class) val isCost: Boolean? = null,
    @Serializable(with = FlexDouble::class) val amount: Double? = null,
    val currency: String? = null,
    @Serializable(with = FlexString::class) val refId: String? = null,
    val note: String? = null,
    val firm: String? = null,
    val why: PnlWhy? = null,
)

/** The server's sentences of how the amount was reached — printed whole, never rebuilt from the numbers. */
@Serializable
data class PnlWhy(
    val words: String? = null,
    val legs: String? = null,
)

@Serializable
data class PnlAsset(
    @Serializable(with = FlexString::class) val id: String? = null,
    val kind: String? = null,
    val name: String? = null,
    @Serializable(with = FlexDouble::class) val capex: Double? = null,
    @Serializable(with = FlexInt::class) val months: Int? = null,
    val start: String? = null,
)

@Serializable
data class PnlClose(
    @Serializable(with = FlexString::class) val id: String? = null,
    val period: String? = null,
    val status: String? = null,
    val totals: PnlTotals? = null,
    val entries: List<PnlEntry>? = null,
)

/** The frozen statement as recorded — snake_case keys. */
@Serializable
data class PnlTotals(
    @Serializable(with = FlexDouble::class) val revenue: Double? = null,
    @Serializable(with = FlexDouble::class) val costs: Double? = null,
    @Serializable(with = FlexDouble::class) val net: Double? = null,
    @SerialName("carry_in") @Serializable(with = FlexDouble::class) val carryIn: Double? = null,
    @Serializable(with = FlexDouble::class) val distributable: Double? = null,
    @SerialName("carry_out") @Serializable(with = FlexDouble::class) val carryOut: Double? = null,
    @SerialName("lead_share") @Serializable(with = FlexDouble::class) val leadShare: Double? = null,
    @SerialName("platform_share") @Serializable(with = FlexDouble::class) val platformShare: Double? = null,
    val params: PnlParams? = null,
)

// ── Abonelikler ──────────────────────────────────────────────────────────────────────────

@Serializable
data class ConsoleSubscriptions(
    val dormant: Boolean? = null,
    @Serializable(with = FlexDouble::class) val mrr: Double? = null,
    @Serializable(with = FlexDouble::class) val openTotal: Double? = null,
    @Serializable(with = FlexInt::class) val overdue: Int? = null,
    val today: String? = null,
    /** "20" today — a string; a number is read too. */
    @Serializable(with = MktLooseText::class) val vatPct: String? = null,
    val rows: List<SubsRow>? = null,
)

@Serializable
data class SubsRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val firm: String? = null,
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    @Serializable(with = FlexDouble::class) val monthlyFee: Double? = null,
    val currency: String? = null,
    val status: String? = null,
    val started: String? = null,
    val invoices: List<SubsInvoice>? = null,
)

@Serializable
data class SubsInvoice(
    @Serializable(with = FlexString::class) val id: String? = null,
    val period: String? = null,
    @Serializable(with = FlexString::class) val number: String? = null,
    @Serializable(with = FlexDouble::class) val amount: Double? = null,
    @Serializable(with = FlexDouble::class) val vatRate: Double? = null,
    @Serializable(with = FlexDouble::class) val vatAmount: Double? = null,
    @Serializable(with = FlexDouble::class) val total: Double? = null,
    val currency: String? = null,
    val dueDate: String? = null,
    val status: String? = null,
    val paidAt: String? = null,
    val paidRef: String? = null,
    @Serializable(with = FlexBool::class) val overdue: Boolean? = null,
)

// ── Pazar ────────────────────────────────────────────────────────────────────────────────

@Serializable
data class ConsoleMarket(
    val dormant: Boolean? = null,
    val stats: MktStats? = null,
    val rfqs: List<MktRfq>? = null,
    val accounts: List<MktAccount>? = null,
    val autonomy: List<MktAutonomy>? = null,
)

@Serializable
data class MktStats(
    @Serializable(with = FlexInt::class) val cases: Int? = null,
    @Serializable(with = FlexInt::class) val rfqsOpen: Int? = null,
    @Serializable(with = FlexInt::class) val rfqsAwarded: Int? = null,
    @Serializable(with = FlexInt::class) val quotes: Int? = null,
    @Serializable(with = FlexInt::class) val orders: Int? = null,
    @Serializable(with = FlexInt::class) val invoices: Int? = null,
    /** One bare number — no currency split on the server; printed bare, like the web. */
    @Serializable(with = FlexDouble::class) val held: Double? = null,
    @Serializable(with = FlexDouble::class) val released: Double? = null,
)

@Serializable
data class MktRfq(
    val title: String? = null,
    val status: String? = null,
    val statusWords: String? = null,
    val buyer: String? = null,
    @Serializable(with = MktLooseText::class) val qty: String? = null,
    val createdAt: String? = null,
    @Serializable(with = FlexInt::class) val invited: Int? = null,
    @Serializable(with = FlexInt::class) val quoted: Int? = null,
    /** A ready string ("39,855.00 TRY") on a concluded request only — printed as sent. */
    val orderTotal: String? = null,
    val escrow: String? = null,
)

@Serializable
data class MktAccount(
    val buyer: String? = null,
    val seller: String? = null,
    val currency: String? = null,
    @Serializable(with = FlexDouble::class) val balance: Double? = null,
    @Serializable(with = FlexInt::class) val entries: Int? = null,
    val updatedAt: String? = null,
)

@Serializable
data class MktAutonomy(
    val at: String? = null,
    val text: String? = null,
    val company: String? = null,
)
