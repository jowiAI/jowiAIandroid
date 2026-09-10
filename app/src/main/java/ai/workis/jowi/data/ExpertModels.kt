package ai.workis.jowi.data

import kotlinx.serialization.Serializable

// Expert seat (role 5) — /workis/expert/* slice 1 (API_CONTRACT.md → "Expert seat").
// Optional-decode discipline: every field nullable; ids through FlexString.

@Serializable
data class ExpertWaiting(
    val questions: Int? = null,
    val reviews: Int? = null,
    val companions: Int? = null,
    val consults: Int? = null,
    val unitsActive: Int? = null,
)

@Serializable
data class ExpertPoints(
    val uses: Int? = null,
    val likes: Int? = null,
    val answers: Int? = null,
    val reviews: Int? = null,
    val consults: Int? = null,
    val total: Int? = null,
)

/** shareUsd is money → decimal-as-string, shown verbatim. */
@Serializable
data class ExpertMonth(val points: ExpertPoints? = null, val shareUsd: String? = null)

@Serializable
data class ExpertSetup(
    val payoutReady: Boolean? = null,
    val showName: Boolean? = null,
    val profileFilled: Boolean? = null,
)

@Serializable
data class ExpertSummary(
    val name: String? = null,
    val sectors: List<String>? = null,
    val waiting: ExpertWaiting? = null,
    val month: ExpertMonth? = null,
    val setup: ExpertSetup? = null,
    val affiliationsExcluded: Int? = null,
    /** The earnings guide (web page + the same as ONE PDF, absolute) — never re-authored here. */
    val guideUrl: String? = null,
    val guidePdfUrl: String? = null,
)

/** GET /workis/expert/guide/ — Markdown sections rendered natively; order and count are the server's. */
@Serializable
data class GuideSection(val key: String? = null, val title: String? = null, val markdown: String? = null)

@Serializable
data class ExpertGuide(
    val success: Boolean? = null,
    val version: String? = null,
    val contentHash: String? = null,
    val updatedAt: String? = null,
    val sections: List<GuideSection>? = null,
)

@Serializable
data class ReviewUnit(
    @Serializable(with = FlexString::class) val id: String? = null,
    val text: String? = null,
    val authorRole: String? = null,
    val status: String? = null,
)

@Serializable
data class ReviewSession(
    val sessionKey: String? = null,
    val question: String? = null,
    val surface: String? = null,
    val at: String? = null,
    val units: List<ReviewUnit>? = null,
)

@Serializable
data class CompanionPair(
    @Serializable(with = FlexString::class) val id: String? = null,
    val a: String? = null,
    val b: String? = null,
    val relation: String? = null,
    val rationale: String? = null,
    val via: String? = null,
    val quote: String? = null,
    val count: Int? = null,
)

@Serializable
data class ExpertReviews(
    val sessions: List<ReviewSession>? = null,
    val companions: List<CompanionPair>? = null,
)

@Serializable
data class KnowledgeUnit(
    @Serializable(with = FlexString::class) val id: String? = null,
    val text: String? = null,
    val status: String? = null, // "active" | "retired"
    val useCount: Int? = null,
    val likeCount: Int? = null,
)

@Serializable
data class KnowledgeNote(
    @Serializable(with = FlexString::class) val id: String? = null,
    val createdAt: String? = null,
    val textPreview: String? = null,
    val unitTotal: Int? = null,
    val useTotal: Int? = null,
    val likeTotal: Int? = null,
    val units: List<KnowledgeUnit>? = null,
)

@Serializable
data class ExpertKnowledge(val unitCount: Int? = null, val notes: List<KnowledgeNote>? = null)

@Serializable
data class KnowledgeConflict(
    val newText: String? = null,
    @Serializable(with = FlexString::class) val oldId: String? = null,
    val oldText: String? = null,
)

@Serializable
data class KnowledgeSaveResult(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    @Serializable(with = FlexString::class) val noteId: String? = null,
    val unitCount: Int? = null,
    val conflicts: List<KnowledgeConflict>? = null,
)

@Serializable
data class ToggleResult(
    val success: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class ConsultBuyer(val short: String? = null, val name: String? = null)

@Serializable
data class ExpertConsult(
    @Serializable(with = FlexString::class) val id: String? = null,
    val buyer: ConsultBuyer? = null,
    val updatedAt: String? = null,
    val lastText: String? = null,
    val lastRole: String? = null,
    val lastAt: String? = null,
    val unread: Int? = null,
) {
    val title: String get() = buyer?.short ?: buyer?.name ?: "?"
}

@Serializable
data class ExpertConsults(val consults: List<ExpertConsult>? = null)

@Serializable
data class ConsultMessage(
    @Serializable(with = FlexString::class) val id: String? = null,
    val role: String? = null, // buyer | expert | system
    val text: String? = null,
    val at: String? = null,
    /** Machine translation into the reader's language (2026-09-09); the original stays in `text`. */
    val textLocalized: String? = null,
    val lang: String? = null,
)

@Serializable
data class ExpertConsultDetail(
    @Serializable(with = FlexString::class) val id: String? = null,
    val buyer: ConsultBuyer? = null,
    val messages: List<ConsultMessage>? = null,
)

@Serializable
data class ExpertAnswerBody(val answer: String, val teach: Boolean = true)

@Serializable
data class CloseReviewBody(val retire: List<String>, val correction: String? = null)

@Serializable
data class VerdictBody(val verdict: String) // confirm | reject

@Serializable
data class KnowledgeBody(val text: String)

@Serializable
data class TextBody(val text: String)

@Serializable
data class UnavailableBody(val note: String? = null)

// §10.2 re-acceptance (every seat): GET /workis/agreement/pending/ → the paper once per launch.

@Serializable
data class AgreementPendingMeta(
    val key: String? = null,
    val version: Int? = null,
    val title: String? = null,
    val labelTr: String? = null,
    val labelEn: String? = null,
    val sha: String? = null,
    val createdAt: String? = null,
    val pageUrl: String? = null,
    val pdfUrl: String? = null,
)

@Serializable
data class AgreementPending(
    val seat: String? = null,
    val key: String? = null,
    val pending: Boolean = false,
    val agreement: AgreementPendingMeta? = null,
)

@Serializable
data class AcceptAgreementBody(val signerName: String)
