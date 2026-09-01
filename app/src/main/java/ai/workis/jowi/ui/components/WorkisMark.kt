package ai.workis.jowi.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * The /workis wordmark. Slash is always bold kiremit-500; wordmark in ink.
 * [breathing] = the brand loading idiom (0.75s ease-in-out, opacity 0.55..1.0, scale 0.96..1.06).
 */
@Composable
fun WorkisMark(
    modifier: Modifier = Modifier,
    size: Int = 32,
    breathing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "breath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (breathing) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathPhase",
    )
    val slashAlpha = if (breathing) 0.55f + 0.45f * phase else 1f
    val slashScale = if (breathing) 0.96f + 0.10f * phase else 1f

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "/",
            fontFamily = WorkisMono,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            color = Kiremit500,
            modifier = Modifier.graphicsLayer {
                alpha = slashAlpha
                scaleX = slashScale
                scaleY = slashScale
            },
        )
        Text(
            text = "workis",
            fontFamily = WorkisMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.sp,
            color = WorkisTheme.colors.ink,
        )
    }
}
