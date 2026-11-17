package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.focus.onFocusChanged
import ai.workis.jowi.data.PLProposal
import ai.workis.jowi.data.PLProposalOption
import ai.workis.jowi.data.PurchaseListDetail
import ai.workis.jowi.data.WORKIS_BASE_URL
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.WorkisUnit
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessBg
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import java.util.Locale

// ---------- the comparison's reading of the proposal (ledger 94, iOS `WorkisQuotePicks`) ----------

/** One row of the request with every quote's option for it, in the columns' order. A row nobody quoted stays. */
internal data class CmpRow(val id: String, val name: String, val qty: Double?, val unit: String, val quoted: Boolean, val options: List<PLProposalOption>)

internal data class CmpTerm(val label: String, val text: String, val conflict: Boolean, val fromChat: Boolean)

/** One column of the matrix: a quote. `rows` = how many rows it quoted, `all` = the net of everything it quoted per currency. */
internal data class CmpCol(val id: String, val name: String, val rows: Int?, val all: List<Pair<String, Double>>, val quoteId: String?, val pdf: Boolean, val terms: List<CmpTerm>)

/** What the selection gives ONE seller in ONE currency — one order after the award. */
internal data class CmpPart(val sellerId: String, val seller: String, val currency: String, val n: Int = 0, val net: Double = 0.0, val vat: Double = 0.0) {
    val gross get() = net + vat
}

/** The selection's total in ONE currency: the net, the VAT per rate (each line's own, the server's), the gross. */
internal data class CmpSum(val currency: String, val net: Double = 0.0, val vats: List<Pair<Double, Double>> = emptyList()) {
    val gross get() = net + vats.sumOf { it.second }
}

internal fun cmpRows(p: PLProposal?): List<CmpRow> {
    p?.matrix?.let { m ->
        return m.rows.map { r -> CmpRow(r.id, r.name.orEmpty(), r.qty, r.unit.orEmpty(), r.isQuoted, r.cells.orEmpty().flatten()) }
    }
    return p?.items.orEmpty().map { it ->
        CmpRow(it.id.orEmpty(), it.name.orEmpty(), it.qty, it.unit.orEmpty(), !it.options.isNullOrEmpty(), it.options.orEmpty())
    }
}

internal fun cmpCols(p: PLProposal?): List<CmpCol> {
    p?.matrix?.let { m ->
        val every = m.rows.flatMap { it.cells.orEmpty().flatten() }
        return m.sellers.mapIndexed { i, s ->
            val terms = m.terms.orEmpty().mapNotNull { term ->
                val c = term.cells.orEmpty().getOrNull(i) ?: return@mapNotNull null
                val text = c.text?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                CmpTerm(term.label ?: term.key.orEmpty(), text, c.conflict == true, c.isFromChat)
            }
            val name = s.lbl?.short ?: every.firstOrNull { it.seller_ == s.id }?.seller ?: "?"
            val all = s.all.orEmpty().mapNotNull { a -> a.currency?.let { c -> a.total?.let { c to it } } }
            CmpCol(s.id, name, s.rows, all, s.quote?.id, s.quote?.pdf == true, terms)
        }
    }
    // an older server: the columns are the sellers that priced something, the terms the sheet's
    return verdicts(p?.items.orEmpty()).map { v ->
        val sheet = p?.quotes?.firstOrNull { it.seller == v.sellerId }
        val terms = sheet?.terms.orEmpty().mapNotNull { t ->
            val text = t.text?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CmpTerm(t.label ?: t.key.orEmpty(), text, t.conflict == true, t.isFromChat)
        }
        CmpCol(v.sellerId, v.seller, null, emptyList(), sheet?.id, sheet?.pdf == true, terms)
    }
}

/** The row's chosen option: the buyer's pick, "-" = left out, absent = Jowi's (`recommended`). */
internal fun cmpChosen(r: CmpRow, picks: Map<String, String>): PLProposalOption? {
    val p = picks[r.id] ?: return r.options.firstOrNull { it.recommended == true } ?: r.options.firstOrNull()
    if (p == "-") return null
    return r.options.firstOrNull { it.key == p }
}

internal fun cmpParts(rows: List<CmpRow>, picks: Map<String, String>): List<CmpPart> {
    val out = LinkedHashMap<String, CmpPart>()
    rows.forEach { r ->
        val o = cmpChosen(r, picks) ?: return@forEach
        val sid = sellerKey(o)
        val cur = o.currency.orEmpty()
        val part = out["$sid|$cur"] ?: CmpPart(sid, o.seller.orEmpty(), cur)
        out["$sid|$cur"] = part.copy(n = part.n + 1, net = part.net + (o.amount ?: 0.0), vat = part.vat + (o.vat ?: 0.0))
    }
    return out.values.toList()
}

/** Sums of the server's `amount` and `vat` per currency — nothing is summed across currencies, no rate is applied here. */
internal fun cmpSums(rows: List<CmpRow>, picks: Map<String, String>): List<CmpSum> {
    val net = LinkedHashMap<String, Double>()
    val vats = HashMap<String, LinkedHashMap<Double, Double>>()
    rows.forEach { r ->
        val o = cmpChosen(r, picks) ?: return@forEach
        val cur = o.currency.orEmpty()
        net[cur] = (net[cur] ?: 0.0) + (o.amount ?: 0.0)
        o.rate?.let { rate -> vats.getOrPut(cur) { LinkedHashMap() }.let { it[rate] = (it[rate] ?: 0.0) + (o.vat ?: 0.0) } }
    }
    return net.map { (cur, n) -> CmpSum(cur, n, vats[cur].orEmpty().toList().sortedByDescending { it.first }) }
}

/** "Her kalemde en düşük fiyat": only among the prices in ONE currency — the row's recommended one. */
internal fun cmpLowestPicks(rows: List<CmpRow>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    rows.forEach { r ->
        val lead = r.options.firstOrNull { it.recommended == true } ?: r.options.firstOrNull() ?: return@forEach
        r.options.filter { it.currency == lead.currency }.minByOrNull { it.unit ?: Double.POSITIVE_INFINITY }?.let { out[r.id] = it.key }
    }
    return out
}

/** "N kalemin hepsini bu satıcıdan al": every row this seller quoted goes to it (its lowest unit price); the others keep their pick. */
internal fun cmpSellerPicks(rows: List<CmpRow>, seller: String, onto: Map<String, String>): Map<String, String> {
    val out = LinkedHashMap(onto)
    rows.forEach { r ->
        r.options.filter { sellerKey(it) == seller }.minByOrNull { it.unit ?: Double.POSITIVE_INFINITY }?.let { out[r.id] = it.key }
    }
    return out
}

// ---------- the Teklifler stage ----------

/**
 * A notice the reader must not miss (ledger 94): a 3 dp stripe, a tinted ground, an icon — never
 * a plain grey line. `ink` / `ground` are one of the token pairs.
 */
@Composable
internal fun NoticeBand(ink: Color, ground: Color, text: AnnotatedString, icon: ImageVector? = null, glyph: String? = null) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp)).background(ground)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(ink))
        Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.Top) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            else Text(glyph.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink)
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = ink)
        }
    }
}

/**
 * The comparison (ledger 94, Django `compare.py`; iOS `WorkisDecisionSections`): the web's matrix
 * is the request widened to the right. On a phone each row of the request is a BAND of its own
 * and the quotes stand under it side by side, a column per quote — as many as the width holds
 * (more with the phone held sideways), the rest behind a pager; at 600 dp and wider the row is
 * the web's table row. One pick per row, Jowi's preselected; a row nobody quoted stays; the lowest
 * unit price is marked only within one currency; totals per seller and per currency and nothing
 * summed across currencies. The app computes no VAT and writes no term: it adds the server's
 * `amount` / `vat` of the picked lines and prints the server's sentences. On an ordered list
 * the picks are inert.
 */
@Composable
internal fun DecisionPage(d: PurchaseListDetail, vm: ListDetailViewModel, onAward: () -> Unit, onQuotePdf: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val p = d.proposal
    val n = p?.nQuotes ?: 0
    val rows = remember(p) { cmpRows(p) }
    if (n == 0 || rows.none { it.quoted }) {
        SurfaceCard { Text(stringResource(R.string.proposal_empty), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint, modifier = Modifier.padding(vertical = 10.dp)) }
        return
    }
    val cols = remember(p) { cmpCols(p) }
    val quoting = d.isQuoting
    val picks = vm.picks
    val parts = cmpParts(rows, picks)
    val lang by Graph.language.collectAsState()
    val locale = Locale.forLanguageTag(lang)
    val units = d.list?.units

    // n gelen teklif · k/m kalem karşılandı
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text("$n", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        Text(stringResource(R.string.quotes_in_word), fontSize = 13.sp, color = colors.muted)
        Text("·", color = colors.faint)
        Text("${rows.count { it.quoted }}/${rows.size}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink)
        Text(stringResource(R.string.items_covered), fontSize = 13.sp, color = colors.muted)
    }

    // Jowi's reason for the recommended coverage — the server's sentence. No amount beside it:
    // the recommendation may span currencies, and the totals stand below per currency.
    p?.reason?.takeIf { it.isNotBlank() }?.let { reason ->
        SurfaceCard(border = Beige.copy(alpha = 0.6f), fill = BeigeBg.copy(alpha = 0.35f)) {
            Text(stringResource(R.string.jowi_pick).uppercase(locale), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = Beige, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
        }
    }

    // the two notices: the server's sentence about prices in another currency, the rows nobody quoted
    p?.currencyNote?.takeIf { it.isNotBlank() }?.let { NoticeBand(Beige, BeigeBg, AnnotatedString(it), glyph = "⇄") }
    val missing = rows.filter { !it.quoted }
    if (missing.isNotEmpty()) {
        val lead = stringResource(R.string.items_no_quote_yet, missing.size) + ": "
        val list = missing.joinToString(" · ") { r ->
            val q = (WorkisMoney.qty(r.qty) + " " + WorkisUnit.text(r.unit, null, units)).trim()
            if (q.isEmpty()) r.name else "${r.name} ($q)"
        }
        NoticeBand(colors.accentText, colors.accentSoft, buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
            append(list)
        }, icon = WorkisIcons.Warning)
    }

    // the buyer's own reason — their record, never a seller's; it saves itself when it is left
    if (quoting) DecisionField(vm, stringResource(R.string.decision_hint))

    CompareTable(rows, cols, picks, quoting, units, vm)

    // one block per quote: what the selection gives it, its terms as the server's lines
    cols.forEach { c -> SellerBlock(c, parts.filter { it.sellerId == c.id }, quoting, onQuotePdf) { vm.pickAll(cmpSellerPicks(rows, c.id, picks)) } }

    // "Bu seçimle oluşacak siparişler": one per (seller, currency), then the totals per currency
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.orders_from_selection).uppercase(locale))
        SurfaceCard {
            if (parts.isEmpty()) Text(stringResource(R.string.nothing_picked), fontSize = 13.sp, color = colors.faint)
            parts.forEach { part ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(part.seller, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${part.n} " + stringResource(R.string.items_word), fontSize = 12.sp, color = colors.muted)
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(WorkisMoney.text(part.gross, part.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = colors.ink, maxLines = 1, softWrap = false)
                        Text(stringResource(R.string.inc_vat_word), fontSize = 11.sp, color = colors.faint)
                    }
                }
            }
            cmpSums(rows, picks).forEach { s ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.vats.isEmpty()) SumLine(stringResource(R.string.total_label), WorkisMoney.text(s.net, s.currency), strong = true)
                    else {
                        SumLine(stringResource(R.string.goods_total_ex_vat), WorkisMoney.text(s.net, s.currency))
                        s.vats.forEach { (rate, amount) -> SumLine(vatLine(rate), WorkisMoney.text(amount, s.currency)) }
                        SumLine(stringResource(R.string.order_total_inc_vat), WorkisMoney.text(s.gross, s.currency), strong = true)
                    }
                }
            }
            if (quoting) {
                Button(
                    onClick = onAward, enabled = !vm.placing && parts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    if (vm.placing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                    else Text("✓ " + stringResource(R.string.award_action), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SumLine(label: String, value: String, strong: Boolean = false) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.5.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, color = if (strong) colors.ink else colors.muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(value, fontFamily = WorkisMono, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.5.sp, color = if (strong) colors.ink else colors.muted, maxLines = 1, softWrap = false)
    }
}

/** Jowi's pick: the accent letter on the accent-soft ground. */
@Composable
private fun JBadge() {
    val colors = WorkisTheme.colors
    Box(Modifier.size(17.dp).background(colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
        Text("J", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = colors.accentText)
    }
}

private val NAME_WIDTH = 250.dp

/**
 * The table: "Toplu seçim", the legend (the two marks a cell may carry) with the pager, then a
 * band per request row with the quotes side by side under it. Columns shown = width / 124 dp, two
 * at least; at 600 dp and wider the band is a fixed 250 dp block on the left.
 */
@Composable
private fun CompareTable(rows: List<CmpRow>, cols: List<CmpCol>, picks: Map<String, String>, quoting: Boolean, units: List<WorkisUnit>?, vm: ListDetailViewModel) {
    val colors = WorkisTheme.colors
    var colStart by rememberSaveable { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(20.dp)
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(colors.tableGround).border(1.dp, colors.border, shape)) {
        val wide = maxWidth >= 600.dp
        val k = minOf(cols.size, maxOf(2, ((maxWidth - (if (wide) NAME_WIDTH else 0.dp)) / 124.dp).toInt()))
        val start = minOf(colStart, maxOf(0, cols.size - k))
        val window = cols.subList(start, start + k)
        val known = cols.map { it.id }.toSet()
        Column(Modifier.fillMaxWidth()) {
            if (quoting) {
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(WorkisIcons.Checklist, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pick_all_at_once), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
                        Text("⌄", color = colors.faint)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.jowi_pick), color = colors.ink) }, onClick = { open = false; vm.pickAll(emptyMap()) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pick_lowest_each), color = colors.ink) }, onClick = { open = false; vm.pickAll(cmpLowestPicks(rows)) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
            // the legend, and the pager when more quotes came than the width holds — beside the
            // legend where the row is wide enough, else on a line of its own under it
            val pager: @Composable () -> Unit = {
                PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = start > 0) { colStart = maxOf(0, start - 1) }
                Text("${start + 1}–${start + k} / ${cols.size}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, maxLines = 1, softWrap = false)
                PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = start + k < cols.size) { colStart = minOf(cols.size - k, start + 1) }
            }
            val paged = cols.size > k
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↓ " + stringResource(R.string.lowest_unit_price), fontSize = 11.5.sp, color = SuccessGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(12.dp))
                JBadge()
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.jowi_pick), fontSize = 11.5.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (paged && wide) { Spacer(Modifier.weight(1f)); pager() }
            }
            if (paged && !wide) Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) { pager() }
            rows.forEachIndexed { i, r ->
                // a tap on a pick ends the typing in the decision field (ledger 100)
                MatrixRow(r, i, window, known, wide, picks, quoting, units) { key -> focus.clearFocus(); vm.pick(r.id, key) }
            }
        }
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.ink else colors.faint.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

/**
 * One row of the request: its heading on a band of its own (number, name, quantity), then every
 * quote's price for it, a column per quote. A row nobody quoted keeps its band, faint, with no cells.
 */
@Composable
private fun MatrixRow(r: CmpRow, index: Int, cols: List<CmpCol>, known: Set<String>, wide: Boolean, picks: Map<String, String>, quoting: Boolean, units: List<WorkisUnit>?, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val chosen = cmpChosen(r, picks)
    val left = picks[r.id] == "-"
    val shown = cols.map { it.id }.toSet()
    val stray = r.options.filter { sellerKey(it) !in known }
    // quieter in light: the full ground reads loud on a light page
    val ground = BeigeBg.copy(alpha = (if (colors.isDark) 1f else 0.5f) * (if (r.quoted) 1f else 0.45f))
    val band: @Composable (Modifier) -> Unit = { m ->
        Row(m.background(ground).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
            Text("${index + 1}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, modifier = Modifier.widthIn(min = 14.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    r.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = if (left || !r.quoted) colors.faint else colors.ink,
                    textDecoration = if (left) TextDecoration.LineThrough else null,
                )
                if (!r.quoted) Text(stringResource(R.string.no_quote_yet), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.accentText)
                if (left) Text(stringResource(R.string.left_out_note), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted)
                // the pick stands in a column the pager has slid away: say it here
                chosen?.takeIf { sellerKey(it) in known && sellerKey(it) !in shown }?.let { o ->
                    Text("✓ " + o.seller.orEmpty() + " · " + WorkisMoney.text(o.amount, o.currency), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = SuccessGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(6.dp))
            Text((WorkisMoney.qty(r.qty) + " " + WorkisUnit.text(r.unit, null, units)).trim(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = if (r.quoted) colors.ink else colors.faint, softWrap = false)
        }
    }
    val cells: @Composable (Modifier) -> Unit = { m ->
        Row(m.height(IntrinsicSize.Min)) {
            cols.forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                QuoteCell(c.name, r.options.filter { sellerKey(it) == c.id }, chosen, quoting, Modifier.weight(1f), onPick)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (wide) {
            // the web's table row: the product a fixed block on the left, the quotes to its right
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (r.quoted) { band(Modifier.width(NAME_WIDTH).fillMaxHeight()); cells(Modifier.weight(1f)) }
                else band(Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        } else {
            band(Modifier.fillMaxWidth())
            if (r.quoted) cells(Modifier.fillMaxWidth())
        }
        // a price whose quote is not among the columns (an odd payload): still pickable
        stray.forEach { o -> QuoteCell(o.seller.orEmpty(), listOf(o), chosen, quoting, Modifier.fillMaxWidth(), onPick) }
    }
}

/** One quote's cell on one row: its price(s) as the pick, or the seller's name faint and a dash where it priced nothing. */
@Composable
private fun QuoteCell(seller: String, mine: List<PLProposalOption>, chosen: PLProposalOption?, quoting: Boolean, modifier: Modifier, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    Column(modifier.padding(if (mine.isEmpty()) 10.dp else 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mine.isEmpty()) {
            Text(seller, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("—", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.faint)
        }
        mine.forEach { o ->
            val on = chosen != null && chosen.key == o.key
            // tapping the chosen one again leaves the row out of the order
            PriceCell(o, seller, on, quoting) { onPick(if (on) "-" else o.key) }
        }
    }
}

/**
 * A price as a pick: the radio and the seller, the UNIT price large (green behind a down arrow
 * when it is the lowest in its currency) with the J badge on the same line, the net amount and
 * its currency — accent and bold when it is not the one asked —, the VAT rate. The chosen one
 * stands on the success ground with a border and the green check: mark + ground, never colour alone.
 */
@Composable
private fun PriceCell(o: PLProposalOption, seller: String, on: Boolean, quoting: Boolean, onTap: () -> Unit) {
    val colors = WorkisTheme.colors
    val low = o.lowest == true && o.off != true
    val shape = RoundedCornerShape(8.dp)
    val tone = if (on) colors.ink else colors.muted
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) SuccessBg.copy(alpha = if (colors.isDark) 1f else 0.5f) else Color.Transparent)
            .then(if (on) Modifier.border(1.dp, SuccessGreen.copy(alpha = 0.55f), shape) else Modifier)
            .selectable(selected = on, enabled = quoting, role = Role.RadioButton, onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (on) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                else Box(Modifier.size(13.dp).border(1.5.dp, colors.faint, CircleShape))
            }
            Spacer(Modifier.width(5.dp))
            Text(seller, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        // the J badge rides the price line: beside the name it cut the name in a narrow column
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (low) "↓ " else "") + WorkisMoney.text(o.unit, null),
                fontFamily = WorkisMono, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 16.sp,
                color = if (low) SuccessGreen else tone, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (o.recommended == true) JBadge()
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(WorkisMoney.text(o.amount, null), fontFamily = WorkisMono, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.5.sp, color = tone, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Text(o.currency.orEmpty(), fontFamily = WorkisMono, fontWeight = if (o.off == true) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, color = if (o.off == true) colors.accentText else colors.faint, maxLines = 1, softWrap = false)
        }
        o.rate?.let { Text(vatLine(it), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1) }
        if (o.off == true) Text(stringResource(R.string.off_currency_hint), fontSize = 10.5.sp, lineHeight = 14.sp, color = colors.accentText, maxLines = 4, overflow = TextOverflow.Ellipsis)
        o.carried?.quote?.takeIf { it.isNotEmpty() }?.let { q ->
            val until = o.carried.until?.takeIf { it.isNotEmpty() }?.let { " · " + stringResource(R.string.valid_until_label) + " " + it.take(10) }.orEmpty()
            Text(stringResource(R.string.carried_from) + " " + q + until, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, color = colors.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        o.fails?.takeIf { it > 0 }?.let { f ->
            Text("$f " + stringResource(R.string.fails_word), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Beige, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One quote: what the selection gives it per currency (picked rows, goods total, VAT, order
 * total), then its terms as lines — the SERVER's words (the payment line reads "Güvenceli ödeme
 * — …" there; the app composes no term of its own) —, everything it quoted, the door that takes
 * every row from it (bordered text — the award stays the page's one fill), the quote document.
 */
@Composable
private fun SellerBlock(c: CmpCol, parts: List<CmpPart>, quoting: Boolean, onQuotePdf: (String) -> Unit, onTakeAll: () -> Unit) {
    val colors = WorkisTheme.colors
    SurfaceCard(fill = colors.tableSub) {
        Text(c.name, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.accentText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (parts.isEmpty()) SumLine(stringResource(R.string.picked_items), "—")
            parts.forEach { part ->
                SumLine(stringResource(R.string.picked_items), "${part.n}")
                SumLine(stringResource(R.string.goods_total_ex_vat), WorkisMoney.text(part.net, part.currency))
                SumLine(stringResource(R.string.vat_word), WorkisMoney.text(part.vat, part.currency))
                SumLine(stringResource(R.string.order_total_inc_vat), WorkisMoney.text(part.gross, part.currency), strong = true)
            }
        }
        if (c.terms.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            c.terms.forEach { term ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(term.label, fontSize = 11.sp, color = colors.faint)
                    Text(term.text, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
                    if (term.conflict) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.term_conflict), fontSize = 11.sp, color = Beige)
                    } else if (term.fromChat) Text(stringResource(R.string.term_from_chat), fontSize = 11.sp, color = colors.faint)
                }
            }
        }
        if (c.all.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.everything_quoted) + " · " + stringResource(R.string.ex_vat_word), fontSize = 11.sp, color = colors.faint)
                Text(c.all.joinToString(" + ") { WorkisMoney.text(it.second, it.first) }, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted)
            }
        }
        val n = c.rows ?: 0
        if (quoting && n > 0) {
            Text(
                stringResource(R.string.take_all_from_seller, n), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onTakeAll).padding(vertical = 4.dp),
            )
        }
        val qid = c.quoteId?.takeIf { it.isNotEmpty() }
        if (c.pdf && qid != null) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onQuotePdf(WORKIS_BASE_URL + "workis/quotes/$qid/pdf/") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.faint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.quote_doc), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted)
            }
        }
    }
}

/**
 * "Karar gerekçeniz" (ledger 100) — the one field of the decision page and of the Sipariş stage.
 * No button: when the field loses focus, or the screen is left, what the buyer typed saves
 * itself. While it is not saved a faint dotted ring stands at the field's bottom-right corner;
 * saved, the ring is a green filled check there for ~1.6 s, then nothing — no word on screen
 * ("kaydedildi" is the check's accessibility label), no banner, no coloured border while editing.
 * Leaving is reachable: the keyboard carries "Tamam", closing the keyboard leaves the field, and
 * a tap on a pick does too.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DecisionField(vm: ListDetailViewModel, hint: String) {
    val colors = WorkisTheme.colors
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // the keyboard closed under a focused field (the back gesture) = the field is left
    val ime = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var imeWasUp by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(ime) {
        if (imeWasUp && !ime && focused) focus.clearFocus()
        imeWasUp = ime
    }
    // the screen (or the stage) is left with words not saved
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.leaveDecision() } }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.muted, unfocusedBorderColor = colors.border, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, cursorColor = colors.blue, focusedLabelColor = colors.muted, unfocusedLabelColor = colors.faint)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            OutlinedTextField(
                value = vm.decision, onValueChange = { vm.typeDecision(it) },
                label = { Text(stringResource(R.string.decision_label), fontSize = 13.sp) },
                minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = colors.ink).readingStep(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (focused && !it.isFocused) vm.leaveDecision()
                    focused = it.isFocused
                },
            )
            val mark = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).size(14.dp)
            if (vm.decisionTick) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.detail_saved), tint = SuccessGreen, modifier = mark)
            } else if (vm.decisionUnsaved) {
                val ring = colors.faint
                androidx.compose.foundation.Canvas(mark) {
                    val w = 1.5.dp.toPx()
                    drawCircle(
                        ring, radius = (size.minDimension - w) / 2,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.dp.toPx())),
                        ),
                    )
                }
            }
        }
        Text(hint, fontSize = 11.sp, lineHeight = 15.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
