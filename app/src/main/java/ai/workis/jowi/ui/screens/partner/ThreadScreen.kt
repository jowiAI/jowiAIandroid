package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.AskLikeBody
import ai.workis.jowi.data.AskPreviewBody
import ai.workis.jowi.data.OrderTerm
import ai.workis.jowi.data.ThreadMessage
import ai.workis.jowi.data.ThreadPayload
import ai.workis.jowi.data.ThreadPostBody
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.dayOf
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The two doors onto the one screen: an order table, or a quote-stage table. */
sealed interface ThreadDoor {
    val id: String
    data class Order(override val id: String) : ThreadDoor
    data class Invitation(override val id: String) : ThreadDoor
}

/**
 * One screen, two doors (Django §6) — the "yazışma", never a chat. The grammar
 * is the Jowi tab's: my turn on the right in a kiremit frame, other seats on the
 * left with WHO · seat, a Jowi turn as a "/" card with 👍/👎 when it carries `kb`.
 * System lines are LEFT-aligned with an icon column: the real ones run several
 * lines, and centring put every line start at a different x.
 *
 * Nothing here deletes or edits: a withdrawn message leaves a trace in its place.
 * Polls every 8 s while open; there is no push channel yet.
 */
class ThreadViewModel : ViewModel() {
    var payload by mutableStateOf<ThreadPayload?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    /** The draft lives here so the two-reading window can hand it back untouched. */
    var draft by mutableStateOf("")
    /** `private` / `routed` answers: drawn for the asker alone, never stored. */
    val localLines = mutableStateListOf<String>()
    var privateNote by mutableStateOf(false)
        private set
    /** A MATERIAL rewrite waits for the writer's choice (typed, reading). */
    var clarify by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var door: ThreadDoor? = null
    private val lang get() = Graph.language.value

    fun open(d: ThreadDoor) {
        if (door == d && payload != null) return
        door = d
        localLines.clear()
        load()
    }

    fun load() {
        val d = door ?: return
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                if (d is ThreadDoor.Order) Graph.api.orderThread(d.id) else Graph.api.invitationThread(d.id)
            }
            if (r != null) { payload = r; loadError = null } else if (payload == null) loadError = err
        }
    }

    /**
     * A quiet re-read: no error text, and the rows are swapped only when one of
     * them really moved, so an idle table does not redraw every eight seconds.
     */
    private suspend fun refresh() {
        val d = door ?: return
        val old = payload ?: return
        val (r, _) = partnerCall(lang) {
            if (d is ThreadDoor.Order) Graph.api.orderThread(d.id) else Graph.api.invitationThread(d.id)
        }
        val fresh = r ?: return
        payload = if (rowSignature(fresh.messages) == rowSignature(old.messages)) {
            fresh.copy(messages = old.messages)
        } else {
            fresh
        }
    }

    /** id + what a poll can change about a row already on screen. */
    private fun rowSignature(ms: List<ThreadMessage>?): List<String> = ms.orEmpty().map {
        "${it.id}|${it.seenBy.orEmpty().joinToString(",")}|${it.canRetract == true}|${it.retracted == true}|${it.textLocalized.orEmpty()}"
    }

    /**
     * `new/?after=` brings the newer rows AND `receipts` for my own recent ones
     * plus the term count. Appending alone left rows on screen with the state
     * they were loaded with: ✓ never became ✓✓, and "Geri çek" outlived the
     * other party's reading (the POST would then have refused it). So: a row
     * arrived, a receipt differs from what is drawn, or the term count moved →
     * read the table once. An older server sends no receipts; then the table is
     * re-read while one of my messages is still withdrawable.
     */
    suspend fun pollOnce() {
        val d = door ?: return
        val p = payload ?: return
        val after = p.messages?.lastOrNull()?.at ?: return
        val (r, _) = partnerCall(lang) {
            if (d is ThreadDoor.Order) Graph.api.orderThreadNew(d.id, after) else Graph.api.invitationThreadNew(d.id, after)
        }
        val fresh = r ?: return
        val known = p.messages.orEmpty().associateBy { it.id }
        val arrived = fresh.messages.orEmpty().any { known[it.id] == null }
        val moved = fresh.receipts.orEmpty().any { rc ->
            val m = known[rc.id] ?: return@any false
            m.seenBy.orEmpty() != rc.seenBy.orEmpty() ||
                (m.canRetract == true) != (rc.canRetract == true) ||
                (m.retracted == true) != (rc.retracted == true)
        }
        val termsMoved = fresh.nTermsOk != null && fresh.nTermsOk != p.nTermsOk
        val blind = fresh.receipts == null && p.messages.orEmpty().any { it.mine == true && it.canRetract == true }
        if (arrived || moved || termsMoved || blind) refresh()
    }

    /**
     * The web's `tSend` rule, and the app's: Jowi proofreads INSIDE send — there
     * is no second button. Nothing changed → the typed text goes. Changed but not
     * material → the corrected text goes silently with the typed words alongside
     * as `textOriginal`. Material → the two-reading window. Slow (4 s) or failed
     * → the typed text goes. A leading "/" skips the proofread.
     */
    fun send() {
        val typed = draft.trim()
        if (typed.isEmpty() || busy || clarify != null) return
        if (typed.startsWith("/")) { post(typed, null); return }
        busy = true; note = null
        viewModelScope.launch {
            val p = withTimeoutOrNull(4_000) {
                runCatching { Graph.api.askPreview(AskPreviewBody(typed)) }.getOrNull()
            }
            val reading = p?.qClarified
            when {
                p?.changed != true || reading.isNullOrEmpty() || reading == typed -> post(typed, null)
                p.material == true -> { busy = false; clarify = typed to reading }
                else -> post(reading, typed)
            }
        }
    }

    fun chooseReading() { clarify?.let { (typed, reading) -> clarify = null; post(reading, typed) } }
    fun chooseOwn() { clarify?.let { (typed, _) -> clarify = null; post(typed, null) } }
    /** The third way: neither reading — the draft stays in the field to be fixed. */
    fun backToEdit() { clarify = null }

    private fun post(text: String, original: String?) {
        val d = door ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        busy = true; note = null; localLines.clear(); privateNote = false
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                val body = ThreadPostBody(t, original?.takeIf { it != t })
                if (d is ThreadDoor.Order) Graph.api.orderThreadPost(d.id, body) else Graph.api.invitationThreadPost(d.id, body)
            }
            busy = false
            when {
                r == null -> note = err
                r.kind == "posted" -> { draft = ""; load() }
                else -> {
                    // private / routed: shown to the asker alone, under the last turn
                    draft = ""
                    privateNote = r.kind == "private"
                    localLines.addAll(r.lines.orEmpty().mapNotNull { it.text }.ifEmpty { listOfNotNull(r.line) })
                    load()
                }
            }
        }
    }

    /**
     * The sender's own message leaves the screens while no other seat has seen
     * it; a trace stays in its place. A refusal is the server's sentence — and
     * the table is read again either way, because a term proposal the message
     * fed falls with it.
     */
    fun retract(m: ThreadMessage) {
        val d = door ?: return
        val id = m.id ?: return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                if (d is ThreadDoor.Order) Graph.api.orderThreadRetract(d.id, id) else Graph.api.invitationThreadRetract(d.id, id)
            }
            busy = false
            if (r?.success != true) note = r?.error ?: err
            load()
        }
    }

    /** `confirm` · `edit` · `withdraw` · `decline` — the card comes back decided. */
    fun term(key: String, body: kotlinx.serialization.json.JsonObject) {
        val d = door as? ThreadDoor.Order ?: return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.orderTerm(d.id, key, body) }
            busy = false
            if (r?.success == true) load() else note = r?.error ?: err
        }
    }

    fun rate(m: ThreadMessage, up: Boolean) {
        val kb = m.kb ?: return
        viewModelScope.launch {
            val r = runCatching { Graph.api.askLike(AskLikeBody(kb, if (up) "up" else "down")) }.getOrNull() ?: return@launch
            if (!r.success) return@launch
            val p = payload ?: return@launch
            payload = p.copy(
                messages = p.messages.orEmpty().map {
                    if (it.id == m.id) it.copy(verdict = if (r.rated == false) "already" else "thanks") else it
                },
            )
        }
    }
}

@Composable
fun ThreadScreen(door: ThreadDoor, onBack: () -> Unit, onContract: (String, String?) -> Unit = { _, _ -> }) {
    val vm: ThreadViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "thread-${door.id}")
    val colors = WorkisTheme.colors
    var editTerm by remember { mutableStateOf<OrderTerm?>(null) }
    var retractTarget by remember { mutableStateOf<ThreadMessage?>(null) }
    val composerFocus = remember { FocusRequester() }
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(door) { vm.open(door) }
    // the web polls every 8 s while the table is open; so does the app
    LaunchedEffect(door) {
        while (true) { delay(8_000); vm.pollOnce() }
    }
    val p = vm.payload
    val messages = p?.messages.orEmpty()
    LaunchedEffect(messages.size, vm.localLines.size) {
        val n = messages.size + vm.localLines.size
        if (n > 0) listState.animateScrollToItem(n + 1)
    }
    val title = p?.order?.po ?: p?.invitation?.title ?: stringResource(R.string.thread_open)

    ConsoleSub(title, onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
        // the contract badge is a door too
        p?.contract?.let { c ->
            val orderId = p.order?.id
            if (orderId != null) {
                Text(
                    stringResource(if (c.status == "accepted") R.string.order_contract_accepted else R.string.order_contract_draft),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                    modifier = Modifier.clickable { onContract(orderId, p.myRole) }.padding(horizontal = 4.dp),
                )
            }
        }
    }) {
        when {
            p == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            p == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().imePadding()) {
                NoteBand(vm.note)
                // the open inspection windows, and what each means for the MONEY —
                // the sentence is the server's, on either payment path
                InspectionStrip(p)
                // the seven term cards live in a strip above the turns; it opens
                // by itself while something is still missing
                val terms = p.terms.orEmpty()
                if (terms.isNotEmpty()) {
                    TermsStrip(terms, p.nTermsOk ?: 0, p.nTerms ?: terms.size, vm) { editTerm = it }
                    Spacer(Modifier.height(10.dp))
                }
                val seats = otherSeats(p)
                LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(messages, key = { i, m -> m.id ?: "m$i" }) { i, m ->
                        // a faint date line whenever the day turns over
                        val day = dayOf(m.at)
                        val prevDay = messages.getOrNull(i - 1)?.let { dayOf(it.at) }
                        if (day.isNotEmpty() && day != prevDay) {
                            Text(
                                day, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                        MessageRow(m, seats, vm) { retractTarget = m }
                    }
                    if (vm.localLines.isNotEmpty()) {
                        item { PrivateCard(vm.localLines.toList(), vm.privateNote) }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                // the coordinator reads the table; no composer
                if (!p.readOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = vm.draft,
                            onValueChange = { vm.draft = it },
                            placeholder = { Text(stringResource(R.string.thread_placeholder), color = colors.faint) },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(composerFocus),
                        )
                        // ONE button. The wand that stood here called the list-note
                        // polish — a REWRITE, the wrong tool, and a second control
                        // nobody remembers to press. The proofread runs inside send.
                        val canSend = vm.draft.isNotBlank() && !vm.busy
                        Box {
                            IconButton(onClick = { vm.send() }, enabled = canSend) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send),
                                    tint = if (canSend) Kiremit500 else colors.faint,
                                )
                            }
                            vm.clarify?.let { (typed, reading) ->
                                ClarifyWindow(typed, reading, vm) { composerFocus.requestFocus() }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    editTerm?.let { t -> TermEditSheet(t, vm) { editTerm = null } }
    retractTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { retractTarget = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.thread_retract), color = colors.ink) },
            text = { Text(stringResource(R.string.thread_retract_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { retractTarget = null; vm.retract(m) }) {
                    Text(stringResource(R.string.thread_retract), color = colors.accentText, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { retractTarget = null }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

/** The seats at this table other than the reader's. */
private fun otherSeats(p: ThreadPayload): List<String> = buildList {
    if (p.parties?.buyer != null) add("buyer")
    if (p.parties?.seller != null) add("seller")
    if (p.parties?.carrier != null) add("carrier")
}.filter { it != p.myRole }

// ---------- the system line's glyph

/** The server's `icon` word → our mark. */
private val systemGlyphs: Map<String, ImageVector> = mapOf(
    "delivered" to WorkisIcons.ShippingBox,
    "accepted" to WorkisIcons.Check,
    "released" to WorkisIcons.LockOpen,
    "objection" to WorkisIcons.Warning,
    "resolved" to WorkisIcons.Check,
    "returned" to WorkisIcons.Undo,
    "escalated" to WorkisIcons.Shield,
    "question" to WorkisIcons.QuestionCircle,
    "declined" to WorkisIcons.Cross,
    "withdrawn" to WorkisIcons.Undo,
)

/** The fallback for a line written before the server named its kind. */
private val emojiGlyphs: Map<Int, ImageVector> = mapOf(
    0x1F4CB to WorkisIcons.Clipboard,                                       // 📋 terms
    0x1F6E1 to WorkisIcons.Shield,                                          // 🛡 warranty
    0x1F69A to WorkisIcons.ShippingBox, 0x1F4E6 to WorkisIcons.ShippingBox, // 🚚 📦 shipment
    0x1F4B0 to WorkisIcons.Lock, 0x1F512 to WorkisIcons.Lock, 0x1F4B3 to WorkisIcons.Lock, // 💰 🔒 💳
    0x1F4DC to WorkisIcons.DocText, 0x1F4C4 to WorkisIcons.DocText, 0x1F4DD to WorkisIcons.DocText, // 📜 📄 📝
    0x26A0 to WorkisIcons.Warning,                                          // ⚠ objection / delay
    0x23F0 to WorkisIcons.Clock, 0x23F3 to WorkisIcons.Clock,               // ⏰ ⏳ deadline
    0x2705 to WorkisIcons.Check,                                            // ✅ confirmed
    0x21A9 to WorkisIcons.Undo,                                             // ↩ return
    0x1F50D to WorkisIcons.Search,                                          // 🔍 inspection
    0x1F4C5 to WorkisIcons.Calendar, 0x1F4C6 to WorkisIcons.Calendar, 0x1F5D3 to WorkisIcons.Calendar, // 📅 📆 🗓
    0x1F4AC to WorkisIcons.Bubble,                                          // 💬 message
)

private fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
        cp in 0x23E9..0x23FA || cp in 0x21A9..0x21AA ||
        cp == 0xFE0F || cp == 0x200D || cp == 0x20E3

/**
 * The rule is icons, not emoji: the server's leading emoji becomes the icon
 * column and the ones inside the sentence are dropped. The `icon` word wins
 * when it is there; an unknown line gets the neutral mark.
 */
private fun systemLine(raw: String, icon: String?): Pair<ImageVector, String> {
    val trimmed = raw.trim()
    val lead = trimmed.takeIf { it.isNotEmpty() }?.codePointAt(0)?.takeIf { isEmojiCodePoint(it) }
    val body = buildString {
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            if (!isEmojiCodePoint(cp)) appendCodePoint(cp)
            i += Character.charCount(cp)
        }
    }
    val glyph = icon?.let { systemGlyphs[it] } ?: lead?.let { emojiGlyphs[it] } ?: WorkisIcons.Dot
    // collapse the gap the dropped emoji left, but keep the paragraphs
    return glyph to body.lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }.trim()
}

/** A withdrawn message is never a silent gap — the trace is drawn, dashed. */
private fun Modifier.dashedFrame(color: Color) = this.drawBehind {
    val dash = 4.dp.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        cornerRadius = CornerRadius(14.dp.toPx()),
    )
}

// ---------- the turns

@Composable
private fun MessageRow(m: ThreadMessage, seats: List<String>, vm: ThreadViewModel, onRetract: () -> Unit) {
    val colors = WorkisTheme.colors
    var showOriginal by remember(m.id) { mutableStateOf(false) }
    // `content` comes back empty for the other seats once a message is withdrawn
    val content = m.content.orEmpty()
    val translated = m.textLocalized?.takeIf { it.isNotEmpty() && it != content }
    val body = if (translated != null && !showOriginal) translated else content
    when {
        m.retracted == true -> Withdrawn(m, if (m.mine == true) body else "")
        // left-aligned, no bubble, an ICON column where the server's emoji was
        m.kind == "system" -> {
            val (glyph, text) = systemLine(body, m.icon)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Icon(glyph, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted)
                    m.atLabel?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint) }
                }
            }
        }
        m.kind == "jowi" -> Row(Modifier.fillMaxWidth().padding(end = 28.dp), verticalAlignment = Alignment.Top) {
            Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kiremit500, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f).background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarkdownText(body, color = colors.ink, fontSize = 14.sp, lineHeight = 20.sp)
                TranslationToggle(translated != null, showOriginal) { showOriginal = !showOriginal }
                if (m.kb != null) ThreadThumbs(m, vm)
            }
        }
        m.mine == true -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier.fillMaxWidth(0.82f).background(Kiremit500.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).border(1.dp, Kiremit500.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
                TranslationToggle(translated != null, showOriginal) { showOriginal = !showOriginal }
                Stamp(m, mine = true)
                Receipt(m, seats, onRetract)
            }
        }
        else -> Row(Modifier.fillMaxWidth().padding(end = 28.dp)) {
            Column(
                Modifier.weight(1f).background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    listOfNotNull(m.who?.takeIf { it.isNotEmpty() }, seatWord(m.seat)).joinToString(" · "),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.accentText,
                )
                Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
                TranslationToggle(translated != null, showOriginal) { showOriginal = !showOriginal }
                Stamp(m, mine = false)
            }
        }
    }
}

/**
 * Where a withdrawn message was: the trace, for every seat. The other side may
 * hold the e-mail of it, and the trace is what keeps the two stories the same.
 * The sender still reads its own text, faded and struck through.
 */
@Composable
private fun Withdrawn(m: ThreadMessage, ownText: String) {
    val colors = WorkisTheme.colors
    val mine = m.mine == true
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .dashedFrame(colors.faint.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (ownText.isNotEmpty()) {
                Text(
                    ownText, fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint,
                    textDecoration = TextDecoration.LineThrough,
                    textAlign = if (mine) TextAlign.End else TextAlign.Start,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Undo, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    m.trace?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.thread_retracted_trace),
                    fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = colors.muted,
                )
            }
        }
    }
}

/**
 * Time, the translation toggle, and — since the proofread moved inside send —
 * the words as TYPED, one tap behind a keyboard glyph: the bubble is Jowi's
 * corrected reading, but the original is what governs.
 */
@Composable
private fun Stamp(m: ThreadMessage, mine: Boolean) {
    val colors = WorkisTheme.colors
    val typed = m.textOriginal?.takeIf { it.isNotEmpty() }
    var open by remember(m.id) { mutableStateOf(false) }
    // the button says whose words it opens: mine, or the other party's
    val showTyped = stringResource(if (mine) R.string.clarify_show_typed else R.string.clarify_show_typed_other)
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (typed != null && open) {
            Text(
                stringResource(if (mine) R.string.clarify_you_wrote else R.string.clarify_typed_label) + " · " + typed,
                fontSize = 11.sp, lineHeight = 16.sp, color = colors.faint,
                textAlign = if (mine) TextAlign.End else TextAlign.Start,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            m.atLabel?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint) }
            if (typed != null) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 24.dp)
                        .clickable { open = !open }
                        .semantics { contentDescription = showTyped },
                    contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Icon(
                        WorkisIcons.Keyboard, contentDescription = null,
                        tint = if (open) colors.muted else colors.faint, modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Under the sender's own message: ✓ while it is on the table, two checkmarks
 * side by side — near each other and NOT touching — once every other seat is in
 * `seenBy`. One tap names the seats. No eye, no hand-drawn glyph: everyone reads
 * ✓ / ✓✓, and pushed together the pair reads as a "W".
 */
@Composable
private fun Receipt(m: ThreadMessage, seats: List<String>, onRetract: () -> Unit) {
    val colors = WorkisTheme.colors
    val seenBy = m.seenBy ?: return
    val all = seats.isNotEmpty() && seats.all { it in seenBy }
    var open by remember(m.id) { mutableStateOf(false) }
    val word = stringResource(R.string.thread_seen)
    val onTable = stringResource(R.string.thread_on_table)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.canRetract == true) {
            Text(
                stringResource(R.string.thread_retract), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.muted,
                modifier = Modifier.clickable(onClick = onRetract),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { open = !open }
                .semantics { contentDescription = if (all) word else onTable }
                .padding(vertical = 2.dp),
        ) {
            val tint = if (all) SuccessGreen else colors.faint
            Icon(WorkisIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            if (all) Icon(WorkisIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        }
    }
    if (open) {
        val seatNames = seats.map { seat -> (seatWord(seat) ?: seat) + " " + (if (seat in seenBy) word else "—") }
        Text(seatNames.joinToString(" · "), fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint)
    }
}

@Composable
private fun seatWord(seat: String?): String? = when (seat) {
    "buyer" -> stringResource(R.string.order_role_buyer)
    "seller" -> stringResource(R.string.order_role_seller)
    "carrier" -> stringResource(R.string.order_role_carrier)
    "coordinator" -> stringResource(R.string.thread_seat_coordinator)
    else -> seat?.takeIf { it.isNotEmpty() }
}

/** Per-reader translation: one tap swaps between the record and the reading. */
@Composable
private fun TranslationToggle(has: Boolean, showingOriginal: Boolean, onToggle: () -> Unit) {
    if (!has) return
    val colors = WorkisTheme.colors
    Text(
        stringResource(if (showingOriginal) R.string.thread_show_translation else R.string.thread_show_original),
        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

@Composable
private fun ThreadThumbs(m: ThreadMessage, vm: ThreadViewModel) {
    val colors = WorkisTheme.colors
    val verdict = m.verdict
    if (verdict != null) {
        Text(
            stringResource(if (verdict == "already") R.string.thumbs_already else R.string.thumbs_thanks),
            fontSize = 12.sp, color = SuccessGreen,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true, false).forEach { up ->
            Text(
                (if (up) "👍 " else "👎 ") + stringResource(if (up) R.string.thumbs_up else R.string.thumbs_down),
                fontSize = 12.sp, color = colors.muted,
                modifier = Modifier
                    .background(colors.ink.copy(alpha = 0.06f), CircleShape)
                    .clickable { vm.rate(m, up) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** A private / routed answer: the asker's eyes only, and not kept on the table. */
@Composable
private fun PrivateCard(lines: List<String>, private: Boolean) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(end = 28.dp), verticalAlignment = Alignment.Top) {
        Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kiremit500, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lines.forEach { MarkdownText(it, color = colors.ink, fontSize = 14.sp, lineHeight = 20.sp) }
            if (private) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(WorkisIcons.Lock, contentDescription = null, tint = colors.faint, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.thread_private_note), fontSize = 11.sp, color = colors.faint)
                }
            }
        }
    }
}

// ---------- the two readings

/**
 * The window hangs off the SEND button — the system's own container, a tap
 * outside closes it, and the draft is still in the field to be corrected. Each
 * choice IS its text: a small capsule names whose words they are, the sentence
 * that would be sent sits under it, and either one sends. The ground is beige,
 * the app's Jowi-note ground; the capsules sit on the right, under the thumb,
 * and the sentences read from the left.
 */
@Composable
private fun ClarifyWindow(typed: String, reading: String, vm: ThreadViewModel, onBackToEdit: () -> Unit) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, with(density) { -(52.dp).roundToPx() }),
        onDismissRequest = { vm.backToEdit() },
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(320.dp)
                .background(BeigeBg, RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClarifyChoice(reading, strong = true, onSend = { vm.chooseReading() }) {
                Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kiremit500)
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.clarify_jowi_suggests), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Kiremit500)
            }
            ClarifyChoice(typed, strong = false, onSend = { vm.chooseOwn() }) {
                Icon(WorkisIcons.Keyboard, contentDescription = null, tint = Beige, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.clarify_your_words), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Beige)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Beige.copy(alpha = 0.25f)))
            // the third way: neither — back to the field, the draft is still there
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(1.dp, Beige.copy(alpha = 0.55f), CircleShape)
                    .clickable { vm.backToEdit(); onBackToEdit() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(WorkisIcons.Pencil, contentDescription = null, tint = Beige, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.clarify_back_to_edit), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Beige)
            }
        }
    }
}

/** The capsule names the words; the sentence under it is the bigger target. */
@Composable
private fun ClarifyChoice(
    text: String,
    strong: Boolean,
    onSend: () -> Unit,
    label: @Composable () -> Unit,
) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onSend),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, Beige.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { label() }
        Text(
            text,
            fontSize = if (strong) 16.sp else 14.sp,
            lineHeight = if (strong) 22.sp else 20.sp,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) colors.ink else Beige.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------- the inspection windows

/**
 * What an open window means for the MONEY is the server's sentence: it differs
 * by path (a blocked payment vs an invoice whose due date stands still), so the
 * app never writes "ödeme bloke" or "serbest kalır" itself.
 */
@Composable
private fun InspectionStrip(p: ThreadPayload) {
    val colors = WorkisTheme.colors
    val rows = p.inspection.orEmpty().filter { !it.name.isNullOrEmpty() || !it.moneyWords.isNullOrEmpty() }
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Clock, contentDescription = null, tint = Beige, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        row.name?.takeIf { it.isNotEmpty() },
                        stringResource(R.string.inspection_open),
                        row.due?.takeIf { it.isNotEmpty() }?.take(16)?.replace("T", " "),
                    ).joinToString(" · "),
                    fontSize = 12.sp, lineHeight = 17.sp, color = Beige,
                )
            }
            row.moneyWords?.takeIf { it.isNotEmpty() }?.let { words ->
                Text(
                    listOfNotNull(words, row.invoice?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    fontSize = 11.sp, lineHeight = 16.sp, color = colors.muted,
                    modifier = Modifier.padding(start = 21.dp),
                )
            }
        }
    }
}

// ---------- the seven term cards

@Composable
private fun TermsStrip(terms: List<OrderTerm>, ok: Int, total: Int, vm: ThreadViewModel, onEdit: (OrderTerm) -> Unit) {
    val colors = WorkisTheme.colors
    // open by itself while a term is still missing
    var open by remember(terms.size) { mutableStateOf(terms.any { it.status == "missing" }) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(WorkisIcons.Checklist, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.terms_title) + " · $ok/$total", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
            Text(if (open) "⌃" else "⌄", color = colors.faint)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                terms.forEach { t -> TermCard(t, vm, onEdit) }
            }
        }
    }
}

@Composable
private fun TermCard(t: OrderTerm, vm: ThreadViewModel, onEdit: (OrderTerm) -> Unit) {
    val colors = WorkisTheme.colors
    // status is icon + word, never colour alone
    val (mark, word, tint) = when (t.status) {
        "confirmed" -> Triple("✓", stringResource(R.string.term_confirmed), SuccessGreen)
        "proposed" -> Triple("◷", stringResource(R.string.term_proposed), Beige)
        "rejected" -> Triple("✕", stringResource(R.string.term_rejected), colors.danger)
        else -> Triple("!", stringResource(R.string.term_missing), colors.muted)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.label.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.weight(1f))
            Text("$mark $word", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = tint)
        }
        val display = t.display?.takeIf { it.isNotEmpty() }
        if (display != null) Text(display, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink)
        else t.question?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        // a change read out of the yazışma: what it would replace stays in sight
        val prev = t.prevDisplay?.takeIf { it.isNotEmpty() }
        if (t.amendment == true && prev != null) {
            Text(
                stringResource(R.string.term_amendment) + " · " + stringResource(R.string.term_previous) + ": " + prev,
                fontSize = 12.sp, lineHeight = 17.sp, color = Beige,
            )
        } else if (prev != null) {
            Text(stringResource(R.string.term_previous) + ": " + prev, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint)
        }
        if (t.conflict == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.term_conflict), fontSize = 12.sp, color = Beige)
            }
        }
        t.source?.takeIf { it.isNotEmpty() }?.let {
            Text("“$it”", fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (t.canEdit == true) {
                Text(
                    stringResource(if (display == null) R.string.term_state else R.string.term_edit),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted,
                    modifier = Modifier.clickable(enabled = !vm.busy) { onEdit(t) },
                )
            }
            // a proposal has a way out on each side; either one puts the previous
            // term back exactly as it stood (the server's walls decide who)
            if (t.canWithdraw == true) {
                Text(
                    stringResource(R.string.term_withdraw), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted,
                    modifier = Modifier.clickable(enabled = !vm.busy) { t.key?.let { vm.term(it, jsonBody("action" to "withdraw")) } },
                )
            }
            if (t.canDecline == true) {
                Text(
                    stringResource(R.string.term_decline), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted,
                    modifier = Modifier.clickable(enabled = !vm.busy) { t.key?.let { vm.term(it, jsonBody("action" to "decline")) } },
                )
            }
            if (t.canConfirm == true) {
                Text(
                    stringResource(R.string.term_confirm), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText,
                    modifier = Modifier.clickable(enabled = !vm.busy) { t.key?.let { vm.term(it, jsonBody("action" to "confirm")) } },
                )
            }
        }
    }
}

/** Editing sends the SENTENCE; the server re-derives the value and resets the other tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermEditSheet(term: OrderTerm, vm: ThreadViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var value by remember { mutableStateOf(term.display.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(term.label.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                TextButton(enabled = value.isNotBlank(), onClick = {
                    term.key?.let { vm.term(it, jsonBody("action" to "edit", "value" to value.trim())) }
                    onClose()
                }) { Text(stringResource(R.string.done), color = if (value.isBlank()) colors.faint else colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            term.question?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
            FormSection(null) {
                FormRow("", value, { value = it }, {}, false, placeholder = stringResource(R.string.term_edit_placeholder), stacked = true, minLines = 2, maxLines = 5)
            }
        }
    }
}
