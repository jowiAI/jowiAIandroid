package ai.workis.jowi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
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
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * Answer + "Jowi'ye öğret" in one action (iOS WorkisAnswerSheet) — shared by
 * the coordinator console and the expert seat; the caller decides which
 * endpoint `onSend` posts to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerSheet(
    question: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (answer: String, teach: Boolean) -> Unit,
) {
    val colors = WorkisTheme.colors
    var answer by remember { mutableStateOf("") }
    var teach by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.padding(20.dp).imePadding()) {
            Text(question, color = colors.muted, fontSize = 13.sp)
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
                onClick = { onSend(answer.trim(), teach) },
                enabled = answer.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(stringResource(R.string.send), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
