package ai.workis.jowi.ui.screens.partner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import ai.workis.jowi.ui.components.WorkisTitleCase
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.readingSize
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import ai.workis.jowi.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.InvListItem
import ai.workis.jowi.data.SellerInvitation
import ai.workis.jowi.data.SellerOrder
import ai.workis.jowi.data.SellerQuote
import ai.workis.jowi.data.TextBody
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import androidx.compose.material3.Icon
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ai.workis.jowi.ui.theme.readingStep

class QuotesViewModel : ViewModel() {
    var invitations by mutableStateOf<List<SellerInvitation>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    /** Counts the answered reads — a card's chosen stage is forgotten on every reload (ledger 75), so it opens on its current one. */
    var loadGen by mutableIntStateOf(0)
        private set
    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.sellerInvitations() }
            if (r != null) { invitations = r.invitations.orEmpty(); error = null; loadGen++ } else error = err
        }
    }

    fun revise(id: String) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.sellerQuoteRevise(id) }
            note = r?.message ?: r?.error ?: err
            load()
        }
    }

    suspend fun submit(id: String, body: JsonObject): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang) { Graph.api.sellerQuote(id, body) }
        val ok = r?.success == true
        if (ok) { note = r?.message; load() }
        return ok to (r?.message ?: r?.error ?: err)
    }

    /** The unsaved quotation as PDF bytes (nothing written server-side). */
    suspend fun preview(id: String, body: JsonObject): Pair<ByteArray?, String?> {
        val (r, err) = partnerCall(lang) { withContext(Dispatchers.IO) { Graph.api.sellerQuotePreview(id, body).use { it.bytes() } } }
        return if (r != null && looksLikePdf(r)) r to null else null to (err ?: statusText())
    }

    /**
     * The server-side draft (`quote/draft/`, 2026-09-30). It is NOT on the invitation payload,
     * so every open asks for it; the stored draft is snake_case (the reader takes both).
     */
    suspend fun draft(id: String): JsonObject? =
        partnerCall(lang) { Graph.api.sellerQuoteDraft(id) }.first?.get("draft") as? JsonObject

    /** Autosave with the send body — in the model's scope, so a close-without-Send still lands. */
    fun saveDraft(id: String, body: JsonObject, onSaved: () -> Unit) {
        viewModelScope.launch {
            val (r, _) = partnerCall(lang) { Graph.api.sellerQuoteDraftSave(id, body) }
            if (r != null && (r["success"] as? JsonPrimitive)?.booleanOrNull != false) onSaved()
        }
    }

    suspend fun polish(text: String): Pair<String?, String?> {
        val (r, err) = partnerCall(lang) { Graph.api.textPolish(TextBody(text)) }
        val s = r?.suggestion?.takeIf { it.isNotEmpty() }
        return s to (if (s == null) (r?.error ?: err ?: statusText()) else null)
    }

    private fun statusText() = ai.workis.jowi.data.statusMessage(-1, lang)
}

/** Teklifler — one card per invitation, ONE stage at a time (request · quote · order). */
@Composable
fun QuotesScreen(vm: QuotesViewModel, onCompose: (String) -> Unit, onThread: (String) -> Unit = {}) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ai.workis.jowi.data.ScreenContext.setPage("quotes"); vm.load() } // Jowi's page (2026-09-19)
    var pdf by remember { mutableStateOf<Pair<String, String>?>(null) } // url to title
    var reviseFor by remember { mutableStateOf<SellerInvitation?>(null) }
    val rows = vm.invitations
    ai.workis.jowi.ui.components.ChangesWatch("quotes") { vm.load() } // changes/ (2026-10-03)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.quotes_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        item { NoteBand(vm.note) }
        when {
            rows != null -> {
                if (rows.isEmpty()) item { Text(stringResource(R.string.quotes_empty), fontSize = 13.sp, color = colors.faint) }
                items(rows, key = { it.id ?: it.hashCode() }) { inv ->
                    InvitationCard(
                        inv,
                        loadGen = vm.loadGen,
                        onCompose = { inv.id?.let(onCompose) },
                        onPdf = { url, title -> pdf = url to title },
                        onRevise = { reviseFor = inv },
                        onThread = { inv.id?.let(onThread) },
                    )
                }
            }
            vm.error != null -> item { Text(vm.error.orEmpty(), color = colors.danger, fontSize = 13.sp) }
            else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
    pdf?.let { (url, title) -> PdfViewerDialog(title, url = url, fileName = "workis.pdf") { pdf = null } }
    reviseFor?.let { inv ->
        AlertDialog(
            onDismissRequest = { reviseFor = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.quote_revise), color = colors.ink) },
            text = { Text(stringResource(R.string.quote_revise_confirm), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted) },
            confirmButton = { TextButton(onClick = { reviseFor = null; inv.id?.let { vm.revise(it) } }) { Text(stringResource(R.string.quote_revise), color = colors.danger, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { reviseFor = null }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

// ---------- one card, one stage (ledgers 75/76, 2026-10-28) ----------

/** A request's three stages. A stage EXISTS once the request reached it; the strip shows all, the card shows one. */
private enum class InvStage { Request, Quote, Order }

/** One step of the strip: the order walk's state word (done · now · wait · skip · lost) and the catalogue label. */
private data class InvStep(val stage: InvStage, val state: String, val label: String, val exists: Boolean)

private fun currentStage(inv: SellerInvitation): InvStage = when {
    inv.order != null -> InvStage.Order
    inv.quote != null -> InvStage.Quote
    else -> InvStage.Request
}

/** Request ✓/●/⊖ · Quote ✓/●/○ (✕ when lost) · Order ●/○ — the order step drops out of a lost request. */
@Composable
private fun invSteps(inv: SellerInvitation): List<InvStep> {
    val hasQuote = inv.quote != null
    val hasOrder = inv.order != null
    val lost = inv.lost == true
    val request = InvStep(InvStage.Request, if (inv.isWithdrawn) "skip" else if (hasQuote) "done" else "now", stringResource(R.string.stage_request), true)
    val quote = InvStep(
        InvStage.Quote,
        when { lost -> "lost"; hasOrder -> "done"; hasQuote -> "now"; else -> "wait" },
        stringResource(if (lost) R.string.stage_lost else R.string.stage_quote), hasQuote,
    )
    val order = InvStep(InvStage.Order, if (hasOrder) "now" else "wait", stringResource(if (hasOrder) R.string.order_won else R.string.stage_order), hasOrder)
    return listOf(request, quote, order).filter { it.exists || !lost || it.stage != InvStage.Order }
}

/**
 * One request as ONE stage at a time: the header names the list, the buyer and the small
 * number of the stage (no money up here — the block sums it once); the strip is the order
 * card's walk and a tap on a step that EXISTS shows that stage; a horizontal swipe on the
 * body walks the reached stages (leftward = next). The card opens on its CURRENT stage and
 * the choice is forgotten when the request moves on. The actions (send · documents ·
 * correspondence) sit under the card whatever page is shown. Blue appears once: "teklifinizi
 * bekliyor" — the seller's own move (the rule on `WorkisColors.blue`).
 */
@Composable
private fun InvitationCard(inv: SellerInvitation, loadGen: Int, onCompose: () -> Unit, onPdf: (String, String) -> Unit, onRevise: () -> Unit, onThread: () -> Unit) {
    val steps = invSteps(inv)
    // forgotten on every answered reload: a request that just moved on opens on its new stage
    var shown by remember(inv.id, loadGen) { mutableStateOf<InvStage?>(null) }
    val sel = shown?.takeIf { s -> steps.any { it.stage == s && it.exists } } ?: currentStage(inv)
    val pick: (InvStage) -> Unit = { s -> if (steps.any { it.stage == s && it.exists }) shown = s }
    val reached = steps.filter { it.exists }.map { it.stage }
    val walk by rememberUpdatedState { forward: Boolean ->
        val i = reached.indexOf(sel)
        val j = if (forward) i + 1 else i - 1
        if (i >= 0 && j in reached.indices) pick(reached[j])
    }
    SurfaceCard {
        InvitationHeader(inv)
        InvitationWalk(steps, sel, pick)
        // the swipe: only a mostly-horizontal drag of ≥ 48 px counts; a vertical one never
        // starts here (horizontal slop first), so the list keeps its scroll
        Box(
            Modifier.fillMaxWidth().pointerInput(inv.id) {
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
            Crossfade(targetState = sel, label = "invStage") { stage ->
                when (stage) {
                    InvStage.Request -> RequestPage(inv)
                    InvStage.Quote -> inv.quote?.let { QuoteBlock(it, inv, onRevise) }
                    InvStage.Order -> inv.order?.let { OrderBlock(it) }
                }
            }
        }
        InvitationActions(inv, onCompose, onPdf, onThread)
    }
}

/**
 * Three paired rows, each ONE line, never two columns: list name ↔ date · buyer (city) ↔ the
 * stage's small number or status word · while open "teklifinizi bekliyor" alone on the right.
 * The delivery place closes the header on every stage — a fact of the request, not of a stage.
 */
@Composable
private fun InvitationHeader(inv: SellerInvitation) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the buyer's own words — never recased; shrinks to 80 % before it truncates
            BasicText(
                inv.caseTitle ?: inv.request ?: "—",
                style = LocalTextStyle.current.merge(TextStyle(fontFamily = WorkisSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.ink)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.8.sp, maxFontSize = 16.sp, stepSize = 0.4.sp),
                modifier = Modifier.weight(1f),
            )
            inv.createdAt?.let {
                Spacer(Modifier.width(10.dp))
                Text(it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, softWrap = false, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val buyer = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = readingSize(12.sp), color = colors.accentText)) { append(inv.buyer?.short.orEmpty()) }
                inv.buyer?.city?.takeIf { it.isNotEmpty() }?.let { c ->
                    withStyle(SpanStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = readingSize(12.sp), color = colors.faint)) { append(" ($c)") }
                }
            }
            Text(buyer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            HeaderMeta(inv)
        }
        if (inv.isOpen && inv.quote == null && inv.order == null && inv.lost != true && !inv.isWithdrawn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // blue = "sıra sizde" — the one place on this screen, beside its words
                Text(stringResource(R.string.awaiting_your_quote), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.blue, maxLines = 1, softWrap = false)
            }
        }
        DeliveryPlaceLine(inv.deliveryPlace, Modifier.padding(top = 3.dp))
    }
}

/** The right side of the buyer's row: won → PO · lost → the closed word · withdrawn · quoted → WTK · open → "n kalem". */
@Composable
private fun HeaderMeta(inv: SellerInvitation) {
    val colors = WorkisTheme.colors
    val o = inv.order
    val q = inv.quote
    val (text, word) = when {
        o != null -> o.poNumber to false
        inv.lost == true -> (inv.statusLabel ?: stringResource(R.string.order_lost)) to true
        inv.isWithdrawn -> stringResource(R.string.ls_withdrawn) to true
        q != null -> q.number to false
        inv.isOpen -> inv.listItems.orEmpty().size.takeIf { it > 0 }?.let { "$it " + stringResource(R.string.items_word) } to false
        else -> (inv.statusLabel ?: inv.status) to true
    }
    val t = text?.takeIf { it.isNotEmpty() } ?: return
    Spacer(Modifier.width(10.dp))
    if (word) Text(t, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1, softWrap = false)
    else Text(t, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1, softWrap = false)
}

/**
 * The strip in the order walk's grammar: ✓ done · ● now · ○ not reached (55 %, not tappable)
 * · ✕ lost · ⊖ withdrawn — a shape AND a word, never colour alone. The shown step sits on a
 * soft capsule.
 */
@Composable
private fun InvitationWalk(steps: List<InvStep>, selected: InvStage, onPick: (InvStage) -> Unit) {
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
                if (s.state == "lost") LostGlyph() else StepGlyph(s.state)
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

/** ✕ lost — a ring with a cross, in the danger ink (the walk's ring, crossed). */
@Composable
private fun LostGlyph(size: Dp = 12.dp) {
    val tint = WorkisTheme.colors.danger
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val sw = this.size.minDimension * 0.13f
        val c = center
        drawCircle(tint, r - sw / 2f, c, style = Stroke(sw))
        val d = r * 0.36f
        drawLine(tint, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), sw, cap = StrokeCap.Round)
    }
}

/** The request as asked: every item from the seller's chair, a faint rule between two items. */
@Composable
private fun RequestPage(inv: SellerInvitation) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // the buyer took the request back (2026-10-04): closed, icon + words, no quote form
        if (inv.isWithdrawn) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(WorkisIcons.Undo, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.inv_withdrawn), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted)
        }
        val items = inv.listItems.orEmpty()
        if (items.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, li ->
                if (i > 0) HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                ListItemBlock(li)
            }
        } else inv.shortlist?.takeIf { it.isNotEmpty() }?.let { sl ->
            Text(stringResource(R.string.shortlist_label) + ": " + sl.mapNotNull { it.name ?: it.sku }.joinToString(" · "), fontSize = 11.sp, color = colors.faint)
        }
    }
}

/**
 * Under the card whatever page is shown: the one primary action while the request is open
 * and unquoted, then the documents in birth order (request · quote · order) as QUIET rows —
 * muted text, faint icon; kiremit stays on "Teklif gönder" alone — then the table's door.
 */
@Composable
private fun InvitationActions(inv: SellerInvitation, onCompose: () -> Unit, onPdf: (String, String) -> Unit, onThread: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (inv.quote == null && inv.isOpen) {
            Button(onClick = onCompose, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("✈ " + stringResource(R.string.send_quote), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        inv.rfqPdfUrl?.takeIf { it.isNotEmpty() }?.let { url -> DocRow(stringResource(R.string.rfq_doc)) { t -> onPdf(url, t) } }
        inv.quote?.let(::quotePdfUrl)?.let { url -> DocRow(stringResource(R.string.quote_doc)) { t -> onPdf(url, t) } }
        inv.order?.poPdfUrl?.takeIf { it.isNotEmpty() }?.let { url -> DocRow(stringResource(R.string.order_doc)) { t -> onPdf(url, t) } }
        // every invitation carries its own table (slice 2 part 2)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onThread).padding(vertical = 4.dp)) {
            Icon(WorkisIcons.Bubble, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.thread_open), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
        }
    }
}

/** A document as a quiet row: faint icon, muted words — never a link colour. */
@Composable
private fun DocRow(title: String, onOpen: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpen(title) }.padding(vertical = 4.dp)) {
        Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.faint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.muted)
    }
}

/**
 * "TÜPSÜZ MODEL & TEK KAPAKLI · 70X70X23 · …": the family is the product, the rest its
 * variant — the server splits `display_name` at the first " · ". The family at 15 semibold
 * (an ALL-CAPS one in Turkish title case, `WorkisTitleCase`), " · " + the variant at 13
 * regular, as printed.
 */
@Composable
private fun productName(full: String): AnnotatedString {
    val ink = WorkisTheme.colors.ink
    val (head, tail) = WorkisTitleCase.product(full)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = readingSize(15.sp), color = ink)) { append(head) }
        tail?.let { withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = readingSize(13.sp), color = ink)) { append(" · $it") } }
    }
}

/**
 * The hierarchy is built from the SELLER's seat: my own product (the fitting variant)
 * leads, its SKU under it, and the buyer's words follow as "İstenen: …". The first cut
 * had it upside down — the buyer's generic name large, my product faint (2026-09-19).
 */
@Composable
private fun ListItemBlock(li: InvListItem) {
    val colors = WorkisTheme.colors
    val alts = li.alts.orEmpty().filter { !(it.name ?: it.sku).isNullOrEmpty() }
    val qty = (WorkisMoney.qty(li.qty) + " " + li.unit.orEmpty()).trim()
    val asked = (listOf(if (alts.size == 1) li.itemName else null) + li.requirements.orEmpty()).filterNotNull().filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (alts.size == 1) {
                Text(productName(alts[0].name ?: alts[0].sku.orEmpty()), lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                // the buyer's row name — their record, never recased
                Text(li.itemName.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (qty.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Text(qty, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink, softWrap = false) }
        }
        if (alts.size == 1) alts[0].sku?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted) }
        // several of my products fit — each one readable, none of them a footnote
        if (alts.size > 1) alts.forEach { a ->
            Row(verticalAlignment = Alignment.Top) {
                Text(WorkisTitleCase.productLine(a.name ?: a.sku.orEmpty()), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                a.sku?.takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.width(6.dp)); Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.muted) }
            }
        }
        if (asked.isNotEmpty()) Text(stringResource(R.string.asked_label) + ": " + asked.joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 3)
        // the use travels (ledger 69): what the buyer will do with it, under the demands
        li.use?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.use_label) + ": " + it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 3) }
    }
}

/** The sent quote's PDF through the token-auth door (`quotes/{id}/pdf/`); the web `pdfUrl` when there is no id. */
private fun quotePdfUrl(q: SellerQuote): String? =
    q.id?.takeIf { it.isNotEmpty() }?.let { WORKIS_BASE_URL + "workis/quotes/$it/pdf/" } ?: q.pdfUrl?.takeIf { it.isNotEmpty() }

/** "Teslim yeri: …" with a pin — the request's own delivery term (2026-09-30); in the header on every stage. */
@Composable
private fun DeliveryPlaceLine(place: String?, modifier: Modifier = Modifier) {
    val p = place?.takeIf { it.isNotEmpty() } ?: return
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.delivery_place_label) + ": " + p, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The sent quote: state ↔ WTK on one line, the lines, a faint rule, "Toplam" ↔ the sum (the money written ONCE), the terms. */
@Composable
private fun QuoteBlock(q: SellerQuote, inv: SellerInvitation, onRevise: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ " + stringResource(R.string.quote_sent), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SuccessGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            q.number?.let { Spacer(Modifier.width(8.dp)); Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1, softWrap = false) }
        }
        q.lines.orEmpty().forEach { l ->
            Row(verticalAlignment = Alignment.Top) {
                Text(WorkisTitleCase.productLine(l.name ?: l.sku.orEmpty()), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(WorkisMoney.qty(l.qty) + " × " + WorkisMoney.text(l.unitPrice, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, softWrap = false)
                Spacer(Modifier.width(8.dp))
                Text(WorkisMoney.text(l.amount, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink, softWrap = false)
            }
            // what the line was priced against (2026-10-01): "liste X · −Y %"
            l.listPrice?.let { lp ->
                Text(
                    stringResource(R.string.quote_list_word) + " " + WorkisMoney.text(lp, null) + (l.discountPct?.let { " · −" + WorkisMoney.pct(it) + " %" } ?: ""),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint,
                )
            }
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.total_label), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted, modifier = Modifier.weight(1f))
            Text(WorkisMoney.text(q.total ?: q.price, q.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, maxLines = 1, softWrap = false)
        }
        // the terms as one flowing text (wraps as text, never as columns)
        val days = listOfNotNull(
            q.leadTimeDays?.let { "$it " + stringResource(R.string.vade_days_word) + " " + stringResource(R.string.lead_time_label) },
            q.validUntil?.let { stringResource(R.string.valid_until_label) + " " + it.take(10) },
            q.vadeDays?.let { if (it == 0) stringResource(R.string.vade_advance) else "$it " + stringResource(R.string.vade_days_word) },
        )
        if (days.isNotEmpty()) Text(days.joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint)
        // the terms the quote was sent with (2026-09-30), one muted line
        q.terms?.let { tm ->
            val warranty = tm.warrantyMonths?.let { if (it == 0) stringResource(R.string.warranty_none) else "$it " + stringResource(R.string.months_word) }
            val parts = listOf(tm.packaging, warranty, tm.warrantyText, tm.returnsText).filterNotNull().filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 3)
        }
        if (inv.order == null && inv.lost != true && inv.rfqStatus != "awarded" && !inv.isWithdrawn) {
            Text(stringResource(R.string.quote_revise), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onRevise))
        }
    }
}

/** The won order: ONE non-wrapping line "SİPARİŞ KAZANILDI … total", the PO under it, payment, shipment. */
@Composable
private fun OrderBlock(o: SellerOrder) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().background(BeigeBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.order_won).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = SuccessGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(WorkisMoney.text(o.total, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, maxLines = 1, softWrap = false)
        }
        o.poNumber?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
        // The payment line says what the order RUNS ON. On a deferred term nothing will
        // ever be blocked — never tell the seller to wait for it (finding 28). Branch on
        // the CODE, never on the label.
        if (o.payment?.mode == "vade") {
            OrderLine(WorkisIcons.Calendar, listOfNotNull(stringResource(R.string.pay_open_account), o.payment.days?.let { "$it " + stringResource(R.string.pay_days_term) }).joinToString(" · "))
        } else o.escrowStateLabel?.takeIf { it.isNotEmpty() }?.let { OrderLine(WorkisIcons.Lock, it) }
        o.shipment?.let { sh ->
            OrderLine(WorkisIcons.ShippingBox, listOfNotNull(sh.number, sh.statusLabel ?: sh.status).filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }
}

/** One fact of the won order: the system icon in the accent tint, the server's words beside it. */
@Composable
private fun OrderLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
    }
}

// ---------- Composer ----------

/**
 * One priceable product of mine. `listPrice` = my catalogue price (2026-09-30), `discountPct`
 * my usual rate on it (2026-10-01); `single` = the item's ONLY candidate — the one that pre-fills.
 */
private data class PriceRow(
    val sku: String, val name: String, val group: String?, val qty: Double?, val unit: String?,
    val listPrice: Double? = null, val discountPct: Double? = null, val single: Boolean = false,
) {
    val list: BigDecimal? get() = listPrice?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it) }
}

private val HUNDRED = BigDecimal(100)

/** Moving the discount sets the unit price: list × (1 − pct/100), 2 decimals (BigDecimal, never Double). */
private fun unitFromDiscount(list: BigDecimal, pct: BigDecimal): String =
    WorkisMoney.plain(list.multiply(BigDecimal.ONE - pct.divide(HUNDRED)).setScale(2, RoundingMode.HALF_UP))

/** Typing a unit price writes the discount it implies: (1 − unit/list) × 100, 1 decimal, never below 0. */
private fun discountFromUnit(list: BigDecimal, unit: BigDecimal): String =
    WorkisMoney.plain((BigDecimal.ONE - unit.divide(list, 10, RoundingMode.HALF_UP)).multiply(HUNDRED).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP))

/**
 * The catalogue price pre-fills an EMPTY unit price on a single-candidate item only — on
 * alternatives the seller says which one is the quote. The usual rate applies at once.
 * → (unit price, discount text or null).
 */
private fun prefillOf(r: PriceRow): Pair<String, String?>? {
    if (!r.single) return null
    val list = r.list ?: return null
    val pct = r.discountPct?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it) }
    return if (pct != null) unitFromDiscount(list, pct) to WorkisMoney.plain(pct) else WorkisMoney.plain(list) to null
}

/** A draft value as text — numbers and strings alike, the first spelling present; JSON null and "" are absent. */
private fun JsonObject.text(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k] as? JsonPrimitive ?: continue
        if (v is JsonNull) continue
        v.content.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/** A typed figure as the API reads it (dot decimal); left as typed when it is not a number. */
private fun apiNumber(text: String): String = WorkisMoney.parse(text)?.let { WorkisMoney.plain(it) } ?: text

/** The quote composer — own products only (the competitor wall is the server's); Jowi's polish is a suggestion the user applies (§0). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteComposerScreen(vm: QuotesViewModel, invitationId: String, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val inv = vm.invitations?.firstOrNull { it.id == invitationId }
    BackHandler(onBack = onClose)
    if (inv == null) { onClose(); return }
    val rows = remember(inv) {
        val li = inv.listItems.orEmpty()
        if (li.isNotEmpty()) li.flatMap { item ->
            val alts = item.alts.orEmpty()
            alts.mapNotNull { a -> a.sku?.let { PriceRow(it, a.name ?: it, item.itemName, item.qty, item.unit, a.listPrice, a.discountPct, single = alts.size == 1) } }
        }
        else inv.shortlist.orEmpty().mapNotNull { p -> p.sku?.let { PriceRow(it, p.name ?: it, null, inv.qty, null) } }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    val prices = remember { mutableStateMapOf<String, String>().apply { rows.forEach { r -> prefillOf(r)?.let { put(r.sku, it.first) } } } }
    // sku → discount % text (rows with a list price)
    val discounts = remember { mutableStateMapOf<String, String>().apply { rows.forEach { r -> prefillOf(r)?.second?.let { put(r.sku, it) } } } }
    val qtys = remember { mutableStateMapOf<String, String>().apply { rows.forEach { put(it.sku, WorkisMoney.qty(it.qty)) } } }
    var leadTime by rememberSaveable { mutableStateOf("") }
    var validDays by rememberSaveable { mutableStateOf("30") }
    var vade by rememberSaveable { mutableStateOf(0) }
    var delivery by rememberSaveable { mutableStateOf("workis_kargo") }
    // the terms born with the quote (2026-09-30)
    var packaging by rememberSaveable { mutableStateOf("") }
    var warrantyMonths by rememberSaveable { mutableStateOf("") }
    var warrantyText by rememberSaveable { mutableStateOf("") }
    var returnsText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var letter by rememberSaveable(inv.id) { mutableStateOf(inv.defaultLetter.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewPdf by remember { mutableStateOf<ByteArray?>(null) }
    var suggestion by remember { mutableStateOf<Pair<String, String>?>(null) } // target to text
    // The server-side draft (`quote/draft/`): restored on open, autosaved 1.5 s after the
    // last edit and on close-without-Send — never per keystroke. Send clears it on the server.
    var draftReady by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf("") }
    var draftTick by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    fun setDiscount(r: PriceRow, text: String) {
        discounts[r.sku] = text
        val list = r.list ?: return
        val pct = WorkisMoney.parse(text) ?: return
        prices[r.sku] = unitFromDiscount(list, pct)
    }
    fun setPrice(r: PriceRow, text: String) {
        prices[r.sku] = text
        val list = r.list ?: return
        val unit = WorkisMoney.parse(text)
        discounts[r.sku] = if (unit == null) "" else discountFromUnit(list, unit)
    }

    fun pricedLines(): JsonArray = JsonArray(rows.mapNotNull { r ->
        val p = prices[r.sku].orEmpty().trim(); if (p.isEmpty()) return@mapNotNull null
        val q = qtys[r.sku].orEmpty().trim().ifEmpty { WorkisMoney.qty(r.qty).ifEmpty { "1" } }
        buildJsonObject {
            put("sku", JsonPrimitive(r.sku)); put("qty", JsonPrimitive(q)); put("unitPrice", JsonPrimitive(apiNumber(p)))
            // the reference the price came from rides beside it (2026-10-01)
            r.list?.let { lp ->
                put("listPrice", JsonPrimitive(WorkisMoney.plain(lp)))
                discounts[r.sku].orEmpty().trim().takeIf { it.isNotEmpty() }?.let { put("discountPct", JsonPrimitive(apiNumber(it))) }
            }
        }
    })
    // only what was stated
    fun terms(): JsonObject = jsonBody(
        "packaging" to packaging.trim().ifEmpty { null }, "warrantyMonths" to warrantyMonths.trim().toIntOrNull(),
        "warrantyText" to warrantyText.trim().ifEmpty { null }, "returnsText" to returnsText.trim().ifEmpty { null },
    )
    fun body(): JsonObject = jsonBody(
        "lines" to pricedLines(), "vadeDays" to vade, "deliveryMode" to delivery,
        "leadTimeDays" to leadTime.toIntOrNull(), "validDays" to validDays.toIntOrNull(),
        "note" to note.ifBlank { null }, "letter" to letter.ifBlank { null },
        "terms" to terms().takeIf { it.isNotEmpty() },
    )
    // the web's Send guard: packaging and warranty months become the order's terms at award
    val termsMissing = packaging.isBlank() || warrantyMonths.trim().toIntOrNull() == null
    // Σ qty × unit price over the priced rows — a preview, never the amount of record
    val runningTotal: BigDecimal? = rows.mapNotNull { r ->
        val p = WorkisMoney.parse(prices[r.sku]) ?: return@mapNotNull null
        p * (WorkisMoney.parse(qtys[r.sku]) ?: r.qty?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ONE)
    }.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)

    fun signature(): String = body().toString()
    /** A draft is what the seller typed: it wins over the prefill, never over an edit made meanwhile. */
    fun applyDraft(d: JsonObject) {
        (d["lines"] as? JsonArray)?.forEach { el ->
            val l = el as? JsonObject ?: return@forEach
            val sku = l.text("sku")?.takeIf { s -> rows.any { it.sku == s } } ?: return@forEach
            val p = l.text("unit_price", "unitPrice")
            if (p != null) { prices[sku] = p; discounts[sku] = l.text("discount_pct", "discountPct").orEmpty() }
            l.text("qty")?.let { qtys[sku] = it }
        }
        d.text("lead_time_days", "leadTimeDays")?.let { leadTime = it }
        d.text("valid_days", "validDays")?.let { validDays = it }
        d.text("vade_days", "vadeDays")?.toIntOrNull()?.takeIf { it in listOf(0, 30, 45, 60, 90) }?.let { vade = it }
        d.text("delivery_mode", "deliveryMode")?.takeIf { it == "workis_kargo" || it == "satici_teslim" }?.let { delivery = it }
        d.text("note")?.let { note = it }
        d.text("letter")?.let { letter = it }
        (d["terms"] as? JsonObject)?.let { tm ->
            tm.text("packaging")?.let { packaging = it }
            tm.text("warranty_months", "warrantyMonths")?.let { warrantyMonths = it }
            tm.text("warranty_text", "warrantyText")?.let { warrantyText = it }
            tm.text("returns_text", "returnsText")?.let { returnsText = it }
        }
    }
    fun saveDraftNow() {
        val sig = signature()
        if (!draftReady || sent || sig == lastSaved) return
        vm.saveDraft(invitationId, body()) {
            lastSaved = sig; draftTick = true
            scope.launch { delay(2_000); draftTick = false }
        }
    }
    LaunchedEffect(invitationId) {
        val untouched = signature()
        val d = vm.draft(invitationId)
        if (d != null && signature() == untouched) applyDraft(d)
        lastSaved = signature(); draftReady = true
    }
    val sig = signature()
    LaunchedEffect(sig, draftReady) { if (draftReady && !sent) { delay(1_500); saveDraftNow() } }
    DisposableEffect(Unit) { onDispose { saveDraftNow() } }

    val guardText = stringResource(R.string.quote_terms_guard)
    val canSend = pricedLines().isNotEmpty() && !busy
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)

    ConsoleSub(
        title = stringResource(R.string.send_quote), onBack = onClose,
        trailing = {
            Button(
                onClick = {
                    // the web refuses Send without packaging and warranty months — the same here
                    if (termsMissing) { error = guardText; tab = 0 }
                    else { busy = true; error = null; scope.launch { val (ok, msg) = vm.submit(invitationId, body()); busy = false; if (ok) { sent = true; onClose() } else error = msg } }
                },
                enabled = canSend, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.surface, disabledContentColor = colors.faint), modifier = Modifier.height(36.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill) else Text(stringResource(R.string.send_quote), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(inv.caseTitle ?: inv.request.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            Text(inv.buyer?.short.orEmpty() + (inv.buyer?.city?.let { " ($it)" } ?: ""), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            // the request's own term, above the price grid
            DeliveryPlaceLine(inv.deliveryPlace)
            // the autosave tick: icon + word, never colour alone
            AnimatedVisibility(visible = draftTick, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.quote_draft_saved), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SuccessGreen)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.prices_tab), stringResource(R.string.letter_note_tab)).forEachIndexed { i, label ->
                    SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2), colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.surface, activeContentColor = colors.ink, inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted)) { Text(label, fontSize = 13.sp) }
                }
            }
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            if (tab == 0) {
                val discountLabel = stringResource(R.string.discount_pct_field)
                rows.groupBy { it.group.orEmpty() }.toSortedMap().forEach { (g, rs) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow((g.ifEmpty { stringResource(R.string.shortlist_label) }).uppercase())
                        // the buyer's demands for this item, so the seller prices the asked size
                        val groupItem = inv.listItems?.firstOrNull { it.itemName == g }?.takeIf { g.isNotEmpty() }
                        groupItem?.requirements?.takeIf { it.isNotEmpty() }?.let { reqs ->
                            Text(stringResource(R.string.asked_label) + ": " + reqs.joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.ink, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        groupItem?.use?.trim()?.takeIf { it.isNotEmpty() }?.let { use ->
                            Text(stringResource(R.string.use_label) + ": " + use, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        SurfaceCard {
                            rs.forEach { r ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(WorkisTitleCase.productLine(r.name), fontSize = 13.sp, color = colors.ink)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(r.sku, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = qtys[r.sku].orEmpty(), onValueChange = { qtys[r.sku] = it }, placeholder = { Text(stringResource(R.string.qty_field), color = colors.faint, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = fc, textStyle = TextStyle(fontFamily = WorkisMono, fontSize = 13.sp, color = colors.ink).readingStep(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(76.dp))
                                        r.unit?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val list = r.list
                                        if (list != null) {
                                            // the list price, faint; the discount moves the unit price
                                            Text(stringResource(R.string.quote_list_word) + " " + WorkisMoney.text(list.toDouble(), null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, modifier = Modifier.weight(1f))
                                            OutlinedTextField(value = discounts[r.sku].orEmpty(), onValueChange = { setDiscount(r, it) }, placeholder = { Text("%", color = colors.faint, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = fc, textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink).readingStep(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(72.dp).semantics { contentDescription = discountLabel })
                                            Text("%", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                                        } else Spacer(Modifier.weight(1f))
                                        OutlinedTextField(value = prices[r.sku].orEmpty(), onValueChange = { setPrice(r, it) }, placeholder = { Text(stringResource(R.string.unit_price_field), color = colors.faint, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = fc, textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink).readingStep(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(120.dp))
                                    }
                                }
                            }
                        }
                        if (g.isNotEmpty() && rs.size > 1) Text(stringResource(R.string.alts_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                // the quote total follows the typing — a preview of the sum the server will write
                runningTotal?.let { total ->
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.quote_total_label), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
                            Text(WorkisMoney.text(total.toDouble(), null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                        }
                    }
                }
                SurfaceCard {
                    NumberRow(stringResource(R.string.lead_time_field), leadTime, "—") { leadTime = it }
                    NumberRow(stringResource(R.string.valid_days_field), validDays, "30") { validDays = it }
                    PickerRow(stringResource(R.string.vade_field), if (vade == 0) stringResource(R.string.vade_advance) else "$vade " + stringResource(R.string.vade_days_word), listOf(0, 30, 45, 60, 90).map { d -> (if (d == 0) stringResource(R.string.vade_advance) else "$d " + stringResource(R.string.vade_days_word)) to { vade = d } })
                    PickerRow(stringResource(R.string.delivery_field), stringResource(if (delivery == "workis_kargo") R.string.delivery_workis else R.string.delivery_seller), listOf(stringResource(R.string.delivery_workis) to { delivery = "workis_kargo" }, stringResource(R.string.delivery_seller) to { delivery = "satici_teslim" }))
                }
                // born with the quote, frozen at award for both seats — a fresh order opens 7/7;
                // cell exit saves the draft at once (the 1.5 s timer covers the rest)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormSection(stringResource(R.string.quote_terms_title).uppercase()) {
                        FormRow(stringResource(R.string.term_packaging), packaging, { packaging = it }, onCommit = { saveDraftNow() }, tick = false, stacked = true, maxLines = 3)
                        FormRow(stringResource(R.string.term_warranty_months), warrantyMonths, { warrantyMonths = it.filter(Char::isDigit) }, onCommit = { saveDraftNow() }, tick = false, placeholder = "—", keyboardType = KeyboardType.Number, mono = true)
                        FormRow(stringResource(R.string.term_warranty_text), warrantyText, { warrantyText = it }, onCommit = { saveDraftNow() }, tick = false, stacked = true, maxLines = 3)
                        FormRow(stringResource(R.string.term_returns), returnsText, { returnsText = it }, onCommit = { saveDraftNow() }, tick = false, stacked = true, maxLines = 3)
                    }
                    Text(stringResource(R.string.quote_terms_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.term_returns_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Text("🔍 " + stringResource(R.string.preview_pdf), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (canSend) colors.accentText else colors.faint, modifier = Modifier.clickable(enabled = canSend) { busy = true; error = null; scope.launch { val (b, msg) = vm.preview(invitationId, body()); busy = false; if (b != null) previewPdf = b else error = msg } }.padding(horizontal = 4.dp))
                Text(stringResource(R.string.preview_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(stringResource(R.string.letter_title).uppercase())
                    OutlinedTextField(value = letter, onValueChange = { letter = it }, minLines = 4, maxLines = 14, shape = RoundedCornerShape(14.dp), colors = fc, modifier = Modifier.fillMaxWidth())
                    PolishLink(enabled = letter.isNotBlank() && !busy) { busy = true; error = null; scope.launch { val (s, msg) = vm.polish(letter); busy = false; if (s != null) suggestion = "letter" to s else error = msg } }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(stringResource(R.string.note_title).uppercase())
                    OutlinedTextField(value = note, onValueChange = { note = it }, placeholder = { Text(stringResource(R.string.note_placeholder), color = colors.faint, fontSize = 14.sp) }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fc, modifier = Modifier.fillMaxWidth())
                    PolishLink(enabled = note.isNotBlank() && !busy) { busy = true; error = null; scope.launch { val (s, msg) = vm.polish(note); busy = false; if (s != null) suggestion = "note" to s else error = msg } }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    previewPdf?.let { PdfViewerDialog(stringResource(R.string.preview_pdf), data = it, fileName = "teklif-taslak.pdf") { previewPdf = null } }
    suggestion?.let { (target, text) ->
        // Jowi's rewrite — a card, applied only on "Uygula" (§0)
        ModalBottomSheet(onDismissRequest = { suggestion = null }, containerColor = colors.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.polish_title), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
                Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = colors.ink, modifier = Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(14.dp))
                Row { TextButton(onClick = { suggestion = null }) { Text(stringResource(R.string.polish_ignore), color = colors.muted) }; Spacer(Modifier.weight(1f))
                    Button(onClick = { if (target == "letter") letter = text else note = text; suggestion = null }, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.height(44.dp)) { Text(stringResource(R.string.polish_apply), fontWeight = FontWeight.SemiBold) } }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PolishLink(enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text("✦ " + stringResource(R.string.polish_action), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (enabled) colors.accentText else colors.faint, modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp))
}

@Composable
private fun NumberRow(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.muted, modifier = Modifier.weight(1f))
        OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(placeholder, color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(10.dp), textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink).readingStep(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = colors.blue), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
    }
}

@Composable
private fun PickerRow(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = colors.muted, modifier = Modifier.weight(1f))
            Text(value, fontSize = 14.sp, color = colors.ink)
            Text(" ⌄", color = colors.faint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (l, act) -> DropdownMenuItem(text = { Text(l, color = colors.ink, fontSize = 14.sp) }, onClick = { open = false; act() }) }
        }
    }
}
