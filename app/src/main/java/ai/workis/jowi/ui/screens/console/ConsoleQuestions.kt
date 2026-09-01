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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleQuestion
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleQuestionsScreen(vm: ConsoleViewModel) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.questions == null) vm.loadQuestions() }

    var topicFilter by remember { mutableStateOf<String?>(null) }
    var answering by remember { mutableStateOf<ConsoleQuestion?>(null) }

    val all = vm.questions?.questions.orEmpty()
    val topics = all.mapNotNull { it.topic }.distinct()
    val rows = if (topicFilter == null) all else all.filter { it.topic == topicFilter }

    Column(Modifier.fillMaxWidth()) {
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

    // Answer + "teach Jowi" in one action (iOS WorkisAnswerSheet)
    answering?.let { q ->
        var answer by remember { mutableStateOf("") }
        var teach by remember { mutableStateOf(true) }
        ModalBottomSheet(onDismissRequest = { answering = null }, containerColor = colors.surface) {
            Column(Modifier.padding(20.dp).imePadding()) {
                Text(q.q ?: "", color = colors.muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    placeholder = { Text(stringResource(R.string.answer_placeholder), color = colors.faint) },
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Kiremit500,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        cursorColor = colors.blue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = teach,
                        onCheckedChange = { teach = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Kiremit500),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.teach_toggle), color = colors.ink, fontSize = 14.sp)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        q.id?.let { id ->
                            vm.answerQuestion(id, answer.trim(), teach) { answering = null }
                        }
                    },
                    enabled = answer.isNotBlank() && vm.busyId == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Kiremit400,
                        contentColor = OnKiremitFill,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(R.string.send), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
