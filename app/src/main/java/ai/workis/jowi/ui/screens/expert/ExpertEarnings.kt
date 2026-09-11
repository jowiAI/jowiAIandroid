package ai.workis.jowi.ui.screens.expert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.EarningsContrib
import ai.workis.jowi.data.ExpertEarnings
import ai.workis.jowi.data.Statement
import ai.workis.jowi.data.TextBody
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ExpertEarningsViewModel : ViewModel() {
    var data by mutableStateOf<ExpertEarnings?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    fun load() {
        error = null
        viewModelScope.launch {
            when (val r = safeCall(Graph.language.value) { Graph.api.expertEarnings() }) {
                is ApiResult.Ok -> data = r.value
                is ApiResult.Err -> error = r.message
            }
        }
    }

    /** Agreement 5.5 — the objection, ≥10 chars, inside the window the server computes (`canObject`). */
    fun objectStatement(id: String, text: String, onDone: (Boolean, String?) -> Unit) {
        if (busy) return
        busy = true
        val lang = Graph.language.value
        viewModelScope.launch {
            val (ok, msg) = try {
                Graph.api.expertObjectStatement(id, TextBody(text)).let { it.success to (it.message ?: it.error) }
            } catch (e: HttpException) { false to messageFromBody(e, lang) } catch (e: Exception) { false to statusMessage(null, lang) }
            busy = false
            if (ok) { note = msg; load() }
            onDone(ok, msg)
        }
    }
}

/** Kazanç — the contribution ledger, this month's points, the statements with objection (agreement 5.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertEarningsScreen(vm: ExpertEarningsViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.data == null) vm.load() }
    var objectTarget by remember { mutableStateOf<Statement?>(null) }
    val d = vm.data

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteBand(vm.note)
        when {
            d != null -> {
                Section(stringResource(R.string.ledger_title)) {
                    ChipFlow(ledgerChips(d.contrib))
                    Text(stringResource(R.string.ledger_note), fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint)
                }
                Section(stringResource(R.string.expert_month)) { ChipFlow(monthChips(d.month?.points)) }
                Section(stringResource(R.string.statements_title)) {
                    val statements = d.statements.orEmpty()
                    if (statements.isEmpty()) Text(stringResource(R.string.statements_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
                    statements.forEach { st -> StatementRow(st) { objectTarget = st } }
                    d.withholdingPct?.let {
                        Text(
                            stringResource(R.string.withholding_label) + " %$it · " + stringResource(R.string.payout_note),
                            fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint,
                        )
                    }
                }
            }
            vm.error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.error.orEmpty(), color = colors.danger, fontSize = 14.sp)
                TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry), color = colors.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
            else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) }
        }
        Spacer(Modifier.height(30.dp))
    }

    objectTarget?.let { st ->
        ObjectionSheet(st, vm) { objectTarget = null }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun ChipFlow(chips: List<String>) {
    val colors = WorkisTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { c ->
            Text(
                c.uppercase(),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp,
                color = colors.muted, maxLines = 1,
                modifier = Modifier.background(colors.ink.copy(alpha = 0.06f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ledgerChips(c: EarningsContrib?): List<String> {
    if (c == null) return emptyList()
    return buildList {
        add("${c.units ?: 0} " + stringResource(R.string.c_facts))
        add("${c.uses ?: 0} " + stringResource(R.string.c_uses))
        add("👍 ${c.likes ?: 0}")
        c.novelty?.novel?.let { add("+ $it " + stringResource(R.string.c_novel)) }
        c.novelty?.refinement?.let { add("◐ $it " + stringResource(R.string.c_refinement)) }
        c.novelty?.redundant?.let { add("⌀ $it " + stringResource(R.string.c_redundant)) }
        add("👎 ${c.dislikes ?: 0}")
        add("${c.answered ?: 0} " + stringResource(R.string.c_answered))
        add("${c.reviews ?: 0} " + stringResource(R.string.c_reviews))
    }
}

@Composable
private fun monthChips(p: Map<String, Int>?): List<String> {
    if (p == null || (p["total"] ?: 0) <= 0) return listOf(stringResource(R.string.expert_month_no_points))
    val order = listOf(
        "total" to stringResource(R.string.expert_pts_total),
        "uses" to stringResource(R.string.expert_pts_uses),
        "likes" to "👍",
        "answers" to stringResource(R.string.expert_pts_answers),
        "reviews_corrective" to stringResource(R.string.p_reviews_corrective),
        "reviews_ok" to stringResource(R.string.p_reviews_ok),
        "consults" to stringResource(R.string.expert_pts_consults),
        "redundant_uses" to stringResource(R.string.p_redundant_uses),
    )
    return order.mapNotNull { (key, label) ->
        val n = p[key] ?: return@mapNotNull null
        if (n <= 0 && key != "total") return@mapNotNull null
        if (label == "👍") "👍 $n" else "$n $label"
    }
}

@Composable
private fun StatementRow(st: Statement, onObject: () -> Unit) {
    val colors = WorkisTheme.colors
    val statusRes = when (st.status) { "paid" -> R.string.st_paid; "objected" -> R.string.st_objected; "resolved" -> R.string.st_resolved; else -> R.string.st_accrued }
    val statusColor = when (st.status) { "paid" -> SuccessGreen; "objected" -> Beige; else -> colors.muted }
    val statusIcon = when (st.status) { "paid", "resolved" -> Icons.Filled.CheckCircle; "objected" -> Icons.Filled.Warning; else -> Icons.Filled.Info }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.period.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text("$" + (st.payableUsd ?: st.adjustedShareUsd ?: st.shareUsd ?: "0"), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(statusRes), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = statusColor)
            }
            st.points?.get("total")?.let { Text("$it " + stringResource(R.string.expert_pts_total), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
            st.poolUsd?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.pool_label) + " $$it", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
            st.payoutRef?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1) }
        }
        st.objection?.takeIf { it.isNotEmpty() }?.let { obj ->
            Column(
                Modifier.fillMaxWidth().background(BeigeBg.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(stringResource(R.string.object_title) + (st.objectedAt?.let { " · " + it.take(10) } ?: ""), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Beige)
                Text(obj, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
                st.resolutionNote?.takeIf { it.isNotEmpty() }?.let { Text("→ $it", fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
            }
        }
        if (st.canObject == true) {
            Text(
                "⚑ " + stringResource(R.string.object_action),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                modifier = Modifier.clickable(onClick = onObject),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectionSheet(st: Statement, vm: ExpertEarningsViewModel, onDismiss: () -> Unit) {
    val colors = WorkisTheme.colors
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = text.trim().length >= 10
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.object_title), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink)
            Text(st.period.orEmpty() + " · $" + (st.payableUsd ?: st.shareUsd ?: "0"), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.object_placeholder), color = colors.faint, fontSize = 14.sp) },
                minLines = 4, maxLines = 10, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.object_sub), fontSize = 12.sp, lineHeight = 17.sp, color = colors.faint)
            error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { st.id?.let { id -> vm.objectStatement(id, text.trim()) { ok, msg -> if (ok) onDismiss() else error = msg } } },
                    enabled = valid && !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.height(44.dp),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                    else Text(stringResource(R.string.send), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
