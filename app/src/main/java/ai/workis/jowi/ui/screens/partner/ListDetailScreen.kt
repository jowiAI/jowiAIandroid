package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.BindBody
import ai.workis.jowi.data.NoteBody
import ai.workis.jowi.data.PLAssignment
import ai.workis.jowi.data.PLCandidate
import ai.workis.jowi.data.PLItem
import ai.workis.jowi.data.PLProposal
import ai.workis.jowi.data.PLQuoteTerm
import ai.workis.jowi.data.PLSearchResult
import ai.workis.jowi.data.PLSendRow
import ai.workis.jowi.data.PLSuggestion
import ai.workis.jowi.data.PLVariant
import ai.workis.jowi.data.PLVariantList
import ai.workis.jowi.data.PurchaseListDetail
import ai.workis.jowi.data.RequestQuotesBody
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.htmlToPlain
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.theme.WorkisIcons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ListDetailViewModel : ViewModel() {
    var detail by mutableStateOf<PurchaseListDetail?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var noteTick by mutableStateOf(false)
        private set
    private var tickJob: Job? = null
    private var id: String? = null
    private val lang get() = Graph.language.value

    /** `canWithdraw` as last read from `GET lists/` (null = not read yet) — the detail's `list`
     *  does not carry it (lists_seat.py:1174 vs the detail serializer). */
    var withdrawable by mutableStateOf<Boolean?>(null)
        private set
    var withdrawing by mutableStateOf(false)
        private set

    fun bind(listId: String) { if (id != listId) { id = listId; detail = null; withdrawable = null; load(); readWithdrawable() } }

    /** This list's row from `GET lists/` (absent = archived, so nothing to withdraw). */
    fun readWithdrawable() {
        val listId = id ?: return
        viewModelScope.launch {
            val (r, _) = partnerCall(lang) { Graph.api.lists() }
            r?.lists?.let { rows -> withdrawable = rows.firstOrNull { it.id == listId }?.canWithdraw == true }
        }
    }

    /** `POST lists/{id}/withdraw/` → the sellers are told, the list is `withdrawn` and archived;
     *  a 409 shows the server's sentence. */
    fun withdraw() {
        val listId = id ?: return
        if (withdrawing) return
        withdrawing = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listWithdraw(listId) }
            withdrawing = false
            if (r == null || !r.success) note = r?.error ?: r?.message ?: err
            load(); readWithdrawable()
        }
    }

    fun load() {
        val listId = id ?: return
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listDetail(listId) }
            if (r != null) { detail = r; error = null } else error = err
        }
    }

    private fun act(after: Boolean = true, call: suspend () -> Pair<Boolean, String?>) {
        busy = true
        viewModelScope.launch {
            val (ok, msg) = try { call() } catch (e: Exception) { false to (partnerCall<Unit>(lang) { throw e }.second) }
            busy = false
            if (!ok || msg != null) note = msg
            if (after) load()
        }
    }

    /** Re-read and hand the fresh detail back (a sheet reads its own row from it). */
    suspend fun reload(): PurchaseListDetail? {
        val listId = id ?: return null
        val (r, err) = partnerCall(lang) { Graph.api.listDetail(listId) }
        if (r != null) { detail = r; error = null } else error = err
        return r
    }

    fun deleteItem(itemId: String) = act { Graph.api.listItemDelete(itemId).let { it.success to (it.message ?: it.error) } }

    /**
     * A suggestion joins as a product row `{cpid, sellerId}` (2026-10-02). "Already on the
     * list." (400) → the note; `needs_variant` → that row opens on its variant list at once.
     */
    fun addSuggestion(s: PLSuggestion, onNeedsVariant: (PLItem, String) -> Unit) {
        addProductRow(s.cpid ?: return, s.sellerId, onNeedsVariant = onNeedsVariant)
    }

    /** A line of the add-item search (2026-10-05): `{cpid, sellerId, q}` + `sku` only when the
     *  line is ONE variant of a multi-variant family (the per-row search's rule). */
    fun addSearchResult(r: PLSearchResult, q: String, onNeedsVariant: (PLItem, String) -> Unit) {
        val sku = if (r.resolved == true && (r.nVariants ?: 0) > 1) r.sku else null
        addProductRow(r.cpid ?: return, r.sellerId, sku, q, onNeedsVariant)
    }

    private fun addProductRow(cpid: String, sellerId: String?, sku: String? = null, q: String? = null, onNeedsVariant: (PLItem, String) -> Unit) {
        val listId = id ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val body = jsonBody(
                "cpid" to cpid, "sellerId" to sellerId?.let { it.toIntOrNull() ?: it },
                "sku" to sku?.takeIf { it.isNotEmpty() }, "q" to q?.takeIf { it.isNotEmpty() },
            )
            val (r, err) = partnerCall(lang) { Graph.api.listAddProduct(listId, body) }
            busy = false
            if (r?.success != true) { note = r?.error ?: r?.message ?: err; load(); return@launch }
            val fresh = reload()
            if (r.needsVariant == true) fresh?.items?.firstOrNull { it.id == r.itemId }?.let { onNeedsVariant(it, cpid) }
        }
    }
    fun addItem(name: String, qty: Double?, unit: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listAddItem(id!!, jsonBody("name" to name, "qty" to qty, "unit" to unit.ifBlank { null })) }
            val ok = r?.success == true
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) load()
        }
    }
    /** `{cpid}` selects, `{cpid: ""}` unselects, `+ sku` chooses the variant, a search pick adds `sellerId` + `q`. → (ok, the server's sentence) */
    suspend fun bindItem(itemId: String, cpid: String?, sku: String? = null, sellerId: String? = null, q: String? = null): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang) {
            Graph.api.listItemBind(itemId, BindBody(cpid.orEmpty(), sku?.takeIf { it.isNotEmpty() }, sellerId?.takeIf { it.isNotEmpty() }, q?.takeIf { it.isNotEmpty() }))
        }
        return (r?.success == true) to (r?.error ?: r?.message ?: err)
    }
    suspend fun search(itemId: String, q: String): List<PLSearchResult> =
        partnerCall(lang) { Graph.api.listItemSearch(itemId, q) }.first?.results.orEmpty()
    /** `GET lists/search/?q=` — the catalogue search for a row not created yet. */
    suspend fun searchNew(q: String): List<PLSearchResult> =
        partnerCall(lang) { Graph.api.listsSearch(q) }.first?.results.orEmpty()
    suspend fun variants(itemId: String, cpid: String): Pair<PLVariantList?, String?> =
        partnerCall(lang) { Graph.api.listItemVariants(itemId, cpid) }

    /** Note + letter save on focus exit; the letter goes with <br> line breaks. */
    fun saveNote(noteText: String, letter: String) {
        val listId = id ?: return
        viewModelScope.launch {
            val l = letter.trim()
            val (r, err) = partnerCall(lang) { Graph.api.listNote(listId, NoteBody(noteText, if (l.isEmpty()) "" else l.replace("\n", "<br>"))) }
            if (r?.success == true) {
                tickJob?.cancel(); noteTick = true
                tickJob = launch { delay(1400); noteTick = false }
            } else note = r?.error ?: r?.message ?: err
        }
    }
    /**
     * The first matched row the request waits for: not `complete`, with candidates (a
     * free-text row never blocks). A `request-quotes/` 400 is the variant gate only when
     * such a row exists — the same 400 also means "nothing covers the list" (2026-10-03).
     */
    fun firstOpenRow(): PLItem? = detail?.items.orEmpty().firstOrNull { it.complete == false && !it.candidates.isNullOrEmpty() }

    fun requestQuotes(excluded: List<String>, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listRequestQuotes(id!!, RequestQuotesBody(excluded)) }
            val ok = r != null && r.success != false
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) { note = r?.message; load() }
        }
    }
    fun chase() = act { Graph.api.listChase(id!!).let { true to (it.message ?: it.error) } }
    /** `skips` = the unticked items, sent as `selections = {itemId: {skip: true}}`; the rest get the server's proposal. */
    fun award(skips: Set<String>, onAwarded: () -> Unit) = act {
        val sel = JsonObject(skips.associateWith { JsonObject(mapOf("skip" to JsonPrimitive(true))) })
        Graph.api.listAwardSelections(id!!, if (sel.isEmpty()) JsonObject(emptyMap()) else jsonBody("selections" to sel)).let {
            if (!it.orderIds.isNullOrEmpty()) onAwarded()
            (it.success != false) to (it.message ?: it.error)
        }
    }
    fun rfqPdfUrl(sellerId: String?): String = WORKIS_BASE_URL + "workis/lists/$id/rfq-pdf/" + (sellerId?.let { "?seller=$it" } ?: "")
}

private data class SendGroup(val key: String, val rows: List<PLSendRow>)

private fun sendGroups(d: PurchaseListDetail): List<SendGroup> {
    val order = mutableListOf<String>()
    val map = mutableMapOf<String, MutableList<PLSendRow>>()
    for (r in d.sendPreview.orEmpty()) {
        val k = r.g.orEmpty()
        if (k !in map) { order += k; map[k] = mutableListOf() }
        map[k]!! += r
    }
    // sellers first, the uncovered group last (as on the web)
    return order.sortedBy { if (it == "~uncovered") 1 else 0 }.map { SendGroup(it, map[it].orEmpty()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(listId: String, onBack: () -> Unit, onThread: (String) -> Unit = {}, canWithdraw: Boolean = false) {
    val vm: ListDetailViewModel = viewModel(key = "list-$listId")
    val colors = WorkisTheme.colors
    LaunchedEffect(listId) { vm.bind(listId) }
    val d = vm.detail
    // Jowi's page + scope: "bu listede kaç kalem var?" is answered from THIS list (2026-09-19)
    LaunchedEffect(listId, d?.list?.title, d?.isDraft) { ai.workis.jowi.data.ScreenContext.openList(listId, d?.list?.title, d?.isDraft == true) }
    var segment by rememberSaveable { mutableStateOf(0) }
    var pickItem by remember { mutableStateOf<PLItem?>(null) }
    // open the pick sheet straight on this candidate's variant list (`needs_variant`)
    var pickVariantCpid by remember { mutableStateOf<String?>(null) }
    // the row "Teklif iste" was refused for — opened once the send sheet is gone
    var pendingPick by remember { mutableStateOf<PLItem?>(null) }
    // an unsent note / letter in a field holds the changes/ re-read back (the pill waits)
    var typing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var showAward by remember { mutableStateOf(false) }
    // items the buyer unticked before the award (2026-10-01); every other item is ordered as proposed
    var awardSkips by remember { mutableStateOf(setOf<String>()) }
    var rfqPdf by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(d?.isWorking) { while (vm.detail?.isWorking == true) { delay(4_000); vm.load() } }
    LaunchedEffect(showSend, pendingPick) {
        val p = pendingPick
        if (!showSend && p != null) { delay(350); pendingPick = null; pickVariantCpid = null; pickItem = p }
    }

    ConsoleSub(
        title = d?.list?.title ?: "",
        onBack = onBack,
        trailing = {
            // one tinted action: request quotes (draft + stage ready + items)
            if (d != null && d.isDraft && d.isReady && !d.items.isNullOrEmpty()) {
                Button(
                    onClick = { showSend = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.height(36.dp),
                ) { Text(stringResource(R.string.request_quotes), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChangesWatch("lists", typing = typing) { vm.load() } // changes/ (2026-10-03)
            when {
                d != null -> {
                    Header(d)
                    // a request out for quotes may be taken back (2026-10-04) — two presses, no
                    // system dialog; the lists row's flag until this screen's own read answers
                    if (vm.withdrawable ?: canWithdraw) WithdrawButton(busy = vm.withdrawing, onConfirm = vm::withdraw)
                    NoteBand(vm.note)
                    if (d.isWorking) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.faint)
                        Spacer(Modifier.width(10.dp))
                        Text(listStageWord(d.list?.stage) ?: stringResource(R.string.processing), fontSize = 13.sp, color = colors.muted)
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            stringResource(R.string.seg_items) + " · ${d.items?.size ?: 0}",
                            stringResource(R.string.seg_rfq) + " · ${d.proposal?.nQuotes ?: 0}",
                        ).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = segment == i, onClick = { segment = i },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.surface, activeContentColor = colors.ink, inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted),
                            ) { Text(label, fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                    if (segment == 0) {
                        ItemsSegment(
                            d, vm,
                            onPick = { pickVariantCpid = null; pickItem = it },
                            onAdd = { showAdd = true },
                            onNeedsVariant = { row, cpid -> pickVariantCpid = cpid; pickItem = row },
                            onTyping = { typing = it },
                        )
                        ChatInvitesSection(d, onThread)
                    }
                    else RfqSegment(d, vm, awardSkips, onToggleSkip = { awardSkips = if (it in awardSkips) awardSkips - it else awardSkips + it }, onRfqPdf = { rfqPdf = vm.rfqPdfUrl(it) }, onAward = { showAward = true })
                }
                vm.error != null -> Text(vm.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    pickItem?.let { item ->
        key(item.id) { CandidateSheet(item, d?.isDraft == true, vm, openVariantsFor = pickVariantCpid, prices = d?.showsPrices ?: true) { pickItem = null; pickVariantCpid = null } }
    }
    if (showAdd) AddItemSheet(vm, prices = d?.showsPrices ?: true, onPick = { r, q ->
        // a catalogue line: the sheet is gone; `needs_variant` opens that row's variant list
        showAdd = false
        vm.addSearchResult(r, q) { row, cpid -> pickVariantCpid = cpid; pickItem = row }
    }) { showAdd = false }
    if (showSend && d != null) SendPreviewSheet(d, vm, onRefused = { msg, row ->
        // the gate's sentence names the rows (2026-10-03): it becomes the screen note and
        // the first open row opens once the send sheet is gone
        vm.note = msg; segment = 0; pendingPick = row; showSend = false
    }) { showSend = false }
    rfqPdf?.let { url -> PdfViewerDialog(stringResource(R.string.rfq_pdf), url = url, fileName = "talep-formu.pdf") { rfqPdf = null } }
    if (showAward) {
        AlertDialog(
            onDismissRequest = { showAward = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.award_action), color = colors.ink) },
            text = { Text(stringResource(R.string.award_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = { TextButton(onClick = { showAward = false; vm.award(awardSkips.filter { s -> d?.proposal?.assignments.orEmpty().any { it.itemId == s } }.toSet()) { awardSkips = emptySet() } }) { Text(stringResource(R.string.award_action), color = colors.accentText, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showAward = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

@Composable
private fun Header(d: PurchaseListDetail) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(listStatusText(d.list?.status), if (d.isQuoting) colors.accentText else colors.muted)
            if (d.list?.demo == true) StatusPill(stringResource(R.string.demo_tag))
            if (d.list?.held != null) StatusPill(stringResource(R.string.held_tag), Beige)
            Spacer(Modifier.weight(1f))
            d.list?.createdAt?.let { Text(it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
        }
        val project = d.projects?.firstOrNull { it.id == d.list?.projectId }?.name ?: d.projects?.firstOrNull { it.isDefault == true }?.name
        project?.let { Text("📁 " + stringResource(R.string.project_label) + ": $it", fontSize = 12.sp, color = colors.muted) }
    }
}

// ---------- Liste ----------

/** One row per invited seller — the buyer's door onto that seller's table. */
@Composable
private fun ChatInvitesSection(d: PurchaseListDetail, onThread: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val invites = d.chatInvites.orEmpty().filter { !it.id.isNullOrEmpty() }
    if (invites.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    FormSection(stringResource(R.string.chat_invites_title).uppercase()) {
        invites.forEachIndexed { i, inv ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { inv.id?.let(onThread) }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(WorkisIcons.Bubble, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(10.dp))
                Text(inv.lbl?.short ?: inv.lbl?.long.orEmpty(), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                (inv.unread ?: 0).takeIf { it > 0 }?.let {
                    StatusPill("$it " + stringResource(R.string.order_unread), Kiremit500)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
            }
            if (i < invites.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
        }
    }
}

@Composable
private fun ItemsSegment(
    d: PurchaseListDetail, vm: ListDetailViewModel, onPick: (PLItem) -> Unit, onAdd: () -> Unit,
    onNeedsVariant: (PLItem, String) -> Unit, onTyping: (Boolean) -> Unit,
) {
    val colors = WorkisTheme.colors
    ProvenanceCard(d)
    JowiNoteCard(d, onPick)
    SurfaceCard {
        d.items.orEmpty().forEachIndexed { i, item ->
            ItemRow(item, draft = d.isDraft, prices = d.showsPrices, onClick = { onPick(item) }, onDelete = { item.id?.let { vm.deleteItem(it) } })
            if (i < d.items.orEmpty().lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        if (d.isDraft) {
            Text("+ " + stringResource(R.string.add_item), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onAdd).padding(vertical = 6.dp))
        }
    }
    val sug = d.suggestions.orEmpty()
    if (sug.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(stringResource(R.string.suggestions_title).uppercase())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sug.forEach { s -> SuggestionCard(s, d.isDraft, d.showsPrices) { vm.addSuggestion(s, onNeedsVariant) } }
            }
            Text(stringResource(R.string.suggestions_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    val reqs = d.generalRequirements.orEmpty()
    if (reqs.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(stringResource(R.string.requirements_title).uppercase())
            SurfaceCard { reqs.forEach { Text("• $it", fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) } }
        }
    }
    if (d.isDraft) NoteLetterSection(d, vm, onTyping)
}

/**
 * How the list was built (2026-10-06, `list.provenance`): collapsed, above the rows — the
 * buyer's sentence, Jowi's reading of the job, the problem, then the card's questions in
 * order. Read-only history: the list is the truth after the build; no price, no seller.
 */
@Composable
private fun ProvenanceCard(d: PurchaseListDetail) {
    val colors = WorkisTheme.colors
    val p = d.list?.provenance ?: return
    var open by rememberSaveable(d.list.id) { mutableStateOf(false) }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.list_provenance_title), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f))
        }
        if (open) {
            if (p.sentence.isNotEmpty()) Text("“${p.sentence}”", fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
            if (p.job.isNotEmpty()) Text(p.job, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
            if (p.problem.isNotEmpty()) Text(stringResource(R.string.need_problem) + ": " + p.problem, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
            p.qa.forEach { r ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // a note answered no question; an older list kept only the answer (question "")
                    if (!r.note && r.question.isNotEmpty()) Text(r.question, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
                    when {
                        r.note -> Text(stringResource(R.string.provenance_note) + ": " + r.answer, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
                        r.skipped -> Text(stringResource(R.string.provenance_skipped), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = colors.faint)
                        else -> Text(r.answer, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
                    }
                }
            }
        }
    }
}

/**
 * Jowi's note (2026-10-03; on every built list with ≥ 2 rows since 2026-10-09, not only a
 * need-built one — drawn wherever present): the salesperson's re-read before the buyer sees the list — the
 * summary, then the findings with their kind in words (incompatible / problem in the warning
 * colour, the word always); a finding naming a row (1-based) opens that row as a tap on it
 * does. No price, no seller in it.
 */
@Composable
private fun JowiNoteCard(d: PurchaseListDetail, onPick: (PLItem) -> Unit) {
    val colors = WorkisTheme.colors
    val note = d.list?.jowiNote ?: return
    val summary = note.summary?.trim().orEmpty()
    val findings = note.findings.orEmpty().filter { !it.text.isNullOrBlank() }
    if (summary.isEmpty() && findings.isEmpty()) return
    val items = d.items.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.jowi_note_title).uppercase())
        SurfaceCard {
            if (summary.isNotEmpty()) Text(summary, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
            findings.forEach { f ->
                val n = f.row?.toInt()
                val row = n?.let { k -> items.firstOrNull { it.n == k } ?: items.getOrNull(k - 1) }
                val warn = f.kind == "problem" || f.kind == "incompatible"
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = row != null) { row?.let(onPick) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        noteKindWord(f.kind), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = if (warn) Beige else colors.muted,
                        modifier = Modifier.background(if (warn) BeigeBg else colors.ink.copy(alpha = 0.07f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (n != null) Text("$n", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                        Text(f.text.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
                    }
                    if (row != null) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun noteKindWord(kind: String?): String = stringResource(
    when (kind) {
        "missing" -> R.string.note_kind_missing
        "incompatible" -> R.string.note_kind_incompatible
        "quantity" -> R.string.note_kind_quantity
        "standard" -> R.string.note_kind_standard
        "duplicate" -> R.string.note_kind_duplicate
        "problem" -> R.string.note_kind_problem
        else -> R.string.note_kind_note
    },
)

/** `prices` false = a buyer seat (`list.prices`): no catalogue price, range or placeholder. */
@Composable
private fun ItemRow(item: PLItem, draft: Boolean, prices: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text("${item.n ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Same grammar as the seller's card (2026-09-19): the PRODUCT leads once
            // there is one — the variant I would be buying — and my own words follow
            // as "İstenen: …". With the generic name on top the row read as a family.
            val sel = item.selected
            Row(verticalAlignment = Alignment.Top) {
                // on a draft the product (family) leads and the chosen VARIANT's name sits
                // under it once one stands for it (`resolved`, 2026-10-02)
                Text((if (draft) sel?.name?.takeIf { it.isNotEmpty() } else sel?.displayName) ?: item.name ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink, maxLines = 3, modifier = Modifier.weight(1f))
                if (item.qty != null || !item.unit.isNullOrEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(WorkisMoney.qty(item.qty) + " " + item.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                }
            }
            if (draft && sel != null && sel.resolved == true) {
                sel.variantName?.takeIf { it.isNotEmpty() && it != sel.name }?.let {
                    Text(it, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            AskedLine(if (sel != null) item.name else null, item.requirements)
            if (draft) {
                DraftRowState(item, prices)
            } else if (sel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(if (item.matchState == "bound") SuccessGreen else colors.faint, CircleShape))
                    sel.sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                    Spacer(Modifier.weight(1f))
                    ComplianceChip(sel.compliance)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (sel.verified == false) "✋ " else "") + sel.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                    Spacer(Modifier.weight(1f))
                    VariantPrice(sel, prices)
                }
            } else {
                // nobody carries it: sourcing is the platform's backstage work — never "no match"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(WorkisIcons.TextLeft, contentDescription = null, tint = colors.faint, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.row_free_text), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
                }
            }
            if (draft) Text(stringResource(R.string.delete_item), fontSize = 11.sp, color = colors.danger, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

/**
 * A DRAFT row's words, from the server's flags only (Django note 2026-10-03): `complete` →
 * tick + price (or "fiyat teklifle gelir"; neither for a buyer seat — `prices` false);
 * selected but not complete → "varyant seçin"; candidates, none selected → "ürünü seçin";
 * no candidates → "Workis tedarikçi bulur". Icon + words — never the colour alone.
 */
@Composable
private fun DraftRowState(item: PLItem, prices: Boolean) {
    val colors = WorkisTheme.colors
    if (item.candidates.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(WorkisIcons.TextLeft, contentDescription = null, tint = colors.faint, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.row_free_text), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        }
        return
    }
    val sel = item.selected
    val complete = item.complete == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            complete -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                val price = sel?.price
                // a buyer seat: the first number is a quote — no price, no placeholder
                if (!prices) Unit
                else if (price != null) Text(WorkisMoney.text(price, sel?.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
                else Text(stringResource(R.string.row_price_in_quote), fontSize = 12.sp, color = colors.muted)
            }
            item.boundCpid != null -> {
                Icon(WorkisIcons.Ruler, contentDescription = null, tint = Beige, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.row_choose_variant), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Beige)
            }
            else -> {
                Icon(WorkisIcons.HandTap, contentDescription = null, tint = Beige, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.row_choose_product), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Beige)
            }
        }
        Spacer(Modifier.weight(1f))
        if (complete) sel?.sku?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        ComplianceChip(sel?.compliance)
    }
    sel?.let { Text((if (it.verified == false) "✋ " else "") + it.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
}

/** "İstenen: <generic name> · <demands>" — the buyer's own words, faint, under the product. */
@Composable
private fun AskedLine(generic: String?, requirements: List<String>?) {
    val asked = (listOf(generic) + requirements.orEmpty()).filterNotNull().filter { it.isNotEmpty() }
    if (asked.isEmpty()) return
    Text(
        stringResource(R.string.asked_label) + ": " + asked.joinToString(" · "),
        fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A candidate's price — or, while the asked size has not resolved to ONE variant, the
 * reason there is none. An unresolved family must never show a price: the first
 * variant's price stood in for the asked one (3/4" shown for 1", 2026-09-18).
 */
@Composable
private fun VariantPrice(c: PLCandidate, prices: Boolean) {
    val colors = WorkisTheme.colors
    when {
        prices && c.price != null -> Text(WorkisMoney.text(c.price, c.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
        c.variant == "none" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(WorkisIcons.QuestionCircle, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.variant_none), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Beige)
        }
        c.variant == "ambiguous" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
            Text(
                stringResource(R.string.variant_ambiguous) + " · ${c.nVariants ?: c.variants?.size ?: 0} " + stringResource(R.string.variants_word),
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Beige,
            )
        }
        // prices come with the quotes — say nothing rather than a dash that reads as zero
        else -> Unit
    }
}

@Composable
private fun SuggestionCard(s: PLSuggestion, draft: Boolean, prices: Boolean, onAdd: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.width(210.dp).background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(s.name.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink, maxLines = 2)
        Text(s.lbl?.short ?: s.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        if (prices) Text(WorkisMoney.text(s.price, s.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        s.because?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.faint, maxLines = 2) }
        if (draft) Text(stringResource(R.string.add_to_list), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onAdd).padding(top = 2.dp))
    }
}

@Composable
private fun NoteLetterSection(d: PurchaseListDetail, vm: ListDetailViewModel, onTyping: (Boolean) -> Unit) {
    val colors = WorkisTheme.colors
    var noteText by rememberSaveable(d.list?.id) { mutableStateOf(d.rfqNote.orEmpty()) }
    var letter by rememberSaveable(d.list?.id) { mutableStateOf(htmlToPlain(d.rfqLetter.orEmpty())) }
    var dirty by remember { mutableStateOf(false) }
    // a field in focus = the reader is writing: changes/ holds its re-read (the pill waits)
    var noteFocused by remember { mutableStateOf(false) }
    var letterFocused by remember { mutableStateOf(false) }
    LaunchedEffect(noteFocused, letterFocused) { onTyping(noteFocused || letterFocused) }
    DisposableEffect(Unit) { onDispose { onTyping(false) } }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)
    fun commit(onFocus: (Boolean) -> Unit) = Modifier.onFocusChanged {
        onFocus(it.isFocused)
        if (!it.isFocused && dirty) { dirty = false; vm.saveNote(noteText, letter) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.note_title).uppercase())
            if (vm.noteTick) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp)) }
        }
        OutlinedTextField(value = noteText, onValueChange = { noteText = it; dirty = true }, placeholder = { Text(stringResource(R.string.note_placeholder), color = colors.faint, fontSize = 14.sp) }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth().then(commit { noteFocused = it }))
        OutlinedTextField(value = letter, onValueChange = { letter = it; dirty = true }, placeholder = { Text(stringResource(R.string.letter_title), color = colors.faint, fontSize = 14.sp) }, minLines = 3, maxLines = 12, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth().then(commit { letterFocused = it }))
        Text(stringResource(R.string.letter_default_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// ---------- Teklif süreci ----------

@Composable
private fun RfqSegment(d: PurchaseListDetail, vm: ListDetailViewModel, awardSkips: Set<String>, onToggleSkip: (String) -> Unit, onRfqPdf: (sellerId: String?) -> Unit, onAward: () -> Unit) {
    val colors = WorkisTheme.colors
    val groups = sendGroups(d)
    val invited = groups.count { it.key != "~uncovered" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.send_preview_title).uppercase())
        SurfaceCard {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$invited", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.ink)
                Text(stringResource(R.string.invited_word), fontSize = 13.sp, color = colors.muted)
                Text("·", color = colors.faint)
                Text("${d.proposal?.nQuotes ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.ink)
                Text(stringResource(R.string.quotes_in_word), fontSize = 13.sp, color = colors.muted)
            }
            val docs = d.rfqDocs.orEmpty()
            if (docs.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                docs.forEach { doc ->
                    Text(
                        "📄 " + (doc.number ?: stringResource(R.string.rfq_pdf)) + " · " + doc.lbl?.short.orEmpty(),
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.accentText,
                        modifier = Modifier.background(colors.ink.copy(alpha = 0.06f), CircleShape).clickable { onRfqPdf(doc.sellerId) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (!d.pendingSellers.isNullOrEmpty() && d.isQuoting) {
                Text("🔔 " + stringResource(R.string.chase_silent), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(enabled = !vm.busy) { vm.chase() })
            }
        }
        Text(stringResource(R.string.send_preview_sub), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
    groups.forEach { g -> SendGroupCard(g, d) }
    val vi = d.virtualInvites.orEmpty()
    if (vi.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.virtual_title).uppercase())
        SurfaceCard {
            vi.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✋ " + v.name.orEmpty(), fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(inviteStatusText(v.status), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                    v.total?.let { Spacer(Modifier.width(8.dp)); Text(WorkisMoney.text(it, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink) }
                }
            }
        }
        Text(stringResource(R.string.virtual_sub), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
    ProposalSection(d, vm, awardSkips, onToggleSkip, onAward)
    val orders = d.orders.orEmpty()
    if (orders.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.orders_title).uppercase())
        SurfaceCard {
            orders.forEach { o ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row { Text(o.poNumber ?: o.id.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f)); Text(WorkisMoney.text(o.total, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink) }
                    Text(listOfNotNull(o.lbl?.short, o.escrow, o.shipment?.let { "🚚 ${it.number.orEmpty()} ${it.status.orEmpty()}" }).joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                }
            }
        }
    }
}

@Composable
private fun SendGroupCard(g: SendGroup, d: PurchaseListDetail) {
    val colors = WorkisTheme.colors
    val uncovered = g.key == "~uncovered"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val head = if (uncovered) stringResource(R.string.uncovered_group) + " · ${g.rows.size}" else buildString {
            append(g.rows.firstOrNull()?.seller.orEmpty())
            if (g.rows.firstOrNull()?.channel == "email") append(" ✉︎")
            if (d.sendStatusMode == true) {
                append(" · " + inviteStatusText(g.rows.firstOrNull()?.status))
                if (g.rows.firstOrNull()?.chased == true) append(" · " + stringResource(R.string.chased_tag))
            }
        }
        Eyebrow(head.uppercase())
        SurfaceCard {
            g.rows.forEach { r -> SendRowLine(r, uncovered) }
        }
        if (uncovered) Text(stringResource(R.string.uncovered_sub), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * The PRODUCT leads (the variant this seller is asked for), the buyer's generic name
 * follows as "İstenen: …" — the same grammar as the list row and the seller's card.
 */
@Composable
private fun SendRowLine(r: PLSendRow, uncovered: Boolean) {
    val colors = WorkisTheme.colors
    val product = if (!uncovered) r.alts?.takeIf { it.isNotEmpty() } else null
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(product ?: r.item.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, color = colors.ink, maxLines = 3, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text(WorkisMoney.qty(r.qty) + " " + r.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
        }
        if (product != null) AskedLine(r.item, null)
    }
}

/**
 * The quote's term sheet for one seller block: the quote whose seller is the block's
 * (through the seller totals' label), or the only quote when there is one seller.
 */
private fun termSheet(d: PurchaseListDetail, p: PLProposal, seller: String, single: Boolean): List<PLQuoteTerm>? {
    val quotes = p.quotes?.takeIf { it.isNotEmpty() } ?: return null
    val sid = (p.sellerTotals ?: d.sellerTotals)?.firstOrNull { it.lbl?.short == seller }?.sellerId
    val sheet = quotes.firstOrNull { sid != null && it.seller == sid } ?: (if (single && quotes.size == 1) quotes[0] else null)
    return sheet?.terms
}

/**
 * The term sheet the buyer reads next to the price (2026-09-30), registry order: a term
 * settled in the yazışma wears the kiremit frame, a conflict the warning colour — each with
 * its words, never the colour alone.
 */
@Composable
private fun TermSheet(terms: List<PLQuoteTerm>) {
    val colors = WorkisTheme.colors
    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        terms.forEach { t ->
            val conflict = t.conflict == true
            val fromChat = t.isFromChat
            val frame = when {
                fromChat -> Modifier.border(1.dp, Kiremit500.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                conflict -> Modifier.border(1.dp, Beige.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                else -> Modifier
            }
            Row(frame, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((t.label ?: t.key.orEmpty()) + ":", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.padding(top = 2.dp))
                Text(
                    buildString {
                        append(t.text?.takeIf { it.isNotEmpty() } ?: "—")
                        if (conflict) append(" · ").append(stringResource(R.string.term_conflict))
                        else if (fromChat) append(" · ").append(stringResource(R.string.term_from_chat))
                    },
                    fontSize = 12.sp, lineHeight = 16.sp, color = if (conflict) Beige else colors.ink, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun ProposalSection(d: PurchaseListDetail, vm: ListDetailViewModel, awardSkips: Set<String>, onToggleSkip: (String) -> Unit, onAward: () -> Unit) {
    val colors = WorkisTheme.colors
    val p = d.proposal
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(if (d.list?.status == "ordered") R.string.awarded_title else R.string.proposal_title).uppercase())
        SurfaceCard {
            if (p != null && ((p.nQuotes ?: 0) > 0 || !p.assignments.isNullOrEmpty())) {
                val bySeller = p.assignments.orEmpty().groupBy { it.sellerName }.toSortedMap()
                bySeller.forEach { (seller, rows) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(seller.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, color = colors.accentText)
                        rows.forEach { a ->
                            // a tick per item while quoting (2026-10-01): unticked = left on the list, not ordered
                            val ticked = a.itemId?.let { it !in awardSkips } ?: true
                            AssignmentRow(a, tick = if (d.isQuoting && a.itemId != null) ticked else null, tickEnabled = !vm.busy) { a.itemId?.let(onToggleSkip) }
                        }
                        termSheet(d, p, seller, single = bySeller.size == 1)?.takeIf { it.isNotEmpty() }?.let { TermSheet(it) }
                    }
                }
                p.totalsByCurrency?.takeIf { it.isNotEmpty() }?.let { totals ->
                    Row { Text(stringResource(R.string.total_label), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f)); Text(totals.keys.sorted().joinToString(" · ") { WorkisMoney.text(totals[it], it) }, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink) }
                }
                p.uncovered?.takeIf { it.isNotEmpty() }?.let { u -> Text(stringResource(R.string.uncovered_group) + ": " + u.mapNotNull { it.name }.joinToString(", "), fontSize = 12.sp, color = colors.faint) }
                if (d.isQuoting) {
                    // at least one item must be ordered (400 otherwise)
                    val anyTicked = p.assignments.orEmpty().any { a -> a.itemId?.let { it !in awardSkips } ?: true }
                    val enabled = !vm.busy && anyTicked
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onAward).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accentText)
                        else Text("✓ " + stringResource(R.string.award_action), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) colors.accentText else colors.faint)
                    }
                }
            } else {
                Text(stringResource(R.string.proposal_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

/** `tick` null = no tick (not quoting); true/false = ordered at award or left on the list. */
@Composable
private fun AssignmentRow(a: PLAssignment, tick: Boolean? = null, tickEnabled: Boolean = true, onToggle: () -> Unit = {}) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        if (tick != null) {
            Box(
                Modifier.padding(top = 1.dp, end = 10.dp).size(20.dp)
                    .toggleable(value = tick, enabled = tickEnabled, role = Role.Checkbox, onValueChange = { onToggle() })
                    .semantics { contentDescription = a.name ?: a.itemName.orEmpty() },
                contentAlignment = Alignment.Center,
            ) {
                if (tick) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                else Box(Modifier.size(17.dp).border(1.5.dp, colors.faint, CircleShape))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // the quoted PRODUCT leads; the buyer's generic name follows as "İstenen: …"
            val product = a.name?.takeIf { it.isNotEmpty() && it != a.itemName }
            Text(product ?: a.itemName.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, color = colors.ink, maxLines = 3)
            if (product != null) AskedLine(a.itemName, null)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(WorkisMoney.text(a.amount, a.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            Text(WorkisMoney.qty(a.qty) + " " + a.unit.orEmpty() + " × " + WorkisMoney.text(a.unitPrice, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        }
    }
}

// ---------- sheets ----------

/**
 * Candidates with a tick on the bound one, the judge's rows under each; tap binds; search
 * other products. On a draft the selected candidate carries the variant door when the server
 * offers a choice (`canChooseVariant`); the variant list replaces the sheet's content (the
 * iOS sheet pushes it) and "Geri" returns to the candidates. `prices` = the list's flag —
 * false (a buyer seat): no catalogue price anywhere here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateSheet(item: PLItem, draft: Boolean, vm: ListDetailViewModel, openVariantsFor: String? = null, prices: Boolean = true, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PLSearchResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // the row as the server last answered — re-read after a family bind
    var row by remember { mutableStateOf(item) }
    // the candidate whose variants are shown: (cpid, family name)
    var variantsOf by remember {
        mutableStateOf(openVariantsFor?.let { cp -> cp to (item.candidates?.firstOrNull { it.cpid == cp }?.name ?: item.name.orEmpty()) })
    }
    val itemId = item.id ?: return
    fun close() { vm.load(); onDismiss() }
    /** After a bind: when the selected family still needs its variant (fresh row:
     *  `canChooseVariant`, not `resolved`) the variant list opens at once; else the sheet closes. */
    suspend fun afterBind(cpid: String?) {
        if (!draft || cpid.isNullOrEmpty()) { busy = false; close(); return }
        val fresh = vm.reload()?.items?.firstOrNull { it.id == itemId }
        val c = fresh?.candidates?.firstOrNull { it.cpid == cpid }
        busy = false
        if (fresh == null || c == null || c.canChooseVariant != true || c.resolved == true) { onDismiss(); return }
        row = fresh; results = emptyList(); variantsOf = cpid to c.name.orEmpty()
    }
    fun bind(cpid: String?) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            val (ok, msg) = vm.bindItem(itemId, cpid)
            if (ok) afterBind(cpid) else { busy = false; error = msg }
        }
    }
    /** A search line joins the row: `{cpid, sellerId, q}` + `sku` only when the line is ONE
     *  variant of a multi-variant family (Django note 2026-10-03). */
    fun pick(r: PLSearchResult) {
        val cpid = r.cpid ?: return
        if (busy) return
        busy = true; error = null
        val sku = if (r.resolved == true && (r.nVariants ?: 0) > 1) r.sku else null
        scope.launch {
            val (ok, msg) = vm.bindItem(itemId, cpid, sku = sku, sellerId = r.sellerId, q = query.trim())
            if (ok) afterBind(cpid) else { busy = false; error = msg }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val target = variantsOf
            if (target != null) {
                VariantPicker(itemId, target.first, target.second, vm, prices, onBack = { variantsOf = null }) { close() }
                Spacer(Modifier.height(30.dp))
                return@Column
            }
            Text(row.name.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            row.requirements.orEmpty().forEach { Text("• $it", fontSize = 12.sp, color = colors.muted) }
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            Eyebrow(stringResource(R.string.candidates_title).uppercase())
            val cands = row.candidates.orEmpty()
            if (cands.isEmpty()) Text(stringResource(R.string.row_free_text), fontSize = 13.sp, color = colors.faint)
            cands.forEach { c ->
                CandidateRow(c, bound = c.isBound == true || c.cpid == row.boundCpid, prices = prices) { c.cpid?.let { bind(it) } }
                // the selected candidate's variant door — the server says whether there is a
                // choice; draft lists only (a sku after the send is refused)
                val cp = c.cpid
                if (draft && cp != null && c.canChooseVariant == true && cp == row.selected?.cpid) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { variantsOf = cp to c.name.orEmpty() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(WorkisIcons.Ruler, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp))
                        Text(stringResource(if (c.resolved == true) R.string.variant_change else R.string.variant_choose), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
                    }
                }
            }
            if (row.boundCpid != null) Text(stringResource(R.string.unbind_action), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { bind(null) })
            Text(stringResource(R.string.candidates_sub), fontSize = 12.sp, color = colors.faint)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_products), color = colors.faint, fontSize = 14.sp) },
                shape = RoundedCornerShape(30.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { val q = query.trim(); if (q.length >= 2) scope.launch { results = vm.search(itemId, q) } }),
                modifier = Modifier.fillMaxWidth(),
            )
            results.forEach { r -> SearchLine(r, prices) { pick(r) } }
            if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.faint) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/**
 * A search line inside a row: ONE price only when one variant stands for it (`resolved`);
 * else "n varyant · from–to currency" — the first variant's price is somebody else's price.
 * A buyer seat (`prices` false) reads neither the price nor the range, only the count.
 */
@Composable
private fun SearchLine(r: PLSearchResult, prices: Boolean, onPick: () -> Unit) {
    val colors = WorkisTheme.colors
    val variantsWord = stringResource(R.string.variants_word)
    Column(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(r.name ?: r.sku.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
        if (r.resolved == true) r.variantName?.takeIf { it.isNotEmpty() && it != r.name }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.weight(1f))
            if (r.resolved == true) {
                r.price?.takeIf { prices }?.let { Text(WorkisMoney.text(it, r.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink) }
            } else {
                val range = buildString {
                    append("${r.nVariants ?: 0} ").append(variantsWord)
                    r.priceFrom?.takeIf { prices }?.let { from ->
                        val to = r.priceTo
                        if (to != null && to != from) append(" · ").append(WorkisMoney.text(from, null)).append("–").append(WorkisMoney.text(to, r.currency))
                        else append(" · ").append(WorkisMoney.text(from, r.currency))
                    }
                }
                Text(range, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted)
            }
        }
    }
}

/**
 * One candidate's variants (`GET lists/items/{id}/variants/?cpid=`); the standing one
 * (`chosen`) wears a solid kiremit border — selection, never a tinted fill. A tap posts
 * `bind {cpid, sku}` and the list re-reads. `prices` = the list's flag; the door answers its
 * own (`prices`), which wins — false: no price and no "fiyat teklifle gelir".
 */
@Composable
private fun VariantPicker(itemId: String, cpid: String, title: String, vm: ListDetailViewModel, prices: Boolean, onBack: () -> Unit, onChosen: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PLVariant>?>(null) }
    var chosen by remember { mutableStateOf("") }
    var serverPrices by remember { mutableStateOf<Boolean?>(null) }
    var busySku by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itemId, cpid) {
        val (list, err) = vm.variants(itemId, cpid)
        if (list != null) { rows = list.variants.orEmpty(); chosen = list.chosen.orEmpty(); serverPrices = list.prices } else error = err
    }
    Row(Modifier.clickable(onClick = onBack).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.back), fontSize = 14.sp, color = colors.accentText)
    }
    Text(stringResource(R.string.variant_choose), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
    if (title.isNotEmpty()) Eyebrow(title.uppercase())
    error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
    val list = rows
    when {
        list == null && error == null -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 22, breathing = true) }
        list != null && list.isEmpty() -> Text(stringResource(R.string.variants_empty), fontSize = 13.sp, color = colors.faint)
        list != null -> list.forEach { v ->
            val isChosen = chosen.isNotEmpty() && v.sku == chosen
            Row(
                Modifier.fillMaxWidth()
                    .border(2.dp, if (isChosen) Kiremit500 else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(enabled = busySku == null && !v.sku.isNullOrEmpty()) {
                        val sku = v.sku ?: return@clickable
                        busySku = sku; error = null
                        scope.launch {
                            val (ok, msg) = vm.bindItem(itemId, cpid, sku = sku)
                            busySku = null
                            if (ok) { chosen = sku; onChosen() } else error = msg
                        }
                    }
                    .semantics { selected = isChosen }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(v.name ?: v.sku.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                    v.sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
                }
                Spacer(Modifier.width(6.dp))
                when {
                    busySku != null && busySku == v.sku -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.faint)
                    !(serverPrices ?: prices) -> Unit
                    v.price != null -> Text(WorkisMoney.text(v.price, v.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                    else -> Text(stringResource(R.string.row_price_in_quote), fontSize = 12.sp, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(c: PLCandidate, bound: Boolean, prices: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f))
            if (bound) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (c.verified == false) "✋ " else "") + c.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
            c.sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
            Spacer(Modifier.weight(1f))
            ComplianceChip(c.compliance)
            VariantPrice(c, prices)
        }
        // several variants fit the asked size: name them, each with its own price
        c.variants?.takeIf { c.variant == "exact" && it.size > 1 }?.forEach { v ->
            Row(verticalAlignment = Alignment.Top) {
                Text(v.name ?: v.sku.orEmpty(), fontSize = 12.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                v.price?.takeIf { prices }?.let { Spacer(Modifier.width(6.dp)); Text(WorkisMoney.text(it, v.currency), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.ink) }
            }
        }
        c.compliance?.rows.orEmpty().forEach { v ->
            Column(Modifier.padding(top = 2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when (v.verdict) { "meets" -> "✓"; "fails" -> "✗"; else -> "?" }, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = when (v.verdict) { "meets" -> SuccessGreen; "fails" -> colors.danger; else -> colors.faint })
                    Text(v.req.orEmpty(), fontSize = 12.sp, color = colors.muted)
                }
                v.evidence?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.faint, modifier = Modifier.padding(start = 16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(vm: ListDetailViewModel, prices: Boolean, onPick: (PLSearchResult, String) -> Unit, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    var name by remember { mutableStateOf("") }
    // `GET lists/search/?q=` as the name is typed (2026-10-05): 300 ms, two characters at least
    var results by remember { mutableStateOf<List<PLSearchResult>>(emptyList()) }
    val typed = name.trim()
    LaunchedEffect(typed) {
        if (typed.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = vm.searchNew(typed)
    }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.add_item), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(stringResource(R.string.item_name), color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fc, modifier = Modifier.fillMaxWidth())
            if (results.isNotEmpty()) {
                Eyebrow(stringResource(R.string.add_item_from_catalogue).uppercase())
                results.forEach { r -> SearchLine(r, prices) { if (!busy && !vm.busy) onPick(r, typed) } }
                // the manual row stays the fallback below the catalogue lines
                Eyebrow(stringResource(R.string.add_item_manual).uppercase())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = qty, onValueChange = { qty = it }, placeholder = { Text(stringResource(R.string.qty_field), color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fc, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = unit, onValueChange = { unit = it }, placeholder = { Text(stringResource(R.string.unit_field), color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fc, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            Row { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) }; Spacer(Modifier.weight(1f))
                Button(onClick = { busy = true; vm.addItem(name.trim(), qty.replace(',', '.').toDoubleOrNull(), unit.trim()) { ok, msg -> busy = false; if (ok) onDismiss() else error = msg } }, enabled = name.isNotBlank() && !busy, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.height(44.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill) else Text(stringResource(R.string.add_item), fontWeight = FontWeight.SemiBold)
                } }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** "Teklif iste" — who receives what: seller sections with Skip/Send per seller, the uncovered group last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendPreviewSheet(d: PurchaseListDetail, vm: ListDetailViewModel, onRefused: (String?, PLItem) -> Unit, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    val groups = remember(d) { sendGroups(d) }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSend = groups.any { it.key != "~uncovered" && it.key !in excluded }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.send_preview_title), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
            Text(stringResource(R.string.send_preview_sub), fontSize = 13.sp, color = colors.muted)
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            groups.forEach { g ->
                val uncovered = g.key == "~uncovered"
                val off = g.key in excluded
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (uncovered) stringResource(R.string.uncovered_group) + " · ${g.rows.size}" else g.rows.firstOrNull()?.seller.orEmpty() + (if (g.rows.firstOrNull()?.channel == "email") " ✉︎" else ""),
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f),
                        )
                        if (!uncovered) Text(stringResource(if (off) R.string.send_action else R.string.exclude_seller), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { excluded = if (off) excluded - g.key else excluded + g.key })
                    }
                    Column(Modifier.alpha(if (off) 0.4f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.rows.forEach { r -> SendRowLine(r, uncovered) }
                    }
                    if (uncovered) Text(stringResource(R.string.uncovered_sub), fontSize = 12.sp, color = colors.faint)
                }
            }
            Row { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) }; Spacer(Modifier.weight(1f))
                Button(onClick = {
                    busy = true
                    vm.requestQuotes(excluded.toList()) { ok, msg ->
                        busy = false
                        // a refusal while a matched row is not complete = the variant gate (2026-10-03):
                        // the sentence goes to the list screen and that row opens there
                        val open = if (ok) null else vm.firstOpenRow()
                        when {
                            ok -> onDismiss()
                            open != null -> onRefused(msg, open)
                            else -> error = msg
                        }
                    }
                }, enabled = canSend && !busy, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.height(44.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill) else Text(stringResource(R.string.send_action), fontWeight = FontWeight.SemiBold)
                } }
            Spacer(Modifier.height(30.dp))
        }
    }
}
