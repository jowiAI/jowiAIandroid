package ai.workis.jowi.data

import kotlinx.serialization.KSerializer
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Seller catalogue — Django docs/WORKIS_PARTNER_API.md §4 (Phases A + B):
 * upload → review → approve, live offers, photos, the maker's web page,
 * teach. Shapes as observed on prod (2026-09-16) are tolerated here:
 * `ingestions[].log` (dicts) and `imageSize` (not a scalar) are simply not
 * declared; `live[].web` is a bare string; `variants[].row` is "" or an int;
 * `families/{id}/` comes wrapped in `{success, family:{…}}`.
 */

/** A number that may arrive as a JSON string ("12.5") — never Double-typed blindly. */
object FlexDouble : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val el = json.decodeJsonElement()
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.replace(',', '.').toDoubleOrNull()
    }
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

@Serializable
data class CatalogCounts(val proposed: Int? = null, val approved: Int? = null, val liveOffers: Int? = null)

@Serializable
data class CatalogPage(
    val counts: CatalogCounts? = null,
    val ingestions: List<CatalogIngestion>? = null,
    val families: List<CatalogFamilyRow>? = null,
    val live: List<CatalogLiveRow>? = null,
    val mode: String? = null,
    @Serializable(with = FlexString::class) val partnerId: String? = null,
)

@Serializable
data class CatalogWebFetch(val status: String? = null, val total: Int? = null, val done: Int? = null, val found: Int? = null)

@Serializable
data class CatalogPageMapEntry(
    val page: Int? = null,
    val archetype: String? = null,
    val fams: Int? = null,
    val webOk: Int? = null,
    val webName: Int? = null,
    val webNone: Int? = null,
    val imgBad: Int? = null,
)

/** One section of a big document's contents page (`status == "plan"`); `ingestionId` is set once it has its own row. */
@Serializable
data class CatalogPlanSection(
    val n: Int? = null,
    val title: String? = null,
    val cats: List<String>? = null,
    val pageFrom: Int? = null,
    val pageTo: Int? = null,
    val pages: Int? = null,
    @Serializable(with = FlexString::class) val ingestionId: String? = null,
)

@Serializable
data class CatalogIngestion(
    @Serializable(with = FlexString::class) val id: String? = null,
    /** The document's NAME (Django 2026-09-22) — never assembled from filename / kind / brandHint. */
    val label: String? = null,
    val filename: String? = null,
    val kind: String? = null,
    val status: String? = null, // created | running | review | failed | plan | queued | cancelled
    /** `status`, or `stalled` when a running row's heartbeat is > 3 min old (2026-09-27) — the label and the polling read THIS. */
    val effectiveStatus: String? = null,
    // which doors are open — the server's word, never derived from the status
    val canResume: Boolean? = null,
    val canCancel: Boolean? = null,
    val canDelete: Boolean? = null,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val errorPages: Int? = null, // pages that failed — retried first on resume
    val heartbeatAt: String? = null,
    val runError: String? = null, // why a failed run failed (server-localized)
    /** A SECTION run of a big document: `parentId` = the document, `sectionTitle` = the row's name. */
    @Serializable(with = FlexString::class) val parentId: String? = null,
    val sectionTitle: String? = null,
    val plan: List<CatalogPlanSection>? = null,
    val brandHint: String? = null,
    val webFetch: CatalogWebFetch? = null,
    val pageMap: List<CatalogPageMapEntry>? = null,
) {
    /** `effectiveStatus` first, the raw status for a row from before the field existed. */
    val state: String get() = effectiveStatus?.takeIf { it.isNotEmpty() } ?: status.orEmpty()
    /** Pages are still being read (or wait their turn) — keep polling. */
    val isRunning get() = state == "created" || state == "running" || state == "queued"
    val isSection get() = !parentId.isNullOrEmpty()
}

@Serializable
data class CatalogWebImage(val url: String? = null, val key: String? = null)

/**
 * The maker's web page — a dict on rows and the detail, a bare string on the
 * live rows (a confidence word or a URL). `images` is a count on the row and a
 * list on the detail.
 */
@Serializable(with = CatalogWebSerializer::class)
data class CatalogWeb(
    val url: String? = null,
    val confidence: String? = null, // exact | name | none | ''
    val pasted: Boolean? = null,
    val images: Int? = null,
    val imageList: List<CatalogWebImage>? = null,
    val tableRows: Int? = null,
    val codesMatched: List<String>? = null,
    val table: List<List<String>>? = null,
)

object CatalogWebSerializer : KSerializer<CatalogWeb> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CatalogWeb", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): CatalogWeb {
        val json = decoder as? JsonDecoder ?: return CatalogWeb(confidence = decoder.decodeString())
        return fromElement(json.decodeJsonElement())
    }

    fun fromElement(el: JsonElement): CatalogWeb {
        if (el is JsonPrimitive) {
            val s = el.content
            return if (s.startsWith("http")) CatalogWeb(url = s) else CatalogWeb(confidence = s.takeIf { it.isNotEmpty() && it != "null" })
        }
        val o = el as? JsonObject ?: return CatalogWeb()
        fun str(k: String) = (o[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        fun int(k: String) = (o[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toDoubleOrNull()?.toInt()
        val images = o["images"]
        return CatalogWeb(
            url = str("url"),
            confidence = str("confidence"),
            pasted = (o["pasted"] as? JsonPrimitive)?.booleanOrNull,
            images = (images as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toDoubleOrNull()?.toInt()
                ?: (images as? JsonArray)?.size,
            imageList = (images as? JsonArray)?.mapNotNull { it as? JsonObject }?.map {
                CatalogWebImage(url = (it["url"] as? JsonPrimitive)?.content, key = (it["key"] as? JsonPrimitive)?.content)
            },
            tableRows = int("tableRows"),
            codesMatched = (o["codesMatched"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content },
            table = (o["table"] as? JsonArray)?.mapNotNull { row -> (row as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: it.toString() } },
        )
    }

    override fun serialize(encoder: Encoder, value: CatalogWeb) = encoder.encodeString(value.url ?: value.confidence.orEmpty())
}

/** One attribute of a variant (Django 2026-09-27: every attribute is shown, no filters). */
data class CatalogAttr(val label: String, val value: String)

/**
 * `live[].attrs` arrives as ready rows `[{label, value}]` in source order; the
 * family detail's `variants[].attrs` is still the raw dictionary (snake_case
 * keys, any scalar value) — read here as rows too, a snake_case key spaced
 * (`size_mm` → "size mm"), a page header kept as printed. kotlinx keeps a JSON
 * object's key order, so the dictionary keeps the server's order here (iOS
 * loses it and sorts). Anything else decodes to no rows.
 */
@Serializable(with = CatalogAttrsSerializer::class)
data class CatalogAttrs(val rows: List<CatalogAttr> = emptyList())

object CatalogAttrsSerializer : KSerializer<CatalogAttrs> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CatalogAttrs", PrimitiveKind.STRING)

    /** A value as printed: "15.2", "3X", "238x370x87"; a bool as ✓ / ✗. */
    private fun scalar(el: JsonElement?): String? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (!p.isString) {
            p.booleanOrNull?.let { return if (it) "✓" else "✗" }
            p.content.toDoubleOrNull()?.let { return WorkisMoney.qty(it) }
        }
        return p.content
    }

    override fun deserialize(decoder: Decoder): CatalogAttrs {
        val json = decoder as? JsonDecoder ?: return CatalogAttrs()
        val rows = when (val el = json.decodeJsonElement()) {
            is JsonArray -> el.mapNotNull { r ->
                val o = r as? JsonObject ?: return@mapNotNull null
                val l = scalar(o["label"])?.trim().orEmpty()
                val v = scalar(o["value"])?.trim().orEmpty()
                if (l.isEmpty() || v.isEmpty()) null else CatalogAttr(l, v)
            }
            is JsonObject -> el.mapNotNull { (k, raw) ->
                val key = k.trim()
                val v = scalar(raw)?.trim().orEmpty()
                if (key.isEmpty() || v.isEmpty()) return@mapNotNull null
                val isSnake = key.all { it.isLowerCase() || it.isDigit() || it == '_' }
                CatalogAttr(if (isSnake) key.replace('_', ' ') else key, v)
            }
            else -> emptyList()
        }
        return CatalogAttrs(rows)
    }

    override fun serialize(encoder: Encoder, value: CatalogAttrs) = encoder.encodeString(value.rows.joinToString { "${it.label}: ${it.value}" })
}

@Serializable
data class CatalogFamilyRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    val page: Int? = null,
    @Serializable(with = FlexString::class) val ingestionId: String? = null,
    val brand: String? = null,
    val category: String? = null, // the catalogue's own section
    val structure: String? = null, // simple | matrix | kit
    val nVariants: Int? = null,
    val img: String? = null, // image key
    val imgQuality: String? = null, // ok | low | page | ''
    val imgSrc: String? = null, // web | catalog | upload | ''
    val web: CatalogWeb? = null,
)

@Serializable
data class CatalogLiveRow(
    @Serializable(with = FlexString::class) val offerId: String? = null,
    val sku: String? = null,
    val name: String? = null,
    @Serializable(with = FlexDouble::class) val price: Double? = null,
    val currency: String? = null,
    val leadTimeDays: Int? = null,
    @Serializable(with = FlexString::class) val stock: String? = null,
    val published: Boolean? = null,
    @Serializable(with = FlexString::class) val productId: String? = null,
    @Serializable(with = FlexString::class) val familyId: String? = null,
    val group: String? = null,
    val mainGroup: String? = null,
    val brand: String? = null,
    val img: String? = null,
    val imgQuality: String? = null,
    val imgSrc: String? = null,
    val web: CatalogWeb? = null,
    val webPasted: Boolean? = null,
    val attrs: CatalogAttrs? = null, // every attribute of the variant, ready rows (2026-09-27)
)

@Serializable
data class CatalogSpecAttr(val key: String? = null, val label: String? = null, val value: String? = null, val unit: String? = null)

@Serializable
data class CatalogVariant(
    val code: String? = null,
    val label: String? = null,
    @Serializable(with = FlexDouble::class) val price: Double? = null,
    val currency: String? = null,
    val priceOnRequest: Boolean? = null,
    val kind: String? = null,
    @Serializable(with = FlexString::class) val row: String? = null, // "" or an int
    val attrs: CatalogAttrs? = null, // the raw dictionary today — see CatalogAttrs
)

@Serializable
data class CatalogKnowledge(val kind: String? = null, val textTr: String? = null, val textEn: String? = null)

@Serializable
data class CatalogMedia(
    @Serializable(with = FlexString::class) val id: String? = null,
    val kind: String? = null,
    val key: String? = null,
    val isPrimary: Boolean? = null,
    val note: String? = null,
    val externalUrl: String? = null,
)

@Serializable
data class CatalogFamilyDetail(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val code: String? = null,
    val description: String? = null,
    /** The catalogue's own heading, as on the review row (not the Workis group). */
    val category: String? = null,
    val status: String? = null,
    @Serializable(with = FlexString::class) val productId: String? = null,
    val specAttrs: List<CatalogSpecAttr>? = null,
    val variants: List<CatalogVariant>? = null,
    val knowledge: List<CatalogKnowledge>? = null,
    val media: List<CatalogMedia>? = null,
    val web: CatalogWeb? = null,
    val imageKey: String? = null,
    val imageQuality: String? = null,
) {
    /** The hero (ledger 18): the PRIMARY row of `media[]` — the main photo lives there now, the
     *  one it replaced stays behind it — with `imageKey` only as the fallback. One slot, never two. */
    val heroKey: String? get() = media?.firstOrNull { it.isPrimary == true && !it.key.isNullOrEmpty() }?.key ?: imageKey

    val isLive get() = status == "approved" || !productId.isNullOrEmpty()
}

/** `{success, family:{…}}` on the preview and every cell response; a bare body is accepted too. */
fun decodeFamily(o: JsonObject): CatalogFamilyDetail? {
    val fam = o["family"] as? JsonObject
    return runCatching { WorkisJson.decodeFromJsonElement(CatalogFamilyDetail.serializer(), fam ?: o) }.getOrNull()
        ?.takeIf { fam != null || it.name != null || it.id != null }
}

fun jsonError(o: JsonObject?): String? =
    (o?.get("error") as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        ?: (o?.get("message") as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

@Serializable
data class CatalogWebProgressState(val status: String? = null, val total: Int? = null, val done: Int? = null, val found: Int? = null, val at: String? = null)

@Serializable
data class CatalogWebProgress(val success: Boolean? = null, val error: String? = null, val progress: CatalogWebProgressState? = null)

@Serializable
data class CatalogTeachResult(val success: Boolean? = null, val error: String? = null, val count: Int? = null)

/**
 * `POST upload/` (2026-09-27): `resumed` = the same file (sha256) was uploaded
 * before and ITS run continues — no new row; `planned` = a multi-section
 * document waits for the section choice.
 */
@Serializable
data class CatalogUploadResult(
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null,
    @Serializable(with = FlexString::class) val ingestionId: String? = null,
    val resumed: Boolean? = null,
    val planned: Boolean? = null,
)

/** GET console/catalog/sellers/ (Phase C, 2026-09-16): role 3 every seller with a seat, role 4 only the sellers with an active helper grant. */
@Serializable
data class CatalogSeller(
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val name: String? = null,
    val nProposed: Int? = null,
    val nOffers: Int? = null,
)

@Serializable
data class CatalogSellers(val success: Boolean? = null, val sellers: List<CatalogSeller>? = null)

/** GET company/ → `catalogHelper` (2026-09-15): the seller opens its catalogue to its field lead. */
@Serializable
data class CatalogHelper(
    val available: Boolean? = null,
    val lead: RegionLead? = null,
    val granted: Boolean? = null,
    val grantedAt: String? = null,
    val expiresAt: String? = null,
)

/** console/partners/ rows: the helper grant, when the seller opened its catalogue. */
@Serializable
data class PartnerCatalogHelper(val lead: String? = null, val since: String? = null)

/** The step after a technical answer (2026-09-15): the purchasable thing in the pool or a gap. */
@Serializable
data class AskNext(
    val kind: String? = null, // "in_pool" | "gap"
    val item: String? = null,
    val sellers: Int? = null,
    val offers: Int? = null,
    val text: String? = null,
)

/** Every agreement payload's `signatures` block (2026-09-16): the two cards under the text. */
@Serializable
data class SignaturePlatform(val name: String? = null, val title: String? = null, val version: Int? = null, val signedAt: String? = null, val sha: String? = null)

@Serializable
data class SignatureAcceptance(val signerName: String? = null, val acceptedAt: String? = null, val ip: String? = null, val kind: String? = null, val version: Int? = null, val sha: String? = null)

@Serializable
data class SignatureParty(val label: String? = null, val acceptance: SignatureAcceptance? = null)

@Serializable
data class AgreementSignatures(val platform: SignaturePlatform? = null, val party: SignatureParty? = null)

/** "16.09.2026" from an ISO stamp — the paper's date grammar. */
fun shortDate(iso: String?): String {
    val ymd = iso.orEmpty().take(10).split("-")
    return if (ymd.size == 3) "${ymd[2]}.${ymd[1]}.${ymd[0]}" else ""
}

/** Catalogue image keys are percent-encoded on the query (`+` would read as a space). */
fun catalogImageUrl(key: String?, rendition: String = "thumb", seller: String? = null): String? {
    if (key.isNullOrEmpty()) return null
    val k = java.net.URLEncoder.encode(key, "UTF-8")
    return "$WORKIS_ORIGIN/api/v1/workis/image/?k=$k&r=$rendition" + (seller?.let { "&seller=$it" } ?: "")
}

fun catalogSourceUrl(ingestionId: String, page: Int, seller: String? = null): String =
    "$WORKIS_ORIGIN/api/v1/workis/seller/catalog/source/$ingestionId/$page/" + (seller?.let { "?seller=$it" } ?: "")
