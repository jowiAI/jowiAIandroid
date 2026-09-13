package ai.workis.jowi.ui.screens.partner

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
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
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class QuotesViewModel : ViewModel() {
    var invitations by mutableStateOf<List<SellerInvitation>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.sellerInvitations() }
            if (r != null) { invitations = r.invitations.orEmpty(); error = null } else error = err
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

    suspend fun polish(text: String): Pair<String?, String?> {
        val (r, err) = partnerCall(lang) { Graph.api.textPolish(TextBody(text)) }
        val s = r?.suggestion?.takeIf { it.isNotEmpty() }
        return s to (if (s == null) (r?.error ?: err ?: statusText()) else null)
    }

    private fun statusText() = ai.workis.jowi.data.statusMessage(-1, lang)
}

/** Teklifler — one card per invitation; ONE of: send quote / the sent quote / the won order. */
@Composable
fun QuotesScreen(vm: QuotesViewModel, onCompose: (String) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    var pdf by remember { mutableStateOf<Pair<String, String>?>(null) } // url to title
    var reviseFor by remember { mutableStateOf<SellerInvitation?>(null) }
    val rows = vm.invitations
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.quotes_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        item { NoteBand(vm.note) }
        when {
            rows != null -> {
                if (rows.isEmpty()) item { Text(stringResource(R.string.quotes_empty), fontSize = 13.sp, color = colors.faint) }
                items(rows, key = { it.id ?: it.hashCode() }) { inv ->
                    InvitationCard(inv, onCompose = { inv.id?.let(onCompose) }, onPdf = { url, title -> pdf = url to title }, onRevise = { reviseFor = inv })
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

@Composable
private fun InvitationCard(inv: SellerInvitation, onCompose: () -> Unit, onPdf: (String, String) -> Unit, onRevise: () -> Unit) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(inv.caseTitle ?: inv.request ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink, maxLines = 2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            StatusPill(inv.statusLabel ?: inv.status.orEmpty(), if (inv.isOpen) colors.accentText else colors.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(inv.buyer?.short.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = colors.accentText)
            inv.buyer?.city?.takeIf { it.isNotEmpty() }?.let { Text("($it)", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
            Spacer(Modifier.weight(1f))
            inv.createdAt?.let { Text(it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
        }
        val items = inv.listItems.orEmpty()
        if (items.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEach { li ->
                Row { Text(li.itemName.orEmpty(), fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f)); Text(WorkisMoney.qty(li.qty) + " " + li.unit.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
                li.alts?.takeIf { it.isNotEmpty() }?.let { a -> Text("↳ " + a.mapNotNull { it.name ?: it.sku }.joinToString(" · "), fontSize = 11.sp, color = colors.faint) }
            }
        } else inv.shortlist?.takeIf { it.isNotEmpty() }?.let { sl ->
            Text(stringResource(R.string.shortlist_label) + ": " + sl.mapNotNull { it.name ?: it.sku }.joinToString(" · "), fontSize = 11.sp, color = colors.faint)
        }
        val q = inv.quote
        when {
            q != null -> QuoteBlock(q, inv, onPdf, onRevise)
            inv.isOpen -> Button(onClick = onCompose, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("✈ " + stringResource(R.string.send_quote), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        inv.order?.let { OrderBlock(it, onPdf) }
        if (inv.lost == true) StatusPill(stringResource(R.string.order_lost))
        inv.rfqPdfUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            val title = stringResource(R.string.rfq_pdf)
            Text("📄 $title", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { onPdf(url, title) })
        }
    }
}

@Composable
private fun QuoteBlock(q: SellerQuote, inv: SellerInvitation, onPdf: (String, String) -> Unit, onRevise: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✓ " + stringResource(R.string.quote_sent), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = SuccessGreen)
            q.number?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
            Spacer(Modifier.weight(1f))
            q.pdfUrl?.takeIf { it.isNotEmpty() }?.let { url -> val t = stringResource(R.string.quote_pdf); Text("↗ $t", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { onPdf(url, t) }) }
        }
        q.lines.orEmpty().forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.name ?: l.sku.orEmpty(), fontSize = 13.sp, color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                Text(WorkisMoney.qty(l.qty) + " × " + WorkisMoney.text(l.unitPrice, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                Spacer(Modifier.width(8.dp))
                Text(WorkisMoney.text(l.amount, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    q.leadTimeDays?.let { "$it " + stringResource(R.string.vade_days_word) + " " + stringResource(R.string.lead_time_label) },
                    q.validUntil?.let { stringResource(R.string.valid_until_label) + " " + it.take(10) },
                    q.vadeDays?.let { if (it == 0) stringResource(R.string.vade_advance) else "$it " + stringResource(R.string.vade_days_word) },
                ).joinToString(" · "),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.total_label) + ": " + WorkisMoney.text(q.total ?: q.price, q.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        }
        if (inv.order == null && inv.lost != true && inv.rfqStatus != "awarded") {
            Text(stringResource(R.string.quote_revise), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable(onClick = onRevise))
        }
    }
}

@Composable
private fun OrderBlock(o: SellerOrder, onPdf: (String, String) -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().background(BeigeBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.order_won).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = SuccessGreen)
            o.poNumber?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.ink) }
            Spacer(Modifier.weight(1f))
            Text(WorkisMoney.text(o.total, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        }
        Text(listOfNotNull((o.escrowStateLabel ?: o.escrowState)?.let { "escrow: $it" }, o.shipment?.let { "🚚 ${it.number.orEmpty()} ${it.statusLabel ?: it.status.orEmpty()}" }).joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        o.poPdfUrl?.takeIf { it.isNotEmpty() }?.let { url -> val t = stringResource(R.string.po_pdf); Text("📄 $t", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { onPdf(url, t) }) }
    }
}

// ---------- Composer ----------

private data class PriceRow(val sku: String, val name: String, val group: String?, val qty: Double?, val unit: String?)

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
        if (li.isNotEmpty()) li.flatMap { item -> item.alts.orEmpty().mapNotNull { a -> a.sku?.let { PriceRow(it, a.name ?: it, item.itemName, item.qty, item.unit) } } }
        else inv.shortlist.orEmpty().mapNotNull { p -> p.sku?.let { PriceRow(it, p.name ?: it, null, inv.qty, null) } }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    val prices = remember { mutableStateMapOf<String, String>() }
    val qtys = remember { mutableStateMapOf<String, String>().apply { rows.forEach { put(it.sku, WorkisMoney.qty(it.qty)) } } }
    var leadTime by rememberSaveable { mutableStateOf("") }
    var validDays by rememberSaveable { mutableStateOf("30") }
    var vade by rememberSaveable { mutableStateOf(0) }
    var delivery by rememberSaveable { mutableStateOf("workis_kargo") }
    var note by rememberSaveable { mutableStateOf("") }
    var letter by rememberSaveable(inv.id) { mutableStateOf(inv.defaultLetter.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewPdf by remember { mutableStateOf<ByteArray?>(null) }
    var suggestion by remember { mutableStateOf<Pair<String, String>?>(null) } // target to text

    fun pricedLines(): JsonArray = JsonArray(rows.mapNotNull { r ->
        val p = prices[r.sku].orEmpty().trim(); if (p.isEmpty()) return@mapNotNull null
        val q = qtys[r.sku].orEmpty().trim().ifEmpty { WorkisMoney.qty(r.qty).ifEmpty { "1" } }
        buildJsonObject { put("sku", JsonPrimitive(r.sku)); put("qty", JsonPrimitive(q)); put("unitPrice", JsonPrimitive(p)) }
    })
    fun body(): JsonObject = jsonBody(
        "lines" to pricedLines(), "vadeDays" to vade, "deliveryMode" to delivery,
        "leadTimeDays" to leadTime.toIntOrNull(), "validDays" to validDays.toIntOrNull(),
        "note" to note.ifBlank { null }, "letter" to letter.ifBlank { null },
    )
    val canSend = pricedLines().isNotEmpty() && !busy
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue)

    ConsoleSub(
        title = stringResource(R.string.send_quote), onBack = onClose,
        trailing = {
            Button(onClick = { busy = true; error = null; scope.launch { val (ok, msg) = vm.submit(invitationId, body()); busy = false; if (ok) onClose() else error = msg } }, enabled = canSend, colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.surface, disabledContentColor = colors.faint), modifier = Modifier.height(36.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill) else Text(stringResource(R.string.send_quote), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(inv.caseTitle ?: inv.request.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            Text(inv.buyer?.short.orEmpty() + (inv.buyer?.city?.let { " ($it)" } ?: ""), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.prices_tab), stringResource(R.string.letter_note_tab)).forEachIndexed { i, label ->
                    SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2), colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.surface, activeContentColor = colors.ink, inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted)) { Text(label, fontSize = 13.sp) }
                }
            }
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            if (tab == 0) {
                rows.groupBy { it.group.orEmpty() }.toSortedMap().forEach { (g, rs) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow((g.ifEmpty { stringResource(R.string.shortlist_label) }).uppercase())
                        SurfaceCard {
                            rs.forEach { r ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(r.name, fontSize = 13.sp, color = colors.ink)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(r.sku, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = qtys[r.sku].orEmpty(), onValueChange = { qtys[r.sku] = it }, placeholder = { Text(stringResource(R.string.qty_field), color = colors.faint, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = fc, textStyle = TextStyle(fontFamily = WorkisMono, fontSize = 13.sp, color = colors.ink), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(76.dp))
                                        r.unit?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint) }
                                        OutlinedTextField(value = prices[r.sku].orEmpty(), onValueChange = { prices[r.sku] = it }, placeholder = { Text(stringResource(R.string.unit_price_field), color = colors.faint, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = fc, textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(120.dp))
                                    }
                                }
                            }
                        }
                        if (g.isNotEmpty() && rs.size > 1) Text(stringResource(R.string.alts_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                SurfaceCard {
                    NumberRow(stringResource(R.string.lead_time_field), leadTime, "—") { leadTime = it }
                    NumberRow(stringResource(R.string.valid_days_field), validDays, "30") { validDays = it }
                    PickerRow(stringResource(R.string.vade_field), if (vade == 0) stringResource(R.string.vade_advance) else "$vade " + stringResource(R.string.vade_days_word), listOf(0, 30, 45, 60, 90).map { d -> (if (d == 0) stringResource(R.string.vade_advance) else "$d " + stringResource(R.string.vade_days_word)) to { vade = d } })
                    PickerRow(stringResource(R.string.delivery_field), stringResource(if (delivery == "workis_kargo") R.string.delivery_workis else R.string.delivery_seller), listOf(stringResource(R.string.delivery_workis) to { delivery = "workis_kargo" }, stringResource(R.string.delivery_seller) to { delivery = "satici_teslim" }))
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
        OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(placeholder, color = colors.faint) }, singleLine = true, shape = RoundedCornerShape(10.dp), textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = colors.blue), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
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
