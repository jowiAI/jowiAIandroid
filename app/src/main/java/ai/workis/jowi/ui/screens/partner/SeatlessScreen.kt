package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.R
import ai.workis.jowi.data.InvitationAcceptBody
import ai.workis.jowi.data.MyInvitation
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.data.SeatApplication
import ai.workis.jowi.data.TeamOutcome
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.data.teamCall
import ai.workis.jowi.ui.components.OutcomeView
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.PenBlue
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * A signed-in user without a partner seat (the server's 403 `seat:"none"`):
 * no application → the apply door; an application → its stage with the
 * company name. The web's /basvuru/ redirect, as a tab.
 *
 * An INVITATION outranks the apply door. A firm has already written this
 * address down as one of its own, so accepting is THE action here and "Başvur"
 * steps down to a secondary control — applying afresh would open a second firm
 * beside the one already waiting. While the team switch is off the list simply
 * comes back empty and this screen is exactly what it was.
 */
@Composable
fun SeatlessScreen(onApply: () -> Unit) {
    // a tab root: no seller screen is open (2026-09-17)
    LaunchedEffect(Unit) { ScreenContext.clear() }
    val colors = WorkisTheme.colors
    val seat by Graph.partner.seat.collectAsState()
    var invitations by remember { mutableStateOf<List<MyInvitation>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        // `off` is the switch: an empty list, and the screen is unchanged
        val r = teamCall(Graph.language.value) { Graph.api.myInvitations() }
        invitations = if (r is TeamOutcome.Ok) r.value.invitations.orEmpty() else emptyList()
    }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        val s = seat
        when {
            s !is PartnerSeatState.None ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kiremit500) }
            invitations.isNotEmpty() ->
                InvitationsView(invitations, onApply) { reload++; }
            s.application == null -> OutcomeView(
                title = stringResource(R.string.seatless_title),
                message = stringResource(R.string.seatless_none),
                footnote = stringResource(R.string.seatless_sub),
                icon = Icons.Filled.AccountCircle,
                iconColor = colors.accentText,
                actionTitle = stringResource(R.string.seatless_apply),
                onAction = onApply,
            )
            else -> StageView(s.application, onApply)
        }
    }
}

/** The firm is waiting for this person: its card leads, the apply door follows. */
@Composable
private fun InvitationsView(invitations: List<MyInvitation>, onApply: () -> Unit, onAccepted: () -> Unit) {
    val colors = WorkisTheme.colors
    var accepting by remember { mutableStateOf<MyInvitation?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Icon(WorkisIcons.Building, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(30.dp))
        Text(
            stringResource(R.string.team_invite_screen_title),
            fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
        )
        NoteBand(note)
        invitations.forEach { inv ->
            InvitationCard(inv) { accepting = inv }
        }
        // still a way through, just no longer the headline
        TextButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.seatless_apply), color = colors.muted, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(30.dp))
    }

    accepting?.let { inv ->
        AcceptSheet(
            inv,
            onClose = { accepting = null },
            onRefused = { note = it },
            onAccepted = { accepting = null; onAccepted() },
        )
    }
}

@Composable
private fun InvitationCard(inv: MyInvitation, onAccept: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(inv.company.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        inv.invitedBy?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.team_invite_from) + " " + it,
                fontSize = 13.sp, color = colors.muted,
            )
        }
        inv.scopeLabel?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
        }
        // what this seat would be — the server's sentence, never ours
        inv.scopeMeans?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
        }
        inv.expiresAt?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.team_expires) + " " + shortDate(it),
                fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.team_accept), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
    }
}

/**
 * The yes. What it binds the person to is the server's `acceptConsent`, shown
 * before the name is asked; the name itself IS the signature — the server keeps
 * it as name · date · IP — so it is written in the app's signature hand, the
 * same one the apply wizard's Sign-Here band and the order contract use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcceptSheet(
    inv: MyInvitation,
    onClose: () -> Unit,
    onRefused: (String?) -> Unit,
    onAccepted: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    var signerName by remember { mutableStateOf(Graph.auth.displayName().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var sheetNote by remember { mutableStateOf<String?>(null) }
    // client-side we only ask that it be a name at all; `signer` 400 is the
    // server's word on what counts
    val enough = signerName.trim().length >= 3

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(inv.company.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            inv.acceptConsent?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
            }
            sheetNote?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.team_accept_name), color = colors.faint) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // the name as it will be recorded: the app's own hand
            if (signerName.isNotBlank()) {
                Text(
                    signerName,
                    fontFamily = FontFamily.Cursive,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = PenBlue,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                stringResource(R.string.team_accept_name_footer),
                fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint,
            )
            Button(
                onClick = {
                    if (busy || !enough) return@Button
                    busy = true; sheetNote = null
                    scope.launch {
                        val id = inv.id.orEmpty()
                        when (val r = teamCall(Graph.language.value) {
                            Graph.api.acceptInvitation(id, InvitationAcceptBody(signerName.trim()))
                        }) {
                            is TeamOutcome.Ok ->
                                if (r.value.success) {
                                    // the seat exists now: let the app find it
                                    Graph.partner.refresh()
                                    onAccepted()
                                } else sheetNote = r.value.error
                            // the switch went off under us: say nothing, just leave
                            is TeamOutcome.Off -> { onRefused(null); onClose() }
                            is TeamOutcome.Refused -> sheetNote = r.error
                        }
                        busy = false
                    }
                },
                enabled = enough && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.team_accept), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
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
