package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleQuestion
import ai.workis.jowi.ui.components.AnswerSheet
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

@Composable
fun ConsoleQuestionsScreen(
    vm: ConsoleViewModel,
    openJowi: Boolean = false,
    jowiSource: String? = null,
    jowiTopic: String? = null,
    onOpenThread: (id: String, partner: String?) -> Unit = { _, _ -> },
) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.questions == null) vm.loadQuestions() }

    var topicFilter by remember { mutableStateOf<String?>(null) }
    var answering by remember { mutableStateOf<ConsoleQuestion?>(null) }
    // "Jowi yanıtladı" lane — the Panel's chips land here with the lane open and a filter preselected
    var lane by rememberSaveable { mutableStateOf(if (openJowi) "jowi" else "pending") }
    LaunchedEffect(Unit) {
        if (openJowi) { vm.laneSource = jowiSource; vm.laneTopic = jowiTopic }
        if (lane == "jowi") vm.loadLane()
    }

    val all = vm.questions?.questions.orEmpty()
    val topics = all.mapNotNull { it.topic }.distinct()
    val rows = if (topicFilter == null) all else all.filter { it.topic == topicFilter }
    val laneTotal = vm.laneData?.total ?: vm.summary?.jowiAnswered?.total ?: 0

    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                "pending" to (stringResource(R.string.lane_waiting) + " · ${all.size}"),
                "jowi" to (stringResource(R.string.jowi_answered_title) + " · $laneTotal"),
            ).forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = lane == key,
                    onClick = { lane = key; if (key == "jowi" && vm.laneData == null) vm.loadLane() },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.surface, activeContentColor = colors.ink,
                        inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted,
                    ),
                ) { Text(label, fontSize = 13.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (lane == "jowi") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                jowiLane(vm, onOpenThread)
                item { Spacer(Modifier.height(30.dp)) }
            }
            return@Column
        }

        if (topics.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(topics) { t ->
                    val selected = topicFilter == t
                    Text(
                        t,
                        color = if (selected) colors.ink else colors.muted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(
                                if (selected) colors.surface else colors.canvas,
                                RoundedCornerShape(5.dp),
                            )
                            .border(
                                1.dp,
                                if (selected) Kiremit500 else colors.border,
                                RoundedCornerShape(5.dp),
                            )
                            .clickable { topicFilter = if (selected) null else t }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(rows) { q ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Text(q.q ?: "—", color = colors.ink, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        q.kind?.let {
                            Text(
                                it.uppercase(),
                                fontFamily = WorkisMono,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = colors.accentText,
                            )
                        }
                        q.days?.let {
                            Text(
                                "  " + "$it " + stringResource(R.string.days_word),
                                fontSize = 11.sp,
                                color = colors.faint,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { q.id?.let(vm::questionDone) }) {
                            Text(stringResource(R.string.close_question), color = colors.muted, fontSize = 13.sp)
                        }
                        TextButton(onClick = { answering = q }) {
                            Text(
                                stringResource(R.string.answer_action),
                                color = colors.accentText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            item {
                vm.error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
                if (rows.isEmpty() && vm.questions != null && vm.error == null) {
                    Text(stringResource(R.string.console_empty), color = colors.muted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // Answer + "teach Jowi" in one action (shared AnswerSheet)
    answering?.let { q ->
        AnswerSheet(
            question = q.q ?: "",
            busy = vm.busyId != null,
            onDismiss = { answering = null },
            onSend = { answer, teach ->
                q.id?.let { id -> vm.answerQuestion(id, answer, teach) { answering = null } }
            },
        )
    }
}
