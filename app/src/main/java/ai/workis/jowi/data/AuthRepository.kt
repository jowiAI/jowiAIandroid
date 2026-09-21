package ai.workis.jowi.data

import ai.workis.jowi.Graph
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

/** Android twin of iOS AuthService: raw Django user payload is the source of truth. */
class AuthRepository(
    private val api: WorkisApi,
    private val store: TokenStore,
    private val lang: () -> String,
) {

    private val _user = MutableStateFlow(loadStoredUser())
    val user: StateFlow<JsonObject?> = _user

    private val _isAuthenticated = MutableStateFlow(store.token != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    val hasToken: Boolean get() = store.token != null

    private fun loadStoredUser(): JsonObject? =
        store.userJson?.let { runCatching { WorkisJson.decodeFromString<JsonObject>(it) }.getOrNull() }

    // role: 1 Partner, 2 Guest, 3 Coordinator, 4 Region lead, 5 Expert — exact-match, never >=
    fun roleCode(): Int? = _user.value?.get("role")?.jsonPrimitive?.intOrNull
    fun email(): String? = _user.value?.get("email")?.jsonPrimitive?.contentOrNull
    fun displayName(): String? =
        _user.value?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: _user.value?.get("fullName")?.jsonPrimitive?.contentOrNull
    fun roleDisplay(): String? =
        _user.value?.get("role_display")?.jsonPrimitive?.contentOrNull
    fun isCoordinator(): Boolean = roleCode() == 3 || roleCode() == 4
    /** The expert seat gets its own Panel and holds no buyer seat (Cases hidden). */
    fun isExpert(): Boolean = roleCode() == 5

    // §10.2 re-acceptance state — asked after sign-in; the Hesap reminder row reads it too.
    private val _agreementPending = MutableStateFlow<AgreementPending?>(null)
    val agreementPending: StateFlow<AgreementPending?> = _agreementPending

    suspend fun refreshAgreementPending() {
        when (val r = safeCall(lang()) { api.agreementPending() }) {
            is ApiResult.Ok -> _agreementPending.value = r.value
            is ApiResult.Err -> Unit // 403 no_seat / network: nothing pending to show
        }
    }

    /** POST /agreement/accept/ {signerName} — same evidence chain as the web. */
    suspend fun acceptAgreement(signerName: String): ApiResult<SimpleResult> {
        val r = safeCall(lang()) { api.agreementAccept(AcceptAgreementBody(signerName)) }
        if (r is ApiResult.Ok && r.value.success) refreshAgreementPending()
        return r
    }

    private fun applyAuth(resp: AuthResponse) {
        resp.token?.let { store.token = it }
        resp.user?.let {
            store.userJson = it.toString()
            _user.value = it
        }
        _isAuthenticated.value = store.token != null
    }

    /**
     * Addresses go out trimmed and lower-cased (2026-09-13): a mixed-case address
     * once produced a twin account beside the seat's; Django is case-insensitive
     * too, but the client must not feed the difference.
     */
    private fun normalizedEmail(email: String) = email.trim().lowercase()

    suspend fun requestOtp(email: String): ApiResult<Unit> =
        safeCall(lang()) { api.otpRequest(OtpRequestBody(normalizedEmail(email))); Unit }

    /**
     * A correct code does not always open a session: an address nobody has an
     * account under is ASKED first, so that a mistyped address stops becoming
     * an empty Guest seat. The answer says `newAccount` — the app decides on
     * that word, never on the absence of a token.
     */
    sealed interface SignIn {
        data object Opened : SignIn
        data class ConfirmSignup(val ticket: String, val email: String) : SignIn
    }

    suspend fun verifyOtp(email: String, code: String): ApiResult<SignIn> {
        val r = safeCall(lang()) { api.otpVerify(OtpVerifyBody(normalizedEmail(email), code.trim())) }
        return when (r) {
            is ApiResult.Err -> r
            is ApiResult.Ok -> {
                val resp = r.value
                val ticket = resp.signupTicket
                if (resp.newAccount == true && !ticket.isNullOrEmpty()) {
                    // nothing is stored: there is no session until the person says yes
                    ApiResult.Ok(SignIn.ConfirmSignup(ticket, resp.email ?: normalizedEmail(email)))
                } else {
                    applyAuth(resp)
                    syncPreferredLanguage()
                    ApiResult.Ok(SignIn.Opened)
                }
            }
        }
    }

    /**
     * The person said yes. The session opens exactly as an OTP verify does —
     * the language rule included. A spent, unknown or ten-minute-old ticket
     * comes back 400 with the sentence to show.
     */
    suspend fun confirmSignup(ticket: String): ApiResult<Unit> =
        try {
            applyAuth(api.signupConfirm(SignupConfirmBody(ticket)))
            syncPreferredLanguage()
            ApiResult.Ok(Unit)
        } catch (e: HttpException) {
            ApiResult.Err(e.code(), messageFromBody(e, lang()))
        } catch (e: java.io.IOException) {
            ApiResult.Err(null, statusMessage(null, lang()))
        } catch (e: Exception) {
            ApiResult.Err(null, statusMessage(-1, lang()))
        }

    /** How this session was opened — the server's word, on every `user` payload. */
    fun signedInWith(): String? = _user.value.signedInWith()

    /**
     * The language picked on the LOGIN screen never reached the account — only
     * Hesap wrote it — and a saved preference BEATS `Accept-Language` (§3), so
     * an English screen kept getting the server's sentences in the stored
     * Turkish.
     *
     * But the field is ONE per account and drives the mails and `textLocalized`
     * everywhere, so Django's rule (2026-09-20) governs what may write it: only
     * when the account has none saved, or when the person changed the language
     * inside the app. A device that merely disagrees must never silently
     * overwrite a choice someone made deliberately on the web.
     */
    suspend fun syncPreferredLanguage() {
        if (_user.value == null) return
        val want = lang()
        val stored = _user.value?.let { u ->
            u["preferredLanguage"]?.jsonPrimitive?.contentOrNull
                ?: u["preferred_language"]?.jsonPrimitive?.contentOrNull
        }
        val picked = Graph.languageWasPicked()
        if (stored == want) { Graph.clearLanguagePicked(); return }
        // (c) a language is already saved and this device did not pick one: the
        // app TAKES the account's, so a choice made on the web travels here
        if (!stored.isNullOrEmpty() && !picked) { Graph.adoptLanguage(stored); return }
        // (a) a deliberate pick, or (b) an empty preference — ours to write
        setPreferredLanguage(want)
    }

    /** A passkey sign-in answers in `otp/verify/`'s shape; it lands the same way. */
    suspend fun adoptPasskeySession(resp: AuthResponse) {
        applyAuth(resp)
        syncPreferredLanguage()
    }

    /**
     * The way out of a code-opened session on an account that HAS a passkey: its
     * security switch is locked and signing out would only send the person back
     * to the inbox. The new key is adopted IN PLACE and the replaced one is
     * ended server-side — but only if the answer is the SAME account, because a
     * phone may hold the passkeys of several.
     */
    suspend fun reopenWithPasskey(resp: AuthResponse): Boolean {
        val mine = _user.value.userId()
        val theirs = resp.user.userId()
        val replaced = store.token
        if (mine != null && theirs != null && mine != theirs) {
            // end the key we were just handed; the open session is left untouched
            resp.token?.let { fresh ->
                val previous = store.token
                store.token = fresh
                safeCall(lang()) { api.logout() }
                store.token = previous
            }
            return false
        }
        applyAuth(resp)
        syncPreferredLanguage()
        // the old key is a separate per-device session now: close it deliberately
        if (!replaced.isNullOrEmpty() && replaced != store.token) {
            val current = store.token
            store.token = replaced
            safeCall(lang()) { api.logout() }
            store.token = current
        }
        return true
    }

    /** Legacy password login — kept for testing while the OTP backend gap is open. */
    suspend fun loginWithPassword(email: String, password: String): ApiResult<Unit> =
        safeCall(lang()) { applyAuth(api.login(LoginBody(email, password))) }

    /**
     * Why a stored session was refused, for the login screen to say. Null means
     * the ordinary dead key — the screen shows its own neutral line then; a
     * sentence here is the SERVER's (`account_frozen`, `passkey_required`) and
     * is printed as it arrives, in the reader's language.
     */
    private val _refusalSentence = MutableStateFlow<String?>(null)
    val refusalSentence: StateFlow<String?> = _refusalSentence

    /** True once a stored session was refused; cleared when the screen has said so. */
    private val _sessionWasRefused = MutableStateFlow(false)
    val sessionWasRefused: StateFlow<Boolean> = _sessionWasRefused

    fun clearRefusal() { _sessionWasRefused.value = false; _refusalSentence.value = null }

    /**
     * Launch check — a three-way decision: only the server's "no" (401/403 or
     * success:false) wipes the token; a timeout (15 s), offline or a 5xx keeps
     * the stored session and lets the user in. (The 8 s answers were cold
     * gunicorn workers after deploys — fixed server-side 2026-09-11.)
     *
     * A refusal is never silent. Landing on a bare login screen reads as a bug
     * to the person: devices that signed in before the per-device keys shared
     * ONE key, so a sign-out on another phone ends this one — real users can
     * meet it once, and they deserve the sentence.
     */
    suspend fun validate(): Boolean {
        if (store.token == null) return false
        val r = withTimeoutOrNull(15_000) {
            try {
                ApiResult.Ok(api.validateToken())
            } catch (e: HttpException) {
                // the body carries `code` + `error` when the refusal has a reason
                if (e.code() == 401 || e.code() == 403) {
                    _refusalSentence.value = serverSentence(e)?.takeIf { it.isNotBlank() }
                }
                ApiResult.Err(e.code(), statusMessage(e.code(), lang()))
            } catch (e: java.io.IOException) {
                ApiResult.Err(null, statusMessage(null, lang()))
            } catch (e: Exception) {
                ApiResult.Err(null, statusMessage(-1, lang()))
            }
        } ?: return true // unreachable in time → keep the session
        return when (r) {
            is ApiResult.Ok -> {
                if (r.value.success || r.value.token != null || r.value.user != null) {
                    applyAuth(r.value)
                    syncPreferredLanguage() // a language switched before this launch still reaches the account
                    true
                } else {
                    refuse(r.value.message?.takeIf { it.isNotBlank() })
                    false
                }
            }
            is ApiResult.Err -> {
                if (r.status == 401 || r.status == 403) { refuse(_refusalSentence.value); false } else true
            }
        }
    }

    /** The server said no: end the session here and keep its words for the screen. */
    private fun refuse(sentence: String?) {
        signOutLocally()
        _refusalSentence.value = sentence
        _sessionWasRefused.value = true
    }

    /** The account now holds what was picked, so the pick has been spent. */
    suspend fun setPreferredLanguage(code: String): ApiResult<Unit> {
        val r = safeCall(lang()) { applyAuth(api.updateProfile(ProfileUpdateBody(code))) }
        if (r is ApiResult.Ok) Graph.clearLanguagePicked()
        return r
    }

    suspend fun logout() {
        safeCall(lang()) { api.logout() }
        signOutLocally()
    }

    fun signOutLocally() {
        store.clear()
        // a pick belonging to the session that just ended must not reach the next
        // account's stored preference
        Graph.clearLanguagePicked()
        _user.value = null
        _agreementPending.value = null
        runCatching { ai.workis.jowi.Graph.partner.clear() }
        ScreenContext.clear() // no screen of the previous seat rides into the next session
        _isAuthenticated.value = false
    }
}
