package ai.workis.jowi.ui.screens.partner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.GeoProvince
import ai.workis.jowi.data.PartnerCompany
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Hesap → Firma: the web /workis/panel/firma/ page as sections for the partner
 * seat. Legal identity read-only, address + phone with cell-exit autosave and
 * the green tick, a maps link that pins the location, the autonomy dials, the
 * tax plate (view / re-upload). Same service, same validation as the web.
 */
class CompanyViewModel : ViewModel() {
    val fields = mutableStateOf(mapOf<String, String>())
    var outward by mutableStateOf(false)
    var reorder by mutableStateOf(false)
    var fund by mutableStateOf(false)
    var provinces by mutableStateOf<List<GeoProvince>>(emptyList())
        private set
    var tick by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busyTax by mutableStateOf(false)
        private set
    var taxBytes by mutableStateOf<ByteArray?>(null)
    var taxIsPdf by mutableStateOf(false)
    private val dirty = mutableSetOf<String>()
    private var tickJob: Job? = null
    private var seededId: String? = null
    private val lang get() = Graph.language.value

    fun seed(c: PartnerCompany) {
        if (seededId == (c.company?.id ?: "")) return
        seededId = c.company?.id ?: ""
        fields.value = mapOf(
            "shortName" to c.company?.shortName.orEmpty(), "fullAddress" to c.company?.fullAddress.orEmpty(),
            "city" to c.company?.city.orEmpty(), "district" to c.company?.district.orEmpty(),
            "postalCode" to c.company?.postalCode.orEmpty(), "phone" to c.company?.phone.orEmpty(), "mapsLink" to "",
            "awardCap" to (c.autonomy?.awardCapUsd?.let { WorkisMoney.qty(it) } ?: ""),
            "releaseDays" to (c.autonomy?.autoReleaseDays?.toString() ?: ""),
        )
        outward = c.autonomy?.outwardRoutine ?: false
        reorder = c.autonomy?.autoReorder ?: false
        fund = c.autonomy?.autoFund ?: false
        dirty.clear()
        if (provinces.isEmpty()) viewModelScope.launch { provinces = partnerCall(lang) { Graph.api.geoProvinces() }.first?.provinces.orEmpty() }
    }

    fun edit(key: String, value: String) { fields.value = fields.value + (key to value); dirty += key }

    private fun flash(key: String) { tickJob?.cancel(); tick = key; tickJob = viewModelScope.launch { delay(1400); if (tick == key) tick = null } }

    fun commit(key: String) {
        if (!dirty.remove(key)) return
        val v = fields.value[key].orEmpty()
        when (key) {
            "shortName", "fullAddress", "district", "postalCode", "phone" -> save(key, jsonBody(key to v))
            "mapsLink" -> if (v.isNotBlank()) save(key, jsonBody("mapsLink" to v.trim()))
            "awardCap" -> autonomy(key, jsonBody("awardCapUsd" to (v.replace(',', '.').toDoubleOrNull() ?: 0.0)))
            "releaseDays" -> autonomy(key, jsonBody("autoReleaseDays" to (v.toIntOrNull() ?: 0)))
        }
    }

    fun setCity(city: String) { fields.value = fields.value + ("city" to city) + ("district" to ""); save("city", jsonBody("city" to city)) }
    fun setDistrict(d: String) { fields.value = fields.value + ("district" to d); save("district", jsonBody("district" to d)) }

    private fun save(key: String, body: kotlinx.serialization.json.JsonObject) {
        note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.companySave(body) }
            if (r != null && r.success) {
                flash(key)
                // the server canonicalizes il/ilçe and answers the pin
                r.city?.let { fields.value = fields.value + ("city" to it) }
                r.district?.let { fields.value = fields.value + ("district" to it) }
                if (r.lat != null) { fields.value = fields.value + ("mapsLink" to ""); Graph.partner.refresh() }
            } else note = r?.error ?: r?.message ?: err
        }
    }

    fun toggle(key: String, on: Boolean) {
        when (key) { "outwardRoutine" -> outward = on; "autoReorder" -> reorder = on; "autoFund" -> fund = on }
        autonomy(key, jsonBody(key to on))
    }

    private fun autonomy(key: String, body: kotlinx.serialization.json.JsonObject) {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.companyAutonomy(body) }
            if (r != null && r.success) flash(key) else note = r?.error ?: r?.message ?: err
        }
    }

    fun openTax() {
        taxBytes = null
        viewModelScope.launch {
            val resp = runCatching { withContext(Dispatchers.IO) { Graph.api.companyTaxFile() } }.getOrNull()
            val body = resp?.body()?.takeIf { resp.isSuccessful } ?: run { note = ai.workis.jowi.data.statusMessage(resp?.code() ?: -1, lang); return@launch }
            val bytes = withContext(Dispatchers.IO) { body.use { it.bytes() } }
            taxIsPdf = looksLikePdf(bytes) || resp.headers()["Content-Type"]?.contains("pdf") == true
            taxBytes = bytes
        }
    }

    fun uploadTax(bytes: ByteArray, name: String, mime: String) {
        if (bytes.size > 5 * 1024 * 1024) { note = ai.workis.jowi.data.applyErrorMessage("bad_file", lang); return }
        busyTax = true
        viewModelScope.launch {
            val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaType()))
            val (r, err) = partnerCall(lang) { Graph.api.companyTaxFileUpload(part) }
            busyTax = false
            if (r != null && r.success) { flash("tax"); Graph.partner.refresh() } else note = r?.error ?: r?.message ?: err
        }
    }
}

@Composable
fun CompanySections(vm: CompanyViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val seat by Graph.partner.seat.collectAsState()
    val c = (seat as? PartnerSeatState.Seat)?.company ?: return
    LaunchedEffect(c.company?.id) { vm.seed(c) }
    val f by vm.fields
    var showTax by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.uploadTax(bytes, if (mime.contains("pdf")) "levha.pdf" else "levha.jpg", mime)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteBand(vm.note)
        // identity — read-only (the coordinator changes it)
        Column {
            SurfaceCard {
                Row(verticalAlignment = Alignment.Top) {
                    Text(c.company?.name.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (c.isBuyer) StatusPill(stringResource(R.string.seat_buyer))
                        if (c.isSeller) StatusPill(stringResource(R.string.seat_seller))
                        if (c.isCarrier) StatusPill(stringResource(R.string.seat_carrier))
                    }
                }
                Text(listOfNotNull(c.company?.vkn?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.vkn_label) + " $it" }, c.company?.taxOffice?.takeIf { it.isNotEmpty() }).joinToString("   "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                c.regionLead?.name?.takeIf { it.isNotEmpty() }?.let { Text("📍 " + stringResource(R.string.region_lead_label) + ": $it" + (c.regionLead.region?.let { r -> " · $r" } ?: ""), fontSize = 12.sp, color = colors.muted) }
            }
            Text(stringResource(R.string.company_legal_note), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }
        // address — cell-exit autosave
        Column {
            FormSection(stringResource(R.string.company_title).uppercase()) {
                FormRow(stringResource(R.string.company_short_name), f["shortName"].orEmpty(), { vm.edit("shortName", it) }, { vm.commit("shortName") }, vm.tick == "shortName")
                FormRow(stringResource(R.string.company_address), f["fullAddress"].orEmpty(), { vm.edit("fullAddress", it) }, { vm.commit("fullAddress") }, vm.tick == "fullAddress", stacked = true, minLines = 1, maxLines = 4)
                PickerFormRow(stringResource(R.string.company_city), f["city"].orEmpty(), vm.provinces.mapNotNull { it.name }, vm.tick == "city") { vm.setCity(it) }
                val districts = vm.provinces.firstOrNull { it.name == f["city"] }?.districts.orEmpty()
                if (districts.isNotEmpty()) PickerFormRow(stringResource(R.string.company_district), f["district"].orEmpty(), districts, vm.tick == "district") { vm.setDistrict(it) }
                else FormRow(stringResource(R.string.company_district), f["district"].orEmpty(), { vm.edit("district", it) }, { vm.commit("district") }, vm.tick == "district")
                FormRow(stringResource(R.string.company_postal), f["postalCode"].orEmpty(), { vm.edit("postalCode", it) }, { vm.commit("postalCode") }, vm.tick == "postalCode", mono = true, keyboardType = KeyboardType.Number)
                FormRow(stringResource(R.string.company_phone), f["phone"].orEmpty(), { vm.edit("phone", it) }, { vm.commit("phone") }, vm.tick == "phone", mono = true, keyboardType = KeyboardType.Phone)
                FormRow(stringResource(R.string.maps_link), f["mapsLink"].orEmpty(), { vm.edit("mapsLink", it) }, { vm.commit("mapsLink") }, vm.tick == "mapsLink", placeholder = "https://maps.app.goo.gl/…", keyboardType = KeyboardType.Uri)
            }
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.maps_link_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
                if (!c.company?.locationSource.isNullOrEmpty() && c.company?.lat != null) {
                    Text("📌 " + stringResource(if (c.company.locationSource == "manual") R.string.pinned_manual else R.string.pinned_geocode), fontSize = 12.sp, color = colors.faint)
                }
            }
        }
        // autonomy
        Column {
            FormSection(stringResource(R.string.autonomy_title).uppercase()) {
                SwitchRow(stringResource(R.string.auto_outward), vm.outward, vm.tick == "outwardRoutine") { vm.toggle("outwardRoutine", it) }
                SwitchRow(stringResource(R.string.auto_reorder), vm.reorder, vm.tick == "autoReorder") { vm.toggle("autoReorder", it) }
                SwitchRow(stringResource(R.string.auto_fund), vm.fund, vm.tick == "autoFund") { vm.toggle("autoFund", it) }
                FormRow(stringResource(R.string.auto_award_cap), f["awardCap"].orEmpty(), { vm.edit("awardCap", it) }, { vm.commit("awardCap") }, vm.tick == "awardCap", placeholder = "—", mono = true, keyboardType = KeyboardType.Decimal)
                FormRow(stringResource(R.string.auto_release), f["releaseDays"].orEmpty(), { vm.edit("releaseDays", it) }, { vm.commit("releaseDays") }, vm.tick == "releaseDays", placeholder = "—", mono = true, keyboardType = KeyboardType.Number)
            }
            Text(stringResource(R.string.autonomy_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }
        // tax plate
        FormSection(stringResource(R.string.tax_plate_title).uppercase()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (c.hasTaxFile == true) Text("🔍 " + stringResource(R.string.tax_plate_view), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { showTax = true; vm.openTax() })
                else Text(stringResource(R.string.tax_plate_missing), fontSize = 13.sp, color = colors.faint)
                Spacer(Modifier.weight(1f))
                if (vm.busyTax) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accentText)
                else {
                    if (vm.tick == "tax") Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Text("⬆ " + stringResource(R.string.tax_plate_upload), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) })
                }
            }
        }
    }

    if (showTax) {
        val bytes = vm.taxBytes
        if (bytes != null && vm.taxIsPdf) PdfViewerDialog(stringResource(R.string.tax_plate_title), data = bytes, fileName = "vergi-levhasi.pdf") { showTax = false }
        else Dialog(onDismissRequest = { showTax = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFFFFFF)).clickable { showTax = false }, contentAlignment = Alignment.Center) {
                val bmp = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                else CircularProgressIndicator(color = Color(0xFF1A1A1F))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, tick: Boolean, onChange: (Boolean) -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        if (tick) Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue))
    }
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
}

@Composable
private fun PickerFormRow(label: String, value: String, options: List<String>, tick: Boolean, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable(enabled = options.isNotEmpty()) { open = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = colors.muted)
            Spacer(Modifier.weight(1f))
            if (tick) Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
            Text(value.ifEmpty { "—" }, fontSize = 14.sp, color = colors.ink)
            Text(" ⌄", color = colors.faint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o, color = if (o == value) colors.accentText else colors.ink, fontSize = 14.sp) }, onClick = { open = false; onPick(o) }) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
}
