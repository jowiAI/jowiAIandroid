package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
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
import ai.workis.jowi.data.PLSearchResult
import ai.workis.jowi.data.PLSendRow
import ai.workis.jowi.data.PLSuggestion
import ai.workis.jowi.data.PurchaseListDetail
import ai.workis.jowi.data.RequestQuotesBody
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.htmlToPlain
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    fun bind(listId: String) { if (id != listId) { id = listId; detail = null; load() } }

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

    fun deleteItem(itemId: String) = act { Graph.api.listItemDelete(itemId).let { it.success to (it.message ?: it.error) } }
    fun addSuggestion(cpid: String) = act { Graph.api.listAddItem(id!!, jsonBody("cpid" to cpid)).let { it.success to (it.message ?: it.error) } }
    fun addItem(name: String, qty: Double?, unit: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listAddItem(id!!, jsonBody("name" to name, "qty" to qty, "unit" to unit.ifBlank { null })) }
            val ok = r?.success == true
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) load()
        }
    }
    fun bindItem(itemId: String, cpid: String?, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listItemBind(itemId, BindBody(cpid.orEmpty())) }
            val ok = r?.success == true
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) load()
        }
    }
    suspend fun search(itemId: String, q: String): List<PLSearchResult> =
        partnerCall(lang) { Graph.api.listItemSearch(itemId, q) }.first?.results.orEmpty()

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
    fun requestQuotes(excluded: List<String>, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listRequestQuotes(id!!, RequestQuotesBody(excluded)) }
            val ok = r != null && r.success != false
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) { note = r?.message; load() }
        }
    }
    fun chase() = act { Graph.api.listChase(id!!).let { true to (it.message ?: it.error) } }
    fun award() = act { Graph.api.listAward(id!!).let { (it.success != false) to (it.message ?: it.error) } }
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
fun ListDetailScreen(listId: String, onBack: () -> Unit) {
    val vm: ListDetailViewModel = viewModel(key = "list-$listId")
    val colors = WorkisTheme.colors
    LaunchedEffect(listId) { vm.bind(listId) }
    val d = vm.detail
    var segment by rememberSaveable { mutableStateOf(0) }
    var pickItem by remember { mutableStateOf<PLItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var showAward by remember { mutableStateOf(false) }
    var rfqPdf by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(d?.isWorking) { while (vm.detail?.isWorking == true) { delay(4_000); vm.load() } }

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
            when {
                d != null -> {
                    Header(d)
                    NoteBand(vm.note)
                    if (d.isWorking) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.faint)
                        Spacer(Modifier.width(10.dp))
                        Text(listStageText(d.list?.stage) ?: stringResource(R.string.processing), fontSize = 13.sp, color = colors.muted)
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
                    if (segment == 0) ItemsSegment(d, vm, onPick = { pickItem = it }, onAdd = { showAdd = true })
                    else RfqSegment(d, vm, onRfqPdf = { rfqPdf = vm.rfqPdfUrl(it) }, onAward = { showAward = true })
                }
                vm.error != null -> Text(vm.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    pickItem?.let { item -> CandidateSheet(item, d?.isDraft == true, vm) { pickItem = null } }
    if (showAdd) AddItemSheet(vm) { showAdd = false }
    if (showSend && d != null) SendPreviewSheet(d, vm) { showSend = false }
    rfqPdf?.let { url -> PdfViewerDialog(stringResource(R.string.rfq_pdf), url = url, fileName = "talep-formu.pdf") { rfqPdf = null } }
    if (showAward) {
        AlertDialog(
            onDismissRequest = { showAward = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.award_action), color = colors.ink) },
            text = { Text(stringResource(R.string.award_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = { TextButton(onClick = { showAward = false; vm.award() }) { Text(stringResource(R.string.award_action), color = colors.accentText, fontWeight = FontWeight.SemiBold) } },
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

@Composable
private fun ItemsSegment(d: PurchaseListDetail, vm: ListDetailViewModel, onPick: (PLItem) -> Unit, onAdd: () -> Unit) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        d.items.orEmpty().forEachIndexed { i, item ->
            ItemRow(item, draft = d.isDraft, onClick = { onPick(item) }, onDelete = { item.id?.let { vm.deleteItem(it) } })
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
                sug.forEach { s -> SuggestionCard(s, d.isDraft) { s.cpid?.let { vm.addSuggestion(it) } } }
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
    if (d.isDraft) NoteLetterSection(d, vm)
}

@Composable
private fun ItemRow(item: PLItem, draft: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text("${item.n ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(item.name ?: "—", fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, modifier = Modifier.weight(1f))
                if (item.qty != null || !item.unit.isNullOrEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(WorkisMoney.qty(item.qty) + " " + item.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted)
                }
            }
            val sel = item.selected
            if (sel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(if (item.matchState == "bound") SuccessGreen else colors.faint, CircleShape))
                    Text(sel.name ?: sel.sku.orEmpty(), fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    ComplianceChip(sel.compliance)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (sel.verified == false) "✋ " else "") + sel.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                    Spacer(Modifier.weight(1f))
                    Text(WorkisMoney.text(sel.price, sel.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
                }
            } else {
                Text(stringResource(R.string.no_match), fontSize = 12.sp, color = colors.faint)
            }
            if (draft) Text(stringResource(R.string.delete_item), fontSize = 11.sp, color = colors.danger, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun SuggestionCard(s: PLSuggestion, draft: Boolean, onAdd: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.width(210.dp).background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(s.name.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink, maxLines = 2)
        Text(s.lbl?.short ?: s.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        Text(WorkisMoney.text(s.price, s.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        s.because?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.faint, maxLines = 2) }
        if (draft) Text(stringResource(R.string.add_to_list), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onAdd).padding(top = 2.dp))
    }
}

@Composable
private fun NoteLetterSection(d: PurchaseListDetail, vm: ListDetailViewModel) {
    val colors = WorkisTheme.colors
    var noteText by rememberSaveable(d.list?.id) { mutableStateOf(d.rfqNote.orEmpty()) }
    var letter by rememberSaveable(d.list?.id) { mutableStateOf(htmlToPlain(d.rfqLetter.orEmpty())) }
    var dirty by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)
    val commit = Modifier.onFocusChanged { if (!it.isFocused && dirty) { dirty = false; vm.saveNote(noteText, letter) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.note_title).uppercase())
            if (vm.noteTick) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp)) }
        }
        OutlinedTextField(value = noteText, onValueChange = { noteText = it; dirty = true }, placeholder = { Text(stringResource(R.string.note_placeholder), color = colors.faint, fontSize = 14.sp) }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth().then(commit))
        OutlinedTextField(value = letter, onValueChange = { letter = it; dirty = true }, placeholder = { Text(stringResource(if (!d.rfqLetterDefault.isNullOrEmpty()) R.string.letter_default_hint else R.string.letter_title), color = colors.faint, fontSize = 14.sp) }, minLines = 3, maxLines = 12, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth().then(commit))
        Text(stringResource(R.string.letter_default_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// ---------- Teklif süreci ----------

@Composable
private fun RfqSegment(d: PurchaseListDetail, vm: ListDetailViewModel, onRfqPdf: (sellerId: String?) -> Unit, onAward: () -> Unit) {
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
    ProposalSection(d, vm, onAward)
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
            g.rows.forEach { r ->
                Row { Text(r.item.orEmpty(), fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f)); Text(WorkisMoney.qty(r.qty) + " " + r.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
                if (!uncovered) r.alts?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.faint) }
            }
        }
        if (uncovered) Text(stringResource(R.string.uncovered_sub), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun ProposalSection(d: PurchaseListDetail, vm: ListDetailViewModel, onAward: () -> Unit) {
    val colors = WorkisTheme.colors
    val p = d.proposal
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(if (d.list?.status == "ordered") R.string.awarded_title else R.string.proposal_title).uppercase())
        SurfaceCard {
            if (p != null && ((p.nQuotes ?: 0) > 0 || !p.assignments.isNullOrEmpty())) {
                p.assignments.orEmpty().groupBy { it.sellerName }.toSortedMap().forEach { (seller, rows) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(seller.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, color = colors.accentText)
                        rows.forEach { AssignmentRow(it) }
                    }
                }
                p.totalsByCurrency?.takeIf { it.isNotEmpty() }?.let { totals ->
                    Row { Text(stringResource(R.string.total_label), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f)); Text(totals.keys.sorted().joinToString(" · ") { WorkisMoney.text(totals[it], it) }, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink) }
                }
                p.uncovered?.takeIf { it.isNotEmpty() }?.let { u -> Text(stringResource(R.string.uncovered_group) + ": " + u.mapNotNull { it.name }.joinToString(", "), fontSize = 12.sp, color = colors.faint) }
                if (d.isQuoting) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !vm.busy, onClick = onAward).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accentText)
                        else Text("✓ " + stringResource(R.string.award_action), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
                    }
                }
            } else {
                Text(stringResource(R.string.proposal_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun AssignmentRow(a: PLAssignment) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(a.itemName.orEmpty(), fontSize = 13.sp, color = colors.ink)
            a.name?.takeIf { it != a.itemName }?.let { Text(it, fontSize = 11.sp, color = colors.faint) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(WorkisMoney.text(a.amount, a.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            Text(WorkisMoney.qty(a.qty) + " " + a.unit.orEmpty() + " × " + WorkisMoney.text(a.unitPrice, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        }
    }
}

// ---------- sheets ----------

/** Candidates with a tick on the bound one, the judge's rows under each; tap binds; search other products. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateSheet(item: PLItem, draft: Boolean, vm: ListDetailViewModel, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PLSearchResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val itemId = item.id ?: return
    fun bind(cpid: String?) {
        if (busy) return
        busy = true
        vm.bindItem(itemId, cpid) { ok, msg -> busy = false; if (ok) onDismiss() else error = msg }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.name.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            item.requirements.orEmpty().forEach { Text("• $it", fontSize = 12.sp, color = colors.muted) }
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            Eyebrow(stringResource(R.string.candidates_title).uppercase())
            val cands = item.candidates.orEmpty()
            if (cands.isEmpty()) Text(stringResource(R.string.no_match), fontSize = 13.sp, color = colors.faint)
            cands.forEach { c -> CandidateRow(c, bound = c.isBound == true || c.cpid == item.boundCpid) { c.cpid?.let { bind(it) } } }
            if (item.boundCpid != null) Text(stringResource(R.string.unbind_action), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { bind(null) })
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
            results.forEach { r ->
                Column(Modifier.fillMaxWidth().clickable { r.cpid?.let { bind(it) } }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(r.name ?: r.sku.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                    Row { Text(r.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.weight(1f)); Text(WorkisMoney.text(r.price, r.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink) }
                }
            }
            @Suppress("UNUSED_EXPRESSION") draft
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CandidateRow(c: PLCandidate, bound: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name ?: c.sku.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f))
            if (bound) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (c.verified == false) "✋ " else "") + c.seller.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
            c.sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
            Spacer(Modifier.weight(1f))
            ComplianceChip(c.compliance)
            Text(WorkisMoney.text(c.price, c.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
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
private fun AddItemSheet(vm: ListDetailViewModel, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.add_item), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(stringResource(R.string.item_name), color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fc, modifier = Modifier.fillMaxWidth())
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
private fun SendPreviewSheet(d: PurchaseListDetail, vm: ListDetailViewModel, onDismiss: () -> Unit) {
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
                    Column(Modifier.alpha(if (off) 0.4f else 1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        g.rows.forEach { r ->
                            Row { Text(r.item.orEmpty(), fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f)); Text(WorkisMoney.qty(r.qty) + " " + r.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
                            if (!uncovered) r.alts?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.faint) }
                        }
                    }
                    if (uncovered) Text(stringResource(R.string.uncovered_sub), fontSize = 12.sp, color = colors.faint)
                }
            }
            Row { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) }; Spacer(Modifier.weight(1f))
                Button(onClick = { busy = true; vm.requestQuotes(excluded.toList()) { ok, msg -> busy = false; if (ok) onDismiss() else error = msg } }, enabled = canSend && !busy, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.height(44.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill) else Text(stringResource(R.string.send_action), fontWeight = FontWeight.SemiBold)
                } }
            Spacer(Modifier.height(30.dp))
        }
    }
}
