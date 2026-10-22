package ai.workis.jowi.ui.screens.decisions

import androidx.compose.runtime.Composable
import ai.workis.jowi.data.JowiWorkspace
import ai.workis.jowi.ui.theme.Text

/**
 * Kararlar — the Jowi hospitality surface (ledgers 20–33). STUB: the ledger, the
 * composer and the view model land with the slice.
 */
@Composable
fun DecisionsScreen(workspace: JowiWorkspace?) { Text(workspace?.title.orEmpty()) }
