package ai.workis.jowi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * A Jowi ANSWER (the Jowi tab, the history rows, the public ask) in the light
 * markdown of Django's `workis/services/md_lite.py` (2026-09-27) — and nothing
 * else: `**bold**` inline, numbered steps one per line, bullets one per line,
 * and a first line `# Title` only on a long explanatory answer. This mirrors
 * the server grammar line for line (iOS `WorkisAnswerText`) rather than handing
 * the text to `MarkdownDocument`: a lone newline stays a line break here, as on
 * the web (`md_lite.js`).
 */
sealed interface AnswerBlock {
    data class Title(val text: String) : AnswerBlock
    /** Lines joined with "\n" — blank lines dropped, a lone newline kept. */
    data class Paragraph(val text: String) : AnswerBlock
    data class Items(val ordered: Boolean, val items: List<String>) : AnswerBlock
}

// md_lite.py's four patterns, verbatim
private val TITLE = Regex("""^\s*#{1,3}\s+(.*\S)\s*$""")
private val OL = Regex("""^\s*\d{1,2}[.)]\s+(.*\S)\s*$""")
private val UL = Regex("""^\s*[-•*]\s+(.*\S)\s*$""")

/**
 * The server grammar: a title only as the FIRST line; a run of `1.` / `1)`
 * lines is one numbered list, a run of `-` / `•` / `*` lines one bullet list;
 * everything else gathers into paragraphs, blank lines dropped.
 */
fun parseAnswer(text: String): List<AnswerBlock> {
    val lines = text.replace("\r", "").split("\n")
    val out = ArrayList<AnswerBlock>()
    var i = 0
    TITLE.matchEntire(lines[0])?.let { out += AnswerBlock.Title(it.groupValues[1]); i = 1 }
    val para = ArrayList<String>()
    fun flush() {
        if (para.isNotEmpty()) { out += AnswerBlock.Paragraph(para.joinToString("\n")); para.clear() }
    }
    while (i < lines.size) {
        val line = lines[i]
        val rx = when {
            OL.matches(line) -> OL
            UL.matches(line) -> UL
            else -> null
        }
        if (rx != null) {
            flush()
            val items = ArrayList<String>()
            while (i < lines.size) {
                val m = rx.matchEntire(lines[i]) ?: break
                items += m.groupValues[1]
                i++
            }
            out += AnswerBlock.Items(ordered = rx === OL, items = items)
            continue
        }
        if (line.isNotBlank()) para += line.trim()
        i++
    }
    flush()
    return out
}

/**
 * One answer on screen. Inline forms (`**bold**`, and a `[label](url)` should
 * one ever arrive) go through the same `inlineMarkdown` the catalog keys use —
 * the asterisks never show.
 */
@Composable
fun AnswerText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WorkisTheme.colors.ink,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val linkColor = colors.blue
    val blocks = remember(text) { parseAnswer(text) }
    fun inline(s: String): AnnotatedString = inlineMarkdown(s, linkColor) { openInApp(context, it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is AnswerBlock.Title -> Text(
                    inline(b.text), color = color, fontWeight = FontWeight.SemiBold,
                    fontSize = (fontSize.value + 1).sp, lineHeight = lineHeight,
                )
                is AnswerBlock.Paragraph -> Text(inline(b.text), color = color, fontSize = fontSize, lineHeight = lineHeight)
                is AnswerBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEachIndexed { n, item ->
                        Row {
                            Text(
                                if (b.ordered) "${n + 1}." else "•",
                                fontFamily = WorkisMono, fontWeight = FontWeight.Medium,
                                fontSize = (fontSize.value - 2).sp, lineHeight = lineHeight,
                                color = colors.muted, textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(inline(item), color = color, fontSize = fontSize, lineHeight = lineHeight)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A clipped excerpt of an answer (the console's Jowi lane): md_lite's `plain`
 * — the title's hashes and the bold markers go, list markers stay (they read
 * fine as text) — with the bold still drawn bold.
 */
fun answerExcerpt(text: String): AnnotatedString {
    val lines = text.replace("\r", "").split("\n").toMutableList()
    TITLE.matchEntire(lines[0])?.let { lines[0] = it.groupValues[1] }
    val joined = lines.joinToString("\n")
    return buildAnnotatedString {
        var last = 0
        for (m in BOLD.findAll(joined)) {
            append(joined.substring(last, m.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
            last = m.range.last + 1
        }
        append(joined.substring(last))
    }
}

private val BOLD = Regex("""\*\*(.+?)\*\*""")
