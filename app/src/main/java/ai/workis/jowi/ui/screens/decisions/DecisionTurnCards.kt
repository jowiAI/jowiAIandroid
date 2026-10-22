package ai.workis.jowi.ui.screens.decisions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.DecisionEntry
import ai.workis.jowi.data.DecisionPreview
import ai.workis.jowi.data.DecisionQuestion
import ai.workis.jowi.data.DecisionRecipient
import ai.workis.jowi.data.DecisionResolved
import ai.workis.jowi.data.DecisionTurn
import ai.workis.jowi.ui.screens.partner.ChipSpec
import ai.workis.jowi.ui.screens.partner.WorkisChip
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep

/*
 * Kararlar — the TURN cards (ledgers 20–33). The conversation half of the screen:
 * her bubble, Jowi's bubble and what each of Jowi's bubbles holds. Pure composables
 * — state and doors live in DecisionsViewModel (DecisionsScreen.kt). The signatures
 * below are the contract between the two files; keep them.
 *
 * Mirrors iOS `JowiDecisionWriter.swift` (turn, quoteLine, TurnMenu, answeringCard,
 * optionButton, sendingRow, recipientsBlock, understood, questionsCard,
 * duplicateChoice, clarifyCard, the composer's reply strip) and
 * `JowiDecisionsView.swift` (JowiResolvedChips, JowiTrailBlock).
 */

/** Long-press on an ANSWERED turn's bubble (ledger 31): Kopyala (clipboard, done here) · Yanıtla → `onReply(DecisionTurn(role, text))`. */
data class TurnMenu(val role: String, val text: String, val onReply: (DecisionTurn) -> Unit)

private val BubbleShape = RoundedCornerShape(18.dp)

/** Her bubble on the right — the yazışma's own-message style exactly (ledger 30); the quote (bar + 2 lines) above her words. */
@Composable
fun HerBubble(typed: String, quote: String? = null, menu: TurnMenu? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.width(48.dp))
        TurnMenuBox(menu, Modifier.weight(1f, fill = false)) { press ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(Kiremit500.copy(alpha = 0.08f), BubbleShape)
                    .border(1.dp, Kiremit500.copy(alpha = 0.55f), BubbleShape)
                    .then(press)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (!quote.isNullOrBlank()) QuoteLine(quote)
                Text(typed, fontSize = 15.sp, lineHeight = 21.sp, color = WorkisTheme.colors.ink)
            }
        }
    }
}

/** Jowi's bubble on the left: neutral `ink` 6 %, radius 18, no stroke; a trailing gap of 24 dp. */
@Composable
fun JowiBubble(menu: TurnMenu? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth()) {
        TurnMenuBox(menu, Modifier.weight(1f)) { press ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.ink.copy(alpha = 0.06f), BubbleShape)
                    .then(press)
                    .padding(14.dp),
                content = content,
            )
        }
        Spacer(Modifier.width(24.dp))
    }
}

/**
 * The long-press menu host (iOS `TurnMenu`): no menu at all on a turn not answered yet,
 * nor on an empty text — no empty lift. `content` receives the press modifier for the bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnMenuBox(menu: TurnMenu?, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val colors = WorkisTheme.colors
    val active = menu != null && menu.text.isNotEmpty()
    var open by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    Box(modifier) {
        content(if (active) Modifier.combinedClickable(onClick = {}, onLongClick = { open = true }) else Modifier)
        if (active && menu != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    leadingIcon = { Icon(WorkisIcons.Copy, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp)) },
                    text = { Text(stringResource(R.string.decision_turn_copy), color = colors.ink, fontSize = 14.sp) },
                    onClick = { open = false; clipboard.setText(AnnotatedString(menu.text)) },
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(WorkisIcons.Reply, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp)) },
                    text = { Text(stringResource(R.string.decision_turn_reply), color = colors.ink, fontSize = 14.sp) },
                    onClick = { open = false; menu.onReply(DecisionTurn(role = menu.role, text = menu.text)) },
                )
            }
        }
    }
}

/** The bubble being answered, as a messaging app quotes it: a 3 dp faint bar and at most two lines. */
@Composable
fun QuoteLine(text: String) {
    val colors = WorkisTheme.colors
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.faint, RoundedCornerShape(1.5.dp)))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Only what the server resolved; an absent field draws nothing (operator · contract · reservation · discount · dates). */
@Composable
fun ResolvedChips(resolved: DecisionResolved?) {
    val r = resolved ?: return
    if (r.isEmpty) return
    val tint = WorkisTheme.colors.muted
    val chips = buildList {
        r.operator?.name?.takeIf { it.isNotEmpty() }?.let { add(ChipSpec(WorkisIcons.Building, it, tint)) }
        r.contract?.display?.let { add(ChipSpec(WorkisIcons.DocText, it, tint)) }
        r.reservation?.display?.let { add(ChipSpec(WorkisIcons.Bed, it, tint)) }
        r.discount?.display?.let { add(ChipSpec(WorkisIcons.Percent, it, tint)) }
        r.dates?.display?.let { add(ChipSpec(WorkisIcons.Calendar, it, tint)) }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 1.dp),
    ) {
        chips.forEach { WorkisChip(it) }
    }
}

/** "Dayanak" (ledger 33): per turn a 3 dp bar (kiremit 55 % hers / faint Jowi's), the speaker, the text — muted. */
@Composable
fun TrailBlock(trail: List<DecisionTurn>) {
    if (trail.isEmpty()) return
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.decision_trail), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted)
        trail.forEach { turn ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.width(3.dp).fillMaxHeight()
                        .background(if (turn.isJowi) colors.faint else Kiremit500.copy(alpha = 0.55f), RoundedCornerShape(1.5.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(if (turn.isJowi) R.string.decision_trail_jowi else R.string.decision_trail_you),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.muted,
                    )
                    Text(turn.text.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                }
            }
        }
    }
}

/** Who will be told — `decisionRecipients` eyebrow + `name · role · via` lines. Draws nothing on an empty list. */
@Composable
fun RecipientsBlock(recipients: List<DecisionRecipient>) {
    if (recipients.isEmpty()) return
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            stringResource(R.string.decision_recipients),
            fontFamily = WorkisMono, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = colors.faint,
        )
        recipients.forEach { r -> Text(r.line, fontSize = 12.sp, color = colors.muted) }
    }
}

/** Jowi's bubble while it reads: a spinner. */
@Composable
fun ThinkingBubble() {
    JowiBubble { SmallSpinner() }
}

/**
 * A turn that did not become a record: `question` → answer, note, Tamam (only when `last`) ·
 * `unsure` → the server's question + its options (first = the tinted one) → `onOption(value)`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnsweringCard(entry: DecisionEntry, last: Boolean, menu: TurnMenu?, onOption: (String) -> Unit, onDone: () -> Unit) {
    val colors = WorkisTheme.colors
    JowiBubble(menu) {
        // her sentence is in her bubble above; the server's own question (unsure) is the only headline here
        val q = entry.question
        if (entry.isUnsure && !q.isNullOrBlank()) {
            Text(q, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = colors.ink)
        }
        entry.answer?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink) }
        entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        val options = entry.options.orEmpty()
        if (options.isNotEmpty()) {
            // the server's own doors, its own words on them — the first carries the tint
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, o ->
                    if (i == 0) ProminentButton(o.text) { onOption(o.value) }
                    else BorderedButton(o.text) { onOption(o.value) }
                }
            }
        } else if (last) {
            PlainButton(stringResource(R.string.done), fontSize = 14.sp, weight = FontWeight.Medium, onClick = onDone)
        }
    }
}

/**
 * The questions card (ledgers 24–26, 29): questions + pickers (nothing pre-selected; "Bilmiyorum" = "" ;
 * free text when `options == null`; `key == "duplicate"` = two plain buttons — "" → `onDuplicateNo`),
 * then `settled`'s restated + chips (spinner while `settling`, else the preview's chips only), trail,
 * note, recipients, Devam (disabled until every question is answered and not settling) / Düzenlemeye dön.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionsCard(
    preview: DecisionPreview,
    answers: Map<String, String>,
    settled: DecisionPreview?,
    settling: Boolean,
    onAnswer: (DecisionQuestion, String?) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onDuplicateNo: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val questions = preview.questions.orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().background(BeigeBg, BubbleShape).padding(14.dp),
    ) {
        Eyebrow(stringResource(R.string.decision_questions_title))
        questions.forEach { q ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(q.question ?: q.key, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                val options = q.options.orEmpty()
                when {
                    q.key == "duplicate" -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { o ->
                            val ticked = answers[q.key] == o.value && o.value.isNotEmpty()
                            BorderedButton(o.text, icon = if (ticked) WorkisIcons.Check else null) {
                                if (o.value.isEmpty()) onDuplicateNo() else onAnswer(q, o.value)
                            }
                        }
                    }
                    options.isEmpty() -> AnswerField(answers[q.key].orEmpty()) { onAnswer(q, it) }
                    else -> AnswerPicker(q, answers[q.key], onAnswer)
                }
            }
        }
        // The reading made BEFORE her answers could contradict her pick: the sentence is drawn
        // only once the server has said it again with her answers in it (`settled`).
        when {
            settled != null -> {
                settled.restated?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                }
                ResolvedChips(settled.resolved)
            }
            settling -> SmallSpinner()
            else -> ResolvedChips(preview.resolved)
        }
        (settled ?: preview).trail?.takeIf { it.isNotEmpty() }?.let { TrailBlock(it) }
        preview.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Beige) }
        RecipientsBlock((settled ?: preview).recipients.orEmpty())
        val answered = questions.all { answers.containsKey(it.key) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProminentButton(stringResource(R.string.decision_questions_continue), enabled = answered && !settling, onClick = onContinue)
            PlainButton(stringResource(R.string.clarify_back_to_edit), fontSize = 13.sp, onClick = onBack)
        }
    }
}

/** The two readings (a MATERIAL rewrite, the one place she is asked): reading, chips, trail, note, recipients, "Bu okuma" / "Kendi sözlerim" / Düzenlemeye dön. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyCard(reading: String, preview: DecisionPreview?, onUseReading: () -> Unit, onUseOwn: () -> Unit, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(BeigeBg, BubbleShape).padding(14.dp),
    ) {
        Eyebrow(stringResource(R.string.clarify_title))
        // her words are in her bubble; Jowi's reading is the one sentence here
        Text(reading, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        ResolvedChips(preview?.resolved)
        preview?.trail?.takeIf { it.isNotEmpty() }?.let { TrailBlock(it) }
        preview?.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Beige) }
        RecipientsBlock(preview?.recipients.orEmpty())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProminentButton(stringResource(R.string.clarify_use_reading), onClick = onUseReading)
            BorderedButton(stringResource(R.string.clarify_use_own), onClick = onUseOwn)
        }
        // neither reading: the typed words go back to be corrected
        PlainButton(stringResource(R.string.clarify_back_to_edit), fontSize = 13.sp, onClick = onBack)
    }
}

/** Jowi's side while the record is written: the headline (if any) + chips + recipients + "İletiliyor…" with a small spinner. */
@Composable
fun SendingRow(headline: String?, resolved: DecisionResolved?, recipients: List<DecisionRecipient>) {
    val colors = WorkisTheme.colors
    JowiBubble {
        headline?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        }
        ResolvedChips(resolved)
        RecipientsBlock(recipients)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallSpinner()
            Text(stringResource(R.string.decision_sending), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Beige)
        }
    }
}

/** Above the field while replying: "Yanıtlanıyor" + the quote + an × that drops it. */
@Composable
fun ReplyStrip(quote: String, onDrop: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.decision_turn_replying), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted)
            QuoteLine(quote)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDrop, modifier = Modifier.size(32.dp)) {
            Icon(WorkisIcons.Cross, contentDescription = stringResource(R.string.cancel), tint = colors.faint, modifier = Modifier.size(20.dp))
        }
    }
}

// MARK: - Private pieces

@Composable
private fun Eyebrow(text: String) {
    Text(text, fontFamily = WorkisMono, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = Beige)
}

@Composable
private fun SmallSpinner() {
    CircularProgressIndicator(color = Kiremit500, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
}

private val PillShape = RoundedCornerShape(50)

/** The card's one tinted action (iOS `WorkisGlassProminent`): Kiremit400 fill, 36 dp, radius 50; disabled = alpha 0.45. */
@Composable
private fun ProminentButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 36.dp)
            .background(Kiremit400, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnKiremitFill, maxLines = 2)
    }
}

/** An untinted door (iOS `WorkisGlassButton(tint: muted)`): 1 dp border, muted words, an optional leading mark. */
@Composable
private fun BorderedButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 36.dp)
            .border(1.dp, colors.border, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 2)
    }
}

/** A plain text button: no frame, muted. */
@Composable
private fun PlainButton(text: String, fontSize: TextUnit, weight: FontWeight? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.heightIn(min = 36.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
    ) {
        Text(text, fontSize = fontSize, fontWeight = weight, color = WorkisTheme.colors.muted)
    }
}

/** A question with no options takes a plain one-line field; every change is her answer. */
@Composable
private fun AnswerField(value: String, onChange: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 14.sp, color = colors.ink).readingStep(),
            cursorBrush = SolidColor(colors.blue),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(stringResource(R.string.decision_answer_placeholder), fontSize = 14.sp, color = colors.faint)
                inner()
            },
        )
    }
}

/**
 * One menu picker per question: nothing pre-selected (the placeholder is the state it opens in),
 * "Bilmiyorum" = "" is a real answer, the VALUE travels and the label is drawn.
 */
@Composable
private fun AnswerPicker(q: DecisionQuestion, picked: String?, onAnswer: (DecisionQuestion, String?) -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val unknown = stringResource(R.string.decision_pick_unknown)
    var open by remember(q.key) { mutableStateOf(false) }
    val options = q.options.orEmpty()
    val label = when (picked) {
        null -> null
        "" -> unknown
        else -> options.firstOrNull { it.value == picked }?.text ?: picked
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                label ?: stringResource(R.string.decision_pick_placeholder),
                fontSize = 14.sp,
                color = if (label == null) colors.faint else colors.accentText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", fontSize = 12.sp, color = colors.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(unknown, fontSize = 14.sp, color = colors.ink) },
                onClick = { open = false; onAnswer(q, "") },
            )
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.text, fontSize = 14.sp, color = colors.ink) },
                    onClick = { open = false; onAnswer(q, o.value) },
                )
            }
        }
    }
}
