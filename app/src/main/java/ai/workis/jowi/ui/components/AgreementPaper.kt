package ai.workis.jowi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.AgreementSignatures
import ai.workis.jowi.data.absoluteWorkisUrl
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fetches the agreement metadata (version · date · sha · urls) from
 * GET /api/v1/workis/agreement/?role=|key= and then the blank PDF — the same
 * pick the web paper makes, so a carrier signs the carrier text and an expert
 * the expert text (the old hard-coded supplier PDF was the bug). Owned by the
 * screen's ViewModel so it can prefetch before the paper shows.
 */
class AgreementLoader(private val scope: CoroutineScope) {
    data class Meta(
        val version: Int,
        val sha: String,
        val createdAt: String,
        val pdfUrl: String,
        val pageUrl: String,
        /** The signature cards (2026-09-16): who signed for the Platform, the party's acceptance or null. */
        val signatures: AgreementSignatures? = null,
    ) {
        /** "v1 · 07.09.2026 · #c6e20ca0a828" — the web letterhead's line. */
        val line: String
            get() {
                val ymd = createdAt.take(10).split("-")
                val date = if (ymd.size == 3) "${ymd[2]}.${ymd[1]}.${ymd[0]}" else ""
                return listOf("v$version", date, if (sha.isEmpty()) "" else "#$sha")
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            }
    }

    var meta by mutableStateOf<Meta?>(null)
        private set
    var pdf by mutableStateOf<ByteArray?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    private var query: Pair<String, String>? = null
    private var job: Job? = null

    /** `role=buyer|maker|both|carrier` — the wizard's pick. */
    fun loadRole(role: String) = load("role" to role)

    /** `key=supplier|carrier|expert` — a fixed text. */
    fun loadKey(key: String) = load("key" to key)

    fun retry() {
        val q = query ?: return
        query = null
        load(q)
    }

    private fun load(q: Pair<String, String>) {
        if (q != query) { // the role changed under us — start over
            query = q
            job?.cancel()
            job = null
            pdf = null
            meta = null
            failed = false
        }
        if (pdf != null || job?.isActive == true) return
        failed = false
        job = scope.launch {
            try {
                val m = if (q.first == "role") Graph.api.agreement(role = q.second)
                else Graph.api.agreement(key = q.second)
                val pdfPath = m.pdfUrl
                if (pdfPath.isNullOrEmpty()) { failed = true; return@launch }
                val pdfUrl = absoluteWorkisUrl(pdfPath)
                meta = Meta(
                    version = m.version ?: 0,
                    sha = m.sha.orEmpty(),
                    createdAt = m.createdAt.orEmpty(),
                    pdfUrl = pdfUrl,
                    pageUrl = absoluteWorkisUrl(m.pageUrl ?: pdfPath),
                    signatures = m.signatures,
                )
                val bytes = withContext(Dispatchers.IO) { Graph.api.download(pdfUrl).use { it.bytes() } }
                if (looksLikePdf(bytes)) pdf = bytes else failed = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
        }
    }
}

// Pen-and-paper fixed colors: the paper is white in BOTH schemes.
private val PaperWhite = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF1A1A1F)
private val PenBlue = Color(0xFF1A47B3)
private val CellYellow = Color(0xFFFFF9D4)
private val CellEdge = Color(0xFFE0C240)

/**
 * The agreement "paper" shared by the wizard's step 04 and the expert screen:
 * letterhead (mark + version line), the real PDF scrolling inside, the
 * 1040-style Sign-Here band (typed name → pen-blue handwriting, stamped date).
 * `onSignerCommit` fires when focus leaves the signature cell (draft autosave).
 */
@Composable
fun AgreementPaper(
    loader: AgreementLoader,
    signerName: String,
    onSignerChange: (String) -> Unit,
    onSignerCommit: () -> Unit = {},
    /** Hesap → Sözleşmem: the accepted paper, nothing to type. */
    readOnly: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .background(PaperWhite, RoundedCornerShape(12.dp)),
    ) {
        // Letterhead — mark left, version line right (web parity)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kiremit500)
            Text("workis", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PaperInk)
            Spacer(Modifier.weight(1f))
            loader.meta?.let {
                Text(it.line, fontFamily = WorkisMono, fontSize = 10.sp, color = PaperInk.copy(alpha = 0.55f))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(2.dp)
                .background(PaperInk),
        )

        // Agreement body (real PDF, scrolls within the paper)
        val pdf = loader.pdf
        when {
            pdf != null -> PdfPages(
                pdf,
                modifier = Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 6.dp),
                spinnerColor = PaperInk.copy(alpha = 0.5f),
            )
            loader.failed -> Column(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.contract_load_fail),
                    fontSize = 14.sp,
                    color = PaperInk.copy(alpha = 0.6f),
                )
                TextButton(onClick = { loader.retry() }) {
                    Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PenBlue)
                }
            }
            else -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PaperInk.copy(alpha = 0.5f))
            }
        }

        SignatureCards(loader.meta?.signatures, signerName, onSignerChange, onSignerCommit, readOnly)
    }
}

/**
 * The two cards under the text, like the web page (2026-09-16): the
 * Platform's electronic signature and the party's acceptance — or, while
 * signing, the Sign-Here band inside the party card.
 */
@Composable
private fun SignatureCards(
    sig: AgreementSignatures?,
    signerName: String,
    onSignerChange: (String) -> Unit,
    onSignerCommit: () -> Unit,
    readOnly: Boolean,
) {
    val cardShape = RoundedCornerShape(8.dp)
    val faint = PaperInk.copy(alpha = 0.55f)
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sig?.platform?.let { p ->
            Column(
                Modifier.fillMaxWidth().border(1.dp, PaperInk.copy(alpha = 0.18f), cardShape).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CardLabel(stringResource(R.string.sig_platform))
                Text(stringResource(R.string.sig_platform_signed), fontSize = 11.sp, color = PaperInk.copy(alpha = 0.6f))
                Text(
                    p.name.orEmpty(),
                    fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, color = PaperInk,
                )
                p.title?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, lineHeight = 15.sp, color = PaperInk.copy(alpha = 0.7f)) }
                Text(
                    listOf("v${p.version ?: 0}", shortDate(p.signedAt), p.sha?.takeIf { it.isNotEmpty() }?.let { "#$it" } ?: "")
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    fontFamily = WorkisMono, fontSize = 9.sp, color = faint,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, PaperInk.copy(alpha = 0.18f), cardShape).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            CardLabel(sig?.party?.label?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.sign_cell_label))
            val a = sig?.party?.acceptance
            if (a != null && !a.signerName.isNullOrEmpty()) {
                Text(stringResource(R.string.sig_accepted), fontSize = 11.sp, color = PaperInk.copy(alpha = 0.6f))
                Text(
                    a.signerName,
                    fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, color = PenBlue,
                )
                Text(
                    listOf(shortDate(a.acceptedAt), a.ip.orEmpty(), a.sha?.takeIf { it.isNotEmpty() }?.let { "#$it v${a.version ?: 0}" } ?: "")
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    fontFamily = WorkisMono, fontSize = 9.sp, color = faint,
                )
            } else {
                Text(stringResource(R.string.sig_awaiting), fontSize = 11.sp, color = PaperInk.copy(alpha = 0.6f))
                SignBand(signerName, onSignerChange, onSignerCommit, Modifier.padding(top = 4.dp), readOnly)
            }
        }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text.uppercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 8.sp,
        letterSpacing = 1.2.sp, color = PaperInk.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The 1040-style Sign-Here band, for any paper that needs a typed signature. */
@Composable
fun SignHereBand(
    signerName: String,
    onSignerChange: (String) -> Unit,
    onSignerCommit: () -> Unit = {},
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) = SignBand(signerName, onSignerChange, onSignerCommit, modifier, readOnly)

@Composable
private fun SignBand(
    signerName: String,
    onSignerChange: (String) -> Unit,
    onSignerCommit: () -> Unit,
    modifier: Modifier,
    readOnly: Boolean = false,
) {
    var hadFocus by remember { mutableStateOf(false) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(PaperInk))
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            // dark "SIGN HERE ▸" tab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight().background(PaperInk).padding(horizontal = 10.dp),
            ) {
                Text(
                    stringResource(R.string.sign_here),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = PaperWhite, maxLines = 1,
                )
                Spacer(Modifier.width(5.dp))
                Text("▸", fontSize = 9.sp, color = PaperWhite)
            }
            // yellow fill-me cell
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(CellYellow)
                    .border(2.dp, CellEdge)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.sign_cell_label),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 8.sp,
                    letterSpacing = 0.3.sp, color = PaperInk.copy(alpha = 0.55f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicTextField(
                    value = signerName,
                    onValueChange = onSignerChange,
                    singleLine = true,
                    readOnly = readOnly,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Cursive,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = PenBlue,
                    ),
                    cursorBrush = SolidColor(PenBlue),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true
                            else if (hadFocus) { hadFocus = false; onSignerCommit() }
                        },
                )
            }
            // auto-stamped date
            Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.sign_date),
                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 8.sp,
                    letterSpacing = 1.sp, color = PaperInk.copy(alpha = 0.55f),
                )
                Text(
                    remember { SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date()) },
                    fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PaperInk,
                )
                Text(
                    stringResource(R.string.sign_date_caption),
                    fontFamily = WorkisMono, fontSize = 8.sp, color = PaperInk.copy(alpha = 0.45f),
                )
            }
        }
    }
}

