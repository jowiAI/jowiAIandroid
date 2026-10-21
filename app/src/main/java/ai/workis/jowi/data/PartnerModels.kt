package ai.workis.jowi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.util.Locale

// Partner seat (role 1) — the native marketplace, slice 1 (Django docs/WORKIS_PARTNER_API.md).
// camelCase; MONEY ON THIS SURFACE IS A JSON NUMBER (formatted with 2 fraction digits in the app
// locale — never parsed as a decimal string); lat/lng are strings; ids tolerant (FlexString).

// ---------- Firma ----------

@Serializable
data class CompanyInfo(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val displayShort: String? = null,
    /**
     * The storefront logo the quote PDF prints — an absolute PUBLIC URL
     * (`https://workis.ai/<slug>/gorsel/?k=<key>`, Django 2026-10-05); null for a
     * firm without one → the monogram. Cache by URL (the key changes on upload).
     */
    val logoUrl: String? = null,
    @Serializable(with = FlexString::class) val vkn: String? = null,
    val taxOffice: String? = null,
    val fullAddress: String? = null,
    val district: String? = null,
    val city: String? = null,
    @Serializable(with = FlexString::class) val postalCode: String? = null,
    val country: String? = null,
    val phone: String? = null,
    @Serializable(with = FlexString::class) val lat: String? = null,
    @Serializable(with = FlexString::class) val lng: String? = null,
    val locationSource: String? = null, // manual | geocode
    val status: String? = null,
)

@Serializable
data class CompanySeats(val buyer: Boolean? = null, val seller: Boolean? = null, val carrier: Boolean? = null)

@Serializable
data class RegionLead(
    val name: String? = null,
    val region: String? = null,
    /** The lead's Madde 11/3 declaration (firm names) and whether this seller is one of them. */
    val relatedFirms: List<String>? = null,
    val ownFirm: Boolean? = null,
) {
    /** "Armağan — İstanbul Avrupa" — the region once, even when the name already carries it. */
    val line: String get() {
        val n = name.orEmpty(); val r = region.orEmpty()
        return if (r.isEmpty() || n.contains(r)) n else "$n · $r"
    }
}

@Serializable
data class CompanyAutonomy(
    val outwardRoutine: Boolean? = null,
    val awardCapUsd: Double? = null,
    val autoReorder: Boolean? = null,
    val autoFund: Boolean? = null,
    val autoReleaseDays: Int? = null,
)

@Serializable
data class PartnerCompany(
    val company: CompanyInfo? = null,
    val readOnly: List<String>? = null,
    val seats: CompanySeats? = null,
    val hasTaxFile: Boolean? = null,
    val regionLead: RegionLead? = null,
    val autonomy: CompanyAutonomy? = null,
    /** `available:false` = no seller seat or no lead covers the firm. */
    val catalogHelper: CatalogHelper? = null,
) {
    val isBuyer get() = seats?.buyer == true
    val isSeller get() = seats?.seller == true
    val isCarrier get() = seats?.carrier == true
    val shortLabel get() = company?.displayShort ?: company?.shortName ?: company?.name ?: ""
}

/** The 403 body of every partner route names the next door (seat_status). */
@Serializable
data class SeatApplication(
    @Serializable(with = FlexString::class) val id: String? = null,
    val company: String? = null,
    val role: String? = null,
    val stage: String? = null, // review | approval | approved | closed
    val createdAt: String? = null,
    /** The address on the application — when it differs from the sign-in, the seat goes THERE. */
    val filedWith: String? = null,
)

@Serializable
data class SeatVerdict(
    val success: Boolean? = null,
    val error: String? = null,
    val seat: String? = null, // "none" | "operator"
    val application: SeatApplication? = null,
    val applyUrl: String? = null,
    val emailConflict: Boolean? = null,
)

sealed interface PartnerSeatState {
    data object Unknown : PartnerSeatState
    data class Seat(val company: PartnerCompany) : PartnerSeatState
    data class None(val application: SeatApplication?, val applyUrl: String?, val emailConflict: Boolean) : PartnerSeatState
    data object Operator : PartnerSeatState
}

@Serializable
data class GeoProvince(val name: String? = null, val districts: List<String>? = null)

@Serializable
data class GeoProvinces(val provinces: List<GeoProvince>? = null)

@Serializable
data class CompanySaveResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val city: String? = null,
    val district: String? = null,
    @Serializable(with = FlexString::class) val lat: String? = null,
    @Serializable(with = FlexString::class) val lng: String? = null,
)

@Serializable
data class AutonomyResult(val success: Boolean = false, val message: String? = null, val error: String? = null, val autonomy: CompanyAutonomy? = null)

// ---------- Purchase lists (buyer) ----------

@Serializable
data class PurchaseListRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val title: String? = null,
    val status: String? = null, // draft | quoting | ordered | closed | withdrawn
    val stage: String? = null, // parsing | matching | compliance | checking | ready | error
    val source: String? = null,
    @Serializable(with = FlexString::class) val projectId: String? = null,
    val createdAt: String? = null,
    val nItems: Int? = null,
    val nMatched: Int? = null,
    val demo: Boolean? = null,
    val held: String? = null,
    val archived: Boolean? = null,
    /** An unsent DRAFT only — once sent the list is a record (no archive, no delete). */
    val canArchive: Boolean? = null,
    /** Stage `error`, or a working stage silent for 3 minutes. */
    val stuck: Boolean? = null,
    val canRetry: Boolean? = null,
    val stageSince: String? = null,
    /** The screens' own words ("teklif aşamasında" / "okunuyor") — drawn before the app's map. */
    val statusLabel: String? = null,
    val stageLabel: String? = null,
    /** 2026-10-04: a request out for quotes with nothing awarded may be taken back
     *  (`POST lists/{id}/withdraw/`, 409 otherwise); `withdrawn` once it was. The detail's
     *  `list` carries neither — read them from this row. */
    val canWithdraw: Boolean? = null,
    val withdrawn: Boolean? = null,
) {
    val isWorking get() = stage in PL_WORKING_STAGES
}

/** `checking` = Jowi's note — every built list polls through it (2026-10-09; purchase_list.WORKING_STAGES). */
val PL_WORKING_STAGES = setOf("parsing", "matching", "compliance", "checking")

@Serializable
data class PurchaseListIndex(val lists: List<PurchaseListRow>? = null)

/** `archive` / `unarchive` / `retry` / `withdraw` — a sent list answers 409 with the sentence to show. */
@Serializable
data class ListActionResult(val success: Boolean = false, val list: PurchaseListRow? = null, val stage: String? = null, val message: String? = null, val error: String? = null)

@Serializable
data class PLLabel(val short: String? = null, val long: String? = null, val city: String? = null, val district: String? = null, val hover: String? = null)

@Serializable
data class PLChatInvite(
    @Serializable(with = FlexString::class) val id: String? = null,
    val lbl: PLLabel? = null,
    val status: String? = null,
    val unread: Int? = null,
)

@Serializable
data class PLInfo(
    @Serializable(with = FlexString::class) val id: String? = null,
    val title: String? = null,
    val status: String? = null,
    val stage: String? = null,
    val source: String? = null,
    @Serializable(with = FlexString::class) val projectId: String? = null,
    val createdAt: String? = null,
    val demo: Boolean? = null,
    val held: String? = null,
    @Serializable(with = FlexString::class) val rfqId: String? = null,
    /** false = a buyer seat: every catalogue price in the payload is null and none is drawn —
     *  the first number a buyer sees is a quote (lists_seat.py, 2026-10-03). Missing = true. */
    val prices: Boolean? = null,
    /** Jowi's re-read before the buyer sees the list — on every built list with ≥ 2 rows
     *  (2026-10-09; it was need-built lists only); null = no note. */
    val jowiNote: PLJowiNote? = null,
    /** How a need-built list was built (2026-10-06); null for any other list or when nothing is left to show. */
    @Serializable(with = PLProvenanceTolerant::class) val provenance: PLProvenance? = null,
    /** Jowi's stage counts + the server's sentence (2026-10-20, ledger 66); null = nothing to say. */
    val jowiSummary: PLJowiSummary? = null,
)

/** `list.jowiSummary {waiting, better, fits, answered, added, line}` — `line` is the sentence the page shows. */
@Serializable
data class PLJowiSummary(
    val waiting: Int? = null,
    val better: Int? = null,
    val fits: Int? = null,
    val answered: Int? = null,
    val added: Int? = null,
    val line: String? = null,
)

/** A row's answer to "Ne için kullanacaksınız?" and Jowi's card for it (`items[].use`, 2026-10-20). */
data class PLUse(val text: String?, val card: PLUseCard?)

/** `use_card` (row_use.py): verdict fits | better | unsure; NO price anywhere on it. */
@Serializable
data class PLUseCard(
    val kind: String? = null,
    @Serializable(with = FlexString::class) val itemId: String? = null,
    val product: String? = null,
    val use: String? = null,
    val verdict: String? = null,
    val reason: String? = null,
    val alternative: PLUseAlternative? = null,
    val complements: List<PLUseComplement>? = null,
)

@Serializable
data class PLUseAlternative(
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val name: String? = null,
    val seller: String? = null,
    @Serializable(with = FlexString::class) val sellerId: String? = null,
    val why: String? = null,
)

/** `role` needed | optional; `cpid`/`catalogueName`/`seller`/`sellerId` only when the catalogue carries it. */
@Serializable
data class PLUseComplement(
    val name: String? = null,
    val why: String? = null,
    val role: String? = null,
    val inCatalogue: Boolean? = null,
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val catalogueName: String? = null,
    val seller: String? = null,
    @Serializable(with = FlexString::class) val sellerId: String? = null,
) {
    /** One complement on one card — the session's "added" memory keys on it. */
    val key get() = cpid?.takeIf { it.isNotEmpty() } ?: name.orEmpty()
}

/** `use` must never sink the list: anything but an object reads as null; a card that fails to
 *  decode leaves the text standing. */
object PLUseTolerant : KSerializer<PLUse?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PLUse", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): PLUse? {
        val json = decoder as? JsonDecoder ?: return null
        val o = json.decodeJsonElement() as? JsonObject ?: return null
        val text = (o["text"] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim().orEmpty()
        val card = (o["card"] as? JsonObject)?.let { runCatching { json.json.decodeFromJsonElement(PLUseCard.serializer(), it) }.getOrNull() }
        if (text.isEmpty() && card == null) return null
        return PLUse(text.ifEmpty { null }, card)
    }
    override fun serialize(encoder: Encoder, value: PLUse?) = encoder.encodeNull()
}

/**
 * `list.provenance {sentence, job, problem, qa[{question, answer, skipped, note}]}`
 * (lists_seat.py `provenance`): the buyer's sentence, Jowi's reading of the job, the problem,
 * the card's questions in order. `skipped` = the question was skipped; `note` = something the
 * buyer wrote that answered no question; `question: ""` = an older list kept only the answer.
 * Read-only history — never a price or a seller. `more` = the needs built into this draft
 * later (2026-10-09, the same shape, present only when there is one).
 */
data class PLProvenance(val sentence: String, val job: String, val problem: String, val qa: List<PLProvenanceQA>, val more: List<PLProvenance> = emptyList()) {
    val isEmpty get() = sentence.isEmpty() && job.isEmpty() && problem.isEmpty() && qa.isEmpty()
}

data class PLProvenanceQA(val question: String, val answer: String, val skipped: Boolean, val note: Boolean)

/** History must never sink the list: an odd shape reads as "nothing to show" (null). */
object PLProvenanceTolerant : KSerializer<PLProvenance?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PLProvenance", PrimitiveKind.STRING)
    private fun text(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim().orEmpty()
    private fun flag(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content == "true"
    override fun deserialize(decoder: Decoder): PLProvenance? {
        val json = decoder as? JsonDecoder ?: return null
        val o = json.decodeJsonElement() as? JsonObject ?: return null
        val more = (o["more"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::block) }
        return block(o)?.copy(more = more) ?: more.firstOrNull()?.copy(more = more.drop(1))
    }
    private fun block(o: JsonObject): PLProvenance? {
        val qa = (o["qa"] as? JsonArray).orEmpty().mapNotNull { r ->
            (r as? JsonObject)?.let { PLProvenanceQA(text(it["question"]), text(it["answer"]), flag(it["skipped"]), flag(it["note"])) }
        }
        return PLProvenance(text(o["sentence"]), text(o["job"]), text(o["problem"]), qa).takeIf { !it.isEmpty }
    }
    override fun serialize(encoder: Encoder, value: PLProvenance?) = encoder.encodeNull()
}

/** One finding of Jowi's note: `kind` missing · incompatible · quantity · standard · duplicate ·
 *  problem · note; `row` = the 1-based row it names (null = the list as a whole). */
@Serializable
data class PLNoteFinding(
    val kind: String? = null,
    @Serializable(with = FlexDouble::class) val row: Double? = null,
    val text: String? = null,
)

/** `list.jowiNote {at, summary, findings, language}` — no price, no seller in it. */
@Serializable
data class PLJowiNote(
    val at: String? = null,
    val summary: String? = null,
    val findings: List<PLNoteFinding>? = null,
    val language: String? = null,
)

@Serializable
data class PLVerdict(val req: String? = null, val verdict: String? = null, val evidence: String? = null)

@Serializable
data class PLCompliance(val meets: Int? = null, val fails: Int? = null, val unclear: Int? = null, val rows: List<PLVerdict>? = null)

@Serializable
data class PLCandidate(
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val sku: String? = null,
    val name: String? = null,
    val seller: String? = null,
    val sellerHover: String? = null,
    @Serializable(with = FlexString::class) val sellerId: String? = null,
    val verified: Boolean? = null,
    val price: Double? = null,
    val currency: String? = null,
    val img: String? = null,
    val isBound: Boolean? = null,
    val compliance: PLCompliance? = null,
    // The asked size decides the VARIANT (Django variant_match, 2026-09-19). `sku` and
    // `price` are the fitting variant's when exactly one fits; an unresolved family
    // carries NO price — a family-level price was the 3/4" one shown for a 1" request.
    val variant: String? = null, // exact | single | none | ambiguous
    val variantName: String? = null,
    val variants: List<PLVariant>? = null,
    val nVariants: Int? = null,
    /** One variant stands for this candidate (single-variant product, a size fitting exactly
     *  one, or the reader's own choice) — then `variantName`/`sku`/`price` are that variant's. */
    val resolved: Boolean? = null,
    /** Several variants of a real seller's product: offer "Varyant seç" / "Varyantı değiştir"
     *  (`GET lists/items/{id}/variants/?cpid=`, 2026-10-02). */
    val canChooseVariant: Boolean? = null,
) {
    /** What the row names: the resolved variant, else the family. */
    val displayName get() = variantName?.takeIf { it.isNotEmpty() } ?: name ?: sku.orEmpty()
}

@Serializable
data class PLVariant(val sku: String? = null, val name: String? = null, @Serializable(with = FlexDouble::class) val price: Double? = null, val currency: String? = null)

/** `GET lists/items/{id}/variants/?cpid=` — that seller's variants of ONE candidate; `chosen` = the sku standing ("" when none). */
@Serializable
data class PLVariantList(
    val variants: List<PLVariant>? = null,
    val chosen: String? = null,
    /** false = a buyer seat, the prices are null (2026-10-03); missing = true. */
    val prices: Boolean? = null,
)

@Serializable
data class PLItem(
    @Serializable(with = FlexString::class) val id: String? = null,
    val n: Int? = null,
    val name: String? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val requirements: List<String>? = null,
    val matchState: String? = null, // unmatched | candidates | bound
    @Serializable(with = FlexString::class) val boundCpid: String? = null,
    @Serializable(with = FlexString::class) val selectedCpid: String? = null,
    val candidates: List<PLCandidate>? = null,
    /** The row's tick: a selected product AND one variant stands for it. "Teklif iste"
     *  refuses until every matched row has it; a free-text row never blocks (2026-10-03). */
    val complete: Boolean? = null,
    /** Jowi's stage (2026-10-20): the row asks what it is for (drafts; once per row). */
    val askUse: Boolean? = null,
    /** Up to four application phrases from the product's facts — a tap fills the line, never sends. */
    val useChips: List<String>? = null,
    /** The answer and its card once given; null until then (decoded leniently). */
    @Serializable(with = PLUseTolerant::class) val use: PLUse? = null,
    /** The row came from another row's card ("+ Ekle") — "Jowi ekledi". */
    val jowiAdded: Boolean? = null,
) {
    /** the bound candidate, else the top one (the web's `sel`) */
    val selected: PLCandidate?
        get() {
            val c = candidates ?: return null
            val cp = selectedCpid ?: boundCpid
            return c.firstOrNull { it.cpid == cp } ?: c.firstOrNull()
        }
}

@Serializable
data class PLSuggestion(
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val name: String? = null,
    val seller: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val because: String? = null,
    val lbl: PLLabel? = null,
    /** whose product — rides `POST lists/{id}/items/` as `sellerId` */
    @Serializable(with = FlexString::class) val sellerId: String? = null,
)

@Serializable
data class PLAssignment(
    @Serializable(with = FlexString::class) val itemId: String? = null,
    val itemName: String? = null,
    val seller: String? = null,
    val name: String? = null,
    val lbl: PLLabel? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val unitPrice: Double? = null,
    val currency: String? = null,
    val amount: Double? = null,
    val fails: Int? = null,
    val recommended: Boolean? = null,
    val alternatives: Int? = null,
) {
    val sellerName get() = lbl?.short ?: seller ?: ""
}

@Serializable
data class PLSellerTotal(@Serializable(with = FlexString::class) val sellerId: String? = null, val lbl: PLLabel? = null, val currency: String? = null, val total: Double? = null)

@Serializable
data class PLUncovered(@Serializable(with = FlexString::class) val itemId: String? = null, val name: String? = null, val qty: Double? = null, val unit: String? = null)

/**
 * One term of a quote's sheet (2026-09-30), in registry order — what the buyer reads next
 * to the price. `fromChat` = settled in the yazışma (kiremit frame); `conflict` = the sheet
 * and the yazışma disagree (warning). The proposal is passed through as computed, so the
 * snake_case spelling is read beside camelCase.
 */
@Serializable
data class PLQuoteTerm(
    val key: String? = null,
    val label: String? = null,
    @Serializable(with = FlexString::class) val text: String? = null,
    val fromChat: Boolean? = null,
    @SerialName("from_chat") val fromChatSnake: Boolean? = null,
    val conflict: Boolean? = null,
) {
    val isFromChat get() = (fromChat ?: fromChatSnake) == true
}

/** A quote's term sheet on the comparison — matched to a seller block through `sellerId`. */
@Serializable
data class PLQuoteSheet(
    @Serializable(with = FlexString::class) val id: String? = null,
    @Serializable(with = FlexString::class) val sellerId: String? = null,
    @SerialName("seller_id") @Serializable(with = FlexString::class) val sellerIdSnake: String? = null,
    val number: String? = null,
    val terms: List<PLQuoteTerm>? = null,
) {
    val seller get() = sellerId ?: sellerIdSnake
}

@Serializable
data class PLProposal(
    val assignments: List<PLAssignment>? = null,
    val sellerTotals: List<PLSellerTotal>? = null,
    val totalsByCurrency: Map<String, Double>? = null,
    val uncovered: List<PLUncovered>? = null,
    val nQuotes: Int? = null,
    val quotes: List<PLQuoteSheet>? = null, // each quote's term sheet (2026-09-30)
)

@Serializable
data class PLRfqDoc(@Serializable(with = FlexString::class) val sellerId: String? = null, val lbl: PLLabel? = null, val number: String? = null, val stored: Boolean? = null)

@Serializable
data class PLSendRow(
    val g: String? = null,
    val seller: String? = null,
    val hover: String? = null,
    val channel: String? = null,
    val status: String? = null,
    val pdf: String? = null,
    val item: String? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val nAlts: Int? = null,
    val alts: String? = null,
    @Serializable(with = FlexString::class) val inv: String? = null,
    val chased: Boolean? = null,
)

@Serializable
data class PLPendingSeller(val lbl: PLLabel? = null, @Serializable(with = FlexString::class) val invId: String? = null, val chased: Boolean? = null)

@Serializable
data class PLShipment(@Serializable(with = FlexString::class) val id: String? = null, val number: String? = null, val status: String? = null, val level: String? = null)

@Serializable
data class PLOrder(
    @Serializable(with = FlexString::class) val id: String? = null,
    val poNumber: String? = null,
    val total: Double? = null,
    val currency: String? = null,
    val escrow: String? = null,
    val lbl: PLLabel? = null,
    val deliveryMode: String? = null,
    val serviceLevel: String? = null,
    val dispatchState: String? = null,
    val hasPdf: Boolean? = null,
    val nLines: Int? = null,
    val shipment: PLShipment? = null,
)

@Serializable
data class PLVirtualInvite(
    @Serializable(with = FlexString::class) val invitationId: String? = null,
    val name: String? = null,
    val status: String? = null,
    val total: Double? = null,
    val vadeDays: Int? = null,
    val draft: Boolean? = null,
)

@Serializable
data class PLProject(@Serializable(with = FlexString::class) val id: String? = null, val name: String? = null, val isDefault: Boolean? = null)

@Serializable
data class PurchaseListDetail(
    val list: PLInfo? = null,
    val items: List<PLItem>? = null,
    val suggestions: List<PLSuggestion>? = null,
    val proposal: PLProposal? = null,
    val sellerTotals: List<PLSellerTotal>? = null,
    val rfqDocs: List<PLRfqDoc>? = null,
    val sendPreview: List<PLSendRow>? = null,
    val sendStatusMode: Boolean? = null,
    val chatUnreadTotal: Int? = null,
    /** The buyer's "Satıcı yazışmaları" rows — one per invited seller. */
    val chatInvites: List<PLChatInvite>? = null,
    val rfqNote: String? = null,
    val rfqLetter: String? = null,
    val rfqLetterDefault: String? = null,
    val pendingSellers: List<PLPendingSeller>? = null,
    val orders: List<PLOrder>? = null,
    val virtualInvites: List<PLVirtualInvite>? = null,
    val projects: List<PLProject>? = null,
    val generalRequirements: List<String>? = null,
) {
    val isDraft get() = list?.status == "draft"
    val isQuoting get() = list?.status == "quoting"
    val isReady get() = (list?.stage ?: "ready") == "ready"
    val isWorking get() = list?.stage in PL_WORKING_STAGES
    /** Catalogue prices are drawn only when the server says so; a missing flag = true. */
    val showsPrices get() = list?.prices ?: true
}

@Serializable
data class PLSearchResult(
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val sku: String? = null,
    val name: String? = null,
    val seller: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val img: String? = null,
    val already: Boolean? = null,
    // 2026-10-02: the search line's keys are the service's own, SNAKE_CASE (returned raw).
    // `price` is set only when `resolved` (one variant stands); else the count + the range.
    // 2026-10-05 (views.py `_camel_twins`): the camelCase twins ride beside the snake keys —
    // both spellings are read, the camelCase one first.
    @SerialName("sellerId") @Serializable(with = FlexString::class) val sellerIdCamel: String? = null,
    @SerialName("seller_id") @Serializable(with = FlexString::class) val sellerIdSnake: String? = null,
    val resolved: Boolean? = null,
    val variant: String? = null,
    @SerialName("variantName") val variantNameCamel: String? = null,
    @SerialName("variant_name") val variantNameSnake: String? = null,
    @SerialName("nVariants") val nVariantsCamel: Int? = null,
    @SerialName("n_variants") val nVariantsSnake: Int? = null,
    @SerialName("priceFrom") @Serializable(with = FlexDouble::class) val priceFromCamel: Double? = null,
    @SerialName("price_from") @Serializable(with = FlexDouble::class) val priceFromSnake: Double? = null,
    @SerialName("priceTo") @Serializable(with = FlexDouble::class) val priceToCamel: Double? = null,
    @SerialName("price_to") @Serializable(with = FlexDouble::class) val priceToSnake: Double? = null,
) {
    val sellerId get() = sellerIdCamel ?: sellerIdSnake
    val variantName get() = variantNameCamel ?: variantNameSnake
    val nVariants get() = nVariantsCamel ?: nVariantsSnake
    val priceFrom get() = priceFromCamel ?: priceFromSnake
    val priceTo get() = priceToCamel ?: priceToSnake
}

@Serializable
data class PLSearchResults(val results: List<PLSearchResult>? = null)

@Serializable
data class ListCreated(@Serializable(with = FlexString::class) val id: String? = null, @Serializable(with = FlexString::class) val listId: String? = null, val success: Boolean? = null, val message: String? = null, val error: String? = null)

/** `{cpid}` selects the family, `+ sku` chooses the VARIANT (draft only), `{cpid: ""}` unselects;
 *  a search pick adds `sellerId` + `q`. Nulls are not encoded (`explicitNulls = false`). */
@Serializable
data class BindBody(val cpid: String, val sku: String? = null, val sellerId: String? = null, val q: String? = null)

/** `POST lists/{id}/items/` answer — raw keys: `needs_variant` → open that row's variant list. */
@Serializable
data class ListItemAdded(
    val success: Boolean = false,
    val matched: Int? = null,
    @SerialName("item_id") @Serializable(with = FlexString::class) val itemId: String? = null,
    @SerialName("needs_variant") val needsVariant: Boolean? = null,
    /** The new row asks what it is for (2026-10-20) — camelCase since Django's view renamed it; */
    val askUse: Boolean? = null,
    /** … `ask_use` as `lists_seat.add_item` first answered. Read both. */
    @SerialName("ask_use") val askUseSnake: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val asksUse get() = askUse ?: askUseSnake ?: false
}

/** `POST lists/items/{id}/use/` — `{use}` answers, `{skip: true}` closes the question for good. */
@Serializable
data class UseBody(val use: String? = null, val skip: Boolean? = null)

/** `{success, card | null}` — a null card on an answer = Jowi found nothing to say. */
@Serializable
data class UseReply(val success: Boolean = false, val card: PLUseCard? = null, val error: String? = null, val message: String? = null)

/** `POST lists/{id}/rename/ {title}` — draft or quoting only; 400 under two characters, 409 once ordered. */
@Serializable
data class ListRenameBody(val title: String)

@Serializable
data class NoteBody(val note: String? = null, val letter: String? = null)

@Serializable
data class RequestQuotesBody(val excluded: List<String>)

@Serializable
data class RequestQuotesResult(val success: Boolean? = null, val held: Boolean? = null, val invited: Int? = null, val message: String? = null, val error: String? = null)

@Serializable
data class AwardResult(val success: Boolean? = null, val orderIds: List<@Serializable(with = FlexString::class) String>? = null, val message: String? = null, val error: String? = null)

// ---------- Seller quotes ----------

@Serializable
data class InvBuyer(val short: String? = null, val city: String? = null, val hover: String? = null)

/**
 * `listPrice` (2026-09-30) = the seller's own catalogue price for this candidate — it
 * pre-fills an EMPTY unit price on a single-candidate item, never on alternatives.
 * `discountPct` (2026-10-01) = the seller's usual rate on it, a percentage (12 = 12 %).
 */
@Serializable
data class SellerProduct(val sku: String? = null, val name: String? = null, val listPrice: Double? = null, val discountPct: Double? = null)

/**
 * The terms born with the quote (2026-09-30), frozen at award for both seats.
 * `warrantyMonths` 0 = "garanti yok", null = not stated; empty `returnsText` = platform standard.
 * The sent quote echoes them AS STORED — snake_case (`warranty_months`) on Django today
 * (`_serialize_quote` passes `metadata.terms` through) — so both spellings are read.
 */
@Serializable
data class QuoteTerms(
    val packaging: String? = null,
    @SerialName("warrantyMonths") @Serializable(with = FlexString::class) private val monthsCamel: String? = null,
    @SerialName("warranty_months") @Serializable(with = FlexString::class) private val monthsSnake: String? = null,
    @SerialName("warrantyText") private val warrantyTextCamel: String? = null,
    @SerialName("warranty_text") private val warrantyTextSnake: String? = null,
    @SerialName("returnsText") private val returnsTextCamel: String? = null,
    @SerialName("returns_text") private val returnsTextSnake: String? = null,
) {
    val warrantyMonths: Int? get() = (monthsCamel ?: monthsSnake)?.trim()?.toIntOrNull()
    val warrantyText: String? get() = warrantyTextCamel ?: warrantyTextSnake
    val returnsText: String? get() = returnsTextCamel ?: returnsTextSnake
}

/**
 * `requirements` = the buyer's demands for THIS item ("1 inç", "yeşil"); `alts` are the
 * FITTING VARIANTS once the size resolved, otherwise the family (2026-09-19).
 */
@Serializable
data class InvListItem(
    val itemName: String? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val alts: List<SellerProduct>? = null,
    val requirements: List<String>? = null,
    /** What the buyer said the row is for (ledger 69, Django slice 3): '' when unanswered. */
    val use: String? = null,
)

/** `listPrice` + `discountPct` echo what the line was priced against (2026-10-01). */
@Serializable
data class QuoteLine(
    val sku: String? = null,
    val name: String? = null,
    val qty: Double? = null,
    val unitPrice: Double? = null,
    val amount: Double? = null,
    val listPrice: Double? = null,
    val discountPct: Double? = null,
)

@Serializable
data class SellerQuote(
    @Serializable(with = FlexString::class) val id: String? = null,
    val number: String? = null,
    val createdAt: String? = null,
    val offerSku: String? = null,
    val offerName: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val leadTimeDays: Int? = null,
    val validUntil: String? = null,
    val vadeDays: Int? = null,
    val deliveryMode: String? = null,
    val note: String? = null,
    val letter: String? = null,
    val lines: List<QuoteLine>? = null,
    val total: Double? = null,
    val pdfUrl: String? = null,
    val terms: QuoteTerms? = null, // echoes what was sent (2026-09-30)
)

@Serializable
data class InvShipment(@Serializable(with = FlexString::class) val id: String? = null, val number: String? = null, val status: String? = null, val statusLabel: String? = null, val promisedAt: String? = null, val labelUrl: String? = null)

/** The payment term in force — "vade" runs on open account, nothing is ever blocked. */
@Serializable
data class SellerPayment(val mode: String? = null, val days: Int? = null) // "vade" | "escrow" | ""

@Serializable
data class SellerOrder(
    @Serializable(with = FlexString::class) val id: String? = null,
    val payment: SellerPayment? = null,
    val poNumber: String? = null,
    val poPdfUrl: String? = null,
    val total: Double? = null,
    val currency: String? = null,
    val escrowState: String? = null,
    val escrowStateLabel: String? = null,
    val deliveryMode: String? = null,
    val dispatch: String? = null,
    val chatUrl: String? = null,
    val shipment: InvShipment? = null,
    val lines: List<SellerOrderLine>? = null, // canMarkDelivered (2026-10-01) — data/OrderModels.kt
)

@Serializable
data class SellerInvitation(
    @Serializable(with = FlexString::class) val id: String? = null,
    val status: String? = null, // invited | viewed | quoted | declined | withdrawn
    val statusLabel: String? = null,
    val createdAt: String? = null,
    @Serializable(with = FlexString::class) val rfqId: String? = null,
    val rfqStatus: String? = null, // open | comparing | awarded
    @Serializable(with = FlexString::class) val caseId: String? = null,
    val caseTitle: String? = null,
    val request: String? = null,
    val buyer: InvBuyer? = null,
    val qty: Double? = null,
    val interpreted: String? = null,
    val imageUrl: String? = null,
    val isListRfq: Boolean? = null,
    val shortlist: List<SellerProduct>? = null,
    val listItems: List<InvListItem>? = null,
    /** The request's own delivery term (the list's project site, else the buyer's address; 2026-09-30). */
    val deliveryPlace: String? = null,
    val defaultLetter: String? = null,
    val rfqPdfUrl: String? = null,
    val quote: SellerQuote? = null,
    val order: SellerOrder? = null,
    val lost: Boolean? = null,
) {
    /** `withdrawn` (2026-10-04, lists_seat.py:116 / quotes_seat.py:299): the buyer took the request
     *  back — closed, no quote form; a quote already given stays on the record. */
    val isWithdrawn get() = status == "withdrawn"
    val isOpen get() = !isWithdrawn && (status == "invited" || status == "viewed")
}

@Serializable
data class SellerInvitations(val invitations: List<SellerInvitation>? = null)

@Serializable
data class QuoteSubmitResult(val success: Boolean = false, val message: String? = null, val error: String? = null)

@Serializable
data class PolishResult(val suggestion: String? = null, val error: String? = null)

// ---------- Money (JSON numbers on this surface) ----------

object WorkisMoney {
    private fun fmt(): NumberFormat = NumberFormat.getNumberInstance(
        if (ai.workis.jowi.Graph.language.value == "en") Locale.US else Locale("tr", "TR"),
    ).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

    /** "4.000,00 USD" in the app language's locale. */
    fun text(value: Double?, currency: String?): String {
        if (value == null) return "—"
        val n = fmt().format(value)
        return if (currency.isNullOrEmpty()) n else "$n $currency"
    }

    fun qty(value: Double?): String {
        if (value == null) return ""
        return if (value == Math.rint(value)) value.toLong().toString() else value.toString()
    }

    /** A percentage figure (12.5 = 12,5 %) in the app locale, one decimal at most: "12", "5,5". */
    fun pct(value: Double?): String {
        if (value == null) return ""
        val f = NumberFormat.getNumberInstance(
            if (ai.workis.jowi.Graph.language.value == "en") Locale.US else Locale("tr", "TR"),
        ).apply { minimumFractionDigits = 0; maximumFractionDigits = 1 }
        return f.format(java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP))
    }

    /**
     * A typed amount with either decimal mark ("1.234,50", "1234.5", "12,5") → BigDecimal;
     * the last mark is the decimal one, the other is a thousands separator. Null when not a number.
     */
    fun parse(text: String?): java.math.BigDecimal? {
        var s = text.orEmpty().trim().replace(" ", "")
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            else -> s.replace(',', '.')
        }
        return s.toBigDecimalOrNull()
    }

    /** A BigDecimal as the plain figure a field holds: "88.5", "100" (no exponent, no trailing zeros). */
    fun plain(value: java.math.BigDecimal): String =
        value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
}

/** Strips the sanitized HTML of the RFQ letter into readable lines. */
fun htmlToPlain(html: String): String =
    html.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
        .replace("</p>", "\n").replace("</li>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

typealias JsonBody = JsonObject
