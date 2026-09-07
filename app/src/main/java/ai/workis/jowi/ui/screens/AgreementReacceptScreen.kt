package ai.workis.jowi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.absoluteWorkisUrl
import ai.workis.jowi.data.applyErrorMessage
import ai.workis.jowi.ui.components.AgreementLoader
import ai.workis.jowi.ui.components.AgreementPaper
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * §10.2 re-acceptance: a newer agreement version asks the seat (partner,
 * carrier or expert) to accept again. The same paper as the wizard and the
 * expert form, the consent line, and Kabul et as the one tinted action.
 * "Daha sonra" dismisses; Hesap keeps a reminder row until accepted.
 */
class ReacceptViewModel : ViewModel() {
    val paper = AgreementLoader(viewModelScope)
    var signerName by mutableStateOf("")
    var agreed by mutableStateOf(false)
    var submitting by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    fun load(key: String?) { key?.let { paper.loadKey(it) } }

    fun accept() {
        if (submitting) return
        submitting = true
        note = null
        val lang = Graph.language.value
        viewModelScope.launch {
            when (val r = Graph.auth.acceptAgreement(signerName.trim())) {
                is ApiResult.Ok -> if (r.value.success) done = true
                else note = r.value.error?.let { code ->
                    if (code == "nothing_pending") (if (lang == "en") "Nothing is pending." else "Bekleyen bir sözleşme yok.")
                    else applyErrorMessage(code, lang)
                } ?: r.value.message
                is ApiResult.Err -> note = r.message
            }
            submitting = false
        }
    }
}

@Composable
fun AgreementReacceptScreen(onClose: () -> Unit, vm: ReacceptViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    val pending by Graph.auth.agreementPending.collectAsState()
    val meta = pending?.agreement
    val label = (if (lang == "en") meta?.labelEn else meta?.labelTr) ?: meta?.title ?: stringResource(R.string.reaccept_title)
    val key = meta?.key ?: pending?.key
    androidx.compose.runtime.LaunchedEffect(key) { vm.load(key) }
    BackHandler(onBack = onClose)

    val valid = vm.agreed && vm.signerName.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // header: Later/Done left, Accept right — the only tinted control
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            TextButton(onClick = onClose) {
                Text(stringResource(if (vm.done) R.string.done else R.string.reaccept_later), color = colors.accentText, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            if (!vm.done) {
                Button(
                    onClick = { vm.accept() },
                    enabled = valid && !vm.submitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Kiremit400, contentColor = OnKiremitFill,
                        disabledContainerColor = colors.surface, disabledContentColor = colors.faint,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    if (vm.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                    else Text(stringResource(R.string.reaccept_accept), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(label, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            if (vm.done) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.reaccept_done), fontSize = 15.sp, lineHeight = 21.sp, color = SuccessGreen)
                }
            } else {
                Text(stringResource(R.string.reaccept_intro), fontSize = 15.sp, lineHeight = 21.sp, color = colors.muted)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.apply_step4_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
                Spacer(Modifier.height(12.dp))
                AgreementPaper(loader = vm.paper, signerName = vm.signerName, onSignerChange = { vm.signerName = it })
                Spacer(Modifier.height(14.dp))
                // "[Label](page) — okudum … ([PDF](pdf))" — links open in-app
                val page = meta?.pageUrl?.let { absoluteWorkisUrl(it) }
                val pdf = meta?.pdfUrl?.let { absoluteWorkisUrl(it) }
                val md = buildString {
                    append(if (page.isNullOrEmpty()) label else "[$label]($page)")
                    append(stringResource(R.string.reaccept_agree_suffix))
                    if (!pdf.isNullOrEmpty()) append(" ([PDF]($pdf))")
                }
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = vm.agreed, onCheckedChange = { vm.agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = Kiremit500, checkmarkColor = OnKiremitFill),
                    )
                    MarkdownText(md, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
                }
                vm.note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = colors.danger, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
