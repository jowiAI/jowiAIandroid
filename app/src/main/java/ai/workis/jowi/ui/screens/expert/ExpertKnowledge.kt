package ai.workis.jowi.ui.screens.expert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.KnowledgeNote
import ai.workis.jowi.data.KnowledgeUnit
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * Bilgi — the composer (Kaydet = the screen's one tinted control), conflicts
 * when the server returns any, then the searchable list of own notes: each
 * note a section (preview + date · facts · uses · 👍), units as rows,
 * swipe → retire / restore (retired = struck through + RETIRED tag).
 */
@Composable
fun ExpertKnowledgeScreen(vm: ExpertViewModel) {
    val colors = WorkisTheme.colors
    val focus = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadKnowledge(query) }
    val data = vm.knowledge

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
        focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
    )

    LazyColumn(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // composer
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.expert_teach_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.expert_teach_placeholder), color = colors.faint, fontSize = 15.sp) },
                        minLines = 4, maxLines = 10, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    vm.saveNote?.let { Text(it, fontSize = 13.sp, color = Beige) }
                    Button(
                        onClick = { focus.clearFocus(); vm.addKnowledge(draft.trim(), query) { draft = "" } },
                        enabled = draft.isNotBlank() && !vm.saving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Kiremit400, contentColor = OnKiremitFill,
                            disabledContainerColor = colors.canvas, disabledContentColor = colors.faint,
                        ),
                        modifier = Modifier.height(44.dp),
                    ) {
                        if (vm.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                        else Text(stringResource(R.string.expert_teach_save), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
                Text(stringResource(R.string.expert_teach_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        // conflicts
        if (vm.conflicts.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.expert_conflicts), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                vm.conflicts.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().background(BeigeBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.expert_conflict_new).uppercase() + " · " + c.newText.orEmpty(), fontSize = 13.sp, color = colors.ink)
                        Text(stringResource(R.string.expert_conflict_old).uppercase() + " · " + c.oldText.orEmpty(), fontSize = 13.sp, color = colors.muted)
                    }
                }
            }
        }
        // filter (server ?q=)
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it; if (it.isEmpty()) vm.loadKnowledge("") },
                placeholder = { Text(stringResource(R.string.expert_knowledge_filter), color = colors.faint, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.muted) },
                singleLine = true, shape = RoundedCornerShape(30.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.loadKnowledge(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            data != null -> {
                val notes = data.notes.orEmpty()
                if (notes.isEmpty()) item {
                    Text(stringResource(R.string.expert_knowledge_empty), fontSize = 13.sp, color = colors.faint)
                }
                notes.forEach { note ->
                    item(key = "note-" + (note.id ?: note.hashCode())) { NoteSection(note, vm, query) }
                }
            }
            vm.knowledgeError != null -> item { Text(vm.knowledgeError.orEmpty(), color = colors.danger, fontSize = 13.sp) }
            else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun NoteSection(note: KnowledgeNote, vm: ExpertViewModel, query: String) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(note.textPreview.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, maxLines = 2)
        Text(
            listOfNotNull(
                note.createdAt?.take(10),
                note.unitTotal?.let { "$it " + stringResource(R.string.expert_units_short) },
                note.useTotal?.let { "$it " + stringResource(R.string.expert_uses_short) },
                note.likeTotal?.let { "👍 $it" },
            ).joinToString(" · "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
        ) {
            note.units.orEmpty().forEachIndexed { i, u ->
                UnitRow(u) { u.id?.let { vm.toggleUnit(it, query) } }
                if (i < note.units.orEmpty().lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                }
            }
        }
    }
}

/** Swipe from the end → retire (kiremit) or restore (success green); the box snaps back, the list reloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitRow(u: KnowledgeUnit, onToggle: () -> Unit) {
    val colors = WorkisTheme.colors
    val retired = u.status == "retired"
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) onToggle(); false },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(if (retired) SuccessGreen else Kiremit500).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(if (retired) R.string.expert_unit_restore else R.string.expert_unit_retire),
                    color = OnKiremitFill, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                u.text.orEmpty(),
                fontSize = 14.sp, lineHeight = 20.sp,
                color = if (retired) colors.faint else colors.ink,
                textDecoration = if (retired) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (retired) {
                    Text(stringResource(R.string.expert_unit_retired), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint)
                }
                u.useCount?.takeIf { it > 0 }?.let {
                    Text("$it " + stringResource(R.string.expert_uses_short), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                }
            }
        }
    }
}
