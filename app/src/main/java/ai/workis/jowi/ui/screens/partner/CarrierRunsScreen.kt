package ai.workis.jowi.ui.screens.partner

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CargoPlanBody
import ai.workis.jowi.data.CargoRun
import ai.workis.jowi.data.CargoStop
import ai.workis.jowi.data.CargoStopBody
import ai.workis.jowi.data.CargoVehicle
import ai.workis.jowi.data.CargoVehicleBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rotalar — the carrier's runs per vehicle AND per day (Django note 2026-10-03 §I,
 * slice 5): `GET cargo/runs/`, `POST cargo/runs/plan/`, `POST cargo/runs/{id}/stops/`,
 * and the vehicles behind them (`cargo/vehicles/`). Read-mostly and plain: the stop
 * order, the deadlines and what did not fit are the server's. Only a PICKUP is ticked
 * here — a dropoff closes through the OTP delivery door (Kargo screen), so the app
 * never offers that tick. §7.1: names, streets and plates only — no money here.
 */
class CargoRunsViewModel : ViewModel() {
    var runs by mutableStateOf<List<CargoRun>?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    /** a refusal's sentence (tick, retire) */
    var note by mutableStateOf<String?>(null)
        private set
    /** the numbers the last plan could not fit; the screen words them */
    var leftOut by mutableStateOf<List<String>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var vehicles by mutableStateOf<List<CargoVehicle>?>(null)
        private set
    var vehiclesError by mutableStateOf<String?>(null)
        private set
    var vehiclesNote by mutableStateOf<String?>(null)
        private set
    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch { fetch() }
    }

    fun refresh() { refreshing = true; load() }

    private suspend fun fetch() {
        val (r, err) = partnerCall(lang) { Graph.api.cargoRuns() }
        refreshing = false
        if (r != null) { runs = r.runs.orEmpty(); loadError = null } else if (runs == null) loadError = err
    }

    fun loadVehicles() {
        viewModelScope.launch { fetchVehicles() }
    }

    private suspend fun fetchVehicles() {
        val (r, err) = partnerCall(lang) { Graph.api.cargoVehicles() }
        if (r != null) { vehicles = r.vehicles.orEmpty(); vehiclesError = null } else if (vehicles == null) vehiclesError = err
    }

    /** A pickup only — the dropoff is never offered (the server refuses it; the OTP door closes it). */
    fun tick(run: CargoRun, stop: CargoStop) {
        val runId = run.id ?: return
        val shipmentId = stop.shipmentId ?: return
        if (stop.kind != "pickup" || !stop.doneAt.isNullOrEmpty()) return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.cargoRunStop(runId, CargoStopBody(shipmentId, "pickup")) }
            busy = false
            if (r == null) note = err
            fetch()
        }
    }

    /** `vehicleId` = a vehicle's id or "all". → null when planned, else the server's sentence. */
    suspend fun plan(vehicleId: String, day: String): String? {
        val (r, err) = partnerCall(lang) { Graph.api.cargoRunsPlan(CargoPlanBody(vehicleId, day)) }
        if (r == null) return err
        note = null
        leftOut = r.leftOut.orEmpty()
        fetch()
        return null
    }

    /** → null when saved, else the server's 400 sentence as sent. */
    suspend fun saveVehicle(plate: String, kind: String, capacityKg: Int?): String? {
        val (r, err) = partnerCall(lang) { Graph.api.cargoVehicleSave(CargoVehicleBody(plate, kind, capacityKg)) }
        if (r == null || r.success == false) return r?.error ?: err
        fetchVehicles()
        return null
    }

    /** Soft — runs and shipments keep the plate. */
    fun retire(v: CargoVehicle) {
        val id = v.id ?: return
        vehiclesNote = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.cargoVehicleRetire(id) }
            if (r == null || r.success == false) vehiclesNote = r?.error ?: err
            fetchVehicles()
        }
    }
}

private enum class RunsPage { Runs, Vehicles }

/** The vehicle kinds `POST cargo/vehicles/` takes (CarrierVehicle.KIND). */
private val vehicleKinds = listOf("motosiklet", "panelvan", "kamyonet", "kamyon")

@Composable
private fun kindWord(k: String): String = when (k) {
    "motosiklet" -> stringResource(R.string.cargo_kind_motosiklet)
    "panelvan" -> stringResource(R.string.cargo_kind_panelvan)
    "kamyonet" -> stringResource(R.string.cargo_kind_kamyonet)
    "kamyon" -> stringResource(R.string.cargo_kind_kamyon)
    else -> k
}

@Composable
private fun runStatusWord(s: String?): String = when (s) {
    "planned" -> stringResource(R.string.cargo_run_planned)
    "active" -> stringResource(R.string.cargo_run_active)
    "done" -> stringResource(R.string.cargo_run_done)
    else -> s.orEmpty()
}

private fun runStatusIcon(s: String?): ImageVector = when (s) {
    "active" -> WorkisIcons.Truck
    "done" -> WorkisIcons.Check
    else -> WorkisIcons.Calendar
}

/** An ISO stamp as HH:mm in the device's zone; anything else as sent. */
private fun clock(s: String): String = runCatching {
    OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrElse { s }

@Composable
fun CarrierRunsScreen(onBack: () -> Unit, vm: CargoRunsViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf(RunsPage.Runs) }
    when (page) {
        RunsPage.Runs -> RunsPageView(vm, onBack, onVehicles = { page = RunsPage.Vehicles })
        RunsPage.Vehicles -> {
            BackHandler { page = RunsPage.Runs }
            VehiclesPageView(vm, onBack = { page = RunsPage.Runs })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunsPageView(vm: CargoRunsViewModel, onBack: () -> Unit, onVehicles: () -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    // the server's `day`: today | tomorrow
    var day by rememberSaveable { mutableStateOf("today") }
    var planning by remember { mutableStateOf(false) }
    ConsoleSub(stringResource(R.string.cargo_runs), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
        IconButton(onClick = { planning = true }) {
            Icon(WorkisIcons.Route, contentDescription = stringResource(R.string.cargo_plan_run), tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }) {
        ChangesWatch("cargo") { vm.load() } // a pickup ticked elsewhere, a re-plan from the web
        DaySwitch(day) { day = it }
        Spacer(Modifier.height(12.dp))
        val runs = vm.runs
        when {
            runs == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            runs == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> {
                val shown = runs.filter { it.day == day }
                val leftWord = stringResource(R.string.cargo_left_out)
                PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { NoteBand(vm.note) }
                        if (vm.leftOut.isNotEmpty()) item { NoteBand("$leftWord ${vm.leftOut.joinToString(", ")}") }
                        if (shown.isEmpty()) item {
                            Text(stringResource(R.string.cargo_runs_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        items(shown, key = { it.id ?: it.hashCode().toString() }) { run -> RunSection(run, vm) }
                        item {
                            SurfaceCard(Modifier.clickable(onClick = onVehicles)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(WorkisIcons.Truck, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.cargo_vehicles), fontSize = 15.sp, color = colors.ink, modifier = Modifier.weight(1f))
                                    Text("›", fontSize = 18.sp, color = colors.faint)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(30.dp)) }
                    }
                }
            }
        }
    }
    if (planning) PlanRunSheet(vm, day) { planning = false }
}

@Composable
private fun DaySwitch(day: String, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val days = listOf("today" to stringResource(R.string.cargo_runs_today), "tomorrow" to stringResource(R.string.cargo_runs_tomorrow))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = day == key, onClick = { onPick(key) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = days.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.surface, activeContentColor = colors.ink, inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted),
            ) { Text(label, fontSize = 13.sp) }
        }
    }
}

/** plate · status words · done/stops, then the stops in the server's `seq` order. */
@Composable
private fun RunSection(run: CargoRun, vm: CargoRunsViewModel) {
    val colors = WorkisTheme.colors
    val stops = run.stops.orEmpty().sortedBy { it.seq ?: 0 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(run.plate ?: "—", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
            Text("  ·  ", fontSize = 12.sp, color = colors.faint)
            Icon(runStatusIcon(run.status), contentDescription = null, tint = colors.muted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(runStatusWord(run.status), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(
                "${run.doneCount ?: 0}/${stops.size} ${stringResource(R.string.cargo_stops_word)}",
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted,
            )
        }
        SurfaceCard {
            run.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
            stops.forEachIndexed { i, stop ->
                if (i > 0 || !run.note.isNullOrEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                StopRow(run, stop, vm)
            }
        }
    }
}

@Composable
private fun StopRow(run: CargoRun, stop: CargoStop, vm: CargoRunsViewModel) {
    val colors = WorkisTheme.colors
    val pickup = stop.kind == "pickup"
    val kindText = stringResource(if (pickup) R.string.cargo_pickup else R.string.cargo_dropoff)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (pickup) WorkisIcons.ShippingBox else WorkisIcons.MapPin,
            contentDescription = kindText, tint = colors.accentText, modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stop.number.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                Spacer(Modifier.width(8.dp))
                Text(kindText, fontSize = 12.sp, color = colors.muted)
            }
            stop.place?.takeIf { it.isNotEmpty() }?.let { Text(it, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink) }
            val where = listOfNotNull(stop.district, stop.street).filter { it.isNotEmpty() }
            if (where.isNotEmpty()) Text(where.joinToString(" · "), fontSize = 12.sp, color = colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                stop.deadline?.takeIf { it.isNotEmpty() }?.let { d ->
                    val late = stop.late == true
                    val tint = if (late) colors.danger else colors.muted
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (late) WorkisIcons.Warning else WorkisIcons.Clock, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (late) "$d · ${stringResource(R.string.cargo_stop_late)}" else d,
                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = tint,
                        )
                    }
                }
                stop.packages?.let {
                    Text("$it ${stringResource(R.string.cargo_packages_word)}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted)
                }
            }
            val done = stop.doneAt
            if (!done.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(clock(done), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SuccessGreen)
                }
            } else if (pickup) {
                // the pickup tick only; a dropoff closes through the OTP delivery door
                OutlinedButton(onClick = { vm.tick(run, stop) }, enabled = !vm.busy, modifier = Modifier.height(34.dp)) {
                    Icon(WorkisIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cargo_stop_picked), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                }
            }
        }
    }
}

/** One choice of a short list: selected = a solid kiremit border and a tick (selection grammar). */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kiremit500 else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(text, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        if (selected) Icon(WorkisIcons.Check, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp))
    }
}

/**
 * "Rotayı planla": one vehicle (or every vehicle by direction, "all") for today or
 * tomorrow. Re-planning replaces that day's run(s); what did not fit comes back as
 * `leftOut` numbers, worded on the runs page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanRunSheet(vm: CargoRunsViewModel, initialDay: String, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadVehicles() }
    var vehicleId by remember { mutableStateOf("all") }
    var day by remember { mutableStateOf(initialDay) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.st_error)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.cargo_plan_run), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink)
            Eyebrow(stringResource(R.string.cargo_plan_vehicle).uppercase())
            ChoiceRow(stringResource(R.string.cargo_plan_all_vehicles), vehicleId == "all") { vehicleId = "all" }
            vm.vehicles.orEmpty().forEach { v ->
                val id = v.id ?: return@forEach
                ChoiceRow(listOfNotNull(v.plate, v.kindLabel).joinToString(" · "), vehicleId == id) { vehicleId = id }
            }
            Eyebrow(stringResource(R.string.cargo_plan_day).uppercase())
            DaySwitch(day) { day = it }
            error?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val msg = vm.plan(vehicleId, day)
                        busy = false
                        if (msg == null) onClose() else error = msg.ifEmpty { fallback }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.canvas, disabledContentColor = colors.faint),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.cargo_plan_run), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * The carrier's vehicles: plate · kind · capacities. Swipe to retire (soft — runs and
 * shipments keep the plate), behind a confirmation; "+" adds one. Driver ids are
 * decoded, not edited here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclesPageView(vm: CargoRunsViewModel, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.loadVehicles() }
    var adding by remember { mutableStateOf(false) }
    var retireTarget by remember { mutableStateOf<CargoVehicle?>(null) }
    ConsoleSub(stringResource(R.string.cargo_vehicles), onBack, trailing = {
        IconButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cargo_vehicle_add), tint = colors.ink)
        }
    }) {
        val list = vm.vehicles
        when {
            list == null && vm.vehiclesError != null -> Text(vm.vehiclesError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            list == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { NoteBand(vm.vehiclesNote) }
                if (list.isEmpty()) item {
                    Text(stringResource(R.string.cargo_vehicles_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
                items(list, key = { it.id ?: it.hashCode().toString() }) { v ->
                    RetireSwipe(stringResource(R.string.cargo_vehicle_retire), onRetire = { retireTarget = v }) { VehicleRow(v) }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
    retireTarget?.let { v ->
        ConfirmDialog(
            title = stringResource(R.string.cargo_vehicle_retire),
            message = listOfNotNull(v.plate, stringResource(R.string.cargo_vehicle_retire_confirm)).joinToString("\n"),
            destructive = true,
            onDismiss = { retireTarget = null },
        ) { vm.retire(v) }
    }
    if (adding) VehicleSheet(vm) { adding = false }
}

/** A row that snaps back after the swipe — the confirmation decides, never the gesture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetireSwipe(label: String, onRetire: () -> Unit, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) onRetire(); false })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(colors.danger.copy(alpha = 0.18f), RoundedCornerShape(20.dp)).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.danger)
            }
        },
    ) { content() }
}

@Composable
private fun VehicleRow(v: CargoVehicle) {
    val colors = WorkisTheme.colors
    val locale = Locale.forLanguageTag(Graph.language.value)
    val packagesWord = stringResource(R.string.cargo_packages_word)
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.plate.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            Spacer(Modifier.width(8.dp))
            (v.kindLabel ?: v.kind)?.let { Text(it, fontSize = 13.sp, color = colors.muted) }
        }
        val caps = listOfNotNull(
            v.capacityKg?.let { "$it kg" },
            v.capacityM3?.let { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(it) + " m³" },
            v.koliCapacity?.let { "$it $packagesWord" },
        )
        if (caps.isNotEmpty()) Text(caps.joinToString(" · "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted)
    }
}

/** "+ Araç": plate, kind, capacity (kg, optional). The server upper-cases the plate and refuses a duplicate with its own sentence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleSheet(vm: CargoRunsViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    var plate by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("panelvan") }
    var capacity by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.st_error)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
        focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
    )
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.cargo_vehicle_add), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink)
            OutlinedTextField(
                value = plate, onValueChange = { plate = it.take(16) },
                singleLine = true,
                label = { Text(stringResource(R.string.cargo_vehicle_plate)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                shape = RoundedCornerShape(14.dp), colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Eyebrow(stringResource(R.string.cargo_vehicle_kind).uppercase())
            vehicleKinds.forEach { k -> ChoiceRow(kindWord(k), kind == k) { kind = k } }
            OutlinedTextField(
                value = capacity, onValueChange = { v -> capacity = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                label = { Text(stringResource(R.string.cargo_vehicle_capacity_kg)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp), colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val msg = vm.saveVehicle(plate.trim(), kind, capacity.toIntOrNull())
                        busy = false
                        if (msg == null) onClose() else error = msg.ifEmpty { fallback }
                    }
                },
                enabled = plate.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.canvas, disabledContentColor = colors.faint),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.cargo_vehicle_save), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
