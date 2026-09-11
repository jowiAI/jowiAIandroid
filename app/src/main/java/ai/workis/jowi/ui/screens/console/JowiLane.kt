package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.data.JowiAnsweredRow
import ai.workis.jowi.data.JowiSource
import ai.workis.jowi.data.JowiTopic
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** "3 sa", "2 gün" — relative age from an ISO timestamp, in the app language. */
@Composable
fun relativeAge(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }
        .recoverCatching { Instant.parse(iso) }
        .getOrNull() ?: return ""
    val d = Duration.between(instant, Instant.now())
    return when {
        d.toMinutes() < 60 -> stringResource(R.string.age_minutes, d.toMinutes().coerceAtLeast(1))
        d.toHours() < 24 -> stringResource(R.string.age_hours, d.toHours())
        else -> "${d.toDays()} " + stringResource(R.string.days_word)
    }
}

/**
 * The "Jowi yanıtladı" lane (read-only) — the web Sorular page's second tab:
 * description, 7/30-day toggle, source + topic chips as filters (selected =
 * kiremit outline), then the rows, then "N soru kapatıldı".
 */
fun LazyListScope.jowiLane(vm: ConsoleViewModel, onOpenThread: (id: String, partner: String?) -> Unit) {
    item {
        val colors = WorkisTheme.colors
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.jowi_lane_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { d ->
                    val on = vm.laneDays == d
                    Text(
                        "$d " + stringResource(R.string.days_word),
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                        color = if (on) colors.ink else colors.faint,
                        modifier = Modifier
                            .background(colors.ink.copy(alpha = if (on) 0.1f else 0.04f), CircleShape)
                            .clickable { vm.laneDays = d; vm.loadLane() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (vm.laneSource != null || vm.laneTopic != null) {
                    Text(
                        "✕ " + stringResource(R.string.clear_filter),
                        fontSize = 12.sp, color = colors.faint,
                        modifier = Modifier.clickable { vm.laneSource = null; vm.laneTopic = null; vm.loadLane() },
                    )
                }
            }
            vm.laneData?.let { d ->
                SourceChips(d.sources.orEmpty(), vm.laneSource) { vm.laneSource = if (vm.laneSource == it) null else it; vm.loadLane() }
                TopicChips(d.topics.orEmpty(), vm.laneTopic) { vm.laneTopic = if (vm.laneTopic == it) null else it; vm.loadLane() }
            }
        }
    }
    val data = vm.laneData
    when {
        data != null -> {
            val rows = data.rows.orEmpty()
            if (rows.isEmpty()) item {
                Text(stringResource(R.string.jowi_lane_empty), fontSize = 13.sp, color = WorkisTheme.colors.faint)
            }
            items(rows.size, key = { rows[it].id ?: it }) { i -> LaneRow(rows[i], onOpenThread) }
            data.closedCount?.takeIf { it > 0 }?.let { n ->
                item {
                    Text(
                        "$n " + stringResource(R.string.closed_questions),
                        fontSize = 12.sp, color = WorkisTheme.colors.faint, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
        vm.laneError != null -> item { Text(vm.laneError.orEmpty(), color = WorkisTheme.colors.danger, fontSize = 13.sp) }
        else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
    }
}

@Composable
private fun Chip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(colors.ink.copy(alpha = 0.06f), CircleShape)
            .border(1.5.dp, if (selected) Kiremit500 else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { content() }
}

/** Count faint, open 👎 in danger, reviewed "✓n" in success — the same marks as the Panel cells. */
@Composable
private fun ChipMarks(count: Int, downOpen: Int, downReviewed: Int) {
    val colors = WorkisTheme.colors
    Text("$count", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
    if (downOpen > 0) Text("👎$downOpen", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.danger)
    if (downReviewed > 0) Text("✓$downReviewed", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = SuccessGreen)
}

@Composable
private fun SourceChips(sources: List<JowiSource>, selected: String?, onTap: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val items = sources.filter { it.source != null }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        items.forEach { src ->
            Chip(selected = selected == src.source, onClick = { onTap(src.source!!) }) {
                Text((src.label ?: src.source.orEmpty()).uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.muted, maxLines = 1)
                ChipMarks(src.count ?: 0, src.downOpen ?: src.down ?: 0, src.downReviewed ?: 0)
            }
        }
    }
}

@Composable
private fun TopicChips(topics: List<JowiTopic>, selected: String?, onTap: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val items = topics.filter { !it.topic.isNullOrEmpty() }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        items.forEach { tp ->
            Chip(selected = selected == tp.topic, onClick = { onTap(tp.topic!!) }) {
                Text(tp.topic.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.accentText, maxLines = 1)
                ChipMarks(tp.count ?: 0, tp.downOpen ?: tp.down ?: 0, tp.downReviewed ?: 0)
            }
        }
    }
}

/** One answered row: source pill (success verified / accent when "model" / faint off-topic), topic, verdict, age, question, excerpt, company, awaiting badge, thread link, review box. */
@Composable
private fun LaneRow(row: JowiAnsweredRow, onOpenThread: (id: String, partner: String?) -> Unit) {
    val colors = WorkisTheme.colors
    val source = row.source.orEmpty()
    val mixed = source.contains("model")
    val off = source == "offtopic"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (row.label ?: source).uppercase(),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                color = if (off) colors.faint else if (mixed) colors.accentText else SuccessGreen,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
            row.topic?.takeIf { it.isNotEmpty() }?.let {
                Text(it.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            // web parity: a reviewed 👎 wears a check (green); an open one stands alone
            val reviewedDown = row.verdict == "down" && row.review != null
            Text(
                when (row.verdict) { "up" -> "👍"; "down" -> if (reviewedDown) "👎 ✓" else "👎"; else -> "—" },
                fontFamily = WorkisMono, fontSize = 11.sp, color = if (reviewedDown) SuccessGreen else colors.faint,
            )
            Text(relativeAge(row.askedAt), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
        }
        Text("“${row.question.orEmpty()}”", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
        row.answerExcerpt?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = colors.muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val short = row.company?.short
            if (!short.isNullOrEmpty()) {
                Text(short, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.accentText)
            } else if (row.company?.seat == "visitor") {
                Text(stringResource(R.string.seat_visitor), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            }
            row.awaiting?.let { w ->
                Text(
                    stringResource(if (w == "expert") R.string.jowi_awaiting_expert else R.string.jowi_awaiting_staff),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Beige,
                    modifier = Modifier.background(BeigeBg, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            row.threadId?.let { tid ->
                Text(
                    "💬 " + stringResource(R.string.open_thread),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.blue,
                    modifier = Modifier.clickable { onOpenThread(tid, short) },
                )
            }
        }
        row.review?.let { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.ink.copy(alpha = 0.05f))
                    .height(IntrinsicSize.Min),
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(SuccessGreen))
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "🎓 " + stringResource(if (r.outcome == "confirmed") R.string.expert_confirmed else R.string.expert_corrected) +
                            (r.by?.let { " · $it" } ?: "") + " · " + relativeAge(r.at),
                        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = SuccessGreen,
                    )
                    r.text?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = colors.ink) }
                }
            }
        }
    }
}
