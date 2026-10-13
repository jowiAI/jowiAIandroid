package ai.workis.jowi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.AskOption
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * "Which one do you mean?" (2026-09-17): one uncoloured capsule per option in
 * the server's order, stacked under the answer. A tap sends that option's `q`
 * with the same screen context and the set dims; the open-ended last one
 * (`q == null`) carries a pencil and hands the field back to the asker, with a
 * quiet hint under the live set. Shared by the Jowi tab and the public ask.
 */
@Composable
fun JowiOptions(
    options: List<AskOption>,
    picked: Boolean,
    enabled: Boolean = true,
    onPick: (AskOption) -> Unit,
) {
    if (options.isEmpty()) return
    val colors = WorkisTheme.colors
    val live = enabled && !picked
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp).alpha(if (picked) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val open = o.q.isNullOrEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable(enabled = live) { onPick(o) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (open) {
                    Icon(WorkisIcons.Pencil, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    o.label, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
                    color = if (open) colors.muted else colors.ink,
                )
            }
        }
        if (!picked && options.any { it.q.isNullOrEmpty() }) {
            Text(stringResource(R.string.jowi_option_other_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
        }
    }
}
