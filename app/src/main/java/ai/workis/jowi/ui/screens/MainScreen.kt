package ai.workis.jowi.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.ui.screens.expert.ExpertDepartureScreen
import ai.workis.jowi.ui.screens.expert.ExpertRoot
import ai.workis.jowi.ui.screens.partner.MarketRoot
import ai.workis.jowi.ui.screens.partner.SeatlessScreen
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme

private enum class MainTab(val labelRes: Int) {
    Panel(R.string.console_tab),
    Market(R.string.market_tab),
    Seatless(R.string.seatless_title),
    Account(R.string.tab_account),
    Jowi(R.string.jowi_tab),
}

/**
 * Tab sets (2026-09-13): role 3/4 and role 5 → Panel · Hesap · Jowi; everyone
 * else follows the `company/` verdict — a firm → Pazar · Hesap · Jowi,
 * `seat:"none"` → Başvuru · Hesap · Jowi (an operator gets no partner tab).
 * The Decora-era Talepler tab is retired from every set.
 */
@Composable
fun MainScreen() {
    val colors = WorkisTheme.colors
    val isCoordinator = remember { Graph.auth.isCoordinator() }
    val isExpert = remember { Graph.auth.isExpert() }
    val seat by Graph.partner.seat.collectAsState()
    LaunchedEffect(Unit) { if (!isCoordinator && !isExpert) Graph.partner.refresh() }

    val partnerTab: MainTab? = when {
        isCoordinator || isExpert -> null
        seat is PartnerSeatState.Seat -> MainTab.Market
        seat is PartnerSeatState.None -> MainTab.Seatless
        else -> null // unknown / operator
    }
    val tabs = buildList {
        if (isCoordinator || isExpert) add(MainTab.Panel)
        partnerTab?.let { add(it) }
        add(MainTab.Account); add(MainTab.Jowi)
    }
    fun icon(tab: MainTab): ImageVector = when (tab) {
        MainTab.Panel -> if (isExpert) WorkisIcons.GraduationCap else WorkisIcons.Grid
        MainTab.Market -> Icons.Filled.ShoppingCart
        MainTab.Seatless -> Icons.Filled.Person
        MainTab.Account -> Icons.Filled.AccountCircle
        MainTab.Jowi -> WorkisIcons.Sparkle
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var userPicked by rememberSaveable { mutableStateOf(false) }
    // until the user taps a tab, land on the first door (it appears once the seat verdict arrives)
    val current = if (userPicked) tabs.firstOrNull { it.name == selected } ?: tabs.first() else tabs.first()

    // §10.2 — the paper opens once per launch when pending; Hesap keeps the reminder row
    val pending by Graph.auth.agreementPending.collectAsState()
    var showReaccept by rememberSaveable { mutableStateOf(false) }
    var reacceptOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { Graph.auth.refreshAgreementPending() }
    LaunchedEffect(pending?.pending) { if (pending?.pending == true && !reacceptOffered) { reacceptOffered = true; showReaccept = true } }
    if (showReaccept) { AgreementReacceptScreen(onClose = { showReaccept = false }); return }
    var showDeparture by rememberSaveable { mutableStateOf(false) }
    if (showDeparture) { ExpertDepartureScreen(onClose = { showDeparture = false }, onLeft = {}); return }
    // the apply wizard as a full-screen cover from the Başvuru tab; company/ re-asked on close
    var showApply by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showApply) { if (!showApply && partnerTab == MainTab.Seatless) Graph.partner.refresh() }
    if (showApply) {
        ApplyScreen(onClose = { showApply = false })
        return
    }

    Scaffold(
        containerColor = colors.canvas,
        bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { selected = tab.name; userPicked = true },
                        icon = { Icon(icon(tab), contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes), fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Kiremit500, selectedTextColor = colors.ink,
                            unselectedIconColor = colors.muted, unselectedTextColor = colors.muted,
                            indicatorColor = colors.canvas,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                MainTab.Panel -> if (isExpert) ExpertRoot() else ai.workis.jowi.ui.screens.console.ConsoleRoot()
                MainTab.Market -> MarketRoot()
                MainTab.Seatless -> SeatlessScreen(onApply = { showApply = true })
                MainTab.Account -> AccountScreen(onReaccept = { showReaccept = true }, onDeparture = { showDeparture = true })
                MainTab.Jowi -> JowiScreen()
            }
        }
    }
}
