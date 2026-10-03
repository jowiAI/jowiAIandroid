package ai.workis.jowi.ui.screens.partner

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
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
import ai.workis.jowi.data.CargoBoard
import ai.workis.jowi.data.CargoDeliverBody
import ai.workis.jowi.data.CargoDropBody
import ai.workis.jowi.data.CargoJob
import ai.workis.jowi.data.CargoParty
import ai.workis.jowi.data.CargoShipment
import ai.workis.jowi.data.CargoStatusBody
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Kargo — the carrier seat (`GET cargo/`, 2026-09-30; the web Kargo page's
 * first tab): the open jobs it may take now (first accept wins) and its own
 * shipments. Every door is drawn from the server: the status buttons from
 * `nextStatuses`, "Teslim et" from `canDeliver`, "İşi bırak" from `canDrop`,
 * and (2026-10-01) the one next step from `primaryAction` / `moreActions` —
 * the app keeps no table of hops. §7.1: names, streets, phones and the
 * carrier's OWN fee only — never the goods' prices. (2026-10-03) A job reads
 * before it is taken: its SEVK LİSTESİ, the "you already go there" line, the
 * consolidated fee and the same-direction bundle; Rotalar sits in the toolbar.
 */
class CargoViewModel : ViewModel() {
    var board by mutableStateOf<CargoBoard?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The scanned shipment's id; `highlightTick` re-fires the scroll for the same row. */
    var highlight by mutableStateOf<String?>(null)
        private set
    var highlightTick by mutableIntStateOf(0)
        private set
    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch { fetch() }
    }

    fun refresh() { refreshing = true; load() }

    private suspend fun fetch() {
        val (r, err) = partnerCall(lang) { Graph.api.cargoBoard() }
        refreshing = false
        if (r != null) { board = r; loadError = null } else if (board == null) loadError = err
    }

    /** Every write: the reply's sentence (or the refusal's) is the note, and the board reloads. */
    private fun act(block: suspend () -> String?) {
        busy = true; note = null
        viewModelScope.launch {
            val msg = block()
            busy = false
            note = msg
            fetch()
        }
    }

    /** First accept wins — a 409 brings the server's "somebody was faster". */
    fun accept(j: CargoJob) {
        val id = j.orderId ?: return
        act { acceptOne(id) }
    }

    private suspend fun acceptOne(orderId: String): String? {
        val (r, err) = partnerCall(lang) { Graph.api.cargoAccept(orderId) }
        return if (r?.success == false) r.error ?: err else r?.message ?: err
    }

    /**
     * "Bu yöndekilerin hepsini al" (2026-10-03): accept stays per job — each job of the
     * bundle in turn; a 409 on one (somebody was faster) keeps its sentence and the walk
     * goes on; every sentence is the note, one reload at the end.
     */
    fun acceptBundle(bundle: String) {
        if (bundle.isEmpty()) return
        val ids = board?.jobs.orEmpty().filter { it.bundle == bundle }.mapNotNull { it.orderId }
        if (ids.isEmpty()) return
        act {
            ids.mapNotNull { id -> acceptOne(id)?.takeIf { it.isNotBlank() } }
                .joinToString("\n").ifEmpty { null }
        }
    }

    /** Never a status the server did not list for this shipment. */
    fun advance(sh: CargoShipment, status: String) {
        val id = sh.id ?: return
        if (status !in sh.nextStatuses.orEmpty()) return
        act {
            val (r, err) = partnerCall(lang) { Graph.api.cargoStatus(id, CargoStatusBody(status)) }
            if (r?.success == false) r.error ?: err else r?.message ?: err
        }
    }

    fun drop(sh: CargoShipment, reason: String) {
        val id = sh.id ?: return
        if (sh.canDrop != true) return
        act {
            val (r, err) = partnerCall(lang) { Graph.api.cargoDrop(id, CargoDropBody(reason.trim().ifEmpty { null })) }
            if (r?.success == false) r.error ?: err else r?.message ?: err
        }
    }

    /** The receiver's code closes the delivery; the reply's `message` says when the inspection windows release. */
    suspend fun deliver(sh: CargoShipment, otp: String): Pair<Boolean, String?> {
        val id = sh.id ?: return false to null
        val (r, err) = partnerCall(lang) { Graph.api.cargoDeliver(id, CargoDeliverBody(otp)) }
        val ok = r != null && r.success != false
        if (ok) { note = r?.message; fetch() }
        return ok to (if (ok) r?.message else r?.error ?: err)
    }

    /** The label's QR is the WSH number; the server resolves it to the carrier's OWN shipment. */
    fun scan(code: String) {
        val c = code.trim()
        if (c.isEmpty()) return
        note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.cargoScan(c) }
            val sh = r?.shipment
            if (sh == null) { note = err; return@launch }
            fetch()
            if (board?.shipments.orEmpty().any { it.id == sh.id }) {
                highlight = sh.id; highlightTick++
            } else {
                // own, but outside the board's latest page — say what it is
                note = listOfNotNull(sh.number, sh.statusLabel).joinToString(" · ")
            }
        }
    }
}

/** What each action key does on the server — the web Kargo row reads the same table (`carrier_seat.STEPS`). */
private val postsStatus = mapOf(
    "out" to "dagitimda", "retry" to "dagitimda", "received" to "kuryede",
    "failed" to "teslim_edilemedi", "return" to "iade",
)

/** A key that posts a status only when `nextStatuses` holds it; `drop` only with `canDrop`; `photo` and unknown keys never. */
private fun offered(key: String, sh: CargoShipment): Boolean {
    postsStatus[key]?.let { return it in sh.nextStatuses.orEmpty() }
    return when (key) {
        "scan", "deliver", "deliver_now", "label" -> true
        "drop" -> sh.canDrop == true
        else -> false
    }
}

@Composable
private fun actionTitle(key: String): String = when (key) {
    "scan" -> stringResource(R.string.cargo_act_scan)
    "out" -> stringResource(R.string.cargo_act_out)
    "retry" -> stringResource(R.string.cargo_act_retry)
    "deliver" -> stringResource(R.string.cargo_deliver)
    "deliver_now" -> stringResource(R.string.cargo_act_deliver_now)
    "received" -> stringResource(R.string.cargo_act_received)
    "failed" -> stringResource(R.string.cargo_status_teslim_edilemedi)
    "return" -> stringResource(R.string.cargo_act_return)
    "drop" -> stringResource(R.string.cargo_drop)
    "label" -> stringResource(R.string.shipment_label_pdf)
    else -> key
}

private fun actionIcon(key: String): ImageVector = when (key) {
    "scan" -> WorkisIcons.QrScan
    "out" -> WorkisIcons.Truck
    "retry" -> WorkisIcons.Retry
    "deliver", "deliver_now" -> WorkisIcons.Check
    "received" -> WorkisIcons.ShippingBox
    "failed" -> WorkisIcons.Warning
    "return", "drop" -> WorkisIcons.Undo
    "label" -> WorkisIcons.DocText
    else -> WorkisIcons.Dot
}

@Composable
private fun statusWord(code: String): String = when (code) {
    "kuryede" -> stringResource(R.string.cargo_status_kuryede)
    "dagitimda" -> stringResource(R.string.cargo_status_dagitimda)
    "teslim_edilemedi" -> stringResource(R.string.cargo_status_teslim_edilemedi)
    "iade" -> stringResource(R.string.cargo_status_iade)
    "iptal" -> stringResource(R.string.cargo_status_iptal)
    else -> code
}

private fun statusIcon(code: String): ImageVector = when (code) {
    "kuryede" -> WorkisIcons.ShippingBox
    "dagitimda" -> WorkisIcons.Truck
    "teslim_edilemedi" -> WorkisIcons.Warning
    "iade" -> WorkisIcons.Undo
    "iptal" -> WorkisIcons.Cross
    else -> WorkisIcons.Dot
}

@Composable
private fun levelWord(code: String): String = when (code) {
    "acil45" -> stringResource(R.string.cargo_level_acil45)
    "ayni_gun" -> stringResource(R.string.cargo_level_ayni_gun)
    "ertesi_gun" -> stringResource(R.string.cargo_level_ertesi_gun)
    else -> code
}

/** An ISO datetime as "dd.MM HH:mm" in the device's zone; a bare date as written. */
private fun stamp(s: String?): String? {
    if (s.isNullOrEmpty()) return null
    return runCatching {
        OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
    }.getOrElse { s.take(10) }
}

/** The screen's rows in order — the scan's scroll finds its shipment by index. */
private sealed interface CargoRow {
    data object Note : CargoRow
    data class Header(val text: Int) : CargoRow
    data class Empty(val text: Int) : CargoRow
    data class Job(val job: CargoJob) : CargoRow
    data class Ship(val shipment: CargoShipment) : CargoRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrierScreen(vm: CargoViewModel, onBack: () -> Unit, onRuns: () -> Unit) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    var otpTarget by remember { mutableStateOf<CargoShipment?>(null) }
    var dispatchFor by remember { mutableStateOf<CargoJob?>(null) }
    var dropTarget by remember { mutableStateOf<CargoShipment?>(null) }
    var statusTarget by remember { mutableStateOf<Pair<CargoShipment, String>?>(null) }
    var labelFor by remember { mutableStateOf<CargoShipment?>(null) }
    val unavailable = stringResource(R.string.cargo_camera_unavailable)
    // Google's code scanner draws its own camera UI — the app asks no camera permission
    val startScan = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(vm::scan) }
            .addOnFailureListener { vm.note = unavailable }
        Unit
    }
    val perform: (String, CargoShipment) -> Unit = { key, sh ->
        // an action key that posts a status, or a legacy door naming the status itself
        val code = postsStatus[key] ?: key.removePrefix("status:").takeIf { key.startsWith("status:") }
        when {
            code != null -> statusTarget = sh to code
            key == "scan" -> startScan()
            key == "deliver" || key == "deliver_now" -> otpTarget = sh
            key == "drop" -> dropTarget = sh
            key == "label" -> labelFor = sh
        }
    }

    ConsoleSub(stringResource(R.string.cargo_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
        IconButton(onClick = startScan) {
            Icon(WorkisIcons.QrScan, contentDescription = stringResource(R.string.cargo_scan), tint = colors.ink, modifier = Modifier.size(22.dp))
        }
        // Rotalar (2026-10-03): the runs per vehicle and day, the vehicles behind them
        IconButton(onClick = onRuns) {
            Icon(WorkisIcons.Route, contentDescription = stringResource(R.string.cargo_runs), tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }) {
        ai.workis.jowi.ui.components.ChangesWatch("cargo", typing = dropTarget != null) { vm.load() } // changes/ (2026-10-03); a drop reason holds it back
        val board = vm.board
        when {
            board == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            board == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> {
                val rows = buildList {
                    add(CargoRow.Note)
                    add(CargoRow.Header(R.string.cargo_jobs_header))
                    val jobs = board.jobs.orEmpty()
                    if (jobs.isEmpty()) add(CargoRow.Empty(R.string.cargo_jobs_empty)) else jobs.forEach { add(CargoRow.Job(it)) }
                    add(CargoRow.Header(R.string.cargo_shipments_header))
                    val ships = board.shipments.orEmpty()
                    if (ships.isEmpty()) add(CargoRow.Empty(R.string.cargo_shipments_empty)) else ships.forEach { add(CargoRow.Ship(it)) }
                }
                val listState = rememberLazyListState()
                LaunchedEffect(vm.highlightTick) {
                    val id = vm.highlight ?: return@LaunchedEffect
                    val i = rows.indexOfFirst { it is CargoRow.Ship && it.shipment.id == id }
                    if (i >= 0) listState.animateScrollToItem(i)
                }
                PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(rows) { row ->
                            when (row) {
                                CargoRow.Note -> NoteBand(vm.note)
                                is CargoRow.Header -> Eyebrow(stringResource(row.text).uppercase())
                                is CargoRow.Empty -> Text(stringResource(row.text), fontSize = 13.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                                is CargoRow.Job -> JobCard(row.job, vm) { dispatchFor = row.job }
                                is CargoRow.Ship -> ShipmentCard(row.shipment, vm, highlighted = vm.highlight == row.shipment.id, perform = perform)
                            }
                        }
                        item { Spacer(Modifier.height(30.dp)) }
                    }
                }
            }
        }
    }

    statusTarget?.let { (sh, code) ->
        ConfirmDialog(statusWord(code), sh.number.orEmpty(), destructive = false, onDismiss = { statusTarget = null }) {
            vm.advance(sh, code)
        }
    }
    dropTarget?.let { sh -> DropDialog(onDismiss = { dropTarget = null }) { reason -> vm.drop(sh, reason) } }
    otpTarget?.let { sh -> DeliverOtpSheet(sh, vm) { otpTarget = null } }
    labelFor?.let { sh ->
        PdfViewerDialog(
            title = stringResource(R.string.shipment_label_pdf),
            url = WORKIS_BASE_URL + "workis/shipments/${sh.id}/label/",
            fileName = "${sh.number ?: "label"}.pdf",
            onDismiss = { labelFor = null },
        )
    }
    // the open job's SEVK LİSTESİ, readable before accepting; 404 → the server's sentence
    dispatchFor?.let { j ->
        PdfViewerDialog(
            title = stringResource(R.string.cargo_dispatch_list),
            url = WORKIS_BASE_URL + "workis/cargo/jobs/${j.orderId}/dispatch-list/",
            fileName = "sevk-listesi.pdf",
            onDismiss = { dispatchFor = null },
        )
    }
}

@Composable
private fun CargoCard(highlighted: Boolean = false, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) BeigeBg else colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun JobCard(j: CargoJob, vm: CargoViewModel, onDispatchList: () -> Unit) {
    val colors = WorkisTheme.colors
    CargoCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                j.serviceLabel ?: j.serviceLevel?.let { levelWord(it) }.orEmpty(),
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f),
            )
            // the carrier's own fee — the only money on this screen (§7.1)
            val consolidated = j.feeConsolidated
            if (consolidated != null) {
                // a fitting ertesi-gün job pays the consolidated fee: the plain one struck
                val label = stringResource(R.string.cargo_fee_consolidated) + " " + WorkisMoney.text(consolidated, j.currency)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
                ) {
                    Text(
                        WorkisMoney.text(j.fee, j.currency), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                        color = colors.muted, textDecoration = TextDecoration.LineThrough,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(WorkisIcons.Link, contentDescription = null, tint = colors.ink, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(WorkisMoney.text(consolidated, j.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                }
            } else {
                Text(WorkisMoney.text(j.fee, j.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            }
        }
        Text("${j.pickupDistrict ?: "—"} → ${j.dropoffDistrict ?: "—"}", fontSize = 13.sp, color = colors.ink)
        val packagesWord = stringResource(R.string.cargo_packages_word)
        val ageWord = stringResource(R.string.cargo_age_word)
        val loadWord = stringResource(R.string.cargo_day_load_word)
        MonoLine(
            listOfNotNull(
                j.packages?.let { "$it $packagesWord" },
                j.ageMinutes?.let { "$it $ageWord" },
                if (j.dayLoad != null && j.dayCapacity != null) "${j.dayLoad}/${j.dayCapacity} $loadWord" else null,
            ),
        )
        val after = stringResource(R.string.cargo_pickup_after)
        val due = stringResource(R.string.cargo_due_at)
        MonoLine(listOfNotNull(stamp(j.pickupAfter)?.let { "$after: $it" }, stamp(j.dueAt)?.let { "$due: $it" }))
        // "you already go there": the server counts this carrier's stops in that district
        j.fitN?.takeIf { it > 0 }?.let { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Route, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(R.string.cargo_fit_prefix)} ${j.fitWhere?.takeIf { it.isNotEmpty() } ?: j.dropoffDistrict.orEmpty()}: $n ${stringResource(R.string.cargo_fit_suffix)}",
                    fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SuccessGreen,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (j.dayFull == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(WorkisIcons.Calendar, contentDescription = null, tint = Beige, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cargo_day_full), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Beige)
                }
            } else {
                DoorRow(stringResource(R.string.cargo_accept), WorkisIcons.Check, enabled = !vm.busy) { vm.accept(j) }
            }
            // read the job before taking it: the SEVK LİSTESİ preview (no street/phone/OTP yet)
            if (j.orderId != null) DoorRow(stringResource(R.string.cargo_dispatch_list), WorkisIcons.DocText, enabled = true, onClick = onDispatchList)
        }
        // the same-direction bundle: accept stays per job, taken in turn
        val bundle = j.bundle
        val n = j.bundleCount ?: 0
        if (n > 1 && !bundle.isNullOrEmpty() && j.dayFull != true) {
            OutlinedButton(onClick = { vm.acceptBundle(bundle) }, enabled = !vm.busy, modifier = Modifier.height(36.dp)) {
                Icon(WorkisIcons.ShippingBox, contentDescription = null, tint = colors.ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("${stringResource(R.string.cargo_bundle_take_all)} ($n)", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
        }
    }
}

@Composable
private fun ShipmentCard(sh: CargoShipment, vm: CargoViewModel, highlighted: Boolean, perform: (String, CargoShipment) -> Unit) {
    val colors = WorkisTheme.colors
    CargoCard(highlighted) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sh.number.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            sh.statusLabel?.takeIf { it.isNotEmpty() }?.let { StatusPill(it) }
            Spacer(Modifier.weight(1f))
            sh.poNumber?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Party(stringResource(R.string.cargo_pickup), sh.pickup, WorkisIcons.ShippingBox)
        Party(stringResource(R.string.cargo_dropoff), sh.dropoff, WorkisIcons.Building)
        val promised = stringResource(R.string.order_promised)
        val packagesWord = stringResource(R.string.cargo_packages_word)
        MonoLine(
            listOfNotNull(
                stamp(sh.promisedAt)?.let { "$promised: $it" },
                sh.packages?.let { "$it $packagesWord" },
                sh.serviceLevel?.takeIf { it.isNotEmpty() }?.let { levelWord(it) },
            ),
        )
        sh.packageNote?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
        Spacer(Modifier.height(2.dp))
        if (sh.primaryAction != null || sh.moreActions != null) StepControls(sh, vm.busy, perform)
        else LegacyDoors(sh, vm.busy, perform = perform)
    }
}

/**
 * One next step (2026-10-01): the server's `primaryAction` is the ONE tinted
 * control on the card; the rest of `moreActions` sit behind "Daha fazla".
 */
@Composable
private fun StepControls(sh: CargoShipment, busy: Boolean, perform: (String, CargoShipment) -> Unit) {
    val colors = WorkisTheme.colors
    val more = sh.moreActions.orEmpty().filter { offered(it, sh) }
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sh.primaryAction?.takeIf { offered(it, sh) }?.let { key ->
            Button(
                onClick = { perform(key, sh) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.surface, disabledContentColor = colors.faint),
                modifier = Modifier.height(40.dp),
            ) {
                Icon(actionIcon(key), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionTitle(key), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        if (more.isNotEmpty()) Box {
            OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.height(40.dp)) {
                Text("⋯ " + stringResource(R.string.cargo_more), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                more.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(actionTitle(key), color = if (key == "drop") colors.danger else colors.ink, fontSize = 14.sp) },
                        leadingIcon = { Icon(actionIcon(key), contentDescription = null, tint = if (key == "drop") colors.danger else colors.accentText, modifier = Modifier.size(16.dp)) },
                        onClick = { menu = false; perform(key, sh) },
                    )
                }
            }
        }
    }
}

/** An older server without `primaryAction`/`moreActions`: every door as before. */
@Composable
private fun LegacyDoors(sh: CargoShipment, busy: Boolean, perform: (String, CargoShipment) -> Unit) {
    // the next hops, as the server lists them — each behind a confirmation
    sh.nextStatuses.orEmpty().forEach { code ->
        DoorRow(statusWord(code), statusIcon(code), enabled = !busy) { perform("status:$code", sh) }
    }
    if (sh.canDeliver == true) DoorRow(stringResource(R.string.cargo_deliver), WorkisIcons.Check, enabled = !busy) { perform("deliver", sh) }
    if (sh.canDrop == true) DoorRow(stringResource(R.string.cargo_drop), WorkisIcons.Undo, enabled = !busy) { perform("drop", sh) }
    if (sh.id != null) DoorRow(stringResource(R.string.shipment_label_pdf), WorkisIcons.DocText, enabled = true) { perform("label", sh) }
}

/** A plain door: accent icon + words, no fill — the card keeps its one tinted control. */
@Composable
private fun DoorRow(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val tint = if (enabled) colors.accentText else colors.faint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = tint)
    }
}

@Composable
private fun MonoLine(parts: List<String>) {
    if (parts.isEmpty()) return
    Text(parts.joinToString("  "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = WorkisTheme.colors.muted)
}

/** name · company, street, district / city, phone (tappable → the dialer). */
@Composable
private fun Party(title: String, p: CargoParty?, icon: ImageVector) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.muted)
        }
        if (p == null) return@Column
        val who = listOfNotNull(p.name, p.company).filter { it.isNotEmpty() }
        if (who.isNotEmpty()) Text(who.joinToString(" · "), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
        val place = listOfNotNull(p.street, listOfNotNull(p.district, p.city).filter { it.isNotEmpty() }.joinToString(" / "))
            .filter { it.isNotEmpty() }
        if (place.isNotEmpty()) Text(place.joinToString(", "), fontSize = 12.sp, color = colors.ink)
        p.phone?.takeIf { it.isNotEmpty() }?.let { phone ->
            val digits = phone.filter { it.isDigit() || it == '+' }
            Text(
                "☎ $phone", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.accentText,
                modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) } },
            )
        }
    }
}

/** "İşi bırak" — before pickup only; the reason is optional. */
@Composable
private fun DropDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.cargo_drop), color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cargo_drop_confirm), color = colors.muted)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    placeholder = { Text(stringResource(R.string.cargo_drop_reason), color = colors.faint) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                    ),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm(reason) }) {
                Text(stringResource(R.string.cargo_drop), color = colors.danger, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}

/** The receiver's 6-digit code closes the delivery (`shipments/{id}/deliver/`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliverOtpSheet(sh: CargoShipment, vm: CargoViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.st_error)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.cargo_otp_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink)
            Text(sh.number.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                placeholder = { Text("000000", color = colors.faint, fontFamily = WorkisMono, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, textAlign = TextAlign.Center, color = colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = colors.blue),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.cargo_otp_hint), fontSize = 13.sp, color = colors.muted)
            error?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val (ok, msg) = vm.deliver(sh, code)
                        busy = false
                        if (ok) onClose() else error = msg ?: fallback
                    }
                },
                enabled = code.length == 6 && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.canvas, disabledContentColor = colors.faint),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.cargo_deliver), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
