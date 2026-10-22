package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.SatisfactionAsk
import ai.workis.jowi.data.SatisfactionBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The per-line state of the satisfaction tick (ledger 54), kept by the screen's
 * ViewModel: which lines are posting, which answer the server took, its thanks
 * line, its refusal. A second tick on the same line simply posts again — the
 * server keeps the last word.
 */
class SatisfactionTicks {
    val busy = mutableStateMapOf<String, Boolean>()
    val chosen = mutableStateMapOf<String, String>()
    val thanks = mutableStateMapOf<String, String>()
    val error = mutableStateMapOf<String, String>()

    /** `POST orders/lines/{id}/satisfaction/` → `message` is the thanks, printed as sent. */
    fun post(scope: CoroutineScope, lineId: String, value: String, note: String, reason: String?) {
        if (busy[lineId] == true) return
        busy[lineId] = true
        error.remove(lineId)
        scope.launch {
            val (r, err) = partnerCall(Graph.language.value) {
                Graph.api.lineSatisfaction(lineId, SatisfactionBody(value, note.trim().ifEmpty { null }?.take(400), reason))
            }
            busy.remove(lineId)
            if (r?.success == true) {
                chosen[lineId] = value
                r.message?.takeIf { it.isNotEmpty() }?.let { thanks[lineId] = it } ?: thanks.remove(lineId)
            } else {
                error[lineId] = r?.error ?: r?.message ?: err ?: ""
            }
        }
    }
}

/**
 * "Teslimat nasıldı?" — the buyer's private one-tap answer on an accepted line,
 * drawn right after a line confirmation (the confirm reply's `satisfaction`) and
 * on Onaylar's pending rows. Every word is the server's (`question`, `private`,
 * the option and reason `label`s); the thanks line is the POST's `message`.
 * Three bordered answers — the chosen one wears a solid kiremit border, never a
 * fill; a reason chip toggles (tick + text) and, once an answer is in, re-sends it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SatisfactionCard(
    ask: SatisfactionAsk,
    privateFallback: String,
    busy: Boolean,
    chosen: String?,
    thanks: String?,
    error: String? = null,
    showQuestion: Boolean = true,
    onPick: (value: String, note: String, reason: String?) -> Unit,
) {
    val colors = WorkisTheme.colors
    val key = ask.lineId.orEmpty()
    var note by rememberSaveable(key) { mutableStateOf("") }
    var reason by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val stError = stringResource(R.string.st_error)

    SurfaceCard {
        if (showQuestion) {
            Text(
                ask.question?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.satisfaction_title),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ask.po?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                Spacer(Modifier.width(8.dp))
            }
            ask.name?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 13.sp, color = colors.muted, maxLines = 2)
            }
        }
        ask.seller?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(WorkisIcons.Lock, contentDescription = null, tint = colors.faint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(ask.private?.takeIf { it.isNotEmpty() } ?: privateFallback, fontSize = 11.sp, color = colors.faint)
        }
        // the three answers — bordered ("glass"); selection = a solid kiremit border, nothing else
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ask.options.orEmpty().forEach { o ->
                val value = o.value ?: return@forEach
                val on = chosen == value
                Text(
                    o.label?.takeIf { it.isNotEmpty() } ?: value,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (busy) colors.faint else colors.ink,
                    modifier = Modifier
                        .border(if (on) 1.5.dp else 1.dp, if (on) Kiremit500 else colors.border, RoundedCornerShape(50))
                        .clickable(enabled = !busy) { onPick(value, note, reason) }
                        .defaultMinSize(minHeight = 32.dp)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        // one toggle chip per reason — a product lesson, never a word against the seller;
        // the chosen code travels as `reason`, and a toggle after the tick re-sends the tick
        val reasons = ask.reasons.orEmpty().filter { !it.code.isNullOrEmpty() }
        if (reasons.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { r ->
                    val code = r.code!!
                    val on = reason == code
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, if (on) colors.ink else colors.border, RoundedCornerShape(50))
                            .clickable(enabled = !busy) {
                                reason = if (on) null else code
                                chosen?.let { onPick(it, note, reason) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        if (on) {
                            Icon(WorkisIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            r.label?.takeIf { it.isNotEmpty() } ?: code,
                            fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) colors.ink else colors.muted,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(400) },
            placeholder = { Text(stringResource(R.string.satisfaction_note), fontSize = 13.sp, color = colors.faint) },
            minLines = 1, maxLines = 3,
            textStyle = TextStyle(fontSize = 13.sp, color = colors.ink).readingStep(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        thanks?.let { Status(it, WorkisIcons.Check, SuccessGreen) }
        error?.let { Status(it.ifEmpty { stError }, WorkisIcons.Warning, colors.danger) }
    }
}

@Composable
private fun Status(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = tint)
    }
}
