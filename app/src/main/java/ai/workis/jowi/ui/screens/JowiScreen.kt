package ai.workis.jowi.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.AskBody
import ai.workis.jowi.data.AskLikeBody
import ai.workis.jowi.data.AskPreviewBody
import ai.workis.jowi.data.AskSeller
import ai.workis.jowi.data.absoluteWorkisUrl
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.openInApp
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/** A purchase request read by Jowi — the parsed line + the catalogue's sellers (price-blind). */
data class AskMatch(val item: String, val qty: String?, val sellers: List<AskSeller>)

/**
 * One line of the conversation — server history rows and the live turns
 * share this shape. The thread is the SERVER's (one AskThread per user,
 * shared with the web drawer); nothing is stored on device.
 */
data class JowiLine(
    val id: Long,
    val who: String, // user | jowi | expert | lead
    val text: String = "",
    val original: String? = null, // the typed text when Jowi rewrote it
    val href: String? = null,
    val match: AskMatch? = null,
    val source: String? = null,
    val surface: String? = null,
    val pending: Boolean = false,
    val error: String? = null, // guidance (null answer), never a red error
    val label: String? = null, // "🎓 Uzman doğrulamalı" | "Üretken model bilgisi"
    val kb: String? = null, // session key → thumbs
    val verdict: String? = null, // "thanks" | "already"
)

class JowiViewModel : ViewModel() {
    val lines = mutableStateListOf<JowiLine>()
    var loaded by mutableStateOf(false)
        private set
    var previewing by mutableStateOf(false)
        private set
    /** A MATERIAL rewrite waits for the asker's choice (typed, reading). */
    var clarify by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var nextId = 1L
    private fun newId() = nextId++
    private val lang get() = Graph.language.value
    private val hasPending get() = lines.any { it.pending }

    /** The server's thread replaces the local one — except mid-turn. */
    fun reload() {
        if (hasPending) return
        viewModelScope.launch {
            when (val r = safeCall(lang) { Graph.api.askHistory() }) {
                is ApiResult.Ok -> {
                    lines.clear()
                    r.value.lines.orEmpty().forEach { l ->
                        val match = if (l.kind == "match") AskMatch(l.item.orEmpty(), l.qty, l.sellers.orEmpty()) else null
                        lines += JowiLine(
                            id = newId(), who = l.who ?: "jowi", text = l.text.orEmpty(),
                            original = l.qOriginal, href = l.href?.takeIf { it.isNotEmpty() },
                            match = match, source = l.source, surface = l.surface, label = l.label, kb = l.kb,
                        )
                    }
                }
                is ApiResult.Err -> Unit // an unreachable history is not an error screen
            }
            loaded = true
        }
    }

    fun clear() {
        viewModelScope.launch {
            val ok = runCatching { Graph.api.askHistoryClear().success }.getOrDefault(false)
            if (ok) lines.clear()
        }
    }

    /**
     * Contract rule: unchanged → send typed; changed but not material → send
     * Jowi's reading with the typed text alongside; material → ask first;
     * preview failure/timeout (10 s) → send typed.
     */
    fun submit(typed: String) {
        if (typed.isBlank() || clarify != null || previewing) return
        previewing = true
        viewModelScope.launch {
            val p = withTimeoutOrNull(10_000) {
                runCatching { Graph.api.askPreview(AskPreviewBody(typed)) }.getOrNull()
            }
            previewing = false
            val reading = p?.qClarified
            if (p?.changed != true || reading.isNullOrEmpty() || reading == typed) {
                send(typed, null)
            } else if (p.material == true) {
                clarify = typed to reading
            } else {
                send(reading, typed)
            }
        }
    }

    fun chooseReading() { clarify?.let { (typed, reading) -> clarify = null; send(reading, typed) } }
    fun chooseOwn() { clarify?.let { (typed, _) -> clarify = null; send(typed, null) } }

    fun send(q: String, original: String?) {
        lines += JowiLine(id = newId(), who = "user", text = q, original = original?.takeIf { it != q })
        val pendingId = newId()
        lines += JowiLine(id = pendingId, who = "jowi", pending = true)
        viewModelScope.launch {
            val i = { lines.indexOfFirst { it.id == pendingId } }
            try {
                val r = Graph.api.ask(AskBody(q = q, qOriginal = original?.takeIf { it != q }, page = "lists"))
                val match = if (r.kind == "match") AskMatch(r.item.orEmpty(), r.qty, r.sellers.orEmpty()) else null
                val guidance = if (r.answer == null) {
                    r.message?.takeIf { it.isNotEmpty() }
                        ?: if (lang == "en") "Couldn't answer that — try rephrasing." else "Buna cevap veremedim — farklı sorabilirsin."
                } else null
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(
                    pending = false, text = r.answer.orEmpty(), error = guidance,
                    source = r.source, surface = r.surface, match = match, label = r.label, kb = r.kb,
                )
            } catch (e: HttpException) {
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(pending = false, error = messageFromBody(e, lang))
            } catch (e: Exception) {
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(pending = false, error = statusMessage(null, lang))
            }
        }
    }

    /** 👍 / 👎 — first verdict wins; a 👎 opens a review on the expert side. */
    fun rate(line: JowiLine, up: Boolean) {
        val kb = line.kb ?: return
        viewModelScope.launch {
            val r = runCatching { Graph.api.askLike(AskLikeBody(kb, if (up) "up" else "down")) }.getOrNull() ?: return@launch
            if (!r.success) return@launch
            val idx = lines.indexOfFirst { it.id == line.id }
            if (idx >= 0) lines[idx] = lines[idx].copy(verdict = if (r.rated == false) "already" else "thanks")
        }
    }
}

@Composable
fun JowiScreen(vm: JowiViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    var question by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(Unit) { vm.reload() }
    // keep the newest turn in view: a new line, the clarify card, or the answer replacing the spinner
    val lastText = vm.lines.lastOrNull()?.text
    LaunchedEffect(vm.lines.size, vm.clarify, lastText) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size + 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Jowi", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.weight(1f))
            if (vm.lines.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.ink) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.jowi_clear), color = colors.danger, fontSize = 14.sp) },
                            onClick = { menu = false; askClear = true },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.lines.isEmpty() && vm.clarify == null && !vm.previewing && vm.loaded) {
                item {
                    Text(
                        stringResource(R.string.jowi_empty),
                        fontSize = 15.sp, lineHeight = 22.sp, color = colors.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    )
                }
            }
            itemsIndexed(vm.lines, key = { _, l -> l.id }) { _, line ->
                if (line.who == "user") UserBubble(line) else ReplyBubble(line, vm)
            }
            vm.clarify?.let { (typed, reading) -> item { ClarifyCard(typed, reading, vm) } }
            if (vm.previewing || (!vm.loaded && vm.lines.isEmpty())) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Kiremit500) } }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text(stringResource(R.string.jowi_prompt), color = colors.faint) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                ),
                modifier = Modifier.weight(1f),
            )
            val canSend = question.isNotBlank() && vm.clarify == null && !vm.previewing
            IconButton(onClick = { val q = question.trim(); question = ""; vm.submit(q) }, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = if (canSend) Kiremit500 else colors.faint)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.jowi_clear), color = colors.ink) },
            text = { Text(stringResource(R.string.jowi_clear_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { askClear = false; vm.clear() }) {
                    Text(stringResource(R.string.jowi_clear), color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

/** The asker's line, right-aligned: ink text, the seat hue as a thin kiremit OUTLINE with a whisper of fill. */
@Composable
private fun UserBubble(line: JowiLine) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(Kiremit500.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .border(1.dp, Kiremit500.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(line.text, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink, textAlign = TextAlign.End)
            line.original?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.clarify_typed_label) + " · " + it,
                    fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint, textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Jowi's (or a human's) reply on a scheme-adaptive card with the slash as the mark; a null answer is guidance — the beige note idiom. */
@Composable
private fun ReplyBubble(line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val guidance = line.error != null && !line.pending
    Row(Modifier.fillMaxWidth().padding(end = 32.dp), verticalAlignment = Alignment.Top) {
        Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kiremit500, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .background(if (guidance) BeigeBg else colors.ink.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (line.who == "expert" || line.who == "lead") {
                // a human answered in the thread — the badge follows the seat
                Text(
                    if (line.who == "expert") "🎓 " + stringResource(R.string.role_expert) else "📍 " + stringResource(R.string.role_lead),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                    color = if (line.who == "expert") colors.blue else colors.accentText,
                )
            }
            when {
                line.pending -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                line.error != null -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Beige, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(line.error, fontSize = 14.sp, lineHeight = 20.sp, color = Beige)
                }
                else -> {
                    Text(line.text, fontSize = 15.sp, lineHeight = 22.sp, color = colors.ink)
                    line.match?.takeIf { it.sellers.isNotEmpty() }?.let { MatchCard(it) }
                    line.href?.let { href ->
                        Text(
                            "↗ " + stringResource(R.string.jowi_open_link),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.blue,
                            modifier = Modifier.clickable { openInApp(context, absoluteWorkisUrl(href)) },
                        )
                    }
                    // the stamp: verified-by-expert vs generative — text, never colour alone
                    val label = line.label
                    if (!label.isNullOrEmpty()) {
                        Text(label, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = colors.muted)
                    } else if (line.source != null && line.surface != null) {
                        Text(
                            stringResource(
                                when {
                                    line.surface == "expert" -> R.string.ask_answer_source_knowledge
                                    line.surface == "console" -> R.string.ask_answer_source_console
                                    line.source == "page" -> R.string.ask_answer_source_page
                                    else -> R.string.ask_answer_source_chain
                                },
                            ),
                            fontSize = 12.sp, color = colors.faint,
                        )
                    }
                    if (line.kb != null) Thumbs(line, vm)
                }
            }
        }
    }
}

@Composable
private fun Thumbs(line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val verdict = line.verdict
    if (verdict != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (verdict == "already") R.string.thumbs_already else R.string.thumbs_thanks), fontSize = 12.sp, color = SuccessGreen)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            ThumbButton("👍 " + stringResource(R.string.thumbs_up), stringResource(R.string.thumbs_up_full)) { vm.rate(line, true) }
            ThumbButton("👎 " + stringResource(R.string.thumbs_down), stringResource(R.string.thumbs_down_full)) { vm.rate(line, false) }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = colors
}

@Composable
private fun ThumbButton(label: String, description: String, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(
        label,
        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted,
        modifier = Modifier
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** The two-reading card — the asker decides which text becomes the record. */
@Composable
private fun ClarifyCard(typed: String, reading: String, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.clarify_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp, color = Beige)
        Text(reading, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink)
        Text(stringResource(R.string.clarify_typed_label) + " · " + typed, fontFamily = WorkisMono, fontSize = 11.sp, lineHeight = 16.sp, color = colors.faint)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.chooseReading() },
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.height(36.dp),
            ) { Text(stringResource(R.string.clarify_use_reading), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            TextButton(onClick = { vm.chooseOwn() }, modifier = Modifier.height(36.dp).border(1.dp, colors.border, CircleShape)) {
                Text(stringResource(R.string.clarify_use_own), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.muted)
            }
        }
    }
}

/** Purchase request read by Jowi: the parsed line, then the sellers with their offer count — price-blind here. */
@Composable
private fun MatchCard(m: AskMatch) {
    val colors = WorkisTheme.colors
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.match_header).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint)
        Text(listOfNotNull(m.qty?.let { "$it ×" }, m.item).joinToString(" "), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
        m.sellers.take(5).forEach { s ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(s.name ?: "?", fontSize = 14.sp, color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    s.city?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, fontSize = 12.sp, color = colors.faint)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${s.offers?.size ?: 0} " + stringResource(R.string.match_offers), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink.copy(alpha = 0.08f)))
            }
        }
    }
}
