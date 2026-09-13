package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.PLCompliance
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/** Status pill: text + quiet well, never colour alone. */
@Composable
fun StatusPill(text: String, tint: Color = WorkisTheme.colors.muted) {
    Text(
        text.uppercase(),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = tint,
        modifier = Modifier.background(WorkisTheme.colors.ink.copy(alpha = 0.06f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** "✓m ✗f ?u" — compliance counts in mono; the fails digit is the only red. */
@Composable
fun ComplianceChip(c: PLCompliance?) {
    if (c == null) return
    val colors = WorkisTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("✓${c.meets ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = SuccessGreen)
        Text("✗${c.fails ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = if ((c.fails ?: 0) > 0) colors.danger else colors.faint)
        Text("?${c.unclear ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun Eyebrow(text: String) {
    Text(text, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = WorkisTheme.colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun listStatusText(s: String?): String = stringResource(
    when (s) { "quoting" -> R.string.ls_quoting; "ordered" -> R.string.ls_ordered; "closed" -> R.string.ls_closed; else -> R.string.ls_draft },
)

@Composable
fun listStageText(s: String?): String? = when (s) {
    "parsing" -> stringResource(R.string.st_parsing)
    "matching" -> stringResource(R.string.st_matching)
    "compliance" -> stringResource(R.string.st_compliance)
    "error" -> stringResource(R.string.st_error)
    else -> null
}

@Composable
fun inviteStatusText(s: String?): String = when (s) {
    "quoted" -> stringResource(R.string.st_quoted)
    "declined" -> stringResource(R.string.st_declined)
    "emailed" -> stringResource(R.string.st_emailed)
    "parsed" -> stringResource(R.string.st_parsed)
    "invited", "viewed" -> stringResource(R.string.st_invited)
    else -> s.orEmpty()
}
