package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Passkeys (API_CONTRACT §2). The app carries JSON and decides nothing: the
 * server's `options` object goes to Credential Manager AS IT IS, and what the
 * system hands back goes to `…/verify/` AS IT IS. `rp.id` is `workis.ai`, the
 * credential never leaves the device, and the four `can…` flags below say what
 * THIS session may do — never derive one.
 *
 * Android's origin is `android:apk-key-hash:<base64url SHA-256 of the signing
 * certificate>`, so every signing key needs its fingerprint in Django and in
 * `/.well-known/assetlinks.json` — until then every assertion fails `verify`.
 */

/** `{success: false, code, error}` — `error` follows Accept-Language; print it as is. */
@Serializable
data class PasskeyError(
    val success: Boolean = false,
    /** ticket · verify · unknown · exists · not_found · need_passkey */
    val code: String? = null,
    val error: String? = null,
)

/** The challenge named server-side: single use, five minutes. */
@Serializable
data class PasskeyOptions(
    val success: Boolean = false,
    val ticket: String? = null,
    /** The W3C JSON shape, untouched — handed to Credential Manager verbatim. */
    val options: JsonObject? = null,
    val code: String? = null,
    val error: String? = null,
)

@Serializable
data class PasskeyRegisterBody(
    val ticket: String,
    val deviceLabel: String? = null,
    val credential: JsonObject,
)

@Serializable
data class PasskeyLoginBody(val ticket: String, val credential: JsonObject)

/** `{allowed, ticket, credential}` — the switch is turned with a FRESH assertion. */
@Serializable
data class PasskeyCodeSignInBody(
    val allowed: Boolean,
    val ticket: String,
    val credential: JsonObject,
)

@Serializable
data class Passkey(
    val id: Int? = null,
    val label: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    /** backupState: a synced key survives the phone; a device-bound one does not. */
    val synced: Boolean? = null,
    val platform: String? = null, // apple | android | other — a hint for the row icon
)

@Serializable
data class PasskeyRegisterResult(
    val success: Boolean = false,
    val passkey: Passkey? = null,
    val code: String? = null,
    val error: String? = null,
)

/**
 * What this session may do, decided by the server. A code-opened session on an
 * account that already has a passkey may not add one, remove one, turn the
 * switch or change the e-mail — that is the whole point of the rule.
 */
@Serializable
data class PasskeyList(
    val success: Boolean = false,
    val passkeys: List<Passkey>? = null,
    val codeSignInAllowed: Boolean? = null,
    val canAddPasskey: Boolean? = null,
    val canRemovePasskey: Boolean? = null,
    val canChangeCodeSignIn: Boolean? = null,
    val canChangeEmail: Boolean? = null,
    val code: String? = null,
    val error: String? = null,
)

@Serializable
data class CodeSignInResult(
    val success: Boolean = false,
    val codeSignInAllowed: Boolean? = null,
    val code: String? = null,
    val error: String? = null,
)
