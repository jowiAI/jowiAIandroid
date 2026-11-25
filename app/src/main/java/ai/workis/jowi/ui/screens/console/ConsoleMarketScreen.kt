package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleMarket
import ai.workis.jowi.data.MktAccount
import ai.workis.jowi.data.MktAutonomy
import ai.workis.jowi.data.MktRfq
import ai.workis.jowi.data.MktStats
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.screens.partner.StatusPill
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Console → Pazar (web /workis/console/pazar/, page key `market`, ledger 128). Read-only: the
 * marketplace's counts, every request's lifecycle (participation and state — the sealed offers of
 * an open request never arrive; a total only once awarded, as a ready string printed as sent), the
 * current accounts between firms and the autonomy lane. `held` / `released` are one bare number
 * each, no currency. A field lead gets a 403 here; the server's sentence is printed.
 */
class ConsoleMarketViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleMarket?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(Graph.language.value) { Graph.api.consoleMarket() }
            refreshing = false
            if (p != null) { page = p; loadError = null } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleMarketScreen(onBack: () -> Unit) {
    val vm: ConsoleMarketViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    ConsoleSub(stringResource(R.string.mkt_title), onBack) {
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (page.dormant == true) {
                        item { MktCard { MktDormant() } }
                    } else {
                        page.stats?.let { s -> item { MktCard { MktStatsGrid(s) } } }
                        item {
                            val rows = page.rfqs.orEmpty()
                            MktSection(WorkisIcons.Search, stringResource(R.string.mkt_rfqs), rows.size, stringResource(R.string.mkt_rfqs_hint)) {
                                if (rows.isEmpty()) MktEmpty(stringResource(R.string.mkt_no_rfqs))
                                MktRows(rows) { MktRfqRow(it) }
                            }
                        }
                        item {
                            val rows = page.accounts.orEmpty()
                            MktSection(WorkisIcons.Book, stringResource(R.string.mkt_accounts), rows.size, null) {
                                if (rows.isEmpty()) MktEmpty(stringResource(R.string.mkt_no_accounts))
                                MktRows(rows) { MktAccountRow(it) }
                            }
                        }
                    }
                    val lane = page.autonomy.orEmpty()
                    if (lane.isNotEmpty()) item {
                        MktSection(WorkisIcons.Sparkle, stringResource(R.string.mkt_autonomy), lane.size, stringResource(R.string.mkt_autonomy_hint)) {
                            MktRows(lane) { MktAutonomyRow(it) }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

/** The marketplace is switched off: the moon and the page's word. */
@Composable
private fun MktDormant() {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(WorkisIcons.Moon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.mkt_dormant), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
    }
}

@Composable
private fun MktCard(content: @Composable () -> Unit) {
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

/** A section: its icon in the accent tone, the name in ink, the count on a chip; the card; the hint under it. */
@Composable
private fun MktSection(icon: ImageVector, title: String, count: Int, hint: String?, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            if (count > 0) Text(
                "$count", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink,
                modifier = Modifier.background(colors.ink.copy(alpha = 0.08f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        MktCard(content)
        hint?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun <T> MktRows(rows: List<T>, row: @Composable (T) -> Unit) {
    val colors = WorkisTheme.colors
    rows.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider(color = colors.border)
        row(r)
    }
}

@Composable
private fun MktEmpty(words: String) {
    Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = WorkisTheme.colors.faint)
}

/** A bare whole number grouped in the app language ("39.855") — the web's floatformat:0, no currency. */
private fun mktBare(v: Double?): String = v?.let { WorkisMoney.count(it.roundToLong().toInt()) } ?: "—"

private fun mktCount(v: Int?): String = v?.let { WorkisMoney.count(it) } ?: "—"

@Composable
private fun MktStatsGrid(s: MktStats) {
    val tiles = listOf(
        mktCount(s.rfqsOpen) to stringResource(R.string.mkt_rfqs_open),
        mktCount(s.rfqsAwarded) to stringResource(R.string.mkt_awarded),
        mktCount(s.quotes) to stringResource(R.string.mkt_quotes),
        mktCount(s.invoices) to stringResource(R.string.mkt_invoices),
        mktBare(s.held) to stringResource(R.string.mkt_held),
        mktBare(s.released) to stringResource(R.string.mkt_released),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (value, label) -> MktTile(value, label, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MktTile(value: String, label: String, modifier: Modifier) {
    val colors = WorkisTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
    }
}

@Composable
private fun MktRfqRow(r: MktRfq) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.title.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, modifier = Modifier.weight(1f))
            r.statusWords?.takeIf { it.isNotEmpty() }?.let { StatusPill(it) }
        }
        val sub = listOfNotNull(r.buyer?.takeIf { it.isNotEmpty() }, r.createdAt?.takeIf { it.isNotEmpty() }?.let { WorkisClock.stamp(it) })
            .joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val invited = r.invited ?: 0
            val line = "$invited " + stringResource(R.string.mkt_invited) + " · ${r.quoted ?: 0}/$invited " + stringResource(R.string.mkt_quoted) +
                (r.qty?.takeIf { it.isNotEmpty() }?.let { " · " + stringResource(R.string.mkt_qty) + " " + it } ?: "")
            Text(line, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, modifier = Modifier.weight(1f))
            val total = r.orderTotal?.takeIf { it.isNotEmpty() }
            if (total != null) {
                Text(total, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
            } else {
                // an open request's offers are sealed — the lock and the word
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(WorkisIcons.Lock, contentDescription = null, tint = colors.faint, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.mkt_sealed), fontSize = 12.sp, color = colors.faint)
                }
            }
        }
        r.escrow?.takeIf { it.isNotEmpty() }?.let {
            Text(stringResource(R.string.mkt_escrow) + ": " + it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        }
    }
}

@Composable
private fun MktAccountRow(a: MktAccount) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${a.buyer.orEmpty()} → ${a.seller.orEmpty()}", fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, modifier = Modifier.weight(1f))
            Text(WorkisMoney.text(a.balance, a.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        }
        val line = listOfNotNull(
            "${a.entries ?: 0} " + stringResource(R.string.mkt_entries),
            a.updatedAt?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.mkt_last_move) + ": " + WorkisClock.stamp(it) },
        ).joinToString(" · ")
        Text(line, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
    }
}

@Composable
private fun MktAutonomyRow(r: MktAutonomy) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.company.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink, modifier = Modifier.weight(1f))
            r.at?.takeIf { it.isNotEmpty() }?.let {
                Text(WorkisClock.stamp(it), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
        }
        Text(r.text.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
    }
}
