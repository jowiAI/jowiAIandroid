package ai.workis.jowi.ui.screens.decisions

import ai.workis.jowi.R
import ai.workis.jowi.data.Decision
import ai.workis.jowi.data.DecisionRecipient
import ai.workis.jowi.data.JowiWorkspace
import ai.workis.jowi.ui.screens.clockOf
import ai.workis.jowi.ui.screens.dayOf
import ai.workis.jowi.ui.screens.partner.ConfirmDialog
import ai.workis.jowi.ui.screens.partner.SurfaceCard
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Kararlar — the Jowi hospitality surface (ledgers 20–33; iOS `JowiDecisionsView` +
 * `JowiDecisionWriter`). The record on top, newest at the BOTTOM; the live turn's
 * bubbles at its foot; the composer under it.
 *
 * THE POINT OF THE SCREEN IS THE DELIVERY STATE (told · not told · still going),
 * icon + words, never colour alone. THE WRITTEN RECORD IS WHOLE: no delete, no edit;
 * "Geri çek" only while the server says `canRetract`, and the row stays with its trace.
 * Server sentences (`note`, `question`, `deliveryError`, refusal `error`) are drawn as
 * they arrive. The one tinted control is the composer's send button.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DecisionsScreen(workspace: JowiWorkspace?) {
    val vm: DecisionsViewModel = viewModel()
    val colors = WorkisTheme.colors
    vm.stError = stringResource(R.string.st_error)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var retractTarget by remember { mutableStateOf<Decision?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.load() }
    // the conversation never survives leaving the tab (the VM is activity-scoped)
    DisposableEffect(Unit) { onDispose { vm.closeConversation() } }
    LaunchedEffect(vm.focusTick) { if (vm.focusTick > 0) runCatching { focus.requestFocus() } }

    val skipping = vm.inConversation
    val shown = vm.decisions.filter { it.id == null || it.id !in skipping }
    val writing = vm.sending != null || vm.thread.isNotEmpty()
    val onRetract: (Decision) -> Unit = { retractTarget = it }
    fun reply(turnId: String?): (ai.workis.jowi.data.DecisionTurn) -> Unit = { vm.startReply(it, turnId) }

    // the turn in progress — at most one of these at a time
    val currentKind = when {
        vm.answering != null -> "answering"
        vm.asking != null -> "asking"
        vm.clarify != null -> "clarify"
        vm.sending != null -> "sending"
        vm.previewing && vm.pending != null -> "thinking"
        else -> null
    }

    // The newest is the one she came for: scroll to the bottom on load and whenever the
    // count or the current card changes. One pass is not enough (a card is measured after
    // it lands; the keyboard shrinks the viewport) — scroll, let the frame settle, again.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(vm.loaded, shown.size, vm.thread.size, currentKind, vm.asking?.preview?.questions?.size, vm.settled, vm.actionError, imeVisible) {
        if (!vm.loaded) return@LaunchedEffect
        repeat(3) {
            val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(last)
            if (!listState.canScrollForward) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        // The hotel's name only. `logoMarkUrl` exists but is NOT drawn: the server serves
        // it as SVG (iOS draws nothing either until a raster rendition lands).
        Text(
            workspace?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.decisions_tab),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!vm.loaded) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Kiremit500)
                    }
                }
            } else if (shown.isEmpty() && !writing && currentKind == null) {
                item(key = "empty") {
                    SurfaceCard {
                        Text(stringResource(R.string.decision_empty), fontSize = 15.sp, lineHeight = 21.sp, color = colors.muted)
                    }
                }
            }
            vm.loadError?.let { e ->
                item(key = "loadError") { StatusLine(WorkisIcons.Warning, e, Beige, 13) }
            }
            // newest at the bottom, day-broken — a record answers "when did I decide this"
            shown.forEachIndexed { i, d ->
                val day = dayOf(d.at)
                val prevDay = if (i > 0) dayOf(shown[i - 1].at) else null
                item(key = "d-${d.id ?: i}") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (day.isNotEmpty() && day != prevDay) {
                            Text(
                                day, fontFamily = WorkisMono, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                color = colors.faint, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                        DecisionRow(d, onRetract)
                    }
                }
            }

            // the conversation, in the order it happened
            vm.thread.forEachIndexed { i, entry ->
                item(key = "t-${entry.id}") {
                    when (entry) {
                        is DecisionsViewModel.Item.Answer -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HerBubble(entry.typed, entry.quote, menu = TurnMenu("user", entry.typed, reply(entry.id)))
                            AnsweringCard(
                                entry.entry,
                                last = i == vm.thread.lastIndex && currentKind == null,
                                menu = TurnMenu("jowi", entry.entry.said, reply(entry.id)),
                                onOption = {}, // an answered turn has no options
                                onDone = vm::closeConversation,
                            )
                        }
                        is DecisionsViewModel.Item.Record -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HerBubble(entry.typed, entry.quote)
                            // the live ledger card, freshest copy by id
                            DecisionRow(vm.decisions.firstOrNull { it.id != null && it.id == entry.decision.id } ?: entry.decision, onRetract)
                        }
                    }
                }
            }

            // the turn in progress
            if (currentKind != null) {
                item(key = "current-$currentKind") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val answering = vm.answering
                        val asking = vm.asking
                        val clarify = vm.clarify
                        val sending = vm.sending
                        when {
                            answering != null -> {
                                HerBubble(answering.typed, answering.quote)
                                AnsweringCard(
                                    answering.entry, last = true, menu = null,
                                    onOption = { vm.resolveUnsure(answering, it) },
                                    onDone = vm::closeConversation,
                                )
                            }
                            asking != null -> {
                                HerBubble(asking.typed)
                                QuestionsCard(
                                    asking.preview, vm.answers, vm.settled, vm.settling,
                                    onAnswer = vm::answer,
                                    onContinue = { vm.continueFrom(asking) },
                                    onBack = { vm.backFromAsking(asking) },
                                    onDuplicateNo = vm::closeAsking,
                                )
                            }
                            clarify != null -> {
                                HerBubble(clarify.typed)
                                ClarifyCard(
                                    clarify.reading, clarify.preview,
                                    onUseReading = { vm.useReading(clarify) },
                                    onUseOwn = { vm.useOwn(clarify) },
                                    onBack = { vm.backFromClarify(clarify) },
                                )
                            }
                            sending != null -> {
                                HerBubble(sending.typed)
                                SendingRow(sending.headline, sending.resolved, sending.recipients)
                            }
                            else -> {
                                HerBubble(vm.pending.orEmpty(), vm.pendingQuote)
                                ThinkingBubble()
                            }
                        }
                    }
                }
            }

            vm.actionError?.let { e ->
                item(key = "actionError") { StatusLine(WorkisIcons.Warning, e, colors.danger, 13) }
            }
            if (vm.loaded && !vm.canWrite) {
                item(key = "readOnly") {
                    Text(stringResource(R.string.decision_read_only), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                }
            }
            item(key = "foot") { Spacer(Modifier.height(6.dp)) }
        }

        if (vm.canWrite) {
            vm.replyTo?.let { r -> ReplyStrip(r.text) { vm.replyTo = null } }
            Composer(vm, focus) { focusManager.clearFocus(); vm.submit() }
        }
    }

    retractTarget?.let { d ->
        // "Geri çek", never "sil" — nothing is destroyed, so no destructive styling
        ConfirmDialog(
            title = stringResource(R.string.decision_retract),
            message = stringResource(R.string.decision_retract_confirm),
            destructive = false,
            onDismiss = { retractTarget = null },
            onConfirm = { vm.retract(d) },
        )
    }
}

/** One line and a send — the WhatsApp bar this screen has to beat. The send is the ONE tinted control. */
@Composable
private fun Composer(vm: DecisionsViewModel, focus: FocusRequester, onSend: () -> Unit) {
    val colors = WorkisTheme.colors
    val inFlight = vm.previewing || vm.sending != null
    val enabled = vm.draft.text.isNotBlank() && !vm.busy
    val sendLabel = stringResource(R.string.send)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        OutlinedTextField(
            value = vm.draft,
            onValueChange = { vm.draft = it },
            placeholder = { Text(stringResource(R.string.decision_prompt), color = colors.faint) },
            minLines = 1,
            maxLines = 5,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
            ),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(40.dp)
                .alpha(if (enabled || inFlight) 1f else 0.4f)
                .background(Kiremit400, CircleShape)
                .clickable(enabled = enabled, onClick = onSend)
                .semantics { contentDescription = sendLabel },
        ) {
            if (inFlight) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
            } else {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = OnKiremitFill, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** One decision: the sentence, what Jowi resolved, and whether the staff were told. A withdrawn row keeps everything and gains its trace. */
@Composable
private fun DecisionRow(d: Decision, onRetract: (Decision) -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ink.copy(alpha = 0.06f), shape)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
    ) {
        // the clean sentence leads; her own words underneath when they differ
        val headline = d.headline
        Text(headline, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink)
        d.ownWords?.let { own ->
            if (own != headline) {
                Text(
                    stringResource(R.string.clarify_you_wrote) + " · " + own,
                    fontFamily = WorkisMono, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint,
                )
            }
        }
        ResolvedChips(d.reading)
        if (d.degraded == true) StatusLine(WorkisIcons.Warning, stringResource(R.string.decision_degraded), Beige, 12)
        d.note?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Beige) }
        d.trail?.takeIf { it.isNotEmpty() }?.let { TrailBlock(it) }
        Delivery(d)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(clockOf(d.at), fontFamily = WorkisMono, fontSize = 9.sp, color = colors.faint)
            Spacer(Modifier.weight(1f))
            // the server's word, never derived from seenAt here
            if (d.canRetract == true && !d.withdrawn) {
                Text(
                    stringResource(R.string.decision_retract),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText,
                    modifier = Modifier.clickable { onRetract(d) }.padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
        if (d.withdrawn) RetractedTrace(d)
    }
}

/** Told / not told / still going — icon + words, never colour alone. */
@Composable
private fun Delivery(d: Decision) {
    val colors = WorkisTheme.colors
    when (d.delivery) {
        Decision.Delivery.Told -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val at = clockOf(d.deliveredAt).takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
            StatusLine(WorkisIcons.Check, stringResource(R.string.decision_delivered) + at, SuccessGreen, 12, FontWeight.Medium)
            names(d.recipients)?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted) }
        }
        Decision.Delivery.Failed -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StatusLine(WorkisIcons.Warning, stringResource(R.string.decision_not_delivered), colors.danger, 12, FontWeight.Medium)
            // the server's sentence, verbatim
            Text(d.deliveryError.orEmpty(), fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted)
        }
        Decision.Delivery.Going ->
            StatusLine(WorkisIcons.Clock, stringResource(R.string.decision_sending), Beige, 12, FontWeight.Medium)
    }
}

private fun names(recipients: List<DecisionRecipient>?): String? =
    recipients.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotEmpty() } }.takeIf { it.isNotEmpty() }?.joinToString(", ")

/** The withdrawal's trace: the content is NOT removed — the trace says what happened and when. */
@Composable
private fun RetractedTrace(d: Decision) {
    val colors = WorkisTheme.colors
    val at = clockOf(d.retractedAt).takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dashedFrame(colors.faint.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(WorkisIcons.Undo, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.decision_retracted_trace) + at,
            fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = colors.muted,
        )
    }
}

/** Icon + words in one colour — every status line on this screen. */
@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, size: Int, weight: FontWeight? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size + 2).dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = size.sp, lineHeight = (size + 5).sp, fontWeight = weight, color = color)
    }
}

private fun Modifier.dashedFrame(color: Color) = this.drawBehind {
    val dash = 4.dp.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        cornerRadius = CornerRadius(12.dp.toPx()),
    )
}
