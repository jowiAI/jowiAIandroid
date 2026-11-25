package ai.workis.jowi.ui.screens.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ai.workis.jowi.R

/** Ledger 128 placeholder — the page's own agent replaces this whole file. */
@Composable
fun ConsoleSourcingScreen(onBack: () -> Unit) {
    ConsoleSub(stringResource(R.string.console_sourcing_title), onBack) {}
}
