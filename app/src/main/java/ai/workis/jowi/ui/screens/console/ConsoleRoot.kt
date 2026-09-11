package ai.workis.jowi.ui.screens.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ai.workis.jowi.data.ApplicationRow
import ai.workis.jowi.data.JowiAnswered
import ai.workis.jowi.data.JowiTopic
import ai.workis.jowi.data.QuestionTopic
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

sealed interface ConsoleDest {
    data object Home : ConsoleDest
    data object Pipeline : ConsoleDest
    /** The Panel's "Jowi answered" chips land here with the lane open and a filter preselected. */
    data class Questions(val openJowi: Boolean = false, val source: String? = null, val topic: String? = null) : ConsoleDest
    data object Partners : ConsoleDest
    data object Conversations : ConsoleDest
    data class Thread(val id: String, val partner: String?, val back: ConsoleDest = Conversations) : ConsoleDest
    data class Detail(val row: ApplicationRow) : ConsoleDest
}

@Composable
fun ConsoleRoot(vm: ConsoleViewModel = viewModel()) {
    var dest by remember { mutableStateOf<ConsoleDest>(ConsoleDest.Home) }

    BackHandler(enabled = dest != ConsoleDest.Home) {
        dest = when (val d = dest) {
            is ConsoleDest.Thread -> d.back
            is ConsoleDest.Detail -> ConsoleDest.Pipeline
            else -> ConsoleDest.Home
        }
    }

    when (val d = dest) {
        is ConsoleDest.Home -> ConsoleHome(vm) { dest = it }
        is ConsoleDest.Pipeline -> ConsoleSub(stringResource(R.string.console_all_apps), { dest = ConsoleDest.Home }) {
            ConsolePipeline(vm) { dest = ConsoleDest.Detail(it) }
        }
        is ConsoleDest.Detail -> ApplicationDetailScreen(vm, d.row) { dest = ConsoleDest.Pipeline }
        is ConsoleDest.Questions -> ConsoleSub(stringResource(R.string.questions_title), { dest = ConsoleDest.Home }) {
            ConsoleQuestionsScreen(vm, openJowi = d.openJowi, jowiSource = d.source, jowiTopic = d.topic) { id, partner ->
                dest = ConsoleDest.Thread(id, partner, back = d)
            }
        }
        is ConsoleDest.Partners -> ConsoleSub(stringResource(R.string.partners_title), { dest = ConsoleDest.Home }) {
            ConsolePartnersScreen(vm)
        }
        is ConsoleDest.Conversations -> ConsoleSub(stringResource(R.string.conversations_title), { dest = ConsoleDest.Home }) {
            ConsoleConversationsScreen(vm) { id, partner -> dest = ConsoleDest.Thread(id, partner) }
        }
        is ConsoleDest.Thread -> ConsoleSub(d.partner ?: "", { dest = d.back }) {
            ConversationThreadScreen(vm, d.id)
        }
    }
}

/** Sub-screen scaffold: back-circle top-left + title, mirrors the iOS header pattern. */
@Composable
fun ConsoleSub(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 22.sp,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ConsoleHome(vm: ConsoleViewModel, onOpen: (ConsoleDest) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.summary == null) vm.loadSummary() }
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

        // iOS tile board: big Applications square on the left, Partners +
        // Conversations capsules stacked on the right
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable { onOpen(ConsoleDest.Pipeline) }
                    .padding(16.dp),
            ) {
                Icon(
                    WorkisIcons.Tray, contentDescription = null,
                    tint = Kiremit500, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    s?.newApplications?.toString() ?: "—",
                    fontFamily = WorkisMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    color = colors.ink,
                )
                Text(
                    stringResource(R.string.stat_new_apps),
                    fontSize = 13.sp,
                    color = colors.muted,
                )
                s?.awaitingApproval?.takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$it " + stringResource(R.string.stat_awaiting).lowercase(),
                        fontFamily = WorkisMono,
                        fontSize = 12.sp,
                        color = colors.accentText,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CapsuleTile(
                    icon = WorkisIcons.Building,
                    title = stringResource(R.string.partners_title),
                    count = s?.activePartners,
                ) { onOpen(ConsoleDest.Partners) }
                CapsuleTile(
                    icon = WorkisIcons.Bubble,
                    title = stringResource(R.string.conversations_title),
                    count = s?.openConversations ?: 0,
                ) { onOpen(ConsoleDest.Conversations) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Sorular widget: envelope + big count + oldest; topic mosaic with counts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .clickable { onOpen(ConsoleDest.Questions()) }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Email, contentDescription = null,
                    tint = Kiremit500, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    s?.openQuestions?.toString() ?: "—",
                    fontFamily = WorkisMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.stat_open_q),
                    fontSize = 15.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                s?.oldestQuestionDays?.takeIf { it > 0 }?.let {
                    Text(
                        stringResource(R.string.oldest_word) + " $it " + stringResource(R.string.days_word),
                        fontFamily = WorkisMono,
                        fontSize = 12.sp,
                        color = colors.accentText,
                    )
                }
            }

            val topics = s?.questionTopics.orEmpty()
                .filter { it.topic != null }
                .sortedByDescending { it.count ?: 0 }
            if (topics.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // biggest topic gets the beige hero square
                    TopicHero(topics[0], Modifier.weight(1f).height(180.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        topics.getOrNull(1)?.let { TopicCard(it, Modifier.height(84.dp)) }
                        topics.getOrNull(2)?.let { TopicCard(it, Modifier.height(84.dp)) }
                    }
                }
                topics.drop(3).chunked(3).forEach { rowTopics ->
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTopics.forEach { t ->
                            TopicCard(t, Modifier.weight(1f).height(72.dp))
                        }
                    }
                }
            }
        }

        s?.jowiAnswered?.takeIf { (it.total ?: 0) > 0 }?.let { ja ->
            Spacer(Modifier.height(14.dp))
            JowiAnsweredBlock(ja) { source, topic -> onOpen(ConsoleDest.Questions(openJowi = true, source = source, topic = topic)) }
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = colors.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(30.dp))
    }
}

/**
 * "Jowi yanıtladı · son 7 gün" — the web lane's block: source cells and the
 * topic bento whose headline is the VERDICT line "👍 n · 👎 m · ✓ r" (open
 * dislikes in danger, reviewed in success green); the question count is the
 * small caption. Two awaiting badges in the header. Cells deep-link into the
 * Sorular lane, filtered.
 */
@Composable
private fun JowiAnsweredBlock(ja: JowiAnswered, onFilter: (source: String?, topic: String?) -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(WorkisIcons.Sparkle, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("${ja.total ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = colors.ink)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.jowi_answered_title) + " · " + stringResource(R.string.jowi_answered_last) +
                    " ${ja.days ?: 30} " + stringResource(R.string.days_word),
                fontSize = 14.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val staffOpen = ja.reviewOpen ?: 0
        val expertOpen = ja.dislikedOpen ?: 0
        if (staffOpen > 0 || expertOpen > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (staffOpen > 0) AwaitingBadge("$staffOpen " + stringResource(R.string.jowi_awaiting_staff))
                if (expertOpen > 0) AwaitingBadge("$expertOpen " + stringResource(R.string.jowi_awaiting_expert))
            }
        }
        // source cells: up to four, equal width, sorted by count
        val sources = ja.sources.orEmpty().filter { it.source != null && (it.count ?: 0) > 0 }
            .sortedByDescending { it.count ?: 0 }.take(4)
        if (sources.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(96.dp)) {
                sources.forEach { src ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.ink.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                            .clickable { onFilter(src.source, null) }
                            .padding(10.dp),
                    ) {
                        Text(
                            (src.label ?: src.source.orEmpty()).uppercase(),
                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                            lineHeight = 12.sp, color = colors.accentText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        VerdictLine(src.up ?: 0, src.downOpen ?: src.down ?: 0, src.downReviewed ?: 0, size = 18.sp, tint = colors.ink)
                        Text("${src.count ?: 0} " + stringResource(R.string.question_unit), fontSize = 11.sp, color = colors.faint)
                    }
                }
            }
        }
        // topics via the bento: hero + two runners-up + three small
        val topics = ja.topics.orEmpty().filter { !it.topic.isNullOrEmpty() && (it.count ?: 0) > 0 }
            .sortedByDescending { it.count ?: 0 }
        if (topics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(160.dp)) {
                LaneCell(topics[0], BentoStyle.Hero, Modifier.weight(1f).fillMaxHeight()) { onFilter(null, topics[0].topic) }
                if (topics.size > 1) {
                    Column(Modifier.width(136.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LaneCell(topics[1], BentoStyle.Medium, Modifier.weight(1f)) { onFilter(null, topics[1].topic) }
                        if (topics.size > 2) LaneCell(topics[2], BentoStyle.Medium, Modifier.weight(1f)) { onFilter(null, topics[2].topic) }
                    }
                }
            }
            if (topics.size > 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(76.dp)) {
                    topics.drop(3).take(3).forEach { tp ->
                        LaneCell(tp, BentoStyle.Small, Modifier.weight(1f).fillMaxHeight()) { onFilter(null, tp.topic) }
                    }
                }
            }
        }
    }
}

private enum class BentoStyle { Hero, Medium, Small }

@Composable
private fun AwaitingBadge(text: String) {
    Text(
        text,
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Beige,
        modifier = Modifier.background(BeigeBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * "👍 n · 👎 m · ✓ r" in mono digits — the cell's headline. The big 👎 is the
 * OPEN count (danger when > 0; the glyph and the "✓ r" caption keep it
 * readable without the colour); ✓ r = reviewed, hidden at 0.
 */
@Composable
fun VerdictLine(up: Int?, downOpen: Int, downReviewed: Int, size: androidx.compose.ui.unit.TextUnit, tint: androidx.compose.ui.graphics.Color) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (up != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("👍", fontSize = size * 0.7f)
                Text("$up", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = size, color = tint)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("👎", fontSize = size * 0.7f)
            Text("$downOpen", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = size, color = if (downOpen > 0) colors.danger else tint)
        }
        if (downReviewed > 0) {
            Text("✓ $downReviewed", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = maxOf(10f, size.value * 0.55f).sp, color = SuccessGreen)
        }
    }
}

@Composable
private fun LaneCell(item: JowiTopic, style: BentoStyle, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val hero = style == BentoStyle.Hero
    Column(
        modifier = modifier
            .background(
                if (hero) BeigeBg else colors.ink.copy(alpha = if (style == BentoStyle.Medium) 0.07f else 0.05f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(if (hero) 14.dp else 10.dp),
    ) {
        Text(
            item.topic.orEmpty(),
            fontWeight = FontWeight.SemiBold,
            fontSize = when (style) { BentoStyle.Hero -> 15.sp; BentoStyle.Medium -> 13.sp; BentoStyle.Small -> 12.sp },
            color = if (hero) Beige else colors.accentText,
            maxLines = if (hero) 2 else 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        VerdictLine(
            item.up, item.downOpen ?: item.down ?: 0, item.downReviewed ?: 0,
            size = when (style) { BentoStyle.Hero -> 24.sp; BentoStyle.Medium -> 15.sp; BentoStyle.Small -> 13.sp },
            tint = if (hero) Beige else colors.ink,
        )
        Text(
            "${item.count ?: 0} " + stringResource(R.string.question_unit),
            fontSize = if (hero) 12.sp else 10.sp,
            color = if (hero) Beige.copy(alpha = 0.8f) else colors.faint,
        )
    }
}

@Composable
private fun CapsuleTile(icon: ImageVector, title: String, count: Int?, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 22.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            count?.toString() ?: "—",
            fontFamily = WorkisMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = colors.ink,
        )
    }
}

@Composable
private fun TopicHero(topic: QuestionTopic, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(BeigeBg, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(topic.topic.orEmpty(), fontSize = 15.sp, color = Beige)
        Spacer(Modifier.weight(1f))
        Text(
            (topic.count ?: 0).toString(),
            fontFamily = WorkisMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            color = Beige,
        )
    }
}

@Composable
private fun TopicCard(topic: QuestionTopic, modifier: Modifier) {
    val colors = WorkisTheme.colors
    Column(
        modifier = modifier
            .background(colors.canvas, RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            topic.topic.orEmpty(),
            fontSize = 13.sp,
            color = colors.accentText,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            (topic.count ?: 0).toString(),
            fontFamily = WorkisMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = colors.ink,
        )
    }
}
