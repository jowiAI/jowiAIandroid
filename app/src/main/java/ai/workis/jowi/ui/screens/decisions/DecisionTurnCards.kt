package ai.workis.jowi.ui.screens.decisions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import ai.workis.jowi.data.DecisionEntry
import ai.workis.jowi.data.DecisionPreview
import ai.workis.jowi.data.DecisionQuestion
import ai.workis.jowi.data.DecisionRecipient
import ai.workis.jowi.data.DecisionResolved
import ai.workis.jowi.data.DecisionTurn
import ai.workis.jowi.ui.theme.Text

/*
 * Kararlar — the TURN cards (ledgers 20–33). The conversation half of the screen:
 * her bubble, Jowi's bubble and what each of Jowi's bubbles holds. Pure composables
 * — state and doors live in DecisionsViewModel (DecisionsScreen.kt). The signatures
 * below are the contract between the two files; keep them.
 *
 * STUB — the bodies are placeholders until the slice lands.
 */

/** Long-press on an ANSWERED turn's bubble (ledger 31): Kopyala (clipboard, done here) · Yanıtla → `onReply(DecisionTurn(role, text))`. */
data class TurnMenu(val role: String, val text: String, val onReply: (DecisionTurn) -> Unit)

/** Her bubble on the right — the yazışma's own-message style exactly (ledger 30); the quote (bar + 2 lines) above her words. */
@Composable
fun HerBubble(typed: String, quote: String? = null, menu: TurnMenu? = null) { Text(typed) }

/** Jowi's bubble on the left: neutral `ink` 6 %, radius 18, no stroke; a trailing gap of 24 dp. */
@Composable
fun JowiBubble(menu: TurnMenu? = null, content: @Composable ColumnScope.() -> Unit) { androidx.compose.foundation.layout.Column { content() } }

/** The bubble being answered, as a messaging app quotes it: a 3 dp faint bar and at most two lines. */
@Composable
fun QuoteLine(text: String) { Text(text) }

/** Only what the server resolved; an absent field draws nothing (operator · contract · reservation · discount · dates). */
@Composable
fun ResolvedChips(resolved: DecisionResolved?) {}

/** "Dayanak" (ledger 33): per turn a 3 dp bar (kiremit 55 % hers / faint Jowi's), the speaker, the text — muted. */
@Composable
fun TrailBlock(trail: List<DecisionTurn>) {}

/** Who will be told — `decisionRecipients` eyebrow + `name · role · via` lines. Draws nothing on an empty list. */
@Composable
fun RecipientsBlock(recipients: List<DecisionRecipient>) {}

/** Jowi's bubble while it reads: a spinner. */
@Composable
fun ThinkingBubble() {}

/**
 * A turn that did not become a record: `question` → answer, note, Tamam (only when `last`) ·
 * `unsure` → the server's question + its options (first = the tinted one) → `onOption(value)`.
 */
@Composable
fun AnsweringCard(entry: DecisionEntry, last: Boolean, menu: TurnMenu?, onOption: (String) -> Unit, onDone: () -> Unit) {}

/**
 * The questions card (ledgers 24–26, 29): questions + pickers (nothing pre-selected; "Bilmiyorum" = "" ;
 * free text when `options == null`; `key == "duplicate"` = two plain buttons — "" → `onDuplicateNo`),
 * then `settled`'s restated + chips (spinner while `settling`, else the preview's chips only), trail,
 * note, recipients, Devam (disabled until every question is answered and not settling) / Düzenlemeye dön.
 */
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
) {}

/** The two readings (a MATERIAL rewrite, the one place she is asked): reading, chips, trail, note, recipients, "Bu okuma" / "Kendi sözlerim" / Düzenlemeye dön. */
@Composable
fun ClarifyCard(reading: String, preview: DecisionPreview?, onUseReading: () -> Unit, onUseOwn: () -> Unit, onBack: () -> Unit) {}

/** Jowi's side while the record is written: the headline (if any) + chips + recipients + "İletiliyor…" with a small spinner. */
@Composable
fun SendingRow(headline: String?, resolved: DecisionResolved?, recipients: List<DecisionRecipient>) {}

/** Above the field while replying: "Yanıtlanıyor" + the quote + an × that drops it. */
@Composable
fun ReplyStrip(quote: String, onDrop: () -> Unit) {}
