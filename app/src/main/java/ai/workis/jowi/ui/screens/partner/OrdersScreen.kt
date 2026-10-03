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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CarrierOption
import ai.workis.jowi.data.CarrierOptions
import ai.workis.jowi.data.DispatchBody
import ai.workis.jowi.data.OrderReadyBody
import ai.workis.jowi.data.PartnerOrder
import ai.workis.jowi.data.ReviewBody
import ai.workis.jowi.data.ServiceLevelBody
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.orderedLevels
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Siparişler — one card per order per seat the firm holds (Django §5). The
 * client derives nothing: the escrow word, the terms badge and the contract
 * badge all arrive decided. A carrier card shows no amount at all (§7.1) —
 * never a zero.
 */
class OrdersViewModel : ViewModel() {
    var orders by mutableStateOf<List<PartnerOrder>?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    /** 0 Aktif · 1 Tamamlananlar — kept here so it survives a visit to the stage. */
    var segment by mutableStateOf(0)
    private val lang get() = Graph.language.value

    val unreadTotal get() = orders.orEmpty().sumOf { it.unread ?: 0 }

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.orders() }
            if (r != null) { orders = r.orders.orEmpty(); loadError = null } else if (orders == null) loadError = err
        }
    }

    /**
     * One door at a time. The sentence lands in this screen's note — or, when a
     * caller passes `onResult` (the stage screen), with that caller instead.
     */
    private fun act(onResult: ((Boolean, String?) -> Unit)? = null, block: suspend () -> Pair<Boolean, String?>) {
        if (busy) return
        busy = true
        if (onResult == null) note = null
        viewModelScope.launch {
            val (ok, msg) = block()
            busy = false
            if (onResult == null) note = msg else onResult(ok, msg)
            if (ok) load()
        }
    }

    fun fund(id: String, onResult: ((Boolean, String?) -> Unit)? = null) = act(onResult) {
        val (r, err) = partnerCall(lang) { Graph.api.orderFund(id) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun lineReceived(lineId: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.orderLineReceived(lineId) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    // the line doors Onaylar already calls — the stage's `lines` job posts to the same ones,
    // and the server drops the system line into the order's yazışma itself
    private suspend fun simple(call: suspend () -> ai.workis.jowi.data.SimpleResult): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang, call)
        return (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun lineConfirm(lineId: String, onResult: ((Boolean, String?) -> Unit)? = null) =
        act(onResult) { simple { Graph.api.lineConfirm(lineId) } }

    fun lineObjection(lineId: String, reason: String, onResult: ((Boolean, String?) -> Unit)? = null) =
        act(onResult) { simple { Graph.api.lineObjection(lineId, ai.workis.jowi.data.jsonBody("reason" to reason)) } }

    fun lineObjectionWithdraw(lineId: String, onResult: ((Boolean, String?) -> Unit)? = null) =
        act(onResult) { simple { Graph.api.lineObjectionWithdraw(lineId) } }

    /**
     * The dispatch doors answer without `success`: a 2xx is the yes and its
     * `message` the note; a refusal is the server's sentence, kept in the sheet.
     */
    private suspend fun door(call: suspend () -> ai.workis.jowi.data.OrderDoorResult): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang) { call() }
        val ok = r != null && r.success != false
        return ok to (if (ok) r?.message else (r?.error ?: r?.message ?: err))
    }

    /** One door at a time from a sheet; `onResult(ok, sentence)` — ok closes the sheet and reloads. */
    private fun sheetDoor(onResult: (Boolean, String?) -> Unit, call: suspend () -> ai.workis.jowi.data.OrderDoorResult) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (ok, msg) = door(call)
            busy = false
            if (ok) { note = msg; load() }
            onResult(ok, msg)
        }
    }

    /** Seller: "Kargoya teslim" on `readyDate` ('YYYY-MM-DD'). */
    fun ready(id: String, readyDate: String, onResult: (Boolean, String?) -> Unit) =
        sheetDoor(onResult) { Graph.api.orderReady(id, OrderReadyBody(readyDate)) }

    /**
     * Since 2026-10-02 this no longer writes the level: it STATES the cargo term as
     * the buyer's proposal (the seller confirms; under a contract in force it is an
     * amendment). The reply `{success, fee, message}` — `message` is the note.
     */
    fun serviceLevel(id: String, level: String, onResult: (Boolean, String?) -> Unit) =
        sheetDoor(onResult) { Graph.api.orderServiceLevel(id, ServiceLevelBody(level)) }

    fun dispatch(id: String, carrierId: String, level: String, onResult: (Boolean, String?) -> Unit) =
        sheetDoor(onResult) { Graph.api.orderDispatch(id, DispatchBody(carrierId, level)) }

    suspend fun carrierOptions(id: String): Pair<CarrierOptions?, String?> =
        partnerCall(lang) { Graph.api.orderCarrierOptions(id) }

    /** `savedWord` is the catalogue's "Your rating was saved." — the caller reads it. */
    fun review(shipmentId: String, rating: Int, comment: String, savedWord: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.shipmentReview(shipmentId, ReviewBody(rating, comment.ifBlank { null })) }
        (r?.success == true) to (if (r?.success == true) (r.message ?: savedWord) else (r?.error ?: err))
    }
}

@Composable
fun OrdersScreen(
    vm: OrdersViewModel,
    onBack: () -> Unit,
    onThread: (PartnerOrder) -> Unit,
    onContract: (PartnerOrder) -> Unit,
    onStage: (PartnerOrder) -> Unit,
) {
    val colors = WorkisTheme.colors
    var reviewFor by remember { mutableStateOf<PartnerOrder?>(null) }
    var confirmFund by remember { mutableStateOf<PartnerOrder?>(null) }
    var readyFor by remember { mutableStateOf<PartnerOrder?>(null) }
    var cargoFor by remember { mutableStateOf<PartnerOrder?>(null) }
    /** A token-walled PDF door: (title, url, file name). */
    var pdfDoor by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(Unit) { ai.workis.jowi.data.ScreenContext.setPage("orders"); vm.load() } // Jowi's page (2026-09-19)
    ConsoleSub(stringResource(R.string.orders_screen_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        val orders = vm.orders
        when {
            orders == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            orders == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // the other party moved an order (`changes/`, 2026-10-03)
                ChangesWatch(scope = "orders") { vm.load() }
                NoteBand(vm.note)
                if (orders.isEmpty()) Text(stringResource(R.string.orders_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
                else {
                    // Aktif / Tamamlananlar — the split is the server's `done` (an older
                    // server sends none: every order then reads as active)
                    val doneCount = orders.count { it.done == true }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            stringResource(R.string.order_seg_active) + " (${orders.size - doneCount})",
                            stringResource(R.string.order_seg_done) + " ($doneCount)",
                        ).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = vm.segment == i, onClick = { vm.segment = i },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.surface, activeContentColor = colors.ink,
                                    inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted,
                                ),
                            ) { Text(label, fontSize = 13.sp) }
                        }
                    }
                }
                val shown = orders.filter { (it.done == true) == (vm.segment == 1) }
                if (orders.isNotEmpty() && shown.isEmpty()) {
                    Text(
                        stringResource(if (vm.segment == 1) R.string.order_done_empty else R.string.order_active_empty),
                        fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp),
                    )
                }
                shown.forEach { o ->
                    OrderCard(
                        o, vm,
                        onStage = { onStage(o) },
                        onThread = { onThread(o) },
                        onContract = { onContract(o) },
                        onFund = { confirmFund = o },
                        onReview = { reviewFor = o },
                        onReady = { readyFor = o },
                        onCargo = { cargoFor = o },
                        onPdf = { pdfDoor = it },
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    confirmFund?.let { o ->
        ConfirmDialog(stringResource(R.string.order_fund), stringResource(R.string.order_fund_confirm), destructive = false, onDismiss = { confirmFund = null }) {
            o.id?.let { vm.fund(it) }
        }
    }
    reviewFor?.let { o -> ReviewSheet(o, vm) { reviewFor = null } }
    readyFor?.let { o -> ReadySheet(o, vm.busy, onReady = { day, done -> o.id?.let { vm.ready(it, day, done) } }) { readyFor = null } }
    cargoFor?.let { o -> CargoLevelSheet(o, vm) { cargoFor = null } }
    pdfDoor?.let { (title, url, file) -> PdfViewerDialog(title, url = url, fileName = file) { pdfDoor = null } }
}

@Composable
private fun OrderCard(
    o: PartnerOrder,
    vm: OrdersViewModel,
    onStage: () -> Unit,
    onThread: () -> Unit,
    onContract: () -> Unit,
    onFund: () -> Unit,
    onReview: () -> Unit,
    onReady: () -> Unit,
    onCargo: () -> Unit,
    onPdf: (Triple<String, String, String>) -> Unit,
) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            // the title row is the door to the order's stage (2026-10-02)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable(enabled = !o.id.isNullOrEmpty(), onClick = onStage),
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(o.po.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                    Text(
                        stringResource(
                            when (o.role) {
                                "seller" -> R.string.order_role_seller
                                "carrier" -> R.string.order_role_carrier
                                else -> R.string.order_role_buyer
                            },
                        ) + (o.at?.take(10)?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.order_stage_title),
                    tint = colors.faint, modifier = Modifier.size(18.dp),
                )
            }
            // a carrier card carries no amount at all — never a zero
            if (o.isCarrier) {
                Text(stringResource(R.string.order_no_money_for_carrier), fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint, modifier = Modifier.weight(1f))
            } else {
                Text(WorkisMoney.text(o.total, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            }
        }
        // the payment word is a legal fact and is NEVER cut: it takes its own
        // line under the amount rather than an ellipsis beside it (seen on a
        // narrow screen as "Güvenceli ödeme yok (açık hes…", 2026-09-19)
        if (!o.isCarrier) {
            o.escrowLabel?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp,
                    color = colors.muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // the parties, as the server names them
        Text(
            listOfNotNull(
                o.buyer?.short?.takeIf { it.isNotEmpty() },
                o.seller?.short?.takeIf { it.isNotEmpty() },
                o.shipment?.carrier?.short?.takeIf { it.isNotEmpty() },
            ).joinToString(" → "),
            fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        // one order card, as the web's (2026-09-30): how it ships, how many lines
        val mode = o.deliveryMode?.takeIf { it.isNotEmpty() }
        val nLines = o.nLines ?: 0
        if (mode != null || nLines > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mode?.let { dm ->
                    WorkisChip(
                        ChipSpec(
                            WorkisIcons.ShippingBox,
                            listOfNotNull(
                                stringResource(if (dm == "satici_teslim") R.string.delivery_seller else R.string.delivery_workis),
                                o.serviceLevel?.takeIf { it.isNotEmpty() }?.let { cargoLevelWord(it) },
                            ).joinToString(" · "),
                            colors.muted,
                        ),
                    )
                }
                if (nLines > 0) WorkisChip(ChipSpec(WorkisIcons.Checklist, "$nLines " + stringResource(R.string.order_lines_word), colors.muted))
            }
        }
        o.shipment?.let { s ->
            // `promised` is the agreed delivery date (18:00 that day, 2026-10-02) — drawn as sent
            Text(
                listOfNotNull(
                    s.statusLabel?.takeIf { it.isNotEmpty() },
                    s.promised?.take(10)?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.order_promised) + " $it" },
                    s.trackingNo?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.order_tracking) + " $it" },
                ).joinToString(" · "),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint,
            )
            // the receiver's delivery code (Senaryo 4.25), read to the carrier at
            // the door — it arrives on the receiver's card only, so it is drawn
            // wherever it arrives and never derived
            s.code?.let { code ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(WorkisIcons.Key, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.order_delivery_code) + ":", fontSize = 12.sp, color = colors.muted)
                    Spacer(Modifier.width(6.dp))
                    SelectionContainer {
                        Text(code, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                    }
                }
            }
        }
        // the virtual supplier's lines — "I received it" per line
        o.vlines.orEmpty().forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    listOfNotNull(l.label, l.qty?.let { "× ${WorkisMoney.qty(it)}" }).joinToString(" "),
                    fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (l.received == true) {
                    Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                } else {
                    Text(
                        stringResource(R.string.order_line_received), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                        modifier = Modifier.clickable(enabled = !vm.busy) { l.id?.let { vm.lineReceived(it) } },
                    )
                }
            }
        }
        // where the order stands (the server's walk) and THE one sentence under it
        o.steps?.takeIf { it.isNotEmpty() }?.let { OrderWalk(it) }
        o.next?.let { OrderNextLine(it) }
        // Each state stands over the door it belongs to — the unread count on
        // the correspondence row, the terms under it, the contract's standing
        // under its own row. In the card's corner they said nothing about
        // where to go (user finding, 2026-09-19).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onThread)) {
                Icon(WorkisIcons.Bubble, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.thread_open), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                Spacer(Modifier.weight(1f))
                // unread is a NOTICE, not an error: a bare Badge takes the scheme's
                // error red (and an `onError` the chart never maps). The mark colour
                // carries it, with the AA pairing the ramp prescribes.
                (o.unread ?: 0).takeIf { it > 0 }?.let {
                    Badge(containerColor = Kiremit500, contentColor = OnKiremitFill) { Text("$it", fontSize = 10.sp) }
                }
            }
            o.terms?.let { t ->
                val ok = t.ok ?: 0
                val total = t.total ?: 0
                // the ONE term asked of this seat now ("Teslim tarihi · 2/7 · belirtin"),
                // else "5/7 koşul kesinleşti" — settled / total, in words
                val asked = currentTermLine(t.current)
                Text(
                    asked ?: ("$ok/$total " + stringResource(R.string.order_terms_settled)),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    color = if (asked == null && total > 0 && ok >= total) SuccessGreen else Beige,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
        // the contract door carries its own standing; a carrier signs nothing
        if (!o.isCarrier) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onContract)) {
                    Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.contract_open), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                }
                val c = o.contract
                Text(
                    when {
                        c == null -> stringResource(R.string.order_contract_after_terms)
                        c.status == "accepted" -> stringResource(R.string.order_contract_accepted)
                        else -> stringResource(R.string.order_contract_draft) + " · v${c.version ?: 1}"
                    },
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    color = if (c?.status == "accepted") SuccessGreen else colors.faint,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
        // Seller: "Kargoya teslim" opens the job to the carrier pool — ONLY on the
        // server's `canReady`; while the door is closed its `readyHint` stands in
        // its place, the server's sentence (like `fundHint`)
        if (o.canReady == true) {
            OrderDoorRow(WorkisIcons.ShippingBox, stringResource(R.string.order_ready_action), enabled = !vm.busy, onClick = onReady)
        } else o.readyHint?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
        }
        // Buyer: the cargo level (and the manual carrier pick) until dispatch starts
        if (o.canPickCargo == true) {
            OrderDoorRow(WorkisIcons.Clock, stringResource(R.string.cargo_level_action), enabled = !vm.busy, onClick = onCargo)
        }
        val poTitle = stringResource(R.string.order_po_form_pdf)
        if (o.poPdf == true && !o.id.isNullOrEmpty()) {
            OrderDoorRow(WorkisIcons.DocText, poTitle) {
                onPdf(Triple(poTitle, WORKIS_BASE_URL + "workis/orders/${o.id}/po-pdf/", (o.po?.takeIf { it.isNotEmpty() } ?: "siparis") + ".pdf"))
            }
        }
        // The label: the seller prints it, the buyer may read it. The carrier's
        // own labels live on its Kargo screen, so its card skips the door.
        val labelTitle = stringResource(R.string.shipment_label_pdf)
        val shipmentId = o.shipment?.id?.takeIf { it.isNotEmpty() }
        if (!o.isCarrier && shipmentId != null) {
            OrderDoorRow(WorkisIcons.Printer, labelTitle) {
                onPdf(Triple(labelTitle, WORKIS_BASE_URL + "workis/shipments/$shipmentId/label/", (o.shipment.number?.takeIf { it.isNotEmpty() } ?: "etiket") + ".pdf"))
            }
        }
        if (o.canReview == true && o.myRating == null && o.shipment?.id != null) {
            Text(
                stringResource(R.string.order_rate_carrier), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                modifier = Modifier.clickable(onClick = onReview),
            )
        }
        // why the block is not on offer — the server's sentence, never ours
        o.fundHint?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
        }
        // the one tinted action a card may carry: escrow the total. The settled
        // payment term decides, not the escrow word — a missing flag is a shut
        // door (a vade order was offering "Ödemeyi blokla" on 2026-09-19).
        if (o.canFund == true && !o.isCarrier) {
            androidx.compose.material3.Button(
                onClick = onFund,
                enabled = !vm.busy,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.order_fund), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** 1–5 stars and an optional comment — the buyer or the seller rates once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(order: PartnerOrder, vm: OrdersViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.order_rate_carrier), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                val savedWord = stringResource(R.string.order_rating_saved)
                TextButton(onClick = {
                    order.shipment?.id?.let { vm.review(it, rating, comment, savedWord) }
                    onClose()
                }) { Text(stringResource(R.string.done), color = colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { n ->
                    Text(
                        if (n <= rating) "★" else "☆",
                        fontSize = 28.sp, color = if (n <= rating) Beige else colors.faint,
                        modifier = Modifier.clickable { rating = n }.padding(horizontal = 2.dp),
                    )
                }
            }
            FormSection(null) {
                FormRow("", comment, { comment = it }, {}, false, placeholder = stringResource(R.string.order_comment), stacked = true, minLines = 2, maxLines = 5, keyboardType = KeyboardType.Text)
            }
        }
    }
}

/** A door on the card: icon + words in the accent ink, like the thread and contract rows. */
@Composable
internal fun OrderDoorRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
    }
}

/** The service-level code in words (`shipping.SERVICE_LABELS`); an unknown code as the server sent it. */
@Composable
private fun cargoLevelWord(code: String): String = when (code) {
    "acil45" -> stringResource(R.string.cargo_level_acil45)
    "ayni_gun" -> stringResource(R.string.cargo_level_ayni_gun)
    "ertesi_gun" -> stringResource(R.string.cargo_level_ertesi_gun)
    else -> code
}

/** The sheet's title row: the title, then a spinner while a door is in flight. */
@Composable
private fun SheetTitle(title: String, busy: Boolean, trailing: @Composable () -> Unit = {}) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500) else trailing()
    }
}

/**
 * Seller: "Kargoya teslim" — the ready day (today … +30 days), then the job
 * opens to the carrier pool. The reply's `message` becomes the screen note; a
 * refusal stays in the sheet in the server's words. From the stage's `ready`
 * job (2026-10-02) it also carries the agreed delivery date, the last day the
 * goods can be handed to cargo and still arrive on it, and whether that passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadySheet(
    order: PartnerOrder,
    busy: Boolean,
    deliveryDate: String? = null,
    handover: String? = null,
    handoverOk: Boolean? = null,
    /** ('YYYY-MM-DD', result) — the door; `result(ok, sentence)` closes the sheet on ok. */
    onReady: (String, (Boolean, String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val today = remember { LocalDate.now() }
    val last = remember { today.plusDays(30) }
    var day by remember { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val failWord = stringResource(R.string.st_error)
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(stringResource(R.string.order_ready_action), busy) {
                TextButton(onClick = {
                    if (order.id == null) return@TextButton
                    errorText = null
                    // 'YYYY-MM-DD', the calendar day as picked
                    onReady(day.toString()) { ok, msg -> if (ok) onClose() else errorText = msg ?: failWord }
                }) { Text(stringResource(R.string.order_ready_action), color = colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            Text(order.po.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            FormSection(null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(WorkisIcons.Calendar, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.order_ready_date), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(shortDate(day.toString()), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.accentText)
                }
                deliveryDate?.takeIf { it.isNotEmpty() }?.let {
                    StageFact(stringResource(R.string.stage_delivery_date), shortDate(it), Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                handover?.takeIf { it.isNotEmpty() }?.let {
                    StageFact(stringResource(R.string.stage_handover), shortDate(it), Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            if (!handover.isNullOrEmpty() && handoverOk == false) HandoverLate()
            Text(stringResource(R.string.order_ready_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
            errorText?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
        }
    }
    if (picking) {
        // the picker speaks UTC midnights; the day is a calendar day, so UTC both ways
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isBefore(today) && !d.isAfter(last)
                }
                override fun isSelectableYear(year: Int): Boolean = year in today.year..last.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(stringResource(R.string.done), color = colors.accentText, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        ) { DatePicker(state) }
    }
}

/**
 * Buyer: the cargo level (`service-level/`) from the platform tariff, and —
 * the fallback path, secondary — the carrier picked by hand (`dispatch/`).
 * Money is neutral here: fees in mono ink (a JSON number → WorkisMoney), no
 * coloured control. A refusal stays in the sheet in the server's words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CargoLevelSheet(order: PartnerOrder, vm: OrdersViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var data by remember { mutableStateOf<CarrierOptions?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var manualOpen by remember { mutableStateOf(false) }
    var carrierId by remember { mutableStateOf<String?>(null) }
    var manualLevel by remember { mutableStateOf("") }
    val failWord = stringResource(R.string.st_error)
    val id = order.id.orEmpty()
    LaunchedEffect(id) {
        val (r, err) = vm.carrierOptions(id)
        if (r != null) data = r else loadError = err ?: failWord
    }
    val done: (Boolean, String?) -> Unit = { ok, msg -> if (ok) onClose() else errorText = msg ?: failWord }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(stringResource(R.string.cargo_level_action), vm.busy)
            val d = data
            when {
                d != null -> {
                    Text(
                        "${d.pickupDistrict?.takeIf { it.isNotEmpty() } ?: "—"} → ${d.dropoffDistrict?.takeIf { it.isNotEmpty() } ?: "—"}",
                        fontSize = 14.sp, color = colors.ink,
                    )
                    errorText?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
                    // the tariff: the first option that states a level carries it
                    val options = d.options.orEmpty()
                    val seen = linkedMapOf<String, Pair<Double, String?>>()
                    options.forEach { o -> o.fees.forEach { (k, v) -> if (k !in seen) seen[k] = v to o.currency } }
                    FormSection(stringResource(R.string.cargo_levels_header)) {
                        if (seen.isEmpty()) {
                            Text(stringResource(R.string.cargo_no_carriers), fontSize = 13.sp, color = colors.muted, modifier = Modifier.padding(14.dp))
                        }
                        orderedLevels(seen.keys).forEach { code ->
                            val (fee, cur) = seen.getValue(code)
                            LevelRow(cargoLevelWord(code), WorkisMoney.text(fee, cur), current = order.serviceLevel == code, enabled = !vm.busy) {
                                errorText = null
                                vm.serviceLevel(id, code, done)
                            }
                        }
                    }
                    Text(stringResource(R.string.cargo_levels_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                    if (options.isNotEmpty()) {
                        FormSection(stringResource(R.string.cargo_carriers_header)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { manualOpen = !manualOpen }.padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(stringResource(R.string.cargo_pick_carrier), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
                                Text(if (manualOpen) "▴" else "▾", fontSize = 13.sp, color = colors.faint)
                            }
                            if (manualOpen) {
                                options.forEach { o ->
                                    CarrierRow(o, picked = carrierId != null && carrierId == o.carrierId) {
                                        carrierId = o.carrierId; manualLevel = ""
                                    }
                                }
                                val chosen = options.firstOrNull { carrierId != null && it.carrierId == carrierId }
                                val fees = chosen?.fees.orEmpty()
                                if (chosen != null && fees.isNotEmpty()) {
                                    orderedLevels(fees.keys).forEach { code ->
                                        LevelRow(
                                            cargoLevelWord(code), WorkisMoney.text(fees.getValue(code), chosen.currency),
                                            current = manualLevel == code, enabled = !vm.busy,
                                        ) { manualLevel = code }
                                    }
                                    TextButton(
                                        onClick = {
                                            val cid = carrierId ?: return@TextButton
                                            errorText = null
                                            vm.dispatch(id, cid, manualLevel, done)
                                        },
                                        enabled = manualLevel.isNotEmpty() && !vm.busy,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.cargo_dispatch_action), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                            color = if (manualLevel.isNotEmpty() && !vm.busy) colors.accentText else colors.faint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                loadError != null -> Text(loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
                }
            }
        }
    }
}

/** A tariff row: level words · fee in mono ink · a check on the one in force (a glyph, not a colour). */
@Composable
private fun LevelRow(word: String, fee: String, current: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(word, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Text(fee, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        if (current) {
            Spacer(Modifier.width(8.dp))
            Icon(WorkisIcons.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
        }
    }
}

/** short · city · rating · n reviews — the carrier as the buyer picks it. */
@Composable
private fun CarrierRow(o: CarrierOption, picked: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.short?.takeIf { it.isNotEmpty() } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
            val rating = o.rating?.let { "★ " + String.format(java.util.Locale.ROOT, "%.1f", it) + " · ${o.nReviews ?: 0} " + stringResource(R.string.cargo_reviews_word) }
            val line = listOfNotNull(o.city?.takeIf { it.isNotEmpty() }, rating).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, fontSize = 12.sp, color = colors.muted)
        }
        if (picked) Icon(WorkisIcons.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
    }
}
