package ai.workis.jowi.ui.screens.partner

import android.graphics.Bitmap
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.AskLine
import ai.workis.jowi.data.Nudge
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.data.OrderQuoting
import ai.workis.jowi.data.PartnerOrder
import ai.workis.jowi.data.SellerInvitation
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.components.AnswerBlock
import ai.workis.jowi.ui.components.AnswerText
import ai.workis.jowi.ui.components.ImageCache
import ai.workis.jowi.ui.components.parseAnswer
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// The partner's desk (artifact "Workis Panel Masası", iOS eff016f / c2c1822 / 9e218cc,
// ledger 49–50): who the firm is · what waits on it ("Sizde", the server's own
// sentences) · the doors its seats open · Jowi's latest line. The app writes no
// sentence of its own about an order — `next.text`, `quoting[]` and the
// invitations say it; the desk only orders and counts them.

/**
 * The Jowi thread as the desk reads it (`GET ask/history/`, the Jowi tab's own
 * door — the same server thread, nothing stored on device): the newest
 * notification for Jowi's line, the newest movement for the empty day.
 */
class PanelDeskViewModel : ViewModel() {
    var history by mutableStateOf<List<AskLine>?>(null)
        private set

    suspend fun loadHistory() {
        val r = safeCall(Graph.language.value) { Graph.api.askHistory() }
        if (r is ApiResult.Ok) history = r.value.lines.orEmpty()
    }

    private val shown get() = history.orEmpty().filter { it.hidden != true && !it.text.isNullOrBlank() }

    /** The newest notification card (`kind: "lines"`), else the newest row that is not the asker's own. */
    val jowiRow: AskLine? get() = shown.let { rows -> rows.lastOrNull { it.kind == "lines" } ?: rows.lastOrNull { it.who != "user" } }

    /** The newest movement that is not the user's own — the empty day's "Son hareket". */
    val lastMove: AskLine? get() = shown.lastOrNull { it.who != "user" }

    // Jowi speaks first (ledger 54, slice 4): due-now nudges from the buyer's own record.
    var nudges by mutableStateOf<List<Nudge>>(emptyList())
        private set
    /** The cpid whose list is being built — one tap at a time. */
    var nudgeBusy by mutableStateOf<String?>(null)
        private set
    /** The refusal sentence, under the row that asked. */
    var nudgeError by mutableStateOf<Pair<String, String>?>(null)
        private set

    /** `GET memory/nudges/` — buyer seat only; a failed read = no rows, never a banner. */
    suspend fun loadNudges(isBuyer: Boolean) {
        if (!isBuyer) { nudges = emptyList(); return }
        val (r, _) = partnerCall(Graph.language.value) { Graph.api.memoryNudges() }
        nudges = r?.nudges.orEmpty().filter { !it.cpid.isNullOrBlank() }
    }

    /** `POST memory/nudges/{cpid}/list/` → the draft list's id, or the server's sentence under the row. */
    suspend fun openNudge(n: Nudge, onList: (String) -> Unit) {
        val cpid = n.cpid ?: return
        if (nudgeBusy != null) return
        nudgeBusy = cpid; nudgeError = null
        val lang = Graph.language.value
        val (r, err) = try { partnerCall(lang) { Graph.api.memoryNudgeList(cpid) } } finally { nudgeBusy = null }
        val listId = r?.listId?.takeIf { it.isNotBlank() }
        if (listId != null) onList(listId)
        else nudgeError = cpid to (err ?: r?.error ?: r?.message ?: statusMessage(-1, lang))
    }
}

private val appLocale: Locale get() = if (Graph.language.value == "en") Locale.UK else Locale("tr", "TR")

/** "Pazartesi, 5 Ekim" — in the in-app language, not the device's. */
private fun todayInWords(): String {
    val s = DateTimeFormatter.ofPattern("EEEE, d MMMM", appLocale).format(LocalDate.now())
    return s.replaceFirstChar { it.titlecase(appLocale) }
}

/** "dün 19:14" / "today 08:50" / "3 Eki 11:02" — the relative day in the app's language + the clock. */
private fun whenLabel(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    val d: ZonedDateTime = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull() ?: return ""
    val clock = DateTimeFormatter.ofPattern("HH:mm", appLocale).format(d)
    val days = java.time.temporal.ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now())
    val rel = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(appLocale))
    val day = when (days) {
        0L -> rel.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
        1L -> rel.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
        else -> DateTimeFormatter.ofPattern("d MMM", appLocale).format(d)
    }
    return "$day $clock"
}

/** The first line of a server answer, through md_lite's grammar (Jowi's line is ONE sentence, not the card). */
private fun firstLine(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return when (val b = parseAnswer(text).firstOrNull()) {
        is AnswerBlock.Title -> b.text
        is AnswerBlock.Paragraph -> b.text.lineSequence().firstOrNull { it.isNotBlank() }
        is AnswerBlock.Items -> b.items.firstOrNull()
        null -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
}

// ---------- identity

/**
 * A 46 dp rounded square: the firm's logo when `company/` carries `logoUrl` (an
 * absolute public URL, cached by URL in `ImageCache`), the first two letters of
 * its name otherwise (the web avatar's fallback). The app never draws a logo with a font.
 */
@Composable
fun FirmMark(name: String, logoUrl: String?, size: Int = 46) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape((size * 0.3f).dp)
    val logo by produceState<Bitmap?>(initialValue = null, logoUrl) {
        value = logoUrl?.takeIf { it.isNotEmpty() }?.let { ImageCache.load(it, maxEdge = 256) }
    }
    val monogram = remember(name) { name.filter { it.isLetterOrDigit() }.take(2).uppercase(Locale("tr", "TR")) }
    Box(
        Modifier.size(size.dp).clip(shape).background(colors.surface).border(1.dp, colors.border, shape)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        val b = logo
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
        else Text(monogram, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.6.sp, color = colors.ink)
    }
}

/**
 * The page's TITLE: the lone kiremit slash, centred above the identity row where
 * a title would stand — read aloud as "Panel". Never the Workis wordmark.
 */
@Composable
fun PanelSlash() {
    val title = stringResource(R.string.market_tab)
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            "/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Kiremit500,
            modifier = Modifier.semantics { heading(); contentDescription = title },
        )
    }
}

@Composable
fun IdentityRow(name: String, markName: String, logoUrl: String?, seats: List<String>) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        FirmMark(markName, logoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                seats.forEach { StatusPill(it) }
                Text(todayInWords(), fontSize = 12.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- "Sizde"

sealed interface MineRow {
    data class Order(val o: PartnerOrder) : MineRow
    data class Quoting(val q: OrderQuoting) : MineRow
    data class Invite(val inv: SellerInvitation) : MineRow
}

/**
 * Orders whose next step is this seat's (newest first), then the buyer's lists in
 * the quote stage, then the seller's open invitations.
 */
fun mineRows(orders: List<PartnerOrder>?, quoting: List<OrderQuoting>, invitations: List<SellerInvitation>?, isBuyer: Boolean, isSeller: Boolean): List<MineRow> {
    val mine = orders.orEmpty()
        .filter { it.next?.who == "me" && !it.next?.text.isNullOrBlank() && it.id != null }
        .sortedByDescending { it.at.orEmpty() }
        .map { MineRow.Order(it) }
    val q = if (isBuyer) quoting.filter { it.listId != null }.map { MineRow.Quoting(it) } else emptyList()
    val inv = if (isSeller) invitations.orEmpty().filter { it.isOpen }.map { MineRow.Invite(it) } else emptyList()
    return mine + q + inv
}

/** `next.job` → the row's icon (the sentence beside it carries the meaning). */
private fun jobIcon(job: String?): ImageVector = when (job) {
    "terms" -> WorkisIcons.Clipboard
    "contract" -> WorkisIcons.DocText
    "fund" -> WorkisIcons.Lock
    "ready" -> WorkisIcons.ShippingBox
    "lines" -> WorkisIcons.Checklist
    "collect" -> WorkisIcons.CreditCard
    else -> WorkisIcons.Dot
}

/** One count of the footer: the number in mono, the catalog's word after it. */
data class DeskCount(val n: Int, val word: String)

/**
 * The hero: a content card (surface + hairline) — the kiremit edge at 55 % says
 * "yours" when something waits; the count says it too, never the colour alone.
 */
@Composable
fun MineCard(
    rows: List<MineRow>,
    invUnread: Map<String, Int>?,
    footer: List<DeskCount>,
    lastMove: AskLine?,
    onRow: (MineRow) -> Unit,
) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, if (rows.isEmpty()) colors.border else Kiremit500.copy(alpha = 0.55f), shape)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.panel_mine).uppercase(appLocale),
                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp, color = colors.faint,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${rows.size}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                color = if (rows.isEmpty()) colors.faint else colors.accentText,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            NothingWaiting(lastMove)
        } else {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                MineRowView(row, invUnread) { onRow(row) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(colors.border))
        // "0 onay · 5 sipariş · 1 liste" — the server's numbers, faint mono
        Text(
            footer.joinToString(" · ") { "${it.n} ${it.word}" },
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MineRowView(row: MineRow, invUnread: Map<String, Int>?, onClick: () -> Unit) {
    val newWord = stringResource(R.string.badge_new_word)
    when (row) {
        is MineRow.Order -> {
            val o = row.o
            val party = (if (o.role == "buyer") o.seller else o.buyer)?.short
            MineRowBody(
                icon = jobIcon(o.next?.job),
                what = o.next?.text.orEmpty(),
                ref = listOfNotNull(o.po, party).filter { it.isNotEmpty() }.joinToString(" · "),
                trailing = null, onClick = onClick,
            )
        }
        is MineRow.Quoting -> {
            val q = row.q
            MineRowBody(
                icon = WorkisIcons.Sparkle,
                what = "${q.nQuoted ?: 0} / ${q.nInvited ?: 0} ${stringResource(R.string.quoting_came)} · ${q.nPending ?: 0} ${stringResource(R.string.quoting_waiting)}",
                ref = q.title.orEmpty(),
                trailing = (q.nNew ?: 0).takeIf { it > 0 }?.let { "$it $newWord" }, onClick = onClick,
            )
        }
        is MineRow.Invite -> {
            val inv = row.inv
            val place = inv.deliveryPlace?.takeIf { it.isNotBlank() }
                ?.let { "${stringResource(R.string.delivery_place_label).lowercase(appLocale)} $it" }
            val nItems = inv.listItems?.size?.takeIf { it > 0 }?.let { "$it ${stringResource(R.string.items_word)}" }
            val unread = invUnread?.get(inv.id.orEmpty()) ?: 0
            MineRowBody(
                icon = WorkisIcons.Sparkle,
                what = listOfNotNull(stringResource(R.string.panel_quote_ask), place).joinToString(" · "),
                ref = listOfNotNull(inv.caseTitle, inv.buyer?.short, nItems).filter { it.isNotEmpty() }.joinToString(" · "),
                trailing = unread.takeIf { it > 0 }?.let { "$it $newWord" }, onClick = onClick,
            )
        }
    }
}

@Composable
private fun MineRowBody(icon: ImageVector, what: String, ref: String, trailing: String?, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.padding(top = 3.dp).size(15.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(what, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.ink, lineHeight = 20.sp)
            if (ref.isNotEmpty()) Text(ref, fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Spacer(Modifier.width(6.dp))
        if (trailing != null) {
            Text(trailing, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = colors.accentText, modifier = Modifier.padding(top = 2.dp))
        } else {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** The empty day: one quiet sentence and, under it, the last thing that moved. */
@Composable
private fun NothingWaiting(last: AskLine?) {
    val colors = WorkisTheme.colors
    Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.panel_nothing_waiting), fontSize = 15.sp, color = colors.muted)
        val line = firstLine(last?.text)
        if (last != null && line != null) {
            val head = listOf(stringResource(R.string.panel_last_move), whenLabel(last.at)).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(
                "$head — ${line.replace("**", "")}",
                fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------- doors

/** One door of the capsule row; `badge` = the page's unread circle in words. */
data class DeskDoor(val label: String, val icon: ImageVector, val count: Int?, val badge: String?, val dest: MarketDest)

/** The doors as a wrapping row of capsules — content surfaces, no tinted primary (the desk has no main action). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoorRow(doors: List<DeskDoor>, onOpen: (MarketDest) -> Unit) {
    val colors = WorkisTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        doors.forEach { d ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { onOpen(d.dest) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(d.icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                Text(d.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1)
                d.count?.let { Text("$it", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink) }
                // a count is never colour alone — the circle in words
                d.badge?.let { Text("· $it", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, maxLines = 1) }
            }
        }
    }
}

// ---------- Jowi speaks first — the memory's nudges

/**
 * Each nudge a quiet row between "Sizde" and the doors (ledger 54): the kiremit slash,
 * the server's sentence as sent (md_lite), and its one tap — a SECONDARY bordered
 * button with `link.text` → the draft list. Nothing when there are no nudges.
 */
@Composable
fun NudgeRows(nudges: List<Nudge>, busy: String?, error: Pair<String, String>?, onOpen: (Nudge) -> Unit) {
    if (nudges.isEmpty()) return
    val colors = WorkisTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        nudges.forEach { n ->
            Row(verticalAlignment = Alignment.Top) {
                Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kiremit500, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnswerText(n.line ?: n.name ?: "", fontSize = 14.sp, lineHeight = 20.sp)
                    val label = n.link?.text?.takeIf { it.isNotBlank() } ?: n.name?.takeIf { it.isNotBlank() }
                    if (label != null) {
                        OutlinedButton(
                            onClick = { onOpen(n) },
                            enabled = busy == null,
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink, disabledContentColor = colors.faint),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            if (busy == n.cpid) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.muted)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (error != null && error.first == n.cpid) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(13.dp))
                            Text(error.second, fontSize = 12.sp, color = colors.danger)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Jowi's line

/** Jowi's newest line, its first sentence through md_lite; a tap opens the Jowi tab. Nothing when there is no row. */
@Composable
fun JowiLineCard(row: AskLine?, onAskJowi: () -> Unit) {
    val text = firstLine(row?.text) ?: return
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.ink.copy(alpha = 0.05f), shape)
            .clickable(onClick = onAskJowi)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kiremit500, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnswerText(text, fontSize = 14.sp, lineHeight = 20.sp)
            Text(
                listOf(whenLabel(row?.at), stringResource(R.string.panel_ask_jowi)).filter { it.isNotEmpty() }.joinToString(" · "),
                fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint,
            )
        }
    }
}
