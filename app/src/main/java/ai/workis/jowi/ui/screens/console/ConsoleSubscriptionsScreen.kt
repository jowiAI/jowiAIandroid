package ai.workis.jowi.ui.screens.console

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleSubscriptions
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.SubsInvoice
import ai.workis.jowi.data.SubsRow
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.screens.partner.StatusPill
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.launch

/**
 * Console → Abonelikler (web /workis/console/abonelikler/, page key `subs`, ledger 128). Every
 * seller subscription with its invoice trail: the header numbers (MRR, the open issued total, how
 * many are overdue), then one row per firm opening to its invoices. An `issued` invoice can be
 * marked paid against the bank receipt reference — the button is drawn by the invoice's own
 * status, never by role. A field lead gets a 403 here; the server's sentence is printed.
 */
class ConsoleSubscriptionsViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleSubscriptions?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    /** The server's word after an act — its success sentence (green) or its refusal (beige). */
    var actionNote by mutableStateOf<String?>(null)
        private set
    var actionOk by mutableStateOf(false)
        private set

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleSubscriptions() }
            refreshing = false
            if (p != null) { page = p; loadError = null } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    /** `POST …/invoices/{id}/paid/ {ref}`; the page is re-read after, whatever the answer. */
    fun markPaid(inv: SubsInvoice, ref: String, fallback: (String?) -> String) {
        val id = inv.id ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleInvoicePaid(id, jsonBody("ref" to ref.trim())) }
            busy = false
            actionOk = r?.success == true
            actionNote = if (actionOk) r?.message?.takeIf { it.isNotEmpty() } ?: fallback(r?.number ?: inv.number)
            else r?.error ?: r?.message ?: err
            load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSubscriptionsScreen(onBack: () -> Unit) {
    val vm: ConsoleSubscriptionsViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    var paying by remember { mutableStateOf<SubsInvoice?>(null) }
    var ref by remember { mutableStateOf("") }
    val markedWord = stringResource(R.string.subs_marked_paid)
    ConsoleSub(stringResource(R.string.subs_title), onBack) {
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    vm.actionNote?.takeIf { it.isNotEmpty() }?.let { note ->
                        item {
                            if (vm.actionOk) SubsClear(note)
                            else Text(
                                note, fontSize = 13.sp, lineHeight = 18.sp, color = Beige,
                                modifier = Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(12.dp)).padding(12.dp),
                            )
                        }
                    }
                    if (page.dormant == true) {
                        item {
                            SubsCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(WorkisIcons.Moon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.mkt_dormant), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                                }
                            }
                        }
                    } else {
                        item { SubsHeader(page) }
                        val rows = page.rows.orEmpty()
                        item {
                            SubsCard {
                                if (rows.isEmpty()) Text(stringResource(R.string.subs_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
                                rows.forEachIndexed { i, row ->
                                    if (i > 0) HorizontalDivider(color = colors.border)
                                    SubsFirmRow(row, busy = vm.busy) { inv -> ref = ""; paying = inv }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
    paying?.let { inv ->
        AlertDialog(
            onDismissRequest = { paying = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.subs_ref_prompt), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        listOfNotNull(inv.number?.takeIf { it.isNotEmpty() }, stringResource(R.string.subs_ref_hint)).joinToString("\n"),
                        fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted,
                    )
                    OutlinedTextField(
                        value = ref, onValueChange = { ref = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.subs_ref_prompt), fontSize = 14.sp, color = colors.faint) },
                        textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 14.sp, color = colors.ink).readingStep(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { paying = null; vm.markPaid(inv, ref) { n -> listOfNotNull(markedWord, n).joinToString(" ") } },
                    enabled = ref.isNotBlank(),
                ) {
                    Text(stringResource(R.string.subs_mark_paid), fontWeight = FontWeight.SemiBold, color = if (ref.isNotBlank()) colors.accentText else colors.faint)
                }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

@Composable
private fun SubsCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The server's success sentence — green is the status colour, with its tick. */
@Composable
private fun SubsClear(words: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = SuccessGreen)
    }
}

@Composable
private fun SubsFigure(label: String, value: String) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Text(value, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
    }
}

@Composable
private fun SubsHeader(p: ConsoleSubscriptions) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubsCard {
            SubsFigure(stringResource(R.string.subs_mrr), WorkisMoney.text(p.mrr, "TRY"))
            SubsFigure(stringResource(R.string.subs_open_total), WorkisMoney.text(p.openTotal, "TRY"))
            // overdue: the number with its icon — the warning and the danger colour, or the green tick at zero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.subs_overdue), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                val n = p.overdue ?: 0
                val tone = if (n > 0) colors.danger else SuccessGreen
                Icon(if (n > 0) WorkisIcons.Warning else WorkisIcons.Check, contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(WorkisMoney.count(n), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = tone)
            }
            p.vatPct?.takeIf { it.isNotEmpty() }?.let { SubsFigure(stringResource(R.string.subs_vat), "%$it") }
        }
        Text(stringResource(R.string.subs_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** "2026-08-01" → "08.2026": an invoice period is a month. */
private fun subsMonth(s: String): String {
    val ymd = s.take(10).split("-")
    return if (ymd.size >= 2) "${ymd[1]}.${ymd[0]}" else s
}

@Composable
private fun subsStatusWord(s: String): String = when (s) {
    "active" -> stringResource(R.string.subs_status_active)
    "paused" -> stringResource(R.string.subs_status_paused)
    "ended" -> stringResource(R.string.subs_status_ended)
    else -> s
}

/** One firm: name, status, fee and start; a tap opens its invoices. */
@Composable
private fun SubsFirmRow(row: SubsRow, busy: Boolean, onPay: (SubsInvoice) -> Unit) {
    val colors = WorkisTheme.colors
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.firm.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f, fill = false))
                    row.status?.takeIf { it.isNotEmpty() }?.let { s -> StatusPill(subsStatusWord(s), tint = if (s == "active") SuccessGreen else colors.muted) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        WorkisMoney.text(row.monthlyFee, row.currency) + " / " + stringResource(R.string.subs_per_month),
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted,
                    )
                    row.started?.takeIf { it.isNotEmpty() }?.let {
                        Text("· " + stringResource(R.string.subs_since) + " " + WorkisClock.day(it), fontSize = 12.sp, color = colors.muted)
                    }
                }
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            val invs = row.invoices.orEmpty()
            if (invs.isEmpty()) Text(stringResource(R.string.subs_no_invoices), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
            invs.forEach { inv ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.ink.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) { SubsInvoiceRow(inv, busy, onPay) }
            }
        }
    }
}

/** The invoice's state: icon + word, never colour alone. */
@Composable
private fun SubsInvoiceStatus(inv: SubsInvoice) {
    val colors = WorkisTheme.colors
    val (icon, word, tone) = when {
        inv.status == "paid" -> Triple(WorkisIcons.Check, stringResource(R.string.subs_inv_paid), SuccessGreen)
        inv.overdue == true -> Triple(WorkisIcons.Warning, stringResource(R.string.subs_inv_overdue), colors.danger)
        inv.status == "issued" -> Triple(WorkisIcons.Clock, stringResource(R.string.subs_inv_issued), Beige)
        inv.status == "void" -> Triple(WorkisIcons.Cross, stringResource(R.string.subs_inv_void), colors.muted)
        else -> Triple(null, inv.status.orEmpty(), colors.muted)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let { Icon(it, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp)) }
        Text(word, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = tone)
    }
}

@Composable
private fun SubsInvoiceRow(inv: SubsInvoice, busy: Boolean, onPay: (SubsInvoice) -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(inv.number.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            inv.period?.takeIf { it.isNotEmpty() }?.let {
                Text(subsMonth(it), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
            Spacer(Modifier.weight(1f))
            SubsInvoiceStatus(inv)
        }
        // amount + VAT = total, each as the server sent it
        Text(
            WorkisMoney.text(inv.amount, null) + " + " + stringResource(R.string.subs_vat) + " " +
                WorkisMoney.text(inv.vatAmount, null) + " = " + WorkisMoney.text(inv.total, inv.currency),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = colors.ink,
        )
        val paid = inv.status == "paid"
        val parts = buildList<Pair<String, Color>> {
            inv.dueDate?.takeIf { it.isNotEmpty() }?.let {
                add(stringResource(R.string.subs_due) + ": " + WorkisClock.day(it) to (if (inv.overdue == true) colors.danger else colors.muted))
            }
            if (paid) inv.paidRef?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.subs_ref) + ": " + it to colors.muted) }
            if (paid) inv.paidAt?.takeIf { it.isNotEmpty() }?.let { add(WorkisClock.day(it) to colors.muted) }
        }
        if (parts.isNotEmpty()) Text(
            buildAnnotatedString {
                parts.forEachIndexed { i, (words, tone) ->
                    if (i > 0) withStyle(SpanStyle(color = colors.muted)) { append(" · ") }
                    withStyle(SpanStyle(color = tone)) { append(words) }
                }
            },
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
        )
        if (inv.status == "issued" && inv.id != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { onPay(inv) }, enabled = !busy) {
                    Text(stringResource(R.string.subs_mark_paid), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                }
            }
        }
    }
}
