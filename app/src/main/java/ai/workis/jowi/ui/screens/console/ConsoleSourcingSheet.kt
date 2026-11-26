package ai.workis.jowi.ui.screens.console

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.SimpleResult
import ai.workis.jowi.data.SourcingBoardRow
import ai.workis.jowi.data.SourcingFirm
import ai.workis.jowi.data.SourcingItem
import ai.workis.jowi.data.SourcingPrice
import ai.workis.jowi.data.SourcingSheet
import ai.workis.jowi.data.SourcingTrialRow
import ai.workis.jowi.data.WorkisClock
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.PdfViewerDialog
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.PaperInk
import ai.workis.jowi.ui.theme.PaperWhite
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File

/*
 * Console → Tedarik → one request's SHEET (`GET console/sourcing/lists/{id}/sheet/`, Django
 * sourcing_sheet.py): its rows × its firms, read ONCE and drawn two ways — "Kaleme göre" (who was
 * asked for each row, at what price; the row nobody was asked for leads in kiremit) and "Firmaya
 * göre" (one firm = one mail = one quote). The segmented control only changes the drawing.
 *
 * A firm opens its own screen: the mail that went out, the replies and their files, the quote as
 * read, and the desk's acts on that firm. Every act prints the server's `message` / `error` and
 * reads the sheet again. While a row's research runs the sheet is read every 20 s.
 *
 * The field lead (`full: false`) gets the same screens: the server sends no price, mail, file,
 * doubt, approve or upload, so none is drawn — no role is checked here.
 */

/** A file the server handed back, shown natively (PDF / image) or handed to another app. */
internal class SrcFile(val bytes: ByteArray, val name: String, val mime: String)

class SourcingSheetViewModel : ViewModel() {
    var listId: String = ""
        private set
    var sheet by mutableStateOf<SourcingSheet?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** The last act's sentence (the server's), and whether it was a refusal. */
    var note by mutableStateOf<String?>(null)
    var noteIsError by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    /** Bumped on every read — the research poll waits on it. */
    var reads by mutableStateOf(0)
        private set
    /** The file being fetched (`what`), and the one fetched. */
    var opening by mutableStateOf<String?>(null)
        private set
    internal var preview by mutableStateOf<SrcFile?>(null)
    /** Which groups stand open; a group not decided yet opens by the web's rule. */
    val open = mutableStateMapOf<String, Boolean>()
    /** The trial addresses as typed, per trial key. */
    val trialTo = mutableStateMapOf<String, String>()

    private val lang get() = Graph.language.value
    private val api get() = Graph.api

    fun start(id: String) {
        if (id != listId) {
            listId = id; sheet = null; loadError = null; note = null
            open.clear(); trialTo.clear()
        }
        load()
    }

    fun load() {
        val id = listId
        if (id.isEmpty()) return
        viewModelScope.launch {
            val (a, err) = partnerCall(lang) { api.consoleSourcingSheet(id) }
            refreshing = false
            val s = a?.sheet
            if (s != null) { sheet = s; loadError = null } else if (sheet == null) loadError = a?.error ?: err
            reads += 1
        }
    }

    fun refresh() { refreshing = true; load() }

    fun say(words: String?, error: Boolean) { note = words; noteIsError = error }

    /** One act: print the server's sentence, then read the sheet again. */
    fun act(call: suspend () -> SimpleResult, done: ((Boolean) -> Unit)? = null) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { call() }
            busy = false
            val ok = r?.success == true
            say(if (ok) r?.message else r?.error ?: r?.message ?: err, !ok)
            if (ok) load()
            done?.invoke(ok)
        }
    }

    fun firmAct(profileId: String, act: String, body: JsonObject, done: ((Boolean) -> Unit)? = null) =
        act({ api.consoleSourcingFirmAct(listId, profileId, act, body) }, done)

    fun research(itemId: String) = act({ api.consoleSourcingResearch(listId, jsonBody("item_id" to itemId)) })

    fun addFirm(body: JsonObject, done: (Boolean) -> Unit) = act({ api.consoleSourcingFirmAdd(listId, body) }, done)

    fun approve(quoteId: String) = act({ api.consoleSourcingApprove(quoteId, jsonBody()) })

    fun sendTrial(rows: List<SourcingTrialRow>) {
        val to = buildMap {
            rows.forEach { r ->
                val key = r.key ?: return@forEach
                val a = (trialTo[key] ?: r.to.orEmpty()).trim()
                if (a.isNotEmpty()) put(key, JsonPrimitive(a))
            }
        }
        act({ api.consoleSourcingTrialSend(listId, jsonBody("to" to JsonObject(to))) })
    }

    fun upload(invitationId: String, bytes: ByteArray, name: String, mime: String) {
        val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaType()))
        act({ api.consoleSourcingQuote(invitationId, part) })
    }

    fun openFile(invitationId: String, what: String) {
        if (opening != null) return
        opening = what
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { api.consoleSourcingFile(invitationId, what) }
                val body = resp.body()
                if (!resp.isSuccessful || body == null) {
                    say(messageFromBody(HttpException(resp), lang), true)
                } else {
                    val bytes = withContext(Dispatchers.IO) { body.use { it.bytes() } }
                    val mime = resp.headers()["Content-Type"]?.substringBefore(';')?.trim().orEmpty()
                    val name = dispositionName(resp.headers()["Content-Disposition"]) ?: what
                    preview = SrcFile(bytes, name, mime.ifEmpty { "application/octet-stream" })
                }
            } catch (_: java.io.IOException) {
                say(statusMessage(null, lang), true)
            } catch (_: Exception) {
                say(statusMessage(-1, lang), true)
            }
            opening = null
        }
    }

    private fun dispositionName(h: String?): String? {
        if (h.isNullOrEmpty()) return null
        Regex("filename\\*=(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(h)?.let {
            return runCatching { java.net.URLDecoder.decode(it.groupValues[1].trim().trim('"'), "UTF-8") }.getOrNull()?.takeIf { n -> n.isNotEmpty() }
        }
        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Reads the sheet when a page of it appears, re-reads it every 20 s while a research runs (until
 * it ends or the page goes away), and presents a fetched file — one presenter for both pages.
 */
@Composable
internal fun SourcingSheetHost(vm: SourcingSheetViewModel, listId: String) {
    LaunchedEffect(listId) { vm.start(listId) }
    LaunchedEffect(vm.reads) {
        if (vm.sheet?.researching == true) {
            delay(20_000)
            vm.load()
        }
    }
    val context = LocalContext.current
    vm.preview?.let { f ->
        val pdf = f.mime.contains("pdf") || looksLikePdf(f.bytes)
        val image = f.mime.startsWith("image/")
        when {
            pdf -> PdfViewerDialog(f.name, data = f.bytes, fileName = f.name.takeIf { it.endsWith(".pdf", true) } ?: "${f.name}.pdf") { vm.preview = null }
            image -> Dialog(onDismissRequest = { vm.preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(PaperWhite).clickable { vm.preview = null }, contentAlignment = Alignment.Center) {
                    val bmp = remember(f) { android.graphics.BitmapFactory.decodeByteArray(f.bytes, 0, f.bytes.size) }
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = f.name, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                    else CircularProgressIndicator(color = PaperInk)
                }
            }
            // a text or a mail file: another app on the phone opens it
            else -> LaunchedEffect(f) { handOver(context, f); vm.preview = null }
        }
    }
}

private fun handOver(context: Context, f: SrcFile) {
    val safe = f.name.replace(Regex("[/\\\\:]"), "_").ifEmpty { "file" }
    val file = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, safe) }.apply { writeBytes(f.bytes) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, f.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(view, null)) }
}

// ---------- small shared pieces ----------

/*
 * The sheet's grounds (ledgers 135 / 139). Every head stands on the card surface; what it opens
 * stands on the comparison table's ground — warm in dark, off the grey card by HUE (a step down in
 * the same grey did not read on the phone), a faint beige in light — with the table's own hairline
 * and faint ink. A row's head that needs a HAND (tone `k`) carries a kiremit edge on its leading side; the tone
 * otherwise lives in the number and the sentence. (Earlier cuts: a full band ground per tone tinted
 * half the screen; a band token is the ground of a short note, never of a block.) Blue (`b`, the
 * app's own letter) is the "sıra sizde" mark.
 */
@Composable
private fun srcInk(tone: String?): Color = if (tone == "b") WorkisTheme.colors.blue else srcToneColor(tone)

/** The rows a head opens: the table ground in dark, the surface under `beigeBg` at 16% in light (an opened product's pair). */
@Composable
private fun srcRecess(): Color {
    val colors = WorkisTheme.colors
    return if (colors.isDark) colors.tableGround else colors.beigeBg.copy(alpha = 0.16f).compositeOver(colors.surface)
}

private fun srcMarkIcon(tone: String?): ImageVector = if (tone == "b") WorkisIcons.HandTap else srcToneIcon(tone)

/**
 * Icon + the server's words in the tone's ink. No well: a long sentence in a capsule turned into a
 * blob; it wraps as text. `boxed` draws the quiet rounded well for the short head chips.
 */
@Composable
private fun SrcMark(words: String, tone: String?, boxed: Boolean = false, modifier: Modifier = Modifier, rest: String? = null) {
    val c = srcInk(tone)
    // `rest` (ledger 143): the tone of what follows the sentence's first ": " — the blue stays on
    // the opening words, the product and the reason keep their own tone. The sentence is the
    // server's, printed whole; only its colour is split.
    val restInk = rest?.let { srcInk(it) }
    val cut = if (restInk != null) words.indexOf(": ") else -1
    Row(
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.then(
            if (boxed) Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 4.dp)
            else Modifier,
        ),
    ) {
        Icon(srcMarkIcon(tone), contentDescription = null, tint = c, modifier = Modifier.padding(top = 3.dp).size(11.dp))
        if (restInk != null && cut >= 0) Text(
            buildAnnotatedString {
                append(words.substring(0, cut + 2))
                withStyle(SpanStyle(color = restInk)) { append(words.substring(cut + 2)) }
            },
            fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = c,
        ) else Text(words, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = c)
    }
}

/** A card whose head stands on the card surface (`edge` = the kiremit line down its leading side) and whose body sits one step down. */
@Composable
private fun GroundCard(
    edge: Boolean,
    head: @Composable ColumnScope.() -> Unit,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.border, shape)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { if (edge) drawRect(Kiremit500, size = Size(3.dp.toPx(), size.height)) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = head,
        )
        if (body != null) {
            HorizontalDivider(color = colors.tableBorder)
            Column(
                Modifier.fillMaxWidth().background(srcRecess()).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = body,
            )
        }
    }
}

@Composable
private fun qtyText(i: SourcingItem): String {
    val q = i.qty ?: return ""
    val u = i.unit?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.src_unit_piece)
    return "${WorkisMoney.qty(q)} $u"
}

/** Where a firm nothing was sent to yet came from (`source`) — the web's small word. */
@Composable
private fun sourceWord(f: SourcingFirm): String? = when (f.source) {
    "jowi", "queue" -> stringResource(R.string.src_jowi_proposed)
    "held" -> stringResource(R.string.src_waits_talk)
    "desk" -> stringResource(R.string.src_you_added)
    else -> null
}

private fun placeText(f: SourcingFirm): String? {
    val c = f.city?.takeIf { it.isNotEmpty() } ?: return null
    val d = f.district?.takeIf { it.isNotEmpty() }
    return if (d != null) "$c / $d" else c
}

@Composable
private fun sentLine(f: SourcingFirm): String? {
    if (f.sent != true) return null
    var s = f.sentAt?.takeIf { it.isNotEmpty() }?.let { WorkisClock.stamp(it) }.orEmpty()
    val c = f.chases ?: 0
    if (c > 0) s += (if (s.isEmpty()) "" else " · ") + "+$c " + stringResource(R.string.src_reminders)
    return s.ifEmpty { null }
}

/** A firm's price on a row: mono digits, and "istenen X" under it when the firm wrote another currency than the request's. */
@Composable
private fun PriceCell(p: SourcingPrice, quoteCurrency: String?) {
    val colors = WorkisTheme.colors
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(WorkisMoney.text(p.price, p.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
        val q = quoteCurrency.orEmpty()
        if (q.isNotEmpty() && p.currency.orEmpty() != q) {
            Text(stringResource(R.string.src_asked_in) + " " + q, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirmTitle(f: SourcingFirm, big: Boolean = false) {
    val colors = WorkisTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            f.name.orEmpty(), fontSize = if (big) 16.sp else 14.sp,
            fontWeight = FontWeight.SemiBold, color = colors.ink,
        )
        if (f.isMember == true) SrcSmallChip(stringResource(R.string.src_member))
        if (!big) {
            val p = placeText(f)
            if (p != null) Text(p, fontSize = 12.sp, color = colors.muted)
            else if (f.unplaced == true) SrcSmallChip(stringResource(R.string.src_city_not_written), "k")
        }
        sourceWord(f)?.let { Text("· $it", fontSize = 12.sp, color = colors.tableFaint) }
    }
}

@Composable
private fun AddressLine(f: SourcingFirm) {
    val colors = WorkisTheme.colors
    if (f.onPanel == true) Text(stringResource(R.string.src_panel_notice), fontSize = 12.sp, color = colors.muted)
    else {
        val e = f.email.orEmpty()
        Text(
            e.ifEmpty { stringResource(R.string.src_no_address) }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (f.emailBad == true) colors.accentText else colors.muted,
        )
    }
}

@Composable
private fun FirmWords(f: SourcingFirm, mine: Boolean = false) {
    // the words are the server's; the mark says which lines need a hand (kiremit), which brought a
    // quote (green) and which only wait (quiet) — never colour alone. `mine` (the firm face, where
    // the blue chip leads and the firm stands once): the waiting sentence is the blue mark (ledger 141)
    if (f.sent != true) SrcMark(stringResource(R.string.src_not_sent), "w")
    else f.words?.takeIf { it.isNotEmpty() }?.let { w ->
        SrcMark(
            w, if (f.needs == true) (if (mine) "b" else "k") else if (f.hasQuote == true) "g" else "n",
            rest = if (f.needs == true && mine) "k" else null,
        )
    }
}

/** Name, place, address and send date — what stays beside a price. */
@Composable
private fun FirmFacts(f: SourcingFirm) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FirmTitle(f)
        AddressLine(f)
        sentLine(f)?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
    }
}

@Composable
private fun FirmSummary(f: SourcingFirm) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FirmFacts(f)
        FirmWords(f, mine = true)
    }
}

/** "Bu kalemden çıkar" — only for a firm nothing went out to that stands under more than one row. */
private fun canDrop(f: SourcingFirm): Boolean = f.sent != true && f.itemIds.orEmpty().size > 1 && !f.profileId.isNullOrEmpty()

// ---------- the sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcingSheetPage(
    vm: SourcingSheetViewModel,
    row: SourcingBoardRow,
    face: String,
    onFace: (String) -> Unit,
    onFirm: (String) -> Unit,
) {
    val colors = WorkisTheme.colors
    val s = vm.sheet
    var adding by remember { mutableStateOf<SourcingItem?>(null) }
    val listState = rememberLazyListState()
    // the firm the blue chip leads to: set on its tap, scrolled to once "Firmaya göre" is drawn (ledger 140)
    var jump by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(jump, face) {
        val key = jump ?: return@LaunchedEffect
        if (face != "firm") return@LaunchedEffect
        val at = vm.sheet?.firms.orEmpty().indexOfFirst { it.key == key }
        if (at >= 0) {
            // what stands above the firms: the note, the head, the trial block, the face switch
            val above = (if (vm.note.isNullOrEmpty()) 0 else 1) + 1 + (if (row.trial != null) 1 else 0) + 1
            listState.animateScrollToItem(above + at)
        }
        jump = null
    }
    when {
        s == null && vm.loadError != null -> SrcLoadError(vm.loadError.orEmpty()) { vm.load() }
        s == null -> SrcSpinner()
        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                vm.note?.takeIf { it.isNotEmpty() }?.let { n -> item { SrcNote(n, vm.noteIsError) } }
                item { SheetHead(s, row) { key -> onFace("firm"); jump = key } }
                row.trial?.let { t -> item { TrialSection(vm, t) } }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("item" to R.string.src_by_row, "firm" to R.string.src_by_firm).forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = face == key, onClick = { onFace(key) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.ink.copy(alpha = 0.08f), activeContentColor = colors.ink,
                                    inactiveContainerColor = colors.surface, inactiveContentColor = colors.muted,
                                    activeBorderColor = colors.border, inactiveBorderColor = colors.border,
                                ),
                                icon = {},
                            ) { Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                if (face == "firm") {
                    val firms = s.firms.orEmpty()
                    if (firms.isEmpty()) item { SrcCard { SrcHint(stringResource(R.string.src_no_firm_yet)) } }
                    firms.forEach { f -> item(key = "f" + f.key) { FirmCard(vm, s, f, onFirm) } }
                } else {
                    s.items.orEmpty().forEach { i -> item(key = "i" + i.id) { ItemCard(vm, s, i, onFirm) { adding = i } } }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
    adding?.let { i -> AddFirmDialog(vm, i, withCity = s?.full != false) { adding = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetHead(s: SourcingSheet, row: SourcingBoardRow, onWaiting: (String) -> Unit) {
    val colors = WorkisTheme.colors
    SrcCard {
        Text(row.title.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        val target = s.target?.let { stringResource(R.string.src_target) + " $it " + stringResource(R.string.src_quotes_word) }
        val sub = listOfNotNull(row.buyer?.takeIf { it.isNotEmpty() }, target).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = colors.muted)
        // the web's head chips, worded as the template words them
        val chips = buildList {
            s.unasked?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.src_rows_unasked) to "k") }
            // the desk's own pending move: the screen's one blue
            s.needs?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.src_firms_need) to "b") }
            s.unsent?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.src_firms_unsent) to "n") }
            if (s.full == true) s.unplaced?.takeIf { it > 0 }?.let { add("$it " + stringResource(R.string.src_firms_unplaced) to "k") }
        }
        if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // the blue mark is a door: to the first firm that waits, on the firm face
            val waiting = s.firms.orEmpty().firstOrNull { it.needs == true }?.key?.takeIf { it.isNotEmpty() }
            val hint = stringResource(R.string.src_by_firm)
            chips.forEach { (w, t) ->
                if (t == "b" && waiting != null) SrcMark(
                    w, t, boxed = true,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = hint) { onWaiting(waiting) },
                ) else SrcMark(w, t, boxed = true)
            }
        }
        if (s.ownRegionOnly == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(WorkisIcons.MapPin, contentDescription = null, tint = colors.muted, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.src_own_region), fontSize = 12.sp, color = colors.muted)
        }
    }
}

/** A trial request (`trial-send`): the firms it would reach, each with the address its trial mail goes to. */
@Composable
private fun TrialSection(vm: SourcingSheetViewModel, rows: List<SourcingTrialRow>) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(R.string.src_trial_title))
        SrcCard {
            SrcHint(stringResource(R.string.src_trial_hint))
            if (rows.isEmpty()) Text(stringResource(R.string.src_trial_none), fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
            rows.forEach { r ->
                val key = r.key.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.name.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                    Text(stringResource(R.string.src_trial_own_addr) + ": " + r.email.orEmpty().ifEmpty { "—" }, fontSize = 12.sp, color = colors.faint)
                    SrcField(vm.trialTo[key] ?: r.to.orEmpty(), { vm.trialTo[key] = it }, stringResource(R.string.src_trial_goes_to), KeyboardType.Email)
                }
            }
            if (rows.isNotEmpty()) SrcPrimaryButton(stringResource(R.string.src_send), enabled = !vm.busy, busy = vm.busy) { vm.sendTrial(rows) }
        }
    }
}

// by row

@Composable
private fun ItemCard(vm: SourcingSheetViewModel, s: SourcingSheet, i: SourcingItem, onFirm: (String) -> Unit, onAdd: () -> Unit) {
    val colors = WorkisTheme.colors
    val id = i.id.orEmpty()
    val default = i.state == "none" || (i.unsent ?: 0) > 0 || i.research?.state == "running"
    val isOpen = vm.open["i$id"] ?: default
    GroundCard(
        edge = i.tone == "k",
        head = {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().clickable { vm.open["i$id"] = !isOpen },
            ) {
                ItemHead(i, Modifier.weight(1f))
                Text(if (isOpen) "⌃" else "⌄", fontSize = 16.sp, color = colors.muted, modifier = Modifier.padding(start = 8.dp))
            }
        },
        body = if (!isOpen) null else { {
            val firms = i.firmKeys.orEmpty().mapNotNull { s.firm(it) }
            firms.forEach { f ->
                // the sentence runs the row's full width, under the price column too (ledger 130:
                // beside the price a long sentence broke into five short lines)
                Column(Modifier.fillMaxWidth().clickable { f.key?.let(onFirm) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { FirmFacts(f) }
                        f.price(id)?.let { Spacer(Modifier.width(8.dp)); PriceCell(it, s.quoteCurrency) }
                    }
                    FirmWords(f)
                    if (canDrop(f)) DropButton(vm, f, id)
                }
                HorizontalDivider(color = colors.tableBorder)
            }
            ItemFoot(vm, s, i, onAdd)
        } },
    )
}

@Composable
private fun ItemHead(i: SourcingItem, modifier: Modifier) {
    val colors = WorkisTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${i.n ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = srcInk(i.tone), modifier = Modifier.padding(top = 2.dp))
            Text(i.name.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val spec = listOf(i.spec.orEmpty(), qtyText(i)).filter { it.isNotEmpty() }.joinToString(" · ")
        if (spec.isNotEmpty()) Text(spec, fontSize = 12.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val parts = buildList {
            val n = i.sellers ?: 0
            add(if (n > 0) stringResource(R.string.src_sellers_on) + " $n" else stringResource(R.string.src_no_sellers))
            add(stringResource(R.string.src_asked) + " ${i.asked ?: 0} · " + stringResource(R.string.src_quotes_word) + " ${i.quotes ?: 0}")
            i.unsent?.takeIf { it > 0 }?.let { add(stringResource(R.string.src_not_sent_n) + " $it") }
        }
        Text(parts.joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
        i.sameAs?.let { Text(stringResource(R.string.src_same_as) + " $it", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.ink) }
        i.words?.takeIf { it.isNotEmpty() }?.let { SrcMark(it, i.tone, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** The row's foot: Jowi's search and "Firma ekle", and how the last search went. A trial request has neither. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemFoot(vm: SourcingSheetViewModel, s: SourcingSheet, i: SourcingItem, onAdd: () -> Unit) {
    val colors = WorkisTheme.colors
    if (s.isTrial == true) {
        SrcHint(stringResource(R.string.src_trial_only), color = colors.tableFaint)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (i.research?.state == "running") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kiremit500)
                    Text(stringResource(R.string.src_searching), fontSize = 13.sp, color = colors.muted)
                }
            } else {
                SrcBorderButton(stringResource(R.string.src_research), enabled = !vm.busy, icon = WorkisIcons.Search) { i.id?.let(vm::research) }
            }
            SrcBorderButton(stringResource(R.string.src_add_firm), enabled = !vm.busy, onClick = onAdd)
        }
        i.research?.let { r ->
            when (r.state) {
                "done" -> SrcHint(
                    stringResource(R.string.src_last_search) + " " + WorkisClock.day(r.at.orEmpty()) + " · ${r.found ?: 0} " + stringResource(R.string.src_firms_found),
                    color = colors.tableFaint,
                )
                "failed" -> Text(stringResource(R.string.src_search_failed), fontSize = 12.sp, color = colors.accentText)
                else -> Unit
            }
        }
    }
}

@Composable
private fun DropButton(vm: SourcingSheetViewModel, f: SourcingFirm, itemId: String) {
    SrcBorderButton(stringResource(R.string.src_remove), enabled = !vm.busy) {
        f.profileId?.let { vm.firmAct(it, "drop", jsonBody("item_id" to itemId)) }
    }
}

// by firm

@Composable
private fun FirmCard(vm: SourcingSheetViewModel, s: SourcingSheet, f: SourcingFirm, onFirm: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val key = f.key.orEmpty()
    val ids = f.itemIds.orEmpty()
    GroundCard(
        edge = false, // the waiting firm's blue sentence is its one mark (ledger 141)
        head = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (key.isNotEmpty()) onFirm(key) }) {
                Box(Modifier.weight(1f)) { FirmSummary(f) }
                Text("›", fontSize = 18.sp, color = colors.muted, modifier = Modifier.padding(start = 8.dp))
            }
        },
        body = if (ids.isEmpty()) null else { {
            val isOpen = vm.open["f$key"] ?: (f.needs == true || f.sent != true)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.open["f$key"] = !isOpen }) {
                Text(stringResource(R.string.src_rows_asked) + " · ${ids.size}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted, modifier = Modifier.weight(1f))
                Text(if (isOpen) "⌃" else "⌄", fontSize = 16.sp, color = colors.tableFaint)
            }
            if (isOpen) ids.mapNotNull { s.item(it) }.forEach { i ->
                val iid = i.id.orEmpty()
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${i.n ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.tableFaint)
                            Text(i.name.orEmpty(), fontSize = 13.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val sub = listOf(i.spec.orEmpty(), qtyText(i)).filter { it.isNotEmpty() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                when {
                                    f.sent != true -> R.string.src_will_be_asked
                                    f.price(iid) != null -> R.string.src_quoted
                                    else -> R.string.src_asked_word
                                },
                            ),
                            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.tableFaint,
                        )
                        if (canDrop(f)) DropButton(vm, f, iid)
                    }
                    f.price(iid)?.let { Spacer(Modifier.width(8.dp)); PriceCell(it, s.quoteCurrency) }
                }
            }
        } },
    )
}

// ---------- Firma ekle ----------

@Composable
private fun AddFirmDialog(vm: SourcingSheetViewModel, item: SourcingItem, withCity: Boolean, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.src_add_firm), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.n ?: 0}. ${item.name.orEmpty()}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                SrcField(name, { name = it }, stringResource(R.string.src_firm_name))
                SrcField(email, { email = it }, stringResource(R.string.src_email_new), KeyboardType.Email)
                if (withCity) {
                    SrcField(city, { city = it }, stringResource(R.string.src_city_new))
                    SrcField(district, { district = it }, stringResource(R.string.src_district))
                }
                SrcHint(stringResource(R.string.src_add_firm_hint))
                if (!withCity) SrcHint(stringResource(R.string.src_in_own_region))
                error?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.busy && name.isNotBlank(),
                onClick = {
                    // a field lead sends no city: the firm stands in its own region
                    val body = if (withCity) jsonBody(
                        "item_id" to item.id.orEmpty(), "name" to name.trim(), "email" to email.trim(),
                        "city" to city.trim(), "district" to district.trim().ifEmpty { null },
                    ) else jsonBody("item_id" to item.id.orEmpty(), "name" to name.trim(), "email" to email.trim())
                    vm.addFirm(body) { ok -> if (ok) onClose() else { error = vm.note; vm.note = null } }
                },
            ) { Text(stringResource(R.string.src_add), fontWeight = FontWeight.SemiBold, color = colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}

// ---------- a firm's screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcingFirmPage(vm: SourcingSheetViewModel, firmKey: String) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    LaunchedEffect(firmKey) { vm.note = null }
    var outcome by remember(firmKey) { mutableStateOf("") }
    var fixing by remember { mutableStateOf(false) }
    var emailText by remember { mutableStateOf("") }
    var placing by remember { mutableStateOf(false) }
    var cityText by remember { mutableStateOf("") }
    var districtText by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val inv = vm.sheet?.firm(firmKey)?.invitationId?.takeIf { it.isNotEmpty() } ?: return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "reply"
        vm.upload(inv, bytes, name, mime)
    }

    val s = vm.sheet
    val f = s?.firm(firmKey)
    when {
        s == null && vm.loadError != null -> SrcLoadError(vm.loadError.orEmpty()) { vm.load() }
        s == null -> SrcSpinner()
        // the firm left the sheet (removed from its last row, …)
        f == null -> SrcCard { SrcHint(stringResource(R.string.src_no_firm_yet)) }
        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                vm.note?.takeIf { it.isNotEmpty() }?.let { n -> item { SrcNote(n, vm.noteIsError) } }
                item {
                    FirmHeadSection(
                        vm, f,
                        onPlace = { cityText = f.city.orEmpty(); districtText = f.district.orEmpty(); placing = true },
                        onFix = { emailText = f.email.orEmpty(); fixing = true },
                    )
                }
                if (f.sent != true && s.isTrial != true) item { SendSection(vm, f, outcome) { outcome = it } }
                item { OutSection(vm, s, f) }
                item { InSection(vm, s, f) }
                item { ItemsSection(vm, s, f) }
                item { ActsSection(vm, f) { picker.launch(arrayOf("application/pdf", "image/*", "text/plain", "message/rfc822")) } }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    if (fixing) AlertDialog(
        onDismissRequest = { fixing = false },
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.src_email), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink) },
        text = { SrcField(emailText, { emailText = it }, stringResource(R.string.src_email), KeyboardType.Email) },
        confirmButton = {
            TextButton(onClick = {
                fixing = false
                f?.profileId?.takeIf { it.isNotEmpty() }?.let { vm.firmAct(it, "email", jsonBody("email" to emailText.trim())) }
            }) { Text(stringResource(R.string.src_save), fontWeight = FontWeight.SemiBold, color = colors.accentText) }
        },
        dismissButton = { TextButton(onClick = { fixing = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
    if (placing) AlertDialog(
        onDismissRequest = { placing = false },
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.src_write_city), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SrcHint(stringResource(R.string.src_no_city_hint))
                SrcField(cityText, { cityText = it }, stringResource(R.string.src_city))
                SrcField(districtText, { districtText = it }, stringResource(R.string.src_district))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                placing = false
                f?.profileId?.takeIf { it.isNotEmpty() }?.let {
                    vm.firmAct(it, "place", jsonBody("city" to cityText.trim(), "district" to districtText.trim().ifEmpty { null }))
                }
            }) { Text(stringResource(R.string.src_save), fontWeight = FontWeight.SemiBold, color = colors.accentText) }
        },
        dismissButton = { TextButton(onClick = { placing = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}

@Composable
private fun FirmHeadSection(vm: SourcingSheetViewModel, f: SourcingFirm, onPlace: () -> Unit, onFix: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(R.string.src_firm))
        SrcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FirmTitle(f, big = true)
                val w = f.words.orEmpty()
                if (f.sent == true) {
                    if (w.isNotEmpty()) { if (f.needs == true) SrcMark(w, "b", rest = "k") else Text(w, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
                } else Text(w.ifEmpty { stringResource(R.string.src_not_sent) }, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink)
            }
            HorizontalDivider(color = colors.border)
            // where it stands
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(WorkisIcons.MapPin, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
                Box(Modifier.weight(1f)) {
                    val p = placeText(f)
                    if (p != null) Text(p, fontSize = 14.sp, color = colors.ink)
                    else SrcSmallChip(stringResource(R.string.src_city_not_written), "k")
                }
                if (f.canPlace == true) SrcBorderButton(
                    stringResource(if (f.unplaced == true) R.string.src_write_city else R.string.src_fix_city), enabled = !vm.busy, onClick = onPlace,
                )
            }
            // the address written to
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (f.onPanel == true) WorkisIcons.Tray else WorkisIcons.Bubble, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
                Box(Modifier.weight(1f)) {
                    if (f.onPanel == true) Text(stringResource(R.string.src_panel_notice), fontSize = 14.sp, color = colors.muted)
                    else Text(
                        f.email.orEmpty().ifEmpty { stringResource(R.string.src_no_address) }, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (f.emailBad == true) colors.accentText else colors.ink,
                    )
                }
                if (f.canFixEmail == true) SrcBorderButton(
                    stringResource(if (f.email.isNullOrEmpty()) R.string.src_write_address else R.string.src_fix), enabled = !vm.busy, onClick = onFix,
                )
            }
            sentLine(f)?.let { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(WorkisIcons.ArrowRightCircle, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
                    Text(stringResource(R.string.src_sent_word) + " " + line, fontSize = 13.sp, color = colors.muted)
                }
            }
        }
        if (f.unplaced == true && f.canPlace == true) SrcHint(stringResource(R.string.src_no_city_hint), Modifier.padding(horizontal = 4.dp))
    }
}

/** Gönder — one mail with every row the firm stands under. Where a talk is owed (`askOutcome`) its outcome is picked first. */
@Composable
private fun SendSection(vm: SourcingSheetViewModel, f: SourcingFirm, outcome: String, onOutcome: (String) -> Unit) {
    val colors = WorkisTheme.colors
    val ask = f.askOutcome == true
    val blocked = (ask && outcome.isEmpty()) || (f.canSend != true && !(ask && outcome == "declined"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcCard {
            if (ask) {
                Text(stringResource(R.string.src_outcome), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                listOf(
                    "member" to R.string.src_outcome_member,
                    "one_off" to R.string.src_outcome_one_off,
                    "declined" to R.string.src_outcome_declined,
                ).forEach { (key, label) ->
                    val on = outcome == key
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(if (on) 1.5.dp else 1.dp, if (on) Kiremit500 else colors.border, RoundedCornerShape(12.dp))
                            .clickable { onOutcome(key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Box(
                            Modifier.size(16.dp).border(1.5.dp, if (on) Kiremit500 else colors.faint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(8.dp).background(Kiremit500, CircleShape)) }
                        Text(stringResource(label), fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = colors.ink)
                    }
                }
                if (outcome.isEmpty()) SrcHint(stringResource(R.string.src_outcome_pick))
            }
            val send = {
                f.profileId?.takeIf { it.isNotEmpty() }?.let { pid ->
                    vm.firmAct(pid, "send", jsonBody("outcome" to outcome.ifEmpty { null })) { ok -> if (ok) onOutcome("") }
                }
                Unit
            }
            if (f.canApprove != true) SrcPrimaryButton(stringResource(R.string.src_send), enabled = !blocked && !vm.busy, busy = vm.busy, onClick = send)
            else SrcBorderButton(stringResource(R.string.src_send), Modifier.fillMaxWidth(), enabled = !blocked && !vm.busy, onClick = send)
        }
        SrcHint(stringResource(R.string.src_send_hint), Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun OutSection(vm: SourcingSheetViewModel, s: SourcingSheet, f: SourcingFirm) {
    val colors = WorkisTheme.colors
    val full = s.full == true
    if (f.sent != true || (f.out == null && f.hasForm != true && !full)) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(R.string.src_sent_mail))
        SrcCard {
            val o = f.out
            if (o != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    o.to?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.src_to) + " " + it, fontSize = 12.sp, color = colors.faint) }
                    o.subject?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink) }
                    o.body?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
                }
            } else if (full) SrcHint(stringResource(if (f.onPanel == true) R.string.src_panel_asked else R.string.src_out_not_kept))
            val inv = f.invitationId.orEmpty()
            if (f.hasForm == true && inv.isNotEmpty()) {
                FileRow(vm, stringResource(R.string.src_request_form) + " " + f.formNumber.orEmpty(), inv, "form")
            }
        }
    }
}

@Composable
private fun InSection(vm: SourcingSheetViewModel, s: SourcingSheet, f: SourcingFirm) {
    val colors = WorkisTheme.colors
    val mails = f.mails.orEmpty()
    if (f.sent != true || (mails.isEmpty() && f.hasReplyFile != true && s.full != true)) return
    val inv = f.invitationId.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(R.string.src_received_mail))
        SrcCard {
            if (mails.isEmpty()) SrcHint(stringResource(R.string.src_no_reply_kept))
            mails.forEachIndexed { k, m ->
                if (k > 0) HorizontalDivider(color = colors.border)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val head = listOfNotNull(m.at?.takeIf { it.isNotEmpty() }?.let { WorkisClock.stamp(it) }, m.from?.takeIf { it.isNotEmpty() }).joinToString(" · ")
                    if (head.isNotEmpty()) Text(head, fontSize = 12.sp, color = colors.faint)
                    m.subject?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink) }
                    m.body?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink) }
                    m.files.orEmpty().forEach { x ->
                        val label = stringResource(R.string.src_file) + ": " + x.name.orEmpty()
                        if (x.canOpen == true && inv.isNotEmpty()) FileRow(vm, label, inv, "m${m.n ?: 0}-${x.n ?: 0}")
                        else Text(label + " — " + stringResource(R.string.src_not_kept), fontSize = 12.sp, color = colors.faint)
                    }
                }
            }
            if (f.hasReplyFile == true && inv.isNotEmpty()) {
                if (mails.isNotEmpty()) HorizontalDivider(color = colors.border)
                FileRow(vm, stringResource(R.string.src_reply_file) + ": " + f.replyFileName.orEmpty(), inv, "reply")
            }
        }
    }
}

/** A file of the firm's: muted ink with its glyph (a document row is never an accent), the spinner while it comes. */
@Composable
private fun FileRow(vm: SourcingSheetViewModel, title: String, invitationId: String, what: String) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = vm.opening == null) { vm.openFile(invitationId, what) }
            .padding(vertical = 4.dp),
    ) {
        Icon(WorkisIcons.DocText, contentDescription = null, tint = colors.muted, modifier = Modifier.size(15.dp))
        Text(title, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (vm.opening == what) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kiremit500)
    }
}

/** The rows the firm stands under, with its price for each (none for the field lead), and its doubts. */
@Composable
private fun ItemsSection(vm: SourcingSheetViewModel, s: SourcingSheet, f: SourcingFirm) {
    val colors = WorkisTheme.colors
    val ids = f.itemIds.orEmpty()
    val anyPrice = ids.any { f.price(it) != null }
    val doubts = f.doubts.orEmpty()
    if (ids.isEmpty() && doubts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(if (anyPrice) R.string.src_quote else R.string.src_rows_asked))
        SrcCard {
            ids.mapNotNull { s.item(it) }.forEachIndexed { k, i ->
                if (k > 0) HorizontalDivider(color = colors.border)
                val iid = i.id.orEmpty()
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${i.n ?: 0}.", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                            Text(i.name.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                        }
                        f.price(iid)?.name?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = colors.muted) }
                        qtyText(i).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
                        if (canDrop(f)) DropButton(vm, f, iid)
                    }
                    f.price(iid)?.let { Spacer(Modifier.width(8.dp)); PriceCell(it, s.quoteCurrency) }
                }
            }
            doubts.forEach { d ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(WorkisIcons.Warning, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text(stringResource(R.string.src_doubt) + ": " + d, fontSize = 13.sp, lineHeight = 18.sp, color = colors.accentText)
                }
            }
        }
        if (anyPrice) SrcHint(stringResource(R.string.src_no_vat), Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun ActsSection(vm: SourcingSheetViewModel, f: SourcingFirm, onUpload: () -> Unit) {
    val quoteId = f.draftQuoteId.orEmpty()
    val approve = f.canApprove == true && quoteId.isNotEmpty()
    val upload = f.canUploadReply == true && !f.invitationId.isNullOrEmpty()
    if (!approve && !upload) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SrcSectionTitle(stringResource(R.string.src_acts))
        if (upload) SrcBorderButton(stringResource(R.string.src_upload_reply), Modifier.fillMaxWidth(), enabled = !vm.busy, icon = WorkisIcons.DocPlus, onClick = onUpload)
        if (approve) SrcPrimaryButton(stringResource(R.string.src_approve), enabled = !vm.busy, busy = vm.busy) { vm.approve(quoteId) }
    }
}
