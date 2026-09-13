package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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
data class RegionLead(val name: String? = null, val region: String? = null)

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
    val status: String? = null, // draft | quoting | ordered | closed
    val stage: String? = null, // parsing | matching | compliance | ready | error
    val source: String? = null,
    @Serializable(with = FlexString::class) val projectId: String? = null,
    val createdAt: String? = null,
    val nItems: Int? = null,
    val nMatched: Int? = null,
    val demo: Boolean? = null,
    val held: String? = null,
) {
    val isWorking get() = stage in setOf("parsing", "matching", "compliance")
}

@Serializable
data class PurchaseListIndex(val lists: List<PurchaseListRow>? = null)

@Serializable
data class PLLabel(val short: String? = null, val long: String? = null, val city: String? = null, val district: String? = null, val hover: String? = null)

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

@Serializable
data class PLProposal(
    val assignments: List<PLAssignment>? = null,
    val sellerTotals: List<PLSellerTotal>? = null,
    val totalsByCurrency: Map<String, Double>? = null,
    val uncovered: List<PLUncovered>? = null,
    val nQuotes: Int? = null,
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
    val isWorking get() = list?.stage in setOf("parsing", "matching", "compliance")
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
)

@Serializable
data class PLSearchResults(val results: List<PLSearchResult>? = null)

@Serializable
data class ListCreated(@Serializable(with = FlexString::class) val id: String? = null, @Serializable(with = FlexString::class) val listId: String? = null, val success: Boolean? = null, val message: String? = null, val error: String? = null)

@Serializable
data class BindBody(val cpid: String)

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

@Serializable
data class SellerProduct(val sku: String? = null, val name: String? = null)

@Serializable
data class InvListItem(val itemName: String? = null, val qty: Double? = null, val unit: String? = null, val alts: List<SellerProduct>? = null)

@Serializable
data class QuoteLine(val sku: String? = null, val name: String? = null, val qty: Double? = null, val unitPrice: Double? = null, val amount: Double? = null)

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
)

@Serializable
data class InvShipment(@Serializable(with = FlexString::class) val id: String? = null, val number: String? = null, val status: String? = null, val statusLabel: String? = null, val promisedAt: String? = null, val labelUrl: String? = null)

@Serializable
data class SellerOrder(
    @Serializable(with = FlexString::class) val id: String? = null,
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
)

@Serializable
data class SellerInvitation(
    @Serializable(with = FlexString::class) val id: String? = null,
    val status: String? = null, // invited | viewed | quoted | declined
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
    val defaultLetter: String? = null,
    val rfqPdfUrl: String? = null,
    val quote: SellerQuote? = null,
    val order: SellerOrder? = null,
    val lost: Boolean? = null,
) {
    val isOpen get() = status == "invited" || status == "viewed"
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
}

/** Strips the sanitized HTML of the RFQ letter into readable lines. */
fun htmlToPlain(html: String): String =
    html.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
        .replace("</p>", "\n").replace("</li>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

typealias JsonBody = JsonObject
