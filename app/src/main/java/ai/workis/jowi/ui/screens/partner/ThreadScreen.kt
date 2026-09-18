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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.AskLikeBody
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

/** The two doors onto the one screen: an order table, or a quote-stage table. */
sealed interface ThreadDoor {
    val id: String
    data class Order(override val id: String) : ThreadDoor
    data class Invitation(override val id: String) : ThreadDoor
}

/**
 * One screen, two doors (Django §6). The grammar is the Jowi tab's: my turn
 * on the right in a kiremit frame, other seats on the left with WHO · seat,
 * `system` centred and faint, a Jowi turn as a "/" card with 👍/👎 when it
 * carries `kb`. Routing has no recipient picker — that is the server's job.
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
    /** `private` / `routed` answers: drawn for the asker alone, never stored. */
    val localLines = mutableStateListOf<String>()
    var privateNote by mutableStateOf(false)
        private set
    var polish by mutableStateOf<Pair<String, String>?>(null) // typed, suggestion
        private set
    var polishing by mutableStateOf(false)
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

    /** The poll the web runs: only new turns since the last one we hold. */
    suspend fun pollOnce() {
        val d = door ?: return
        val after = payload?.messages?.lastOrNull()?.at ?: return
        val (r, _) = partnerCall(lang) {
            if (d is ThreadDoor.Order) Graph.api.orderThreadNew(d.id, after) else Graph.api.invitationThreadNew(d.id, after)
        }
        val fresh = r?.messages.orEmpty()
        if (fresh.isNotEmpty()) {
            val p = payload ?: return
            payload = p.copy(messages = p.messages.orEmpty() + fresh)
        }
    }

    fun send(text: String, textOriginal: String? = null) {
        val d = door ?: return
        val t = text.trim()
        if (t.isEmpty() || busy) return
        busy = true; note = null; localLines.clear(); privateNote = false
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                val body = ThreadPostBody(t, textOriginal?.takeIf { it != t })
                if (d is ThreadDoor.Order) Graph.api.orderThreadPost(d.id, body) else Graph.api.invitationThreadPost(d.id, body)
            }
            busy = false
            when {
                r == null -> note = err
                r.kind == "posted" -> load()
                else -> {
                    // private / routed: shown to the asker alone, under the last turn
                    privateNote = r.kind == "private"
                    localLines.addAll(r.lines.orEmpty().mapNotNull { it.text }.ifEmpty { listOfNotNull(r.line) })
                    load()
                }
            }
        }
    }

    /** Wording help — never applied on its own. */
    fun askPolish(text: String) {
        val t = text.trim()
        if (t.isEmpty() || polishing) return
        polishing = true
        viewModelScope.launch {
            val (r, _) = partnerCall(lang) { Graph.api.textPolish(ai.workis.jowi.data.TextBody(t)) }
            polishing = false
            val suggestion = r?.suggestion?.takeIf { it.isNotEmpty() && it != t }
            if (suggestion != null) polish = t to suggestion
        }
    }

    fun clearPolish() { polish = null }

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
fun ThreadScreen(door: ThreadDoor, onBack: () -> Unit, onContract: (String) -> Unit = {}) {
    val vm: ThreadViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "thread-${door.id}")
    val colors = WorkisTheme.colors
    var text by remember { mutableStateOf("") }
    var editTerm by remember { mutableStateOf<OrderTerm?>(null) }
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
                    modifier = Modifier.clickable { onContract(orderId) }.padding(horizontal = 4.dp),
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
                // the seven term cards live in a strip above the turns; it opens
                // by itself while something is still missing
                val terms = p.terms.orEmpty()
                if (terms.isNotEmpty()) {
                    TermsStrip(terms, p.nTermsOk ?: 0, p.nTerms ?: terms.size, vm) { editTerm = it }
                    Spacer(Modifier.height(10.dp))
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(messages, key = { i, m -> m.id ?: "m$i" }) { _, m -> MessageRow(m, vm) }
                    if (vm.localLines.isNotEmpty()) {
                        item { PrivateCard(vm.localLines.toList(), vm.privateNote) }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                // the polish card: suggested wording, applied only on a tap
                vm.polish?.let { (typed, suggestion) ->
                    Column(
                        Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.thread_polish_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, color = Beige)
                        Text(suggestion, fontSize = 14.sp, lineHeight = 19.sp, color = Beige)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                stringResource(R.string.thread_polish_apply), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText,
                                modifier = Modifier.clickable { text = suggestion; vm.clearPolish() },
                            )
                            Text(
                                stringResource(R.string.thread_polish_ignore), fontSize = 13.sp, color = colors.muted,
                                modifier = Modifier.clickable { vm.clearPolish() },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // the coordinator reads the table; no composer
                if (!p.readOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(stringResource(R.string.thread_placeholder), color = colors.faint) },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        val canSend = text.isNotBlank() && !vm.busy
                        IconButton(onClick = { val t = text; text = ""; vm.clearPolish(); vm.send(t) }, enabled = canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = if (canSend) Kiremit500 else colors.faint)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        if (vm.polishing) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.faint)
                        else Text(
                            stringResource(R.string.thread_polish), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (text.isBlank()) colors.faint else colors.accentText,
                            modifier = Modifier.clickable(enabled = text.isNotBlank()) { vm.askPolish(text) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    editTerm?.let { t -> TermEditSheet(t, vm) { editTerm = null } }
}

// ---------- the turns

@Composable
private fun MessageRow(m: ThreadMessage, vm: ThreadViewModel) {
    val colors = WorkisTheme.colors
    var showOriginal by remember(m.id) { mutableStateOf(false) }
    val translated = m.textLocalized?.takeIf { it.isNotEmpty() && it != m.content }
    val body = if (translated != null && !showOriginal) translated else m.content.orEmpty()
    when {
        // a system turn sits in the middle, faint — the server writes these
        m.kind == "system" -> Text(
            body, fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
                m.atLabel?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint) }
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
                m.atLabel?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint) }
            }
        }
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
                    Text("🔒 " + stringResource(R.string.thread_private_note), fontSize = 11.sp, color = colors.faint)
                }
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
        t.prevDisplay?.takeIf { it.isNotEmpty() }?.let {
            Text(stringResource(R.string.term_previous) + ": $it", fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint)
        }
        t.source?.takeIf { it.isNotEmpty() }?.let {
            Text("“$it”", fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.conflict == true) StatusPill(stringResource(R.string.term_conflict), Beige)
            if (t.amendment == true) StatusPill(stringResource(R.string.term_amendment), Beige)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (t.canConfirm == true) {
                Text(
                    stringResource(R.string.term_confirm), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText,
                    modifier = Modifier.clickable(enabled = !vm.busy) { t.key?.let { vm.term(it, jsonBody("action" to "confirm")) } },
                )
            }
            if (t.canEdit == true) {
                Text(
                    stringResource(if (display == null) R.string.term_state else R.string.term_edit),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                    modifier = Modifier.clickable(enabled = !vm.busy) { onEdit(t) },
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
