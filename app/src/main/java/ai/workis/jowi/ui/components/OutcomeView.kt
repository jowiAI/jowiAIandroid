package ai.workis.jowi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * The one "it's done" screen (iOS WorkisOutcomeView): a big status glyph, a
 * mono title, one paragraph, an optional footnote and ONE primary action.
 * Apply sent, expert application sent, agreement accepted — all this shape.
 * Status is glyph + text, never colour alone; success green is a status
 * colour, the action stays kiremit.
 */
@Composable
fun OutcomeView(
    title: String,
    message: String,
    actionTitle: String,
    onAction: () -> Unit,
    footnote: String? = null,
    icon: ImageVector = Icons.Filled.CheckCircle,
    iconColor: Color = SuccessGreen,
) {
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
            color = colors.ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(message, fontSize = 15.sp, lineHeight = 22.sp, color = colors.muted, textAlign = TextAlign.Center)
        footnote?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.height(48.dp),
        ) {
            Text(actionTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}
