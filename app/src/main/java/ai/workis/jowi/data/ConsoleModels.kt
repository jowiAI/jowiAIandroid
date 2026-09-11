package ai.workis.jowi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// Console payloads — every field nullable (optional-decode discipline:
// several fields ship after the client; a deploy lights them up).

/** Accepts both 121 and "121" — the live API emits numeric ids. */
object FlexString : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val el = json.decodeJsonElement()
        return (el as? JsonPrimitive)?.content ?: el.toString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class QuestionTopic(val topic: String? = null, val count: Int? = null)

@Serializable
data class UnansweredQuestion(
    @Serializable(with = FlexString::class) val id: String? = null,
    val q: String? = null,
    val days: Int? = null,
    val kind: String? = null, // "handoff" | "unmatched"
)

@Serializable
data class ConsoleSummary(
    val newApplications: Int? = null,
    val awaitingApproval: Int? = null,
    val activePartners: Int? = null,
    val openQuestions: Int? = null,
    val openConversations: Int? = null,
    val oldestQuestionDays: Int? = null,
    val unansweredQuestions: List<UnansweredQuestion>? = null,
    val questionTopics: List<QuestionTopic>? = null,
    /** "Jowi answered" lane (2026-09-10): what Jowi answered in the window, by source and topic. */
    val jowiAnswered: JowiAnswered? = null,
)

// downOpen = 👎 nobody has closed yet (either lane); downReviewed = down − downOpen
// (confirmed or corrected). Shipped 2026-09-11; `down` stays as the fallback.
@Serializable
data class JowiSource(
    val source: String? = null,
    val label: String? = null,
    val count: Int? = null,
    val up: Int? = null,
    val down: Int? = null,
    val downOpen: Int? = null,
    val downReviewed: Int? = null,
)

@Serializable
data class JowiTopic(
    val topic: String? = null,
    val count: Int? = null,
    val up: Int? = null,
    val down: Int? = null,
    val downOpen: Int? = null,
    val downReviewed: Int? = null,
)

@Serializable
data class JowiAnswered(
    val days: Int? = null,
    val total: Int? = null,
    val sources: List<JowiSource>? = null,
    val topics: List<JowiTopic>? = null,
    val dislikedOpen: Int? = null, // experts' lane
    val reviewOpen: Int? = null, // staff lane
)

/** GET /workis/console/jowi-answered/ — the web Sorular page's second tab as rows; walls are the server's. */
@Serializable
data class JowiRowCompany(val short: String? = null, val seat: String? = null)

@Serializable
data class JowiRowReview(
    val outcome: String? = null, // corrected | confirmed
    val by: String? = null,
    val at: String? = null,
    val text: String? = null,
)

@Serializable
data class JowiAnsweredRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val askedAt: String? = null,
    val question: String? = null,
    val answerExcerpt: String? = null,
    val source: String? = null, // knowledge | knowledge+model | model | offtopic
    val label: String? = null,
    val topic: String? = null,
    val verdict: String? = null, // up | down | null
    val company: JowiRowCompany? = null,
    val review: JowiRowReview? = null,
    /** who still has to act on a 👎: "expert" | "staff" | null */
    val awaiting: String? = null,
    @Serializable(with = FlexString::class) val threadId: String? = null,
    @Serializable(with = FlexString::class) val messageId: String? = null,
)

@Serializable
data class JowiAnsweredLane(
    val days: Int? = null,
    val total: Int? = null,
    val closedCount: Int? = null,
    val sources: List<JowiSource>? = null,
    val topics: List<JowiTopic>? = null,
    val rows: List<JowiAnsweredRow>? = null,
)

@Serializable
data class ApplicationRow(
    @Serializable(with = FlexString::class) val applicationId: String? = null,
    val company: String? = null,
    val shortName: String? = null,
    @Serializable(with = FlexString::class) val vkn: String? = null,
    val city: String? = null,
    val role: String? = null,
    val email: String? = null,
    val confidence: Double? = null,
    val hasTaxFile: Boolean? = null,
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val partnerStatus: String? = null,
    val appliedAt: String? = null,
    val openChats: Int? = null,
    val openQuestions: Int? = null,
)

@Serializable
data class ConsoleApplications(
    val leads: List<ApplicationRow>? = null,
    val pending: List<ApplicationRow>? = null,
    val approved: List<ApplicationRow>? = null,
)

/** GET applications/{id}/ — the shipped payload says `id`/`createdAt`; the
 *  contract's `applicationId`/`appliedAt` are accepted too so a rename on
 *  either side never blanks the screen. */
@Serializable
data class ApplicationDetail(
    @Serializable(with = FlexString::class) val applicationId: String? = null,
    @Serializable(with = FlexString::class) val id: String? = null,
    val company: String? = null,
    val shortName: String? = null,
    val email: String? = null,
    val sector: String? = null,
    val city: String? = null,
    @Serializable(with = FlexString::class) val taxNumber: String? = null,
    val taxOffice: String? = null,
    val entityType: String? = null,
    val address: String? = null,
    @Serializable(with = FlexString::class) val catalogFormats: String? = null,
    val confidence: Double? = null,
    val role: String? = null,
    val status: String? = null,
    val appliedAt: String? = null,
    val createdAt: String? = null,
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val partnerStatus: String? = null,
    /** false once approved — every field freezes (server 403s edits anyway). */
    val canEdit: Boolean? = null,
    val hasTaxFile: Boolean? = null,
    val agreement: AgreementAcceptance? = null,
    val priorQuestions: List<PriorQuestion>? = null,
) {
    val resolvedId: String? get() = applicationId ?: id
    val resolvedAppliedAt: String? get() = appliedAt ?: createdAt
}

@Serializable
data class AgreementAcceptance(
    val version: Int? = null,
    val kind: String? = null,
    val acceptedAt: String? = null,
    val ip: String? = null,
    val sha: String? = null,
    val signerName: String? = null,
    val signedPdfUrl: String? = null,
)

@Serializable
data class PriorQuestion(
    val at: String? = null,
    val q: String? = null,
    val tag: String? = null,
)

@Serializable
data class UpdateFieldBody(val field: String, val value: String)

@Serializable
data class ConsoleQuestion(
    @Serializable(with = FlexString::class) val id: String? = null,
    val q: String? = null,
    val days: Int? = null,
    val kind: String? = null,
    val visitor: String? = null,
    val hasEmail: Boolean? = null,
    val topic: String? = null,
    // clarification + translation (2026-09-09): `q` is the WORKING text (Jowi's accepted
    // reading); `qOriginal` the raw keystrokes when it differs; `qLocalized` the machine
    // translation into the expert's language (null until ready — the list never waits)
    val qOriginal: String? = null,
    val clarifiedByJowi: Boolean? = null,
    val qLocalized: String? = null,
    val qLang: String? = null,
    val localizedIsMachine: Boolean? = null,
) {
    /** What the expert reads first; the original rides underneath. */
    val display: String get() = qLocalized?.takeIf { it.isNotEmpty() } ?: q.orEmpty()
}

@Serializable
data class ConsoleQuestions(val questions: List<ConsoleQuestion>? = null)

@Serializable
data class PartnerRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val status: String? = null,
    val city: String? = null,
    val sector: String? = null,
    val seat: String? = null, // "partner" | "virtual" | "expert"
    val openConversations: Int? = null,
    val openQuestions: Int? = null,
    val lastActivityDays: Int? = null,
)

@Serializable
data class ConsolePartners(val partners: List<PartnerRow>? = null)

@Serializable
data class ConversationRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val partner: String? = null,
    val lastMessage: String? = null,
    val days: Int? = null,
    val open: Boolean? = null,
    val messageCount: Int? = null,
)

@Serializable
data class ConsoleConversations(val conversations: List<ConversationRow>? = null)

@Serializable
data class ConsoleMessage(
    val role: String? = null, // user | jowi | expert | lead
    val text: String? = null,
    val at: String? = null,
    val internal: Boolean? = null,
    val toWho: String? = null, // "partner" | "lead" | "expert" | null
)

@Serializable
data class ConversationDetail(
    val partner: String? = null,
    val open: Boolean? = null,
    val canInternal: Boolean? = null,
    val messages: List<ConsoleMessage>? = null,
)

@Serializable
data class SimpleResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val conversationId: String? = null,
)

@Serializable
data class AnswerBody(val answer: String, val teach: Boolean = true)

@Serializable
data class ReplyBody(val text: String, val internal: Boolean? = null)

@Serializable
data class StartConvBody(val text: String)
