package ai.workis.jowi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ai.workis.jowi.R
import ai.workis.jowi.data.ChangeStamps
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay

/**
 * Re-reads a trade screen when ITS scope moved (`GET changes/`, 2026-10-03): one tick on
 * appear and every 20 s while the app is in the foreground — never in the background —
 * plus every push naming the scope. `scope` = `orders` | `quotes` | `cargo` | `lists`.
 *
 * Never under a reader who is typing: with `typing` true the change is held and this
 * draws the web's "yenile" pill instead — an uncoloured glass pill, tap = `onChange`.
 * Place it where the pill may sit (the top of the screen's column); it draws nothing
 * otherwise. The yazışma keeps its own 8 s poll and does not use this.
 */
@Composable
fun ChangesWatch(scope: String, typing: Boolean = false, onChange: () -> Unit) {
    var stale by remember(scope) { mutableStateOf(false) }
    val typingNow by rememberUpdatedState(typing)
    val reread by rememberUpdatedState(onChange)
    fun moved() { if (typingNow) stale = true else reread() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(scope, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (scope in ChangeStamps.tick()) moved()
                delay(20_000)
            }
        }
    }
    LaunchedEffect(scope) {
        ChangeStamps.pushes.collect { scopes -> if (scope in scopes) moved() }
    }
    if (stale) {
        val colors = WorkisTheme.colors
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.changes_pill),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink,
                modifier = Modifier
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { stale = false; reread() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
