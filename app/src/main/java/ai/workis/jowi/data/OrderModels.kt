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
)

/** "2 şart eksik" / "7/7 şart" — the server counts, the app prints. */
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
    val escrow: String? = null, // raw state; "no_escrow" is what opens Blokla
    val escrowLabel: String? = null,
    val shipment: OrderShipment? = null,
    val myRating: Int? = null,
    val canReview: Boolean? = null,
    val unread: Int? = null,
    val terms: OrderTermsBadge? = null,
    val contract: OrderContractBadge? = null,
    @Serializable(with = FlexString::class) val listId: String? = null,
    @Serializable(with = FlexString::class) val invitationId: String? = null,
) {
    val isCarrier get() = role == "carrier"
    val canFund get() = escrow == "no_escrow"
    val termsMissing get() = ((terms?.total ?: 0) - (terms?.ok ?: 0)).coerceAtLeast(0)
}

@Serializable
data class PartnerOrders(val orders: List<PartnerOrder>? = null)

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
    val content: String? = null,
    /** Per-reader translation; when it differs from `content` it is what shows. */
    val textLocalized: String? = null,
    val srcLang: String? = null,
    val mine: Boolean? = null,
    val at: String? = null,
    val atLabel: String? = null,
    val kb: String? = null,
    val verdict: String? = null,
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

@Serializable
data class ThreadNew(val messages: List<ThreadMessage>? = null)

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

@Serializable
data class OrderContract(
    @Serializable(with = FlexString::class) val id: String? = null,
    val version: Int? = null,
    val status: String? = null, // draft | accepted | superseded
    val sha256: String? = null,
    val bodyMd: String? = null,
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
data class ApFund(@Serializable(with = FlexString::class) val orderId: String? = null, val po: String? = null)

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
