package ai.workis.jowi.ui.screens.expert

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.R
import ai.workis.jowi.data.ExpertMonth
import ai.workis.jowi.data.ExpertSetup
import ai.workis.jowi.data.ExpertSummary
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * The expert seat's native console (role 5) — the mobile twin of the web's
 * /workis/uzman/ pages. Same grammar as the coordinator Panel: a tile board
 * whose tiles ARE the doors (Sorular, İncelemeler + Öneriler, Danışmalar,
 * Bilgi), then "Bu ay" and the "Kurulum" checklist. Slice 1; Kazanç and the
 * Profil sections join with slice 2.
 */
sealed interface ExpertDest {
    data object Home : ExpertDest
    data object Questions : ExpertDest
    data object Reviews : ExpertDest
    data object Knowledge : ExpertDest
    data object Consults : ExpertDest
    data class Thread(val id: String, val title: String) : ExpertDest
    data object Guide : ExpertDest
}

@Composable
fun ExpertRoot(vm: ExpertViewModel = viewModel()) {
    var dest by remember { mutableStateOf<ExpertDest>(ExpertDest.Home) }
    BackHandler(enabled = dest != ExpertDest.Home) {
        dest = if (dest is ExpertDest.Thread) ExpertDest.Consults else ExpertDest.Home
    }
    val home = { dest = ExpertDest.Home }
    when (val d = dest) {
        ExpertDest.Home -> ExpertHome(vm) { dest = it }
        ExpertDest.Questions -> ConsoleSub(stringResource(R.string.expert_questions_title), home) { ExpertQuestionsScreen(vm) }
        ExpertDest.Reviews -> ConsoleSub(stringResource(R.string.expert_reviews_title), home) { ExpertReviewsScreen(vm) }
        ExpertDest.Knowledge -> ConsoleSub(stringResource(R.string.expert_knowledge_title), home) { ExpertKnowledgeScreen(vm) }
        ExpertDest.Consults -> ConsoleSub(stringResource(R.string.expert_consults_title), home) {
            ExpertConsultsScreen(vm) { id, title -> dest = ExpertDest.Thread(id, title) }
        }
        is ExpertDest.Thread -> ExpertConsultThread(vm, d.id, d.title) { dest = ExpertDest.Consults }
        ExpertDest.Guide -> ExpertGuideScreen(pdfUrl = vm.summary?.guidePdfUrl, onBack = home)
    }
}

@Composable
private fun ExpertHome(vm: ExpertViewModel, onOpen: (ExpertDest) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.loadSummary() }
    val s = vm.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.console_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (vm.loading) WorkisMark(size = 16, breathing = true)
        }
        Spacer(Modifier.height(14.dp))

        when {
            s != null -> Board(s, onOpen)
            vm.summaryError != null -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.summaryError.orEmpty(), color = colors.danger, fontSize = 14.sp)
                TextButton(onClick = { vm.loadSummary() }) {
                    Text(stringResource(R.string.retry), color = colors.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                WorkisMark(size = 26, breathing = true)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Board(s: ExpertSummary, onOpen: (ExpertDest) -> Unit) {
    val w = s.waiting
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(stringResource(R.string.expert_panel_waiting), WorkisIcons.Tray)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(160.dp)) {
            BigTile(
                number = w?.questions ?: 0,
                label = stringResource(R.string.expert_tile_questions),
                sub = if ((w?.questions ?: 0) == 0) stringResource(R.string.expert_tile_questions_empty) else null,
                icon = WorkisIcons.QuestionCircle,
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) { onOpen(ExpertDest.Questions) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CapsuleTile(
                    count = (w?.reviews ?: 0) + (w?.companions ?: 0),
                    label = stringResource(R.string.expert_tile_reviews),
                    icon = WorkisIcons.ThumbsDown,
                    modifier = Modifier.weight(1f),
                ) { onOpen(ExpertDest.Reviews) }
                CapsuleTile(
                    count = w?.consults ?: 0,
                    label = stringResource(R.string.expert_tile_consults),
                    icon = WorkisIcons.GraduationCap,
                    modifier = Modifier.weight(1f),
                ) { onOpen(ExpertDest.Consults) }
            }
        }
        CapsuleTile(
            count = w?.unitsActive ?: 0,
            label = stringResource(R.string.expert_tile_knowledge),
            icon = WorkisIcons.Pencil,
            modifier = Modifier.height(56.dp),
        ) { onOpen(ExpertDest.Knowledge) }
        // the earnings guide — server Markdown, native chrome; the ONE guide door
        Card(Modifier.fillMaxWidth().height(56.dp), { onOpen(ExpertDest.Guide) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Icon(WorkisIcons.Book, contentDescription = null, tint = colorsOf().accentText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.expert_guide_door), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colorsOf().ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colorsOf().faint, modifier = Modifier.size(18.dp))
            }
        }

        SectionHeader(stringResource(R.string.expert_month), WorkisIcons.ChartBars)
        MonthCard(s.month)

        SectionHeader(stringResource(R.string.expert_setup), WorkisIcons.Checklist)
        SetupCard(s.setup)
    }
}

@Composable
private fun colorsOf() = WorkisTheme.colors

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Box(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
    ) { content() }
}

@Composable
private fun BigTile(number: Int, label: String, sub: String?, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Card(modifier, onClick) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            Text("$number", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = colors.ink)
            Text(label, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub ?: " ", fontSize = 11.sp, lineHeight = 14.sp, color = colors.faint, maxLines = 2)
        }
    }
}

@Composable
private fun CapsuleTile(count: Int, label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Card(modifier.fillMaxWidth(), onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("$count", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        }
    }
}

/** "Bu ay" — live points as mono data chips; the Kazanç door joins with slice 2. */
@Composable
private fun MonthCard(m: ExpertMonth?) {
    val colors = WorkisTheme.colors
    val chips = buildList {
        val p = m?.points
        if (p != null && (p.total ?: 0) > 0) {
            add("${p.total ?: 0} " + stringResource(R.string.expert_pts_total))
            p.uses?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.expert_pts_uses)) }
            p.likes?.takeIf { it > 0 }?.let { add("👍 $it") }
            p.answers?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.expert_pts_answers)) }
            p.reviews?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.expert_pts_reviews)) }
            p.consults?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.expert_pts_consults)) }
        } else {
            add(stringResource(R.string.expert_month_no_points))
        }
        m?.shareUsd?.takeIf { it.isNotEmpty() && it != "0" && it != "0.00" }?.let {
            add("$$it " + stringResource(R.string.expert_share))
        }
    }
    Card(Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            chips.forEach { c ->
                Text(
                    c.uppercase(),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    letterSpacing = 1.sp, color = colors.muted, maxLines = 1,
                    modifier = Modifier
                        .background(colors.ink.copy(alpha = 0.06f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Setup checklist — status is icon + text (never colour alone); missing rows wear the beige band. */
@Composable
private fun SetupCard(setup: ExpertSetup?) {
    val colors = WorkisTheme.colors
    val rows = listOf(
        (setup?.payoutReady ?: false) to
            stringResource(if (setup?.payoutReady == true) R.string.expert_setup_payout_ok else R.string.expert_setup_payout_missing),
        (setup?.showName ?: false) to
            stringResource(if (setup?.showName == true) R.string.expert_setup_name_on else R.string.expert_setup_name_off),
        (setup?.profileFilled ?: false) to
            stringResource(if (setup?.profileFilled == true) R.string.expert_setup_profile_ok else R.string.expert_setup_profile_empty),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (ok, text) ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (ok) colors.surface else BeigeBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (ok) SuccessGreen else Beige,
                        modifier = Modifier.size(15.dp).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text,
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                        lineHeight = 16.sp, letterSpacing = 0.5.sp,
                        color = if (ok) colors.muted else Beige,
                    )
                }
            }
            Text(
                stringResource(R.string.expert_setup_web),
                fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
