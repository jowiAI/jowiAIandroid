package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import ai.workis.jowi.data.ConsoleMessage
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

@Composable
fun ConsoleConversationsScreen(vm: ConsoleViewModel, onOpen: (String, String?) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.conversations == null) vm.loadConversations() }
    val rows = vm.conversations?.conversations.orEmpty()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(rows) { c ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable { c.id?.let { onOpen(it, c.partner) } }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.partner ?: "—",
                        color = colors.ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.open == true) {
                        Text("●", color = SuccessGreen, fontSize = 12.sp)
                    } else {
                        Text(
                            stringResource(R.string.conv_closed),
                            color = colors.faint,
                            fontSize = 11.sp,
                        )
                    }
                }
                c.lastMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = colors.muted, fontSize = 13.sp, maxLines = 2)
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    c.messageCount?.let {
                        Text("$it ✉", color = colors.faint, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    c.days?.let {
                        Text("$it " + stringResource(R.string.days_word), color = colors.faint, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            vm.error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
            if (rows.isEmpty() && vm.conversations != null && vm.error == null) {
                Text(stringResource(R.string.conv_empty), color = colors.muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun ConversationThreadScreen(vm: ConsoleViewModel, convId: String) {
    val colors = WorkisTheme.colors
    LaunchedEffect(convId) { vm.loadThread(convId) }
    val detail = vm.thread

    var text by remember { mutableStateOf("") }
    var internal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        // an expert-seat thread (2026-09-11): the seat badge under the title; no internal notes there
        if (detail?.seat == "expert") {
            Text(
                "🎓 " + stringResource(R.string.seat_expert),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
                color = colors.blue, modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (vm.threadMissing) {
            // a real 404 only — the thread isn't there or isn't open to this seat
            Text(
                stringResource(R.string.conversation_missing),
                fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
            return@Column
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(detail?.messages.orEmpty()) { m -> MessageBubble(m) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        vm.error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }

        if (detail?.open != false) {
            // internal-note toggle only when the server says canInternal (fail-closed)
            if (detail?.canInternal == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = internal,
                        onCheckedChange = { internal = it },
                        colors = CheckboxDefaults.colors(checkedColor = Kiremit500),
                    )
                    Text(stringResource(R.string.internal_note), color = colors.muted, fontSize = 13.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.reply_placeholder), color = colors.faint) },
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
                IconButton(
                    onClick = {
                        vm.reply(convId, text.trim(), internal) { text = ""; internal = false }
                    },
                    enabled = text.isNotBlank() && vm.busyId == null,
                ) {
                    if (vm.busyId == convId) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Kiremit500)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MessageBubble(m: ConsoleMessage) {
    val colors = WorkisTheme.colors
    val isInternal = m.internal == true
    // author badge follows the seat — no role picker
    val badge = when (m.role) {
        "expert" -> "🎓"
        "lead" -> "📍"
        "jowi" -> "JOWİ"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isInternal) BeigeBg else colors.surface,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row {
            badge?.let {
                Text(
                    it,
                    fontFamily = WorkisMono,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = if (isInternal) Beige else colors.accentText,
                )
            }
            m.toWho?.let {
                Text(
                    "  → ${it.uppercase()}",
                    fontFamily = WorkisMono,
                    fontSize = 10.sp,
                    color = colors.faint,
                )
            }
            Spacer(Modifier.weight(1f))
            m.at?.take(10)?.let { Text(it, color = colors.faint, fontSize = 10.sp) }
        }
        Spacer(Modifier.height(6.dp))

        // plain-text reply marker: ↩ "quoted" — reply → muted quote line + reply body
        val raw = m.text.orEmpty()
        val quoteSplit = if (raw.startsWith("↩")) raw.split(" — ", limit = 2) else null
        if (quoteSplit != null && quoteSplit.size == 2) {
            Text(
                quoteSplit[0].removePrefix("↩").trim(),
                color = colors.faint,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(quoteSplit[1], color = if (isInternal) Beige else colors.ink, fontSize = 14.sp)
        } else {
            Text(raw, color = if (isInternal) Beige else colors.ink, fontSize = 14.sp)
        }
    }
}
