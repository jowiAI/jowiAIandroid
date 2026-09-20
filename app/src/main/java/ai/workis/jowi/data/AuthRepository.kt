package ai.workis.jowi.data

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

    suspend fun verifyOtp(email: String, code: String): ApiResult<Unit> {
        val r = safeCall(lang()) { applyAuth(api.otpVerify(OtpVerifyBody(normalizedEmail(email), code.trim()))) }
        if (r is ApiResult.Ok) syncPreferredLanguage()
        return r
    }

    /** How this session was opened — the server's word, on every `user` payload. */
    fun signedInWith(): String? = _user.value.signedInWith()

    /**
     * The language picked on the LOGIN screen never reached the account — only
     * Hesap wrote it. A saved preference BEATS `Accept-Language` (§3), so an
     * English screen kept getting the server's sentences in the stored Turkish.
     * Every sign-in now carries the choice to the account when the two differ.
     */
    suspend fun syncPreferredLanguage() {
        if (_user.value == null) return
        val want = lang()
        val stored = _user.value?.let { u ->
            u["preferredLanguage"]?.jsonPrimitive?.contentOrNull
                ?: u["preferred_language"]?.jsonPrimitive?.contentOrNull
        }
        // an ABSENT field counts as different (iOS does the same): the trap is a
        // stored preference the app never wrote, and staying quiet would keep it
        if (stored == want) return
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
     * Launch check — a three-way decision: only the server's "no" (401/403 or
     * success:false) wipes the token; a timeout (15 s), offline or a 5xx keeps
     * the stored session and lets the user in. (The 8 s answers were cold
     * gunicorn workers after deploys — fixed server-side 2026-09-11.)
     */
    suspend fun validate(): Boolean {
        if (store.token == null) return false
        val r = withTimeoutOrNull(15_000) { safeCall(lang()) { api.validateToken() } }
            ?: return true // unreachable in time → keep the session
        return when (r) {
            is ApiResult.Ok -> {
                if (r.value.success || r.value.token != null || r.value.user != null) {
                    applyAuth(r.value)
                    syncPreferredLanguage() // a language switched before this launch still reaches the account
                    true
                } else {
                    signOutLocally() // the server said no
                    false
                }
            }
            is ApiResult.Err -> {
                if (r.status == 401 || r.status == 403) { signOutLocally(); false } else true
            }
        }
    }

    suspend fun setPreferredLanguage(code: String): ApiResult<Unit> =
        safeCall(lang()) { applyAuth(api.updateProfile(ProfileUpdateBody(code))) }

    suspend fun logout() {
        safeCall(lang()) { api.logout() }
        signOutLocally()
    }

    fun signOutLocally() {
        store.clear()
        _user.value = null
        _agreementPending.value = null
        runCatching { ai.workis.jowi.Graph.partner.clear() }
        ScreenContext.clear() // no screen of the previous seat rides into the next session
        _isAuthenticated.value = false
    }
}
