package ai.workis.jowi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.R
import ai.workis.jowi.ui.components.AgreementLoader
import ai.workis.jowi.ui.components.AgreementPaper
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme

class AgreementViewModel : ViewModel() {
    val paper = AgreementLoader(viewModelScope)
}

/**
 * Hesap → Sözleşmem (2026-09-16): the seat's agreement as the white paper,
 * read-only — the Platform's signature and the seat's own acceptance in the
 * two cards (the shared client sends the token), plus the wet-signature PDF.
 * A pending version never lands here: Hesap routes that to the re-accept
 * screen instead.
 */
@Composable
fun AgreementScreen(key: String, vm: AgreementViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    LaunchedEffect(key) { vm.paper.loadKey(key) }
    var showPdf by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(colors.canvas).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AgreementPaper(loader = vm.paper, signerName = "", onSignerChange = {}, readOnly = true)
        vm.paper.meta?.let {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showPdf = true }.padding(8.dp),
            ) {
                Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.signed_pdf), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
    if (showPdf) {
        PdfViewerDialog(stringResource(R.string.my_agreement), url = vm.paper.meta?.pdfUrl, fileName = "sozlesme.pdf") { showPdf = false }
    }
}
