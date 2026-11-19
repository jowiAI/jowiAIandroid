package ai.workis.jowi.ui.screens.partner

import ai.workis.jowi.data.SellerSetup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import ai.workis.jowi.data.WorkisClock
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import ai.workis.jowi.ui.theme.Text
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
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.VisStyle
import ai.workis.jowi.data.vis
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.PaperInk
import ai.workis.jowi.ui.theme.PaperWhite
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
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

    /** Grants the CURRENT field lead concierge rights on the catalogue, or revokes (2026-09-15). */
    var helperOn by mutableStateOf<Boolean?>(null)
    fun setCatalogHelper(on: Boolean) {
        helperOn = on
        note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.companyCatalogHelper(jsonBody("enabled" to on)) }
            if (r != null && r.success) { flash("catalogHelper"); Graph.partner.refresh() } else { helperOn = null; note = r?.error ?: r?.message ?: err }
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

    /** A plate the server could not read as one: its sentence, and the file kept for "Yine de yükle". */
    class Doubted(val bytes: ByteArray, val name: String, val mime: String, val words: String)
    var doubted by mutableStateOf<Doubted?>(null)

    /**
     * The server reads the plate while it takes it (seconds — the spinner stands). A file it doubts
     * (400, `doubt: true`) comes back with its sentence and may be sent once more as it is
     * (`keep=1`); a plate with another firm's tax number is only refused.
     */
    fun uploadTax(bytes: ByteArray, name: String, mime: String, keep: Boolean = false) {
        if (bytes.size > 5 * 1024 * 1024) { note = ai.workis.jowi.data.applyErrorMessage("bad_file", lang); return }
        busyTax = true; note = null
        viewModelScope.launch {
            val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaType()))
            val keepPart = if (keep) MultipartBody.Part.createFormData("keep", "1") else null
            var doubt = false
            val (r, err) = try {
                Graph.api.companyTaxFileUpload(part, keepPart) to null
            } catch (e: retrofit2.HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
                val obj = runCatching { ai.workis.jowi.data.WorkisJson.decodeFromString<kotlinx.serialization.json.JsonObject>(body) }.getOrNull()
                fun word(k: String) = (obj?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                doubt = word("doubt") == "true"
                null to (word("error") ?: word("message") ?: ai.workis.jowi.data.statusMessage(e.code(), lang))
            } catch (e: java.io.IOException) {
                null to ai.workis.jowi.data.statusMessage(null, lang)
            } catch (e: Exception) {
                null to ai.workis.jowi.data.statusMessage(-1, lang)
            }
            busyTax = false
            if (r != null && r.success) { flash("tax"); Graph.partner.refresh() }
            else {
                val words = r?.error ?: r?.message ?: err
                if (doubt && !keep && words != null) doubted = Doubted(bytes, name, mime, words) else note = words
            }
        }
    }

    // ---- Workis'te satış (ledger 108): the tariff saves on the pick; the account and the plate
    // facts go by their own button, never on leaving a field. The fields follow the server's row.
    var tariff by mutableStateOf("")
        private set
    var iban by mutableStateOf("")
    var holder by mutableStateOf("")
    var plateOffice by mutableStateOf("")
    var plateCode by mutableStateOf("")
    var setupNote by mutableStateOf<String?>(null)
        private set
    var busySetup by mutableStateOf<String?>(null)
        private set

    fun seedSetup(c: PartnerCompany) {
        val s = c.sellerSetup ?: return
        tariff = s.tariff.orEmpty()
        if (holder.isEmpty()) holder = s.holder?.takeIf { it.isNotEmpty() } ?: c.company?.name.orEmpty()
        plateOffice = s.plateFacts?.office.orEmpty()
        plateCode = s.plateFacts?.code.orEmpty()
    }

    fun pickTariff(key: String, server: String) {
        if (key.isEmpty() || key == server || busySetup != null) return
        tariff = key
        saveSetup("tariff", jsonBody("tariff" to key)) { tariff = server } // a refused pick goes back
    }
    fun savePayout() = saveSetup("payout", jsonBody("iban" to iban, "holder" to holder), onOk = { iban = "" })
    fun saveFacts() = saveSetup("facts", jsonBody("plateOffice" to plateOffice, "plateCode" to plateCode))

    private fun saveSetup(key: String, body: kotlinx.serialization.json.JsonObject, onOk: () -> Unit = {}, onRefused: () -> Unit = {}) {
        if (busySetup != null) return
        setupNote = null; busySetup = key
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.companySellerSetup(body) }
            if (r != null && r.success) {
                onOk(); flash(key)
                Graph.partner.refresh() // the fields follow the re-read (seedSetup on the new row)
            } else {
                setupNote = r?.error ?: r?.message ?: err
                onRefused()
            }
            busySetup = null
        }
    }
}


/** The web split the Firma page (2026-09-16): "Firma bilgileri" keeps identity + address + tax plate, "Ayarlar" holds Yetkiler + Otonomi. */
enum class CompanyMode { Company, Settings }

@Composable
fun CompanySections(mode: CompanyMode = CompanyMode.Company, vm: CompanyViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val seat by Graph.partner.seat.collectAsState()
    val c = (seat as? PartnerSeatState.Seat)?.company ?: return
    LaunchedEffect(c.company?.id) { vm.seed(c) }
    LaunchedEffect(c.sellerSetup) { vm.seedSetup(c) }
    val f by vm.fields
    var showTax by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.uploadTax(bytes, if (mime.contains("pdf")) "levha.pdf" else "levha.jpg", mime)
    }

    // Jowi's screen context: which of the two Hesap doors is open, with its labels
    val contextLabels = if (mode == CompanyMode.Company) listOf(
        vis(stringResource(R.string.company_title)), vis(stringResource(R.string.company_short_name)), vis(stringResource(R.string.company_address)),
        vis(stringResource(R.string.company_city)), vis(stringResource(R.string.company_district)), vis(stringResource(R.string.company_postal)),
        vis(stringResource(R.string.company_phone)), vis(stringResource(R.string.maps_link)), vis(stringResource(R.string.tax_plate_title)),
        vis(stringResource(R.string.tax_plate_view), VisStyle.SECONDARY), vis(stringResource(R.string.tax_plate_upload), VisStyle.SECONDARY),
        vis(stringResource(R.string.vkn_label)), vis(stringResource(R.string.region_lead_label)),
    ) else listOf(
        vis(stringResource(R.string.settings_title)), vis(stringResource(R.string.permissions_title)), vis(stringResource(R.string.region_lead_label)),
        vis(stringResource(R.string.related_firms_label)), vis(stringResource(R.string.catalog_helper_toggle), VisStyle.SECONDARY),
        vis(stringResource(R.string.autonomy_title)),
        vis(stringResource(R.string.auto_outward), VisStyle.SECONDARY), vis(stringResource(R.string.auto_reorder), VisStyle.SECONDARY),
        vis(stringResource(R.string.auto_fund), VisStyle.SECONDARY), vis(stringResource(R.string.auto_award_cap)),
        vis(stringResource(R.string.auto_release)),
    )
    LaunchedEffect(mode) {
        ScreenContext.set(if (mode == CompanyMode.Company) "seller.company" else "seller.settings", visible = contextLabels)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteBand(vm.note)
        if (mode == CompanyMode.Settings) {
            // Yetkiler: the field-lead line + the catalogue-helper switch (only when
            // available). EVERY row here is conditional, so a seat that has none of
            // them — a plain buyer — would get a bare heading over an empty card.
            // The section is drawn only once it has something to say.
            val lead = c.regionLead ?: c.catalogHelper?.lead
            val leadLine = lead?.name?.takeIf { it.isNotEmpty() }
            val relatedFirms = c.catalogHelper?.lead?.relatedFirms.orEmpty()
            val helper = c.catalogHelper?.takeIf { it.available == true }
            if (leadLine != null || relatedFirms.isNotEmpty() || helper != null) {
                Column {
                    FormSection(stringResource(R.string.permissions_title).uppercase()) {
                        if (leadLine != null && lead != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.region_lead_label) + ": " + lead.line, fontSize = 13.sp, color = colors.muted)
                            }
                        }
                        // Madde 4/4 + 11/3: the lead's related-firm declaration is read
                        // BEFORE the switch below — the seller decides knowing it
                        val firms = relatedFirms
                        if (firms.isNotEmpty()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(WorkisIcons.Building, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.related_firms_label) + ": " + firms.joinToString(", "),
                                        fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (c.catalogHelper?.lead?.ownFirm == true) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "· " + stringResource(R.string.related_firms_own),
                                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.accentText,
                                        )
                                    }
                                }
                                Text(
                                    stringResource(R.string.related_firms_note),
                                    fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
                                    modifier = Modifier.padding(start = 20.dp),
                                )
                            }
                            Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                        }
                        val h = helper
                        if (h != null) {
                            val granted = vm.helperOn ?: (h.granted == true)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.catalog_helper_toggle), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                                    Text(
                                        if (granted) stringResource(R.string.catalog_helper_on) + (h.grantedAt?.let(WorkisClock::day)?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                                        else stringResource(R.string.catalog_helper_off),
                                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp,
                                        color = if (granted) SuccessGreen else colors.faint,
                                    )
                                }
                                if (vm.tick == "catalogHelper") Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
                                Switch(checked = granted, onCheckedChange = { vm.setCatalogHelper(it) }, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue))
                            }
                        }
                    }
                    if (helper != null) {
                        Text(stringResource(R.string.catalog_helper_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                    }
                }
            }
            // autonomy (moved here from the Firma page)
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
            return@Column
        }
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
                c.regionLead?.name?.takeIf { it.isNotEmpty() }?.let { Text("📍 " + stringResource(R.string.region_lead_label) + ": " + c.regionLead.line, fontSize = 12.sp, color = colors.muted) }
            }
            Text(stringResource(R.string.company_legal_note), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }
        // Workis'te satış — right under the identity block; nothing for a firm that does not sell
        c.sellerSetup?.let { SellingSection(it, vm) }
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

    vm.doubted?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.doubted = null },
            text = { Text(d.words, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink) },
            confirmButton = { TextButton(onClick = { vm.doubted = null; vm.uploadTax(d.bytes, d.name, d.mime, keep = true) }) { Text(stringResource(R.string.tax_plate_keep), color = colors.accentText) } },
            dismissButton = { TextButton(onClick = { vm.doubted = null }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
            containerColor = colors.surface,
        )
    }

    if (showTax) {
        val bytes = vm.taxBytes
        if (bytes != null && vm.taxIsPdf) PdfViewerDialog(stringResource(R.string.tax_plate_title), data = bytes, fileName = "vergi-levhasi.pdf") { showTax = false }
        else Dialog(onDismissRequest = { showTax = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(PaperWhite).clickable { showTax = false }, contentAlignment = Alignment.Center) {
                val bmp = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                else CircularProgressIndicator(color = PaperInk)
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

/**
 * What a seller's first order waits for (ledger 108): the tariff, the payout account, the plate's
 * three facts. Every sentence about a tariff is the server's (`options[].words`). Buttons here are
 * bordered words — the page has no fill of its own.
 */
@Composable
private fun SellingSection(setup: SellerSetup, vm: CompanyViewModel) {
    val colors = WorkisTheme.colors
    val server = setup.tariff.orEmpty()
    val options = setup.options.orEmpty().filter { !it.key.isNullOrEmpty() }
    Column {
        FormSection(stringResource(R.string.selling_title).uppercase()) {
            if (setup.owed.orEmpty().isNotEmpty()) {
                Text(stringResource(R.string.selling_owed), fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
            }
            // tariff — the pick saves at once; "seçilmedi" stands only while none is chosen
            var open by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.fillMaxWidth().clickable(enabled = options.isNotEmpty() && vm.busySetup == null) { open = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tariff_label), fontSize = 14.sp, color = colors.muted)
                    Spacer(Modifier.weight(1f))
                    if (vm.busySetup == "tariff") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accentText)
                    else if (vm.tick == "tariff") Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    val chosen = options.firstOrNull { it.key == vm.tariff }
                    Text(chosen?.let { it.title ?: it.key.orEmpty() } ?: stringResource(R.string.tariff_not_chosen), fontSize = 14.sp, color = if (chosen != null) colors.ink else colors.faint)
                    Text(" ⌄", color = colors.faint)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.title ?: o.key.orEmpty(), color = if (o.key == vm.tariff) colors.accentText else colors.ink, fontSize = 14.sp) },
                            onClick = { open = false; vm.pickTariff(o.key.orEmpty(), server) },
                        )
                    }
                }
            }
            options.filter { !it.words.isNullOrEmpty() && (vm.tariff.isEmpty() || vm.tariff == it.key) }.forEach { o ->
                Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (vm.tariff.isEmpty()) o.title?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink) }
                    Text(o.words.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
            // payout account — masked as it comes
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.payout_label), fontSize = 14.sp, color = colors.muted)
                Spacer(Modifier.weight(1f))
                val saved = setup.iban?.takeIf { it.isNotEmpty() }
                if (saved != null) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(saved, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                    setup.holder?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
                } else Text(stringResource(R.string.payout_not_written), fontSize = 14.sp, color = colors.faint)
            }
            Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
            FormRow("IBAN", vm.iban, { vm.iban = it.uppercase() }, {}, false, placeholder = "TR00 0000 0000 0000 0000 0000 00", mono = true, stacked = true, maxLines = 1)
            FormRow(stringResource(R.string.payout_holder), vm.holder, { vm.holder = it }, {}, false, stacked = true, maxLines = 2)
            SetupButton(
                stringResource(if (setup.iban.isNullOrEmpty()) R.string.payout_save else R.string.payout_change),
                enabled = vm.iban.isNotBlank() && vm.busySetup == null, busy = vm.busySetup == "payout", tick = vm.tick == "payout", onClick = vm::savePayout,
            )
            // the plate's three facts — the firm writes what the file did not give
            val pf = setup.plateFacts
            if (setup.plate == true && pf != null) {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                if (pf.missing.orEmpty().isNotEmpty()) {
                    Text(stringResource(R.string.plate_facts_missing), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                FormRow(stringResource(R.string.tax_office_label), vm.plateOffice, { vm.plateOffice = it.uppercase() }, {}, false, placeholder = "—")
                FormRow(stringResource(R.string.vkn_label), pf.vkn?.takeIf { it.isNotEmpty() } ?: "—", {}, {}, false, mono = true, locked = true)
                FormRow(stringResource(R.string.plate_code_label), vm.plateCode, { vm.plateCode = it.uppercase() }, {}, false, placeholder = "—", mono = true)
                if (pf.verified == true) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.plate_verified), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuccessGreen)
                    }
                }
                if (vm.plateOffice != pf.office.orEmpty() || vm.plateCode != pf.code.orEmpty()) {
                    SetupButton(stringResource(R.string.plate_facts_save), enabled = vm.busySetup == null, busy = vm.busySetup == "facts", tick = false, onClick = vm::saveFacts)
                } else if (vm.tick == "facts") {
                    Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            } else if (setup.plate != true) {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.tax_plate_title), fontSize = 14.sp, color = colors.muted)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.tax_plate_missing), fontSize = 14.sp, color = colors.faint)
                }
            }
        }
        vm.setupNote?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) }
    }
}

@Composable
private fun SetupButton(title: String, enabled: Boolean, busy: Boolean, tick: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (enabled) colors.accentText else colors.faint)
        }
        Spacer(Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accentText)
        else if (tick) Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold)
    }
}
