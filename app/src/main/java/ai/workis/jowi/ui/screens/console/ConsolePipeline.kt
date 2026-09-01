package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.ApplicationRow
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

@Composable
fun ConsolePipeline(vm: ConsoleViewModel) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.apps == null) vm.loadApps() }
    val apps = vm.apps

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (apps != null) {
            section(R.string.pipeline_leads, apps.leads.orEmpty()) { row ->
                LeadActions(vm, row)
            }
            section(R.string.pipeline_pending, apps.pending.orEmpty()) { row ->
                PendingActions(vm, row)
            }
            section(R.string.pipeline_approved, apps.approved.orEmpty()) { null }
        }
        item {
            vm.error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            if (apps != null &&
                apps.leads.orEmpty().isEmpty() &&
                apps.pending.orEmpty().isEmpty() &&
                apps.approved.orEmpty().isEmpty() &&
                vm.error == null
            ) {
                Text(stringResource(R.string.pipeline_empty), color = colors.muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    rows: List<ApplicationRow>,
    actions: @Composable (ApplicationRow) -> Unit?,
) {
    if (rows.isEmpty()) return
    item {
        Text(
            stringResource(titleRes).uppercase(),
            fontFamily = WorkisMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = WorkisTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(rows) { row -> ApplicationCard(row) { actions(row) } }
}

@Composable
private fun ApplicationCard(row: ApplicationRow, actions: @Composable () -> Unit?) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.company ?: row.shortName ?: "—",
                    color = colors.ink,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    listOfNotNull(row.city, row.role, row.email).take(3).forEach {
                        Text("$it  ", color = colors.muted, fontSize = 12.sp)
                    }
                }
            }
            row.vkn?.let {
                Text(it, fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            row.confidence?.let {
                Text(
                    "%${(it * 100).toInt()}",
                    fontFamily = WorkisMono,
                    fontSize = 11.sp,
                    color = if (it >= 0.8) SuccessGreen else colors.muted,
                )
                Spacer(Modifier.padding(4.dp))
            }
            if (row.hasTaxFile == true) {
                Text("📄", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

@Composable
private fun ActionButton(label: Int, busy: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    TextButton(onClick = onClick, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kiremit500)
        } else {
            Text(
                stringResource(label),
                color = if (danger) colors.danger else colors.accentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LeadActions(vm: ConsoleViewModel, row: ApplicationRow) {
    val id = row.applicationId ?: return
    Row {
        ActionButton(R.string.create_partner, vm.busyId == id) { vm.createPartner(id) }
        if (row.partnerId == null) {
            ActionButton(R.string.close_lead, vm.busyId == id, danger = true) { vm.closeLead(id) }
        }
    }
}

@Composable
private fun PendingActions(vm: ConsoleViewModel, row: ApplicationRow) {
    Row {
        row.partnerId?.let { pid ->
            ActionButton(R.string.approve, vm.busyId == pid) { vm.approvePartner(pid) }
        }
        row.applicationId?.let { id ->
            ActionButton(R.string.resend_invite, vm.busyId == id) { vm.invite(id) }
        }
    }
}
