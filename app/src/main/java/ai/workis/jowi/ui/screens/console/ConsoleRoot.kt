package ai.workis.jowi.ui.screens.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import ai.workis.jowi.data.QuestionTopic
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
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
 * "Jowi yanıtladı · son 30 gün" — the web lane's compact block: source pills
 * with 👍/👎, topic chips, the "awaiting expert" badge for generative answers
 * with an unreviewed 👎. Chips deep-link into the Sorular lane, filtered.
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
            ja.dislikedOpen?.takeIf { it > 0 }?.let { n ->
                Text(
                    "$n " + stringResource(R.string.jowi_awaiting_expert),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp,
                    color = Beige,
                    modifier = Modifier.background(BeigeBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        val sources = ja.sources.orEmpty().filter { it.source != null }
        if (sources.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { src ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(colors.ink.copy(alpha = 0.06f), CircleShape)
                            .clickable { onFilter(src.source, null) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text((src.label ?: src.source.orEmpty()).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.muted, maxLines = 1)
                        Text("${src.count ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
                        if ((src.up ?: 0) + (src.down ?: 0) > 0) {
                            Text("👍${src.up ?: 0} 👎${src.down ?: 0}", fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint)
                        }
                    }
                }
            }
        }
        val topics = ja.topics.orEmpty().filter { !it.topic.isNullOrEmpty() }.take(8)
        if (topics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { tp ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .background(colors.ink.copy(alpha = 0.05f), CircleShape)
                            .clickable { onFilter(null, tp.topic) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(tp.topic.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.accentText, maxLines = 1)
                        Text("×${tp.count ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                        tp.down?.takeIf { it > 0 }?.let { Text("👎$it", fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint) }
                    }
                }
            }
        }
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
