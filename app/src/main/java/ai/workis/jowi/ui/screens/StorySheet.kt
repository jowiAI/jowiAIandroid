package ai.workis.jowi.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.ApplyAskBody
import ai.workis.jowi.data.PublicStory
import ai.workis.jowi.data.PublicStoryPage
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.components.AnswerText
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// "Nasıl çalışır?" — the homepage's twelve-page story on the login's public Jowi (ledger 60).
// Content from `GET public/home/`: twelve pages on the nine-step order spine. Opened from the
// `how` card or from the story offer under a free answer — a SEPARATE task from asking, so it
// rises as a sheet; the pages are sequential steps of ONE task, so they page horizontally.
// A question typed on a page goes to the public door with `story: <page id>`.

private data class StoryTurn(val q: String, val a: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorySheet(story: PublicStory, startAt: String? = null, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pages = story.pages
    val start = startAt?.let { id -> pages.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
    val pager = rememberPagerState(initialPage = start) { pages.size }

    // per-page drafts and threads, kept while the sheet is open
    val drafts = remember { mutableStateMapOf<String, String>() }
    val threads = remember { mutableStateMapOf<String, List<StoryTurn>>() }
    val asking = remember { mutableStateMapOf<String, Boolean>() }
    val fail = stringResource(R.string.apply_ask_fail)

    fun ask(page: PublicStoryPage) {
        val q = (drafts[page.id] ?: "").trim()
        if (q.isEmpty() || asking[page.id] == true) return
        drafts[page.id] = ""
        asking[page.id] = true
        scope.launch {
            val lang = Graph.language.value
            val r = safeCall(lang) {
                Graph.api.applyAsk(
                    ApplyAskBody(q = q, lang = lang, screen = "public.login", story = page.id, vk = Graph.visitorKey),
                )
            }
            val a = (r as? ApiResult.Ok)?.value?.text?.takeIf { it.isNotBlank() } ?: fail
            threads[page.id] = (threads[page.id] ?: emptyList()) + StoryTurn(q, a)
            asking.remove(page.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = colors.canvas,
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            ) {
                Text(
                    stringResource(R.string.story_eyebrow),
                    fontFamily = WorkisMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
                }) {
                    Icon(
                        WorkisIcons.Cross,
                        contentDescription = stringResource(R.string.story_close),
                        tint = colors.muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { pages[it].id },
            ) { index ->
                val page = pages[index]
                StoryPage(
                    page = page,
                    turns = threads[page.id] ?: emptyList(),
                    asking = asking[page.id] == true,
                    draft = drafts[page.id] ?: "",
                    onDraft = { drafts[page.id] = it },
                    onSend = { ask(page) },
                )
            }
            // page dots: the current one ink, the others hairline
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (i == pager.currentPage) colors.ink else colors.border, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryPage(
    page: PublicStoryPage,
    turns: List<StoryTurn>,
    asking: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    // Turkish upper-casing needs the locale ("blokesi" → "BLOKESİ", not "BLOKESI")
    val locale = Locale.forLanguageTag(lang)
    val spine = "${stringResource(R.string.story_step)} ${page.step} · ${page.stepLabel}".uppercase(locale)
    val card = RoundedCornerShape(20.dp)

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        // the place on the spine: "ADIM 4 · ÖDEME BLOKESİ" + its status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spine,
                fontFamily = WorkisMono,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.faint,
                modifier = Modifier.weight(1f, fill = false),
            )
            val chip = when (page.status) {
                "planned" -> stringResource(R.string.story_soon)
                "partly" -> stringResource(R.string.story_partly)
                else -> null
            }
            chip?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    it,
                    fontFamily = WorkisSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Beige,
                    modifier = Modifier
                        .background(BeigeBg, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(page.feature, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
        Text(page.hook, fontFamily = WorkisSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = colors.ink)
        Text(page.what, fontFamily = WorkisSans, fontSize = 15.sp, color = colors.muted)

        // the screen the page shows: a real screenshot when the server has one, nothing otherwise
        page.frameUrl?.takeIf { it.isNotBlank() }?.let { url -> StoryFrame(url) }

        // what Jowi would say on that screen — the sample line, in Jowi's voice
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, card)
                .border(1.dp, colors.border, card)
                .padding(14.dp),
        ) {
            Text(page.screenTitle, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.faint)
            Row {
                Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.accentText)
                Spacer(Modifier.width(8.dp))
                Text(page.jowi, fontFamily = WorkisSans, fontSize = 15.sp, color = colors.ink)
            }
        }

        // questions asked on this page, answered with the page's facts leading
        turns.forEach { turn ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, card)
                    .border(1.dp, colors.border, card)
                    .padding(12.dp),
            ) {
                Text(turn.q, fontFamily = WorkisSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.accentText)
                AnswerText(turn.a, color = colors.ink, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        if (asking) {
            Text("Jowi …", fontFamily = WorkisSans, fontSize = 14.sp, color = colors.faint)
        }

        StoryAskField(draft, onDraft, onSend)
    }
}

/** "Bu sayfa hakkında sorun…" — the page's own field, sent from the keyboard. */
@Composable
private fun StoryAskField(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(horizontal = 14.dp),
    ) {
        Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.accentText)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.story_ask), fontFamily = WorkisSans, fontSize = 15.sp, color = colors.faint)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = WorkisSans, fontSize = 15.sp, color = colors.ink).readingStep(),
                cursorBrush = SolidColor(colors.accentText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The page's frame: fetched once, fit to width; nothing drawn until it loads or if it fails. */
@Composable
private fun StoryFrame(url: String) {
    val colors = WorkisTheme.colors
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = java.net.URL(url).readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        val shape = RoundedCornerShape(16.dp)
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, colors.border, shape),
        )
    }
}
