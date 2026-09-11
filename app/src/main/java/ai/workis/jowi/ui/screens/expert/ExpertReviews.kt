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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.ReviewSession
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Kiremit300
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/** Dashed neutral empty box (iOS emptyBox). */
@Composable
fun EmptyBox(text: String) {
    val colors = WorkisTheme.colors
    val dash = colors.faint.copy(alpha = 0.5f)
    Text(
        text,
        fontSize = 13.sp, color = colors.faint, textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = dash, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(vertical = 18.dp, horizontal = 12.dp),
    )
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun GlassButton(label: String, busy: Boolean, enabled: Boolean, accent: Boolean = true, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(36.dp).border(1.dp, colors.border, CircleShape),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kiremit500)
        else Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (accent) colors.accentText else colors.muted)
    }
}

/** İncelemeler: flagged sessions (units as checkboxes → retire, correction, close). */
@Composable
fun ExpertReviewsScreen(vm: ExpertViewModel) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.loadReviews() }
    val retire = remember { mutableStateMapOf<String, Set<String>>() }
    val corrections = remember { mutableStateMapOf<String, String>() }
    val data = vm.reviews

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NoteBand(vm.actionNote)
        when {
            data != null -> {
                Text(stringResource(R.string.expert_reviews_sub), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted)
                val sessions = data.sessions.orEmpty()
                if (sessions.isEmpty()) EmptyBox(stringResource(R.string.expert_reviews_empty))
                sessions.forEach { s ->
                    val key = s.sessionKey ?: return@forEach
                    SessionCard(
                        s = s,
                        chosen = retire[key].orEmpty(),
                        onToggle = { id -> retire[key] = retire[key].orEmpty().let { if (id in it) it - id else it + id } },
                        correction = corrections[key].orEmpty(),
                        onCorrection = { corrections[key] = it },
                        busy = vm.busyKey == key,
                        enabled = vm.busyKey == null,
                    ) { vm.closeReview(key, retire[key].orEmpty().toList(), corrections[key].orEmpty().trim()) }
                }
                // product pairs ("Öneriler") moved to staff (2026-09-11) — no cards here
            }
            vm.reviewsError != null -> Text(vm.reviewsError.orEmpty(), color = colors.danger, fontSize = 13.sp)
            else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                WorkisMark(size = 26, breathing = true)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

private fun stamp(at: String?): String =
    if (at != null && at.length >= 16) at.take(16).replace("T", " ") else at.orEmpty()

@Composable
private fun SessionCard(
    s: ReviewSession,
    chosen: Set<String>,
    onToggle: (String) -> Unit,
    correction: String,
    onCorrection: (String) -> Unit,
    busy: Boolean,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    val colors = WorkisTheme.colors
    SurfaceCard {
        Text("“${s.question.orEmpty()}”", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.surface?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.uppercase(),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                    color = colors.muted,
                    modifier = Modifier.background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Text(stamp(s.at), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
        }
        // the units the answer stood on — checked = retire
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            s.units.orEmpty().forEach { u ->
                val id = u.id ?: return@forEach
                val on = id in chosen
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(id) },
                ) {
                    Checkbox(
                        checked = on, onCheckedChange = { onToggle(id) },
                        colors = CheckboxDefaults.colors(checkedColor = Kiremit300, checkmarkColor = OnKiremitFill, uncheckedColor = colors.faint),
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        buildString {
                            append(u.text.orEmpty())
                            u.authorRole?.let { append("  [$it]") }
                        },
                        fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink,
                        modifier = Modifier.weight(1f).padding(top = 6.dp),
                    )
                }
            }
        }
        OutlinedTextField(
            value = correction,
            onValueChange = onCorrection,
            placeholder = { Text(stringResource(R.string.expert_correction_placeholder), color = colors.faint, fontSize = 14.sp) },
            minLines = 2, maxLines = 6,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.expert_retire_hint), fontFamily = WorkisMono, fontSize = 10.sp, color = colors.faint, modifier = Modifier.weight(1f))
            GlassButton("✓ " + stringResource(R.string.expert_close_review), busy = busy, enabled = enabled, onClick = onClose)
        }
    }
}
