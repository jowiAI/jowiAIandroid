package ai.workis.jowi.ui.screens.partner

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ContractAcceptBody
import ai.workis.jowi.data.OrderContract
import ai.workis.jowi.data.absoluteWorkisUrl
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.ui.components.MarkdownDocument
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.SignHereBand
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * The order contract (Django §7): the terms, settled, as one paper. White in
 * both schemes like the agreement paper, `bodyMd` through the block Markdown
 * renderer (the `>` lines are dropped here as well, so both ends agree), the
 * two acceptance cards underneath, and the Sign-Here band on the reader's own
 * card. A 409 means the draft moved: the paper swaps to the fresh text and
 * says so, and nothing was signed.
 */
class OrderContractViewModel : ViewModel() {
    var contract by mutableStateOf<OrderContract?>(null)
        private set
    /** The server's sentence while the terms are not settled yet. */
    var blocked by mutableStateOf<String?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var moved by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var signerName by mutableStateOf("")
    private var orderId = ""
    private val lang get() = Graph.language.value

    fun load(id: String) {
        orderId = id
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.orderContract(id) }
            if (r != null) { contract = r.contract; blocked = r.error; loadError = null } else if (contract == null) loadError = err
        }
    }

    fun accept() {
        val name = signerName.trim()
        if (name.isEmpty() || busy) return
        busy = true; note = null; moved = false
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.orderContractAccept(orderId, ContractAcceptBody(name)) }
            busy = false
            when {
                r?.success == true -> { contract = r.contract ?: contract; signerName = ""; load(orderId) }
                // 409: the terms changed under us — show the fresh text, sign nothing
                r?.contract != null -> { contract = r.contract; moved = true; note = r.error }
                else -> note = r?.error ?: err
            }
        }
    }
}

@Composable
fun OrderContractScreen(orderId: String, onBack: () -> Unit) {
    val vm: OrderContractViewModel = viewModel(key = "contract-$orderId")
    val colors = WorkisTheme.colors
    var showPdf by remember { mutableStateOf(false) }
    LaunchedEffect(orderId) { vm.load(orderId) }
    val c = vm.contract

    ConsoleSub(stringResource(R.string.contract_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
        else if (c?.pdfUrl?.isNotEmpty() == true) {
            Icon(
                WorkisIcons.DocText, contentDescription = stringResource(R.string.contract_pdf), tint = colors.accentText,
                modifier = Modifier.size(20.dp).clickable { showPdf = true },
            )
        }
    }) {
        when {
            c == null && vm.blocked != null -> Text(vm.blocked.orEmpty(), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
            c == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            c == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.moved) {
                    Text(
                        stringResource(R.string.contract_moved_title) + (vm.note?.let { " — $it" } ?: ""),
                        fontSize = 13.sp, lineHeight = 18.sp, color = ai.workis.jowi.ui.theme.Beige,
                        modifier = Modifier.fillMaxWidth().background(ai.workis.jowi.ui.theme.BeigeBg, RoundedCornerShape(12.dp)).padding(12.dp),
                    )
                } else vm.note?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 13.sp, color = colors.danger)
                }
                // the paper reads light in BOTH schemes, so the ink stays on white
                androidx.compose.runtime.CompositionLocalProvider(
                    ai.workis.jowi.ui.theme.LocalWorkisColors provides ai.workis.jowi.ui.theme.WorkisLightColors,
                ) { ContractPaper(c, vm) }
                if (c.canAccept != true) {
                    Text(stringResource(R.string.contract_read_only), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    if (showPdf) {
        PdfViewerDialog(stringResource(R.string.contract_pdf), url = c?.pdfUrl?.let { absoluteWorkisUrl(it) }, fileName = "sozlesme.pdf") { showPdf = false }
    }
}

// Pen-and-paper fixed colours: the paper is white in BOTH schemes.
private val PaperWhite = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF1A1A1F)
private val PenBlue = Color(0xFF1A47B3)

@Composable
private fun ContractPaper(c: OrderContract, vm: OrderContractViewModel) {
    Column(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(12.dp)).background(PaperWhite, RoundedCornerShape(12.dp)),
    ) {
        // letterhead: the mark left, version · status · sha right
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kiremit500)
            Text("workis", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PaperInk)
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(
                    "v${c.version ?: 1}",
                    statusWord(c.status),
                    c.sha256?.take(12)?.takeIf { it.isNotEmpty() }?.let { "#$it" },
                ).joinToString(" · "),
                fontFamily = WorkisMono, fontSize = 10.sp, color = PaperInk.copy(alpha = 0.55f),
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp).background(PaperInk))
        // the body: the agreed Markdown subset, `>` lines dropped on both ends
        MarkdownDocument(
            dropQuotes(c.bodyMd.orEmpty()),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        )
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AcceptanceCard(stringResource(R.string.order_role_buyer), c.buyer, c.version, c.sha256)
            AcceptanceCard(stringResource(R.string.order_role_seller), c.seller, c.version, c.sha256)
            if (c.canAccept == true) {
                SignHereBand(vm.signerName, { vm.signerName = it })
                Button(
                    onClick = { vm.accept() },
                    enabled = !vm.busy && vm.signerName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.contract_accept), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun statusWord(status: String?): String = stringResource(
    when (status) {
        "accepted" -> R.string.contract_accepted
        "superseded" -> R.string.contract_superseded
        else -> R.string.contract_draft
    },
)

@Composable
private fun AcceptanceCard(label: String, a: ai.workis.jowi.data.ContractAcceptance?, version: Int?, sha: String?) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, PaperInk.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 8.sp, letterSpacing = 1.2.sp, color = PaperInk.copy(alpha = 0.55f))
        if (a?.signer?.isNotEmpty() == true) {
            Text(stringResource(R.string.sig_accepted), fontSize = 11.sp, color = PaperInk.copy(alpha = 0.6f))
            Text(
                a.signer,
                fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, color = PenBlue,
            )
            Text(
                listOf(shortDate(a.acceptedAt), a.ip.orEmpty(), sha?.take(12)?.takeIf { it.isNotEmpty() }?.let { "#$it v${version ?: 1}" } ?: "")
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                fontFamily = WorkisMono, fontSize = 9.sp, color = PaperInk.copy(alpha = 0.55f),
            )
        } else {
            Text(stringResource(R.string.sig_awaiting), fontSize = 11.sp, color = PaperInk.copy(alpha = 0.6f))
        }
    }
}

/** `>` lines are dropped server-side; drop them here too so the renderers agree. */
private fun dropQuotes(md: String): String =
    md.lineSequence().filterNot { it.trimStart().startsWith(">") }.joinToString("\n")
