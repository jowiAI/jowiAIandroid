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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
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
import ai.workis.jowi.data.PartnerOrder
import ai.workis.jowi.data.ReviewBody
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.partnerCall
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
    private val lang get() = Graph.language.value

    val unreadTotal get() = orders.orEmpty().sumOf { it.unread ?: 0 }

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.orders() }
            if (r != null) { orders = r.orders.orEmpty(); loadError = null } else if (orders == null) loadError = err
        }
    }

    private fun act(block: suspend () -> Pair<Boolean, String?>) {
        busy = true; note = null
        viewModelScope.launch {
            val (ok, msg) = block()
            busy = false
            note = msg
            if (ok) load()
        }
    }

    fun fund(id: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.orderFund(id) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun lineReceived(lineId: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.orderLineReceived(lineId) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    /** `savedWord` is the catalogue's "Your rating was saved." — the caller reads it. */
    fun review(shipmentId: String, rating: Int, comment: String, savedWord: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.shipmentReview(shipmentId, ReviewBody(rating, comment.ifBlank { null })) }
        (r?.success == true) to (if (r?.success == true) (r.message ?: savedWord) else (r?.error ?: err))
    }
}

@Composable
fun OrdersScreen(vm: OrdersViewModel, onBack: () -> Unit, onThread: (PartnerOrder) -> Unit, onContract: (PartnerOrder) -> Unit) {
    val colors = WorkisTheme.colors
    var reviewFor by remember { mutableStateOf<PartnerOrder?>(null) }
    var confirmFund by remember { mutableStateOf<PartnerOrder?>(null) }
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
                NoteBand(vm.note)
                if (orders.isEmpty()) Text(stringResource(R.string.orders_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
                orders.forEach { o ->
                    OrderCard(
                        o, vm,
                        onThread = { onThread(o) },
                        onContract = { onContract(o) },
                        onFund = { confirmFund = o },
                        onReview = { reviewFor = o },
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
}

@Composable
private fun OrderCard(
    o: PartnerOrder,
    vm: OrdersViewModel,
    onThread: () -> Unit,
    onContract: () -> Unit,
    onFund: () -> Unit,
    onReview: () -> Unit,
) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
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
        o.shipment?.let { s ->
            Text(
                listOfNotNull(
                    s.statusLabel?.takeIf { it.isNotEmpty() },
                    s.promised?.take(10)?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.order_promised) + " $it" },
                    s.trackingNo?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.order_tracking) + " $it" },
                ).joinToString(" · "),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint,
            )
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
                (o.unread ?: 0).takeIf { it > 0 }?.let { Badge { Text("$it", fontSize = 10.sp) } }
            }
            o.terms?.let { t ->
                val ok = t.ok ?: 0
                val total = t.total ?: 0
                Text(
                    "$ok/$total " + stringResource(R.string.order_terms_settled),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    color = if (total > 0 && ok >= total) SuccessGreen else Beige,
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
