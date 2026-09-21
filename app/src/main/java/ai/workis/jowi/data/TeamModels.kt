package ai.workis.jowi.data

import kotlinx.serialization.Serializable
import retrofit2.HttpException

/**
 * Ekip — a firm's colleagues act in the FIRM's name (API_CONTRACT "Team seats").
 * The admin seat invites and removes; a colleague trades with equal authority.
 *
 * Two rules shape every type here. **The app reads flags and derives nothing:**
 * `canInvite`, `canManage`, a row's own `canChangeScope`, `canTrade` — `myScope`
 * is decoded because the server sends it, and deliberately never read, because
 * the server's `SeatScopeMiddleware` already turns every `can…` false for a
 * view seat. **The server owns the sentences:** `inviteConsent`, `acceptConsent`,
 * `scopeMeans`, `adminTransfer`, `viewSeatNote` and every refusal's `error` are
 * printed as they arrive; none is in the string catalogue and none has a
 * fallback of ours — no sentence, no row.
 */

/** One row of the scope picker, worded by the server. */
@Serializable
data class TeamScope(
    val value: String? = null,
    val label: String? = null,
    /** What choosing this means, in the server's words. */
    val means: String? = null,
)

@Serializable
data class TeamSeat(
    @Serializable(with = FlexString::class) val userId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val admin: Boolean? = null,
    val me: Boolean? = null,
    val since: String? = null,
    val lastSignIn: String? = null,
    val scope: String? = null,
    val scopeLabel: String? = null,
    val scopeMeans: String? = null,
    /** The ROW's own permission — never the caller's, never a scope comparison. */
    val canChangeScope: Boolean? = null,
)

@Serializable
data class TeamInvitation(
    @Serializable(with = FlexString::class) val id: String? = null,
    val email: String? = null,
    val invitedBy: String? = null,
    val invitedAt: String? = null,
    val expiresAt: String? = null,
    val scope: String? = null,
    val scopeLabel: String? = null,
    val scopeMeans: String? = null,
)

@Serializable
data class Team(
    val success: Boolean = false,
    /** The switch. While it is false the Ekip row does not exist at all. */
    val enabled: Boolean? = null,
    val seatLimit: Int? = null,
    val seatsUsed: Int? = null,
    val canInvite: Boolean? = null,
    val canManage: Boolean? = null,
    /** The inviter's binding sentence — the server's, localized. */
    val inviteConsent: String? = null,
    /** Decoded because it arrives; never read. The flags decide. */
    val myScope: String? = null,
    val canTrade: Boolean? = null,
    val canChangeScope: Boolean? = null,
    /** The one line saying what THIS seat is — empty unless it is a view seat. */
    val viewSeatNote: String? = null,
    val scopes: List<TeamScope>? = null,
    val supportEmail: String? = null,
    /** Where an admin handover goes, in the server's words. */
    val adminTransfer: String? = null,
    val seats: List<TeamSeat>? = null,
    val invitations: List<TeamInvitation>? = null,
)

/** An invitation waiting for THIS person — no token: `accept/` goes by id. */
@Serializable
data class MyInvitation(
    @Serializable(with = FlexString::class) val id: String? = null,
    val company: String? = null,
    val invitedBy: String? = null,
    val expiresAt: String? = null,
    /** What saying yes means, per row, in the server's words. */
    val acceptConsent: String? = null,
    val scope: String? = null,
    val scopeLabel: String? = null,
    val scopeMeans: String? = null,
)

@Serializable
data class MyInvitations(
    val success: Boolean = false,
    val invitations: List<MyInvitation>? = null,
)

@Serializable
data class TeamInviteBody(val email: String, val scope: String)

@Serializable
data class TeamScopeBody(val scope: String)

/** The name IS the signature: the server stores name · date · IP. */
@Serializable
data class InvitationAcceptBody(val signerName: String)

@Serializable
data class InvitationAcceptResult(
    val success: Boolean = false,
    val company: String? = null,
    val code: String? = null,
    val error: String? = null,
)

/** `{success: false, code, error}` — the `code` carries the meaning, not the status. */
@Serializable
data class TeamError(
    val success: Boolean = false,
    val code: String? = null,
    val error: String? = null,
)

/**
 * What a team call can answer. [Off] is the switch — `off` 404 is not a
 * failure and never reaches the person; [Refused] carries the server's own
 * sentence, which is the only sentence shown.
 */
sealed interface TeamOutcome<out T> {
    data class Ok<T>(val value: T) : TeamOutcome<T>
    data object Off : TeamOutcome<Nothing>
    data class Refused(val code: String?, val error: String?) : TeamOutcome<Nothing>
}

/**
 * Branch on `code`, never on the status: `admin_scope` is 400 and `admin` is
 * 403, and which number carries which meaning is the server's business.
 */
suspend fun <T> teamCall(lang: String, call: suspend () -> T): TeamOutcome<T> =
    try {
        TeamOutcome.Ok(call())
    } catch (e: HttpException) {
        val body = runCatching {
            WorkisJson.decodeFromString<TeamError>(e.response()?.errorBody()?.string().orEmpty())
        }.getOrNull()
        if (body?.code == "off") TeamOutcome.Off
        else TeamOutcome.Refused(body?.code, body?.error?.takeIf { it.isNotBlank() })
    } catch (e: java.io.IOException) {
        TeamOutcome.Refused(null, statusMessage(null, lang))
    } catch (e: Exception) {
        TeamOutcome.Refused(null, statusMessage(-1, lang))
    }
