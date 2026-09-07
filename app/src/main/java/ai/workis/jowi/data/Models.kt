package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// api/v1 responses are camelCase; legacy auth nests snake_case inside `user`,
// so `user` stays a raw JsonObject (single source of truth, like iOS).
// New backend fields must decode optionally → every field nullable + ignoreUnknownKeys.

@Serializable
data class AuthResponse(
    val success: Boolean = false,
    val message: String? = null,
    val token: String? = null,
    val user: JsonObject? = null,
)

@Serializable
data class OtpRequestBody(val email: String)

@Serializable
data class OtpVerifyBody(val email: String, val code: String)

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
data class ApplyAskBody(val q: String, val lang: String? = null)

@Serializable
data class ApplyAskResponse(val ok: Boolean = false, val text: String? = null)

@Serializable
data class AskBody(
    val q: String,
    val page: String? = null,
    val ctxList: String? = null,
    val voice: Boolean? = null,
)

@Serializable
data class AskResponse(
    val success: Boolean = false,
    val answer: String? = null,
    val source: String? = null,
)
