package ai.workis.jowi.ui.screens.partner

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.LedgerAccount
import ai.workis.jowi.data.LedgerOpenInvoice
import ai.workis.jowi.data.LedgerOrderGroup
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * Cari — the web /workis/panel/cari/ twin (`GET ledger/`, 2026-09-30): every
 * current account this firm is a party to, as buyer and as seller; one row per
 * order that opens into its movements; the open invoices under the account.
 * Read-only. Every amount and every state word is the server's — nothing here
 * adds, subtracts or decides a state.
 */
class LedgerViewModel : ViewModel() {
    var accounts by mutableStateOf<List<LedgerAccount>?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(Graph.language.value) { Graph.api.ledger() }
            refreshing = false
            if (r != null) { accounts = r.accounts.orEmpty(); loadError = null } else if (accounts == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    ConsoleSub(stringResource(R.string.cari_title), onBack) {
        val accounts = vm.accounts
        when {
            accounts == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            accounts == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (accounts.isEmpty()) item {
                        Text(stringResource(R.string.cari_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
                    }
                    items(accounts) { a -> AccountSection(a) }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AccountSection(a: LedgerAccount) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // counterparty · role word · currency · balance
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(a.counterparty ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        stringResource(if (a.role == "seller") R.string.cari_as_seller else R.string.cari_as_buyer),
                        if (a.virtual == true) stringResource(R.string.cari_virtual) else null,
                        a.currency,
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = colors.muted, modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.cari_balance) + " ", fontSize = 12.sp, color = colors.muted)
                Text(WorkisMoney.text(a.balance, a.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
            }
        }
        val groups = a.orders.orEmpty()
        val invoices = a.openInvoices.orEmpty()
        if (groups.isNotEmpty() || invoices.isNotEmpty()) SurfaceCard {
            groups.forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider(color = colors.border)
                OrderGroupRow(g, a.currency)
            }
            if (invoices.isNotEmpty()) {
                if (groups.isNotEmpty()) HorizontalDivider(color = colors.border)
                Text(stringResource(R.string.cari_open_invoices), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                invoices.forEach { InvoiceRow(it) }
            }
        }
    }
}

/** One row per order (po · state words · balance); a tap opens its movements. */
@Composable
private fun OrderGroupRow(g: LedgerOrderGroup, currency: String?) {
    val colors = WorkisTheme.colors
    var open by rememberSaveable(g.orderId ?: g.po) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.po ?: "—", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                val movements = stringResource(R.string.cari_movements_word)
                Text(
                    listOfNotNull(g.state, g.movements?.let { "$it $movements" }).filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp, color = colors.muted,
                )
            }
            Text(WorkisMoney.text(g.balance, currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            Spacer(Modifier.width(6.dp))
            Text(if (open) "▾" else "▸", fontSize = 12.sp, color = colors.faint)
        }
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure(stringResource(R.string.cari_invoiced), g.invoiced, currency)
                Figure(stringResource(R.string.cari_paid), g.paid, currency)
            }
            val balanceWord = stringResource(R.string.cari_balance)
            g.rows.orEmpty().forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(r.typeLabel ?: r.type.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f))
                        Text(WorkisMoney.text(r.amount, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            listOfNotNull(r.at?.take(10), r.note, r.extra).filter { it.isNotEmpty() }.joinToString(" · "),
                            fontSize = 11.sp, color = colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$balanceWord ${WorkisMoney.text(r.balance, null)}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: Double?, currency: String?) {
    val colors = WorkisTheme.colors
    Column {
        Text(label, fontSize = 11.sp, color = colors.muted)
        Text(WorkisMoney.text(value, currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
    }
}

/** Overdue / disputed are words with an icon — colour never carries them alone. */
@Composable
private fun InvoiceRow(i: LedgerOpenInvoice) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(i.number ?: "—", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink, modifier = Modifier.weight(1f))
            Text(WorkisMoney.text(i.total, i.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            i.dueDate?.let { Text(stringResource(R.string.cari_due) + " " + it.take(10), fontSize = 11.sp, color = colors.muted) }
            if (i.overdue == true) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.cari_overdue), fontSize = 11.sp, color = colors.danger)
            }
            if (i.disputed == true) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Shield, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.cari_disputed), fontSize = 11.sp, color = Beige)
            }
        }
    }
}
