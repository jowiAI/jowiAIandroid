package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.OrderCurrentTerm
import ai.workis.jowi.data.OrderJob
import ai.workis.jowi.data.OrderJobLine
import ai.workis.jowi.data.OrderNext
import ai.workis.jowi.data.OrderStep
import ai.workis.jowi.data.PartnerOrder
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sipariş — ONE order as the web stage draws it (`GET orders/{id}/stage/`, Django
 * `order_seat.stage_for`, doc `WORKIS_ORDER_STAGE.md`, 2026-10-02). The walk on
 * top, then each step with its line, its note and its dated movements; under the
 * live step, the job it asks of this seat, posted to the door that already holds
 * the rule (the doors live on `OrdersViewModel`). The app draws the server's
 * sentence and flags — it never derives a step.
 */
class OrderStageViewModel : ViewModel() {
    var order by mutableStateOf<PartnerOrder?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)

    fun load(id: String) {
        viewModelScope.launch {
            val (r, err) = partnerCall(Graph.language.value) { Graph.api.orderStage(id) }
            val o = r?.order
            // a 404 (missing, or not this firm's order) arrives as the server's sentence
            if (o != null) { order = o; loadError = null } else if (order == null) loadError = r?.error ?: err
        }
    }
}

@Composable
fun OrderStageScreen(
    orderId: String,
    doors: OrdersViewModel,
    onBack: () -> Unit,
    onThread: (PartnerOrder) -> Unit,
    onContract: (PartnerOrder) -> Unit,
) {
    val vm: OrderStageViewModel = viewModel(key = "stage-$orderId")
    val colors = WorkisTheme.colors
    var confirmFund by remember { mutableStateOf(false) }
    var readyOpen by remember { mutableStateOf(false) }
    var lineConfirm by remember { mutableStateOf<OrderJobLine?>(null) }
    // the line whose "Teslim ettim" waits for the confirmation (2026-10-05)
    var lineMark by remember { mutableStateOf<OrderJobLine?>(null) }
    var objectionFor by remember { mutableStateOf<OrderJobLine?>(null) }
    LaunchedEffect(orderId) { ai.workis.jowi.data.ScreenContext.setPage("orders"); vm.load(orderId) }
    /** A door's answer: the sentence is this screen's note; a yes re-reads the stage. */
    val after: (Boolean, String?) -> Unit = { ok, msg -> vm.note = msg; if (ok) vm.load(orderId) }
    val o = vm.order

    ConsoleSub(o?.po?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.order_stage_title), onBack, trailing = {
        if (doors.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        when {
            o == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            o == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // the other party moved an order (`changes/`, 2026-10-03)
                ChangesWatch(scope = "orders") { vm.load(orderId) }
                NoteBand(vm.note)
                StageHead(o)
                val steps = o.steps.orEmpty()
                val job = o.job
                val jobView: @Composable (OrderJob) -> Unit = { j ->
                    JobView(
                        j, o, busy = doors.busy,
                        onTerms = { onThread(o) },
                        onContract = { onContract(o) },
                        onFund = { confirmFund = true },
                        onReady = { readyOpen = true },
                        onConfirmLine = { lineConfirm = it },
                        onMarkLine = { lineMark = it },
                        onObject = { objectionFor = it },
                        onWithdraw = { l -> l.id?.let { doors.lineObjectionWithdraw(it, after) } },
                    )
                }
                // the job sits under the live step; with none marked live, it leads
                if (job != null && steps.none { it.state == "now" }) SurfaceCard { jobView(job) }
                steps.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            StepGlyph(s.state, cut = colors.canvas)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                (s.label?.takeIf { it.isNotEmpty() } ?: s.key.orEmpty()).uppercase(),
                                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
                                color = if (s.state == "now") colors.ink else colors.faint,
                            )
                        }
                        val live = s.state == "now" && job != null
                        if (live || stepHasBody(s)) {
                            SurfaceCard {
                                StepBody(s)
                                if (live && job != null) jobView(job)
                            }
                        }
                    }
                }
                // the order's two papers, as on the card
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { OrderDoorRow(WorkisIcons.Bubble, stringResource(R.string.thread_open)) { onThread(o) } }
                        (o.unread ?: 0).takeIf { it > 0 }?.let {
                            Badge(containerColor = Kiremit500, contentColor = OnKiremitFill) { Text("$it", fontSize = 10.sp) }
                        }
                    }
                    // a carrier signs nothing
                    if (!o.isCarrier) OrderDoorRow(WorkisIcons.DocText, stringResource(R.string.contract_open)) { onContract(o) }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    if (confirmFund) {
        ConfirmDialog(stringResource(R.string.order_fund), stringResource(R.string.order_fund_confirm), destructive = false, onDismiss = { confirmFund = false }) {
            doors.fund(orderId, after)
        }
    }
    lineConfirm?.let { l ->
        // on open account nothing is released — the invoice simply runs
        ConfirmDialog(
            stringResource(R.string.ap_confirm_delivery),
            stringResource(if (l.openAccount == true) R.string.ap_confirm_delivery_open_account else R.string.ap_confirm_delivery_confirm),
            destructive = false,
            onDismiss = { lineConfirm = null },
        ) { l.id?.let { doors.lineConfirm(it, after) } }
    }
    lineMark?.let { l ->
        // the seller's hand-over mark: the server's `message` (or its refusal) becomes the note
        ConfirmDialog(
            stringResource(R.string.line_mark_delivered),
            stringResource(R.string.line_mark_delivered_confirm),
            destructive = false,
            onDismiss = { lineMark = null },
        ) { l.id?.let { doors.lineMarkDelivered(it, after) } }
    }
    objectionFor?.let { l ->
        ObjectionSheet(onSend = { reason -> l.id?.let { doors.lineObjection(it, reason, after) } }) { objectionFor = null }
    }
    val ready = o
    if (readyOpen && ready != null) {
        ReadySheet(
            ready, doors.busy,
            deliveryDate = ready.job?.deliveryDate,
            handover = ready.job?.handover,
            handoverOk = ready.job?.handoverOk,
            onReady = { day, done -> doors.ready(orderId, day) { ok, msg -> done(ok, msg); if (ok) after(true, msg) } },
        ) { readyOpen = false }
    }
}

// ---------- head: PO, role, parties, money, the walk, the sentence

@Composable
private fun StageHead(o: PartnerOrder) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(o.po.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            StatusPill(
                stringResource(
                    when (o.role) {
                        "seller" -> R.string.order_role_seller
                        "carrier" -> R.string.order_role_carrier
                        else -> R.string.order_role_buyer
                    },
                ),
            )
        }
        val names = listOfNotNull(o.buyer?.short, o.seller?.short).filter { it.isNotEmpty() }
        if (names.isNotEmpty()) Text(names.joinToString(" · "), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, maxLines = 2)
        // §7.1: a carrier moves the goods and never sees the money
        if (o.isCarrier) {
            Text(stringResource(R.string.order_no_money_for_carrier), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        } else {
            Text(WorkisMoney.text(o.total, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink)
            o.escrowLabel?.takeIf { it.isNotEmpty() }?.let { WorkisChip(ChipSpec(WorkisIcons.Lock, it, colors.muted)) }
        }
        o.steps?.takeIf { it.isNotEmpty() }?.let { OrderWalk(it) }
        o.next?.let { OrderNextLine(it) }
    }
}

// ---------- one step: its line, its note, its dated movements

private fun stepHasBody(s: OrderStep) =
    !s.line.isNullOrEmpty() || !s.note.isNullOrEmpty() || s.rows.orEmpty().isNotEmpty()

@Composable
private fun StepBody(s: OrderStep) {
    val colors = WorkisTheme.colors
    s.line?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink) }
    s.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted) }
    s.rows.orEmpty().forEach { r ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stageWhen(r.at).takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint)
                }
                Text(r.text.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
            }
            r.value?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink,
                    textAlign = TextAlign.End, modifier = Modifier.weight(0.8f, fill = false),
                )
            }
        }
    }
}

// ---------- the job of the live step

@Composable
private fun JobView(
    job: OrderJob,
    o: PartnerOrder,
    busy: Boolean,
    onTerms: () -> Unit,
    onContract: () -> Unit,
    onFund: () -> Unit,
    onReady: () -> Unit,
    onConfirmLine: (OrderJobLine) -> Unit,
    onMarkLine: (OrderJobLine) -> Unit,
    onObject: (OrderJobLine) -> Unit,
    onWithdraw: (OrderJobLine) -> Unit,
) {
    val colors = WorkisTheme.colors
    when (job.kind) {
        // term cards are confirmed in the order's yazışma, where they stand as cards
        "terms" -> StageDoor(WorkisIcons.Clipboard, stringResource(R.string.stage_job_terms), termsSub(job, o), accent = true, onClick = onTerms)
        "contract" -> StageDoor(
            WorkisIcons.DocText, stringResource(R.string.stage_job_contract),
            // what comes first, in the server's words, when this seat cannot sign yet
            job.signHint?.takeIf { job.canAccept != true && it.isNotEmpty() },
            accent = job.canAccept == true, onClick = onContract,
        )
        // the screen's one tinted action: escrow the total
        "fund" -> Button(
            onClick = onFund, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.order_fund), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        "ready" -> {
            if (job.sellerDelivers == true) Text(stringResource(R.string.delivery_seller), fontSize = 13.sp, color = colors.muted)
            job.deliveryDate?.takeIf { it.isNotEmpty() }?.let { StageFact(stringResource(R.string.stage_delivery_date), shortDate(it)) }
            job.handover?.takeIf { it.isNotEmpty() }?.let {
                StageFact(stringResource(R.string.stage_handover), shortDate(it))
                if (job.handoverOk == false) HandoverLate()
            }
            OrderDoorRow(WorkisIcons.ShippingBox, stringResource(R.string.order_ready_action), enabled = !busy, onClick = onReady)
        }
        "lines" -> job.lines.orEmpty().forEach { l -> LineJob(l, busy, onConfirmLine, onMarkLine, onObject, onWithdraw) }
        // read-only: there is no collect door in the app
        "collect" -> {
            job.number?.takeIf { it.isNotEmpty() }?.let { StageFact(stringResource(R.string.stage_invoice), it) }
            job.total?.let { StageFact(stringResource(R.string.total_label), WorkisMoney.text(it, job.currency)) }
            job.due?.takeIf { it.isNotEmpty() }?.let { StageFact(stringResource(R.string.cari_due), shortDate(it)) }
            if (job.suspended == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(WorkisIcons.Clock, contentDescription = null, tint = Beige, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stage_collect_suspended), fontSize = 12.sp, lineHeight = 16.sp, color = Beige)
                }
            }
        }
        else -> Unit
    }
}

/**
 * "Teslim tarihi · 2/7" — the term named by the card's `terms.current`: the job
 * carries only its KEY.
 */
private fun termsSub(job: OrderJob, o: PartnerOrder): String? {
    val cur = o.terms?.current
    val label = cur?.label?.takeIf { job.current == null || cur.key == job.current }
    val parts = mutableListOf<String>()
    label?.takeIf { it.isNotEmpty() }?.let(parts::add)
    val i = job.i ?: cur?.i
    val n = job.n ?: cur?.n
    if (i != null && n != null && i > 0 && n > 0) parts += "$i/$n"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** One line of the `lines` job: the seller's hand-over mark / confirm / object / withdraw by the flags. */
@Composable
private fun LineJob(
    l: OrderJobLine,
    busy: Boolean,
    onConfirm: (OrderJobLine) -> Unit,
    onMark: (OrderJobLine) -> Unit,
    onObject: (OrderJobLine) -> Unit,
    onWithdraw: (OrderJobLine) -> Unit,
) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            listOfNotNull(l.name?.takeIf { it.isNotEmpty() }, l.qty?.let { "× ${WorkisMoney.qty(it)}" }).joinToString(" "),
            fontSize = 14.sp, color = colors.ink,
        )
        l.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted) }
        if (l.canMark == true || l.canConfirm == true || l.canObject == true || l.canWithdraw == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // the seller's hand-over mark (`orders/lines/{id}/delivered/`) — the buyer confirms after it
                if (l.canMark == true) LineAction(stringResource(R.string.line_mark_delivered), colors.accentText, !busy) { onMark(l) }
                if (l.canConfirm == true) LineAction(stringResource(R.string.ap_confirm_delivery), colors.accentText, !busy) { onConfirm(l) }
                if (l.canObject == true) LineAction(stringResource(R.string.ap_object), colors.danger, !busy) { onObject(l) }
                if (l.canWithdraw == true) LineAction(stringResource(R.string.ap_object_withdraw), colors.accentText, !busy) { onWithdraw(l) }
            }
        }
    }
}

@Composable
private fun LineAction(label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = if (enabled) tint else WorkisTheme.colors.faint,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** A door with an optional sub-line: accent words when it is this seat's move, ink otherwise. */
@Composable
private fun StageDoor(icon: ImageVector, title: String, sub: String?, accent: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, tint = if (accent) colors.accentText else colors.muted, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (accent) colors.accentText else colors.ink)
            sub?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted) }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp),
        )
    }
}

/** The objection's one field: the reason, posted to `orders/lines/{id}/objection/`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectionSheet(onSend: (String) -> Unit, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ap_object), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                TextButton(enabled = reason.isNotBlank(), onClick = { onSend(reason.trim()); onClose() }) {
                    Text(stringResource(R.string.done), color = if (reason.isNotBlank()) colors.accentText else colors.faint, fontWeight = FontWeight.SemiBold)
                }
            }
            FormSection(null) {
                FormRow("", reason, { reason = it }, {}, false, placeholder = stringResource(R.string.ap_reason), stacked = true, minLines = 2, maxLines = 5)
            }
        }
    }
}

// ---------- shared with the Siparişler card

/**
 * The steps as a compact horizontal walk. State is a shape AND a word, never
 * colour alone: done ✓ (filled, success), now ● (filled, accent), wait ○ and
 * skip ⊖ (faint) — each beside the server's label.
 */
@Composable
internal fun OrderWalk(steps: List<OrderStep>) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
    ) {
        steps.forEachIndexed { i, s ->
            if (i > 0) Text("›", fontSize = 11.sp, color = colors.faint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepGlyph(s.state)
                Spacer(Modifier.width(4.dp))
                Text(
                    s.label?.takeIf { it.isNotEmpty() } ?: s.key.orEmpty(),
                    fontSize = 11.sp,
                    fontWeight = if (s.state == "now") FontWeight.SemiBold else FontWeight.Normal,
                    color = when (s.state) {
                        "now" -> colors.ink
                        "done" -> colors.muted
                        else -> colors.faint
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * done = filled circle with a check (success) · now = filled circle (accent) ·
 * wait = ring (faint) · skip = ring with a minus (faint). `cut` is the colour
 * the check is cut in — the surface the glyph sits on.
 */
@Composable
internal fun StepGlyph(state: String?, size: Dp = 12.dp, cut: Color = WorkisTheme.colors.surface) {
    val colors = WorkisTheme.colors
    val tint = when (state) {
        "done" -> SuccessGreen
        "now" -> colors.accentText
        else -> colors.faint
    }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val sw = this.size.minDimension * 0.13f
        val c = center
        when (state) {
            "done" -> {
                drawCircle(tint, r, c)
                val check = Path().apply {
                    moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.1f, c.y + r * 0.38f)
                    lineTo(c.x + r * 0.48f, c.y - r * 0.32f)
                }
                drawPath(check, cut, style = Stroke(width = sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "now" -> drawCircle(tint, r, c)
            "skip" -> {
                drawCircle(tint, r - sw / 2f, c, style = Stroke(sw))
                drawLine(tint, Offset(c.x - r * 0.45f, c.y), Offset(c.x + r * 0.45f, c.y), sw, cap = StrokeCap.Round)
            }
            else -> drawCircle(tint, r - sw / 2f, c, style = Stroke(sw))
        }
    }
}

/**
 * THE one sentence under an order — the server's `next.text`, prefixed with
 * whose move it is. Ink when it is the reader's own move, muted otherwise.
 */
@Composable
internal fun OrderNextLine(next: OrderNext) {
    val text = next.text?.takeIf { it.isNotEmpty() } ?: return
    val colors = WorkisTheme.colors
    val mine = next.who == "me"
    val who = when (next.who) {
        "me" -> R.string.order_who_me
        "buyer" -> R.string.order_who_buyer
        "seller" -> R.string.order_who_seller
        "carrier" -> R.string.order_who_carrier
        "workis" -> R.string.order_who_workis
        else -> null
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        who?.let {
            Text(
                stringResource(it), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (mine) colors.ink else colors.muted, maxLines = 1,
                modifier = Modifier.padding(top = 1.dp).background(colors.ink.copy(alpha = 0.06f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = if (mine) colors.ink else colors.muted, modifier = Modifier.weight(1f))
    }
}

/** "Teslim tarihi · 2/7 · belirtin" — the ONE term asked of this seat now; null when none. */
@Composable
internal fun currentTermLine(cur: OrderCurrentTerm?): String? {
    if (cur == null) return null
    val parts = mutableListOf<String>()
    cur.label?.takeIf { it.isNotEmpty() }?.let(parts::add)
    val i = cur.i
    val n = cur.n
    if (i != null && n != null && n > 0) parts += "$i/$n"
    when (cur.turn) {
        "answer" -> parts += stringResource(R.string.order_term_turn_answer)
        "confirm" -> parts += stringResource(R.string.order_term_turn_confirm)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** label … value — a fact of the job (dates and figures in mono). */
@Composable
internal fun StageFact(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
        Text(value, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
    }
}

/** The last hand-over day has passed — icon + words, never colour alone. */
@Composable
internal fun HandoverLate() {
    Row(verticalAlignment = Alignment.Top) {
        Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.stage_handover_late), fontSize = 12.sp, lineHeight = 16.sp, color = Beige)
    }
}

/** Dates as the stage prints them: an instant → "dd.MM.yyyy HH:mm" (device zone), a day → "dd.MM.yyyy". */
private fun stageWhen(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    if (iso.length > 10) {
        runCatching {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }
    }
    return shortDate(iso)
}
