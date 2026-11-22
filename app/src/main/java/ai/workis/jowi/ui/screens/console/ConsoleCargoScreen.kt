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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CargoNetBroadcast
import ai.workis.jowi.data.CargoNetObjection
import ai.workis.jowi.data.CargoNetRun
import ai.workis.jowi.data.CargoNetShipment
import ai.workis.jowi.data.CargoNetVehicle
import ai.workis.jowi.data.ConsoleCargo
import ai.workis.jowi.data.ScreenContext
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
import kotlin.math.abs

/**
 * Console → Kargo: the network's control tower (web /workis/console/kargo/, page key `cargo`,
 * ledger 114). The rating objections (carrier agreement 7.1: keep or remove), the jobs still
 * waiting for a carrier, everything not yet delivered, today's routes with "plan route" per
 * vehicle, the platform tariff, the consolidation numbers and the Workis-fleet tick. Who may
 * write is the server's flag (`canWrite`, ledger 115): without it the tariff, the numbers and
 * the fleet are read-only and no verdict button is drawn; route planning is open to every seat.
 */
class ConsoleCargoViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleCargo?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The tariff as typed, per level; seeded from the server's numbers. */
    val fees = mutableStateMapOf<String, String>()
    var tariffNote by mutableStateOf<String?>(null)
        private set
    var tariffSaved by mutableStateOf(false)
        private set
    /** The consolidation numbers as typed, per key. */
    val ruleTexts = mutableStateMapOf<String, String>()
    var rulesNote by mutableStateOf<String?>(null)
        private set
    var rulesSaved by mutableStateOf(false)
        private set
    /** The fleet ticks as shown, per carrier id. */
    val fleet = mutableStateMapOf<String, Boolean>()
    val canWrite: Boolean get() = page?.canWrite == true
    var busy by mutableStateOf(false)
        private set
    /** The server's word after an action (a refusal, or the planner's note). */
    var actionNote by mutableStateOf<String?>(null)
        private set
    var planning by mutableStateOf<String?>(null)
        private set

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleCargo() }
            refreshing = false
            if (p != null) {
                page = p; loadError = null
                fees.clear()
                p.tariff.orEmpty().forEach { row -> row.level?.let { fees[it] = WorkisMoney.field(row.feeUsd ?: 0.0) } }
                ruleTexts.clear()
                p.rules.orEmpty().forEach { row -> row.key?.let { ruleTexts[it] = WorkisMoney.field(row.usd ?: 0.0) } }
                fleet.clear()
                p.carriers.orEmpty().forEach { c -> c.id?.let { fleet[it] = c.ownFleet == true } }
            } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    fun type(level: String, text: String) { fees[level] = text; tariffSaved = false; tariffNote = null }

    /** The levels whose typed number differs from the server's — only these are sent. */
    fun changedFees(): Map<String, Double> = buildMap {
        page?.tariff.orEmpty().forEach { row ->
            val level = row.level ?: return@forEach
            val typed = WorkisMoney.wire(fees[level].orEmpty()).toDoubleOrNull() ?: return@forEach
            if (typed > 0 && abs(typed - (row.feeUsd ?: 0.0)) > 0.0001) put(level, typed)
        }
    }

    fun typeRule(key: String, text: String) { ruleTexts[key] = text; rulesSaved = false; rulesNote = null }

    /** The rules whose typed number differs from the server's (zero is a number here; negative is not sent). */
    fun changedRules(): Map<String, Double> = buildMap {
        page?.rules.orEmpty().forEach { row ->
            val key = row.key ?: return@forEach
            val typed = WorkisMoney.wire(ruleTexts[key].orEmpty()).toDoubleOrNull() ?: return@forEach
            if (typed >= 0 && abs(typed - (row.usd ?: 0.0)) > 0.0001) put(key, typed)
        }
    }

    fun saveRules() {
        val changed = changedRules()
        if (changed.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleCargoRules(jsonBody(*changed.map { it.key to it.value }.toTypedArray())) }
            busy = false
            if (r?.success == true) { rulesSaved = true; rulesNote = null; load() }
            else rulesNote = r?.error ?: r?.message ?: err
        }
    }

    /** The tick moves at once and goes back if the server refuses. */
    fun setFleet(carrierId: String, on: Boolean) {
        if (busy) return
        fleet[carrierId] = on
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleCargoOwnFleet(carrierId, jsonBody("on" to on)) }
            busy = false
            if (r?.success != true) { fleet[carrierId] = !on; actionNote = r?.error ?: r?.message ?: err }
        }
    }

    fun saveTariff() {
        val changed = changedFees()
        if (changed.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleCargoTariff(jsonBody(*changed.map { it.key to it.value }.toTypedArray())) }
            busy = false
            if (r?.success == true) { tariffSaved = true; tariffNote = null; load() }
            else tariffNote = r?.error ?: r?.message ?: err
        }
    }

    fun decide(id: String, decision: String, note: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleCargoObjection(id, jsonBody("decision" to decision, "note" to note.trim())) }
            busy = false
            actionNote = if (r?.success == true) null else r?.error ?: r?.message ?: err
            load()
        }
    }

    fun plan(vehicleId: String) {
        if (planning != null) return
        planning = vehicleId
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleCargoPlan(vehicleId, jsonBody()) }
            planning = null
            actionNote = r?.error ?: r?.message ?: r?.note ?: err
            load()
        }
    }
}

private data class Deciding(val objection: CargoNetObjection, val decision: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleCargoScreen(vm: ConsoleCargoViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    var deciding by androidx.compose.runtime.remember { mutableStateOf<Deciding?>(null) }
    var decisionNote by androidx.compose.runtime.remember { mutableStateOf("") }
    var face by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("net") }
    val page = vm.page
    when {
        page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
        page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
        }
        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                vm.actionNote?.takeIf { it.isNotEmpty() }?.let { note ->
                    item {
                        Text(
                            note, fontSize = 13.sp, lineHeight = 18.sp, color = Beige,
                            modifier = Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(12.dp)).padding(12.dp),
                        )
                    }
                }
                // two faces of one page (ledger 118): "Ağ" is what is watched and decided,
                // "Tarife" what is set — the numbers are not lost under a long network list
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("net" to R.string.cargo_net_tab_network, "tariff" to R.string.cargo_net_tab_tariff).forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = face == key, onClick = { face = key },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.ink.copy(alpha = 0.08f), activeContentColor = colors.ink,
                                    inactiveContainerColor = colors.surface, inactiveContentColor = colors.muted,
                                    activeBorderColor = colors.border, inactiveBorderColor = colors.border,
                                ),
                                icon = {},
                            ) { Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                if (face == "net") {
                    // agreement 7.1 — first on the phone: the one list that waits for a decision;
                    // its chip is blue when this reader decides (blue's one meaning)
                    item {
                        val rows = page.objections.orEmpty()
                        Section(WorkisIcons.Bubble, stringResource(R.string.cargo_net_objections), rows.size, stringResource(R.string.cargo_net_objections_hint), mine = vm.canWrite) {
                            if (rows.isEmpty()) Clear(stringResource(R.string.cargo_net_objections_empty))
                            Rows(rows) { rv ->
                                ObjectionRow(rv, canDecide = vm.canWrite, enabled = !vm.busy) { decision -> decisionNote = ""; deciding = Deciding(rv, decision) }
                            }
                        }
                    }
                    item {
                        val rows = page.broadcasts.orEmpty()
                        Section(WorkisIcons.Globe, stringResource(R.string.cargo_net_broadcasts), rows.size, stringResource(R.string.cargo_net_broadcasts_hint)) {
                            if (rows.isEmpty()) Clear(stringResource(R.string.cargo_net_broadcasts_empty))
                            Rows(rows) { BroadcastRow(it) }
                        }
                    }
                    item {
                        val rows = page.shipments.orEmpty()
                        Section(WorkisIcons.ShippingBox, stringResource(R.string.cargo_net_shipments), rows.size, stringResource(R.string.cargo_net_shipments_hint)) {
                            if (rows.isEmpty()) Empty(stringResource(R.string.cargo_net_shipments_empty))
                            Rows(rows) { ShipmentRow(it) }
                        }
                    }
                    // today's runs, then every vehicle of every carrier with its "plan route"
                    item {
                        val runs = page.runs.orEmpty()
                        val vehicles = page.carriers.orEmpty().flatMap { c -> c.vehicles.orEmpty().map { it to (c.short ?: c.company).orEmpty() } }
                        Section(WorkisIcons.Route, stringResource(R.string.cargo_net_routes), runs.size, null) {
                            if (runs.isEmpty()) Empty(stringResource(R.string.cargo_net_routes_empty))
                            Rows(runs) { RunRow(it) }
                            if (vehicles.isNotEmpty()) HorizontalDivider(color = colors.border)
                            Rows(vehicles) { (v, carrier) -> VehicleRow(v, carrier, vm.planning) { v.id?.let(vm::plan) } }
                        }
                    }
                } else {
                    item {
                        val rows = page.tariff.orEmpty().filter { !it.level.isNullOrEmpty() }
                        Section(WorkisIcons.CreditCard, stringResource(R.string.cargo_net_tariff), 0, stringResource(R.string.cargo_net_tariff_hint)) {
                            Rows(rows) { row -> NumberRow(levelWord(row.level.orEmpty()), row.feeUsd, vm.fees[row.level].orEmpty(), vm.canWrite) { vm.type(row.level.orEmpty(), it) } }
                            vm.tariffNote?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
                            if (vm.tariffSaved) Clear(stringResource(R.string.cargo_net_saved))
                            // only when a typed number differs from the server's
                            if (vm.canWrite && vm.changedFees().isNotEmpty()) {
                                OutlinedButton(onClick = vm::saveTariff, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.cargo_net_tariff_save), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
                                }
                            }
                        }
                    }
                    // the consolidation numbers — the tariff's pattern: typed, then saved with the button
                    val rules = page.rules.orEmpty().filter { !it.key.isNullOrEmpty() }
                    if (rules.isNotEmpty()) item {
                        Section(WorkisIcons.Link, stringResource(R.string.cargo_net_rules), 0, stringResource(R.string.cargo_net_rules_hint)) {
                            Rows(rules) { row -> NumberRow(ruleWord(row.key.orEmpty()), row.usd, vm.ruleTexts[row.key].orEmpty(), vm.canWrite) { vm.typeRule(row.key.orEmpty(), it) } }
                            vm.rulesNote?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
                            if (vm.rulesSaved) Clear(stringResource(R.string.cargo_net_saved))
                            if (vm.canWrite && vm.changedRules().isNotEmpty()) {
                                OutlinedButton(onClick = vm::saveRules, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.cargo_net_rules_save), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
                                }
                            }
                        }
                    }
                    // the Workis-fleet tick, one carrier a row — a setting, so it lives beside the numbers
                    val carriers = page.carriers.orEmpty()
                    if (carriers.isNotEmpty()) item {
                        Section(WorkisIcons.Truck, stringResource(R.string.cargo_net_fleet), 0, stringResource(R.string.cargo_net_fleet_hint)) {
                            Rows(carriers) { c ->
                                val cid = c.id
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text((c.short ?: c.company).orEmpty(), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                                    if (vm.canWrite && cid != null) Switch(
                                        checked = vm.fleet[cid] == true, onCheckedChange = { vm.setFleet(cid, it) }, enabled = !vm.busy,
                                        colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen, checkedThumbColor = colors.surface),
                                    ) else if (c.ownFleet == true) {
                                        Icon(WorkisIcons.Check, contentDescription = stringResource(R.string.cargo_net_fleet), tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
    deciding?.let { d ->
        val remove = d.decision == "remove"
        AlertDialog(
            onDismissRequest = { deciding = null },
            containerColor = colors.surface,
            title = {
                Text(
                    stringResource(if (remove) R.string.cargo_net_remove_note else R.string.cargo_net_keep_note),
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink,
                )
            },
            text = {
                OutlinedTextField(
                    value = decisionNote, onValueChange = { decisionNote = it },
                    textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 14.sp, color = colors.ink).readingStep(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { deciding = null; d.objection.id?.let { vm.decide(it, d.decision, decisionNote) } }) {
                    Text(stringResource(if (remove) R.string.cargo_net_remove else R.string.cargo_net_keep), fontWeight = FontWeight.SemiBold, color = colors.accentText)
                }
            },
            dismissButton = { TextButton(onClick = { deciding = null }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
        )
    }
}

/**
 * A section: its own icon in the accent tone, the name in ink (never upper-cased), the count on a
 * chip; the card; the web's hint under it. `mine`: the rows wait for THIS reader's decision —
 * the chip is blue (blue's one meaning, "sıra sizde").
 */
@Composable
private fun Section(icon: ImageVector, title: String, count: Int, hint: String?, mine: Boolean = false, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            if (count > 0) Text(
                "$count", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = if (mine) colors.blue else colors.ink,
                modifier = Modifier
                    .background((if (mine) colors.blue else colors.ink).copy(alpha = if (mine) 0.16f else 0.08f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
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

/** An empty list that is GOOD news (and "saved") — green is the status colour, with its tick. */
@Composable
private fun Clear(words: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = SuccessGreen)
    }
}

/** The server's minutes in a unit a person reads: "45 dk", "17 sa", "43 gün" — whole units, floor. */
@Composable
private fun age(minutes: Int): String = when {
    minutes >= 1440 -> "${minutes / 1440} " + stringResource(R.string.days_word)
    minutes >= 60 -> "${minutes / 60} " + stringResource(R.string.cargo_net_hour)
    else -> "$minutes " + stringResource(R.string.cargo_net_min)
}

/** Rows with a hairline between them. */
@Composable
private fun <T> Rows(rows: List<T>, row: @Composable (T) -> Unit) {
    val colors = WorkisTheme.colors
    rows.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider(color = colors.border)
        row(r)
    }
}

@Composable
private fun Empty(words: String) {
    Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = WorkisTheme.colors.faint)
}

@Composable
private fun levelWord(code: String): String = when (code) {
    "acil45" -> stringResource(R.string.cargo_level_acil45)
    "ayni_gun" -> stringResource(R.string.cargo_level_ayni_gun)
    "ertesi_gun" -> stringResource(R.string.cargo_level_ertesi_gun)
    else -> code
}

@Composable
private fun ruleWord(key: String): String = when (key) {
    "ertesi_gun_birlesik" -> stringResource(R.string.cargo_net_rule_consolidated)
    "free_basket_min" -> stringResource(R.string.cargo_net_rule_floor)
    else -> key
}

@Composable
private fun ObjectionRow(rv: CargoNetObjection, canDecide: Boolean, enabled: Boolean, onDecide: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                rv.carrier.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Text("★".repeat((rv.rating ?: 0).coerceIn(0, 5)), fontSize = 12.sp, color = Beige)
            Spacer(Modifier.weight(1f))
            Text(rv.shipmentNumber.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
        }
        rv.by?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
        rv.comment?.takeIf { it.isNotEmpty() }?.let { Text("“$it”", fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
        Text(
            stringResource(R.string.cargo_net_objection_word) + ": " + rv.objection.orEmpty() +
                (rv.objectedAt?.takeIf { it.isNotEmpty() }?.let { " · " + WorkisClock.stamp(it) } ?: ""),
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
        )
        if (canDecide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onDecide("keep") }, enabled = enabled) {
                Text(stringResource(R.string.cargo_net_keep), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
            OutlinedButton(onClick = { onDecide("remove") }, enabled = enabled) {
                Text(stringResource(R.string.cargo_net_remove), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
        }
    }
}

@Composable
private fun BroadcastRow(b: CargoNetBroadcast) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${b.pickup.orEmpty()} → ${b.dropoff.orEmpty()}", fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            b.level?.takeIf { it.isNotEmpty() }?.let { StatusPill(levelWord(it)) }
            // how long it has waited is this row's signal — the beige note chip, clock + words
            b.ageMin?.let { m ->
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.background(BeigeBg, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Icon(WorkisIcons.Clock, contentDescription = null, tint = Beige, modifier = Modifier.size(11.dp))
                    Text(age(m), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Beige)
                }
            }
            Spacer(Modifier.weight(1f))
            b.feeUsd?.let {
                Text(WorkisMoney.text(it, null), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                Text("USD", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
        }
        b.buyer?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted) }
    }
}

@Composable
private fun ShipmentRow(sh: CargoNetShipment) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sh.number.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            sh.statusLabel?.takeIf { it.isNotEmpty() }?.let { StatusPill(it) }
            Spacer(Modifier.weight(1f))
            sh.promisedAt?.takeIf { it.isNotEmpty() }?.let {
                Text(WorkisClock.stamp(it), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
        }
        Text(
            listOfNotNull(sh.carrier?.takeIf { it.isNotEmpty() }, "${sh.pickup.orEmpty()} → ${sh.dropoff.orEmpty()}").joinToString(" · "),
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
        )
    }
}

@Composable
private fun RunRow(r: CargoNetRun) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.plate.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
            Text(r.carrier.orEmpty(), fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // every stop done: green, with its tick — never colour alone
            val all = (r.stops ?: 0) > 0 && r.done == r.stops
            if (all) Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
            Text("${r.done ?: 0}/${r.stops ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (all) SuccessGreen else colors.ink)
        }
        r.aiNote?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VehicleRow(v: CargoNetVehicle, carrier: String, planning: String?, onPlan: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(v.plate?.takeIf { it.isNotEmpty() }, v.kindLabel?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink,
            )
            if (carrier.isNotEmpty()) Text(carrier, fontSize = 12.sp, color = colors.muted)
        }
        if (planning != null && planning == v.id) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
        } else {
            OutlinedButton(onClick = onPlan, enabled = planning == null && v.id != null) {
                Text(stringResource(R.string.cargo_net_plan), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Double?, typed: String, canWrite: Boolean, onType: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        // a reader (a field lead) gets the number as plain text
        if (!canWrite) Text(WorkisMoney.text(value ?: 0.0, null), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
        else BasicTextField(
            value = typed, onValueChange = onType, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink, textAlign = TextAlign.End).readingStep(),
            cursorBrush = SolidColor(Kiremit500),
            modifier = Modifier
                .width(110.dp)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Text("USD", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.faint)
    }
}
