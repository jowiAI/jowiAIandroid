package ai.workis.jowi.ui.screens.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.SourcingBoard
import ai.workis.jowi.data.SourcingBoardRow
import ai.workis.jowi.data.SourcingInbox
import ai.workis.jowi.data.SourcingSettings
import ai.workis.jowi.data.SourcingState
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.launch

/*
 * Console → Tedarik (web /workis/console/tedarik/, page key `sourcing`, ledger 128). The desk's
 * board: ONE row per request out for quotes, in the server's order (the desk's move first), each
 * saying where it stands in the server's words and tone (`stand`). On top the lane's state in one
 * strip — Jowi asking by itself, the reply box — and the door to Tedarik › Ayarlar. A request
 * opens its SHEET (ConsoleSourcingSheet.kt: rows × firms), a firm its own screen.
 *
 * Nothing is derived here: the chips are the server's (`chips`, else `counts`), the colour and
 * the sentence `stand`, the switches are drawn by `canSet`. The field lead (`full: false`) gets
 * the same list, already walled by the server.
 */

// ---------- navigation ----------

private sealed interface SrcPage {
    data object Board : SrcPage
    data object Settings : SrcPage
    data class Sheet(val row: SourcingBoardRow) : SrcPage
    data class Firm(val row: SourcingBoardRow, val firmKey: String) : SrcPage
}

@Composable
fun ConsoleSourcingScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf<SrcPage>(SrcPage.Board) }
    // "Kaleme göre | Firmaya göre" stays as last picked while the page lives
    var face by rememberSaveable { mutableStateOf("item") }
    LaunchedEffect(Unit) { ScreenContext.clear() }

    BackHandler(enabled = page != SrcPage.Board) {
        page = when (val p = page) {
            is SrcPage.Firm -> SrcPage.Sheet(p.row)
            else -> SrcPage.Board
        }
    }

    when (val p = page) {
        SrcPage.Board -> ConsoleSub(stringResource(R.string.console_sourcing_title), onBack) {
            SourcingBoardPage(
                onSettings = { page = SrcPage.Settings },
                onOpen = { row -> page = SrcPage.Sheet(row) },
            )
        }
        SrcPage.Settings -> ConsoleSub(stringResource(R.string.src_settings), { page = SrcPage.Board }) {
            SourcingSettingsPage()
        }
        is SrcPage.Sheet -> {
            val vm: SourcingSheetViewModel = viewModel(key = "sourcing-sheet-" + p.row.id.orEmpty())
            ConsoleSub(p.row.title.orEmpty(), { page = SrcPage.Board }) {
                SourcingSheetHost(vm, p.row.id.orEmpty())
                SourcingSheetPage(vm, p.row, face, onFace = { face = it }, onFirm = { key -> page = SrcPage.Firm(p.row, key) })
            }
        }
        is SrcPage.Firm -> {
            val vm: SourcingSheetViewModel = viewModel(key = "sourcing-sheet-" + p.row.id.orEmpty())
            ConsoleSub(vm.sheet?.firm(p.firmKey)?.name.orEmpty(), { page = SrcPage.Sheet(p.row) }) {
                SourcingSheetHost(vm, p.row.id.orEmpty())
                SourcingFirmPage(vm, p.firmKey)
            }
        }
    }
}

// ---------- the board ----------

class ConsoleSourcingViewModel : ViewModel() {
    var page by mutableStateOf<SourcingBoard?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The chip held: a chip code (`all` = every row, `trial` = `isTrial`). */
    var filter by mutableStateOf("all")

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleSourcing() }
            refreshing = false
            if (p != null) {
                page = p; loadError = null
                val codes = srcChips(p).map { it.code }
                if (filter != "all" && filter !in codes) filter = "all"
            } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }
}

internal data class SrcChip(val code: String, val label: String?, val count: Int, val tone: String?)

/** The chips in the server's order (`chips`); an older answer without it → `counts` in the web's fixed order. */
internal fun srcChips(p: SourcingBoard): List<SrcChip> {
    val sent = p.chips.orEmpty().filter { !it.code.isNullOrEmpty() }
    if (sent.isNotEmpty()) return sent.map { SrcChip(it.code!!, it.label, it.count ?: 0, it.tone?.takeIf { t -> t.isNotEmpty() }) }
    val counts = p.counts.orEmpty()
    if (counts.isEmpty()) return emptyList()
    val known = listOf("all", "you", "send", "low", "wait", "ok", "trial")
    val tones = mapOf("you" to "k", "send" to "k", "low" to "w", "wait" to "n", "ok" to "g")
    val rest = counts.keys.filter { it !in known }.sorted()
    return (known.filter { k -> counts.containsKey(k) && (k != "trial" || (counts[k] ?: 0) > 0) } + rest)
        .map { SrcChip(it, null, counts[it] ?: 0, tones[it]) }
}

@Composable
private fun chipWord(c: SrcChip): String = when (c.code) {
    "all" -> stringResource(R.string.src_chip_all)
    "you" -> stringResource(R.string.src_chip_you)
    "send" -> stringResource(R.string.src_chip_send)
    "low" -> stringResource(R.string.src_chip_low)
    "wait" -> stringResource(R.string.src_chip_wait)
    "ok" -> stringResource(R.string.src_chip_ok)
    "trial" -> stringResource(R.string.src_chip_trial)
    else -> c.label?.takeIf { it.isNotEmpty() } ?: c.code
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcingBoardPage(onSettings: () -> Unit, onOpen: (SourcingBoardRow) -> Unit) {
    val vm: ConsoleSourcingViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    val page = vm.page
    when {
        page == null && vm.loadError != null -> SrcLoadError(vm.loadError.orEmpty()) { vm.load() }
        page == null -> SrcSpinner()
        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page.dormant == true) {
                    item {
                        SrcCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(WorkisIcons.Moon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.src_dormant), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                            }
                        }
                    }
                } else {
                    page.state?.let { s -> item { StateStrip(s, onSettings) } }
                    val rows = page.rows.orEmpty()
                    if (rows.isEmpty()) {
                        item {
                            SrcCard {
                                Text(
                                    stringResource(if (page.full == true) R.string.src_empty_full else R.string.src_empty_lead),
                                    fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint,
                                )
                            }
                        }
                    } else {
                        val chips = srcChips(page)
                        if (chips.isNotEmpty()) item { ChipRow(chips, vm.filter) { vm.filter = it } }
                        val shown = when (vm.filter) {
                            "all" -> rows
                            "trial" -> rows.filter { it.isTrial == true }
                            else -> rows.filter { it.stand?.code == vm.filter }
                        }
                        item { SrcSectionTitle(stringResource(R.string.src_requests)) }
                        if (shown.isEmpty()) item {
                            Text(stringResource(R.string.src_no_match), fontSize = 13.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        items(shown, key = { it.id ?: it.hashCode().toString() }) { r -> RequestRow(r) { if (!r.id.isNullOrEmpty()) onOpen(r) } }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

/** The lane's state: Jowi asking by itself, the reply box, and the Settings door. */
@Composable
private fun StateStrip(s: SourcingState, onSettings: () -> Unit) {
    val colors = WorkisTheme.colors
    SrcCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.src_outreach) + ":", fontSize = 13.sp, color = colors.muted)
            Text(
                stringResource(if (s.outreachOn == true) R.string.src_on else R.string.src_off),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (s.outreachOn == true) colors.ink else colors.accentText,
            )
        }
        s.inbox?.let { InboxLine(it) }
        HorizontalDivider(color = colors.border)
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(vertical = 2.dp),
        ) {
            Icon(WorkisIcons.Key, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
            Text(stringResource(R.string.src_settings), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
            Text("›", fontSize = 18.sp, color = colors.faint)
        }
    }
}

/** Not connected is kiremit, with the word and what it means — never colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxLine(box: SourcingInbox) {
    val colors = WorkisTheme.colors
    if (box.connected == true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.src_inbox) + ":", fontSize = 13.sp, color = colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.src_connected), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.success)
            }
            box.lastRunAt?.takeIf { it.isNotEmpty() }?.let {
                Text("· " + stringResource(R.string.src_last_read) + " " + it, fontSize = 12.sp, color = colors.faint)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.src_inbox) + ":", fontSize = 13.sp, color = colors.muted)
                Icon(WorkisIcons.Warning, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(13.dp))
                Text(
                    stringResource(if (box.hasConnection == true) R.string.src_connected_unreadable else R.string.src_not_connected),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText,
                )
            }
            Text(stringResource(R.string.src_not_read_word), fontSize = 12.sp, lineHeight = 16.sp, color = colors.accentText)
        }
    }
}

@Composable
private fun ChipRow(chips: List<SrcChip>, filter: String, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { c ->
            val on = filter == c.code
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .border(if (on) 1.5.dp else 1.dp, if (on) Kiremit500 else colors.border, CircleShape)
                    .clickable { onPick(c.code) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                // the web's dot: the tone the rows of that chip carry (the chip's word stands beside it)
                c.tone?.let { Box(Modifier.size(7.dp).background(srcToneColor(it), CircleShape)) }
                val ink = if (on) colors.ink else colors.muted
                Text(chipWord(c), fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (c.count == 0 && !on) ink.copy(alpha = 0.5f) else ink)
                Text("${c.count}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (c.count == 0 && !on) ink.copy(alpha = 0.5f) else ink)
            }
        }
    }
}

@Composable
private fun RequestRow(r: SourcingBoardRow, onOpen: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(r.title.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val trialWord = stringResource(R.string.src_chip_trial)
            val sub = listOfNotNull(
                r.buyer?.takeIf { it.isNotEmpty() },
                r.sentAt?.takeIf { it.isNotEmpty() }?.let { WorkisClock.day(it) },
                if (r.isTrial == true) trialWord else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = colors.muted)
            r.stand?.words?.takeIf { it.isNotEmpty() }?.let { SrcToneTag(it, r.stand.tone) }
        }
        Text("›", fontSize = 18.sp, color = colors.faint, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---------- Tedarik › Ayarlar ----------

class ConsoleSourcingSettingsViewModel : ViewModel() {
    var page by mutableStateOf<SourcingSettings?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var noteIsError by mutableStateOf(false)
        private set
    var buyers by mutableStateOf("")
    var emails by mutableStateOf("")

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleSourcingSettings() }
            refreshing = false
            if (p != null) {
                page = p; loadError = null
                buyers = p.trialAccounts.orEmpty().joinToString(", ")
                emails = p.trialAddresses.orEmpty().joinToString(", ")
            } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    fun flip(join: Boolean, on: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.consoleSourcingOutreach(if (join) jsonBody("what" to "join", "on" to on) else jsonBody("on" to on))
            }
            busy = false
            val ok = r?.success == true
            note = if (ok) r?.message else r?.error ?: r?.message ?: err
            noteIsError = !ok
            load()
        }
    }

    fun saveTrial(saved: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.consoleSourcingTrialBuyers(jsonBody("buyers" to buyers.trim(), "emails" to emails.trim()))
            }
            busy = false
            val ok = r?.success == true
            note = if (ok) r?.message ?: saved else r?.error ?: r?.message ?: err
            noteIsError = !ok
            if (ok) load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcingSettingsPage() {
    val vm: ConsoleSourcingSettingsViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    val p = vm.page
    when {
        p == null && vm.loadError != null -> SrcLoadError(vm.loadError.orEmpty()) { vm.load() }
        p == null -> SrcSpinner()
        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            val canSet = p.canSet == true
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                vm.note?.takeIf { it.isNotEmpty() }?.let { n -> item { SrcNote(n, vm.noteIsError) } }
                // the two switches — turned only by the coordinator itself (`canSet`), read by everyone else
                item {
                    SrcCard {
                        SwitchLine(stringResource(R.string.src_set_outreach_title), p.outreachOn == true, canSet, !vm.busy) { vm.flip(join = false, on = it) }
                        if (p.outreachOn != true) SrcHint(stringResource(R.string.src_set_outreach_off))
                        HorizontalDivider(color = colors.border)
                        SwitchLine(stringResource(R.string.src_join), p.joinOn == true, canSet, !vm.busy) { vm.flip(join = true, on = it) }
                        SrcHint(stringResource(if (p.joinOn == true) R.string.src_join_on_hint else R.string.src_join_off_hint))
                    }
                }
                // the reply box: read here; its connection form is the web's
                p.inbox?.let { box ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SrcSectionTitle(stringResource(R.string.src_inbox))
                            SrcCard {
                                box.address?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, color = colors.ink) }
                                if (box.connected == true) {
                                    val via = when {
                                        box.via == "imap" -> " · IMAP"
                                        !box.via.isNullOrEmpty() -> " · Google"
                                        else -> ""
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(WorkisIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.src_connected) + via, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.success)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        SrcHint(stringResource(R.string.src_inbox_every))
                                        box.lastRunAt?.takeIf { it.isNotEmpty() }?.let { SrcHint(stringResource(R.string.src_last_read) + " " + it) }
                                        SrcHint("${box.taken ?: 0} " + stringResource(R.string.src_replies_taken))
                                        box.lastReplyAt?.takeIf { it.isNotEmpty() }?.let { SrcHint(stringResource(R.string.src_last_reply) + " " + it) }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(WorkisIcons.Warning, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(if (box.hasConnection == true) R.string.src_connected_unreadable else R.string.src_not_connected),
                                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                                        )
                                    }
                                    Text(stringResource(R.string.src_not_read_word), fontSize = 13.sp, lineHeight = 18.sp, color = colors.accentText)
                                }
                            }
                            SrcHint(stringResource(R.string.src_inbox_on_web), Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                // trial buyers: read-out for everyone, the two fields + Save for the coordinator
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SrcSectionTitle(stringResource(R.string.src_trial_buyers))
                        SrcCard {
                            Readout(stringResource(R.string.src_trial_accounts_label), p.trialAccounts.orEmpty())
                            Readout(stringResource(R.string.src_trial_addresses_label), p.trialAddresses.orEmpty())
                            if (canSet) {
                                SrcField(vm.buyers, { vm.buyers = it }, stringResource(R.string.src_trial_accounts), KeyboardType.Email)
                                SrcField(vm.emails, { vm.emails = it }, stringResource(R.string.src_trial_addresses), KeyboardType.Email, singleLine = false)
                                val saved = stringResource(R.string.src_saved)
                                SrcPrimaryButton(stringResource(R.string.src_save), enabled = !vm.busy, busy = vm.busy) { vm.saveTrial(saved) }
                            }
                        }
                        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SrcHint(stringResource(R.string.src_trial_buyers_hint))
                            if (canSet) SrcHint(stringResource(R.string.src_trial_save_hint))
                        }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
private fun SwitchLine(title: String, on: Boolean, canSet: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, modifier = Modifier.weight(1f))
        if (canSet) Switch(
            checked = on, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.success, checkedThumbColor = colors.surface),
        ) else Text(stringResource(if (on) R.string.src_on else R.string.src_off), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
    }
}

@Composable
private fun Readout(label: String, values: List<String>) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = colors.muted)
        Text(
            if (values.isEmpty()) stringResource(R.string.src_none) else values.joinToString(", "),
            fontSize = 14.sp, lineHeight = 19.sp, color = if (values.isEmpty()) colors.faint else colors.ink,
        )
    }
}

// ---------- shared bits (the board, the settings, the sheet, the firm) ----------

/** The server's tone letter → colour (k kiremit · w warning · g green · n neutral). Always drawn WITH the server's words. */
@Composable
internal fun srcToneColor(tone: String?): Color {
    val colors = WorkisTheme.colors
    return when (tone) {
        "k" -> colors.accentText
        "w" -> colors.beige
        "g" -> colors.success
        else -> colors.muted
    }
}

internal fun srcToneIcon(tone: String?): ImageVector = when (tone) {
    "k", "w" -> WorkisIcons.Warning
    "g" -> WorkisIcons.Check
    else -> WorkisIcons.Clock
}

/** The server's sentence in its tone: icon + words on a quiet well of the same colour. */
@Composable
internal fun SrcToneTag(words: String, tone: String?) {
    val c = srcToneColor(tone)
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(srcToneIcon(tone), contentDescription = null, tint = c, modifier = Modifier.size(11.dp))
        Text(words, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = c)
    }
}

/** A card: surface, radius 20, hairline border. */
@Composable
internal fun SrcCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun SrcSectionTitle(title: String) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WorkisTheme.colors.ink, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
internal fun SrcHint(words: String, modifier: Modifier = Modifier) {
    Text(words, fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.faint, modifier = modifier)
}

/** The last act's sentence as the server sent it — beige note, or danger on a refusal. */
@Composable
internal fun SrcNote(words: String, error: Boolean) {
    val colors = WorkisTheme.colors
    Text(
        words, fontSize = 13.sp, lineHeight = 18.sp, color = if (error) colors.danger else colors.beige,
        modifier = Modifier.fillMaxWidth()
            .background(if (error) colors.danger.copy(alpha = 0.08f) else colors.beigeBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

@Composable
internal fun SrcSpinner() {
    Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
    }
}

@Composable
internal fun SrcLoadError(words: String, onRetry: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger)
        SrcBorderButton(stringResource(R.string.retry), onClick = onRetry)
    }
}

/** The screen's ONE filled kiremit action. */
@Composable
internal fun SrcPrimaryButton(text: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
        else Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnKiremitFill)
    }
}

/** Every other button: bordered, ink. */
@Composable
internal fun SrcBorderButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        icon?.let {
            Icon(it, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
    }
}

@Composable
internal fun SrcField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val colors = WorkisTheme.colors
    OutlinedTextField(
        value = value, onValueChange = onValue, singleLine = singleLine,
        label = { Text(label, fontSize = 12.sp, color = colors.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 14.sp, color = colors.ink).readingStep(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A small chip: neutral ("üye"), or in a tone ("il yazılmamış" in kiremit) — with its word. */
@Composable
internal fun SrcSmallChip(text: String, tone: String? = null) {
    val colors = WorkisTheme.colors
    val c = if (tone == null) colors.muted else srcToneColor(tone)
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c,
        modifier = Modifier
            .background((if (tone == null) colors.ink else c).copy(alpha = if (tone == null) 0.07f else 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
