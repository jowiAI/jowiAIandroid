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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.ApplicationDetail
import ai.workis.jowi.data.ApplicationRow
import ai.workis.jowi.data.UpdateFieldBody
import ai.workis.jowi.data.absoluteWorkisUrl
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.PdfPages
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Application detail — the web /console/basvuru/{id}/ page's native twin.
 * Review-before-approve: every field is inline-editable with the wizard's
 * cell-exit autosave, but the tick here is SERVER-confirmed (it lights only
 * after POST .../update/ succeeds). One field per call.
 */
class AppDetailViewModel : ViewModel() {
    var detail by mutableStateOf<ApplicationDetail?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var actionNote by mutableStateOf<String?>(null)
    var signedPdf by mutableStateOf<ByteArray?>(null)
    var loadingPdf by mutableStateOf(false)
        private set

    /** The SAVED short name: server value on load, edited value once its save succeeds. */
    var savedShortName by mutableStateOf<String?>(null)
        private set
    var tickField by mutableStateOf<String?>(null)
        private set

    val fields = mutableStateOf(mapOf<String, String>())
    private val dirty = mutableSetOf<String>()
    private var tickJob: Job? = null
    private var id: String? = null

    fun bind(appId: String, row: ApplicationRow) {
        if (id == appId) return
        id = appId
        load(row)
    }

    fun load(row: ApplicationRow? = null) {
        val appId = id ?: return
        loadError = null
        viewModelScope.launch {
            when (val r = safeCall(Graph.language.value) { Graph.api.applicationDetail(appId) }) {
                is ApiResult.Ok -> {
                    val d = r.value
                    detail = d
                    savedShortName = d.shortName
                    // seeding isn't a user edit — nothing goes dirty
                    fields.value = mapOf(
                        "shortName" to (d.shortName ?: row?.shortName ?: ""),
                        "email" to (d.email ?: row?.email ?: ""),
                        "sector" to (d.sector ?: ""),
                        "city" to (d.city ?: row?.city ?: ""),
                        "taxNumber" to (d.taxNumber ?: row?.vkn ?: ""),
                        "taxOffice" to (d.taxOffice ?: ""),
                        "entityType" to (d.entityType ?: ""),
                        "address" to (d.address ?: ""),
                        "catalogFormats" to (d.catalogFormats ?: ""),
                    )
                    dirty.clear()
                }
                is ApiResult.Err -> loadError = r.message
            }
        }
    }

    fun edit(field: String, value: String) {
        fields.value = fields.value + (field to value)
        dirty += field
    }

    /** Cell exit → one POST; the tick lights only on success. */
    fun commit(field: String) {
        val appId = id ?: return
        if (!dirty.remove(field)) return
        val value = fields.value[field] ?: return
        val lang = Graph.language.value
        viewModelScope.launch {
            try {
                val r = Graph.api.applicationUpdate(appId, UpdateFieldBody(field, value))
                if (r.success) {
                    if (field == "shortName") savedShortName = value
                    tickJob?.cancel()
                    tickField = field
                    tickJob = launch {
                        delay(1400)
                        if (tickField == field) tickField = null
                    }
                } else {
                    actionNote = r.message ?: r.error ?: statusMessage(-1, lang)
                }
            } catch (e: HttpException) {
                actionNote = messageFromBody(e, lang)
            } catch (e: Exception) {
                actionNote = statusMessage(null, lang)
            }
        }
    }

    /** signedPdfUrl is a TOKEN-AUTH endpoint — fetched by us, shown natively. */
    fun openSignedPdf(path: String) {
        if (loadingPdf) return
        loadingPdf = true
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) { Graph.api.download(absoluteWorkisUrl(path)).use { it.bytes() } }
            }.getOrNull()
            if (bytes != null && looksLikePdf(bytes)) signedPdf = bytes
            else actionNote = statusMessage(null, Graph.language.value)
            loadingPdf = false
        }
    }
}

@Composable
fun ApplicationDetailScreen(console: ConsoleViewModel, row: ApplicationRow, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    val appId = row.applicationId
    if (appId == null) { // a row without an id has nothing to review
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val vm: AppDetailViewModel = viewModel(key = "app-detail-$appId")
    LaunchedEffect(appId) { vm.bind(appId, row) }
    val detail = vm.detail
    val fields by vm.fields

    // Title follows the SAVED short name — never the list row's snapshot,
    // which goes stale on the first edit.
    val title = vm.savedShortName?.trim().takeUnless { it.isNullOrEmpty() }
        ?: row.shortName?.takeIf { it.isNotEmpty() }
        ?: row.company.orEmpty()

    ConsoleSub(
        title = title,
        onBack = onBack,
        trailing = { PrimaryAction(console, vm, row) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(row, detail)

            vm.actionNote?.let {
                Text(
                    it,
                    fontSize = 13.sp, lineHeight = 18.sp, color = Beige,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BeigeBg, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }

            when {
                detail != null -> {
                    FieldsCard(vm, fields, editable = detail.canEdit ?: true)
                    AgreementCard(vm, detail)
                    PriorQuestionsCard(detail)
                }
                vm.loadError != null -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.loadError.orEmpty(), color = colors.danger, fontSize = 14.sp)
                    TextButton(onClick = { vm.load() }) {
                        Text(stringResource(R.string.retry), color = colors.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    WorkisMark(size = 26, breathing = true)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    vm.signedPdf?.let { data ->
        Dialog(
            onDismissRequest = { vm.signedPdf = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFFFFFFFF))) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.signedPdf = null }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.done), tint = Color(0xFF1A1A1F))
                    }
                }
                PdfPages(data, Modifier.fillMaxSize(), spinnerColor = Color(0xFF1A1A1F))
            }
        }
    }
}

/** Review → approve on one screen: create partner, or approve a pending one. */
@Composable
private fun PrimaryAction(console: ConsoleViewModel, vm: AppDetailViewModel, row: ApplicationRow) {
    val d = vm.detail
    val partnerId = d?.partnerId ?: row.partnerId
    val partnerStatus = d?.partnerStatus ?: row.partnerStatus
    val appId = row.applicationId ?: return
    val busy = console.busyId != null
    val (label, action) = when {
        partnerId == null -> R.string.create_partner to {
            console.createPartner(appId) { vm.actionNote = it; vm.load() }
        }
        partnerStatus == "pending" -> R.string.approve to {
            console.approvePartner(partnerId) { vm.actionNote = it; vm.load() }
        }
        else -> return
    }
    Button(
        onClick = action,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill)
        } else {
            Text(stringResource(label), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Header(row: ApplicationRow, detail: ApplicationDetail?) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.company ?: detail?.company ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (detail?.role ?: row.role)?.takeIf { it.isNotEmpty() }?.let { Chip(it.uppercase(), colors.accentText) }
            detail?.status?.takeIf { it.isNotEmpty() }?.let { Chip(it.uppercase(), Beige) }
            (detail?.resolvedAppliedAt ?: row.appliedAt)?.let {
                Text(it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
            Spacer(Modifier.weight(1f))
            (detail?.confidence ?: row.confidence)?.let {
                Text("AI " + "%.2f".format(java.util.Locale.ROOT, it), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun FieldsCard(vm: AppDetailViewModel, fields: Map<String, String>, editable: Boolean) {
    @Composable
    fun row(
        field: String, labelRes: Int, keyboard: KeyboardType = KeyboardType.Text,
        mono: Boolean = false, multiline: Boolean = false, locked: Boolean = false,
    ) = FormRow(
        label = stringResource(labelRes),
        value = fields[field].orEmpty(),
        onChange = { vm.edit(field, it) },
        onCommit = { vm.commit(field) },
        tick = vm.tickField == field,
        keyboardType = keyboard,
        mono = mono,
        stacked = multiline,
        minLines = 1,
        maxLines = 4,
        locked = locked,
        frozen = !editable, // approved applications freeze (server 403s edits anyway)
    )
    FormSection(null) {
        row("shortName", R.string.f_short_name)
        row("email", R.string.email_label, keyboard = KeyboardType.Email, mono = true)
        row("sector", R.string.f_sector, multiline = true)
        row("city", R.string.f_city)
        // legal identity from the tax certificate — server refuses writes
        row("taxNumber", R.string.f_tax_number, mono = true, locked = true)
        row("taxOffice", R.string.f_tax_office)
        row("entityType", R.string.f_entity_type)
        row("address", R.string.f_address, multiline = true)
        row("catalogFormats", R.string.f_catalog_formats)
    }
}

@Composable
private fun SectionEyebrow(textRes: Int) {
    Text(
        stringResource(textRes),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
        letterSpacing = 1.5.sp, color = WorkisTheme.colors.faint,
    )
}

@Composable
private fun EvidenceCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Agreement acceptance — read-only evidence card. */
@Composable
private fun AgreementCard(vm: AppDetailViewModel, detail: ApplicationDetail) {
    val colors = WorkisTheme.colors
    EvidenceCard {
        SectionEyebrow(R.string.agreement_section)
        val ag = detail.agreement
        if (ag == null) {
            Text(stringResource(R.string.no_agreement), fontSize = 13.sp, color = colors.faint)
            return@EvidenceCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ag.version?.let { Chip("${(ag.kind ?: "supplier").uppercase()} V$it", SuccessGreen) }
            ag.acceptedAt?.let {
                Text(
                    it.take(16).replace("T", " "),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted,
                )
            }
        }
        Text(
            listOfNotNull(
                ag.ip?.let { "IP $it" },
                ag.sha?.let { "#" + it.take(12) },
                ag.signerName?.let { "✍︎ $it" },
            ).joinToString(" · "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint,
        )
        ag.signedPdfUrl?.takeIf { it.isNotEmpty() }?.let { path ->
            TextButton(
                onClick = { vm.openSignedPdf(path) },
                enabled = !vm.loadingPdf,
                modifier = Modifier
                    .height(34.dp)
                    .border(1.dp, colors.border, CircleShape),
            ) {
                if (vm.loadingPdf) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.ink)
                } else {
                    Text("⬇ " + stringResource(R.string.signed_pdf), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                }
            }
        }
    }
}

/** The visitor's pre-application trail; dates render as yyyy-MM-dd (no trailing T). */
@Composable
private fun PriorQuestionsCard(detail: ApplicationDetail) {
    val colors = WorkisTheme.colors
    val questions = detail.priorQuestions.orEmpty().filter { !it.q.isNullOrEmpty() }
    if (questions.isEmpty()) return
    EvidenceCard {
        SectionEyebrow(R.string.prior_questions_section)
        questions.forEach { q ->
            Row(verticalAlignment = Alignment.Top) {
                q.at?.let {
                    Text(it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                    Spacer(Modifier.width(8.dp))
                }
                Text("“${q.q}”", fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, modifier = Modifier.weight(1f))
                q.tag?.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(it.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint)
                }
            }
        }
    }
}
