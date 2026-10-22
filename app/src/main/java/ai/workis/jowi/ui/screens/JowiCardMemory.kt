package ai.workis.jowi.ui.screens

import ai.workis.jowi.R
import ai.workis.jowi.data.AskDestination
import ai.workis.jowi.data.AskDraft
import ai.workis.jowi.data.AskMemory
import ai.workis.jowi.data.AskMemoryChip
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Alım hafızası on the cards (ledger 54; iOS WorkisMainView `destinationLine`,
// `WorkisDestinationSheet`, `memoryBlock`). Words are the server's; only the labels of the
// destination line come from the catalog (dest_*).

/** The reader's choice of where a card's rows go; no entry = `destination.default`. */
sealed interface DestPick {
    data class Into(val id: String) : DestPick
    data object New : DestPick
}

private const val NAME_MAX = 110

/** "Başlık (3 satır)". */
@Composable
private fun draftWords(dr: AskDraft): String =
    "${dr.title.orEmpty()} (${dr.rows ?: 0} ${stringResource(R.string.dest_rows_word)})"

/** "21 Eki 14:05" from the draft's ISO stamp; null when unreadable. */
private fun draftWhen(iso: String?): String? {
    val s = iso?.take(16) ?: return null
    if (s.length < 16) return null
    val d = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.ROOT).parse(s) }.getOrNull() ?: return null
    val locale = if (ai.workis.jowi.Graph.language.value == "en") java.util.Locale.UK else java.util.Locale.forLanguageTag("tr-TR")
    return java.text.SimpleDateFormat("d MMM HH:mm", locale).format(d)
}

/**
 * ONE line above the card's button: "→ Taslak listeye eklenecek: <title> (<rows> satır)" or
 * "→ Yeni liste: <title>" (a name field when the server's title is ""), then "değiştir".
 * `needsChoice`: the line IS the drafts' chips until one is tapped (frame + tick); the button waits.
 */
@Composable
internal fun DestinationLine(d: AskDestination, key: String, prefill: String, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val live = !vm.busy
    val pick = vm.destPick(key, d)
    val pickedInto = (pick as? DestPick.Into)?.id
    val drafts = d.drafts.orEmpty().filter { !it.id.isNullOrEmpty() }
    var sheet by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            d.needsChoice == true && pick != DestPick.New -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                drafts.forEach { dr ->
                    val on = pickedInto == dr.id
                    SelectChip(on = on, enabled = live, onClick = { vm.destPicks[key] = DestPick.Into(dr.id!!) }) {
                        Text(draftWords(dr), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                    }
                }
            }
            pickedInto != null -> {
                val dr = drafts.firstOrNull { it.id == pickedInto }
                Text(
                    "→ " + stringResource(R.string.dest_into) + ": " + (dr?.let { draftWords(it) } ?: d.default?.title.orEmpty()),
                    fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted,
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("→ " + stringResource(R.string.dest_new) + ":", fontSize = 13.sp, color = colors.muted)
                Spacer(Modifier.width(6.dp))
                if (d.default?.title.isNullOrEmpty()) {
                    val placeholder = stringResource(R.string.dest_name_placeholder)
                    val value = vm.destNames[key].orEmpty()
                    BasicTextField(
                        value = value,
                        onValueChange = { vm.destNames[key] = it.take(NAME_MAX) },
                        enabled = live,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.ink).readingStep(),
                        cursorBrush = SolidColor(Kiremit500),
                        modifier = Modifier.weight(1f)
                            .background(colors.ink.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = colors.faint)
                                inner()
                            }
                        },
                    )
                } else {
                    Text(vm.destName(key, d), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                }
            }
        }
        Text(
            stringResource(R.string.dest_change), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (live) colors.blue else colors.faint,
            modifier = Modifier.clickable(enabled = live, role = Role.Button) { sheet = true }.padding(vertical = 2.dp),
        )
    }
    if (sheet) {
        DestinationSheet(
            drafts = drafts,
            currentInto = pickedInto,
            name = vm.destNames[key] ?: prefill.ifEmpty { d.default?.title.orEmpty() },
            onClose = { sheet = false },
            onInto = { vm.destPicks[key] = DestPick.Into(it); sheet = false },
            onNew = { vm.destPicks[key] = DestPick.New; vm.destNames[key] = it; sheet = false },
        )
    }
}

/**
 * The picker behind "değiştir": the caller's open drafts (title · rows satır · updatedAt), the
 * current one ticked, and "Yeni liste…" with a name field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSheet(
    drafts: List<AskDraft>, currentInto: String?, name: String,
    onClose: () -> Unit, onInto: (String) -> Unit, onNew: (String) -> Unit,
) {
    val colors = WorkisTheme.colors
    var typed by remember { mutableStateOf(name) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            drafts.forEach { dr ->
                val on = currentInto == dr.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .semantics { selected = on }
                        .clickable(role = Role.Button) { onInto(dr.id!!) }
                        .padding(vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dr.title.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                        Text(
                            (listOf("${dr.rows ?: 0} " + stringResource(R.string.dest_rows_word)) + listOfNotNull(draftWhen(dr.updatedAt)))
                                .joinToString(" · "),
                            fontSize = 12.sp, color = colors.muted,
                        )
                    }
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(20.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink.copy(alpha = 0.08f)))
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.dest_new_list), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(NAME_MAX) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dest_name_placeholder), color = colors.faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = Kiremit500,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { onNew(typed.trim()) },
                border = BorderStroke(1.dp, colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            ) {
                Text(stringResource(R.string.dest_new), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** A secondary chip: hairline frame; chosen = a Kiremit500 frame AND a tick (never colour alone). */
@Composable
private fun SelectChip(on: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .border(if (on) 1.5.dp else 1.dp, if (on) Kiremit500 else colors.border, shape)
            .semantics { selected = on }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (on) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.widthIn(max = 300.dp)) { content() }
    }
}

/** The memory's line on a need card — under "Hatırladığım", muted. */
@Composable
internal fun NeedMemoryLine(mem: AskMemory?) {
    val text = mem?.line?.takeIf { it.isNotEmpty() } ?: return
    MarkdownText(text, color = WorkisTheme.colors.muted, fontSize = 13.sp, lineHeight = 18.sp)
}

/** The partner a chip acts on: `action.partnerId`, else the chip's own. */
private fun chipPartner(c: AskMemoryChip): String? =
    c.action?.partnerId?.takeIf { it.isNotEmpty() } ?: c.partnerId?.takeIf { it.isNotEmpty() }

internal fun chipId(c: AskMemoryChip): String = c.kind.orEmpty() + "\u001F" + c.action?.select.orEmpty() + "\u001F" + chipPartner(c).orEmpty()

/**
 * Only a chip that can act on THIS card: its seller is among the rows; `except` also needs
 * another seller; `all` needs a row.
 */
private fun usableChips(mem: AskMemory, m: AskMatch): List<AskMemoryChip> {
    val onCard = m.products.mapNotNull { it.partnerId?.takeIf { p -> p.isNotEmpty() } }.toSet()
    if (onCard.isEmpty()) return emptyList()
    return mem.chips.orEmpty().filter { c ->
        val pid = chipPartner(c)
        when (c.action?.select) {
            "all" -> true
            "seller" -> pid != null && pid in onCard
            "except" -> pid != null && pid in onCard && (onCard - pid).isNotEmpty()
            else -> false
        }
    }
}

/**
 * The firm's own record above the match card's reading: `line` (light markdown, the seller in
 * bold) and the chips — each ticks the card's rows locally; nothing is sent.
 */
@Composable
internal fun MatchMemoryBlock(mem: AskMemory, m: AskMatch, line: JowiLine, vm: JowiViewModel) {
    val colors = WorkisTheme.colors
    val chips = usableChips(mem, m)
    val text = mem.line.orEmpty()
    if (text.isEmpty() && chips.isEmpty()) return
    val key = vm.matchKey(line)
    val enabled = line.listId == null && !line.collecting
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        if (text.isNotEmpty()) MarkdownText(text, color = colors.ink, fontSize = 14.sp, lineHeight = 20.sp)
        chips.forEach { c ->
            SelectChip(on = vm.memoryChipOn[key] == chipId(c), enabled = enabled, onClick = { vm.applyChip(c, m, line) }) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                    c.evidence?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, fontSize = 11.sp, lineHeight = 15.sp, color = colors.muted)
                    }
                }
            }
        }
    }
}
