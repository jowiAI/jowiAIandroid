package ai.workis.jowi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme

/**
 * Server Markdown rendered natively in the Workis type and tokens — the
 * dialect agreed in API_CONTRACT (earnings guide, 2026-09-10): #/##/###
 * headings, paragraphs, **bold**, *italic*, `code` = chips, > blockquote =
 * the thesis/warning box, GFM tables, ordered/unordered lists, absolute
 * links. Content is the server's — one copy, three doors. Never re-authored.
 */

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Quote(val text: String) : Block
    data class ListBlock(val ordered: Boolean, val items: List<String>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

private fun splitRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun isSeparatorRow(line: String): Boolean =
    line.trim().let { it.startsWith("|") && it.replace("|", "").replace("-", "").replace(":", "").isBlank() }

private fun parseBlocks(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.setLength(0)
    }
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        when {
            line.isEmpty() -> { flushPara(); i++ }
            line.startsWith("#") -> {
                flushPara()
                val level = line.takeWhile { it == '#' }.length
                out += Block.Heading(level, line.drop(level).trim())
                i++
            }
            line.startsWith(">") -> {
                flushPara()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    q.append(lines[i].trim().removePrefix(">").trim()).append(' ')
                    i++
                }
                out += Block.Quote(q.toString().trim())
            }
            line.startsWith("|") && i + 1 < lines.size && isSeparatorRow(lines[i + 1]) -> {
                flushPara()
                val header = splitRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitRow(lines[i]); i++
                }
                out += Block.Table(header, rows)
            }
            Regex("""^(\d+[.)]|[-*•])\s+""").containsMatchIn(line) -> {
                flushPara()
                val ordered = line.first().isDigit()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    val m = Regex("""^(\d+[.)]|[-*•])\s+(.*)""").find(l)
                    if (m != null) { items += m.groupValues[2]; i++ }
                    else if (l.isNotEmpty() && lines[i].startsWith("  ") && items.isNotEmpty()) {
                        items[items.lastIndex] = items.last() + " " + l; i++ // continuation line
                    } else break
                }
                out += Block.ListBlock(ordered, items)
            }
            else -> { para.append(line).append(' '); i++ }
        }
    }
    flushPara()
    return out
}

private val INLINE_RICH = Regex("""`([^`]+)`|\*\*(.+?)\*\*|\*(?!\s)(.+?)(?<!\s)\*|\[([^\]]+)]\(([^)]+)\)""")

/** Inline dialect: `code` chips, **bold**, *italic*, [label](url). */
@Composable
private fun richInline(text: String, base: Color): AnnotatedString {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val chipBg = colors.ink.copy(alpha = 0.06f)
    return remember(text, base) {
        buildAnnotatedString {
            var last = 0
            for (m in INLINE_RICH.findAll(text)) {
                append(text.substring(last, m.range.first))
                val g = m.groups
                when {
                    g[1] != null -> withStyle(
                        SpanStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, color = colors.accentText, background = chipBg, fontSize = 13.sp),
                    ) { append(" " + g[1]!!.value + " ") }
                    g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[2]!!.value) }
                    g[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]!!.value) }
                    g[4] != null && g[5] != null -> withLink(
                        LinkAnnotation.Url(
                            g[5]!!.value,
                            TextLinkStyles(SpanStyle(color = colors.blue, textDecoration = TextDecoration.Underline)),
                        ) { openInApp(context, g[5]!!.value) },
                    ) { append(g[4]!!.value) }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

@Composable
private fun Body(text: String, color: Color, size: TextUnit = 15.sp, lineHeight: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Text(richInline(text, color), color = color, fontFamily = WorkisSans, fontSize = size, lineHeight = lineHeight, modifier = modifier)
}

@Composable
fun MarkdownDocument(markdown: String, modifier: Modifier = Modifier) {
    val colors = WorkisTheme.colors
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        blocks.forEachIndexed { index, b ->
            when (b) {
                is Block.Heading -> {
                    Spacer(Modifier.height(if (index == 0) 4.dp else if (b.level <= 2) 22.dp else 18.dp))
                    Text(
                        b.text,
                        fontFamily = if (b.level <= 2) WorkisMono else WorkisSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = when (b.level) { 1 -> 24.sp; 2 -> 20.sp; else -> 17.sp },
                        lineHeight = when (b.level) { 1 -> 32.sp; 2 -> 28.sp; else -> 24.sp },
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(if (b.level <= 2) 8.dp else 6.dp))
                }
                is Block.Paragraph -> {
                    Body(b.text, colors.ink)
                    Spacer(Modifier.height(14.dp))
                }
                is Block.Quote -> {
                    // the thesis / warning box: kiremit edge, quiet well
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.ink.copy(alpha = 0.05f))
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Kiremit500))
                        Body(b.text, colors.ink, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                is Block.ListBlock -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.items.forEachIndexed { n, item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    if (b.ordered) "${n + 1}." else "•",
                                    fontFamily = if (b.ordered) WorkisMono else WorkisSans,
                                    fontSize = 15.sp, lineHeight = 22.sp, color = colors.muted,
                                    modifier = Modifier.width(26.dp),
                                )
                                Body(item, colors.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                is Block.Table -> {
                    MarkdownTable(b.header, b.rows)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** GFM table: border token, mono column heads, alternating quiet rows; a row whose label AND figure are bold reads as the total. */
@Composable
private fun MarkdownTable(header: List<String>, rows: List<List<String>>) {
    val colors = WorkisTheme.colors
    val cols = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    // column width follows content: a figures column stays narrow, prose gets the room
    val weights = remember(header, rows) {
        (0 until cols).map { c ->
            val longest = (listOf(header.getOrElse(c) { "" }) + rows.map { it.getOrElse(c) { "" } }).maxOf { it.length }
            kotlin.math.sqrt(longest.coerceIn(4, 60).toFloat())
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            for (c in 0 until cols) {
                Text(
                    header.getOrElse(c) { "" },
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp,
                    color = colors.faint,
                    modifier = Modifier.weight(weights[c]).fillMaxHeight().padding(horizontal = 8.dp, vertical = 8.dp),
                )
                if (c < cols - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
            }
        }
        rows.forEachIndexed { r, row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (r % 2 == 1) colors.ink.copy(alpha = 0.04f) else Color.Transparent)
                    .height(IntrinsicSize.Min),
            ) {
                for (c in 0 until cols) {
                    Body(
                        row.getOrElse(c) { "" }, colors.ink, size = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.weight(weights[c]).fillMaxHeight().padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    if (c < cols - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                }
            }
        }
    }
}
