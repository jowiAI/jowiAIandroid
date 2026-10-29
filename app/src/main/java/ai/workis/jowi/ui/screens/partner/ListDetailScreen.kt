package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import ai.workis.jowi.ui.theme.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight as KeyboardArrowRightAuto
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import ai.workis.jowi.data.ListRenameBody
import ai.workis.jowi.data.PLProvenance
import ai.workis.jowi.data.NoteBody
import ai.workis.jowi.data.PLCandidate
import ai.workis.jowi.data.PLItem
import ai.workis.jowi.data.PLSearchResult
import ai.workis.jowi.data.PLSendRow
import ai.workis.jowi.data.PLSuggestion
import ai.workis.jowi.data.PLUseAlternative
import ai.workis.jowi.data.PLUseCard
import ai.workis.jowi.data.PLUseComplement
import ai.workis.jowi.data.UseBody
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
import java.util.Locale
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.style.TextDecoration
import ai.workis.jowi.data.PLProposalItem
import ai.workis.jowi.data.PLProposalOption
import ai.workis.jowi.data.PLQuoteSheet
import ai.workis.jowi.ui.theme.Kiremit800
import ai.workis.jowi.ui.theme.PaperInk
import ai.workis.jowi.ui.theme.PaperWhite
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

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

    /** The name the bar shows while `rename/` is out (null = the detail's own title). */
    var pendingTitle by mutableStateOf<String?>(null)
        private set

    /** `POST lists/{id}/rename/ {title}` (draft/quoting, 2026-10-19) → re-read; a refusal
     *  (400 under two characters, 409 once ordered) is the screen note and the title falls back. */
    fun rename(title: String) {
        val listId = id ?: return
        if (pendingTitle != null) return
        pendingTitle = title; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listRename(listId, ListRenameBody(title)) }
            if (r != null && r.success) reload() else note = r?.error ?: r?.message ?: err
            pendingTitle = null
        }
    }

    fun load() {
        val listId = id ?: return
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listDetail(listId) }
            if (r != null) landed(r) else error = err
        }
    }

    // ---------- the four stages (2026-10-29, ledger 79) ----------

    /** The stage the reader walked to; null = the list's current one. Forgotten whenever the
     *  current stage moves (a status change, the first quote, an award) — not on every read: the
     *  page polls while working and re-reads on changes/, and a reader on Talep stays there. */
    var shown by mutableStateOf<ListStage?>(null)
    private var lastCurrent: ListStage? = null
    private var lastStatus: String? = null

    /** The decision page's picks: itemId → "sellerId|sku", "-" = left out; absent = Jowi's pick. */
    var picks by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** The buyer's reason — prefilled ONCE with Jowi's while empty (quoting lists only). */
    var decision by mutableStateOf("")
    private var decisionPrefilled = false
    /** The note per winning seller, typed on the order letters sheet (keyed as the proposal names the seller). */
    var sellerNotes by mutableStateOf<Map<String, String>>(emptyMap())
    var placing by mutableStateOf(false)
        private set
    /** The award's refusal, shown on the letters sheet too (the screen note sits under it). */
    var placeError by mutableStateOf<String?>(null)
        private set

    /** Every answered read lands here: a moved stage or status forgets the walk and the picks. */
    private fun landed(r: PurchaseListDetail) {
        val cur = currentStage(r)
        val status = r.list?.status
        if (detail != null && (cur != lastCurrent || status != lastStatus)) {
            shown = null
            if (status != lastStatus) picks = emptyMap()
        }
        lastCurrent = cur; lastStatus = status
        detail = r; error = null
        if (!decisionPrefilled && r.isQuoting) {
            r.proposal?.reason?.takeIf { it.isNotBlank() }?.let { if (decision.isEmpty()) decision = it; decisionPrefilled = true }
        }
    }

    fun pick(itemId: String, key: String) { picks = picks + (itemId to key) }

    /**
     * The order letters' "Siparişleri ver": `selections` ({itemId: {seller_id, sku}} | {skip: true}),
     * `decisionNote` (trimmed, omitted when empty), `sellerNotes` ({sellerId: text}, empty ones
     * dropped). The server's sentence is the note; orders back = the sheet closes, the picks reset
     * and the page re-reads onto Sipariş.
     */
    fun placeOrders(selections: JsonObject, onPlaced: () -> Unit) {
        val listId = id ?: return
        if (placing) return
        placing = true; note = null; placeError = null
        val notes = sellerNotes.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        val body = jsonBody(
            "selections" to selections,
            "decisionNote" to decision.trim().takeIf { it.isNotEmpty() },
            "sellerNotes" to notes.takeIf { it.isNotEmpty() }?.let { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }) },
        )
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listAwardSelections(listId, body) }
            placing = false
            note = r?.message ?: r?.error ?: err
            if (r != null && r.success != false && !r.orderIds.isNullOrEmpty()) {
                picks = emptyMap(); sellerNotes = emptyMap(); shown = null
                onPlaced()
            } else placeError = r?.error ?: r?.message ?: err
            load()
        }
    }

    /** `POST lists/{id}/requote-skipped/ {carry}` — the items left out become their own request;
     *  the server's sentence is the note and the child list appears under the header. */
    fun requoteSkipped(carry: Boolean) {
        val listId = id ?: return
        if (busy) return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listRequoteSkipped(listId, jsonBody("carry" to carry)) }
            busy = false
            note = r?.message ?: r?.error ?: err
            load()
        }
    }

    /** The buyer's "acele" on a draft's row: `POST lists/items/{id}/edit/ {urgent}`, then the re-read. */
    fun setUrgent(itemId: String, on: Boolean) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listItemEdit(itemId, jsonBody("urgent" to on)) }
            if (r == null || !r.success) note = r?.error ?: r?.message ?: err
            load()
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
        if (r != null) landed(r) else error = err
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
        addProductRow(r.cpid ?: return, r.sellerId, sku, q, onNeedsVariant = onNeedsVariant)
    }

    /** `fromItem` = the row whose Jowi card added this one ("+ Ekle", 2026-10-20) — the stage
     *  then stays on that source row; any other add whose reply says `askUse` opens the stage
     *  on the new row (the variant sheet, when `needs_variant`, opens over it first). */
    private fun addProductRow(
        cpid: String, sellerId: String?, sku: String? = null, q: String? = null, fromItem: String? = null,
        onAdded: () -> Unit = {}, onNeedsVariant: (PLItem, String) -> Unit,
    ) {
        val listId = id ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val body = jsonBody(
                "cpid" to cpid, "sellerId" to sellerId?.let { it.toIntOrNull() ?: it },
                "sku" to sku?.takeIf { it.isNotEmpty() }, "q" to q?.takeIf { it.isNotEmpty() },
                "fromItem" to fromItem?.takeIf { it.isNotEmpty() },
            )
            val (r, err) = partnerCall(lang) { Graph.api.listAddProduct(listId, body) }
            busy = false
            if (r?.success != true) { note = r?.error ?: r?.message ?: err; load(); return@launch }
            onAdded()
            if (fromItem == null && r.askUse == true) r.itemId?.let { showStage(it) }
            val fresh = reload()
            if (r.needsVariant == true) fresh?.items?.firstOrNull { it.id == r.itemId }?.let { onNeedsVariant(it, cpid) }
        }
    }
    fun addItem(name: String, qty: Double?, unit: String, fromItem: String? = null, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.listAddItem(id!!, jsonBody("name" to name, "qty" to qty, "unit" to unit.ifBlank { null }, "fromItem" to fromItem?.takeIf { it.isNotEmpty() }))
            }
            val ok = r?.success == true
            onDone(ok, r?.message ?: r?.error ?: err)
            if (ok) {
                // a manual row never asks today (it binds nothing) — honoured all the same
                if (fromItem == null && r.askUse == true) r.itemId?.let { showStage(it) }
                load()
            }
        }
    }

    // ---------- Jowi's stage (2026-10-20, ledger 66) ----------

    /** The row the buyer put on the stage (a badge, an add that asks, an answer); null = Jowi's own pick. */
    var stageItemId by mutableStateOf<String?>(null)
        private set
    /** × / "Atla" / the summary line shut the stage; a badge or the summary line opens it again. */
    var stageClosed by mutableStateOf(false)
        private set
    /** The question's text line — a chip SETS it, only "Jowi'ye söyle" sends it. */
    var useDraft by mutableStateOf("")
    /** `use/` is out — the stage's slash breathes. */
    var useReading by mutableStateOf(false)
        private set
    /** Cards that landed from a `use/` or `bind/` reply before the re-read carries them on the row
     *  (by item id; an explicit null = the reply dropped the row's card, so the stale one goes too). */
    var stageCards by mutableStateOf<Map<String, PLUseCard?>>(emptyMap())
        private set
    /** "sourceItemId|complementKey" added from a card this session → "Eklendi". */
    var addedFromStage by mutableStateOf<Set<String>>(emptySet())
        private set
    /** The page scrolls to the stage once it is drawn (the stage clears it). */
    var stageScrollPending by mutableStateOf(false)

    fun cardOf(item: PLItem): PLUseCard? {
        val id = item.id
        return if (id != null && stageCards.containsKey(id)) stageCards[id] else item.use?.card
    }
    /** A reply's `card` (present or null) stands for the row until the next re-read carries it. */
    private fun freshCard(itemId: String, card: PLUseCard?) { stageCards = stageCards + (itemId to card) }

    /** The stage's row: the explicit one, else the first that asks, else the first `better` card. Drafts only
     *  (no bound check — after "Buna geç" the server itself says `fits` + `switched`, ledger 67). */
    fun stageItem(d: PurchaseListDetail): PLItem? {
        if (!d.isDraft || stageClosed) return null
        val items = d.items.orEmpty()
        stageItemId?.let { sid -> items.firstOrNull { it.id == sid }?.let { return it } }
        return items.firstOrNull { it.askUse == true }
            ?: items.firstOrNull { cardOf(it)?.verdict == "better" }
    }

    fun complementKey(itemId: String, c: PLUseComplement) = "$itemId|${c.key}"

    /** A badge tap or an add that asks: that row on the stage, scrolled to. */
    fun showStage(itemId: String) {
        if (stageItemId != itemId) useDraft = ""
        stageItemId = itemId; stageClosed = false; stageScrollPending = true
    }
    fun closeStage() { stageClosed = true; stageScrollPending = false }
    /** The summary line under a shut stage: Jowi picks the row again. */
    fun reopenStage() { stageClosed = false; stageItemId = null }

    /**
     * `POST lists/items/{id}/use/` — `{use}` (a real answer) or `{skip: true}` (blank text).
     * A card lands on the stage at once and the re-read then carries it on the row; a null card
     * on an answer → `nothing` as the screen note and the stage closes; a skip closes it.
     */
    fun answerUse(itemId: String, text: String?, nothing: String) {
        if (useReading) return
        val t = text?.trim().orEmpty()
        useReading = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listItemUse(itemId, if (t.isEmpty()) UseBody(skip = true) else UseBody(use = t)) }
            useReading = false
            if (r == null || !r.success) { note = r?.error ?: r?.message ?: err; load(); return@launch }
            val card = r.card
            when {
                t.isEmpty() -> closeStage()
                card == null -> { note = nothing; closeStage() }
                else -> { freshCard(itemId, card); stageItemId = itemId }
            }
            useDraft = ""
            load()
        }
    }

    /** "Buna geç": the bind door with the alternative's `cpid` + `sellerId`; the reply's `card`
     *  (`fits` + `switched`) is on the stage at once, then the re-read, then the variant rule as
     *  on any bind (the variant list opens when one must still be chosen). */
    fun switchTo(itemId: String, alt: PLUseAlternative, onNeedsVariant: (PLItem, String) -> Unit) {
        val cpid = alt.cpid?.takeIf { it.isNotEmpty() } ?: return
        if (busy) return
        busy = true; note = null
        viewModelScope.launch {
            val (ok, msg) = bindItem(itemId, cpid, sellerId = alt.sellerId)
            busy = false
            if (!ok) { note = msg; return@launch }
            stageItemId = itemId
            val row = reload()?.items?.firstOrNull { it.id == itemId } ?: return@launch
            val c = row.candidates?.firstOrNull { it.cpid == cpid }
            if (c != null && c.canChooseVariant == true && c.resolved != true) onNeedsVariant(row, cpid)
        }
    }

    /** "+ Ekle": a catalogue complement through the product door (`cpid`, `sellerId`, `fromItem`),
     *  a free one through the manual door (`{name, fromItem}`). The stage stays on the source row;
     *  the new row's own badge waits. */
    fun addComplement(fromItemId: String, c: PLUseComplement, onNeedsVariant: (PLItem, String) -> Unit) {
        val key = complementKey(fromItemId, c)
        if (key in addedFromStage) return
        stageItemId = fromItemId
        val cpid = c.cpid?.takeIf { it.isNotEmpty() }
        if (cpid != null) {
            addProductRow(cpid, c.sellerId, fromItem = fromItemId, onAdded = { addedFromStage = addedFromStage + key }, onNeedsVariant = onNeedsVariant)
        } else {
            val name = c.name?.trim().orEmpty().ifEmpty { return }
            addItem(name, null, "", fromItem = fromItemId) { ok, msg -> if (ok) addedFromStage = addedFromStage + key else note = msg }
        }
    }
    /** `{cpid}` selects, `{cpid: ""}` unselects, `+ sku` chooses the variant, a search pick adds `sellerId` + `q`. → (ok, the server's sentence).
     *  The reply's `card` (2026-10-21) is the row's use card after the bind — it takes the stage's
     *  place for the row at once (null drops a stale card) until the re-read carries it. */
    suspend fun bindItem(itemId: String, cpid: String?, sku: String? = null, sellerId: String? = null, q: String? = null): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang) {
            Graph.api.listItemBind(itemId, BindBody(cpid.orEmpty(), sku?.takeIf { it.isNotEmpty() }, sellerId?.takeIf { it.isNotEmpty() }, q?.takeIf { it.isNotEmpty() }))
        }
        if (r?.success == true) freshCard(itemId, r.card)
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
fun ListDetailScreen(listId: String, onBack: () -> Unit, onThread: (String) -> Unit = {}, canWithdraw: Boolean = false, onOpenList: (String) -> Unit = {}) {
    val vm: ListDetailViewModel = viewModel(key = "list-$listId")
    val colors = WorkisTheme.colors
    LaunchedEffect(listId) { vm.bind(listId) }
    val d = vm.detail
    // Jowi's page + scope: "bu listede kaç kalem var?" is answered from THIS list (2026-09-19)
    LaunchedEffect(listId, d?.list?.title, d?.isDraft) { ai.workis.jowi.data.ScreenContext.openList(listId, d?.list?.title, d?.isDraft == true) }
    var pickItem by remember { mutableStateOf<PLItem?>(null) }
    // open the pick sheet straight on this candidate's variant list (`needs_variant`)
    var pickVariantCpid by remember { mutableStateOf<String?>(null) }
    // the row "Teklif iste" was refused for — opened once the send sheet is gone
    var pendingPick by remember { mutableStateOf<PLItem?>(null) }
    // an unsent note / letter in a field holds the changes/ re-read back (the pill waits)
    var typing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    // the order letters before the award (2026-10-29): one paper + one note per winning seller
    var showLetters by remember { mutableStateOf(false) }
    var requoteAsk by remember { mutableStateOf(false) }
    // (title, url) of the PDF on screen — a request letter or a quote
    var pdf by remember { mutableStateOf<Pair<String, String>?>(null) }
    val rfqTitle = stringResource(R.string.rfq_doc)
    val quoteTitle = stringResource(R.string.quote_doc)
    LaunchedEffect(d?.isWorking) { while (vm.detail?.isWorking == true) { delay(4_000); vm.load() } }
    LaunchedEffect(showSend, pendingPick) {
        val p = pendingPick
        if (!showSend && p != null) { delay(350); pendingPick = null; pickVariantCpid = null; pickItem = p }
    }

    // the web shows the pencil for draft and quoting only (panel_list_detail.html:185)
    val canRename = d != null && (d.isDraft || d.isQuoting)
    ConsoleSub(
        title = d?.list?.title ?: "",
        onBack = onBack,
        titleContent = { ListTitle(vm.pendingTitle ?: d?.list?.title.orEmpty(), editable = canRename, onRename = vm::rename) },
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
        // hoisted: Jowi's stage scrolls the page to itself (a badge tap, an add that asks)
        val pageScroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChangesWatch("lists", typing = typing) { vm.load() } // changes/ (2026-10-03)
            when {
                d != null -> {
                    Header(d)
                    ListLinks(d, onOpenList)
                    NoteBand(vm.note)
                    if (d.isWorking) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.faint)
                        Spacer(Modifier.width(10.dp))
                        Text(listStageWord(d.list?.stage) ?: stringResource(R.string.processing), fontSize = 13.sp, color = colors.muted)
                    }
                    // Liste › Talep › Teklifler › Sipariş (ledger 79): one stage on screen, the
                    // strip and a horizontal swipe move only between the stages reached
                    val steps = listSteps(d)
                    val sel = vm.shown?.takeIf { exists(it, d) } ?: currentStage(d)
                    val reached = steps.filter { it.exists }.map { it.stage }
                    val walk by rememberUpdatedState { forward: Boolean ->
                        val i = reached.indexOf(sel)
                        val j = if (forward) i + 1 else i - 1
                        if (i >= 0 && j in reached.indices) vm.shown = reached[j]
                    }
                    StageWalk(steps, sel) { s -> if (exists(s, d)) vm.shown = s }
                    // the swipe: only a mostly-horizontal drag of ≥ 48 px counts. The detector
                    // starts after the HORIZONTAL slop, so a vertical drag goes to the page's
                    // scroll untouched; a child that takes the drag first (a row's urgent swipe,
                    // the suggestions' strip) consumes it and the walk never sees it.
                    Box(
                        Modifier.fillMaxWidth().pointerInput(listId) {
                            var start = Offset.Zero
                            var last = Offset.Zero
                            detectHorizontalDragGestures(
                                onDragStart = { start = it; last = it },
                                onDragEnd = {
                                    val dx = last.x - start.x
                                    val dy = last.y - start.y
                                    if (abs(dx) >= 48f && abs(dx) > abs(dy) * 1.5f) walk(dx < 0)
                                },
                                onHorizontalDrag = { change, _ -> last = change.position },
                            )
                        },
                    ) {
                        Crossfade(targetState = sel, label = "listStage") { stage ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                when (stage) {
                                    ListStage.List -> ItemsSegment(
                                        d, vm,
                                        onPick = { pickVariantCpid = null; pickItem = it },
                                        onAdd = { showAdd = true },
                                        onNeedsVariant = { row, cpid -> pickVariantCpid = cpid; pickItem = row },
                                        onTyping = { typing = it },
                                        pageScroll = pageScroll,
                                    )
                                    ListStage.Request -> RequestPage(d, vm, withdrawable = vm.withdrawable ?: canWithdraw)
                                    ListStage.Quotes -> DecisionPage(
                                        d, vm,
                                        onAward = { showLetters = true },
                                        onQuotePdf = { url -> pdf = quoteTitle to url },
                                    )
                                    ListStage.Order -> OrderPage(d, vm, onRequote = { requoteAsk = true })
                                }
                                // under every stage once the request is out: the letters, then the tables
                                if (!d.isDraft) {
                                    RfqDocRows(d) { sellerId -> pdf = rfqTitle to vm.rfqPdfUrl(sellerId) }
                                    ChatInvitesSection(d, onThread)
                                }
                            }
                        }
                    }
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
        vm.note = msg; vm.shown = ListStage.List; pendingPick = row; showSend = false
    }) { showSend = false }
    pdf?.let { (title, url) -> PdfViewerDialog(title, url = url, fileName = if (title == rfqTitle) "talep-formu.pdf" else "teklif.pdf") { pdf = null } }
    if (showLetters && d != null) OrderLettersSheet(d, vm) { showLetters = false }
    if (requoteAsk) {
        // "Sipariş dışı kalanlarla yeni talep": carried quotes or a fresh round, or nothing
        AlertDialog(
            onDismissRequest = { requoteAsk = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.requote_skipped), color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.requote_hint), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { requoteAsk = false; vm.requoteSkipped(carry = true) }) { Text(stringResource(R.string.requote_carry), color = colors.accentText, fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = { requoteAsk = false; vm.requoteSkipped(carry = false) }) { Text(stringResource(R.string.requote_fresh), color = colors.accentText, fontWeight = FontWeight.Medium) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { requoteAsk = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

// ---------- the four stages ----------

enum class ListStage { List, Request, Quotes, Order }

private fun nQuotes(d: PurchaseListDetail) = d.proposal?.nQuotes ?: 0
private fun isOrdered(d: PurchaseListDetail) = d.list?.status == "ordered"

/** ordered → Sipariş; draft → Liste; else Teklifler once a quote is in, Talep before. */
private fun currentStage(d: PurchaseListDetail): ListStage = when {
    isOrdered(d) -> ListStage.Order
    d.isDraft -> ListStage.List
    nQuotes(d) > 0 -> ListStage.Quotes
    else -> ListStage.Request
}

private fun exists(s: ListStage, d: PurchaseListDetail): Boolean = when (s) {
    ListStage.List -> true
    ListStage.Request -> !d.isDraft
    ListStage.Quotes -> !d.isDraft && (nQuotes(d) > 0 || isOrdered(d))
    ListStage.Order -> isOrdered(d)
}

private data class ListStep(val stage: ListStage, val state: String, val label: String, val exists: Boolean)

/** The strip's states, the order walk's words: done · now · wait. */
@Composable
private fun listSteps(d: PurchaseListDetail): List<ListStep> {
    val done = isOrdered(d)
    val n = nQuotes(d)
    return listOf(
        ListStep(ListStage.List, if (d.isDraft) "now" else "done", stringResource(R.string.stage_list), true),
        ListStep(ListStage.Request, if (d.isDraft) "wait" else if (n == 0 && !done) "now" else "done", stringResource(R.string.stage_request), exists(ListStage.Request, d)),
        ListStep(ListStage.Quotes, if (done) "done" else if (n > 0) "now" else "wait", stringResource(R.string.stage_quotes), exists(ListStage.Quotes, d)),
        ListStep(ListStage.Order, if (done) "now" else "wait", stringResource(if (done) R.string.ls_ordered else R.string.stage_order), done),
    )
}

/**
 * The strip in the seller card's grammar (QuotesScreen `InvitationWalk`): ✓ done · ● now ·
 * ○ not reached (55 %, not tappable) — a shape AND a word, never colour alone. The shown step
 * sits on a soft capsule.
 */
@Composable
private fun StageWalk(steps: List<ListStep>, selected: ListStage, onPick: (ListStage) -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 1.dp),
    ) {
        steps.forEachIndexed { i, s ->
            if (i > 0) Text("›", fontSize = 11.sp, color = colors.faint)
            val isSel = s.stage == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSel) colors.ink.copy(alpha = 0.07f) else Color.Transparent, CircleShape)
                    .selectable(selected = isSel, enabled = s.exists, role = Role.Tab) { onPick(s.stage) }
                    .alpha(if (s.exists) 1f else 0.55f)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                StepGlyph(s.state, cut = colors.canvas)
                Spacer(Modifier.width(5.dp))
                Text(
                    s.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = when { isSel -> colors.ink; s.exists -> colors.muted; else -> colors.faint },
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** Where this list came from and the lists that continue it (`requote-skipped`, both ways). */
@Composable
private fun ListLinks(d: PurchaseListDetail, onOpenList: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val parent = d.list?.parent?.takeIf { !it.id.isNullOrEmpty() }
    val children = d.list?.children.orEmpty().filter { !it.id.isNullOrEmpty() }
    if (parent == null && children.isEmpty()) return
    val sourceWord = stringResource(R.string.source_list)
    val nextWord = stringResource(R.string.continued_in)
    SurfaceCard {
        (listOfNotNull(parent?.let { sourceWord to it }) + children.map { nextWord to it }).forEachIndexed { i, (word, l) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { l.id?.let(onOpenList) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (l === parent) WorkisIcons.Undo else WorkisIcons.ArrowRightCircle, contentDescription = null, tint = colors.faint, modifier = Modifier.size(15.dp))
                Text(word, fontSize = 13.sp, color = colors.muted)
                Text(l.title.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRightAuto, contentDescription = null, tint = colors.faint)
            }
        }
    }
}

/** The request letters as quiet rows under every non-draft stage: faint icon, muted words. */
@Composable
private fun RfqDocRows(d: PurchaseListDetail, onOpen: (String?) -> Unit) {
    val colors = WorkisTheme.colors
    val docs = d.rfqDocs.orEmpty()
    if (docs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        docs.forEach { doc ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(doc.sellerId) }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.faint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.rfq_doc), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1, softWrap = false)
                Text(
                    " · " + (doc.lbl?.short ?: doc.number).orEmpty(),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun Header(d: PurchaseListDetail) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the strip says the status (2026-10-29): up here the tags, the web's meta line
        // ("19.10.2026 01:55 · Chat") and, on the right, whose move it is — blue only when
        // it is the buyer's own (the rule on `WorkisColors.blue`)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.list?.demo == true) StatusPill(stringResource(R.string.demo_tag))
            if (d.list?.held != null) StatusPill(stringResource(R.string.held_tag), Beige)
            val lang by Graph.language.collectAsState()
            val meta = listOfNotNull(metaDate(d.list?.createdAt), sourceWord(d.list?.source, lang)).filter { it.isNotEmpty() }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            when {
                isOrdered(d) -> d.orders.orEmpty().mapNotNull { it.poNumber?.takeIf { p -> p.isNotEmpty() } }.takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                d.isQuoting && nQuotes(d) > 0 -> Text(stringResource(R.string.awaiting_your_pick), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.blue, maxLines = 1, softWrap = false)
                d.isQuoting -> Text(stringResource(R.string.awaiting_quote), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1, softWrap = false)
            }
        }
        val project = d.projects?.firstOrNull { it.id == d.list?.projectId }?.name ?: d.projects?.firstOrNull { it.isDefault == true }?.name
        project?.let {
            Text("📁 " + stringResource(R.string.project_label) + ": $it", fontSize = 12.sp, color = colors.muted)
            Text(stringResource(R.string.project_hint), fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint)
        }
    }
}

/** "2026-10-19T01:55:12…" → "19.10.2026 01:55" — the web's `d.m.Y H:i` (string slicing, as iOS). */
private fun metaDate(iso: String?): String? {
    if (iso == null) return null
    if (iso.length < 16) return iso.take(10)
    val p = iso.take(10).split("-")
    if (p.size != 3) return iso.take(10)
    return "${p[2]}.${p[1]}.${p[0]} " + iso.substring(11, 16)
}

/** The web's source display ("Chat", "PDF"): the server's word with a capital, nothing else. */
private fun sourceWord(source: String?, lang: String): String? {
    val s = source?.trim().orEmpty()
    if (s.isEmpty()) return null
    return s.take(1).uppercase(Locale.forLanguageTag(lang)) + s.drop(1)
}

/**
 * The list's name in the bar (`POST lists/{id}/rename/`, 2026-10-19): tap-to-edit while the
 * list is a draft or out for quotes (a faint pencil says so), a plain title otherwise. Done or
 * focus loss commits a trimmed, non-empty, changed value; a refusal falls back to the title.
 */
@Composable
private fun RowScope.ListTitle(title: String, editable: Boolean, onRename: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(editable) { if (!editable) editing = false }
    if (!editable || !editing) {
        Row(
            Modifier.weight(1f).then(
                if (editable) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.groups_rename)) { editing = true } else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontSize = 22.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (editable) {
                Spacer(Modifier.width(6.dp))
                Icon(WorkisIcons.Pencil, contentDescription = null, tint = colors.faint, modifier = Modifier.size(14.dp))
            }
        }
        return
    }
    var field by remember { mutableStateOf(TextFieldValue(title, selection = TextRange(title.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    fun commit() {
        if (!editing) return
        editing = false
        val want = field.text.trim()
        if (want.isNotEmpty() && want != title) onRename(want)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = field,
        onValueChange = { field = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, color = colors.ink),
        cursorBrush = SolidColor(colors.accentText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged {
            if (it.isFocused) hadFocus = true else if (hadFocus) commit()
        },
    )
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
    onNeedsVariant: (PLItem, String) -> Unit, onTyping: (Boolean) -> Unit, pageScroll: ScrollState,
) {
    val colors = WorkisTheme.colors
    // the web's order: Jowi's note, then how the list was built, Jowi's stage, then the rows
    JowiNoteCard(d, onPick)
    ProvenanceCard(d)
    // Jowi's stage (ledger 66): one row at a time, drafts only — a section, never a sheet
    if (d.isDraft) JowiStage(d, vm.stageItem(d), vm, pageScroll, onNeedsVariant)
    // the web frames the rows in kiremit while the list is being written (ledger 64): the one
    // block on the page that asks for the buyer's hand. Sent lists keep the plain card.
    SurfaceCard(border = if (d.isDraft) Kiremit500 else null) {
        // rows a seller carries whose product or variant is still unpicked (the web's `#liOpenNote`)
        if (d.isDraft) {
            val open = d.items.orEmpty().count { !it.candidates.isNullOrEmpty() && it.complete != true }
            if (open > 0) Text(
                // the web's sentence: the count leads in ink, the rest in the muted tone
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold)) { append("$open ") }
                    withStyle(SpanStyle(color = colors.muted)) { append(stringResource(R.string.list_open_rows)) }
                },
                fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
        d.items.orEmpty().forEachIndexed { i, item ->
            // the buyer's "acele" (ledger 79): a leading swipe on a draft's row, never a dismiss
            UrgentSwipe(urgent = item.urgent == true, enabled = d.isDraft && item.id != null, onToggle = { item.id?.let { vm.setUrgent(it, item.urgent != true) } }) {
                ItemRow(
                    item, draft = d.isDraft, prices = d.showsPrices, onClick = { onPick(item) }, onDelete = { item.id?.let { vm.deleteItem(it) } },
                    card = vm.cardOf(item), onBadge = { item.id?.let(vm::showStage) },
                )
            }
            if (i < d.items.orEmpty().lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        if (d.isDraft) {
            Text("+ " + stringResource(R.string.add_item), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onAdd).padding(vertical = 6.dp))
        }
    }
    // the section's footer, under the frame
    Text(stringResource(R.string.list_rows_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
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
            ProvenanceBlock(p)
            // the needs built into this draft later (`more`), under the first block
            p.more.forEach { m ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                ProvenanceLabel(stringResource(R.string.provenance_later))
                ProvenanceBlock(m)
            }
        }
    }
}

/** The web's small labels over each part of the block ("Yazdığınız", "Jowi işi şöyle okudu"…). */
@Composable
private fun ProvenanceLabel(s: String) {
    val lang by Graph.language.collectAsState()
    Text(s.uppercase(Locale.forLanguageTag(lang)), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = WorkisTheme.colors.faint)
}

/** One need's block, labelled as on the web: the buyer's sentence, the job, the problem, the questions answered. */
@Composable
private fun ProvenanceBlock(p: PLProvenance) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p.sentence.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvenanceLabel(stringResource(R.string.provenance_you_wrote))
            Text("“${p.sentence}”", fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
        }
        if (p.job.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvenanceLabel(stringResource(R.string.provenance_job_read))
            Text(p.job, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
        }
        if (p.problem.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvenanceLabel(stringResource(R.string.provenance_behind))
            Text(p.problem, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
        }
        if (p.qa.isNotEmpty()) ProvenanceLabel(stringResource(R.string.provenance_questions))
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
        // the web's title carries `at[:16]`
        val at = note.at
        val head = stringResource(R.string.jowi_note_title) + if (at != null && at.length >= 16) " · " + at.take(16).replace('T', ' ') else ""
        Eyebrow(head.uppercase())
        // the web's card (ledger 64): a note has a beige frame — colour says what the block IS (information)
        SurfaceCard(border = Beige.copy(alpha = 0.6f), fill = BeigeBg.copy(alpha = 0.35f)) {
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
private fun ItemRow(item: PLItem, draft: Boolean, prices: Boolean, onClick: () -> Unit, onDelete: () -> Unit, card: PLUseCard? = null, onBadge: () -> Unit = {}) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text("${item.n ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Same grammar as the seller's card (2026-09-19): the PRODUCT leads once
            // there is one — the variant I would be buying — and my own words follow
            // as "İstenen: …". With the generic name on top the row read as a family.
            val sel = item.selected
            Row(verticalAlignment = Alignment.Top) {
                // the web's dot: green = a seller catalogue carries the row, kiremit = Workis
                // sources it — the words under the name say the same (never colour alone)
                val carried = !item.candidates.isNullOrEmpty()
                val dotWords = stringResource(if (carried) R.string.matched_in_catalogs else R.string.row_free_text)
                Box(Modifier.padding(top = 7.dp, end = 7.dp).size(7.dp).background(if (carried) SuccessGreen else Kiremit500, CircleShape).semantics { contentDescription = dotWords })
                // on a draft the product (family) leads and the chosen VARIANT's name sits
                // under it once one stands for it (`resolved`, 2026-10-02)
                Text((if (draft) sel?.name?.takeIf { it.isNotEmpty() } else sel?.displayName) ?: item.name ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink, maxLines = 3, modifier = Modifier.weight(1f, fill = false))
                // "+3": the other candidates, while none is bound (the web's `n_cands-1`)
                val n = item.candidates?.size ?: 0
                if (draft && item.boundCpid == null && n > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("+${n - 1}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 20.sp, color = colors.faint)
                }
                Spacer(Modifier.weight(1f))
                // the buyer's "acele" (ledger 79): the WORD is the status, beige only tints it
                if (item.urgent == true) {
                    Spacer(Modifier.width(6.dp))
                    UrgentWord()
                }
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
            // Jowi's line (ledger 66): the badge — a door to the stage — and "Kullanım: …"
            JowiRowLine(item, card, draft, onBadge)
            if (draft) {
                DraftRowState(item, prices)
            } else if (sel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sel.sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                    Spacer(Modifier.weight(1f))
                    ComplianceChip(sel.compliance)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // the server's label carries the mark of a virtual seller ("/ NAME", 2026-10-19)
                    SellerLabel(sel)
                    Spacer(Modifier.weight(1f))
                    VariantPrice(sel, prices)
                }
            } else {
                SourcedLine()
            }
            if (draft) Text(stringResource(R.string.delete_item), fontSize = 11.sp, color = colors.danger, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

/** "ACELE" — mono, beige on its soft tint, beside the quantity (the seller's card prints the same). */
@Composable
private fun UrgentWord() {
    val lang by Graph.language.collectAsState()
    Text(
        stringResource(R.string.urgent_word).uppercase(Locale.forLanguageTag(lang)),
        fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = Beige, softWrap = false,
        modifier = Modifier.padding(top = 2.dp).background(BeigeBg, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * A draft row's leading swipe (start → end) toggles "acele": the box settles back (never a
 * dismiss) and the list re-reads. The same toggle rides the row as an accessibility action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrgentSwipe(urgent: Boolean, enabled: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val colors = WorkisTheme.colors
    val toggle by rememberUpdatedState(onToggle)
    val label = stringResource(if (urgent) R.string.not_urgent_action else R.string.urgent_action)
    val state = rememberSwipeToDismissBoxState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        // never a dismiss: the row settles back, the server's re-read draws the word
        onDismiss = { v -> if (v == SwipeToDismissBoxValue.StartToEnd) toggle(); scope.launch { state.reset() } },
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { toggle(); true }) },
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(BeigeBg, RoundedCornerShape(10.dp)).padding(start = 14.dp),
                horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WorkisIcons.Clock, contentDescription = null, tint = Beige, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Beige)
            }
        },
    ) { Box(Modifier.fillMaxWidth().background(colors.surface)) { content() } }
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
        SourcedLine()
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
    sel?.let { SellerLabel(it) }
}

/**
 * No seller carries it: sourcing is the platform's backstage work — never "no match" (Django
 * note 2026-10-03 §N). The seller cell says "/ Workis", muted, as the web grid does since
 * 2026-10-19 (the API's row has no seller; the empty candidate list decides).
 */
@Composable
private fun SourcedLine() {
    val colors = WorkisTheme.colors
    val hint = stringResource(R.string.sourced_seller_hint)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(WorkisIcons.TextLeft, contentDescription = null, tint = colors.faint, modifier = Modifier.size(13.dp))
        Text(stringResource(R.string.row_free_text), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.sourced_seller), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.semantics { contentDescription = hint })
    }
}

/** The seller as the server labels it — a virtual seller already carries "/ NAME" (2026-10-19);
 *  `sellerHover` is the spoken hint. */
@Composable
private fun SellerLabel(c: PLCandidate, fontSize: Int = 10) {
    val hover = c.sellerHover?.takeIf { it.isNotBlank() }
    val seller = c.seller.orEmpty()
    Text(
        seller, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = fontSize.sp, color = WorkisTheme.colors.faint,
        modifier = if (hover != null) Modifier.semantics { contentDescription = "$seller, $hover" } else Modifier,
    )
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

// ---------- Talep ----------

/** The Talep stage: who was sent what and who answered; the request's own doors (chase, withdraw). */
@Composable
private fun RequestPage(d: PurchaseListDetail, vm: ListDetailViewModel, withdrawable: Boolean) {
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
                    // the raw company name: the virtual mark is the slash (2026-10-19)
                    val vName = "/ " + v.name.orEmpty()
                    val vHint = stringResource(R.string.virtual_seller_hint)
                    Text(vName, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f).semantics { contentDescription = "$vName, $vHint" })
                    Text(inviteStatusText(v.status), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                    v.total?.let { Spacer(Modifier.width(8.dp)); Text(WorkisMoney.text(it, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink) }
                }
            }
        }
        Text(stringResource(R.string.virtual_sub), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
    // a request out for quotes may be taken back (2026-10-04) — two presses, no system
    // dialog; the lists row's flag until this screen's own read answers
    if (withdrawable) WithdrawButton(busy = vm.withdrawing, onConfirm = vm::withdraw)
}

// ---------- Sipariş ----------

/** The Sipariş stage: the orders, the buyer's recorded decision, the one door on from here. */
@Composable
private fun OrderPage(d: PurchaseListDetail, vm: ListDetailViewModel, onRequote: () -> Unit) {
    val colors = WorkisTheme.colors
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
    // the buyer's own reason, as recorded at the award — never edited, never a seller's to read
    d.list?.decisionNote?.takeIf { it.isNotBlank() }?.let { dn ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(stringResource(R.string.decision_note_label).uppercase())
            SurfaceCard { Text(dn, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
            Text(stringResource(R.string.decision_stays_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    // the one door on: the items the award left out become their own request (bordered — the
    // page's fill stays with the award)
    if (d.list?.canRequoteSkipped == true) {
        OutlinedButton(
            onClick = onRequote, enabled = !vm.busy,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.faint)
            else {
                Icon(WorkisIcons.Retry, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.requote_skipped), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ---------- Teklifler: the decision ----------

/** The item's chosen option: the buyer's pick, "-" = left out, absent = Jowi's (`recommended`). */
private fun chosenOption(it: PLProposalItem, picks: Map<String, String>): PLProposalOption? {
    val opts = it.options.orEmpty()
    val p = picks[it.id.orEmpty()] ?: return opts.firstOrNull { o -> o.recommended == true } ?: opts.firstOrNull()
    if (p == "-") return null
    return opts.firstOrNull { o -> o.key == p }
}

/** The seller as the proposal names it — a partner id or `vp:<id>` (the `sellerNotes` key too). */
private fun sellerKey(o: PLProposalOption): String = o.seller_?.takeIf { it.isNotEmpty() } ?: o.seller.orEmpty()

/** Sums per currency in the order met — one figure when the picks share a currency. */
private fun moneyLine(options: List<PLProposalOption>): String {
    val by = LinkedHashMap<String?, Double>()
    options.forEach { o -> by[o.currency] = (by[o.currency] ?: 0.0) + (o.amount ?: 0.0) }
    if (by.isEmpty()) return WorkisMoney.text(0.0, null)
    return by.entries.joinToString(" · ") { WorkisMoney.text(it.value, it.key) }
}

/** Every seller that quoted: its sum over the items it priced and how many it covers. */
private data class Verdict(val sellerId: String, val seller: String, val sum: Double, val covered: Int, val currency: String?)

private fun verdicts(items: List<PLProposalItem>): List<Verdict> {
    val acc = LinkedHashMap<String, Verdict>()
    items.forEach { it ->
        it.options.orEmpty().forEach { o ->
            val id = sellerKey(o)
            val v = acc[id] ?: Verdict(id, o.seller ?: id, 0.0, 0, o.currency)
            acc[id] = v.copy(sum = v.sum + (o.amount ?: 0.0), covered = v.covered + 1)
        }
    }
    return acc.values.toList()
}

/** `{itemId: {seller_id, sku}}` for every chosen item, `{skip: true}` for the ones left out. */
private fun selectionsBody(items: List<PLProposalItem>, picks: Map<String, String>): JsonObject = buildJsonObject {
    items.forEach { it ->
        val id = it.id ?: return@forEach
        val o = chosenOption(it, picks)
        if (o != null) put(id, buildJsonObject { put("seller_id", o.seller_.orEmpty()); put("sku", o.sku.orEmpty()) })
        else put(id, buildJsonObject { put("skip", true) })
    }
}

/** The quote's facts as one flowing line: lead time · payment term · the terms (with where they came from). */
@Composable
private fun sheetFacts(sheet: PLQuoteSheet?, marks: Boolean): List<String> {
    if (sheet == null) return emptyList()
    val days = stringResource(R.string.vade_days_word)
    val lead = stringResource(R.string.lead_time_label)
    val advance = stringResource(R.string.vade_advance)
    val conflict = stringResource(R.string.term_conflict)
    val fromChat = stringResource(R.string.term_from_chat)
    val out = mutableListOf<String>()
    sheet.leadTime?.let { out += "$it $days $lead" }
    sheet.vade?.let { out += if (it == 0) advance else "$it $days" }
    sheet.terms.orEmpty().forEach { t ->
        val text = t.text?.takeIf { it.isNotEmpty() }
        if (!marks && text == null) return@forEach
        val mark = if (!marks) "" else if (t.conflict == true) " ($conflict)" else if (t.isFromChat) " ($fromChat)" else ""
        out += (t.label ?: t.key.orEmpty()) + ": " + (text ?: "—") + mark
    }
    return out
}

/**
 * The Teklifler stage (`WorkisDecisionSections`): the summary, Jowi's pick and reason (the
 * server's sentence), the buyer's own reason, every item with every seller's option as a
 * radio, one block per seller with the verdict in words, one total and the one kiremit action.
 * The app writes no sentence of its own. On an ordered list the radios are inert.
 */
@Composable
private fun DecisionPage(d: PurchaseListDetail, vm: ListDetailViewModel, onAward: () -> Unit, onQuotePdf: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val p = d.proposal
    val items = p?.items.orEmpty()
    val n = p?.nQuotes ?: 0
    if (n == 0 || items.isEmpty()) {
        SurfaceCard { Text(stringResource(R.string.proposal_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint, modifier = Modifier.padding(vertical = 10.dp)) }
        return
    }
    val quoting = d.isQuoting
    val picks = vm.picks
    val lang by Graph.language.collectAsState()
    val locale = Locale.forLanguageTag(lang)

    // n gelen teklif · k/m kalem karşılandı
    val covered = items.count { !it.options.isNullOrEmpty() }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text("$n", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        Text(stringResource(R.string.quotes_in_word), fontSize = 13.sp, color = colors.muted)
        Text("·", color = colors.faint)
        Text("$covered/${items.size}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        Text(stringResource(R.string.items_covered), fontSize = 13.sp, color = colors.muted)
    }

    // Jowi's pick: the recommended coverage's total, the server's reason under it
    SurfaceCard(border = Beige.copy(alpha = 0.6f), fill = BeigeBg.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.jowi_pick).uppercase(locale), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = Beige, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(moneyLine(items.mapNotNull { chosenOption(it, emptyMap()) }), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, maxLines = 1, softWrap = false)
        }
        p?.reason?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
    }

    // the buyer's own reason — prefilled once with Jowi's; their record, never a seller's
    if (quoting) {
        val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue, focusedLabelColor = colors.muted, unfocusedLabelColor = colors.faint)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = vm.decision, onValueChange = { vm.decision = it.take(1000) },
                label = { Text(stringResource(R.string.decision_label), fontSize = 13.sp) },
                minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = colors.ink).readingStep(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.decision_hint), fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    // every item, every seller's option as a radio; a second tap on the chosen one leaves it out
    SurfaceCard {
        items.forEachIndexed { i, it ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            DecisionItem(it, picks, quoting) { key -> it.id?.let { id -> vm.pick(id, key) } }
        }
    }

    // one block per seller that quoted: the verdict in words (the tone only tints it)
    val all = verdicts(items)
    val full = all.filter { it.covered == items.size }
    val lowest = full.minByOrNull { it.sum }
    all.forEach { v -> SellerVerdict(v, items.size, lowest, p?.quotes?.firstOrNull { it.seller == v.sellerId }, onQuotePdf) }

    // the total of the picks, the award, the items nothing covers
    val chosen = items.mapNotNull { chosenOption(it, picks) }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.total_label) + " · ${chosen.size} " + stringResource(R.string.items_word), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
            Text(moneyLine(chosen), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink, maxLines = 1, softWrap = false)
        }
        if (quoting) {
            Button(
                onClick = onAward, enabled = !vm.placing && chosen.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                if (vm.placing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text("✓ " + stringResource(R.string.award_action), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        p?.uncovered?.filter { it.skipped != true }?.takeIf { it.isNotEmpty() }?.let { u ->
            Text(stringResource(R.string.uncovered_group) + ": " + u.mapNotNull { it.name }.joinToString(", "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        }
    }
}

@Composable
private fun DecisionItem(it: PLProposalItem, picks: Map<String, String>, quoting: Boolean, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val chosen = chosenOption(it, picks)
    val left = picks[it.id.orEmpty()] == "-"
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                it.name.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (left) colors.faint else colors.ink,
                textDecoration = if (left) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text((WorkisMoney.qty(it.qty) + " " + it.unit.orEmpty()).trim(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink, softWrap = false)
        }
        it.options.orEmpty().forEach { o ->
            OptionRow(o, it, on = chosen != null && chosen.key == o.key, quoting = quoting) { on -> onPick(if (on) "-" else o.key) }
        }
        if (left) Text(stringResource(R.string.left_out_note), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
    }
}

/** One seller's option: the radio, the seller and the amount on one line (amounts align), the
 *  calculation under it faint ("önerilen · unit × qty"), a carried quote, the unmet requirements. */
@Composable
private fun OptionRow(o: PLProposalOption, item: PLProposalItem, on: Boolean, quoting: Boolean, onTap: (Boolean) -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth()
            .selectable(selected = on, enabled = quoting, role = Role.RadioButton) { onTap(on) }
            .padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (on) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(19.dp))
                else Box(Modifier.size(16.dp).border(1.5.dp, colors.faint, CircleShape))
            }
            Spacer(Modifier.width(8.dp))
            Text(o.seller.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) colors.ink else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text(WorkisMoney.text(o.amount, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (on) colors.ink else colors.muted, maxLines = 1, softWrap = false)
        }
        val sub = Modifier.padding(start = 28.dp)
        Text(
            listOfNotNull(
                if (o.recommended == true) stringResource(R.string.recommended_word) else null,
                WorkisMoney.text(o.unit, null) + " × " + WorkisMoney.qty(item.qty),
            ).joinToString(" · "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = sub,
        )
        o.carried?.quote?.takeIf { it.isNotEmpty() }?.let { q ->
            val until = o.carried.until?.takeIf { it.isNotEmpty() }?.let { " · " + stringResource(R.string.valid_until_label) + " " + it.take(10) }.orEmpty()
            Text(stringResource(R.string.carried_from) + " " + q + until, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = sub)
        }
        o.fails?.takeIf { it > 0 }?.let { f ->
            Text("$f " + stringResource(R.string.fails_word), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Beige, modifier = sub)
        }
    }
}

/** A seller's block: the name, its sum and the verdict in words, the term sheet as one flowing
 *  line, the quote document as a quiet row. */
@Composable
private fun SellerVerdict(v: Verdict, nItems: Int, lowest: Verdict?, sheet: PLQuoteSheet?, onQuotePdf: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val sum = WorkisMoney.text(v.sum, v.currency)
    val (line, tone) = when {
        v.covered < nItems -> "$sum · ${v.covered}/$nItems " + stringResource(R.string.items_word) to colors.muted
        lowest?.sellerId == v.sellerId -> "$sum · " + stringResource(R.string.lowest_word) to SuccessGreen
        lowest != null -> "$sum · " + WorkisMoney.text(v.sum - lowest.sum, null) + " " + stringResource(R.string.more_expensive_word) to Beige
        else -> sum to colors.muted
    }
    Column(
        Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(v.seller, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.accentText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(line, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = tone, maxLines = 2)
        val facts = sheetFacts(sheet, marks = true)
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint)
        val qid = sheet?.id?.takeIf { it.isNotEmpty() }
        if (sheet?.pdf == true && qid != null) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onQuotePdf(WORKIS_BASE_URL + "workis/quotes/$qid/pdf/") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.faint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.quote_doc), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted)
            }
        }
    }
}

// ---------- the order letters, before the award ----------

/** One winning seller's letter: its lines in item order. */
private data class Letter(val sellerId: String, val seller: String, val lines: List<Pair<PLProposalItem, PLProposalOption>>)

private fun letters(items: List<PLProposalItem>, picks: Map<String, String>): List<Letter> {
    val out = LinkedHashMap<String, Letter>()
    items.forEach { it ->
        val o = chosenOption(it, picks) ?: return@forEach
        val id = sellerKey(o)
        val l = out[id] ?: Letter(id, o.seller ?: id, emptyList())
        out[id] = l.copy(lines = l.lines + (it to o))
    }
    return out.values.toList()
}

/**
 * `WorkisOrderLettersSheet`: one paper per winning seller as the PDF will print it (white in
 * both schemes), and under each a note to THAT seller; "Siparişleri ver" places them all at
 * once (one award per request). The decision note never appears here — a seller never reads it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderLettersSheet(d: PurchaseListDetail, vm: ListDetailViewModel, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    val items = d.proposal?.items.orEmpty()
    val all = letters(items, vm.picks)
    val lang by Graph.language.collectAsState()
    val locale = Locale.forLanguageTag(lang)
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)
    ModalBottomSheet(onDismissRequest = { if (!vm.placing) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.canvas) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // the toolbar: ✕ · the title · the one kiremit action
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !vm.placing) { Icon(WorkisIcons.Cross, contentDescription = stringResource(R.string.cancel), tint = colors.muted, modifier = Modifier.size(18.dp)) }
                Text(
                    if (all.size > 1) stringResource(R.string.order_letters) + " · ${all.size}" else stringResource(R.string.order_letter_title),
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.placeOrders(selectionsBody(items, vm.picks), onPlaced = onDismiss) },
                    enabled = !vm.placing && all.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.height(38.dp),
                ) {
                    if (vm.placing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                    else Text(stringResource(if (all.size > 1) R.string.place_orders else R.string.place_order), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            NoteBand(vm.placeError)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                all.forEachIndexed { i, l ->
                    LetterPaper(l, i + 1, all.size, d.proposal?.quotes?.firstOrNull { it.seller == l.sellerId }, locale)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        Text((stringResource(R.string.seller_note_label) + " · " + l.seller).uppercase(locale), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedTextField(
                            value = vm.sellerNotes[l.sellerId].orEmpty(),
                            onValueChange = { t -> vm.sellerNotes = vm.sellerNotes + (l.sellerId to t.take(1000)) },
                            placeholder = { Text(stringResource(R.string.seller_note_placeholder), color = colors.faint, fontSize = 14.sp) },
                            minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.seller_note_hint), fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                Text(stringResource(R.string.decision_stays_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** The letter as the PDF will print it: the title, the lines, the total, the quote's facts — read-only paper. */
@Composable
private fun LetterPaper(l: Letter, index: Int, of: Int, sheet: PLQuoteSheet?, locale: Locale) {
    val muted = PaperInk.copy(alpha = 0.65f)
    val title = buildString {
        append(stringResource(R.string.order_letter_title).uppercase(locale))
        if (of > 1) append(" $index/$of")
        append(" · ").append(l.seller)
        sheet?.number?.takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
    }
    val facts = sheetFacts(sheet, marks = false)
    Column(
        Modifier.fillMaxWidth().background(PaperWhite, RoundedCornerShape(10.dp)).border(1.dp, PaperInk.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = Kiremit800, maxLines = 2)
        l.lines.forEach { (item, o) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(o.name?.takeIf { it.isNotEmpty() } ?: item.name.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = PaperInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(WorkisMoney.text(o.amount, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PaperInk, softWrap = false)
                    Text(WorkisMoney.qty(item.qty) + " × " + WorkisMoney.text(o.unit, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = muted, softWrap = false)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PaperInk.copy(alpha = 0.08f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.total_label), fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
            Text(moneyLine(l.lines.map { it.second }), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PaperInk, softWrap = false)
        }
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 11.sp, lineHeight = 15.sp, color = muted)
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
            SellerLabel(c)
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
