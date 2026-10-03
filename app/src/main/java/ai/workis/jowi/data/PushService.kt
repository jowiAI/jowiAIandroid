package ai.workis.jowi.data

import ai.workis.jowi.Graph
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** `POST push/register/` — `device` is the model only (the device's name is personal). */
@Serializable
data class PushRegisterBody(val platform: String, val token: String, val device: String? = null, val lang: String? = null)

@Serializable
data class PushUnregisterBody(val platform: String, val token: String)

/** `{success, ready}` from register, `{success, removed}` from unregister; `ready: false` = stored, carrier not switched on. */
@Serializable
data class PushReply(
    val success: Boolean? = null,
    val ready: Boolean? = null,
    val removed: JsonElement? = null,
    val error: String? = null,
)

/**
 * Push — the FCM twin of iOS `WorkisPush` (ledger 44/45; API_CONTRACT "APNs"). The push names
 * only SCOPES, the words of `GET changes/` (`orders` · `quotes` · `cargo`); on arrival the
 * watching screen re-reads ([ChangeStamps.pushed]) — the push never carries the record.
 *
 * Hesap → Bildirimler is the person's choice, off by default; POST_NOTIFICATIONS is asked only
 * there, never at launch. `register/` goes out on every sign-in (and launch with a session)
 * while the switch is on; `unregister/` when it goes off and on sign-out, with the outgoing key.
 *
 * THE SEAM. There is no Firebase in this build, so [tokenSource] is null and nothing is ever
 * registered: Django knows only `apns` and `web` today and files any other platform as an APNs
 * token, so a register without a real FCM token must never leave the device. When the carrier
 * lands (Django's FCM sender + `google-services.json` + the firebase-messaging dependency):
 *  - at start-up set `PushService.tokenSource = { FirebaseMessaging.getInstance().token.await() }`;
 *  - a `FirebaseMessagingService` calls [tokenArrived] from `onNewToken` and [messageArrived]
 *    with `message.data` from `onMessageReceived` (foreground: no banner, the screen re-reads);
 *  - a tapped notification's intent extras go to [messageArrived] the same way.
 */
object PushService {
    const val PLATFORM = "fcm"
    private const val ENABLED = "workis_push_enabled"
    private const val TOKEN = "workis_push_token"
    /** The server's last `ready`: 1 = carrier on, 0 = stored but not switched on, -1 = unknown. */
    private const val READY = "workis_push_ready"

    /** Where the device token comes from — null = no push carrier in this build (today). */
    @Volatile
    var tokenSource: (suspend () -> String?)? = null

    val hasCarrier: Boolean get() = tokenSource != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs get() = Graph.prefs

    private val _enabled by lazy { MutableStateFlow(prefs.getBoolean(ENABLED, false)) }
    val enabled: StateFlow<Boolean> get() = _enabled
    private val _ready by lazy { MutableStateFlow(prefs.getInt(READY, -1)) }
    val ready: StateFlow<Int> get() = _ready

    private val storedToken: String? get() = prefs.getString(TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Once, from the Application: every session that opens (sign-in or a stored one) registers. */
    fun start() {
        scope.launch { Graph.auth.isAuthenticated.collect { signedIn -> if (signedIn) register() } }
    }

    /** The Hesap switch. ON only after the permission answered yes (Android 13+). */
    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(ENABLED, on).apply()
        _enabled.value = on
        scope.launch { if (on) register() else unregister() }
    }

    /** FCM's `onNewToken`: keep it, and register it if the person wants push. */
    fun tokenArrived(token: String) {
        if (token.isBlank()) return
        prefs.edit().putString(TOKEN, token).apply()
        scope.launch { register() }
    }

    /**
     * A message's data (foreground arrival or a tap): its `scopes` — a JSON array or a
     * comma list — reach the watchers; `thread-id` / `scope` stand in when `scopes` is missing.
     */
    fun messageArrived(data: Map<String, String>) {
        val raw = data["scopes"].orEmpty().trim()
        val parsed = runCatching { WorkisJson.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: raw.trim('[', ']').split(',').map { it.trim().trim('"') }
        val scopes = parsed.filter { it.isNotEmpty() }
            .ifEmpty { listOfNotNull((data["thread-id"] ?: data["scope"])?.takeIf { it.isNotBlank() }) }
        scope.launch { ChangeStamps.pushed(scopes) }
    }

    /** Sign-out: unregister with the OUTGOING key before the session is ended (8 s at most). */
    suspend fun signingOut() {
        withTimeoutOrNull(8_000) { unregister() }
    }

    private suspend fun register() {
        if (!_enabled.value || !Graph.auth.hasToken) return
        // never without a real token — Django would store anything else as an APNs token
        val token = currentToken() ?: return
        val r = runCatching {
            Graph.api.pushRegister(PushRegisterBody(PLATFORM, token, Build.MODEL, Graph.language.value))
        }.getOrNull() ?: return
        if (r.success == true) setReady(if (r.ready == true) 1 else 0)
    }

    private suspend fun unregister() {
        val token = storedToken ?: return
        if (Graph.auth.hasToken) runCatching { Graph.api.pushUnregister(PushUnregisterBody(PLATFORM, token)) }
        setReady(-1)
    }

    private suspend fun currentToken(): String? {
        val src = tokenSource ?: return null
        val fresh = runCatching { src() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (fresh != null && fresh != storedToken) prefs.edit().putString(TOKEN, fresh).apply()
        return fresh ?: storedToken
    }

    private fun setReady(v: Int) {
        prefs.edit().putInt(READY, v).apply()
        _ready.value = v
    }
}
