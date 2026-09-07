package ai.workis.jowi.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * Real `https://workis.ai/...` links in consent texts open IN-APP (Custom Tab —
 * the Safari-sheet twin), never the external browser. Falls back to ACTION_VIEW
 * on a device without a Custom-Tabs-capable browser.
 */
fun openInApp(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\(([^)]+)\)""")

/**
 * The catalog's inline markdown (`**bold**`, `[label](url)`) → AnnotatedString.
 * Only what the three markdown-carrying keys use (applyAgree, expertAgree,
 * expertIntro); anything else renders as plain text.
 */
fun inlineMarkdown(
    text: String,
    linkColor: Color,
    onLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        val label = m.groups[2]?.value
        val url = m.groups[3]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            label != null && url != null -> withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ) { onLink(url) },
            ) { append(label) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkColor = WorkisTheme.colors.blue
    val annotated = remember(text, linkColor) {
        inlineMarkdown(text, linkColor) { openInApp(context, it) }
    }
    Text(annotated, color = color, fontSize = fontSize, lineHeight = lineHeight, modifier = modifier)
}
