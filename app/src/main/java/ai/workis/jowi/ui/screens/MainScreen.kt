package ai.workis.jowi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisTheme

private enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    Panel(R.string.console_tab, ai.workis.jowi.ui.theme.WorkisIcons.Grid),
    Cases(R.string.tab_cases, Icons.AutoMirrored.Filled.List),
    Account(R.string.tab_account, Icons.Filled.AccountCircle),
    Jowi(R.string.jowi_tab, ai.workis.jowi.ui.theme.WorkisIcons.Sparkle),
}

@Composable
fun MainScreen() {
    val colors = WorkisTheme.colors
    // Panel tab only for role 3 or 4 (exact match)
    val tabs = remember {
        if (Graph.auth.isCoordinator()) MainTab.entries.toList()
        else MainTab.entries.filter { it != MainTab.Panel }
    }
    // coordinators land on Panel (first tab, like iOS); everyone else on Cases
    var selected by rememberSaveable {
        mutableStateOf(if (Graph.auth.isCoordinator()) MainTab.Panel.name else MainTab.Cases.name)
    }
    val current = MainTab.valueOf(selected)

    Scaffold(
        containerColor = colors.canvas,
        bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { selected = tab.name },
                        icon = { Icon(tab.icon, contentDescription = null) },
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
                MainTab.Panel -> ai.workis.jowi.ui.screens.console.ConsoleRoot()
                MainTab.Cases -> CasesScreen()
                MainTab.Account -> AccountScreen()
                MainTab.Jowi -> JowiScreen()
            }
        }
    }
}

