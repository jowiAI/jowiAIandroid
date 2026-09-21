package ai.workis.jowi.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.Team
import ai.workis.jowi.data.TeamInviteBody
import ai.workis.jowi.data.TeamInvitation
import ai.workis.jowi.data.TeamOutcome
import ai.workis.jowi.data.TeamScope
import ai.workis.jowi.data.TeamScopeBody
import ai.workis.jowi.data.TeamSeat
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.data.teamCall
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.screens.partner.ConfirmDialog
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/**
 * Ekip (API_CONTRACT "Team seats"). The firm's admin seat invites colleagues,
 * who then act in the FIRM's name — the contracting party is the legal entity,
 * not the first seat.
 *
 * Every control on this screen is drawn from a flag the server sent, and every
 * sentence on it is a sentence the server wrote. The screen derives neither: a
 * view seat arrives with its `can…` already false, so there is no second code
 * path here to keep in step with the middleware.
 *
 * It ships dark. While `enabled` is false the door into this screen does not
 * exist, and a 404 `off` on any call is the switch rather than a fault — the
 * screen simply leaves.
 */
class TeamViewModel : ViewModel() {
    var team by mutableStateOf<Team?>(null)
        private set
    /** The switch answered `off`: this screen has nothing to be. */
    var off by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    private val lang get() = Graph.language.value

    val seats: List<TeamSeat> get() = team?.seats.orEmpty()
    val invitations: List<TeamInvitation> get() = team?.invitations.orEmpty()
    val scopes: List<TeamScope> get() = team?.scopes.orEmpty()
    /** Absent means closed — the app never grants itself a door. */
    val canInvite get() = team?.canInvite == true
    val canManage get() = team?.canManage == true

    fun load() {
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        when (val r = teamCall(lang) { Graph.api.team() }) {
            is TeamOutcome.Ok -> {
                // the switch can go off under us as well as before us
                if (r.value.enabled == false) off = true else team = r.value
            }
            is TeamOutcome.Off -> off = true
            // a refusal on the READ is the server's sentence, or nothing at all
            is TeamOutcome.Refused -> if (team == null) note = r.error
        }
    }

    /**
     * One place for the two rules an action obeys: `off` closes the screen in
     * silence, and any other refusal speaks in the server's own sentence — we
     * never stand one of ours in its place.
     */
    private fun act(block: suspend () -> TeamOutcome<Team>) {
        if (busy) return
        busy = true; note = null
        viewModelScope.launch {
            when (val r = block()) {
                is TeamOutcome.Ok -> if (r.value.enabled == false) off = true else team = r.value
                is TeamOutcome.Off -> off = true
                is TeamOutcome.Refused -> { note = r.error; fetch() }
            }
            busy = false
        }
    }

    fun invite(email: String, scope: String) = act {
        teamCall(lang) { Graph.api.teamInvite(TeamInviteBody(email.trim().lowercase(), scope)) }
    }

    fun cancelInvitation(id: String) = act {
        teamCall(lang) { Graph.api.teamInvitationCancel(id) }
    }

    fun removeSeat(userId: String) = act {
        teamCall(lang) { Graph.api.teamSeatRemove(userId) }
    }

    fun changeScope(userId: String, scope: String) = act {
        teamCall(lang) { Graph.api.teamSeatScope(userId, TeamScopeBody(scope)) }
    }
}

@Composable
fun TeamScreen(onBack: () -> Unit) {
    val vm: TeamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var removeTarget by remember { mutableStateOf<TeamSeat?>(null) }
    var cancelTarget by remember { mutableStateOf<TeamInvitation?>(null) }
    var scopeTarget by remember { mutableStateOf<TeamSeat?>(null) }
    LaunchedEffect(Unit) { vm.load() }
    // the switch went off: there is nothing here, and that is not an error
    LaunchedEffect(vm.off) { if (vm.off) onBack() }

    val t = vm.team
    ConsoleSub(stringResource(R.string.team_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteBand(vm.note)
            if (t == null) {
                Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
                }
                return@ConsoleSub
            }

            // What THIS seat is, said by the server. The condition is the FLAG:
            // a scope comparison would break the day a third scope lands and
            // the sentence would simply go unsaid (entry 17).
            if (t.canTrade == false) {
                t.viewSeatNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Row(
                        Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(WorkisIcons.PersonKey, contentDescription = null, tint = Beige, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(note, fontSize = 13.sp, lineHeight = 19.sp, color = Beige)
                    }
                }
            }

            // ---- Koltuklar
            Spacer(Modifier.height(4.dp))
            TeamEyebrow(
                stringResource(R.string.team_seats_section),
                trailing = t.seatLimit?.let { limit ->
                    stringResource(R.string.team_seats_used) + " ${t.seatsUsed ?: vm.seats.size}/$limit"
                },
            )
            TeamCard {
                vm.seats.forEachIndexed { i, seat ->
                    if (i > 0) TeamDivider()
                    SeatRow(
                        seat,
                        canManage = vm.canManage,
                        busy = vm.busy,
                        onRemove = { removeTarget = seat },
                        onScope = { scopeTarget = seat },
                    )
                }
            }

            // ---- Davetler
            Spacer(Modifier.height(4.dp))
            TeamEyebrow(stringResource(R.string.team_invites_section))
            TeamCard {
                if (vm.invitations.isEmpty()) {
                    Text(
                        stringResource(R.string.team_invites_empty),
                        fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    vm.invitations.forEachIndexed { i, inv ->
                        if (i > 0) TeamDivider()
                        InvitationRow(inv, canManage = vm.canManage, busy = vm.busy) { cancelTarget = inv }
                    }
                }
            }

            // ---- Davet et
            if (vm.canInvite) InviteSection(vm, t)

            // ---- the handover footer: where an admin change goes
            t.adminTransfer?.takeIf { it.isNotBlank() }?.let { sentence ->
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TeamFootnote(sentence)
                    t.supportEmail?.takeIf { it.isNotBlank() }?.let { mail ->
                        Text(
                            mail,
                            fontFamily = WorkisMono, fontSize = 12.sp, color = colors.accentText,
                            modifier = Modifier
                                // a mail address wants the mail app, not the in-app
                                // browser every workis.ai link goes through
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, "mailto:$mail".toUri()),
                                        )
                                    }
                                }
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    removeTarget?.let { seat ->
        ConfirmDialog(
            stringResource(R.string.team_remove_title),
            stringResource(R.string.team_remove_body),
            destructive = true,
            onDismiss = { removeTarget = null },
        ) { seat.userId?.let { vm.removeSeat(it) } }
    }
    cancelTarget?.let { inv ->
        ConfirmDialog(
            stringResource(R.string.team_cancel_invite_title),
            stringResource(R.string.team_cancel_invite_body),
            destructive = true,
            onDismiss = { cancelTarget = null },
        ) { inv.id?.let { vm.cancelInvitation(it) } }
    }
    scopeTarget?.let { seat ->
        ScopeSheet(seat, vm.scopes, onClose = { scopeTarget = null }) { value ->
            scopeTarget = null
            seat.userId?.let { vm.changeScope(it, value) }
        }
    }
}

/** name (else the address) · the address · the scope's label. */
@Composable
private fun SeatRow(
    seat: TeamSeat,
    canManage: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
    onScope: () -> Unit,
) {
    val colors = WorkisTheme.colors
    // never the admin seat, never one's own — the server refuses both, and the
    // screen does not offer what would be refused
    val removable = canManage && seat.admin != true && seat.me != true
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                seat.name?.takeIf { it.isNotBlank() } ?: seat.email.orEmpty(),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (seat.admin == true) { Spacer(Modifier.width(8.dp)); SeatMark(stringResource(R.string.team_admin_mark)) }
            if (seat.me == true) { Spacer(Modifier.width(6.dp)); SeatMark(stringResource(R.string.team_you_mark)) }
        }
        seat.email?.takeIf { it.isNotBlank() && it != seat.name }?.let {
            Text(it, fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            seat.scopeLabel?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = colors.muted, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            // the ROW's own permission, never the caller's and never a comparison
            if (seat.canChangeScope == true) {
                Text(
                    stringResource(R.string.team_scope),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                    modifier = Modifier.clickable(enabled = !busy, onClick = onScope),
                )
            }
            if (removable) {
                Text(
                    stringResource(R.string.team_remove),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.danger,
                    modifier = Modifier.clickable(enabled = !busy, onClick = onRemove),
                )
            }
        }
    }
}

@Composable
private fun InvitationRow(inv: TeamInvitation, canManage: Boolean, busy: Boolean, onCancel: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(inv.email.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
        Text(
            listOfNotNull(
                inv.scopeLabel?.takeIf { it.isNotBlank() },
                inv.expiresAt?.let { stringResource(R.string.team_expires) + " " + shortDate(it) }?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            fontFamily = WorkisMono, fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint,
        )
        // the row's own words about what this seat would be
        inv.scopeMeans?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted)
        }
        if (canManage) {
            Text(
                stringResource(R.string.team_cancel_invite),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.danger,
                modifier = Modifier.clickable(enabled = !busy, onClick = onCancel).padding(top = 2.dp),
            )
        }
    }
}

/**
 * The one tinted action on this screen. The binding sentence above it is the
 * server's `inviteConsent`: if it did not arrive, the line is simply not there
 * — the app has no sentence of its own to stand in for a legal one.
 */
@Composable
private fun InviteSection(vm: TeamViewModel, t: Team) {
    val colors = WorkisTheme.colors
    var email by remember { mutableStateOf("") }
    var scope by remember(vm.scopes) { mutableStateOf(vm.scopes.firstOrNull()?.value.orEmpty()) }

    Spacer(Modifier.height(4.dp))
    TeamEyebrow(stringResource(R.string.team_invite_section))
    TeamCard {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.email_placeholder), color = colors.faint) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        // each option carries the server's sentence about what it means
        vm.scopes.forEach { option ->
            val value = option.value.orEmpty()
            ScopeOption(option, selected = value == scope) { scope = value }
        }
    }
    t.inviteConsent?.takeIf { it.isNotBlank() }?.let { TeamFootnote(it) }
    Button(
        onClick = { vm.invite(email, scope); email = "" },
        enabled = !vm.busy && email.isNotBlank() && scope.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.team_invite_send), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** A scope and what choosing it means — both the server's words. */
@Composable
private fun ScopeOption(option: TeamScope, selected: Boolean, onPick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) WorkisIcons.Check else WorkisIcons.Dot,
                contentDescription = null,
                tint = if (selected) colors.accentText else colors.faint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                option.label.orEmpty(),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = colors.ink,
            )
        }
        option.means?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted, modifier = Modifier.padding(start = 23.dp))
        }
    }
}

/** Changing a seat's scope: the picker is the server's rows, nothing derived. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSheet(seat: TeamSeat, scopes: List<TeamScope>, onClose: () -> Unit, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                seat.name?.takeIf { it.isNotBlank() } ?: seat.email.orEmpty(),
                fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            scopes.forEach { option ->
                ScopeOption(option, selected = option.value == seat.scope) {
                    option.value?.let(onPick)
                }
            }
        }
    }
}

// ---------- small parts, in the Hesap grammar

@Composable
private fun SeatMark(text: String) {
    Text(
        text.uppercase(),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
        color = WorkisTheme.colors.accentText,
        modifier = Modifier
            .background(WorkisTheme.colors.ink.copy(alpha = 0.06f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun TeamCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun TeamEyebrow(text: String, trailing: String? = null) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp,
            color = colors.faint, modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint) }
    }
}

@Composable
private fun TeamFootnote(text: String) {
    Text(
        text,
        fontSize = 12.sp, lineHeight = 17.sp, color = WorkisTheme.colors.faint,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun TeamDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WorkisTheme.colors.border))
}
