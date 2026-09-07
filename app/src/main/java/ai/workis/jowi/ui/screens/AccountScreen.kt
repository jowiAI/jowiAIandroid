package ai.workis.jowi.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(onReaccept: () -> Unit = {}) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    val lang by Graph.language.collectAsState()
    val appearance by Graph.appearance.collectAsState()
    val user by Graph.auth.user.collectAsState()
    val pending by Graph.auth.agreementPending.collectAsState()

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
            InfoRow(stringResource(R.string.email_label), Graph.auth.email() ?: "—")
            RowDivider()
            InfoRow(
                stringResource(R.string.account_role),
                Graph.auth.roleDisplay() ?: Graph.auth.roleCode()?.toString() ?: "—",
            )
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
                            Graph.setLanguage(code)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(code)
                            )
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

        // §10.2 reminder — stays until the seat re-accepts
        if (pending?.pending == true) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BeigeBg, RoundedCornerShape(20.dp))
                    .clickable(onClick = onReaccept)
                    .padding(16.dp),
            ) {
                Icon(WorkisIcons.DocPlus, contentDescription = null, tint = Beige, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.reaccept_pending_row),
                    color = Beige, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Beige)
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

@Composable
private fun segmentedColors() = SegmentedButtonDefaults.colors(
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
