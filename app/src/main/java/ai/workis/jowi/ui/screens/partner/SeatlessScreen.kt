package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.data.SeatApplication
import ai.workis.jowi.ui.components.OutcomeView
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * A signed-in user without a partner seat (the server's 403 `seat:"none"`):
 * no application → the apply door; an application → its stage with the
 * company name. The web's /basvuru/ redirect, as a tab.
 */
@Composable
fun SeatlessScreen(onApply: () -> Unit) {
    val colors = WorkisTheme.colors
    val seat by Graph.partner.seat.collectAsState()
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        val s = seat
        if (s is PartnerSeatState.None) {
            val app = s.application
            if (app == null) {
                OutcomeView(
                    title = stringResource(R.string.seatless_title),
                    message = stringResource(R.string.seatless_none),
                    footnote = stringResource(R.string.seatless_sub),
                    icon = Icons.Filled.AccountCircle,
                    iconColor = colors.accentText,
                    actionTitle = stringResource(R.string.seatless_apply),
                    onAction = onApply,
                )
            } else {
                StageView(app, onApply)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kiremit500) }
        }
    }
}

@Composable
private fun StageView(app: SeatApplication, onApply: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (title, icon, tint) = when (app.stage) {
        "approval" -> Triple(stringResource(R.string.seatless_approval), Icons.Filled.Info, Beige)
        "approved" -> Triple(stringResource(R.string.seatless_approved), Icons.Filled.CheckCircle, SuccessGreen)
        "closed" -> Triple(stringResource(R.string.seatless_closed), Icons.Filled.Close, colors.faint)
        else -> Triple(stringResource(R.string.seatless_review), Icons.Filled.Info, Beige)
    }
    val meta = listOfNotNull(app.company, app.role, app.createdAt?.take(10)).filter { it.isNotEmpty() }.joinToString(" · ")
    // filed with another address: the seat and the sign-in link go there
    val mine = Graph.auth.email().orEmpty().lowercase()
    val other = app.filedWith.orEmpty().lowercase()
    val message = if (other.isNotEmpty() && other != mine) {
        meta + "\n\n" + stringResource(R.string.seatless_other_email) + "\n" + app.filedWith.orEmpty() + "\n" + stringResource(R.string.seatless_switch_hint)
    } else meta
    val closed = app.stage == "closed"
    OutcomeView(
        title = title,
        message = message,
        footnote = if (closed) null else stringResource(R.string.seatless_sub),
        icon = icon,
        iconColor = tint,
        actionTitle = stringResource(if (closed) R.string.seatless_apply else R.string.retry),
        onAction = { if (closed) onApply() else scope.launch { Graph.partner.refresh() } },
    )
}

