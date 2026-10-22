package ai.workis.jowi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/**
 * Kararlar — the Jowi HOSPITALITY surface (ledgers 20–33; API_CONTRACT "Jowi ·
 * hospitality — the decision a manager takes away from a desk", and "What this
 * account has access to — auth/workspaces/").
 *
 * WHY `Decision…` AND NOT `Partner…`: `/api/v1/jowi/decisions/` is a Jowi
 * WORKSPACE surface — a hotel manager with no Workis seat and no intention of
 * one. Nothing here passes the seat wall (`SeatScopeMiddleware` looks only at
 * `/workis/`). `auth/workspaces/` rides along because it decides WHICH of the
 * two surfaces a signed-in account gets.
 *
 * Three rules from the contract shape every type below:
 *  • THE SERVER RESOLVES, THE APP DRAWS. She never picks an operator or a
 *    contract; `resolved` carries what Jowi read and NOTHING else — a discount
 *    it could not parse is ABSENT, never zero. Every field is optional and an
 *    absent one draws no chip.
 *  • `deliveredAt` IS THE MOMENT THE MAIL LEFT. No stamp = not told. A failed
 *    send writes the server's sentence to `deliveryError` — shown as it arrives,
 *    never reworded, never in the catalogue.
 *  • THE WRITTEN RECORD IS WHOLE. No delete, no edit — there is no such door.
 *    `retract` only while `canRetract` (the server's word, never derived from
 *    `seenAt`); the row STAYS with `retractedAt`; a seen row answers 409.
 *
 * Money is a decimal STRING. `Double` appears nowhere in this file.
 */

// MARK: - auth/workspaces/

/**
 * `GET auth/workspaces/` → `{success, jowi: {available, workspaces[]}, workis: {available,
 * hasSeat, …}, account {id, email, name}}`. Two NAMED objects, not a flat list with a
 * `kind` — `jowi.workspaces` IS the server saying "these are the Jowi ones", so the app
 * derives nothing. `available` ≠ `hasSeat`: the first says the marketplace is installed,
 * the second that THIS account holds a seat.
 */
@Serializable
data class JowiWorkspaces(
    val success: Boolean? = null,
    val jowi: JowiSide? = null,
    val workis: WorkisSide? = null,
    val account: WorkspaceAccount? = null,
) {
    val rooms: List<JowiWorkspace> get() = jowi?.workspaces.orEmpty()
    /** Kararlar opens on a HOSPITALITY room — `industry` says which Jowi surface a room wants. */
    val hasHospitality: Boolean get() = rooms.any { it.industry == "hospitality" }
    val hospitality: JowiWorkspace? get() = rooms.firstOrNull { it.industry == "hospitality" }
}

@Serializable
data class JowiSide(val available: Boolean? = null, val workspaces: List<JowiWorkspace>? = null)

@Serializable
data class WorkisSide(
    val available: Boolean? = null,
    val hasSeat: Boolean? = null,
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val company: String? = null,
    val roles: List<String>? = null,
    val scope: String? = null,
    val canTrade: Boolean? = null,
)

@Serializable
data class WorkspaceAccount(
    @Serializable(with = FlexString::class) val id: String? = null,
    val email: String? = null,
    val name: String? = null,
)

/** One Jowi workspace: the hotel this account works in. `logoUrl` null = the partner uploaded none — draw the name, never chase a link. */
@Serializable
data class JowiWorkspace(
    @Serializable(with = FlexString::class) val partnerId: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    /** "hospitality" | "construction" */
    val industry: String? = null,
    val isOwner: Boolean? = null,
    val position: String? = null,
    val logoUrl: String? = null,
    val logoMarkUrl: String? = null,
) {
    val title: String get() = shortName?.takeIf { it.isNotBlank() } ?: name.orEmpty()
}

// MARK: - What the server resolved (typed, every id an explicit string — Django 2026-09-23)

@Serializable
data class DecisionNamed(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
)

@Serializable
data class DecisionContract(
    @Serializable(with = FlexString::class) val id: String? = null,
    val code: String? = null,
    val season: String? = null,
) {
    /** The code is what the hotel says out loud; the season only when it adds something. */
    val display: String? get() = listOfNotNull(code, season).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
}

@Serializable
data class DecisionReservation(
    @Serializable(with = FlexString::class) val id: String? = null,
    /** the hotel's own reference (alias of `bookingNo`) */
    val no: String? = null,
    val bookingNo: String? = null,
    /** the OPERATOR's number — the one on the voucher, the one she types (Django 25.09) */
    val reservationNo: String? = null,
    val guest: String? = null,
) {
    /** `reservationNo · bookingNo (fallback no) · guest`, de-duplicated, empty parts dropped — hers first (ledger 29). */
    val display: String? get() {
        val hotel = bookingNo?.takeIf { it.isNotEmpty() } ?: no
        return listOfNotNull(reservationNo, hotel, guest).filter { it.isNotEmpty() }.distinct().joinToString(" · ").ifEmpty { null }
    }
}

@Serializable
data class DecisionDates(val from: String? = null, val to: String? = null) {
    val display: String? get() {
        val a = from?.takeIf { it.isNotEmpty() }
        val b = to?.takeIf { it.isNotEmpty() }
        return when {
            a != null && b != null -> if (a == b) a else "$a – $b"
            a != null -> a
            b != null -> b
            else -> null
        }
    }
}

/** `{kind: "percent"|"amount", value: decimal string, currency}` — currency empty on a percent. ABSENT when unparsed, never zero. */
@Serializable
data class DecisionDiscount(
    val kind: String? = null,
    @Serializable(with = FlexString::class) val value: String? = null,
    val currency: String? = null,
) {
    /**
     * Nothing said twice (ledger 22): the chip's icon is already a percent sign and
     * the trailing zeros are trimmed as TEXT — "10.00" → "10", never through a Double.
     */
    val display: String? get() {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amount = trimDecimal(raw)
        val cur = currency?.takeIf { it.isNotEmpty() }
        return if (cur != null) "$amount $cur" else amount
    }

    companion object {
        fun trimDecimal(raw: String): String {
            if (!raw.contains('.')) return raw
            var out = raw.trimEnd('0')
            if (out.endsWith('.')) out = out.dropLast(1)
            return out.ifEmpty { raw }
        }
    }
}

@Serializable
data class DecisionResolved(
    @SerialName("operator") val operator: DecisionNamed? = null,
    val contract: DecisionContract? = null,
    val reservation: DecisionReservation? = null,
    val discount: DecisionDiscount? = null,
    val dates: DecisionDates? = null,
) {
    val isEmpty: Boolean get() =
        operator?.name.isNullOrEmpty() && contract?.display == null && reservation?.display == null &&
            dates?.display == null && discount?.display == null
}

/** One option of a question: the VALUE travels under `answers{key: value}`, the label is drawn. */
@Serializable
data class DecisionOption(val value: String = "", val label: String? = null) {
    val text: String get() = label?.takeIf { it.isNotEmpty() } ?: value
}

/** `options: null` = free text. Never answered by the app, never pre-selected. `key == "duplicate"` = two plain buttons (ledger 29). */
@Serializable
data class DecisionQuestion(val key: String = "", val question: String? = null, val options: List<DecisionOption>? = null)

/** Who will be told, and how. `role` is a readable label, not a key. */
@Serializable
data class DecisionRecipient(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val role: String? = null,
    val via: String? = null,
) {
    val line: String get() = listOfNotNull(name, role, via).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** One turn of a decision's trail ("Dayanak") or of the conversation sent as `history` / `replyTo`: `role` "user" | "jowi". */
@Serializable
data class DecisionTurn(val role: String? = null, val text: String? = null) {
    val isJowi: Boolean get() = role == "jowi"
}

/** `POST jowi/decisions/preview/` — ask/preview's contract plus what makes a decision a decision. Nothing written yet. */
@Serializable
data class DecisionPreview(
    val text: String? = null,
    /** THE record's sentence (Django 24.09); `textClarified` is the same string. */
    val restated: String? = null,
    val textClarified: String? = null,
    val changed: Boolean? = null,
    val material: Boolean? = null,
    val resolved: DecisionResolved? = null,
    val questions: List<DecisionQuestion>? = null,
    /** shown BEFORE the record is written */
    val recipients: List<DecisionRecipient>? = null,
    /** the server's sentence — never reworded, never in the catalogue */
    val note: String? = null,
    /** the MAIL subject only — never drawn on a card (ledger 24) */
    val subject: String? = null,
    /** what the record would rest on, before Devam (ledger 33) */
    val trail: List<DecisionTurn>? = null,
)

/**
 * `POST jowi/entry/` — the ONE door a manager's sentence goes through. The SERVER
 * decides whether it was an instruction, a question, or `unsure`; the app never
 * guesses. The classifier falls TOWARDS asking: every failure is `unsure`.
 */
@Serializable
data class DecisionEntry(
    /** "instruction" · "question" · "unsure" */
    val kind: String? = null,
    val text: String? = null,
    // kind == "instruction" — the preview's shape
    val restated: String? = null,
    val subject: String? = null,
    val textClarified: String? = null,
    val changed: Boolean? = null,
    val material: Boolean? = null,
    val resolved: DecisionResolved? = null,
    val questions: List<DecisionQuestion>? = null,
    val recipients: List<DecisionRecipient>? = null,
    val note: String? = null,
    val trail: List<DecisionTurn>? = null,
    // kind == "question"
    val answer: String? = null,
    // kind == "unsure" — the server asking, with its own two options
    val question: String? = null,
    val options: List<DecisionOption>? = null,
) {
    val isInstruction get() = kind == "instruction"
    val isQuestion get() = kind == "question"
    val isUnsure get() = kind == "unsure"
    /** What Jowi said in an answered turn: the answer, else the server's note ("I could not find it" is an answer too). */
    val said: String get() = listOfNotNull(answer, note).firstOrNull { it.isNotEmpty() }.orEmpty()
    fun preview() = DecisionPreview(
        text = text, restated = restated, textClarified = textClarified, changed = changed, material = material,
        resolved = resolved, questions = questions, recipients = recipients, note = note, subject = subject, trail = trail,
    )
}

/**
 * One written decision. A WRITTEN row carries the resolved fields FLAT (`operator`,
 * `contract`, `reservation`, `discount`, `dates` at the top level); only `preview` nests
 * them under `resolved` — both are read, the nest wins (ledger 22). The timestamp is `at`
 * (ledger 21). `degraded: true` = written while the model could not run (ledger 27).
 */
@Serializable
data class Decision(
    @Serializable(with = FlexString::class) val id: String? = null,
    /** what became the record — her words, or Jowi's reading when she chose it */
    val text: String? = null,
    val restated: String? = null,
    /** what she typed, when Jowi's reading became the record — the record keeps BOTH */
    val textOriginal: String? = null,
    val at: String? = null,
    val deliveredAt: String? = null,
    val deliveryError: String? = null,
    val seenAt: String? = null,
    val retractedAt: String? = null,
    val canRetract: Boolean? = null,
    val resolved: DecisionResolved? = null,
    @SerialName("operator") val operator: DecisionNamed? = null,
    val contract: DecisionContract? = null,
    val reservation: DecisionReservation? = null,
    val discount: DecisionDiscount? = null,
    val dates: DecisionDates? = null,
    val recipients: List<DecisionRecipient>? = null,
    val note: String? = null,
    val subject: String? = null,
    val degraded: Boolean? = null,
    val trail: List<DecisionTurn>? = null,
) {
    /** The nest when sent, else the flat fields; null when nothing was resolved. */
    val reading: DecisionResolved? get() = resolved
        ?: DecisionResolved(operator, contract, reservation, discount, dates).takeIf { !it.isEmpty }
    /** The clean sentence leads; her own words underneath when they differ. */
    val headline: String get() = restated?.takeIf { it.isNotEmpty() } ?: text.orEmpty()
    val ownWords: String? get() = textOriginal?.takeIf { it.isNotEmpty() } ?: text?.takeIf { it.isNotEmpty() }
    val withdrawn: Boolean get() = !retractedAt.isNullOrEmpty()
    val delivery: Delivery get() = when {
        !deliveredAt.isNullOrEmpty() -> Delivery.Told
        !deliveryError.isNullOrEmpty() -> Delivery.Failed
        else -> Delivery.Going
    }
    /** Is this the row we just tried to write? (a 502 may have landed — ledger 20 / iOS) */
    fun matches(sent: String): Boolean {
        val a = sent.trim(); if (a.isEmpty()) return false
        return listOfNotNull(text, restated, textOriginal).any { it.trim() == a }
    }
    enum class Delivery { Told, Failed, Going }
}

@Serializable
data class DecisionList(val success: Boolean? = null, val decisions: List<Decision>? = null, val canWrite: Boolean? = null)

/** `201 → {success, decision}` on create; `{success, decision}` on retract. */
@Serializable
data class DecisionWritten(val success: Boolean? = null, val decision: Decision? = null, val error: String? = null, val code: String? = null)

/**
 * One body for `entry/`, `decisions/preview/` and `decisions/` — nulls are omitted
 * (`explicitNulls = false`). `kind` may only ever be "question" (an instruction still goes
 * through the preview — the client can never force a line into the record). `history` =
 * the conversation on screen, oldest first, last 6; `replyTo` = the bubble she quoted.
 */
@Serializable
data class DecisionRequest(
    val text: String,
    val lang: String,
    val kind: String? = null,
    val textOriginal: String? = null,
    val answers: Map<String, String>? = null,
    val history: List<DecisionTurn>? = null,
    val replyTo: DecisionTurn? = null,
)

/**
 * A refusal as it arrives: the STATUS and the CODE decide what the app does, `error` is
 * what the person reads — but ONLY when the server MEANT it: a refusal carries a `code`;
 * without one (a 500, a raw exception) the text is a database's words, and the app's own
 * status idiom is shown instead (ledger 22 — a foreign-key error was printed to a manager).
 */
data class DecisionRefusal(
    val status: Int,
    val code: String?,
    val error: String,
    /** `409 unanswered`: the server ASKING, with the questions — the card opens instead of an error line. */
    val questions: List<DecisionQuestion>? = null,
) {
    val isUnanswered get() = code == "unanswered" && !questions.isNullOrEmpty()
    /** A gateway answer with no code of its own: the request did not reach a verdict — the record MAY have landed (502 seen live). */
    val mayHaveLanded get() = code == null && (status == 0 || status >= 502)
    /** Seen: it can no longer be withdrawn; the way out is a NEW decision. */
    val isSeen get() = status == 409 && code == "seen"
}

/** → (the 2xx body, null) or (null, the refusal). Transport failures are the app's own sentence; everything else the server's, by the code rule. */
suspend fun <T> decisionCall(lang: String, call: suspend () -> T): Pair<T?, DecisionRefusal?> =
    try {
        call() to null
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val obj = runCatching { WorkisJson.decodeFromString<JsonObject>(body) }.getOrNull()
        val code = (obj?.get("code") as? JsonPrimitive)?.contentOrNull
        val sentence = if (code != null) (obj?.get("error") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } else null
        val asks = if (code == "unanswered") runCatching {
            obj?.get("questions")?.jsonArray?.map { WorkisJson.decodeFromJsonElement(DecisionQuestion.serializer(), it) }
        }.getOrNull() else null
        null to DecisionRefusal(e.code(), code, sentence ?: statusMessage(e.code(), lang), asks)
    } catch (e: java.io.IOException) {
        null to DecisionRefusal(0, "network", statusMessage(null, lang))
    } catch (e: kotlinx.coroutines.CancellationException) {
        // a caller's withTimeoutOrNull cancels us: let it, so the timeout reads as null, never as a refusal
        throw e
    } catch (e: Exception) {
        null to DecisionRefusal(0, null, statusMessage(-1, lang))
    }
