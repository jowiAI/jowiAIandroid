package ai.workis.jowi.ui.screens.partner

import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.PLOwnRate
import ai.workis.jowi.data.PLProcess
import ai.workis.jowi.data.PLProcessOthers
import ai.workis.jowi.data.PLProcessRow
import ai.workis.jowi.data.PLProcessStage
import ai.workis.jowi.data.PLProposalOption
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.WorkisUnit
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.util.Locale

// ---------- Where the buyer's request stands (ledger 128, iOS WorkisListProcessViews.swift) ----------
//
// Django `sourcing_sheet.for_buyer` → `GET lists/{id}/` `process`. Counts only, every word the
// server's: the four stages, every row with how many firms it was asked of and how many quotes
// came, the non-member firms as ONE line of counts — such a firm is never named here. The page's
// walk stays the strip; the four stages are the Talep step read in detail, drawn as rows under it.
// "now" is the strip's own mark (kiremit dot + the word), never blue.

/** The four stages, then "Kalemleriniz nerede" with the others' counts. */
@Composable
internal fun ProcessSections(process: PLProcess, units: List<WorkisUnit>?) {
    if (process.stages.isNotEmpty()) SurfaceCard {
        process.stages.forEach { ProcessStageRow(it) }
    }
    val others = process.others?.takeIf { it.asked > 0 }
    if (process.rows.isNotEmpty() || others != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lang by Graph.language.collectAsState()
        Eyebrow(stringResource(R.string.process_rows_title).uppercase(Locale.forLanguageTag(lang)))
        SurfaceCard {
            process.rows.forEach { ProcessItemRow(it, units) }
            others?.let { ProcessOthersLine(it) }
        }
    }
}

/** One stage: the walk's mark (✓ · ● · ○) and its word, the server's title and words. */
@Composable
private fun ProcessStageRow(s: PLProcessStage) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    val tint = when (s.state) { "done" -> SuccessGreen; "now" -> colors.accentText; else -> colors.faint }
    val word = when (s.state) {
        "done" -> stringResource(R.string.process_stage_done)
        "now" -> stringResource(R.string.process_stage_now)
        else -> ""
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics(mergeDescendants = true) { selected = s.state == "now" }.padding(vertical = 2.dp),
    ) {
        Box(Modifier.padding(top = 3.dp)) { StepGlyph(if (s.state == "done" || s.state == "now") s.state else "next", size = 13.dp) }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${s.n}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                if (word.isNotEmpty()) Text(
                    "· " + word.uppercase(Locale.forLanguageTag(lang)),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp, color = tint,
                )
            }
            Text(
                s.title, fontSize = 14.sp, fontWeight = if (s.state == "now") FontWeight.SemiBold else FontWeight.Medium,
                color = if (s.state == "next") colors.muted else colors.ink,
            )
            if (s.words.isNotEmpty()) Text(s.words, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.muted)
        }
    }
}

/** One row: number, name as sent, "N firmaya soruldu · N teklif", the state as icon + the server's word; the quantity. */
@Composable
private fun ProcessItemRow(r: PLProcessRow, units: List<WorkisUnit>?) {
    val colors = WorkisTheme.colors
    val (icon, tint) = when (r.state) {
        "ok" -> WorkisIcons.Check to SuccessGreen
        "none" -> WorkisIcons.Search to colors.accentText
        else -> WorkisIcons.Clock to colors.muted
    }
    val amount = r.qty?.let { (WorkisMoney.qty(it) + " " + WorkisUnit.text(r.unit, null, units)).trim() }.orEmpty()
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(r.n?.toString().orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted, modifier = Modifier.widthIn(min = 14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${r.asked}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink)
                Text(stringResource(R.string.process_firms_asked), fontSize = 12.sp, color = colors.muted)
                Text("·", fontSize = 12.sp, color = colors.faint)
                Text("${r.quotes}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (r.quotes > 0) colors.ink else colors.muted)
                Text(stringResource(R.string.process_quotes_word), fontSize = 12.sp, color = colors.muted)
            }
            if (r.words.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(r.words, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint)
            }
        }
        if (amount.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(amount, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink, softWrap = false)
        }
    }
}

/** The firms that are not members: counts only, the web's one sentence — never a name. */
@Composable
private fun ProcessOthersLine(o: PLProcessOthers) {
    val line = stringResource(R.string.process_others_lead) + " ${o.asked} " + stringResource(R.string.process_others_asked) +
        " · ${o.quoted} " + stringResource(R.string.process_others_quoted) +
        " · ${o.declined} " + stringResource(R.string.process_others_declined) +
        " · ${o.waiting} " + stringResource(R.string.process_others_waiting) +
        " " + stringResource(R.string.process_others_tail)
    Text(line, fontSize = 12.5.sp, lineHeight = 17.sp, color = WorkisTheme.colors.muted, modifier = Modifier.padding(vertical = 2.dp))
}

// ---------- The buyer's own rate (ledger 128, Django `quote_currency.own_rate`) ----------
//
// `proposal.ownRate {base, currencies}`. The rate is the BUYER's: typed here, kept on this device
// per list, sent to no door. It only adds an approximate figure BESIDE a price in another currency
// and one approximate total of the picks; no price, pick, "lowest" mark, shortcut or award body
// reads it.

/** What was typed, per currency, kept as typed under `workis_ownrate_<listId>`. */
internal object OwnRateStore {
    private fun key(listId: String) = "workis_ownrate_$listId"

    fun load(listId: String): Map<String, String> = runCatching {
        val raw = Graph.prefs.getString(key(listId), null) ?: return emptyMap()
        (Json.parseToJsonElement(raw) as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    fun save(listId: String, rates: Map<String, String>) {
        if (listId.isEmpty()) return
        val kept = rates.filterValues { it.isNotBlank() }
        runCatching {
            val e = Graph.prefs.edit()
            if (kept.isEmpty()) e.remove(key(listId)) else e.putString(key(listId), buildJsonObject { kept.forEach { (k, v) -> put(k, v) } }.toString())
            e.apply()
        }
    }

    /** The typed rate as a number — "," or "." as the decimal mark — only when above zero. */
    fun rate(typed: String?): BigDecimal? {
        val w = WorkisMoney.wire(typed ?: return null)
        if (w.isEmpty() || !w.all { it.isDigit() || it == '.' }) return null
        return runCatching { BigDecimal(w) }.getOrNull()?.takeIf { it.signum() > 0 }
    }
}

/** The page's rates, read from the device once per list. */
@Composable
internal fun rememberOwnRates(listId: String): MutableState<Map<String, String>> =
    remember(listId) { mutableStateOf(OwnRateStore.load(listId)) }

/** The rate as the matrix cells read it — provided around the table on the comparison. */
internal data class OwnRateView(val own: PLOwnRate, val rates: Map<String, String>)

internal val LocalOwnRate = compositionLocalOf<OwnRateView?> { null }

private fun dec(v: Double?): BigDecimal? = v?.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it) }

/** "≈ unit × rate · amount × rate BASE" beside a price in a currency other than base, once its rate is typed; null = nothing to add. */
internal fun ownRateBeside(o: PLProposalOption, view: OwnRateView?): String? {
    view ?: return null
    val cur = o.currency?.takeIf { it.isNotEmpty() && it != view.own.base } ?: return null
    val k = OwnRateStore.rate(view.rates[cur]) ?: return null
    val unit = dec(o.unit)?.multiply(k)?.toDouble()
    val amount = dec(o.amount)?.multiply(k)?.toDouble()
    return "≈ " + WorkisMoney.text(unit, null) + " · " + WorkisMoney.text(amount, view.own.base)
}

/**
 * The picks through the buyer's rate: only when a picked currency is not base and EVERY picked
 * currency has its rate — the VAT-inclusive total per currency × its rate (base × 1), summed.
 * null = no line.
 */
internal fun ownRateTotal(sums: List<CmpSum>, own: PLOwnRate?, rates: Map<String, String>): String? {
    own ?: return null
    if (sums.none { it.currency != own.base }) return null
    var all = BigDecimal.ZERO
    for (s in sums) {
        val k = if (s.currency == own.base) BigDecimal.ONE else OwnRateStore.rate(rates[s.currency]) ?: return null
        val g = dec(s.net)?.let { n -> s.vats.fold(n) { acc, v -> acc + (dec(v.second) ?: BigDecimal.ZERO) } } ?: return null
        all += g.multiply(k)
    }
    return "≈ " + WorkisMoney.text(all.toDouble(), own.base)
}

/** "Kendi kurunuz": one field per currency, "1 USD = [ ] TRY", and the fixed sentence under them. */
@Composable
internal fun OwnRateBox(own: PLOwnRate, rates: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    val focus = LocalFocusManager.current
    val title = stringResource(R.string.own_rate_title)
    SurfaceCard {
        Text(title.uppercase(Locale.forLanguageTag(lang)), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, color = colors.faint)
        own.currencies.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1 $c =", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
                Spacer(Modifier.width(8.dp))
                val shape = RoundedCornerShape(10.dp)
                BasicTextField(
                    value = rates[c].orEmpty(),
                    onValueChange = { v -> onChange(rates + (c to v.take(20))) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink).readingStep(),
                    cursorBrush = SolidColor(colors.accentText),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.width(130.dp).background(colors.canvas, shape).border(1.dp, colors.border, shape)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .semantics { contentDescription = "$title: 1 $c = ${own.base}" },
                )
                Spacer(Modifier.width(8.dp))
                Text(own.base, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
            }
        }
        Text(stringResource(R.string.own_rate_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = colors.muted)
    }
}
