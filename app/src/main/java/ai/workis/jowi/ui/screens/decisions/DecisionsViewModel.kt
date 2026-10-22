package ai.workis.jowi.ui.screens.decisions

import ai.workis.jowi.Graph
import ai.workis.jowi.data.Decision
import ai.workis.jowi.data.DecisionEntry
import ai.workis.jowi.data.DecisionPreview
import ai.workis.jowi.data.DecisionQuestion
import ai.workis.jowi.data.DecisionRecipient
import ai.workis.jowi.data.DecisionRefusal
import ai.workis.jowi.data.DecisionRequest
import ai.workis.jowi.data.DecisionResolved
import ai.workis.jowi.data.DecisionTurn
import ai.workis.jowi.data.decisionCall
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Kararlar — the ledger and the writer (ledgers 20–33), a port of iOS
 * `JowiDecisionsView` (the record) + `JowiDecisionWriter` (the state machine).
 *
 * The SERVER decides what a sentence is (`jowi/entry/`); the app never guesses.
 * The send rule is the Jowi tab's: unchanged → her words · changed, not material →
 * Jowi's reading with `textOriginal` = her words · material → the two-reading card.
 * A refused send never costs her anything: the sentence (and its quote) go back
 * into the field, and answered questions come back with their answers.
 *
 * The conversation (`thread`) lives in memory only and closes when she leaves the
 * screen (`closeConversation()` from the screen's DisposableEffect — this VM is
 * activity-scoped, so it is not trusted to die with the tab).
 */
class DecisionsViewModel : ViewModel() {

    // MARK: - Writer shapes

    /** The preview came back with questions: nothing is written until she answers them. */
    data class Asking(val typed: String, val preview: DecisionPreview)

    /** A turn that did not become a record: an answered `question`, or the server asking (`unsure`). */
    data class Answering(val typed: String, val entry: DecisionEntry, val quote: String? = null, val id: String = UUID.randomUUID().toString())

    /** One line of the conversation, in the order it happened. */
    sealed class Item {
        abstract val id: String
        data class Answer(override val id: String, val typed: String, val entry: DecisionEntry, val quote: String?) : Item()
        data class Record(override val id: String, val typed: String, val quote: String?, val decision: Decision) : Item()
    }

    /** A bubble she long-pressed and chose "Yanıtla" on. */
    data class Reply(val turnId: String?, val text: String, val role: String) {
        val payload: DecisionTurn get() = DecisionTurn(role, text)
    }

    /** A MATERIAL rewrite: she picks which text becomes the record. */
    data class Clarify(
        val typed: String,
        val reading: String,
        val answers: Map<String, String>,
        val recipients: List<DecisionRecipient>,
        val note: String?,
        val preview: DecisionPreview?,
    )

    /** The row that exists while the POST is in flight — the names are on screen before the mail leaves. */
    data class Sending(
        val text: String,
        val typed: String,
        val recipients: List<DecisionRecipient>,
        /** the record's sentence only when the reading had no open questions and she did not pick her own words */
        val headline: String?,
        val resolved: DecisionResolved?,
    )

    // MARK: - Ledger state

    var decisions by mutableStateOf<List<Decision>>(emptyList()); private set
    var canWrite by mutableStateOf(true); private set
    var loaded by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set
    /** A refused send and a refused withdrawal share this one line. */
    var actionError by mutableStateOf<String?>(null)

    /** The app's own sentence for "nothing came back" — the screen sets it from `st_error`. */
    var stError: String = ""

    // MARK: - Writer state

    var draft by mutableStateOf(TextFieldValue(""))
    var previewing by mutableStateOf(false); private set
    /** her sentence while Jowi reads it (the field is already empty) */
    var pending by mutableStateOf<String?>(null); private set
    var pendingQuote by mutableStateOf<String?>(null); private set
    var sending by mutableStateOf<Sending?>(null); private set
    var asking by mutableStateOf<Asking?>(null); private set
    var answers by mutableStateOf<Map<String, String>>(emptyMap()); private set
    /** the server's reading AGAIN with her answers in it — only a preview with no questions left */
    var settled by mutableStateOf<DecisionPreview?>(null); private set
    var settling by mutableStateOf(false); private set
    private var settleRound = 0
    var clarify by mutableStateOf<Clarify?>(null); private set
    var answering by mutableStateOf<Answering?>(null); private set
    var thread by mutableStateOf<List<Item>>(emptyList()); private set
    var replyTo by mutableStateOf<Reply?>(null)
    /** what the sentence in flight replied to — rides on every call of that flow */
    private var sentReply: Reply? = null
    /** Bumped when the field should take focus (Yanıtla, Düzenlemeye dön). */
    var focusTick by mutableIntStateOf(0); private set

    /** While a card is up the composer has nothing to send. An answer does not hold the field. */
    val busy: Boolean get() = previewing || sending != null || asking != null || clarify != null || answering != null

    /** The decisions drawn inside the conversation — the ledger skips them until it closes. */
    val inConversation: Set<String> get() =
        thread.mapNotNull { (it as? Item.Record)?.decision?.id }.toSet()

    private val lang: String get() = Graph.language.value

    // MARK: - Ledger

    fun load() { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val (list, refusal) = decisionCall(lang) { Graph.api.decisions() }
        loaded = true
        if (list == null) { loadError = refusal?.error ?: stError; return }
        loadError = null
        decisions = list.decisions.orEmpty()
        canWrite = list.canWrite ?: true
    }

    /** The row stays either way; a 409 (seen) is the server's sentence. */
    fun retract(d: Decision) {
        val id = d.id ?: return
        actionError = null
        viewModelScope.launch {
            val (_, refusal) = decisionCall(lang) { Graph.api.decisionRetract(id) }
            if (refusal != null) actionError = refusal.error.ifEmpty { stError }
            reload()
        }
    }

    // MARK: - Field helpers

    private fun putDraft(text: String) { draft = TextFieldValue(text, TextRange(text.length)) }
    private fun restoreDraft(text: String) { if (draft.text.isEmpty()) putDraft(text) }

    fun startReply(turn: DecisionTurn, turnId: String?) {
        replyTo = Reply(turnId, turn.text.orEmpty(), turn.role ?: "user")
        focusTick++
    }

    // MARK: - The one door

    fun submit() {
        val typed = draft.text.trim()
        if (typed.isEmpty() || !canWrite || busy) return
        draft = TextFieldValue("")
        actionError = null
        previewing = true
        pending = typed
        val reply = replyTo
        replyTo = null
        pendingQuote = reply?.text
        sentReply = reply
        viewModelScope.launch {
            // a question is two chained model calls — 30 s
            val e = withTimeoutOrNull(30_000) {
                decisionCall(lang) {
                    Graph.api.jowiEntry(DecisionRequest(typed, lang, history = history(reply?.turnId).ifEmpty { null }, replyTo = reply?.payload))
                }.first
            }
            pendingQuote = null
            previewing = false; pending = null
            if (e == null) {
                // nothing came back: her sentence returns to the field, never silently into the record
                actionError = stError
                restoreDraft(typed)
                if (replyTo == null) replyTo = reply
                return@launch
            }
            when {
                e.isInstruction -> begin(e.preview(), typed)
                e.isQuestion -> thread = thread + Item.Answer(UUID.randomUUID().toString(), typed, e, reply?.text)
                else -> answering = Answering(typed, e, reply?.text)
            }
        }
    }

    private fun begin(p: DecisionPreview, typed: String) {
        if (!p.questions.isNullOrEmpty()) {
            answers = emptyMap()
            asking = Asking(typed, p)
            return
        }
        applySendRule(typed, p, emptyMap())
    }

    /** She told the server which it was (`unsure`). */
    fun resolveUnsure(a: Answering, choice: String) {
        answering = null
        previewing = true
        pending = a.typed
        pendingQuote = a.quote
        if (choice != "instruction") {
            // "Soruydu, cevapla": back through the one door, named as a question
            viewModelScope.launch {
                val e = withTimeoutOrNull(30_000) {
                    decisionCall(lang) {
                        Graph.api.jowiEntry(DecisionRequest(a.typed, lang, kind = "question", history = history().ifEmpty { null }, replyTo = sentReply?.payload))
                    }.first
                }
                previewing = false; pending = null; pendingQuote = null
                if (e == null || !e.isQuestion) {
                    actionError = stError
                    restoreDraft(a.typed)
                    return@launch
                }
                thread = thread + Item.Answer(UUID.randomUUID().toString(), a.typed, e, a.quote)
            }
            return
        }
        viewModelScope.launch {
            // "Kayda geçir" goes on through preview/ — a slow preview is worse than none: 6 s
            val p = withTimeoutOrNull(6_000) {
                decisionCall(lang) {
                    Graph.api.decisionPreview(DecisionRequest(a.typed, lang, history = history().ifEmpty { null }, replyTo = sentReply?.payload))
                }.first
            }
            previewing = false; pending = null; pendingQuote = null
            if (p == null) {
                actionError = stError
                restoreDraft(a.typed)
                return@launch
            }
            begin(p, a.typed)
        }
    }

    // MARK: - What the server could not resolve

    /**
     * The card's `onAnswer`. A `duplicate` "again" goes on at once when nothing else
     * is open; its "" ("Hayır") closes the card without writing anything.
     */
    fun answer(q: DecisionQuestion, value: String?) {
        if (q.key == "duplicate") {
            if (value.isNullOrEmpty()) { closeAsking(); return }
            setAnswer(q, value)
            val a = asking ?: return
            if (answered(a)) continueFrom(a)
            return
        }
        setAnswer(q, value)
    }

    /** Every question after a changed answer was asked because of the old one — they go, with their answers. */
    fun setAnswer(q: DecisionQuestion, value: String?) {
        if (answers[q.key] == value) return
        val next = answers.toMutableMap()
        val current = asking
        val qs = current?.preview?.questions
        if (current != null && qs != null) {
            val i = qs.indexOfFirst { it.key == q.key }
            if (i >= 0 && i + 1 < qs.size) {
                qs.drop(i + 1).forEach { next.remove(it.key) }
                asking = Asking(current.typed, current.preview.copy(questions = qs.take(i + 1)))
            }
        }
        if (value == null) next.remove(q.key) else next[q.key] = value
        answers = next
        settle()
    }

    /** Answered = the control was TOUCHED ("" is "Bilmiyorum", an answer). */
    private fun answered(a: Asking): Boolean = a.preview.questions.orEmpty().all { answers.containsKey(it.key) }

    /** Every question answered → the server says the sentence again with her answers (0.6 s debounce, latest round wins). */
    private fun settle() {
        settled = null
        settleRound++
        settling = false
        val a = asking ?: return
        if (!answered(a)) return
        settling = true
        val round = settleRound
        val given = answers.mapValues { it.value.trim() }
        viewModelScope.launch {
            delay(600)
            if (round != settleRound) return@launch
            val p = withTimeoutOrNull(20_000) {
                decisionCall(lang) {
                    Graph.api.decisionPreview(DecisionRequest(a.typed, lang, answers = given, history = history().ifEmpty { null }, replyTo = sentReply?.payload))
                }.first
            }
            if (round != settleRound) return@launch
            settling = false
            val current = asking ?: return@launch
            // no reply: Devam opens on the old path
            if (p == null) return@launch
            val nextQs = p.questions.orEmpty()
            if (nextQs.isEmpty()) { settled = p; return@launch }
            // one question at a time, in dependency order: the follow-up joins the card
            val asked = current.preview.questions.orEmpty()
            val known = asked.map { it.key }.toSet()
            val merged = asked + nextQs.filter { it.key !in known }
            asking = Asking(current.typed, p.copy(questions = merged))
        }
    }

    /** "Hayır" to a repeat: nothing written, nothing sent, the field stays empty. */
    fun closeAsking() {
        asking = null
        answers = emptyMap()
        settled = null
        settling = false
        settleRound++
    }

    /** Düzenlemeye dön on the questions card: her words back in the field, nothing written. */
    fun backFromAsking(a: Asking) {
        closeAsking()
        putDraft(a.typed)
        focusTick++
    }

    fun continueFrom(a: Asking) {
        // every key she touched travels, INCLUDING "" (deliberately unresolved)
        val given = answers.mapValues { it.value.trim() }
        val reading = settled
        closeAsking()
        applySendRule(a.typed, reading ?: a.preview, given, asked = a)
    }

    // MARK: - The two readings

    fun useReading(c: Clarify) {
        clarify = null
        post(c.reading, c.typed, original = c.typed, given = c.answers, recipients = c.recipients, reading = c.preview)
    }

    fun useOwn(c: Clarify) {
        clarify = null
        post(c.typed, c.typed, original = null, given = c.answers, recipients = c.recipients, reading = c.preview, ownWords = true)
    }

    fun backFromClarify(c: Clarify) {
        clarify = null
        putDraft(c.typed)
        focusTick++
    }

    // MARK: - The send rule

    private fun applySendRule(typed: String, p: DecisionPreview?, given: Map<String, String>, asked: Asking? = null) {
        val recipients = p?.recipients.orEmpty()
        val reading = p?.textClarified
        if (p == null || p.changed != true || reading.isNullOrEmpty() || reading == typed) {
            post(typed, typed, original = null, given = given, recipients = recipients, asked = asked, reading = p)
            return
        }
        if (p.material == true) {
            clarify = Clarify(typed, reading, given, recipients, p.note, p)
        } else {
            // changed but not material: Jowi's reading is the record, her words ride along
            post(reading, typed, original = typed, given = given, recipients = recipients, asked = asked, reading = p)
        }
    }

    private fun post(
        text: String,
        typed: String,
        original: String?,
        given: Map<String, String>,
        recipients: List<DecisionRecipient>,
        asked: Asking? = null,
        reading: DecisionPreview? = null,
        ownWords: Boolean = false,
    ) {
        val current = reading?.questions.isNullOrEmpty()
        sending = Sending(text, typed, recipients, headline = if (current && !ownWords) reading?.restated else null, resolved = reading?.resolved)
        val reply = sentReply
        viewModelScope.launch {
            val (written, refusal) = decisionCall(lang) {
                Graph.api.decisionCreate(
                    DecisionRequest(
                        text, lang,
                        textOriginal = original?.takeIf { it != text },
                        answers = given.ifEmpty { null },
                        history = history().ifEmpty { null },
                        replyTo = reply?.payload,
                    ),
                )
            }
            sending = null
            if (written == null) { refused(refusal, text, typed, given, asked); return@launch }
            // a record landed: inside a conversation it stays there, after the turns that led to it
            val row = written.decision
            if (thread.isNotEmpty() && row != null) {
                thread = thread + Item.Record(UUID.randomUUID().toString(), typed, reply?.text, row)
            }
            sentReply = null
            reload()
        }
    }

    private suspend fun refused(refusal: DecisionRefusal?, text: String, typed: String, given: Map<String, String>, asked: Asking?) {
        // A GATEWAY gave up, the server may not have (a 502 that had written the record):
        // re-read the ledger and let it answer — a duplicate record is worse than a delay.
        if (refusal != null && refusal.mayHaveLanded) {
            reload()
            if (decisions.any { it.matches(text) }) {
                asking = null; answers = emptyMap(); actionError = null
            } else {
                actionError = refusal.error.ifEmpty { stError }
                restoreDraft(typed)
                if (asked != null) { answers = given; asking = asked }
            }
            return
        }
        // 409 `unanswered` is the server ASKING — the card opens instead of an error line
        if (refusal != null && refusal.isUnanswered) {
            answers = given
            asking = Asking(typed, (asked?.preview ?: DecisionPreview()).copy(questions = refusal.questions))
            return
        }
        actionError = refusal?.error?.ifEmpty { null } ?: stError
        restoreDraft(typed)
        if (asked != null) { answers = given; asking = asked }
    }

    // MARK: - The conversation

    /** Tamam on the newest answer, and leaving the screen: the whole conversation goes. */
    fun closeConversation() {
        answering = null
        thread = emptyList()
        replyTo = null
    }

    /**
     * The conversation as the doors take it: the pointed-at turn moved LAST (the newest
     * context, inside the six sent), each item flattened to her words + Jowi's.
     */
    private fun history(pointingAt: String? = null): List<DecisionTurn> {
        val turns = thread.toMutableList()
        if (pointingAt != null) {
            val i = turns.indexOfFirst { it.id == pointingAt }
            if (i >= 0) turns.add(turns.removeAt(i))
        }
        return turns.takeLast(6).flatMap { item ->
            when (item) {
                is Item.Answer -> listOf(DecisionTurn("user", item.typed), DecisionTurn("jowi", item.entry.said))
                is Item.Record -> listOf(
                    DecisionTurn("user", item.typed),
                    DecisionTurn("jowi", item.decision.restated?.takeIf { it.isNotEmpty() } ?: item.decision.text.orEmpty()),
                )
            }
        }
    }
}
