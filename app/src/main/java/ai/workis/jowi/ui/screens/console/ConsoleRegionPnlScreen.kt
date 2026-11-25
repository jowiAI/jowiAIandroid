package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleRegionPnl
import ai.workis.jowi.data.PnlAsset
import ai.workis.jowi.data.PnlClose
import ai.workis.jowi.data.PnlEntry
import ai.workis.jowi.data.PnlParams
import ai.workis.jowi.data.PnlRegion
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.screens.partner.StatusPill
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.launch

/**
 * Console → Bölge K/Z (web /workis/console/bolge-pnl/, page key `pnl`, ledger 128). The region
 * pool made visible (Ek-4). The field lead PROVES its share here: every attributed line names the
 * seller and carries the server's own sentence of how the amount was reached (`why.words`, then
 * `why.legs`) — printed whole, never truncated, never rebuilt on the phone. Per region: the open
 * month's figures, its lines, what would be distributed if it closed today (`soFar`), the
 * amortised assets and the closed months, each opening to its lines and its distribution. Who may
 * add a documented cost is the server's flag (`canWrite`); a lead never sees it.
 */
class ConsoleRegionPnlViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleRegionPnl?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The server's sentence after a recorded cost. */
    var actionNote by mutableStateOf<String?>(null)
        private set

    // the cost sheet
    var adding by mutableStateOf(false)
        private set
    var region by mutableStateOf("")
    var kind by mutableStateOf("")
    var amount by mutableStateOf("")
    var note by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var costError by mutableStateOf<String?>(null)
        private set

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleRegionPnl() }
            refreshing = false
            if (p != null) { page = p; loadError = null } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    fun openSheet() {
        val p = page ?: return
        if (region.isEmpty() || p.regions.orEmpty().none { it.id == region }) region = p.regions.orEmpty().firstNotNullOfOrNull { it.id }.orEmpty()
        if (kind.isEmpty() || kind !in p.costKinds.orEmpty()) kind = p.costKinds.orEmpty().firstOrNull().orEmpty()
        amount = ""; note = ""; costError = null
        adding = true
    }

    fun closeSheet() { if (!busy) adding = false }

    val ready: Boolean get() = region.isNotEmpty() && kind.isNotEmpty() && amount.isNotBlank() && !busy

    /** The amount goes as typed ("1250,50") — the server reads it and refuses in its own words. */
    fun save(fallback: String) {
        if (!ready) return
        busy = true; costError = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.consoleRegionCost(jsonBody("region" to region, "kind" to kind, "amount" to amount.trim(), "note" to note.trim()))
            }
            busy = false
            if (r?.success == true) {
                adding = false
                actionNote = r.message?.takeIf { it.isNotEmpty() } ?: fallback
                load()
            } else costError = r?.error ?: r?.message ?: err
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleRegionPnlScreen(onBack: () -> Unit) {
    val vm: ConsoleRegionPnlViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    val page = vm.page
    val mayAdd = page != null && page.canWrite == true && page.dormant != true && page.regions.orEmpty().isNotEmpty()
    ConsoleSub(
        stringResource(R.string.pnl_title), onBack,
        trailing = {
            if (mayAdd) OutlinedButton(onClick = vm::openSheet) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.pnl_add_cost), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
        },
    ) {
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    vm.actionNote?.takeIf { it.isNotEmpty() }?.let { n ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(n, fontSize = 13.sp, lineHeight = 18.sp, color = SuccessGreen)
                            }
                        }
                    }
                    if (page.dormant == true) {
                        item {
                            PnlCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(WorkisIcons.Moon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.mkt_dormant), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                                }
                            }
                        }
                    } else {
                        item { PnlRule(page) }
                        val regions = page.regions.orEmpty()
                        if (regions.isEmpty()) item {
                            PnlCard { Text(stringResource(R.string.pnl_no_regions), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint) }
                        }
                        regions.forEachIndexed { i, r -> pnlRegionItems(i, r, page) }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
    if (vm.adding && page != null) PnlCostSheet(vm, page)
}

/** The region's sections, one LazyColumn item each. */
private fun androidx.compose.foundation.lazy.LazyListScope.pnlRegionItems(index: Int, r: PnlRegion, page: ConsoleRegionPnl) {
    val key = r.id ?: "r$index"
    // the open month's figures
    item(key = "$key-fig") {
        PnlSection(WorkisIcons.MapPin, r.region.orEmpty()) {
            PnlFigure(stringResource(R.string.pnl_revenue), r.revenue)
            PnlCostFigure(r.cost)
            PnlFigure(stringResource(R.string.pnl_net), r.net, strong = true)
        }
    }
    // the open month's lines
    item(key = "$key-lines") {
        PnlSection(WorkisIcons.Checklist, stringResource(R.string.pnl_this_month_lines)) {
            val rows = r.entries.orEmpty()
            if (rows.isEmpty()) Text(stringResource(R.string.pnl_no_entries), fontSize = 13.sp, lineHeight = 18.sp, color = WorkisTheme.colors.faint)
            PnlRows(rows) { PnlEntryRow(it, page) }
        }
    }
    // what would be distributed if the month closed today
    r.soFar?.let { s ->
        item(key = "$key-sofar") {
            PnlSection(WorkisIcons.Hourglass, stringResource(R.string.pnl_so_far), hint = stringResource(R.string.pnl_so_far_note)) {
                PnlDistribution(s.net, s.carryIn, s.distributable, s.leadShare, s.platformShare, s.carryOut)
                s.leadRate?.let { rate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pnl_lead_rate), fontSize = 14.sp, color = WorkisTheme.colors.ink, modifier = Modifier.weight(1f))
                        Text(pnlPct(rate), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = WorkisTheme.colors.ink)
                    }
                }
            }
        }
    }
    val assets = r.assets.orEmpty()
    if (assets.isNotEmpty()) item(key = "$key-assets") {
        PnlSection(WorkisIcons.Truck, stringResource(R.string.pnl_assets)) { PnlRows(assets) { PnlAssetRow(it) } }
    }
    val closes = r.closes.orEmpty()
    if (closes.isNotEmpty()) item(key = "$key-closes") {
        PnlSection(WorkisIcons.Calendar, stringResource(R.string.pnl_closed_months)) { PnlRows(closes) { PnlCloseRow(it, page) } }
    }
}

@Composable
private fun PnlCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** A section: its icon in the accent tone, the name in ink (never upper-cased); the card; the hint under it. */
@Composable
private fun PnlSection(icon: ImageVector, title: String, hint: String? = null, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
        }
        PnlCard(content)
        hint?.let { PnlHint(it, Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun PnlHint(words: String, modifier: Modifier = Modifier) {
    Text(words, fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.faint, modifier = modifier)
}

@Composable
private fun <T> PnlRows(rows: List<T>, row: @Composable (T) -> Unit) {
    val colors = WorkisTheme.colors
    rows.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider(color = colors.border)
        row(r)
    }
}

/** "2026-11-01" → "11.2026": a period is a month. */
private fun pnlMonth(s: String?): String {
    if (s == null) return ""
    val ymd = s.take(10).split("-")
    return if (ymd.size >= 2) "${ymd[1]}.${ymd[0]}" else s
}

/** A rate (0.5) as the web writes it: "%50". */
private fun pnlPct(v: Double?): String = v?.let { "%" + WorkisMoney.pct(it * 100) } ?: "—"

@Composable
private fun pnlParamsLine(p: PnlParams): String =
    stringResource(R.string.pnl_pool_share_param) + " " + pnlPct(p.poolShare) + " · " +
        stringResource(R.string.pnl_lead_share_param) + " " + pnlPct(p.leadShare) + " · " +
        stringResource(R.string.pnl_hq_leg_param) + " " + pnlPct(p.attrHq)

/** The kind in the app's words; an unknown kind reads the server's own label, then the code. */
@Composable
private fun pnlKindWord(k: String?, label: String? = null, page: ConsoleRegionPnl? = null): String = when (k) {
    "cargo_fee" -> stringResource(R.string.pnl_kind_cargo_fee)
    "subs_attr" -> stringResource(R.string.pnl_kind_subs_attr)
    "commission_attr" -> stringResource(R.string.pnl_kind_commission_attr)
    "cost_energy" -> stringResource(R.string.pnl_kind_energy)
    "cost_maintenance" -> stringResource(R.string.pnl_kind_maintenance)
    "cost_station" -> stringResource(R.string.pnl_kind_station)
    "cost_personnel" -> stringResource(R.string.pnl_kind_personnel)
    "cost_external" -> stringResource(R.string.pnl_kind_external)
    "cost_other" -> stringResource(R.string.pnl_kind_other)
    "amort_vehicle" -> stringResource(R.string.pnl_kind_amort_vehicle)
    "amort_station" -> stringResource(R.string.pnl_kind_amort_station)
    else -> label?.takeIf { it.isNotEmpty() } ?: page?.kindLabels?.get(k.orEmpty())?.takeIf { it.isNotEmpty() } ?: k.orEmpty()
}

/** The month and the rule the pool runs on — the web's sub-line. */
@Composable
private fun PnlRule(p: ConsoleRegionPnl) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PnlCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pnl_open_month) + ": " + pnlMonth(p.period), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                p.params?.let { Text(pnlParamsLine(it), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
            }
        }
        PnlHint(stringResource(R.string.pnl_close_rule), Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun PnlFigure(label: String, v: Double?, strong: Boolean = false, tone: Color = WorkisTheme.colors.ink) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, color = colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(WorkisMoney.text(v, "TRY"), fontFamily = WorkisMono, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp, color = tone)
    }
}

/** The cost figure: with the minus sign and the danger colour when above zero — never colour alone. */
@Composable
private fun PnlCostFigure(v: Double?) {
    val colors = WorkisTheme.colors
    val positive = (v ?: 0.0) > 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pnl_cost), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(
            (if (positive) "−" else "") + WorkisMoney.text(v, "TRY"),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            color = if (positive) colors.danger else colors.ink,
        )
    }
}

/** net + carry in → distributable · lead share · platform share (and the carry out) — the same split for "so far" and a closed month. */
@Composable
private fun PnlDistribution(net: Double?, carryIn: Double?, distributable: Double?, lead: Double?, platform: Double?, carryOut: Double?) {
    PnlFigure(stringResource(R.string.pnl_net), net)
    PnlFigure("+ " + stringResource(R.string.pnl_carry_in), carryIn)
    PnlFigure("→ " + stringResource(R.string.pnl_distributable), distributable, strong = true)
    PnlFigure(stringResource(R.string.pnl_lead_share), lead, strong = true)
    PnlFigure(stringResource(R.string.pnl_platform_share), platform)
    if (carryOut != null && carryOut != 0.0) {
        PnlFigure(stringResource(R.string.pnl_carry_out), carryOut, tone = if (carryOut < 0) WorkisTheme.colors.danger else WorkisTheme.colors.ink)
    }
}

/** One line. The explanation is the proof of the lead's share — printed whole, never truncated. */
@Composable
private fun PnlEntryRow(e: PnlEntry, page: ConsoleRegionPnl) {
    val colors = WorkisTheme.colors
    val cost = e.isCost == true
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pnlKindWord(e.kind, e.kindLabel, page), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
                e.firm?.takeIf { it.isNotEmpty() }?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink) }
                e.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
            }
            Text(
                (if (cost) "−" else "+") + WorkisMoney.text(e.amount, e.currency ?: "TRY"),
                fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (cost) colors.danger else colors.ink,
            )
        }
        e.why?.words?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted) }
        e.why?.legs?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint) }
    }
}

@Composable
private fun PnlAssetRow(a: PnlAsset) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(a.name.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
            a.kind?.takeIf { it.isNotEmpty() }?.let { k ->
                Text(
                    when (k) {
                        "vehicle" -> stringResource(R.string.pnl_asset_vehicle)
                        "station" -> stringResource(R.string.pnl_asset_station)
                        else -> k
                    },
                    fontSize = 12.sp, color = colors.muted,
                )
            }
        }
        val monthsWord = stringResource(R.string.pnl_months_word)
        Text(
            listOfNotNull(
                WorkisMoney.text(a.capex, "TRY"),
                a.months?.let { "$it $monthsWord" },
                a.start?.takeIf { it.isNotEmpty() }?.let { WorkisClock.day(it) },
            ).joinToString(" · "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted,
        )
    }
}

/** A closed month: the summary row; a tap opens its lines and its distribution. */
@Composable
private fun PnlCloseRow(c: PnlClose, page: ConsoleRegionPnl) {
    val colors = WorkisTheme.colors
    var open by rememberSaveable(c.id ?: c.period) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pnlMonth(c.period), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                    if (c.status == "paid") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                            Text(stringResource(R.string.pnl_status_paid), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SuccessGreen)
                        }
                    } else c.status?.takeIf { it.isNotEmpty() }?.let { s ->
                        StatusPill(if (s == "closed") stringResource(R.string.pnl_status_closed) else s)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(WorkisMoney.text(c.totals?.net, "TRY"), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                }
                c.totals?.let { tt ->
                    Text(
                        stringResource(R.string.pnl_revenue) + " " + WorkisMoney.text(tt.revenue, null) + " · " +
                            stringResource(R.string.pnl_cost) + " −" + WorkisMoney.text(tt.costs, null) + " · " +
                            stringResource(R.string.pnl_lead_share) + " " + WorkisMoney.text(tt.leadShare, null),
                        fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
                    )
                }
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val rows = c.entries.orEmpty()
                if (rows.isEmpty()) Text(stringResource(R.string.pnl_no_lines), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
                PnlRows(rows) { PnlEntryRow(it, page) }
                c.totals?.let { tt ->
                    Text(stringResource(R.string.pnl_distribution), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink, modifier = Modifier.padding(top = 4.dp))
                    PnlDistribution(tt.net, tt.carryIn, tt.distributable, tt.leadShare, tt.platformShare, tt.carryOut)
                    tt.params?.let { PnlHint(pnlParamsLine(it)) }
                }
            }
        }
    }
}

// ── Add cost (sheet) ─────────────────────────────────────────────────────────────────────

/** A documented cost of the open month: region from the page's regions, kind from `costKinds`, the amount as typed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PnlCostSheet(vm: ConsoleRegionPnlViewModel, page: ConsoleRegionPnl) {
    val colors = WorkisTheme.colors
    val added = stringResource(R.string.pnl_cost_added)
    ModalBottomSheet(
        onDismissRequest = vm::closeSheet,
        containerColor = colors.canvas,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.pnl_add_cost), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)

            PnlFieldLabel(stringResource(R.string.pnl_cost_region))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.regions.orEmpty().forEach { r ->
                    val id = r.id ?: return@forEach
                    PnlChip(r.region ?: id, selected = vm.region == id) { vm.region = id }
                }
            }

            PnlFieldLabel(stringResource(R.string.pnl_cost_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.costKinds.orEmpty().forEach { k -> PnlChip(pnlKindWord(k, null, page), selected = vm.kind == k) { vm.kind = k } }
            }

            OutlinedTextField(
                value = vm.amount, onValueChange = { vm.amount = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.pnl_cost_amount), fontSize = 15.sp, color = colors.faint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.ink).readingStep(),
                colors = pnlFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it }, minLines = 2,
                placeholder = { Text(stringResource(R.string.pnl_cost_note), fontSize = 15.sp, color = colors.faint) },
                textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 15.sp, color = colors.ink).readingStep(),
                colors = pnlFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            PnlHint(stringResource(R.string.pnl_cost_hint))
            vm.costError?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }

            Button(
                onClick = { vm.save(added) },
                enabled = vm.ready,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.pnl_cost_add), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            TextButton(onClick = vm::closeSheet, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), color = colors.muted)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PnlFieldLabel(words: String) {
    Text(words, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = WorkisTheme.colors.muted)
}

/** A choice chip: selected = kiremit border, never a fill. */
@Composable
private fun PnlChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(
        label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, color = colors.ink,
        modifier = Modifier
            .background(colors.surface, CircleShape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kiremit500 else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun pnlFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Kiremit500,
    unfocusedBorderColor = WorkisTheme.colors.border,
    focusedTextColor = WorkisTheme.colors.ink,
    unfocusedTextColor = WorkisTheme.colors.ink,
    cursorColor = Kiremit500,
)
