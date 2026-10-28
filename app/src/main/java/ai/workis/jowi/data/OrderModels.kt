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

/**
 * `terms.current` (2026-10-02): the ONE term asked of this seat now, in the walk
 * order; `turn` is `answer` (state it) or `confirm` (the other side stated it).
 */
@Serializable
data class OrderCurrentTerm(
    val key: String? = null,
    val label: String? = null,
    val turn: String? = null, // answer | confirm
    val i: Int? = null,
    val n: Int? = null,
)

/** "7/7 koşul kesinleşti" — the server counts, the app prints. */
@Serializable
data class OrderTermsBadge(
    val ok: Int? = null,
    val total: Int? = null,
    /** How many terms wait on this seat. */
    val mine: Int? = null,
    /** null when nothing waits on this seat (and for the seller until the buyer signed). */
    val current: OrderCurrentTerm? = null,
)

/**
 * THE one sentence under an order (`order_stage.next_step`): draw `text`, never
 * derive one from state codes. `who`: me · buyer · seller · carrier · workis · "".
 */
@Serializable
data class OrderNext(
    val who: String? = null,
    val text: String? = null,
    val step: String? = null, // the live step's key
    val job: String? = null, // terms · contract · fund · ready · lines · collect · ""
    val action: String? = null, // a control the card already has (cargo_pick) or ""
)

/** One dated movement under a step — filled on `orders/{id}/stage/` only. */
@Serializable
data class OrderStepRow(val at: String? = null, val text: String? = null, val value: String? = null)

/**
 * terms · contract · payment · cargo · accept · account (a carrier: cargo only);
 * `state` done · now · wait · skip — exactly one `now` on an unfinished order.
 * `label` is the server's word for the step: the app keeps no table of its own.
 */
@Serializable
data class OrderStep(
    val key: String? = null,
    val label: String? = null,
    val state: String? = null,
    val line: String? = null,
    val note: String? = null,
    val rows: List<OrderStepRow>? = null,
)

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
    /** The seller's "Kargoya teslim" is the server's call (`order_seat.serialize`) — never derived from role / mode / dispatchState. */
    val canReady: Boolean? = null,
    /** Why the goods may not leave yet — the server's sentence; "" when not worth a line. */
    val readyHint: String? = null,
    // the order stage (`order_seat.serialize`, 2026-10-02) — all absent on an older server
    /** Aktif / Tamamlananlar — the server's split; a missing `done` reads as active. */
    val done: Boolean? = null,
    val next: OrderNext? = null,
    val steps: List<OrderStep>? = null,
    /** What the live step asks of this seat — `orders/{id}/stage/` only, never on the list. */
    val job: OrderJob? = null,
) {
    val isCarrier get() = role == "carrier"
}

/**
 * The buyer's lists in the quote stage, on `GET orders/` (2026-10-03; api/v1/workis/views.py:166-175,
 * lists_seat.py `quoting_cards`) — compare and approve stay on the list. `badge` = new quotes +
 * unread quote-stage yazışma since the buyer last opened that list's quotes.
 */
@Serializable
data class OrderQuoting(
    @Serializable(with = FlexString::class) val listId: String? = null,
    val title: String? = null,
    val nInvited: Int? = null,
    val nQuoted: Int? = null,
    val nPending: Int? = null,
    val nDeclined: Int? = null,
    val nNew: Int? = null,
    val nUnread: Int? = null,
    val badge: Int? = null,
)

/** `GET orders/` → `{orders[], quoting[]}`; `quoting` is absent on an older server, [] for a seat without a buyer side. */
@Serializable
data class PartnerOrders(val orders: List<PartnerOrder>? = null, val quoting: List<OrderQuoting>? = null)

// ---------- the stage (`GET orders/{id}/stage/`, Django `order_seat.stage_for` + `serialize_job`, 2026-10-02)

/**
 * One line of the `lines` job. The flags only keep dead buttons off the screen;
 * the doors hold the rules. `canMark` = the seller's hand-delivery mark →
 * `POST orders/lines/{id}/delivered/` (2026-10-05).
 */
@Serializable
data class OrderJobLine(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    @Serializable(with = FlexDouble::class) val qty: Double? = null,
    val note: String? = null,
    val openAccount: Boolean? = null,
    val canMark: Boolean? = null,
    val canConfirm: Boolean? = null,
    val canObject: Boolean? = null,
    val canWithdraw: Boolean? = null,
)

/** The live step's job — one shape for every `kind`; the unused fields stay null. */
@Serializable
data class OrderJob(
    val kind: String? = null, // terms | contract | fund | ready | lines | collect
    // terms — `current` is the term KEY; the card's `terms.current` names it
    val current: String? = null,
    val i: Int? = null,
    val n: Int? = null,
    // contract
    val canAccept: Boolean? = null,
    val signHint: String? = null,
    // ready
    val today: String? = null,
    val sellerDelivers: Boolean? = null,
    val deliveryDate: String? = null,
    /** The last day the goods can be handed to cargo and still arrive on the agreed date. */
    val handover: String? = null,
    /** false = that day has passed. */
    val handoverOk: Boolean? = null,
    // lines
    val lines: List<OrderJobLine>? = null,
    // collect — money is a JSON number here too
    @Serializable(with = FlexString::class) val invoiceId: String? = null,
    val number: String? = null,
    @Serializable(with = FlexDouble::class) val total: Double? = null,
    val currency: String? = null,
    val due: String? = null,
    val suspended: Boolean? = null,
)

/** `{success, order}` — the card with `steps[].rows` filled and `job`. 404 = the server's sentence. */
@Serializable
data class OrderStageResult(val success: Boolean? = null, val order: PartnerOrder? = null, val error: String? = null)

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
    @Serializable(with = FlexDouble::class) val amount: Double? = null,
    val escrowState: String? = null,
    val escrowStateLabel: String? = null,
    val plannedDeliveryDate: String? = null,
    val deliveredAt: String? = null,
    val confirmedAt: String? = null,
    val canMarkDelivered: Boolean? = null,
)

@Serializable
data class OrderFundResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val order: PartnerOrder? = null,
    /** Ledger 71: the PSP's hosted card page — present = the money is NOT held yet (`PaymentWebView`). */
    val paymentUrl: String? = null,
)

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
    /** "quote" on a row of the teklif-stage yazışma shown inside the order's; null otherwise. */
    val stage: String? = null,
    /**
     * The cargo channel (2026-10-03, thread_seat.py:103-111, :722-726): true on a line the
     * carrier reads too — its own, a party's line sent to cargo, the shipping system lines.
     * A carrier reader receives cargo lines only. Absent on an older server.
     */
    val cargo: Boolean? = null,
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
    /**
     * The winning quote's yazışma (2026-10-02): same grammar, each row `stage:
     * "quote"`, read-only. Drawn ABOVE `messages`; `[]` for a carrier.
     */
    val quoteStage: List<ThreadMessage>? = null,
    val shipment: OrderShipment? = null,
    val terms: List<OrderTerm>? = null,
    val nTermsOk: Int? = null,
    val nTerms: Int? = null,
    val contract: OrderContractBadge? = null,
    val inspection: List<ThreadInspection>? = null,
    /** What the three typed cards offer (data/ThreadModels.kt); null for a carrier. */
    val termChoices: TermChoices? = null,
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
data class ThreadPostBody(
    val text: String,
    val textOriginal: String? = null,
    /** "cargo" (order table, buyer / seller) sends the line to the carrier too; "table" (ledger 54) skips the
     *  triage and posts the text as typed — the way back from a `kind: "private"` turn; null = the parties only. */
    val audience: String? = null,
)

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
    /**
     * The stored value behind `display` (camelCase; its shape differs per key) —
     * raw, read through `valueText` / `valueInt` (data/ThreadModels.kt).
     */
    val value: kotlinx.serialization.json.JsonElement? = null,
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
    /**
     * Why this seat cannot sign yet when `canAccept` is false (2026-10-02,
     * `contract_seat.why_not_signable`) — the server's sentence, shown as sent.
     */
    val signHint: String? = null,
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
    /** Satisfaction ticks waiting — NOT in `total` (a right, not a task). */
    val satisfaction: Int? = null,
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
    /** The buyer's lines accepted in the last 14 days with no tick yet, newest first, ≤ 20 (ledger 54). */
    val satisfaction: List<SatisfactionAsk>? = null,
)

// ---------- The satisfaction tick (ledger 54, Django 2026-10-09; `reasons` 2026-10-20) ----------

@Serializable
data class SatisfactionOption(val value: String? = null, val label: String? = null)

@Serializable
data class SatisfactionReason(val code: String? = null, val label: String? = null)

/**
 * "Teslimat nasıldı?" — the buyer's one-tap answer, private (the seller never sees it, nothing on
 * the thread). The SAME shape rides on the confirm reply (`satisfaction`, with `question` and
 * `private`) and on `GET approvals/ → satisfaction[]` (with `seller`, `confirmedAt`, `daysLeft`,
 * no `question`/`private` → the app's `satisfactionPrivate` key). Words as sent.
 */
@Serializable
data class SatisfactionAsk(
    val kind: String? = null,
    @Serializable(with = FlexString::class) val lineId: String? = null,
    val name: String? = null,
    val po: String? = null,
    val seller: String? = null,
    val question: String? = null,
    val private: String? = null,
    val confirmedAt: String? = null,
    val daysLeft: Int? = null,
    val options: List<SatisfactionOption>? = null,
    /** Toggle chips under the three answers — today one, "Bilseydim başka ürün alırdım"; the chosen `code` goes as `reason`. */
    val reasons: List<SatisfactionReason>? = null,
)

/** `POST orders/lines/{id}/confirm/` → the thanks line and the satisfaction ask to render right there. */
@Serializable
data class LineConfirmResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val satisfaction: SatisfactionAsk? = null,
)

/** `POST orders/lines/{id}/satisfaction/` — `value` good|ok|bad, `note` ≤ 400, `reason` = a chip's code. A second tick overwrites. */
@Serializable
data class SatisfactionBody(val value: String, val note: String? = null, val reason: String? = null)

// ---------- Jowi speaks first — the memory's nudges (ledger 54, Django 2026-10-09) ----------

@Serializable
data class NudgeLink(val text: String? = null, val href: String? = null)

/**
 * One nudge due NOW: `stale` (the last price is six months old or more) or `rhythm` (a bought-on-a-rhythm
 * product whose interval has passed). `line` is light markdown (the product name in bold) → the markdown
 * renderer; `link.text` is the button. Read-only — the GET marks nothing.
 */
@Serializable
data class Nudge(
    val kind: String? = null,
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val name: String? = null,
    val line: String? = null,
    val link: NudgeLink? = null,
    val lastAt: String? = null,
    val daysSince: Int? = null,
    val months: Int? = null,
    val cadenceDays: Int? = null,
)

@Serializable
data class NudgesPage(val nudges: List<Nudge>? = null, val error: String? = null)

/** `POST memory/nudges/{cpid}/list/` → a draft list from the buyer's LAST order of that product (no prices); a second tap returns the same open draft. */
@Serializable
data class NudgeListResult(
    val success: Boolean = false,
    @Serializable(with = FlexString::class) val listId: String? = null,
    val listStage: String? = null,
    val listUrl: String? = null,
    val error: String? = null,
    val message: String? = null,
)
