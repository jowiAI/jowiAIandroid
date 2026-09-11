package ai.workis.jowi.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
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
import ai.workis.jowi.ui.screens.expert.ExpertDepartureScreen
import ai.workis.jowi.ui.screens.expert.ExpertRoot
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme

private enum class MainTab(val labelRes: Int) {
    Panel(R.string.console_tab),
    Cases(R.string.tab_cases),
    Account(R.string.tab_account),
    Jowi(R.string.jowi_tab),
}

@Composable
fun MainScreen() {
    val colors = WorkisTheme.colors
    // Panel for role 3/4 (coordinator console) and role 5 (the expert seat's own
    // Panel, graduation cap); the expert holds no buyer seat, so Cases stays hidden.
    val isCoordinator = remember { Graph.auth.isCoordinator() }
    val isExpert = remember { Graph.auth.isExpert() }
    val tabs = remember {
        MainTab.entries.filter { tab ->
            when (tab) {
                MainTab.Panel -> isCoordinator || isExpert
                MainTab.Cases -> !isExpert
                else -> true
            }
        }
    }
    fun icon(tab: MainTab): ImageVector = when (tab) {
        MainTab.Panel -> if (isExpert) WorkisIcons.GraduationCap else WorkisIcons.Grid
        MainTab.Cases -> Icons.AutoMirrored.Filled.List
        MainTab.Account -> Icons.Filled.AccountCircle
        MainTab.Jowi -> WorkisIcons.Sparkle
    }
    var selected by rememberSaveable {
        mutableStateOf(if (isCoordinator || isExpert) MainTab.Panel.name else MainTab.Cases.name)
    }
    val current = MainTab.valueOf(selected)

    // §10.2 — the paper opens once per launch when pending; "Daha sonra" keeps
    // a reminder row in Hesap that reopens it.
    val pending by Graph.auth.agreementPending.collectAsState()
    var showReaccept by rememberSaveable { mutableStateOf(false) }
    var reacceptOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { Graph.auth.refreshAgreementPending() }
    LaunchedEffect(pending?.pending) {
        if (pending?.pending == true && !reacceptOffered) {
            reacceptOffered = true
            showReaccept = true
        }
    }

    if (showReaccept) {
        AgreementReacceptScreen(onClose = { showReaccept = false })
        return
    }
    var showDeparture by rememberSaveable { mutableStateOf(false) }
    if (showDeparture) {
        ExpertDepartureScreen(onClose = { showDeparture = false }, onLeft = {})
        return
    }

    Scaffold(
        containerColor = colors.canvas,
        bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { selected = tab.name },
                        icon = { Icon(icon(tab), contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes), fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Kiremit500,
                            selectedTextColor = colors.ink,
                            unselectedIconColor = colors.muted,
                            unselectedTextColor = colors.muted,
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
                MainTab.Cases -> CasesScreen()
                MainTab.Account -> AccountScreen(onReaccept = { showReaccept = true }, onDeparture = { showDeparture = true })
                MainTab.Jowi -> JowiScreen()
            }
        }
    }
}
