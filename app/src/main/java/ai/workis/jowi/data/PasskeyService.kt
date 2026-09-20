package ai.workis.jowi.data

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import ai.workis.jowi.Graph
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.HttpException

/**
 * Passkeys (API_CONTRACT §2). The system holds the key and draws its own sheet:
 * the app never writes a biometric prompt here, and it never derives a rule —
 * the server's `can…` flags and its `error` sentences are what the screens obey.
 *
 * The one thing this file guards is the pass-through: the server's `options`
 * object reaches Credential Manager unchanged, and the JSON the system returns
 * reaches `…/verify/` unchanged. A `ticket` is single use and lives five
 * minutes, so every flow fetches its own right before the person acts.
 *
 * Below API 28 there are no platform passkeys; those devices stay on the
 * e-mail code, which is why every door is behind [available].
 */
class PasskeyService(private val api: WorkisApi, private val lang: () -> String) {

    /** A closed sheet is not a failure — the caller shows nothing. */
    class Cancelled : Exception()

    /** The server refused, in its own words (`code` says which wall). */
    class Refused(val code: String?, override val message: String) : Exception(message)

    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private fun manager(context: Context) = CredentialManager.create(context)

    /** The sentence to print when a call fails: the server's, else the status idiom. */
    private fun refusal(e: Throwable): Refused = when (e) {
        is HttpException -> {
            val body = runCatching {
                WorkisJson.decodeFromString<PasskeyError>(e.response()?.errorBody()?.string().orEmpty())
            }.getOrNull()
            Refused(body?.code, body?.error?.takeIf { it.isNotBlank() } ?: statusMessage(e.code(), lang()))
        }
        else -> Refused(null, statusMessage(null, lang()))
    }

    private fun optionsJson(o: PasskeyOptions): Pair<String, String> {
        val ticket = o.ticket
        val options = o.options
        if (!o.success || ticket.isNullOrEmpty() || options == null) {
            throw Refused(o.code, o.error?.takeIf { it.isNotBlank() } ?: statusMessage(null, lang()))
        }
        return ticket to options.toString()
    }

    private fun credentialOf(json: String): JsonObject =
        WorkisJson.decodeFromString(json)

    // ---------- enrolment

    /**
     * The first passkey is necessarily enrolled from a code-opened session; once
     * the account has one, `register/options/` answers 403 `need_passkey`.
     */
    suspend fun register(context: Context, deviceLabel: String?): Passkey {
        val (ticket, options) = runCatching { optionsJson(api.passkeyRegisterOptions()) }
            .getOrElse { throw if (it is Refused) it else refusal(it) }
        val created = try {
            manager(context).createCredential(context, CreatePublicKeyCredentialRequest(options))
        } catch (e: CreateCredentialCancellationException) {
            throw Cancelled()
        } catch (e: CreateCredentialException) {
            throw Refused(null, e.errorMessage?.toString().orEmpty().ifBlank { statusMessage(null, lang()) })
        }
        val json = (created as? CreatePublicKeyCredentialResponse)?.registrationResponseJson ?: throw Cancelled()
        val r = runCatching { api.passkeyRegisterVerify(PasskeyRegisterBody(ticket, deviceLabel, credentialOf(json))) }
            .getOrElse { throw refusal(it) }
        if (!r.success) throw Refused(r.code, r.error.orEmpty().ifBlank { statusMessage(null, lang()) })
        return r.passkey ?: Passkey()
    }

    // ---------- the assertion, used by three doors

    /** Runs the system sheet against the given options and returns its JSON. */
    private suspend fun assert(context: Context, o: PasskeyOptions): Pair<String, JsonObject> {
        val (ticket, options) = optionsJson(o)
        val got = try {
            manager(context).getCredential(
                context,
                GetCredentialRequest(listOf(GetPublicKeyCredentialOption(options))),
            )
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (e: GetCredentialException) {
            throw Refused(null, e.errorMessage?.toString().orEmpty().ifBlank { statusMessage(null, lang()) })
        }
        val cred = got.credential as? PublicKeyCredential ?: throw Cancelled()
        return ticket to credentialOf(cred.authenticationResponseJson)
    }

    /**
     * Sign in with no e-mail asked — the credential is discoverable. The answer
     * is `otp/verify/`'s shape exactly, so the caller applies it the same way.
     */
    suspend fun signIn(context: Context): AuthResponse {
        val options = runCatching { api.passkeyLoginOptions() }.getOrElse { throw refusal(it) }
        val (ticket, credential) = assert(context, options)
        val r = runCatching { api.passkeyLoginVerify(PasskeyLoginBody(ticket, credential)) }
            .getOrElse { throw refusal(it) }
        if (!r.success && r.token == null) throw Refused(null, r.message.orEmpty().ifBlank { statusMessage(null, lang()) })
        return r
    }

    /**
     * The fresh assertion the one switch needs. `allowed` is what the person
     * asked for; the server answers with what now stands.
     */
    suspend fun setCodeSignIn(context: Context, allowed: Boolean): Boolean {
        val options = runCatching { api.passkeyStepUpOptions() }.getOrElse { throw refusal(it) }
        val (ticket, credential) = assert(context, options)
        val r = runCatching { api.passkeyCodeSignIn(PasskeyCodeSignInBody(allowed, ticket, credential)) }
            .getOrElse { throw refusal(it) }
        if (!r.success) throw Refused(r.code, r.error.orEmpty().ifBlank { statusMessage(null, lang()) })
        return r.codeSignInAllowed ?: allowed
    }

    // ---------- the list

    suspend fun list(): PasskeyList = runCatching { api.passkeys() }.getOrElse { throw refusal(it) }

    suspend fun remove(id: Int) {
        val r = runCatching { api.passkeyDelete(id) }.getOrElse { throw refusal(it) }
        if (!r.success) throw Refused(null, r.error ?: r.message ?: statusMessage(null, lang()))
    }
}

/** `user.signedInWith` — "passkey" | "code". The server says it; the app never guesses. */
fun JsonObject?.signedInWith(): String? = this?.get("signedInWith")?.jsonPrimitive?.contentOrNull

/** The account behind a `user` payload, for the "same account" check on a re-open. */
fun JsonObject?.userId(): Int? = this?.get("id")?.jsonPrimitive?.intOrNull

/** A row label for a newly enrolled key: the phone's own name. */
fun deviceLabel(): String =
    listOfNotNull(Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, Build.MODEL)
        .joinToString(" ").trim().ifEmpty { "Android" }
