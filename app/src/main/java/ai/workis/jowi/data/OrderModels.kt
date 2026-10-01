package ai.workis.jowi.data

import kotlinx.serialization.Serializable

/**
 * Partner seat — slice 2: the order loop (Django `docs/WORKIS_PARTNER_API.md`
 * §5–§8, contract "Partner seat — slice 2"). Siparişler · the two threads ·
 * the order contract · Onaylar.
 *
 * MONEY ON THIS SURFACE IS A JSON NUMBER, not a decimal string.
 * The client derives no state and no wall: badges, counts and routing all
 * arrive decided — the app only draws them.
 */

@Serializable
data class OrderParty(val short: String? = null, val hover: String? = null, val city: String? = null)

@Serializable
data class OrderVLine(
    @Serializable(with = FlexString::class) val id: String? = null,
    val label: String? = null,
    val qty: Double? = null,
    val received: Boolean? = null,
)

@Serializable
data class OrderShipment(
    @Serializable(with = FlexString::class) val id: String? = null,
    val number: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val delivered: Boolean? = null,
    val promised: String? = null,
    val trackingNo: String? = null,
    val carrier: OrderParty? = null,
    /**
     * The 6-digit code the RECEIVER reads to the carrier at the door — the
     * server puts it on the receiver's card only; the app draws what arrives
     * and never derives it. Both spellings are read (2026-09-30).
     */
    @Serializable(with = FlexString::class) val deliveryCode: String? = null,
    @kotlinx.serialization.SerialName("delivery_code")
    @Serializable(with = FlexString::class) val deliveryCodeSnake: String? = null,
) {
    val code get() = listOfNotNull(deliveryCode, deliveryCodeSnake).firstOrNull { it.isNotEmpty() && it != "null" }
}

/** "7/7 koşul kesinleşti" — the server counts, the app prints. */
@Serializable
data class OrderTermsBadge(val ok: Int? = null, val total: Int? = null)

@Serializable
data class OrderContractBadge(val version: Int? = null, val status: String? = null) // draft | accepted

@Serializable
data class PartnerOrder(
    @Serializable(with = FlexString::class) val id: String? = null,
    val po: String? = null,
    val at: String? = null,
    val role: String? = null, // buyer | seller | carrier
    val buyer: OrderParty? = null,
    val seller: OrderParty? = null,
    val virtual: Boolean? = null,
    val vlines: List<OrderVLine>? = null,
    /** null on a carrier card (§7.1) — never draw a zero there. */
    val total: Double? = null,
    val currency: String? = null,
    val escrow: String? = null, // a display fact only — never what opens Blokla
    val escrowLabel: String? = null,
    /**
     * The server weighs the settled payment term: a deferred term runs on open
     * account and is never blocked. FAILS CLOSED — a missing flag is a shut door.
     */
    val canFund: Boolean? = null,
    val fundHint: String? = null, // the refusal in the server's words; "" when not worth a line
    val shipment: OrderShipment? = null,
    val myRating: Int? = null,
    val canReview: Boolean? = null,
    val unread: Int? = null,
    val terms: OrderTermsBadge? = null,
    val contract: OrderContractBadge? = null,
    @Serializable(with = FlexString::class) val listId: String? = null,
    @Serializable(with = FlexString::class) val invitationId: String? = null,
    // one order card, as the web's (2026-09-30)
    /** The PO PDF exists — `orders/{id}/po-pdf/`, token auth. */
    val poPdf: Boolean? = null,
    val nLines: Int? = null,
    val deliveryMode: String? = null, // workis_kargo | satici_teslim
    val serviceLevel: String? = null, // acil45 | ayni_gun | ertesi_gun
    val dispatchState: String? = null, // "" | satici_teslim | broadcast | accepted | fallback
    /** The buyer may pick the cargo level (and the carrier) until dispatch starts. */
    val canPickCargo: Boolean? = null,
    /**
     * The seller's "Kargoya teslim" as the server's call (requested 2026-09-30).
     * Absent on an older server — then the card derives it (the one exception).
     */
    val canReady: Boolean? = null,
    /** Why the goods may not leave yet — the server's sentence; "" when not worth a line. */
    val readyHint: String? = null,
) {
    val isCarrier get() = role == "carrier"

    /**
     * The flag wins when it arrives; until then role seller · workis_kargo ·
     * no dispatch state. The server still refuses a wrong call in its words.
     */
    val showsReady get() = canReady ?: (role == "seller" && deliveryMode == "workis_kargo" && dispatchState.isNullOrEmpty())
}

@Serializable
data class PartnerOrders(val orders: List<PartnerOrder>? = null)

// ---------- the dispatch doors (2026-09-30, "The missing doors, all at once")

@Serializable
data class OrderReadyBody(val readyDate: String? = null) // 'YYYY-MM-DD', ≤ 30 days

@Serializable
data class ServiceLevelBody(val level: String)

@Serializable
data class DispatchBody(val carrierId: String, val serviceLevel: String)

/**
 * `ready/` · `service-level/` · `dispatch/` replies. No `success` key on these —
 * a 2xx is the yes, `message` is the note, a refusal comes as `error`.
 */
@Serializable
data class OrderDoorResult(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val dispatchState: String? = null,
    val serviceLevel: String? = null,
    val number: String? = null,
)

/**
 * One carrier covering both districts (`order_seat.carrier_options`). `levels`
 * = level code → fee in `currency` (the platform tariff, a JSON number — read
 * tolerantly in case one arrives as a string).
 */
@Serializable
data class CarrierOption(
    @Serializable(with = FlexString::class) val carrierId: String? = null,
    val short: String? = null,
    val hover: String? = null,
    val city: String? = null,
    val rating: Double? = null,
    val nReviews: Int? = null,
    val api: Boolean? = null,
    val levels: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val currency: String? = null,
) {
    val fees: Map<String, Double>
        get() = levels.orEmpty().mapNotNull { (k, v) ->
            ((v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull())?.let { k to it }
        }.toMap()
}

@Serializable
data class CarrierOptions(
    val pickupDistrict: String? = null,
    val dropoffDistrict: String? = null,
    val options: List<CarrierOption>? = null,
)

/** The service-level codes in the platform's order (`shipping.SERVICE_LABELS`). */
val CargoLevelOrder = listOf("acil45", "ayni_gun", "ertesi_gun")

fun orderedLevels(codes: Collection<String>): List<String> =
    codes.sortedWith(compareBy<String>({ CargoLevelOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }, { it }))

/**
 * A line of the seller's order (`seller/invitations/` → `order.lines[]`).
 * `canMarkDelivered` = the seller's own hand delivery only (escrow held AND no
 * Workis shipment, 2026-10-01) — decoded; the app draws no such door.
 */
@Serializable
data class SellerOrderLine(
    @Serializable(with = FlexString::class) val id: String? = null,
    val stageLabel: String? = null,
    @Serializable(with = FlexString::class) val quantity: String? = null,
    val amount: Double? = null,
    val escrowState: String? = null,
    val escrowStateLabel: String? = null,
    val plannedDeliveryDate: String? = null,
    val deliveredAt: String? = null,
    val confirmedAt: String? = null,
    val canMarkDelivered: Boolean? = null,
)

@Serializable
data class OrderFundResult(val success: Boolean = false, val message: String? = null, val error: String? = null, val order: PartnerOrder? = null)

@Serializable
data class ReviewBody(val rating: Int, val comment: String? = null)

// ---------- part 2: the two threads

@Serializable
data class ThreadMessage(
    @Serializable(with = FlexString::class) val id: String? = null,
    val who: String? = null,
    val seat: String? = null,
    val kind: String? = null, // user | jowi | system
    /**
     * Jowi's corrected reading. EMPTY or null once the message is withdrawn —
     * read it optionally, or one null makes the whole table undecodable.
     */
    val content: String? = null,
    /** The words as typed, when Jowi corrected them; the keyboard glyph opens it. */
    val textOriginal: String? = null,
    /** Per-reader translation; when it differs from `content` it is what shows. */
    val textLocalized: String? = null,
    val srcLang: String? = null,
    val mine: Boolean? = null,
    val at: String? = null,
    val atLabel: String? = null,
    val kb: String? = null,
    val verdict: String? = null,
    /** The seats that have seen it — one ✓ on the table, two once the others are all in. */
    val seenBy: List<String>? = null,
    val canRetract: Boolean? = null,
    val retracted: Boolean? = null,
    val retractedAt: String? = null,
    /** What stands where a withdrawn message was; never a gap. */
    val trace: String? = null,
    /** The system line's own word (delivered · accepted · … · withdrawn); the emoji map is the fallback. */
    val icon: String? = null,
)

/** The lighter door: what a poll says about rows already on screen. */
@Serializable
data class ThreadReceipt(
    @Serializable(with = FlexString::class) val id: String? = null,
    val seenBy: List<String>? = null,
    val canRetract: Boolean? = null,
    val retracted: Boolean? = null,
)

@Serializable
data class ThreadRetractResult(
    val success: Boolean = false,
    val message: ThreadMessage? = null,
    val error: String? = null,
)

@Serializable
data class ThreadParties(val buyer: OrderParty? = null, val seller: OrderParty? = null, val carrier: OrderParty? = null)

@Serializable
data class ThreadOrderRef(@Serializable(with = FlexString::class) val id: String? = null, val po: String? = null)

@Serializable
data class ThreadInvitationRef(@Serializable(with = FlexString::class) val id: String? = null, val title: String? = null)

@Serializable
data class ThreadInspection(
    @Serializable(with = FlexString::class) val lineId: String? = null,
    val name: String? = null,
    val status: String? = null,
    val due: String? = null,
    /** escrow | open_account — the app never writes "ödeme bloke" on its own. */
    val moneyPath: String? = null,
    /** The server's sentence for this state, in the request language: print it. */
    val moneyWords: String? = null,
    val invoice: String? = null,
)

@Serializable
data class ThreadPayload(
    val order: ThreadOrderRef? = null,
    /** The quote table sends this instead of `order`. */
    val invitation: ThreadInvitationRef? = null,
    val myRole: String? = null, // buyer | seller | carrier | coordinator
    val parties: ThreadParties? = null,
    val messages: List<ThreadMessage>? = null,
    val shipment: OrderShipment? = null,
    val terms: List<OrderTerm>? = null,
    val nTermsOk: Int? = null,
    val nTerms: Int? = null,
    val contract: OrderContractBadge? = null,
    val inspection: List<ThreadInspection>? = null,
) {
    /** The coordinator reads the table; the composer is hidden. */
    val readOnly get() = myRole == "coordinator"
}

/**
 * The poll answers with the new rows AND the standing of the caller's recent
 * ones, so a ✓ can become ✓✓ and a spent "Geri çek" can go without a full read.
 */
@Serializable
data class ThreadNew(
    val messages: List<ThreadMessage>? = null,
    val receipts: List<ThreadReceipt>? = null,
    val nTermsOk: Int? = null,
    val nTerms: Int? = null,
)

@Serializable
data class ThreadLine(val text: String? = null, val href: String? = null)

@Serializable
data class ThreadPostBody(val text: String, val textOriginal: String? = null)

/**
 * `posted` → reload. `private` / `routed` → draw `lines` as a Jowi card for
 * the asker alone; they are NOT stored in the table and never come back.
 */
@Serializable
data class ThreadPostResult(
    val success: Boolean = false,
    val kind: String? = null, // posted | private | routed
    val lines: List<ThreadLine>? = null,
    val line: String? = null,
    val error: String? = null,
)

// ---------- part 2: the seven term cards (order table only, from award on)

@Serializable
data class OrderTerm(
    val key: String? = null,
    val label: String? = null,
    val owner: String? = null, // buyer | seller | quote
    val question: String? = null,
    val status: String? = null, // missing | proposed | confirmed | rejected
    val display: String? = null,
    /** The cited excerpt — a string, flat (part 2 as built). */
    val source: String? = null,
    val conflict: Boolean? = null,
    val amendment: Boolean? = null,
    val prevDisplay: String? = null,
    val nHistory: Int? = null,
    val buyerOk: Boolean? = null,
    val sellerOk: Boolean? = null,
    val canConfirm: Boolean? = null,
    val canEdit: Boolean? = null,
    /** The way out of a proposal: the proposer withdraws, the other party declines. */
    val canWithdraw: Boolean? = null,
    val canDecline: Boolean? = null,
    val proposedBy: String? = null,
)

@Serializable
data class TermActionResult(
    val success: Boolean = false,
    val term: OrderTerm? = null,
    val nTermsOk: Int? = null,
    val error: String? = null,
)

// ---------- part 3: the order contract

@Serializable
data class ContractAcceptance(val acceptedAt: String? = null, val signer: String? = null, val ip: String? = null)

/**
 * Ek-1 as the contract's annex: accounting facts, decided on the server. The
 * paper DRAWS these rows and computes nothing — the same table the web page
 * and the PDF show, and `sha256` covers it.
 */
@Serializable
data class ContractAnnexLine(
    val n: Int? = null,
    val sku: String? = null,
    /** The QUOTED VARIANT's name, not the buyer's generic words. */
    val name: String? = null,
    /** What the buyer asked for ("Emici spiral hortum · 1 inç · yeşil"). */
    val asked: String? = null,
    val qty: Double? = null,
    /** The order's unit per line — may be EMPTY; never assume "adet". */
    val unit: String? = null,
    val unitPrice: Double? = null,
    val amount: Double? = null,
    val vatRate: Double? = null,
    val listPrice: Double? = null,
    val discountPct: Double? = null,
)

/** One row per rate; `rate` is a fraction (0.20 → "%20"). */
@Serializable
data class ContractVat(val rate: Double? = null, val base: Double? = null, val amount: Double? = null)

@Serializable
data class ContractShipping(val label: String? = null, val amount: Double? = null, val payer: String? = null)

@Serializable
data class ContractAnnex(
    val lines: List<ContractAnnexLine>? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val vat: List<ContractVat>? = null,
    val shipping: ContractShipping? = null,
    val total: Double? = null,
    val pricesExcludeVat: Boolean? = null,
    val note: String? = null,
)

@Serializable
data class OrderContract(
    @Serializable(with = FlexString::class) val id: String? = null,
    val version: Int? = null,
    val status: String? = null, // draft | accepted | superseded
    val sha256: String? = null,
    /** Ends with the heading "Ek-1 — Sipariş Kalemleri"; the table follows it. */
    val bodyMd: String? = null,
    /** null on a version made before the rule — then Ek-1 stays the body's text. */
    val annex: ContractAnnex? = null,
    val buyer: ContractAcceptance? = null,
    val seller: ContractAcceptance? = null,
    val acceptedAt: String? = null,
    val canAccept: Boolean? = null,
    val pdfUrl: String? = null,
)

/** While a card is unsettled `contract` is null and `error` is the sentence to show. */
@Serializable
data class ContractResponse(val contract: OrderContract? = null, val error: String? = null)

@Serializable
data class ContractAcceptBody(val signerName: String)

/** 409 = the draft moved; the fresh contract rides in the body and nothing was signed. */
@Serializable
data class ContractAcceptResult(
    val success: Boolean = false,
    val status: String? = null,
    val version: Int? = null,
    val contract: OrderContract? = null,
    val error: String? = null,
)

// ---------- part 4: Onaylar

@Serializable
data class ApprovalTotal(val currency: String? = null, val amount: Double? = null)

@Serializable
data class ApAward(
    @Serializable(with = FlexString::class) val listId: String? = null,
    val title: String? = null,
    val nQuotes: Int? = null,
    val uncovered: Int? = null,
    val total: Double? = null,
    val currency: String? = null,
    val sellers: Int? = null,
    val totals: List<ApprovalTotal>? = null,
)

@Serializable
data class ApOutward(
    @Serializable(with = FlexString::class) val mid: String? = null,
    val po: String? = null,
    val target: String? = null, // order | quote
    val draft: String? = null,
    val at: String? = null,
)

@Serializable
data class ApFund(
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val po: String? = null,
    /** The server already leaves these out; a false row is never drawn either. */
    val canFund: Boolean? = null,
)

@Serializable
data class ApOverdueProposal(val display: String? = null, val prev: String? = null)

@Serializable
data class ApOverdue(
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val po: String? = null,
    val proposal: ApOverdueProposal? = null,
)

@Serializable
data class ApLine(
    @Serializable(with = FlexString::class) val lineId: String? = null,
    val name: String? = null,
    val po: String? = null,
    val daysLeft: Int? = null,
    val reason: String? = null,
    val status: String? = null,
    val releaseDue: String? = null,
    val otpAt: String? = null,
    val due: String? = null,
    val description: String? = null,
    val discoveredOn: String? = null,
    /** escrow | open_account: the money's road, and the server's words for it. */
    val moneyPath: String? = null,
    val moneyWords: String? = null,
    val invoice: String? = null,
)

@Serializable
data class ApprovalCounts(
    val total: Int? = null,
    val award: Int? = null,
    val outward: Int? = null,
    val fund: Int? = null,
    val overdue: Int? = null,
    val returnOffer: Int? = null,
    val returnDecision: Int? = null,
    val inspection: Int? = null,
    val objection: Int? = null,
    val warranty: Int? = null,
)

@Serializable
data class Approvals(
    val counts: ApprovalCounts? = null,
    val award: List<ApAward>? = null,
    val outward: List<ApOutward>? = null,
    val fund: List<ApFund>? = null,
    val overdue: List<ApOverdue>? = null,
    val returnOffer: List<ApLine>? = null,
    val returnDecision: List<ApLine>? = null,
    val inspection: List<ApLine>? = null,
    val objection: List<ApLine>? = null,
    val warranty: List<ApLine>? = null,
)
