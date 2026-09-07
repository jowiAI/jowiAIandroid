package ai.workis.jowi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    suspend fun requestOtp(email: String): ApiResult<Unit> =
        safeCall(lang()) { api.otpRequest(OtpRequestBody(email)); Unit }

    suspend fun verifyOtp(email: String, code: String): ApiResult<Unit> =
        safeCall(lang()) { applyAuth(api.otpVerify(OtpVerifyBody(email, code))) }

    /** Legacy password login — kept for testing while the OTP backend gap is open. */
    suspend fun loginWithPassword(email: String, password: String): ApiResult<Unit> =
        safeCall(lang()) { applyAuth(api.login(LoginBody(email, password))) }

    /** Launch check: token exists → validate; 401 kills the token. */
    suspend fun validate(): Boolean {
        if (store.token == null) return false
        return when (val r = safeCall(lang()) { api.validateToken() }) {
            is ApiResult.Ok -> {
                applyAuth(r.value)
                true
            }
            is ApiResult.Err -> {
                if (r.status == 401) signOutLocally()
                // network error → keep token, still let the user in
                r.status == null
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
        _isAuthenticated.value = false
    }
}
