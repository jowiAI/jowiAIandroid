package ai.workis.jowi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApplySubmitBody
import ai.workis.jowi.data.applyErrorMessage
import ai.workis.jowi.data.errorCodeFromBody
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.AgreementLoader
import ai.workis.jowi.ui.components.AgreementPaper
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.components.OutcomeView
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val DRAFT_PREFIX = "workis_expert_draft."

/**
 * Expert application — the native twin of the web /uzman-basvuru/ form.
 * Person-focused and separate from the partner wizard (no tax plate, no
 * company): one page of fields, the expert agreement as the paper + Sign-Here
 * band, the consent row, submit in the header. Fields + signature autosave to
 * a SharedPreferences draft on cell exit (green tick), restore on open, clear
 * on success. Submits role=expert to POST /workis/apply/.
 */
class ExpertApplyViewModel : ViewModel() {
    val agreement = AgreementLoader(viewModelScope)

    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var sector by mutableStateOf("")
    var expertise by mutableStateOf("")
    var affiliation by mutableStateOf("")
    var signerName by mutableStateOf("")
    var agreed by mutableStateOf(false)

    var tickField by mutableStateOf<String?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sent by mutableStateOf(false)
        private set

    private val dirty = mutableSetOf<String>()
    private var tickJob: Job? = null

    init {
        restoreDraft()
        agreement.loadKey("expert")
    }

    val formValid: Boolean
        get() = listOf(name, sector, expertise, affiliation).none { it.isBlank() } &&
            email.contains("@") && email.contains(".") &&
            agreed && signerName.isNotBlank()

    /** A keystroke in a row marks it dirty; a draft restore is not a user edit. */
    fun edit(field: String, value: String) {
        when (field) {
            "name" -> name = value
            "email" -> email = value
            "sector" -> sector = value
            "expertise" -> expertise = value
            "affiliation" -> affiliation = value
            "signer_name" -> signerName = value
        }
        dirty += field
    }

    /** Cell exit: persist the draft, flash the tick on that row. */
    fun commit(field: String) {
        if (!dirty.remove(field)) return
        saveDraft()
        tickJob?.cancel()
        tickField = field
        tickJob = viewModelScope.launch {
            delay(1400)
            if (tickField == field) tickField = null
        }
    }

    private fun saveDraft() {
        Graph.prefs.edit()
            .putString(DRAFT_PREFIX + "name", name)
            .putString(DRAFT_PREFIX + "email", email)
            .putString(DRAFT_PREFIX + "sector", sector)
            .putString(DRAFT_PREFIX + "expertise", expertise)
            .putString(DRAFT_PREFIX + "affiliation", affiliation)
            .putString(DRAFT_PREFIX + "signer_name", signerName)
            .apply()
    }

    private fun restoreDraft() {
        val p = Graph.prefs
        name = p.getString(DRAFT_PREFIX + "name", "") ?: ""
        email = p.getString(DRAFT_PREFIX + "email", "") ?: ""
        sector = p.getString(DRAFT_PREFIX + "sector", "") ?: ""
        expertise = p.getString(DRAFT_PREFIX + "expertise", "") ?: ""
        affiliation = p.getString(DRAFT_PREFIX + "affiliation", "") ?: ""
        signerName = p.getString(DRAFT_PREFIX + "signer_name", "") ?: ""
    }

    private fun clearDraft() {
        Graph.prefs.edit().also { e ->
            listOf("name", "email", "sector", "expertise", "affiliation", "signer_name")
                .forEach { e.remove(DRAFT_PREFIX + it) }
        }.apply()
    }

    fun submit() {
        if (submitting || !formValid) return
        if (dirty.isNotEmpty()) { dirty.clear(); saveDraft() }
        submitting = true
        error = null
        val lang = Graph.language.value
        viewModelScope.launch {
            try {
                val r = Graph.api.applySubmit(
                    ApplySubmitBody(
                        role = "expert",
                        company = name.trim(), // the web form's 'company' field = the person's full name
                        email = email.trim(),
                        agree = true,
                        signerName = signerName.trim(),
                        sector = sector.trim(),
                        expertise = expertise.trim(),
                        affiliation = affiliation.trim(),
                        lang = lang,
                    ),
                )
                if (r.success) {
                    sent = true
                    clearDraft()
                } else {
                    error = applyErrorMessage(r.error, lang)
                }
            } catch (e: HttpException) {
                error = applyErrorMessage(errorCodeFromBody(e), lang)
            } catch (e: Exception) {
                error = statusMessage(null, lang)
            }
            submitting = false
        }
    }
}

@Composable
fun ExpertApplyScreen(onClose: () -> Unit, vm: ExpertApplyViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        if (vm.sent) {
            SuccessView(onClose)
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.statusBarsPadding().height(72.dp)) // clears the floating header at rest
            Text(
                stringResource(R.string.expert_title),
                fontFamily = WorkisMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            MarkdownText(stringResource(R.string.expert_intro), color = colors.muted, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(16.dp))

            FormSection(stringResource(R.string.expert_section)) {
                FormRow(
                    label = stringResource(R.string.expert_name),
                    value = vm.name, onChange = { vm.edit("name", it) },
                    onCommit = { vm.commit("name") }, tick = vm.tickField == "name",
                    placeholder = stringResource(R.string.expert_name_placeholder),
                )
                FormRow(
                    label = stringResource(R.string.expert_email),
                    value = vm.email, onChange = { vm.edit("email", it) },
                    onCommit = { vm.commit("email") }, tick = vm.tickField == "email",
                    placeholder = stringResource(R.string.expert_email_placeholder),
                    keyboardType = KeyboardType.Email, mono = true,
                )
                FormRow(
                    label = stringResource(R.string.expert_sector),
                    value = vm.sector, onChange = { vm.edit("sector", it) },
                    onCommit = { vm.commit("sector") }, tick = vm.tickField == "sector",
                    placeholder = stringResource(R.string.expert_sector_placeholder),
                    stacked = true, minLines = 1, maxLines = 3,
                )
                FormRow(
                    label = stringResource(R.string.expert_summary),
                    value = vm.expertise, onChange = { vm.edit("expertise", it) },
                    onCommit = { vm.commit("expertise") }, tick = vm.tickField == "expertise",
                    placeholder = stringResource(R.string.expert_summary_placeholder),
                    stacked = true, minLines = 3, maxLines = 8,
                )
                FormRow(
                    label = stringResource(R.string.expert_affiliation),
                    value = vm.affiliation, onChange = { vm.edit("affiliation", it) },
                    onCommit = { vm.commit("affiliation") }, tick = vm.tickField == "affiliation",
                    placeholder = stringResource(R.string.expert_affiliation_placeholder),
                    stacked = true, minLines = 2, maxLines = 6,
                )
            }
            Spacer(Modifier.height(12.dp))
            Footnote(stringResource(R.string.expert_email_hint))
            Spacer(Modifier.height(8.dp))
            Footnote(stringResource(R.string.expert_affiliation_note))

            // The signing room — same paper as the wizard, expert text.
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.apply_step4_sub),
                fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted,
            )
            Spacer(Modifier.height(12.dp))
            AgreementPaper(
                loader = vm.agreement,
                signerName = vm.signerName,
                onSignerChange = { vm.edit("signer_name", it) },
                onSignerCommit = { vm.commit("signer_name") },
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = vm.agreed,
                    onCheckedChange = { vm.agreed = it },
                    colors = CheckboxDefaults.colors(checkedColor = Kiremit500, checkmarkColor = OnKiremitFill),
                )
                MarkdownText(
                    stringResource(R.string.expert_agree),
                    color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            vm.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = colors.danger, fontSize = 14.sp)
            }
            Spacer(Modifier.height(30.dp))
        }

        // Floating header: back circle left, submit pill right — the ONLY tinted control.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas.copy(alpha = 0.92f))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.ink)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.submit() },
                enabled = vm.formValid && !vm.submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Kiremit400,
                    contentColor = OnKiremitFill,
                    disabledContainerColor = colors.surface,
                    disabledContentColor = colors.faint,
                ),
                modifier = Modifier.height(40.dp),
            ) {
                if (vm.submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                } else {
                    Text(stringResource(R.string.expert_submit), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = WorkisTheme.colors.faint,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SuccessView(onClose: () -> Unit) {
    OutcomeView(
        title = stringResource(R.string.apply_sent_title),
        message = stringResource(R.string.expert_sent),
        actionTitle = stringResource(R.string.done),
        onAction = onClose,
    )
}
