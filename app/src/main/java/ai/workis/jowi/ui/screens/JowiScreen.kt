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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import ai.workis.jowi.data.AskLine
import ai.workis.jowi.ui.screens.expert.NoteBand
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.data.AskNext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.data.AskOption
import ai.workis.jowi.data.ScreenContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import ai.workis.jowi.ui.components.JowiOptions
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/** A purchase request read by Jowi — the parsed line + the catalogue's sellers (price-blind). */
data class AskMatch(
    val item: String,
    val qty: String?,
    val unit: String?,
    val nProducts: Int?,
    val approximate: Boolean,
    val listStage: String?,
    val sellers: List<AskSeller>,
) {
    /** "100 metre · yeşil emici spiral hortum" — `item` no longer carries the quantity. */
    val title: String
        get() {
            val measure = listOfNotNull(qty?.takeIf { it.isNotEmpty() }, unit?.takeIf { it.isNotEmpty() }).joinToString(" ")
            return listOf(measure, item).filter { it.isNotEmpty() }.joinToString(" · ")
        }
}

/**
 * A trailing full stop or a stray space is not a rewrite. A changed word, an
 * added "?" or "!", or a different case is.
 */
fun materiallyDifferent(typed: String, shown: String): Boolean {
    fun norm(s: String) = s.trim().trimEnd('.', ' ', '\u00A0')
    return norm(typed) != norm(shown)
}

/** ISO-8601 for a turn written right now, so live and history read alike. */
private fun nowStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())

/** "HH:mm" under a turn; empty when the stamp is unreadable. */
fun clockOf(at: String?): String {
    val t = at.orEmpty()
    val i = t.indexOf('T')
    return if (i > 0 && t.length >= i + 6) t.substring(i + 1, i + 6) else ""
}

/** "18 Eylül 2026" — the faint separator when the day turns over. */
fun dayOf(at: String?): String {
    val d = at.orEmpty().take(10)
    if (d.length != 10) return ""
    val parsed = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(d)
    }.getOrNull() ?: return ""
    val locale = if (ai.workis.jowi.Graph.language.value == "en") java.util.Locale.UK else java.util.Locale("tr", "TR")
    return java.text.SimpleDateFormat("d MMMM yyyy", locale).format(parsed)
}

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
    val listId: String? = null, // a chat-born purchase list → "Listeyi aç"
    val next: AskNext? = null, // the step after a technical answer
    val question: String? = null, // the asker's text this reply answers
    val options: List<AskOption>? = null, // "which one do you mean?" (2026-09-17)
    val optionPicked: Boolean = false, // a tapped option closes the set
    val at: String? = null, // the turn's stamp; a live turn takes "now"
    /** "Teklif topla" on a match card: the spinner and the list stay on THAT card. */
    val collecting: Boolean = false,
    // a written record is never deleted (2026-09-18): the server's row id and
    // whether this turn can still be WITHDRAWN into a stub in its place
    val serverId: String? = null,
    val canHide: Boolean = false,
    /** A withdrawn turn's stub: dashed trace where the question stood; "Geri getir" while `canUnhide`. */
    val hidden: Boolean = false,
    val trace: String? = null,
    val canUnhide: Boolean = false,
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

    /** A chat-born list opened from the thread — the native list detail as a cover. */
    var openList by mutableStateOf<String?>(null)

    /** Bumped whenever something lands at the BOTTOM — older pages prepend and must not scroll. */
    var scrollTick by mutableStateOf(0)
        private set
    /** A server sentence to show once (a 400's reason, "Sorunuz ekibe yeniden iletildi."). */
    var note by mutableStateOf<String?>(null)
    var noteRes by mutableStateOf<Int?>(null)

    // "Eski kayıtları yükle": the archive paged backwards until `nextBefore` is null
    var loadingOlder by mutableStateOf(false)
        private set
    var noOlder by mutableStateOf(false)
        private set
    private var nextBefore: String? = null

    /** The scope picker: "Genel" drops `ctxList`; a newly opened list starts scoped. */
    var scopeGeneral by mutableStateOf(false)
    var scopeFor: String? = null

    private var nextId = 1L
    private fun newId() = nextId++
    private val lang get() = Graph.language.value
    private val hasPending get() = lines.any { it.pending }

    /** The page the asker came from; a seller's catalogue screens say "products". */
    private fun askPage(ctx: ScreenContext.Snapshot?): String =
        if (ctx?.screen?.startsWith("seller.product") == true) "products" else ScreenContext.page ?: "lists"

    /** The open list rides as `ctxList` while the scope stays on it. */
    private fun ctxList(): String? = if (scopeGeneral) null else ScreenContext.openListId

    /** Server rows → lines; each reply remembers the question it answers ("Teklif topla" resends it). */
    private fun fromServer(rows: List<AskLine>, kept: Map<String, Pair<List<AskOption>, Boolean>> = emptyMap()): List<JowiLine> {
        val out = ArrayList<JowiLine>(rows.size)
        var lastQ: String? = null
        rows.forEach { l ->
            // a withdrawn turn: a stub in its place, the answer rows folded into it
            if (l.hidden == true) {
                out += JowiLine(
                    id = newId(), who = "user", hidden = true, trace = l.trace?.takeIf { it.isNotEmpty() },
                    canUnhide = l.canUnhide == true, serverId = l.id, at = l.at,
                )
                return@forEach
            }
            val match = if (l.kind == "match") AskMatch(
                l.item.orEmpty(), l.qty, l.unit, l.nProducts, l.approximate == true, l.listStage, l.sellers.orEmpty(),
            ) else null
            val isUser = l.who == "user"
            var line = JowiLine(
                id = newId(), who = l.who ?: "jowi", text = l.text.orEmpty(),
                original = l.qOriginal, href = l.href?.takeIf { it.isNotEmpty() },
                match = match, source = l.source, surface = l.surface, label = l.label, kb = l.kb,
                listId = l.listId?.takeIf { it.isNotEmpty() }, next = l.next, question = if (isUser) null else lastQ,
                at = l.at, serverId = l.id, canHide = l.canHide == true,
            )
            if (!isUser) {
                kept[lastQ.orEmpty() + "\u001F" + l.text.orEmpty()]?.let { (opts, picked) ->
                    line = line.copy(options = opts, optionPicked = picked)
                }
            }
            out += line
            if (isUser) lastQ = l.text
        }
        return out
    }

    /** The server's thread replaces the local one — except mid-turn. */
    fun reload() {
        if (hasPending) return
        viewModelScope.launch {
            when (val r = safeCall(lang) { Graph.api.askHistory() }) {
                is ApiResult.Ok -> {
                    // options are not in history rows: keep the ones this session
                    // received on the reply they belong to. The clarify sentence
                    // repeats, so the key carries the question it answers too.
                    val kept = lines.filter { it.who != "user" && it.options != null }
                        .associate { (it.question.orEmpty() + "\u001F" + it.text) to (it.options!! to it.optionPicked) }
                    lines.clear()
                    nextBefore = null; noOlder = false
                    lines += fromServer(r.value.lines.orEmpty(), kept)
                    // the server keeps a "Teklif topla" resend as its own question +
                    // answer pair; the card that bore the match carries the listId,
                    // so the repeat pair is folded away
                    val collected = HashMap<String, String>() // question → listId
                    lines.forEach { l -> if (l.who != "user") l.listId?.let { collected[l.question.orEmpty()] = it } }
                    foldResends(collected)
                    scrollTick++
                }
                is ApiResult.Err -> Unit // an unreachable history is not an error screen
            }
            loaded = true
        }
    }

    /** One archive page older than what is on screen, prepended; the scroll stays put. */
    fun loadOlder() {
        if (loadingOlder || noOlder) return
        loadingOlder = true
        viewModelScope.launch {
            when (val r = safeCall(lang) { Graph.api.askHistoryArchived(nextBefore) }) {
                is ApiResult.Ok -> {
                    val rows = fromServer(r.value.lines.orEmpty())
                    lines.addAll(0, rows)
                    nextBefore = r.value.nextBefore?.takeIf { it.isNotEmpty() }
                    if (nextBefore == null || rows.isEmpty()) noOlder = true
                }
                is ApiResult.Err -> note = r.message
            }
            loadingOlder = false
        }
    }

    /** "Arşivle": nothing is deleted — the open history closes into the archive and a new one starts. */
    fun archive() {
        viewModelScope.launch {
            val ok = runCatching { Graph.api.askHistoryArchive().success }.getOrDefault(false)
            if (ok) { lines.clear(); nextBefore = null; noOlder = false }
        }
    }

    /** "Soruyu geri çek": the turn becomes the returned stub IN PLACE — no reload. */
    fun hide(line: JowiLine) {
        val id = line.serverId ?: return
        viewModelScope.launch {
            try {
                val r = Graph.api.askHide(id)
                if (!r.success) { note = r.error; return@launch }
                val idx = lines.indexOfFirst { it.id == line.id }
                if (idx < 0) return@launch
                // the question and the answers under it, up to the next question
                var end = idx + 1
                while (end < lines.size && lines[end].who != "user") end++
                repeat(end - idx) { lines.removeAt(idx) }
                // an older server sends no stub: the turn simply leaves
                r.stub?.let { s ->
                    lines.add(idx, JowiLine(
                        id = newId(), who = "user", hidden = true, trace = s.trace?.takeIf { it.isNotEmpty() },
                        canUnhide = s.canUnhide == true, serverId = s.id ?: id, at = s.at ?: line.at,
                    ))
                }
            } catch (e: HttpException) {
                note = messageFromBody(e, lang)
            } catch (e: Exception) {
                note = statusMessage(null, lang)
            }
        }
    }

    /** "Geri getir": the stub becomes the returned rows; a re-opened hand-off says so. */
    fun unhide(line: JowiLine) {
        val id = line.serverId ?: return
        viewModelScope.launch {
            try {
                val r = Graph.api.askUnhide(id)
                if (!r.success) { note = r.error; return@launch }
                val idx = lines.indexOfFirst { it.id == line.id }
                if (idx < 0) return@launch
                val rows = fromServer(r.lines.orEmpty())
                lines.removeAt(idx)
                lines.addAll(idx, rows)
                if (r.lines.orEmpty().firstOrNull()?.handoffReopened == true) noteRes = R.string.ask_handoff_reopened
            } catch (e: HttpException) {
                note = messageFromBody(e, lang)
            } catch (e: Exception) {
                note = statusMessage(null, lang)
            }
        }
    }

    /** "Düzenlemeye dön": the typed text goes back to the field, nothing is sent. */
    fun backToEdit(): String? = clarify?.also { clarify = null }?.first

    /** Move a resend's `listId` onto the match card and drop the repeat pair. */
    private fun foldResends(collected: Map<String, String>) {
        if (collected.isEmpty()) return
        val drop = mutableSetOf<Long>()
        lines.forEachIndexed { i, l ->
            if (l.match == null) return@forEachIndexed
            val listId = collected[l.question.orEmpty()] ?: return@forEachIndexed
            lines[i] = l.copy(listId = l.listId ?: listId)
            // the repeat is the LATER pair with the same question and no match
            lines.forEachIndexed { j, other ->
                if (j > i && other.question == l.question && other.match == null && other.listId == listId) {
                    drop += other.id
                    lines.getOrNull(j - 1)?.takeIf { it.who == "user" && it.text == l.question }?.let { drop += it.id }
                }
            }
        }
        if (drop.isNotEmpty()) lines.removeAll { it.id in drop }
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

    /** "Teklif topla": the same question again with createList — the server opens the chat-born list (pool) or records the demand gap. */
    fun collectQuotes(line: JowiLine) {
        val q = line.question ?: lines.lastOrNull { it.who == "user" }?.text ?: return
        send(q, null, createList = true)
    }

    /** "Teklif topla" on a match card: the same sentence again, but the answer
     *  stays on THAT card — no new pair is drawn. */
    fun collectQuotesOnCard(line: JowiLine) {
        val q = line.question ?: lines.lastOrNull { it.who == "user" }?.text ?: return
        val id = line.id
        val idx = lines.indexOfFirst { it.id == id }
        if (idx < 0 || lines[idx].collecting) return
        lines[idx] = lines[idx].copy(collecting = true)
        viewModelScope.launch {
            val ctx = ScreenContext.current
            val r = runCatching {
                Graph.api.ask(
                    AskBody(
                        q = q, page = askPage(ctx), ctxList = ctxList(),
                        createList = true, screen = ctx?.screen, state = ctx?.state,
                        visible = ctx?.visible?.takeIf { it.isNotEmpty() }, seller = ctx?.seller,
                    ),
                )
            }.getOrNull()
            val at = lines.indexOfFirst { it.id == id }
            if (at >= 0) lines[at] = lines[at].copy(
                collecting = false,
                listId = r?.listId?.takeIf { it.isNotEmpty() } ?: lines[at].listId,
            )
        }
    }

    fun send(q: String, original: String?, createList: Boolean = false) {
        val now = nowStamp()
        val userId = newId()
        lines += JowiLine(id = userId, who = "user", text = q, original = original?.takeIf { it != q }, at = now)
        val pendingId = newId()
        lines += JowiLine(id = pendingId, who = "jowi", pending = true, question = q, at = now)
        scrollTick++
        viewModelScope.launch {
            val i = { lines.indexOfFirst { it.id == pendingId } }
            try {
                // where the user is (2026-09-17): the server resolves "bu ürün" /
                // "sağ üstteki düğme" against the snapshot the screens wrote
                val ctx = ScreenContext.current
                val r = Graph.api.ask(
                    AskBody(
                        q = q, qOriginal = original?.takeIf { it != q },
                        page = askPage(ctx), ctxList = ctxList(),
                        createList = if (createList) true else null,
                        screen = ctx?.screen, state = ctx?.state, visible = ctx?.visible?.takeIf { it.isNotEmpty() },
                        seller = ctx?.seller,
                    ),
                )
                val match = if (r.kind == "match") AskMatch(
                    r.item.orEmpty(), r.qty, r.unit, r.nProducts, r.approximate == true, r.listStage, r.sellers.orEmpty(),
                ) else null
                val guidance = if (r.answer == null) {
                    r.message?.takeIf { it.isNotEmpty() }
                        ?: if (lang == "en") "Couldn't answer that — try rephrasing." else "Buna cevap veremedim — farklı sorabilirsin."
                } else null
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(
                    pending = false, text = r.answer.orEmpty(), error = guidance,
                    source = r.source, surface = r.surface, match = match, label = r.label, kb = r.kb,
                    listId = r.listId?.takeIf { it.isNotEmpty() }, next = r.next,
                    options = r.options?.takeIf { it.isNotEmpty() },
                    serverId = r.turn?.answerIds?.firstOrNull(),
                )
                // the row ids ride in the answer (2026-09-19): "Soruyu geri çek"
                // works the moment the reply lands, no history re-read
                r.turn?.let { t ->
                    val u = lines.indexOfFirst { it.id == userId }
                    if (u >= 0) lines[u] = lines[u].copy(serverId = t.questionId, canHide = t.canHide == true)
                }
                scrollTick++
            } catch (e: HttpException) {
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(pending = false, error = messageFromBody(e, lang))
            } catch (e: Exception) {
                val idx = i(); if (idx >= 0) lines[idx] = lines[idx].copy(pending = false, error = statusMessage(null, lang))
            }
        }
    }

    /**
     * A tapped option asks its precise follow-up with the same screen context;
     * the open-ended one (`q == null`) hands the field back to the asker.
     */
    fun pick(line: JowiLine, option: AskOption): Boolean {
        val idx = lines.indexOfFirst { it.id == line.id }
        if (idx >= 0) lines[idx] = lines[idx].copy(optionPicked = true)
        val q = option.q
        if (q.isNullOrEmpty()) return false
        send(q, null)
        return true
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
    vm.openList?.let { id ->
        ai.workis.jowi.ui.screens.partner.ListDetailScreen(id, onBack = { vm.openList = null })
        return
    }
    var question by remember { mutableStateOf("") }
    val askFocus = remember { FocusRequester() }
    var askArchive by remember { mutableStateOf(false) }
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(Unit) { vm.reload() }
    // keep the newest turn in view: a new line, the clarify card, or the answer
    // replacing the spinner — every one bumps the tick; an older page prepends
    // and leaves the scroll alone. The target is the trailing spacer, which
    // always clears the ask field
    LaunchedEffect(vm.scrollTick, vm.clarify) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }
    // the list open on the Pazar tab, if any: the standard scope picker
    // [ <list> | Genel ] — "Genel" drops ctxList; no list, no picker
    val openListId = ScreenContext.openListId
    val openListTitle = ScreenContext.openListTitle
    LaunchedEffect(openListId) {
        if (openListId != vm.scopeFor) { vm.scopeFor = openListId; vm.scopeGeneral = false }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Jowi", style = MaterialTheme.typography.headlineMedium, color = colors.ink)
        if (openListId != null) {
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { i, general ->
                    SegmentedButton(
                        selected = vm.scopeGeneral == general,
                        onClick = { vm.scopeGeneral = general },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Kiremit500.copy(alpha = 0.12f), activeContentColor = colors.ink,
                            inactiveContainerColor = colors.surface, inactiveContentColor = colors.muted,
                            activeBorderColor = colors.border, inactiveBorderColor = colors.border,
                        ),
                    ) {
                        Text(
                            if (general) stringResource(R.string.jowi_scope_general) else openListTitle ?: stringResource(R.string.jowi_scope_this_list),
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        NoteBand(vm.note ?: vm.noteRes?.let { stringResource(it) })

        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // the top of the history: the archive door and the way back into it —
            // nothing is deleted, the record moves and can be read again
            if (vm.loaded) {
                item(key = "history-top") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        when {
                            vm.loadingOlder -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = colors.faint)
                            vm.noOlder -> Text(stringResource(R.string.jowi_no_older), fontSize = 12.sp, color = colors.faint)
                            else -> Text(
                                stringResource(R.string.jowi_load_older), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                                modifier = Modifier.clickable { vm.loadOlder() }.padding(vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (vm.lines.any { !it.pending }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { askArchive = true }.padding(vertical = 4.dp),
                            ) {
                                Icon(WorkisIcons.Archive, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.jowi_clear), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted)
                            }
                        }
                    }
                }
            }
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
            itemsIndexed(vm.lines, key = { _, l -> l.id }) { i, line ->
                // a faint date line whenever the day turns over
                val day = dayOf(line.at)
                val prevDay = vm.lines.getOrNull(i - 1)?.let { dayOf(it.at) }
                if (day.isNotEmpty() && day != prevDay) {
                    Text(
                        day, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                when {
                    line.hidden -> WithdrawnStub(line, vm)
                    line.who == "user" -> UserBubble(line, vm)
                    else -> ReplyBubble(line, vm) { askFocus.requestFocus() }
                }
            }
            vm.clarify?.let { (typed, reading) ->
                item { ClarifyCard(typed, reading, vm) { back -> question = back; askFocus.requestFocus() } }
            }
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
                modifier = Modifier.weight(1f).focusRequester(askFocus),
            )
            val canSend = question.isNotBlank() && vm.clarify == null && !vm.previewing
            IconButton(onClick = { val q = question.trim(); question = ""; vm.submit(q) }, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = if (canSend) Kiremit500 else colors.faint)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    // archiving is not deleting — a neutral confirm, never the danger colour
    if (askArchive) {
        AlertDialog(
            onDismissRequest = { askArchive = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.jowi_clear), color = colors.ink) },
            text = { Text(stringResource(R.string.jowi_clear_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { askArchive = false; vm.archive() }) {
                    Text(stringResource(R.string.jowi_clear), color = colors.accentText, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { askArchive = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

/**
 * A withdrawn turn, where the question stood: right-aligned, dashed frame, the
 * server's trace in italics, "Geri getir" beside it while `canUnhide`. The
 * WhatsApp "deleted" idiom with the two differences that are the point — the
 * content is not deleted, and here it can come back.
 */
@Composable
private fun WithdrawnStub(line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val dash = colors.faint
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .drawBehind {
                    drawRoundRect(
                        color = dash, cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                line.trace ?: stringResource(R.string.jowi_withdrawn_trace),
                fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = colors.muted,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (line.canUnhide) {
                Spacer(Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.unhide(line) }) {
                    Icon(WorkisIcons.Undo, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.jowi_bring_back), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
                }
            }
        }
    }
}

/**
 * The asker's line, right-aligned: ink text, the seat hue as a thin kiremit
 * OUTLINE with a whisper of fill. A long press offers "Soruyu geri çek" — only
 * while the server says the turn did nothing (`canHide`); otherwise no menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val showTypedLabel = stringResource(R.string.clarify_show_typed)
    var menu by remember(line.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    leadingIcon = { Icon(WorkisIcons.Undo, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp)) },
                    text = { Text(stringResource(R.string.jowi_hide_turn), color = colors.ink, fontSize = 14.sp) },
                    onClick = { menu = false; vm.hide(line) },
                )
            }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(Kiremit500.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .border(1.dp, Kiremit500.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .combinedClickable(enabled = line.canHide && line.serverId != null, onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(line.text, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink, textAlign = TextAlign.End)
            // Jowi rewrote the question: the record hides behind a keyboard glyph
            // rather than a second permanent line. A monogram would say WHO wrote
            // it — that is kept for the three-seat tables.
            var showTyped by remember(line.id) { mutableStateOf(false) }
            val typed = line.original?.takeIf { materiallyDifferent(it, line.text) }
            if (typed != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 30.dp)
                            .clickable { showTyped = !showTyped }
                            .semantics { contentDescription = showTypedLabel },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(WorkisIcons.Keyboard, contentDescription = null, tint = if (showTyped) colors.accentText else colors.faint, modifier = Modifier.size(16.dp))
                    }
                }
                if (showTyped) {
                    Text(
                        stringResource(R.string.clarify_you_wrote) + " · " + typed,
                        fontFamily = WorkisMono, fontSize = 10.sp, lineHeight = 15.sp, color = colors.faint, textAlign = TextAlign.End,
                    )
                }
            }
            clockOf(line.at).takeIf { it.isNotEmpty() }?.let {
                Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint, textAlign = TextAlign.End)
            }
        }
        }
    }
}

/** Jowi's (or a human's) reply on a scheme-adaptive card with the slash as the mark; a null answer is guidance — the beige note idiom. */
@Composable
private fun ReplyBubble(line: JowiLine, vm: JowiViewModel, onFocusAsk: () -> Unit) {
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
                    // the server writes **bold** and links — render the inline
                    // markdown, never the asterisks (2026-09-17)
                    MarkdownText(line.text, color = colors.ink, fontSize = 15.sp, lineHeight = 22.sp)
                    line.match?.takeIf { it.sellers.isNotEmpty() }?.let { MatchCard(it, line, vm) }
                    // the chat-born list opens natively; else the step after a technical answer
                    val listId = line.listId
                    if (listId != null && line.match == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { vm.openList = listId }.padding(vertical = 4.dp),
                        ) {
                            Icon(WorkisIcons.Checklist, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.open_list), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                        }
                    } else line.next?.takeIf { !it.text.isNullOrEmpty() }?.let { NextCard(it, line, vm) }
                    line.href?.let { href ->
                        Text(
                            "↗ " + stringResource(R.string.jowi_open_link),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.blue,
                            modifier = Modifier.clickable { openInApp(context, absoluteWorkisUrl(href)) },
                        )
                    }
                    line.options?.takeIf { it.isNotEmpty() }?.let { opts ->
                        JowiOptions(opts, line.optionPicked, enabled = vm.lines.none { it.pending }) { o ->
                            if (!vm.pick(line, o)) onFocusAsk()
                        }
                    }
                    // the stamp: verified-by-expert vs generative — text, never colour alone.
                    // A "which one do you mean?" turn has no provenance to stamp.
                    val label = line.label
                    // the competitor-price wall says itself: a lock and the sentence,
                    // no provenance line and no thumbs (2026-09-18)
                    if (line.source == "wall") {
                        Text(
                            "🔒 " + stringResource(R.string.ask_answer_source_wall),
                            fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint,
                        )
                    } else if (line.source == "clarify" || line.options != null) Unit
                    else if (!label.isNullOrEmpty()) {
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
                    if (line.kb != null && line.source != "wall") Thumbs(line, vm)
                    clockOf(line.at).takeIf { it.isNotEmpty() }?.let {
                        Text(it, fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint)
                    }
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

/**
 * The two-reading card — the asker decides which text becomes the record.
 * Each choice IS its text (2026-09-19): a small capsule label on the right,
 * the sentence that would go out under it on the left; tapping either sends
 * that text. No pair of coloured buttons — the screen's one tinted action is
 * the send button. The third way, "Düzenlemeye dön", hands the typed text back
 * to the field so the writer can fix their own slip.
 */
@Composable
private fun ClarifyCard(typed: String, reading: String, vm: JowiViewModel, onBackToEdit: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.clarify_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp, color = Beige)
        // Jowi's reading: the "/" capsule in kiremit text, the sentence in ink
        Column(Modifier.fillMaxWidth().clickable { vm.chooseReading() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ClarifyCapsule(stringResource(R.string.clarify_jowi_suggests), Kiremit500, slash = true)
            }
            Text(reading, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink)
        }
        // the writer's own words: keyboard capsule, the sentence in light beige
        Column(Modifier.fillMaxWidth().clickable { vm.chooseOwn() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ClarifyCapsule(stringResource(R.string.clarify_your_words), Beige, icon = WorkisIcons.Keyboard)
            }
            Text(typed, fontSize = 15.sp, lineHeight = 21.sp, color = Beige)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Beige.copy(alpha = 0.25f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.clickable { onBackToEdit(vm.backToEdit() ?: typed) }) {
                ClarifyCapsule(stringResource(R.string.clarify_back_to_edit), Beige, icon = WorkisIcons.Pencil)
            }
        }
    }
}

/** A small capsule label on the beige ground: thin light-beige edge, never a filled colour ("selected"). */
@Composable
private fun ClarifyCapsule(label: String, tint: androidx.compose.ui.graphics.Color, slash: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.border(1.dp, Beige.copy(alpha = 0.45f), CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        if (slash) {
            Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tint)
            Spacer(Modifier.width(5.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = tint)
    }
}

/** The step after a technical answer (2026-09-15): the server's line + one action. In the pool → "N satıcıda M ürün"; a gap → the demand is recorded. */
@Composable
private fun NextCard(n: AskNext, line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val busy = vm.lines.any { it.pending }
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp).background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (n.kind == "gap") WorkisIcons.QuestionCircle else WorkisIcons.ShippingBox, contentDescription = null, tint = colors.faint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(n.item.orEmpty().uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint, maxLines = 1)
        }
        Text(n.text.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = !busy) { vm.collectQuotes(line) },
        ) {
            Icon(WorkisIcons.Sparkle, contentDescription = null, tint = if (busy) colors.faint else colors.accentText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.collect_quotes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (busy) colors.faint else colors.accentText)
        }
    }
}

/** Purchase request read by Jowi: the parsed line, then the sellers with their offer count — price-blind here. */
@Composable
private fun MatchCard(m: AskMatch, line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // no exact match is said in words, in the beige idiom — never "N matches"
        if (m.approximate) {
            Text(
                stringResource(R.string.match_approx_header),
                fontSize = 13.sp, lineHeight = 18.sp, color = Beige,
                modifier = Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        } else {
            Text(stringResource(R.string.match_header).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint)
        }
        Text(m.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink)
        m.sellers.take(5).forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = listOfNotNull(s.name, s.city).joinToString(" · ") },
                ) {
                    // the row draws the SHORT name; the long trading title cut off
                    Text(s.rowName.ifEmpty { "?" }, fontSize = 14.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    s.city?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, fontSize = 12.sp, color = colors.faint)
                    }
                    Spacer(Modifier.weight(1f))
                    // a catalogue row is a PRODUCT; a quote is what the seller sends back
                    Text("${s.offers?.size ?: 0} " + stringResource(R.string.match_offers), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted)
                }
                // the products ride a shelf that runs to the bubble's edge, so the
                // next card peeks and the scroll is obvious; no prices here
                s.offers?.takeIf { it.isNotEmpty() }?.let { offers ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        offers.forEach { o ->
                            Column(
                                Modifier
                                    .size(width = 168.dp, height = 92.dp)
                                    .background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                            ) {
                                Text(o.name.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                o.sku?.takeIf { it.isNotEmpty() }?.let {
                                    Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink.copy(alpha = 0.08f)))
            }
        }
        // the card has its own door: the same sentence again with createList,
        // but the answer lands HERE — no second pair is drawn
        val listId = line.listId
        when {
            listId != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { vm.openList = listId }.padding(vertical = 4.dp),
            ) {
                Icon(WorkisIcons.Checklist, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.open_list), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
            }
            line.collecting -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kiremit500)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.collect_quotes), fontSize = 13.sp, color = colors.faint)
            }
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { vm.collectQuotesOnCard(line) }.padding(vertical = 4.dp),
            ) {
                Icon(WorkisIcons.Sparkle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.collect_quotes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
            }
        }
    }
}
