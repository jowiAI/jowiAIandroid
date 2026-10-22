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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import ai.workis.jowi.ui.theme.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApLine
import ai.workis.jowi.data.Approvals
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * Onaylar (Django §8): nine kinds, one card each, one action each. The app
 * renders what the server sends and composes nothing. `counts.total` leaves
 * `returnOffer` out — an offer is a right, not a task — so that section sits
 * last. Money-moving and destructive actions ask first; the ones that need
 * words open one small form.
 */
class ApprovalsViewModel : ViewModel() {
    var data by mutableStateOf<Approvals?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    private val lang get() = Graph.language.value

    val total get() = data?.counts?.total ?: 0

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.approvals() }
            if (r != null) { data = r; loadError = null } else if (data == null) loadError = err
        }
    }

    /** Every action ends the same way: the server speaks, the inbox reloads, the app echoes nothing. */
    fun act(block: suspend () -> Pair<Boolean, String?>) {
        busy = true; note = null
        viewModelScope.launch {
            val (ok, msg) = block()
            busy = false
            note = msg
            if (ok) load()
        }
    }

    private suspend fun simple(call: suspend () -> ai.workis.jowi.data.SimpleResult): Pair<Boolean, String?> {
        val (r, err) = partnerCall(lang, call)
        return (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun outward(mid: String, action: String) = act { simple { Graph.api.approvalOutward(mid, jsonBody("action" to action)) } }
    fun fund(orderId: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.orderFund(orderId) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }
    fun acceptNewDate(orderId: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.orderTerm(orderId, "delivery_date", jsonBody("action" to "confirm")) }
        (r?.success == true) to (r?.error ?: err)
    }
    fun delayCancel(orderId: String) = act { simple { Graph.api.orderDelayCancel(orderId) } }
    fun requestReturn(lineId: String, reason: String) = act { simple { Graph.api.lineReturn(lineId, jsonBody("reason" to reason)) } }
    fun returnDecision(lineId: String, action: String, note: String, callCargo: Boolean) = act {
        simple { Graph.api.lineReturnDecision(lineId, jsonBody("action" to action, "note" to note.ifBlank { null }, "callCargo" to callCargo)) }
    }
    fun confirmDelivery(lineId: String) = act { simple { Graph.api.lineConfirm(lineId).let { ai.workis.jowi.data.SimpleResult(success = it.success, message = it.message, error = it.error) } } }
    fun objection(lineId: String, reason: String) = act { simple { Graph.api.lineObjection(lineId, jsonBody("reason" to reason)) } }
    fun objectionWithdraw(lineId: String) = act { simple { Graph.api.lineObjectionWithdraw(lineId) } }
    fun objectionResolve(lineId: String, action: String, amount: String, note: String) = act {
        simple {
            Graph.api.lineObjectionResolve(
                lineId,
                jsonBody("action" to action, "note" to note.ifBlank { null }, "amount" to amount.replace(',', '.').toDoubleOrNull()),
            )
        }
    }
    fun warrantyDecision(lineId: String, remedy: String, amount: String, note: String) = act {
        simple {
            Graph.api.lineWarrantyDecision(
                lineId,
                jsonBody("remedy" to remedy, "note" to note.ifBlank { null }, "amount" to amount.replace(',', '.').toDoubleOrNull()),
            )
        }
    }
}

/** A card's "are you sure": its own words, and whether it destroys anything. */
private data class ApConfirm(
    val title: String,
    val body: String,
    val destructive: Boolean,
    val run: () -> Unit,
)

/** What one small form has to collect, and what to do with it. */
private data class ApForm(
    val title: String,
    val lineId: String,
    val kind: String, // objection | returnRequest | returnDecision | objectionResolve | warranty
)

@Composable
fun ApprovalsScreen(vm: ApprovalsViewModel, onBack: () -> Unit, onList: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var form by remember { mutableStateOf<ApForm?>(null) }
    // whether the tap destroys something is the caller's word, not a guess from
    // its title: "Teslimatı onayla" and "yeni tarihi kabul et" build something
    var confirm by remember { mutableStateOf<ApConfirm?>(null) }
    LaunchedEffect(Unit) { vm.load() }
    val d = vm.data

    ConsoleSub(stringResource(R.string.approvals_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        when {
            d == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            d == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NoteBand(vm.note)
                val empty = (d.counts?.total ?: 0) == 0 && d.returnOffer.orEmpty().isEmpty()
                if (empty) Text(stringResource(R.string.approvals_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp))

                // 1 · quotes ready to award — a link to the list the app already has
                Section(stringResource(R.string.ap_award), d.award.orEmpty().size) {
                    d.award.orEmpty().forEach { a ->
                        ApCard(a.title.orEmpty(), listOfNotNull(
                            a.nQuotes?.let { "$it " + stringResource(R.string.ap_quotes) },
                            a.uncovered?.takeIf { it > 0 }?.let { "$it " + stringResource(R.string.ap_uncovered) },
                            a.total?.let { WorkisMoney.text(it, a.currency) },
                        ).joinToString(" · ")) {
                            Action(stringResource(R.string.ap_award_open)) { a.listId?.let(onList) }
                        }
                    }
                }
                // 2 · outward messages waiting for consent
                Section(stringResource(R.string.ap_outward), d.outward.orEmpty().size) {
                    d.outward.orEmpty().forEach { o ->
                        ApCard(o.po ?: o.target.orEmpty(), o.draft.orEmpty()) {
                            Action(stringResource(R.string.ap_outward_send)) { o.mid?.let { vm.outward(it, "send") } }
                            Action(stringResource(R.string.ap_outward_cancel), destructive = true) { o.mid?.let { vm.outward(it, "cancel") } }
                        }
                    }
                }
                // 3 · payments to hold — money moves, so it asks first. A settled
                // deferred term closes the door: the server leaves those rows out,
                // and a `canFund: false` that slips through is not drawn either.
                val fundTitle = stringResource(R.string.order_fund)
                val fundBody = stringResource(R.string.order_fund_confirm)
                val fundable = d.fund.orEmpty().filter { it.canFund != false }
                Section(stringResource(R.string.ap_fund), fundable.size) {
                    fundable.forEach { f ->
                        ApCard(f.po.orEmpty(), "") {
                            Action(fundTitle) {
                                confirm = ApConfirm(fundTitle, fundBody, destructive = false) { f.orderId?.let { vm.fund(it) } }
                            }
                        }
                    }
                }
                // 4 · late deliveries: accept the new date, or cancel for delay
                val cancelTitle = stringResource(R.string.ap_overdue_cancel)
                val cancelBody = stringResource(R.string.ap_overdue_cancel_confirm)
                val acceptTitle = stringResource(R.string.ap_overdue_accept)
                val amendBody = stringResource(R.string.term_confirm_amend_body)
                Section(stringResource(R.string.ap_overdue), d.overdue.orEmpty().size) {
                    d.overdue.orEmpty().forEach { o ->
                        ApCard(
                            o.po.orEmpty(),
                            listOfNotNull(o.proposal?.display, o.proposal?.prev?.let { stringResource(R.string.term_previous) + ": $it" }).joinToString(" · "),
                        ) {
                            // the same second signature as "Onayla" on an amended term
                            // card — it writes the next version of a signed contract,
                            // so it asks first, with that card's sentence
                            o.proposal?.display?.takeIf { it.isNotEmpty() }?.let { shown ->
                                val change = o.proposal?.prev?.takeIf { it.isNotEmpty() }?.let { "$it → $shown" } ?: shown
                                Action(acceptTitle) {
                                    confirm = ApConfirm(acceptTitle, "$change\n\n$amendBody", destructive = false) {
                                        o.orderId?.let { vm.acceptNewDate(it) }
                                    }
                                }
                            }
                            Action(cancelTitle, destructive = true) {
                                confirm = ApConfirm(cancelTitle, cancelBody, destructive = true) { o.orderId?.let { vm.delayCancel(it) } }
                            }
                        }
                    }
                }
                // 5 · deliveries under inspection. What happens to the money is
                // the server's sentence (`moneyWords`) — on open account nothing
                // is released, the invoice simply runs to its due date.
                val releaseTitle = stringResource(R.string.ap_confirm_delivery)
                val releaseBody = stringResource(R.string.ap_confirm_delivery_confirm)
                val releaseBodyOpen = stringResource(R.string.ap_confirm_delivery_open_account)
                val objectTitle = stringResource(R.string.ap_object)
                Section(stringResource(R.string.ap_inspection), d.inspection.orEmpty().size) {
                    d.inspection.orEmpty().forEach { l ->
                        ApCard(l.name.orEmpty(), lineSub(l), words = moneyLine(l)) {
                            if (l.status == "objected") {
                                Action(stringResource(R.string.ap_object_withdraw)) { l.lineId?.let { vm.objectionWithdraw(it) } }
                            } else {
                                val body = if (l.moneyPath == "open_account") releaseBodyOpen else releaseBody
                                Action(releaseTitle) {
                                    confirm = ApConfirm(releaseTitle, body, destructive = false) { l.lineId?.let { vm.confirmDelivery(it) } }
                                }
                                Action(objectTitle, destructive = true) { l.lineId?.let { form = ApForm(objectTitle, it, "objection") } }
                            }
                        }
                    }
                }
                // 6 · objections to answer
                val resolveTitle = stringResource(R.string.ap_resolve)
                Section(stringResource(R.string.ap_objection), d.objection.orEmpty().size) {
                    d.objection.orEmpty().forEach { l ->
                        ApCard(l.name.orEmpty(), lineSub(l), words = moneyLine(l)) {
                            Action(resolveTitle) { l.lineId?.let { form = ApForm(resolveTitle, it, "objectionResolve") } }
                        }
                    }
                }
                // 7 · return requests to decide
                val decideTitle = stringResource(R.string.ap_resolve)
                Section(stringResource(R.string.ap_return_decision), d.returnDecision.orEmpty().size) {
                    d.returnDecision.orEmpty().forEach { l ->
                        ApCard(l.name.orEmpty(), lineSub(l)) {
                            Action(decideTitle) { l.lineId?.let { form = ApForm(decideTitle, it, "returnDecision") } }
                        }
                    }
                }
                // 8 · warranty claims
                Section(stringResource(R.string.ap_warranty), d.warranty.orEmpty().size) {
                    d.warranty.orEmpty().forEach { l ->
                        ApCard(l.name.orEmpty(), listOfNotNull(l.po, l.description, l.discoveredOn?.take(10)).joinToString(" · ")) {
                            Action(resolveTitle) { l.lineId?.let { form = ApForm(resolveTitle, it, "warranty") } }
                        }
                    }
                }
                // 9 · the return right — a right, not a task, so it sits last
                val returnTitle = stringResource(R.string.ap_return_request)
                Section(stringResource(R.string.ap_return_offer), d.returnOffer.orEmpty().size) {
                    d.returnOffer.orEmpty().forEach { l ->
                        ApCard(l.name.orEmpty(), lineSub(l)) {
                            Action(returnTitle) { l.lineId?.let { form = ApForm(returnTitle, it, "returnRequest") } }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    confirm?.let { c ->
        ConfirmDialog(c.title, c.body, c.destructive, onDismiss = { confirm = null }) { c.run() }
    }
    form?.let { f -> ApFormSheet(f, vm) { form = null } }
}

@Composable
private fun lineSub(l: ApLine): String = listOfNotNull(
    l.po,
    l.reason,
    l.status,
    l.daysLeft?.let { "$it " + stringResource(R.string.ap_days_left) },
    // "serbest kalır" is an escrow word: on open account no money is held and
    // what the clock ends is the right to object
    l.releaseDue?.take(16)?.takeIf { it.isNotEmpty() }?.let {
        stringResource(if (l.moneyPath == "open_account") R.string.ap_window_ends else R.string.ap_release_due) + " $it"
    },
    l.due?.take(16)?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ap_with_coordinator) + " $it" },
).joinToString(" · ")

/** The money's fate in the server's own words, with the invoice it hangs on. */
private fun moneyLine(l: ApLine): String? {
    val words = l.moneyWords?.takeIf { it.isNotEmpty() } ?: return null
    val invoice = l.invoice?.takeIf { it.isNotEmpty() } ?: return words
    return "$words · $invoice"
}

@Composable
private fun Section(title: String, count: Int, content: @Composable () -> Unit) {
    if (count == 0) return
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${title.uppercase()} · $count",
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
            color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ApCard(title: String, sub: String, words: String? = null, actions: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint, maxLines = 3, overflow = TextOverflow.Ellipsis)
        // printed as it arrives: whether the money is held or the invoice runs
        // is a fact of this order, not a sentence for the app to compose
        words?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
    }
}

@Composable
private fun Action(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = if (destructive) colors.danger else colors.accentText,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The one small form: a reason, or a decision + note (+ amount, + call the courier). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApFormSheet(f: ApForm, vm: ApprovalsViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var callCargo by remember { mutableStateOf(false) }
    var choice by remember {
        mutableStateOf(
            when (f.kind) {
                "returnDecision" -> "approve"
                "objectionResolve" -> "reduction"
                "warranty" -> "repair"
                else -> ""
            },
        )
    }
    val choices: List<Pair<String, Int>> = when (f.kind) {
        "returnDecision" -> listOf("approve" to R.string.ap_approve, "reject" to R.string.ap_reject)
        "objectionResolve" -> listOf("reduction" to R.string.ap_reduction, "return" to R.string.ap_return, "reject" to R.string.ap_reject)
        "warranty" -> listOf("repair" to R.string.ap_repair, "replace" to R.string.ap_replace, "reduction" to R.string.ap_reduction, "reject" to R.string.ap_reject)
        else -> emptyList()
    }
    val needsAmount = choice == "reduction"
    val wantsReason = f.kind == "objection" || f.kind == "returnRequest"
    val canSend = if (wantsReason) reason.isNotBlank() else choice.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                TextButton(enabled = canSend, onClick = {
                    when (f.kind) {
                        "objection" -> vm.objection(f.lineId, reason)
                        "returnRequest" -> vm.requestReturn(f.lineId, reason)
                        "returnDecision" -> vm.returnDecision(f.lineId, choice, note, callCargo)
                        "objectionResolve" -> vm.objectionResolve(f.lineId, choice, amount, note)
                        "warranty" -> vm.warrantyDecision(f.lineId, choice, amount, note)
                    }
                    onClose()
                }) { Text(stringResource(R.string.done), color = if (canSend) colors.accentText else colors.faint, fontWeight = FontWeight.SemiBold) }
            }
            if (choices.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (value, res) ->
                        val on = choice == value
                        Text(
                            stringResource(res),
                            fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) colors.ink else colors.muted,
                            modifier = Modifier
                                .background(if (on) colors.ink.copy(alpha = 0.10f) else colors.ink.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                                .clickable { choice = value }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            FormSection(null) {
                if (wantsReason) {
                    FormRow("", reason, { reason = it }, {}, false, placeholder = stringResource(R.string.ap_reason), stacked = true, minLines = 2, maxLines = 5)
                } else {
                    FormRow("", note, { note = it }, {}, false, placeholder = stringResource(R.string.ap_note), stacked = true, minLines = 2, maxLines = 5)
                    if (needsAmount) {
                        FormRow(stringResource(R.string.ap_amount), amount, { amount = it }, {}, false, placeholder = "—", mono = true, keyboardType = KeyboardType.Decimal)
                    }
                    if (f.kind == "returnDecision" && choice == "approve") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.ap_call_cargo), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                            Switch(checked = callCargo, onCheckedChange = { callCargo = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue))
                        }
                    }
                }
            }
        }
    }
}
