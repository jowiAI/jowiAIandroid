package ai.workis.jowi.ui.screens.expert

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import ai.workis.jowi.ui.components.AnswerSheet
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/** Beige note band — server messages and business walls, never a red error. */
@Composable
fun NoteBand(text: String?) {
    if (text.isNullOrEmpty()) return
    Text(
        text,
        fontSize = 13.sp, lineHeight = 18.sp, color = Beige,
        modifier = Modifier
            .fillMaxWidth()
            .background(BeigeBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

/** Sorular — the expert's sector queue; tap → the shared answer sheet posting to the expert endpoint. */
@Composable
fun ExpertQuestionsScreen(vm: ExpertViewModel) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.loadQuestions() }
    var target by remember { mutableStateOf<ConsoleQuestion?>(null) }
    val rows = vm.questions

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { NoteBand(vm.actionNote) }
        when {
            rows != null -> {
                if (rows.isEmpty()) item {
                    Text(stringResource(R.string.expert_questions_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
                }
                items(rows) { q -> QuestionRow(q) { if (q.id != null) target = q } }
                vm.summary?.affiliationsExcluded?.takeIf { it > 0 }?.let { n ->
                    item {
                        Text(
                            "$n · " + stringResource(R.string.expert_affil_excluded),
                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint,
                        )
                    }
                }
            }
            vm.questionsError != null -> item { Text(vm.questionsError.orEmpty(), color = colors.danger, fontSize = 13.sp) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    target?.let { q ->
        AnswerSheet(
            question = q.display,
            busy = vm.busyKey != null,
            onDismiss = { target = null },
            onSend = { answer, teach -> q.id?.let { id -> vm.answer(id, answer, teach) { target = null } } },
        )
    }
}

@Composable
private fun QuestionRow(q: ConsoleQuestion, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (q.kind == "handoff") R.string.badge_handoff else R.string.badge_unmatched),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                color = Beige,
                modifier = Modifier.background(BeigeBg, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
            q.topic?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.accentText, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${q.days ?: 0} " + stringResource(R.string.days_word),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint,
            )
        }
        // translation is the main line; the working text (original language)
        // and the raw keystrokes ride underneath, small
        Text("“${q.display}”", fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
        if (q.display != q.q.orEmpty()) {
            Text(
                stringResource(R.string.original_label) +
                    (if (q.localizedIsMachine == true) " · " + stringResource(R.string.machine_translation) else "") +
                    " · " + q.q.orEmpty(),
                fontFamily = WorkisMono, fontSize = 11.sp, lineHeight = 16.sp, color = colors.faint,
            )
        }
        q.qOriginal?.takeIf { it != q.q }?.let {
            Text(stringResource(R.string.clarify_typed_label) + " · " + it, fontFamily = WorkisMono, fontSize = 11.sp, lineHeight = 16.sp, color = colors.faint)
        }
        q.hasEmail?.let { has ->
            Text(
                stringResource(if (has) R.string.has_email_label else R.string.no_email),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                color = if (has) SuccessGreen else colors.faint,
            )
        }
    }
}
