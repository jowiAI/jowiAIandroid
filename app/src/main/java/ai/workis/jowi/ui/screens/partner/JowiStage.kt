package ai.workis.jowi.ui.screens.partner

import ai.workis.jowi.R
import ai.workis.jowi.data.PLItem
import ai.workis.jowi.data.PLUseAlternative
import ai.workis.jowi.data.PLUseCard
import ai.workis.jowi.data.PLUseComplement
import ai.workis.jowi.data.PurchaseListDetail
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Jowi's stage (ledger 66, Django row_use.py · contract "Lists: the row asks what it is for"):
// the row asks what it is for, Jowi answers with a better fit and what goes with the job.
// A section ON the list page (not a sheet — a sheet would cover the row it talks about), drafts
// only, one row at a time. Its buttons are bordered: the toolbar's "Teklif iste" stays the page's
// one tinted fill. No price anywhere on it — the first number a buyer sees is a quote.

/**
 * The stage (`item` = the row on it, see `ListDetailViewModel.stageItem`), or — shut, or with no
 * row to talk about — the list's `jowiSummary.line` alone as a tap that reopens it. Scrolls the
 * page to itself while `vm.stageScrollPending` (a badge tap, an add that asks).
 */
@Composable
fun JowiStage(
    d: PurchaseListDetail, item: PLItem?, vm: ListDetailViewModel, pageScroll: ScrollState,
    onNeedsVariant: (PLItem, String) -> Unit,
) {
    val colors = WorkisTheme.colors
    val summary = d.list?.jowiSummary?.line?.trim().orEmpty()
    val card = item?.let { vm.cardOf(it) }
    val showStage = item != null && (vm.useReading || item.askUse == true || card != null)

    // the stage's top in the page's content (the Column it sits in scrolls)
    var top by remember { mutableStateOf<Float?>(null) }
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(vm.stageScrollPending, top, showStage) {
        val y = top
        if (vm.stageScrollPending && showStage && y != null) {
            pageScroll.animateScrollTo((y - gap).toInt().coerceAtLeast(0))
            vm.stageScrollPending = false
        }
    }
    val place = Modifier.onGloballyPositioned { top = it.positionInParent().y }

    if (!showStage) {
        if (summary.isEmpty()) return
        // the stage is shut: the summary line stays, a tap brings the stage back
        Row(
            place.fillMaxWidth().clickable(role = Role.Button) { vm.reopenStage() }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WorkisMark(size = 16)
            Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, modifier = Modifier.weight(1f))
        }
        return
    }
    val row = item ?: return
    val itemId = row.id ?: return
    Column(
        place.fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .background(Kiremit400.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .border(1.dp, Kiremit400, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StageHeader(row, reading = vm.useReading, onClose = vm::closeStage)
        when {
            vm.useReading -> Text(stringResource(R.string.use_reading), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
            card != null -> CardState(row, card, vm.addedFromStage, vm::complementKey, busy = vm.busy,
                onSwitch = { vm.switchTo(itemId, it, onNeedsVariant) },
                onAdd = { vm.addComplement(itemId, it, onNeedsVariant) })
            else -> {
                val nothing = stringResource(R.string.use_nothing)
                QuestionState(row, vm.useDraft, onDraft = { vm.useDraft = it },
                    onTell = { vm.answerUse(itemId, vm.useDraft, nothing) },
                    onSkip = { vm.answerUse(itemId, null, nothing) })
            }
        }
        if (summary.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            // the summary line closes the stage
            Text(
                summary, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = vm::closeStage).padding(vertical = 2.dp),
            )
        }
    }
}

/** The slash (breathing while the answer is out) · "Jowi · satır N" · product · qty unit · seller · ×. */
@Composable
private fun StageHeader(item: PLItem, reading: Boolean, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkisMark(size = 18, breathing = reading)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "Jowi · " + stringResource(R.string.use_row_word) + " ${item.n ?: 0}",
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp, color = Kiremit500,
            )
            val sel = item.selected
            val qty = if (item.qty != null || !item.unit.isNullOrEmpty()) (WorkisMoney.qty(item.qty) + " " + item.unit.orEmpty()).trim() else ""
            val line = listOf(sel?.displayName ?: item.name.orEmpty(), qty, sel?.seller.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }
            if (line.isNotEmpty()) Text(line.joinToString(" · "), fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.use_close), tint = colors.faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Ne için kullanacaksınız?" — the chips fill the line (never send); "Jowi'ye söyle" sends, "Atla" skips for good. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionState(item: PLItem, draft: String, onDraft: (String) -> Unit, onTell: () -> Unit, onSkip: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(stringResource(R.string.use_question), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
    // the web's set sentence first (ledger 68) — the satisfaction tick's `wrong_product` chip, word for word — then why Jowi asks
    Text(stringResource(R.string.use_why_ask) + " " + stringResource(R.string.use_intro), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
    val chips = item.useChips.orEmpty().filter { it.isNotBlank() }
    if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            val on = chip == draft
            Row(
                Modifier
                    .semantics { selected = on }
                    .background(if (on) Kiremit500.copy(alpha = 0.08f) else colors.surface, CircleShape)
                    .border(if (on) 1.5.dp else 1.dp, if (on) Kiremit500 else colors.border, CircleShape)
                    .clickable(role = Role.Button) { onDraft(chip) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // selected says itself with a tick and the heavier frame, never colour alone
                if (on) Icon(WorkisIcons.Check, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(12.dp))
                Text(chip, fontSize = 13.sp, color = colors.ink, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
    val canTell = draft.isNotBlank()
    OutlinedTextField(
        value = draft, onValueChange = onDraft, singleLine = true,
        placeholder = { Text(stringResource(R.string.use_placeholder), color = colors.faint) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
            focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
            focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (canTell) onTell() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StageButton(stringResource(R.string.use_tell), Kiremit500, enabled = canTell, onClick = onTell)
        TextButton(onClick = onSkip) { Text(stringResource(R.string.use_skip), fontSize = 14.sp, color = colors.muted) }
    }
}

/** "Kullanım: …", the reason, Jowi's better fit ("Buna geç") and what goes with the job ("+ Ekle"). */
@Composable
private fun CardState(
    item: PLItem, card: PLUseCard, added: Set<String>, keyOf: (String, PLUseComplement) -> String, busy: Boolean,
    onSwitch: (PLUseAlternative) -> Unit, onAdd: (PLUseComplement) -> Unit,
) {
    val colors = WorkisTheme.colors
    val use = item.use?.text?.takeIf { it.isNotBlank() } ?: card.use?.takeIf { it.isNotBlank() }
    use?.let { Text(stringResource(R.string.use_label) + ": " + it, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = colors.ink) }
    card.reason?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }

    // the server's word after "Buna geç" (ledger 67): the alternative is gone from the card,
    // `switched {from, to}` names the move — a tick line where the alternative stood, never a second button
    card.switched?.let { sw ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.use_switched), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuccessGreen)
            sw.to?.takeIf { it.isNotBlank() }?.let { Text("· $it", fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

    card.alternative?.takeIf { !it.cpid.isNullOrEmpty() || !it.name.isNullOrEmpty() }?.let { alt ->
        StageEyebrow(stringResource(R.string.use_better_title))
        Column(
            Modifier.fillMaxWidth()
                .background(Kiremit400.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .border(1.dp, Kiremit400.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(alt.name.orEmpty(), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            alt.seller?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
            alt.why?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
            // the web's set sentence under the why (ledger 68)
            Text(stringResource(R.string.use_why_tell), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
            Spacer(Modifier.height(4.dp))
            if (!alt.cpid.isNullOrEmpty()) StageButton(stringResource(R.string.use_switch), Kiremit500, enabled = !busy) { onSwitch(alt) }
        }
    }

    val comps = card.complements.orEmpty().filter { !it.name.isNullOrBlank() }
    if (comps.isNotEmpty()) {
        StageEyebrow(stringResource(R.string.use_complements_title))
        comps.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.name.orEmpty(), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    val carried = c.inCatalogue == true || !c.cpid.isNullOrEmpty()
                    val where = if (carried) listOf(c.catalogueName.orEmpty(), c.seller.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                    else stringResource(R.string.row_free_text)
                    val second = where + if (c.role == "optional") " · " + stringResource(R.string.use_optional) else ""
                    if (second.isNotBlank()) Text(second.removePrefix(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    c.why?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
                }
                if (keyOf(item.id.orEmpty(), c) in added) Done(stringResource(R.string.use_added), muted = true)
                else StageButton("+ " + stringResource(R.string.use_add), colors.ink, enabled = !busy) { onAdd(c) }
            }
        }
    }
}

@Composable
private fun StageEyebrow(text: String) {
    Text(text.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp, color = WorkisTheme.colors.faint, modifier = Modifier.padding(top = 4.dp))
}

/** A bordered (never filled) stage button — kiremit for Jowi's doors, ink for "+ Ekle". */
@Composable
private fun StageButton(label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        border = BorderStroke(1.dp, if (enabled) tint else colors.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint, disabledContentColor = colors.faint),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** A tick + words — "Bu ürüne geçildi" in green, "Eklendi" muted. */
@Composable
private fun Done(text: String, muted: Boolean = false) {
    val tint = if (muted) WorkisTheme.colors.muted else SuccessGreen
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (muted) WorkisTheme.colors.faint else SuccessGreen, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

// ---------- the row's Jowi line ----------

private enum class JowiBadge { Asking, Better, Fits, Added }

/**
 * Under "İstenen": the badge (a door to the stage — asking/better on drafts only, fits and
 * "Jowi ekledi" always) and "Kullanım: <text>". Every badge carries its word; colour never alone.
 */
@Composable
fun JowiRowLine(item: PLItem, card: PLUseCard?, draft: Boolean, onBadge: () -> Unit) {
    val colors = WorkisTheme.colors
    val use = item.use?.text?.trim().orEmpty()
    val kind = when {
        draft && item.askUse == true && card == null -> JowiBadge.Asking
        card?.verdict == "better" -> if (draft) JowiBadge.Better else JowiBadge.Fits
        card?.verdict == "fits" -> JowiBadge.Fits
        else -> null
    }
    val added = item.jowiAdded == true
    if (kind == null && !added && use.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kind?.let { k ->
            Badge(k, modifier = if (draft) Modifier.clickable(role = Role.Button, onClick = onBadge) else Modifier)
        }
        if (added) Badge(JowiBadge.Added)
        if (use.isNotEmpty()) Text(
            stringResource(R.string.use_label) + ": " + use, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Badge(kind: JowiBadge, modifier: Modifier = Modifier) {
    val colors = WorkisTheme.colors
    val pad = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    when (kind) {
        JowiBadge.Asking -> Text(
            stringResource(R.string.use_badge_asking), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Kiremit500, maxLines = 1,
            modifier = modifier.border(1.dp, Kiremit500, CircleShape).then(pad),
        )
        JowiBadge.Better -> Text(
            stringResource(R.string.use_badge_better), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OnKiremitFill, maxLines = 1,
            modifier = modifier.background(Kiremit400, CircleShape).then(pad),
        )
        JowiBadge.Fits -> Row(modifier.then(Modifier.padding(vertical = 3.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.use_badge_fits), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SuccessGreen, maxLines = 1)
        }
        JowiBadge.Added -> Text(
            stringResource(R.string.use_badge_added), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1,
            modifier = modifier.border(1.dp, colors.border, CircleShape).then(pad),
        )
    }
}
