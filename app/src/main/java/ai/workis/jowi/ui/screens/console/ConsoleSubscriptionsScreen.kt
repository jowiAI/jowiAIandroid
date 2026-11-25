package ai.workis.jowi.ui.screens.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ai.workis.jowi.R

/** Ledger 128 placeholder — the page's own agent replaces this whole file. */
@Composable
fun ConsoleSubscriptionsScreen(onBack: () -> Unit) {
    ConsoleSub(stringResource(R.string.console_subs_title), onBack) {}
}
