package ai.workis.jowi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// api/v1 responses are camelCase; legacy auth nests snake_case inside `user`,
// so `user` stays a raw JsonObject (single source of truth, like iOS).
// New backend fields must decode optionally → every field nullable + ignoreUnknownKeys.

@Serializable
data class AuthResponse(
    val success: Boolean = false,
    val message: String? = null,
    val token: String? = null,
    val user: JsonObject? = null,
    /**
     * A verified address with NO account behind it: the person is asked before
     * one is opened. Decide on THIS, never on the absence of a token — a
     * missing token means many other things.
     */
    val newAccount: Boolean? = null,
    val signupTicket: String? = null,
    val email: String? = null,
    val created: Boolean? = null,
)

@Serializable
data class OtpRequestBody(val email: String)

@Serializable
data class OtpVerifyBody(
    val email: String,
    val code: String,
    /** A build that HAS the asking screen says so; the server's switch decides. */
    val confirmSignup: Boolean = true,
)

@Serializable
data class SignupConfirmBody(val ticket: String)

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class ProfileUpdateBody(val preferredLanguage: String)

@Serializable
data class Case(
    val id: String? = null,
    val projectId: String? = null,
    val sectorId: String? = null,
    val title: String? = null,
    val symptom: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateCaseBody(
    val symptom: String,
    val title: String? = null,
    val projectId: String? = null,
)

// --- public apply lane (no auth) ---

/** /apply/parse/ returns extracted fields in snake_case inside `data`. */
@Serializable
data class ApplyParseData(
    val company_name: String? = null,
    val short_name: String? = null,
    val tax_number: String? = null,
    val tax_office: String? = null,
    val business_address: String? = null,
    val city: String? = null,
    val entity_type: String? = null,
)

@Serializable
data class ApplyParseResponse(
    val success: Boolean = false,
    val data: ApplyParseData? = null,
    val confidence: Double? = null,
    val error: String? = null,
)

@Serializable
data class ApplySubmitBody(
    val role: String,
    val company: String,
    val email: String,
    val agree: Boolean,
    val signerName: String,
    val taxNumber: String? = null,
    val taxOffice: String? = null,
    val entityType: String? = null,
    val address: String? = null,
    val city: String? = null,
    val sector: String? = null,
    val lang: String? = null,
    val shortName: String? = null,
    val confidence: Double? = null,
    // role=expert only (API_CONTRACT.md → "EXPERT application"): `company` carries the full name
    val expertise: String? = null,
    val affiliation: String? = null,
)

/** GET /workis/agreement/?role=|key= — the paper's letterhead + which PDF to render. */
@Serializable
data class AgreementMeta(
    val key: String? = null,
    val version: Int? = null,
    val title: String? = null,
    val sha: String? = null,
    val createdAt: String? = null,
    val pageUrl: String? = null,
    val pdfUrl: String? = null,
    /** The two signature cards under the text (2026-09-16). */
    val signatures: AgreementSignatures? = null,
)

@Serializable
data class ApplySubmitResponse(
    val success: Boolean = false,
    val applicationId: String? = null,
    val autoActivated: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
)

/** Anonymous applicant Jowi (login screen's Jowi tab): /workis/apply/ask/ */
@Serializable
data class ApplyAskBody(
    val q: String,
    val lang: String? = null,
    /** public.login | public.apply | public.expert_apply (2026-09-17). */
    val screen: String? = null,
    /** On the wizard: {step: the open step's title, as drawn}. */
    val state: kotlinx.serialization.json.JsonObject? = null,
    /** The public door reads the controls too (2026-09-17) — it stops offering buttons the screen has not got. */
    val visible: List<VisibleItem>? = null,
)

@Serializable
data class ApplyAskResponse(val ok: Boolean = false, val text: String? = null, val options: List<AskOption>? = null)

@Serializable
data class AskBody(
    val q: String,
    /** The typed text when Jowi's reading was sent instead (clarification, 2026-09-09). */
    val qOriginal: String? = null,
    val page: String? = null,
    val ctxList: String? = null,
    val voice: Boolean? = null,
    /** "Teklif topla": the same question again opens the chat-born list (2026-09-15). */
    val createList: Boolean? = null,
    // where the user is (2026-09-17): the server resolves "bu ürün" / "sağ üstteki
    // düğme" against these and offers options drawn from `visible`
    val screen: String? = null,
    val state: kotlinx.serialization.json.JsonObject? = null,
    val visible: List<VisibleItem>? = null,
    /** A console seat asking from a seller's catalogue — whose. */
    val seller: String? = null,
    /** A tapped chip under a match's one question (2026-10-01): the SAME request again with it. */
    val hint: String? = null,
    /** "Teklif topla" on a pick list: the ticked product rows — only ever with `createList`. */
    val picks: List<AskPick>? = null,
)

/**
 * One ticked row of the match card (2026-10-01): `sku` '' = the family (the
 * seller decides the variant), `skus` = every variant of the product. No
 * defaults here on purpose — `encodeDefaults = false` would drop an empty sku.
 */
@Serializable
data class AskPick(
    val cpid: String,
    /** A number when the id reads as one, as iOS sends it. */
    val sellerId: JsonPrimitive,
    val sku: String,
    val skus: List<String>,
    val name: String,
)

/**
 * "Which one do you mean?" (2026-09-17): `q` is the follow-up to send, null = type again.
 * Two shapes share `options` (2026-10-01): the clarify flow's `{label, q}` and a
 * match answer's bare chip strings — a bare string decodes as `label`, no `q`.
 */
@Serializable(with = AskOptionSerializer::class)
data class AskOption(val label: String = "", val q: String? = null)

object AskOptionSerializer : KSerializer<AskOption> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AskOption")
    override fun deserialize(decoder: Decoder): AskOption {
        val json = decoder as? JsonDecoder ?: return AskOption(decoder.decodeString())
        fun text(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        return when (val el = json.decodeJsonElement()) {
            is JsonObject -> AskOption(label = text(el["label"]).orEmpty(), q = text(el["q"]))
            is JsonPrimitive -> AskOption(text(el).orEmpty())
            else -> AskOption()
        }
    }
    override fun serialize(encoder: Encoder, value: AskOption) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(buildJsonObject {
            put("label", value.label)
            value.q?.let { put("q", it) }
        })
    }
}

/**
 * `next` is ONLY the technical step (Django 77bb5926). A bare string under it
 * was the transitional scene id — read past it (an empty step), never built on:
 * the next scene is `nextScene`.
 */
object AskNextTolerant : KSerializer<AskNext> {
    override val descriptor: SerialDescriptor = AskNext.serializer().descriptor
    override fun deserialize(decoder: Decoder): AskNext {
        val json = decoder as? JsonDecoder ?: return AskNext.serializer().deserialize(decoder)
        val el = json.decodeJsonElement()
        return if (el is JsonObject) json.json.decodeFromJsonElement(AskNext.serializer(), el) else AskNext()
    }
    override fun serialize(encoder: Encoder, value: AskNext) = AskNext.serializer().serialize(encoder, value)
}

/**
 * One seller's PRODUCT rows on a match (2026-10-01): `{partnerId, seller,
 * products[…]}`. Every field tolerant — an odd value must never sink the answer.
 */
@Serializable
data class AskProductSeller(
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val seller: String? = null,
    val products: List<AskProduct>? = null,
)

/** One product row: its variants sit under a picker; `selectedSku` is what the request's size singled out ('' = nothing). */
@Serializable
data class AskProduct(
    @Serializable(with = FlexString::class) val cpid: String? = null,
    val name: String? = null,
    val img: String? = null,
    val kind: String? = null,
    @Serializable(with = FlexDouble::class) val score: Double? = null,
    val currency: String? = null,
    @Serializable(with = FlexDouble::class) val priceMin: Double? = null,
    @Serializable(with = FlexDouble::class) val priceMax: Double? = null,
    @Serializable(with = FlexString::class) val selectedSku: String? = null,
    val variants: List<AskVariant>? = null,
)

@Serializable
data class AskVariant(
    @Serializable(with = FlexString::class) val sku: String? = null,
    val label: String? = null,
    @Serializable(with = FlexDouble::class) val price: Double? = null,
    val currency: String? = null,
    /** `[[label, value], …]` — kept raw, read through [attrRows]; any scalar value. */
    val attrs: JsonElement? = null,
) {
    /** The variant's attributes as (label, value), source order; an odd shape = none. */
    val attrRows: List<Pair<String, String>>
        get() = (attrs as? JsonArray).orEmpty().mapNotNull { p ->
            val pair = p as? JsonArray ?: return@mapNotNull null
            if (pair.size < 2) return@mapNotNull null
            fun text(e: JsonElement) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
            text(pair[0]) to text(pair[1])
        }
}

/** What Jowi mapped the words onto (2026-10-01) — decoded, never drawn. */
@Serializable
data class AskReading(
    val termTr: String? = null,
    val families: List<@Serializable(with = FlexString::class) String>? = null,
    val category: String? = null,
    val attributes: AskReadingAttributes? = null,
    val language: String? = null,
)

@Serializable
data class AskReadingAttributes(
    @Serializable(with = FlexString::class) val size: String? = null,
    @Serializable(with = FlexString::class) val material: String? = null,
    @Serializable(with = FlexString::class) val type: String? = null,
)

/** One catalogue row in a match — a PRODUCT, not an offer. Price-blind here. */
@Serializable
data class AskOffer(val sku: String? = null, val name: String? = null)

@Serializable
data class AskSeller(
    @SerialName("partner_id") @Serializable(with = FlexString::class) val partnerId: String? = null,
    /** The long trading name — TalkBack only; the row draws `short`. */
    val name: String? = null,
    val short: String? = null,
    val hover: String? = null,
    val city: String? = null,
    val offers: List<AskOffer>? = null,
) {
    /** History rows predate `short`; they fall back to the long name. */
    val rowName get() = short?.takeIf { it.isNotEmpty() } ?: name.orEmpty()
}

@Serializable
data class AskResponse(
    val success: Boolean = false,
    val answer: String? = null,
    val source: String? = null, // "page" | "chain" | "knowledge" | "model" | "knowledge+model" | "match" …
    /** Seat-aware ask: which chain answered — "buyer" | "console" | "expert". */
    val surface: String? = null,
    /** Guidance when `answer` is null (server-localized). */
    val message: String? = null,
    // purchase request: kind == "match" carries the parsed item/qty + sellers
    val kind: String? = null,
    val item: String? = null,
    @Serializable(with = FlexString::class) val qty: String? = null,
    /** "metre" — `item` no longer carries the quantity, so the app joins them. */
    val unit: String? = null,
    val nProducts: Int? = null,
    /** No exact match: say so, never "N matches". */
    val approximate: Boolean? = null,
    /** parsing → ready: the list is still being read. */
    val listStage: String? = null,
    val sellers: List<AskSeller>? = null,
    @Serializable(with = FlexString::class) val listId: String? = null,
    // answer policy B: a stamp under the bubble; `kb` present → 👍/👎
    val label: String? = null,
    val kb: String? = null,
    /** The step after a technical answer (2026-09-15) — only that, never a scene id. */
    @Serializable(with = AskNextTolerant::class) val next: AskNext? = null,
    /**
     * `source: "clarify"` carries tappable options (2026-09-17); on a match
     * (2026-10-01) they are the chips of its one `question`.
     */
    val options: List<AskOption>? = null,
    /** A launch SCENE answered (2026-09-27): its id, and the one after it — "Bir örnek daha". */
    val scene: String? = null,
    val nextScene: String? = null,
    /** The match by PRODUCT (2026-10-01): one row per product per seller; `sellers[].offers` stays for the old shelf. */
    val products: List<AskProductSeller>? = null,
    val reading: AskReading? = null,
    /** ONE clarifying question on a match; `options` then carries its chips. */
    val question: String? = null,
    /** The turn's row ids + `canHide`, so "Soruyu geri çek" works right after sending (2026-09-19). */
    val turn: AskTurn? = null,
    val handedOff: Boolean? = null,
)

@Serializable
data class AskTurn(
    @Serializable(with = FlexString::class) val questionId: String? = null,
    val answerIds: List<@Serializable(with = FlexString::class) String>? = null,
    val canHide: Boolean? = null,
)

/**
 * One row of the Jowi thread (GET /workis/ask/history/) — the SAME thread the web drawer keeps.
 * A withdrawn turn comes back IN PLACE as a stub: `hidden: true`, no text, the
 * folded row ids in `turn`, `trace` ready to print, `canUnhide` (2026-09-18).
 */
@Serializable
data class AskLine(
    @Serializable(with = FlexString::class) val id: String? = null,
    /** Only a turn that did nothing can be withdrawn — the server decides. */
    val canHide: Boolean? = null,
    val hidden: Boolean? = null,
    /** An id ARRAY — the rows folded into the stub. */
    val turn: List<@Serializable(with = FlexString::class) String>? = null,
    val hiddenAt: String? = null,
    val canUnhide: Boolean? = null,
    val trace: String? = null,
    val handoffClosed: Boolean? = null,
    /** First row of an `unhide/`: the question went back to the team's inbox. */
    val handoffReopened: Boolean? = null,
    val who: String? = null, // user | jowi | expert | lead
    val text: String? = null,
    val href: String? = null,
    val at: String? = null,
    val qOriginal: String? = null,
    val kind: String? = null,
    val item: String? = null,
    @Serializable(with = FlexString::class) val qty: String? = null,
    val unit: String? = null,
    val nProducts: Int? = null,
    val approximate: Boolean? = null,
    val listStage: String? = null,
    val sellers: List<AskSeller>? = null,
    @Serializable(with = FlexString::class) val listId: String? = null,
    val label: String? = null,
    val kb: String? = null,
    val source: String? = null,
    val surface: String? = null,
    @Serializable(with = AskNextTolerant::class) val next: AskNext? = null,
    val scene: String? = null, // a scene answer's id (2026-09-27)
    val nextScene: String? = null,
    /**
     * A notification card (`kind: "lines"`, 2026-09-30): `action` = the reader
     * must act (a quote request); `good` = a closed loop.
     */
    val accent: String? = null,
    // 2026-10-01 match fields — tolerated; history rows do not carry `products` today
    val products: List<AskProductSeller>? = null,
    val reading: AskReading? = null,
    val question: String? = null,
    val options: List<AskOption>? = null,
)

/** `nextBefore` only on the archive pages — null once the oldest page is in hand. */
@Serializable
data class AskHistory(val success: Boolean? = null, val lines: List<AskLine>? = null, val nextBefore: String? = null)

/** `hide/` → draw `stub` where the turn stood, no reload. */
@Serializable
data class AskHideResult(
    val success: Boolean = false,
    val hidden: List<@Serializable(with = FlexString::class) String>? = null,
    val stub: AskLine? = null,
    val error: String? = null,
)

/** `unhide/` → the turn back, in the ordinary row shape. */
@Serializable
data class AskUnhideResult(val success: Boolean = false, val lines: List<AskLine>? = null, val error: String? = null)

@Serializable
data class AskPreviewBody(val q: String)

/** Jowi's clean reading of a typed question, decided BEFORE the send. */
@Serializable
data class AskPreview(
    val q: String? = null,
    val qClarified: String? = null,
    val changed: Boolean? = null,
    val material: Boolean? = null,
)

@Serializable
data class AskLikeBody(val kb: String, val v: String) // "up" | "down"

@Serializable
data class AskLikeResult(val success: Boolean = false, val rated: Boolean? = null)
