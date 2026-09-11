package ai.workis.jowi.ui.screens.expert

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.DepartureBody
import ai.workis.jowi.data.ExpertPayout
import ai.workis.jowi.data.ExpertProfile
import ai.workis.jowi.data.PayoutBody
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.OutcomeView
import ai.workis.jowi.ui.components.openInApp
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.util.Locale

/**
 * The web Profil page as Hesap sections for the expert seat (slice 2): name
 * visibility, public profile, affiliations, payout account + tax status,
 * departure / rejoin. Fields save on cell exit with the server-confirmed tick.
 */
class ExpertAccountViewModel : ViewModel() {
    var profile by mutableStateOf<ExpertProfile?>(null)
        private set
    var payout by mutableStateOf<ExpertPayout?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var tick by mutableStateOf<String?>(null)
        private set

    var showName by mutableStateOf(false)
    val fields = mutableStateOf(mapOf<String, String>())
    private val dirty = mutableSetOf<String>()
    private var tickJob: Job? = null
    private val lang get() = Graph.language.value

    val left: Boolean get() = !profile?.left.isNullOrEmpty()

    fun load() {
        viewModelScope.launch {
            runCatching { Graph.api.expertProfile() }.getOrNull()?.let { p ->
                profile = p
                showName = p.showName ?: false
                fields.value = fields.value + mapOf(
                    "title" to p.title.orEmpty(),
                    "bio" to p.bio.orEmpty(),
                    "affiliations" to p.affiliations.orEmpty().joinToString(", "),
                )
            }
            runCatching { Graph.api.payout() }.getOrNull()?.let { seedPayout(it) }
            dirty.clear()
        }
    }

    private fun seedPayout(p: ExpertPayout) {
        payout = p
        fields.value = fields.value + mapOf(
            "iban" to p.iban.orEmpty(), "holder" to p.holder.orEmpty(), "taxStatus" to p.taxStatus.orEmpty(),
            "taxId" to p.taxId.orEmpty(), "taxOffice" to p.taxOffice.orEmpty(), "address" to p.billingAddress.orEmpty(),
        )
    }

    fun edit(key: String, value: String) { fields.value = fields.value + (key to value); dirty += key }

    private fun flash(key: String) {
        tickJob?.cancel()
        tick = key
        tickJob = viewModelScope.launch { delay(1400); if (tick == key) tick = null }
    }

    fun toggleShowName(on: Boolean) {
        showName = on
        viewModelScope.launch {
            try {
                Graph.api.expertProfileUpdate(buildJsonObject { put("showName", JsonPrimitive(on)) })
                flash("showName")
            } catch (e: HttpException) { note = messageFromBody(e, lang) } catch (e: Exception) { note = statusMessage(null, lang) }
        }
    }

    /** Cell exit → one POST; the tick lights only on success. */
    fun commit(key: String) {
        if (!dirty.remove(key)) return
        val value = fields.value[key].orEmpty()
        viewModelScope.launch {
            try {
                when (key) {
                    "title", "bio", "affiliations" -> {
                        Graph.api.expertProfileUpdate(buildJsonObject { put(key, JsonPrimitive(value)) })
                        flash(key)
                    }
                    else -> savePayout(key)
                }
            } catch (e: HttpException) { note = messageFromBody(e, lang) } catch (e: Exception) { note = statusMessage(null, lang) }
        }
    }

    fun setTaxStatus(value: String) {
        fields.value = fields.value + ("taxStatus" to value)
        viewModelScope.launch {
            try { savePayout("taxStatus") } catch (e: HttpException) { note = messageFromBody(e, lang) } catch (e: Exception) { note = statusMessage(null, lang) }
        }
    }

    private suspend fun savePayout(key: String) {
        val f = fields.value
        val p = Graph.api.payoutSave(
            PayoutBody(
                iban = f["iban"].orEmpty(), holder = f["holder"].orEmpty(), taxStatus = f["taxStatus"].orEmpty(),
                taxId = f["taxId"].orEmpty(), taxOffice = f["taxOffice"].orEmpty(), billingAddress = f["address"].orEmpty(),
            ),
        )
        payout = p
        flash(key)
    }

    fun rejoin() {
        viewModelScope.launch {
            try {
                val r = Graph.api.expertRejoin()
                note = r.message ?: r.error
                if (r.success) load()
            } catch (e: HttpException) { note = messageFromBody(e, lang) } catch (e: Exception) { note = statusMessage(null, lang) }
        }
    }
}

@Composable
fun ExpertAccountSections(onDeparture: () -> Unit, vm: ExpertAccountViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    val f by vm.fields

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteBand(vm.note)

        // Ad görünürlüğü
        FormSection(stringResource(R.string.name_visibility_title)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_name_toggle), fontSize = 14.sp, color = colors.ink)
                    Text(stringResource(R.string.show_name_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
                }
                if (vm.tick == "showName") Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                Switch(checked = vm.showName, onCheckedChange = { vm.toggleShowName(it) }, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue))
            }
        }

        // Herkese açık profil
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.public_profile_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = colors.faint)
                if (!vm.showName) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_hidden), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.5.sp, color = Beige)
                }
            }
            FormSection(null) {
                FormRow(stringResource(R.string.title_field), f["title"].orEmpty(), { vm.edit("title", it) }, { vm.commit("title") }, vm.tick == "title", placeholder = stringResource(R.string.title_placeholder))
                FormRow(stringResource(R.string.bio_field), f["bio"].orEmpty(), { vm.edit("bio", it) }, { vm.commit("bio") }, vm.tick == "bio", placeholder = stringResource(R.string.bio_placeholder), stacked = true, minLines = 2, maxLines = 5)
                vm.profile?.publicProfileUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    Text(
                        "↗ " + stringResource(R.string.open_public_profile),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.blue,
                        modifier = Modifier.fillMaxWidth().clickable { openInApp(context, url) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
            Text(stringResource(R.string.profile_note), fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }

        // İlişkili firmalar ve markalar
        Column {
            FormSection(stringResource(R.string.affiliations_title)) {
                FormRow(stringResource(R.string.affiliations_field), f["affiliations"].orEmpty(), { vm.edit("affiliations", it) }, { vm.commit("affiliations") }, vm.tick == "affiliations", placeholder = stringResource(R.string.affiliations_placeholder), stacked = true, minLines = 1, maxLines = 4)
            }
            Text(stringResource(R.string.affiliations_note), fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }

        // Ödeme hesabı ve vergi durumu
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.payout_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = colors.faint)
                Spacer(Modifier.width(8.dp))
                val ready = vm.payout?.payoutReady == true
                Text(
                    stringResource(if (ready) R.string.payout_ready_tag else R.string.payout_missing),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.5.sp,
                    color = if (ready) SuccessGreen else Beige, maxLines = 2, modifier = Modifier.weight(1f),
                )
            }
            FormSection(null) {
                FormRow(stringResource(R.string.iban_field), f["iban"].orEmpty(), { vm.edit("iban", it.uppercase(Locale.ROOT)) }, { vm.commit("iban") }, vm.tick == "iban", placeholder = "TR00 0000 0000 0000 0000 0000 00", mono = true)
                FormRow(stringResource(R.string.holder_field), f["holder"].orEmpty(), { vm.edit("holder", it) }, { vm.commit("holder") }, vm.tick == "holder")
                TaxStatusRow(vm, f["taxStatus"].orEmpty())
                FormRow(stringResource(R.string.tax_id_field), f["taxId"].orEmpty(), { vm.edit("taxId", it) }, { vm.commit("taxId") }, vm.tick == "taxId", mono = true, keyboardType = KeyboardType.Number)
                FormRow(stringResource(R.string.tax_office_field), f["taxOffice"].orEmpty(), { vm.edit("taxOffice", it) }, { vm.commit("taxOffice") }, vm.tick == "taxOffice")
                FormRow(stringResource(R.string.address_field), f["address"].orEmpty(), { vm.edit("address", it) }, { vm.commit("address") }, vm.tick == "address", placeholder = stringResource(R.string.address_placeholder), stacked = true, minLines = 1, maxLines = 4)
            }
            Text(stringResource(R.string.payout_note), fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }

        // Ayrılma / geri dönüş
        if (vm.left) {
            Column(
                Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.left_badge), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = Beige)
                Text(stringResource(R.string.rejoin_sub), fontSize = 13.sp, lineHeight = 18.sp, color = Beige)
                TextButton(onClick = { vm.rejoin() }) { Text(stringResource(R.string.rejoin_row), color = colors.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable(onClick = onDeparture)
                    .padding(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.departure_row), color = colors.danger, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Tax status — the server's option list as a dropdown row; saves at once. */
@Composable
private fun TaxStatusRow(vm: ExpertAccountViewModel, value: String) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    val options = vm.payout?.taxStatusOptions.orEmpty().filter { it.value != null }
    val label = options.firstOrNull { it.value == value }?.label ?: value.ifEmpty { "—" }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.tax_status_field), fontSize = 14.sp, color = colors.muted)
            Spacer(Modifier.weight(1f))
            if (vm.tick == "taxStatus") Text("✓ ", color = SuccessGreen, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 14.sp, color = colors.ink)
            Text(" ⌄", color = colors.faint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label ?: o.value.orEmpty(), color = if (o.value == value) colors.accentText else colors.ink, fontSize = 14.sp) },
                    onClick = { open = false; vm.setTaxStatus(o.value.orEmpty()) },
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
}

/** Platformdan ayrılma (agreement 4.1) — the typed confirmation, then the outcome shape. */
@Composable
fun ExpertDepartureScreen(onClose: () -> Unit, onLeft: () -> Unit) {
    val colors = WorkisTheme.colors
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val word = stringResource(R.string.departure_typed)
    val confirmed = typed.trim().uppercase(Locale.forLanguageTag("tr")) == word
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        if (done) {
            OutcomeView(
                title = stringResource(R.string.departure_done_title),
                message = stringResource(R.string.departure_done),
                actionTitle = stringResource(R.string.done),
                onAction = { onLeft(); onClose() },
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                iconColor = Beige,
            )
            return@Box
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.cancel), color = colors.accentText, fontSize = 15.sp) }
                Spacer(Modifier.weight(1f))
            }
            Text(stringResource(R.string.departure_title), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.departure_what), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            Spacer(Modifier.height(8.dp))
            listOf(R.string.departure_b1, R.string.departure_b2, R.string.departure_b3, R.string.departure_b4).forEach { res ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", color = colors.faint, modifier = Modifier.width(18.dp))
                    Text(stringResource(res), fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = typed, onValueChange = { typed = it },
                placeholder = { Text(word, color = colors.faint, fontFamily = WorkisMono) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.ink),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = colors.blue),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.departure_hint), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val r = Graph.api.expertDeparture(DepartureBody(typed.trim()))
                            if (r.success) done = true else error = when (r.error) {
                                "confirm_required" -> if (Graph.language.value == "en") "Type the confirmation word exactly." else "Onay sözcüğünü aynen yazın."
                                "already_left" -> if (Graph.language.value == "en") "You have already left." else "Zaten ayrılmışsınız."
                                else -> r.message ?: r.error
                            }
                        } catch (e: HttpException) { error = messageFromBody(e, Graph.language.value) } catch (e: Exception) { error = statusMessage(null, Graph.language.value) }
                        busy = false
                    }
                },
                enabled = confirmed && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.canvas, disabledContainerColor = colors.surface, disabledContentColor = colors.faint),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.canvas)
                else Text(stringResource(R.string.departure_action), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = colors.danger, fontSize = 13.sp) }
            Spacer(Modifier.height(30.dp))
        }
    }
}
