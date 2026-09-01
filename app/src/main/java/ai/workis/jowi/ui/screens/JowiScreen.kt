package ai.workis.jowi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.AskBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

@Composable
fun JowiScreen() {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()

    var question by remember { mutableStateOf("") }
    var lastQuestion by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || asking) return
        asking = true
        error = null
        lastQuestion = q
        question = ""
        scope.launch {
            when (val r = safeCall(Graph.language.value) {
                Graph.api.ask(AskBody(q = q, page = "lists"))
            }) {
                is ApiResult.Ok -> { answer = r.value.answer; source = r.value.source }
                is ApiResult.Err -> error = r.message
            }
            asking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Jowi",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            lastQuestion?.let { q ->
                Text(q, color = colors.muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
            }
            if (asking) {
                // chat typing idiom: "Jowi" + dots
                Text("Jowi …", color = colors.faint, fontSize = 14.sp)
            }
            answer?.let { a ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Text(a, color = colors.ink, fontSize = 15.sp)
                    source?.let { s ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (s == "page") stringResource(R.string.ask_answer_source_page)
                            else stringResource(R.string.ask_answer_source_chain),
                            color = colors.faint,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = colors.danger, fontSize = 13.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text(stringResource(R.string.ask_placeholder), color = colors.faint) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Kiremit500,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.blue,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { ask() }, enabled = question.isNotBlank() && !asking) {
                if (asking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Kiremit500,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Kiremit500,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
