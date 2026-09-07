package ai.workis.jowi.ui.screens.expert

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsultMessage
import ai.workis.jowi.data.ExpertConsult
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/** Danışmalar — one channel per buyer; price-blind by design. */
@Composable
fun ExpertConsultsScreen(vm: ExpertViewModel, onOpen: (id: String, title: String) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.loadConsults() }
    val rows = vm.consults

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(
                stringResource(R.string.expert_consults_sub),
                fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(14.dp),
            )
        }
        when {
            rows != null -> {
                if (rows.isEmpty()) item {
                    Text(stringResource(R.string.expert_consults_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
                }
                items(rows) { c -> ConsultRow(c) { c.id?.let { onOpen(it, c.title) } } }
            }
            vm.consultsError != null -> item { Text(vm.consultsError.orEmpty(), color = colors.danger, fontSize = 13.sp) }
            else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun ConsultRow(c: ExpertConsult, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
            c.unread?.takeIf { it > 0 }?.let {
                Text(
                    "$it", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = OnKiremitFill,
                    modifier = Modifier.background(Kiremit400, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text((c.lastAt ?: c.updatedAt).orEmpty().take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
        }
        c.lastText?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = colors.muted, maxLines = 2) }
    }
}

/** A consult thread: full-width cards with a role label and time, reply bar, "Yanıt veremeyeceğim" in the menu (agreement 2.1). */
@Composable
fun ExpertConsultThread(vm: ExpertViewModel, id: String, title: String, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(id) { vm.thread = null; vm.loadThread(id) }
    var reply by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var askUnavailable by remember { mutableStateOf(false) }
    var unavailableNote by remember { mutableStateOf("") }

    ConsoleSub(
        title = title,
        onBack = onBack,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.ink) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.expert_unavailable), color = colors.danger, fontSize = 14.sp) },
                        onClick = { menu = false; askUnavailable = true },
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val d = vm.thread
                when {
                    d != null -> items(d.messages.orEmpty()) { m -> Bubble(m) }
                    vm.threadError != null -> item { Text(vm.threadError.orEmpty(), color = colors.danger, fontSize = 13.sp) }
                    else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
                }
                item { NoteBand(vm.actionNote) }
                item { Spacer(Modifier.height(8.dp)) }
            }
            // reply bar
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it },
                    placeholder = { Text(stringResource(R.string.reply_placeholder), color = colors.faint, fontSize = 14.sp) },
                    maxLines = 4, shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                val sending = vm.busyKey == id
                IconButton(
                    onClick = { vm.reply(id, reply.trim()) { reply = "" } },
                    enabled = reply.isNotBlank() && !sending,
                    modifier = Modifier.size(44.dp).background(if (reply.isNotBlank()) Kiremit400 else colors.border, CircleShape),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                    else Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.send), tint = if (reply.isNotBlank()) OnKiremitFill else colors.muted)
                }
            }
        }
    }

    if (askUnavailable) {
        AlertDialog(
            onDismissRequest = { askUnavailable = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.expert_unavailable), color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.expert_unavailable_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                    OutlinedTextField(
                        value = unavailableNote, onValueChange = { unavailableNote = it },
                        placeholder = { Text(stringResource(R.string.expert_unavailable_note), color = colors.faint, fontSize = 14.sp) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { askUnavailable = false; vm.unavailable(id, unavailableNote.trim()); unavailableNote = "" }) {
                    Text(stringResource(R.string.send), color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { askUnavailable = false }) { Text(stringResource(R.string.cancel), color = colors.muted) }
            },
        )
    }
}

@Composable
private fun Bubble(m: ConsultMessage) {
    val colors = WorkisTheme.colors
    val mine = m.role == "expert"
    val system = m.role == "system"
    val who = when (m.role) {
        "expert" -> "🎓 " + stringResource(R.string.expert_you)
        "system" -> "·"
        else -> stringResource(R.string.expert_buyer)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(who.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = if (mine) colors.blue else colors.faint)
            Spacer(Modifier.weight(1f))
            m.at?.let { Text(it.take(16).replace("T", " "), fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint) }
        }
        Text(
            m.text.orEmpty(),
            fontSize = if (system) 13.sp else 14.sp, lineHeight = 20.sp,
            fontStyle = if (system) FontStyle.Italic else FontStyle.Normal,
            color = if (system) colors.muted else colors.ink,
        )
    }
}
