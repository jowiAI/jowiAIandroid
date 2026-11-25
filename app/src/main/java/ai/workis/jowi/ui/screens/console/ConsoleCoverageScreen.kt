package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleCoveragePage
import ai.workis.jowi.data.CoverageBatch
import ai.workis.jowi.data.CoverageBatchFirm
import ai.workis.jowi.data.CoverageLabels
import ai.workis.jowi.data.CoverageMoving
import ai.workis.jowi.data.CoverageNeed
import ai.workis.jowi.data.CoverageNet
import ai.workis.jowi.data.CoverageResearch
import ai.workis.jowi.data.CoverageRow
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.covBatchOf
import ai.workis.jowi.data.covBool
import ai.workis.jowi.data.covCall
import ai.workis.jowi.data.covStr
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Console → Kapsama (web /workis/console/kapsama/, page key `coverage`, ledger 128): per Workis
 * group, how many sellers cover it with an approved catalogue, what buyers asked in the window,
 * and the asks no group covers. A group (or an unmet ask in its own words) can be researched
 * (~60 s, Jowi's web sweep — a long-timeout client, `CoverageNet.slow`); the firms found are
 * ticked and their RAW objects go to `batch/` exactly as received, where they become candidates
 * whose catalogues are read — nothing is e-mailed. The batch is re-read every 4 s while moving.
 */
class ConsoleCoverageViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleCoveragePage?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The sector filter; "" = every sector. */
    var sector by mutableStateOf("")
    /** One research box per group ("g:<id>") or unmet ask ("u:<text>"). */
    val slots = mutableStateMapOf<String, CovSlot>()

    private val lang get() = Graph.language.value

    /** The web offers no window choice (`?days=` only from the address bar): fixed 30. */
    val days = 30

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleCoverage(days) }
            refreshing = false
            if (p != null) { page = p; loadError = null } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    /** The server's sentence for a failed call; a lost connection or a timeout says so in the app's words. */
    private fun failure(status: Int, json: JsonObject): CovSlot =
        if (status == 0) CovSlot(noteRes = R.string.cov_connection_error, noteIsError = true)
        else CovSlot(
            note = json.covStr("error").ifEmpty { json.covStr("detail") }.ifEmpty { statusMessage(status, lang) },
            noteIsError = true,
        )

    private fun ok(status: Int, json: JsonObject) = status in 200..299 && json.covBool("success") != false

    /** A fresh answer opens the box with the firms the batch would take ticked. */
    private fun open(key: String, json: JsonObject) {
        val res = CoverageResearch(json)
        slots[key] = CovSlot(result = res, ticked = res.firms.indices.filter { covAdoptable(res.firms[it]) }.toSet())
    }

    fun researchGroup(id: String) {
        val key = "g:$id"
        if (slots[key]?.busy == true) return
        slots[key] = CovSlot(busy = true)
        viewModelScope.launch {
            val (status, json) = covCall { CoverageNet.slow.consoleCoverageResearchGroup(id, jsonBody()) }
            if (ok(status, json)) { open(key, json); load() }       // the door stamped the row
            else slots[key] = failure(status, json)
        }
    }

    fun researchText(text: String) {
        val key = "u:$text"
        if (text.isEmpty() || slots[key]?.busy == true) return
        slots[key] = CovSlot(busy = true)
        viewModelScope.launch {
            val (status, json) = covCall { CoverageNet.slow.consoleCoverageResearchText(jsonBody("text" to text)) }
            if (ok(status, json)) open(key, json) else slots[key] = failure(status, json)
        }
    }

    /** The stored last result — 404 means nothing is kept for the group, not an error. */
    fun showFound(id: String) {
        val key = "g:$id"
        viewModelScope.launch {
            val (status, json) = covCall { Graph.api.consoleCoverageFound(id) }
            when {
                ok(status, json) -> open(key, json)
                status == 404 -> slots[key] = CovSlot(noteRes = R.string.cov_no_results)
                else -> slots[key] = failure(status, json)
            }
        }
    }

    fun toggle(key: String, i: Int) {
        val s = slots[key] ?: return
        slots[key] = s.copy(ticked = if (i in s.ticked) s.ticked - i else s.ticked + i)
    }

    fun close(key: String) { slots.remove(key) }

    fun adopt(key: String) {
        val s = slots[key] ?: return
        val res = s.result ?: return
        // the ticked firm objects exactly as the research answer sent them
        val firms = s.ticked.sorted().mapNotNull { res.firms.getOrNull(it) }
        if (firms.isEmpty() || s.adopting) return
        slots[key] = s.copy(adopting = true, note = null, noteRes = null)
        viewModelScope.launch {
            val (status, json) = covCall { Graph.api.consoleCoverageBatch(jsonBody("firms" to JsonArray(firms))) }
            val cur = slots[key] ?: return@launch
            val batch = if (ok(status, json)) covBatchOf(json["batch"]) else null
            slots[key] = if (batch != null) cur.copy(adopting = false, batch = batch)
            else failure(status, json).let { f -> cur.copy(adopting = false, note = f.note, noteRes = f.noteRes, noteIsError = true) }
        }
    }

    /** Each batch still moving is read once more (the screen calls this every few seconds). */
    fun pollBatches() {
        slots.forEach { (key, s) ->
            val b = s.batch ?: return@forEach
            val id = b.id ?: return@forEach
            if (!b.running) return@forEach
            viewModelScope.launch {
                val (status, json) = covCall { Graph.api.consoleCoverageBatchState(id) }
                if (status in 200..299) covBatchOf(json["batch"])?.let { nb -> slots[key]?.let { slots[key] = it.copy(batch = nb) } }
            }
        }
    }
}

data class CovSlot(
    val busy: Boolean = false,
    val result: CoverageResearch? = null,
    /** Indexes into `result.firms` that are ticked. */
    val ticked: Set<Int> = emptySet(),
    /** The server's sentence, as sent. */
    val note: String? = null,
    /** The app's own sentence (connection lost, nothing stored). */
    val noteRes: Int? = null,
    val noteIsError: Boolean = false,
    val adopting: Boolean = false,
    val batch: CoverageBatch? = null,
)

/** A firm the batch would take: a site that answered and not already on Workis (the web's own filter). */
private fun covAdoptable(f: JsonObject): Boolean =
    (f.covBool("site_ok") ?: true) && f.covBool("known") != true && f.covStr("website").isNotEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleCoverageScreen(onBack: () -> Unit) {
    val vm: ConsoleCoverageViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    // every 4 s, each batch still moving is read again; stops when the page goes
    LaunchedEffect(Unit) {
        while (true) { delay(4_000); vm.pollBatches() }
    }
    ConsoleSub(stringResource(R.string.console_coverage_title), onBack) {
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { CovTotals(page, vm.days) }
                    item { CovUnmet(page.uncovered.orEmpty(), vm) }
                    item { CovGroups(page.rows.orEmpty(), vm) }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

// ── building blocks ──────────────────────────────────────────────────────────────────────

@Composable
private fun CovSection(icon: ImageVector, title: String, count: Int, hint: String?, content: @Composable () -> Unit) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
        hint?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun CovHint(words: String, modifier: Modifier = Modifier) {
    Text(words, fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.faint, modifier = modifier)
}

/** An icon + its word in one tint — status is never colour alone. */
@Composable
private fun CovLabel(icon: ImageVector, words: String, tint: Color, size: Int = 12) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size + 1).dp))
        Text(words, fontSize = size.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
private fun CovButton(label: String, icon: ImageVector?, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    OutlinedButton(onClick = onClick, enabled = enabled) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (enabled) colors.ink else colors.faint)
    }
}

/** The group's status: the row's `statusWords`, else the page's `labels.status`, else the app's word. Icon and colour stay on the code. */
@Composable
private fun covStatusWord(r: CoverageRow, labels: CoverageLabels?): String =
    r.statusWords?.takeIf { it.isNotEmpty() }
        ?: labels?.status?.get(r.status.orEmpty())?.takeIf { it.isNotEmpty() }
        ?: covStatusWord(r.status)

@Composable
private fun covStatusWord(s: String?): String = when (s) {
    "short" -> stringResource(R.string.cov_status_short)
    "thin" -> stringResource(R.string.cov_status_thin)
    "ok" -> stringResource(R.string.cov_status_ok)
    else -> s.orEmpty()
}

private fun covStatusIcon(s: String?): ImageVector = when (s) {
    "short" -> WorkisIcons.Cross
    "thin" -> WorkisIcons.Warning
    "ok" -> WorkisIcons.Check
    else -> WorkisIcons.Dot
}

@Composable
private fun covStatusColor(s: String?): Color {
    val colors = WorkisTheme.colors
    return when (s) {
        "short" -> colors.danger
        "thin" -> colors.beige
        "ok" -> colors.success
        else -> colors.muted
    }
}

/** One figure of the strip: the number in mono, its word under it. */
@Composable
private fun CovStat(value: Int?, word: String, tint: Color, modifier: Modifier) {
    val colors = WorkisTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${value ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = tint)
        Text(word, fontSize = 11.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CovPair(label: String, value: String) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Text(value, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
    }
}

// ── sections ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CovTotals(page: ConsoleCoveragePage, days: Int) {
    val colors = WorkisTheme.colors
    val tot = page.totals
    CovSection(WorkisIcons.ChartBars, stringResource(R.string.cov_title), 0, stringResource(R.string.cov_explain)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val w = Modifier.weight(1f)
            CovStat(tot?.groups, stringResource(R.string.cov_totals_groups), colors.ink, w)
            // a count above zero carries its status tint; the word under it says what it counts
            CovStat(tot?.short, stringResource(R.string.cov_status_short), if ((tot?.short ?: 0) > 0) colors.danger else colors.ink, w)
            CovStat(tot?.thin, stringResource(R.string.cov_status_thin), if ((tot?.thin ?: 0) > 0) colors.beige else colors.ink, w)
            CovStat(tot?.asks, stringResource(R.string.cov_totals_asks), colors.ink, w)
            CovStat(tot?.uncovered, stringResource(R.string.cov_totals_uncovered), colors.ink, w)
        }
        HorizontalDivider(color = colors.border)
        CovPair(
            stringResource(R.string.cov_per_segment),
            "${stringResource(R.string.cov_min)} ${page.floor ?: 0} · ${stringResource(R.string.cov_max)} ${page.target ?: 0}",
        )
        CovPair(stringResource(R.string.cov_window), "${page.days ?: days} ${stringResource(R.string.days_word)}")
    }
}

@Composable
private fun CovUnmet(needs: List<CoverageNeed>, vm: ConsoleCoverageViewModel) {
    val colors = WorkisTheme.colors
    CovSection(WorkisIcons.Bubble, stringResource(R.string.cov_unmet), needs.size, stringResource(R.string.cov_unmet_hint)) {
        if (needs.isEmpty()) CovLabel(WorkisIcons.Check, stringResource(R.string.cov_unmet_empty), colors.success, 13)
        needs.forEachIndexed { i, need ->
            if (i > 0) HorizontalDivider(color = colors.border)
            val text = need.text.orEmpty()
            val key = "u:$text"
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${need.count ?: 0}×", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                    Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, modifier = Modifier.weight(1f))
                }
                need.last?.takeIf { it.isNotEmpty() }?.let { CovHint("${stringResource(R.string.cov_last_asked)} ${WorkisClock.day(it)}") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CovButton(stringResource(R.string.cov_research_ask), WorkisIcons.Search, enabled = text.isNotEmpty() && vm.slots[key]?.busy != true) {
                        vm.researchText(text)
                    }
                }
                CovSlotRows(key, vm)
            }
        }
    }
}

@Composable
private fun CovGroups(all: List<CoverageRow>, vm: ConsoleCoverageViewModel) {
    val colors = WorkisTheme.colors
    val sectors = all.mapNotNull { it.sector?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted()
    val rows = all.filter { vm.sector.isEmpty() || it.sector == vm.sector }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the web's sector chips — selected = kiremit border, a tap on the selected one clears
        if (sectors.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CovChip(stringResource(R.string.cov_all_sectors), vm.sector.isEmpty()) { vm.sector = "" }
                sectors.forEach { s -> CovChip(s, vm.sector == s) { vm.sector = if (vm.sector == s) "" else s } }
            }
        }
        CovSection(WorkisIcons.Grid, stringResource(R.string.cov_groups), rows.size, null) {
            if (rows.isEmpty()) CovHint(stringResource(R.string.cov_groups_empty))
            rows.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = colors.border)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CovGroupRow(r, vm)
                    r.id?.let { CovSlotRows("g:$it", vm) }
                }
            }
        }
    }
}

@Composable
private fun CovChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1,
        modifier = Modifier
            .background(colors.surface, CircleShape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kiremit500 else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CovGroupRow(r: CoverageRow, vm: ConsoleCoverageViewModel) {
    val colors = WorkisTheme.colors
    val id = r.id.orEmpty()
    val busy = vm.slots["g:$id"]?.busy == true
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOfNotNull(r.sector?.takeIf { it.isNotEmpty() }, r.parent?.takeIf { it.isNotEmpty() }).joinToString(" › ").takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, color = colors.muted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.group.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink, modifier = Modifier.weight(1f, fill = false))
            if ((r.subGroups ?: 0) > 0) Text(stringResource(R.string.cov_sub_groups), fontSize = 12.sp, color = colors.faint)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CovLabel(
                covStatusIcon(r.status),
                "${covStatusWord(r, vm.page?.labels)} · ${r.nSellers ?: 0} ${stringResource(R.string.cov_sellers_word)}",
                covStatusColor(r.status),
            )
            Spacer(Modifier.weight(1f))
            Text("${stringResource(R.string.cov_totals_asks)} ${r.asks ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink)
            r.uncoveredRows?.takeIf { it > 0 }?.let {
                Text("${stringResource(R.string.cov_unmatched)} $it", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted)
            }
        }
        val sellers = r.sellers.orEmpty()
        if (sellers.isEmpty()) CovHint(stringResource(R.string.cov_no_seller))
        else {
            val virtual = stringResource(R.string.cov_virtual)
            Text(
                sellers.joinToString(", ") { s -> s.name.orEmpty() + " (${s.families ?: 0})" + (if (s.seat == "shadow") " · $virtual" else "") },
                fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        CovHint("${stringResource(R.string.cov_last_research)}: ${covLastResearch(r)}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (r.found != null && id.isNotEmpty()) CovButton(stringResource(R.string.cov_show_found), WorkisIcons.Archive, enabled = !busy) { vm.showFound(id) }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            else if (id.isNotEmpty()) CovButton(stringResource(R.string.cov_research), WorkisIcons.Search) { vm.researchGroup(id) }
        }
    }
}

/** The web's "Last research" cell: the stored result (date · n firms · automatic or by hand), else the stamp, else "never". */
@Composable
private fun covLastResearch(r: CoverageRow): String {
    val firms = stringResource(R.string.cov_firms)
    r.found?.let { f ->
        return listOf(
            WorkisClock.day(f.at), "${f.firms ?: 0} $firms",
            stringResource(if (f.auto == true) R.string.cov_auto else R.string.cov_by_hand),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    r.lastResearch?.takeIf { it.isNotEmpty() }?.let { at ->
        return WorkisClock.day(at) + (r.researchFirms?.let { " · $it $firms" } ?: "")
    }
    return stringResource(R.string.cov_never)
}

// ── the research box ─────────────────────────────────────────────────────────────────────

@Composable
private fun CovSlotRows(key: String, vm: ConsoleCoverageViewModel) {
    val colors = WorkisTheme.colors
    val slot = vm.slots[key] ?: return
    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (slot.busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cov_researching), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                CovHint(stringResource(R.string.cov_researching_hint))
            }
        }
        (slot.note ?: slot.noteRes?.let { stringResource(it) })?.let { note ->
            if (slot.noteIsError) CovLabel(WorkisIcons.Cross, note, colors.danger, 13)
            else Text(note, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
        }
        val res = slot.result
        if (res != null && !slot.busy) CovResult(key, res, slot, vm)
        slot.batch?.let { CovBatchRows(it, vm.page?.labels) }
    }
}

@Composable
private fun CovResult(key: String, res: CoverageResearch, slot: CovSlot, vm: ConsoleCoverageViewModel) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            (listOf("${res.firms.size} ${stringResource(R.string.cov_firms)}", "${res.searches} ${stringResource(R.string.cov_searches)}") +
                (if (res.known > 0) listOf("${res.known} ${stringResource(R.string.cov_already_on_workis)}") else emptyList()) +
                (if (res.segment.isEmpty()) emptyList() else listOf(res.segment))).joinToString(" · "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, color = colors.muted,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { vm.close(key) }) { Text(stringResource(R.string.cov_close), fontSize = 12.sp, color = colors.muted) }
    }
    if (res.firms.isEmpty()) CovHint(stringResource(R.string.cov_no_results))
    val locked = slot.batch != null || slot.adopting
    res.firms.forEachIndexed { i, f -> CovFirmRow(f, ticked = i in slot.ticked, locked = locked) { vm.toggle(key, i) } }
    if (slot.batch == null && res.firms.isNotEmpty()) {
        if (res.firms.none(::covAdoptable)) CovHint(stringResource(R.string.cov_nothing_to_adopt))
        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (slot.adopting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            else CovButton("${stringResource(R.string.cov_adopt_ticked)} (${slot.ticked.size})", WorkisIcons.People, enabled = slot.ticked.isNotEmpty()) { vm.adopt(key) }
            CovHint(stringResource(R.string.cov_tick_hint))
        }
    }
}

@Composable
private fun covRoleWord(r: String): String? = when (r) {
    "üretici" -> stringResource(R.string.cov_role_manufacturer)
    "distribütör" -> stringResource(R.string.cov_role_distributor)
    "bayi" -> stringResource(R.string.cov_role_dealer)
    else -> null
}

/** One found firm, read from its raw object — only what is drawn is decoded. */
@Composable
private fun CovFirmRow(f: JsonObject, ticked: Boolean, locked: Boolean, onToggle: () -> Unit) {
    val colors = WorkisTheme.colors
    val can = covAdoptable(f)
    val website = f.covStr("website")
    val siteOk = f.covBool("site_ok") ?: true
    val meta = listOfNotNull(covRoleWord(f.covStr("role")), f.covStr("city").takeIf { it.isNotEmpty() })
    val why = f.covStr("why").ifEmpty { f.covStr("note") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = can && !locked, onClick = onToggle).padding(vertical = 2.dp),
    ) {
        // the tick: a hollow ring, filled with its check when ticked; faded when the batch would not take the firm
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .background(if (ticked) Kiremit500 else Color.Transparent, CircleShape)
                .border(1.5.dp, (if (ticked) Kiremit500 else colors.muted).copy(alpha = if (can) 1f else 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (ticked) Icon(WorkisIcons.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(f.covStr("company").ifEmpty { website }, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                when {
                    website.isEmpty() -> CovLabel(WorkisIcons.Dot, stringResource(R.string.cov_no_site), colors.faint, 11)
                    siteOk -> CovLabel(WorkisIcons.Check, stringResource(R.string.cov_site_answered), colors.success, 11)
                    else -> CovLabel(WorkisIcons.Cross, stringResource(R.string.cov_site_unreachable), colors.danger, 11)
                }
                if (f.covBool("known") == true) CovLabel(WorkisIcons.Shield, stringResource(R.string.cov_already_on_workis), colors.muted, 11)
                if (f.covBool("confirmed") != true) CovLabel(WorkisIcons.QuestionCircle, stringResource(R.string.cov_not_verified), colors.beige, 11)
            }
            if (website.isNotEmpty()) Text(website, fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (why.isNotEmpty()) Text(why, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A batch firm's state: its `stateWords`, else the page's `labels.state`, else the app's word. Icon and colour stay on the code. */
@Composable
private fun covStateWord(f: CoverageBatchFirm, labels: CoverageLabels?): String =
    f.stateWords?.takeIf { it.isNotEmpty() }
        ?: labels?.state?.get(f.state.orEmpty())?.takeIf { it.isNotEmpty() }
        ?: covStateWord(f.state)

@Composable
private fun covStateWord(s: String?): String = when (s) {
    "queued" -> stringResource(R.string.cov_state_queued)
    "candidate" -> stringResource(R.string.cov_state_candidate)
    "reading" -> stringResource(R.string.cov_state_reading)
    "read" -> stringResource(R.string.cov_state_read)
    "failed" -> stringResource(R.string.cov_state_failed)
    "skipped" -> stringResource(R.string.cov_state_skipped)
    else -> s.orEmpty()
}

private fun covStateIcon(s: String?): ImageVector = when (s) {
    "read" -> WorkisIcons.Check
    "failed" -> WorkisIcons.Cross
    "skipped" -> WorkisIcons.Dot
    else -> WorkisIcons.Clock
}

@Composable
private fun covStateColor(s: String?): Color {
    val colors = WorkisTheme.colors
    return when (s) {
        "read" -> colors.success
        "failed" -> colors.danger
        "skipped" -> colors.faint
        else -> colors.accentText
    }
}

/** The batch's `note` keys in the app's words — only for a server that sends no `noteWords`; an unknown note as it came. */
@Composable
private fun covNoteWords(note: String?): String = when (note.orEmpty()) {
    "", "born" -> ""
    "known" -> stringResource(R.string.cov_already_on_workis)
    "site" -> stringResource(R.string.cov_site_unreachable)
    "existing" -> stringResource(R.string.cov_note_existing)
    "already read" -> stringResource(R.string.cov_note_already_read)
    "plan" -> stringResource(R.string.cov_note_plan)
    "continues" -> stringResource(R.string.cov_note_continues)
    else -> note.orEmpty()
}

@Composable
private fun CovBatchRows(b: CoverageBatch, labels: CoverageLabels?) {
    val colors = WorkisTheme.colors
    b.firms.orEmpty().forEach { f ->
        val moving = b.state != "done" && f.state in CoverageMoving
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    f.company?.takeIf { it.isNotEmpty() } ?: f.host.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (moving) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Kiremit500)
                CovLabel(covStateIcon(f.state), covStateWord(f, labels), covStateColor(f.state))
            }
            if (f.state == "read") CovHint("${f.families ?: 0} ${stringResource(R.string.cov_families_awaiting)}")
            // the server's words whenever the key came ("" = nothing, e.g. `born`); ours only for an older server
            (f.noteWords ?: covNoteWords(f.note)).takeIf { it.isNotEmpty() }?.let { CovHint(it) }
        }
    }
}
