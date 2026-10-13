package ai.workis.jowi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import ai.workis.jowi.ui.screens.expert.ExpertAccountSections
import ai.workis.jowi.ui.screens.partner.CompanyMode
import ai.workis.jowi.ui.screens.partner.CompanySections
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Kiremit500
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.core.content.ContextCompat
import ai.workis.jowi.data.PushService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.WorkisIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import ai.workis.jowi.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.TeamOutcome
import ai.workis.jowi.data.teamCall
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/** Hesap's own doors: Firma bilgileri · Ayarlar (partner) / Sözleşmem (other seats). */
private sealed interface AccountDest {
    data object Home : AccountDest
    data object Company : AccountDest
    data object Settings : AccountDest
    data object Security : AccountDest
    data object Team : AccountDest
    data class Agreement(val key: String) : AccountDest
}

@Composable
fun AccountScreen(onReaccept: () -> Unit = {}, onDeparture: () -> Unit = {}) {
    var dest by remember { mutableStateOf<AccountDest>(AccountDest.Home) }
    BackHandler(enabled = dest != AccountDest.Home) { dest = AccountDest.Home }
    when (val d = dest) {
        AccountDest.Home -> AccountHome(onReaccept, onDeparture) { dest = it }
        AccountDest.Company -> ConsoleSub(stringResource(R.string.company_title), { dest = AccountDest.Home }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp)); CompanySections(CompanyMode.Company); Spacer(Modifier.height(30.dp))
            }
        }
        AccountDest.Settings -> ConsoleSub(stringResource(R.string.settings_title), { dest = AccountDest.Home }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp)); CompanySections(CompanyMode.Settings); Spacer(Modifier.height(30.dp))
            }
        }
        AccountDest.Security -> SecurityScreen { dest = AccountDest.Home }
        AccountDest.Team -> TeamScreen { dest = AccountDest.Home }
        is AccountDest.Agreement -> ConsoleSub(stringResource(R.string.my_agreement), { dest = AccountDest.Home }) {
            AgreementScreen(d.key)
        }
    }
}

@Composable
private fun AccountHome(onReaccept: () -> Unit, onDeparture: () -> Unit, onOpen: (AccountDest) -> Unit) {
    // a tab root: no seller screen is open (2026-09-17)
    LaunchedEffect(Unit) { ScreenContext.clear() }
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    val lang by Graph.language.collectAsState()
    val appearance by Graph.appearance.collectAsState()
    val user by Graph.auth.user.collectAsState()
    val pending by Graph.auth.agreementPending.collectAsState()

    // Ekip ships dark: while the switch is off the row does not exist, and a
    // 404 `off` is the switch rather than a fault — nothing is said either way.
    var teamEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = teamCall(Graph.language.value) { Graph.api.team() }
        teamEnabled = r is TeamOutcome.Ok && r.value.enabled == true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.tab_account),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Spacer(Modifier.height(16.dp))

        SettingsCard {
            InfoRow(stringResource(R.string.account_email), Graph.auth.email() ?: "—")
            RowDivider()
            // the seat name in the app's language — the server's role_display is
            // English-only and stops at role 3 (a lead read "Region Lead" under Türkçe)
            val roleLabel = when (Graph.auth.roleCode()) {
                1 -> stringResource(R.string.account_role_partner)
                2 -> stringResource(R.string.account_role_guest)
                3 -> stringResource(R.string.account_role_coordinator)
                4 -> stringResource(R.string.account_role_lead)
                5 -> stringResource(R.string.account_role_expert)
                else -> Graph.auth.roleDisplay() ?: Graph.auth.roleCode()?.toString() ?: "—"
            }
            InfoRow(stringResource(R.string.account_role), roleLabel)
        }

        Spacer(Modifier.height(16.dp))

        SettingsCard {
            Text(
                stringResource(R.string.account_language),
                color = colors.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("tr" to "Türkçe", "en" to "English").forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = lang == code,
                        onClick = {
                            Graph.setLanguage(code) // the per-app locale follows it
                            // header alone is insufficient once DB pref is set —
                            // in-app switch must persist preferredLanguage server-side
                            scope.launch { Graph.auth.setPreferredLanguage(code) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        colors = segmentedColors(),
                    ) { Text(label, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.appearance),
                color = colors.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf(
                "system" to stringResource(R.string.appearance_system),
                "light" to stringResource(R.string.appearance_light),
                "dark" to stringResource(R.string.appearance_dark),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = appearance == value,
                        onClick = { Graph.setAppearance(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        colors = segmentedColors(),
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
        }

        // Expert seat: the web Profil page as sections (slice 2)
        if (Graph.auth.isExpert()) {
            Spacer(Modifier.height(16.dp))
            ExpertAccountSections(onDeparture = onDeparture)
        }

        // Partner seat: the web "Company info" page and Ayarlar on their own screens
        // (2026-09-15: Hesap is daily, Firma is rare); other seats get "Sözleşmem"
        // — the accepted paper with the signature cards (2026-09-16)
        val seatState by Graph.partner.seat.collectAsState()
        val company = (seatState as? ai.workis.jowi.data.PartnerSeatState.Seat)?.company
        val agreementKey = pending?.key?.takeIf { it.isNotEmpty() && pending?.pending != true }
        // One group for the doors this seat has. Güvenlik belongs with them
        // rather than in a card of its own — a second card for a single row
        // broke the rhythm. Below API 28 there are no platform passkeys, so
        // that door is not drawn at all; those devices stay on the e-mail code.
        val security = Graph.passkeys.available
        if (company != null || agreementKey != null || security || teamEnabled) {
            Spacer(Modifier.height(16.dp))
            SettingsCard {
                var drawn = false
                if (company != null) {
                    DoorRow(WorkisIcons.Building, stringResource(R.string.company_title), company.shortLabel) { onOpen(AccountDest.Company) }
                    RowDivider()
                    DoorRow(Icons.Filled.Settings, stringResource(R.string.settings_title), null) { onOpen(AccountDest.Settings) }
                    drawn = true
                } else if (agreementKey != null) {
                    DoorRow(WorkisIcons.DocText, stringResource(R.string.my_agreement), null) { onOpen(AccountDest.Agreement(agreementKey)) }
                    drawn = true
                }
                // the firm's colleagues live with the firm's own doors
                if (teamEnabled) {
                    if (drawn) RowDivider()
                    DoorRow(WorkisIcons.PersonKey, stringResource(R.string.team_title), null) { onOpen(AccountDest.Team) }
                    drawn = true
                }
                if (security) {
                    if (drawn) RowDivider()
                    DoorRow(WorkisIcons.PersonKey, stringResource(R.string.security_title), null) { onOpen(AccountDest.Security) }
                }
            }
        }

        // Bildirimler (push, 2026-10-03): a firm's trade moving — `push/register/` answers
        // only a partner seat, so only a firm gets the switch
        if (company != null) {
            Spacer(Modifier.height(16.dp))
            NotificationsCard()
        }

        // §10.2 reminder — stays until the seat re-accepts. A colleague seat
        // cannot accept: the paper's only button answers 403 `admin_required`,
        // so it gets a band it cannot tap, carrying the SERVER's sentence. With
        // no sentence there is no row — we have none of our own to put there.
        val reaccept = pending
        if (reaccept?.pending == true) {
            val mayAccept = reaccept.canAccept != false
            val note = reaccept.acceptNote?.takeIf { it.isNotBlank() }
            if (mayAccept || note != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BeigeBg, RoundedCornerShape(20.dp))
                        .then(if (mayAccept) Modifier.clickable(onClick = onReaccept) else Modifier)
                        .padding(16.dp),
                ) {
                    Icon(WorkisIcons.DocPlus, contentDescription = null, tint = Beige, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (mayAccept) stringResource(R.string.reaccept_pending_row) else note.orEmpty(),
                        color = Beige, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (mayAccept) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Beige)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = { scope.launch { Graph.auth.logout() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.sign_out),
                color = colors.danger,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

/**
 * The Hesap "Bildirimler" switch — off by default. Turning it on asks POST_NOTIFICATIONS
 * (Android 13+) right here and nowhere else; a refusal leaves it off and says so. The footer
 * names why nothing will arrive: the permission, no push carrier in this build, or the
 * server's `ready: false`.
 */
@Composable
private fun NotificationsCard() {
    val colors = WorkisTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val on by PushService.enabled.collectAsState()
    val ready by PushService.ready.collectAsState()
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PushService.setEnabled(true) else denied = true
    }
    SettingsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notifications_row), color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) PushService.setEnabled(false)
                    else {
                        denied = false
                        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsAsk) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else PushService.setEnabled(true)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.blue),
            )
        }
    }
    val footer = when {
        denied -> R.string.notifications_denied
        on && !PushService.hasCarrier -> R.string.notifications_build_off
        on && ready == 0 -> R.string.notifications_server_off
        else -> null
    }
    footer?.let {
        Text(stringResource(it), color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    }
}

@Composable
private fun segmentedColors() =SegmentedButtonDefaults.colors(
    activeContainerColor = WorkisTheme.colors.canvas,
    activeContentColor = WorkisTheme.colors.ink,
    inactiveContainerColor = WorkisTheme.colors.surface,
    inactiveContentColor = WorkisTheme.colors.muted,
)

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun DoorRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String?, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        value?.let { Text(it, color = colors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = colors.muted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = colors.ink, fontSize = 14.sp)
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(color = WorkisTheme.colors.border, thickness = 1.dp)
}
