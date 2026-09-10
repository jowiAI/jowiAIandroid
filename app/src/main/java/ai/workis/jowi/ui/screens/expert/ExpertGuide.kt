package ai.workis.jowi.ui.screens.expert

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.ExpertGuide
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.components.MarkdownDocument
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.components.looksLikePdf
import ai.workis.jowi.ui.components.openInApp
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExpertGuideViewModel : ViewModel() {
    var guide by mutableStateOf<ExpertGuide?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sharing by mutableStateOf(false)
        private set

    fun load() {
        error = null
        viewModelScope.launch {
            when (val r = safeCall(Graph.language.value) { Graph.api.expertGuide() }) {
                is ApiResult.Ok -> guide = r.value
                is ApiResult.Err -> error = r.message
            }
        }
    }

    /** The guide as ONE PDF through the system share sheet (save / print / send); the page is the fallback. */
    fun share(context: Context, pdfUrl: String) {
        if (sharing) return
        sharing = true
        viewModelScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = Graph.api.download(pdfUrl).use { it.bytes() }
                    require(looksLikePdf(bytes))
                    File(context.cacheDir, "share").apply { mkdirs() }
                        .let { File(it, "workis-uzman-rehberi.pdf") }
                        .apply { writeBytes(bytes) }
                }
            }.getOrNull()
            sharing = false
            if (file == null) { openInApp(context, pdfUrl); return@launch }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        }
    }
}

/**
 * Rehber — the guide's text lives ONCE on the server as Markdown sections;
 * this screen renders whatever arrives (order and count are the server's)
 * in the Workis type, with the section picker on top and the PDF one tap
 * away for sharing. Never re-authored here.
 */
@Composable
fun ExpertGuideScreen(pdfUrl: String?, onBack: () -> Unit, vm: ExpertGuideViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (vm.guide == null) vm.load() }
    var selected by rememberSaveable { mutableStateOf("") }
    val guide = vm.guide
    val sections = guide?.sections.orEmpty().filter { it.key != null }

    ConsoleSub(
        title = stringResource(R.string.expert_guide_title),
        onBack = onBack,
        trailing = {
            if (!pdfUrl.isNullOrEmpty()) {
                IconButton(onClick = { vm.share(context, pdfUrl) }, enabled = !vm.sharing) {
                    if (vm.sharing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                    else Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.guide_share), tint = colors.accentText)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when {
                guide != null -> {
                    val current = sections.firstOrNull { it.key == selected } ?: sections.firstOrNull()
                    if (sections.size > 1) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            sections.forEachIndexed { i, sec ->
                                SegmentedButton(
                                    selected = current?.key == sec.key,
                                    onClick = { selected = sec.key.orEmpty() },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = sections.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = colors.surface, activeContentColor = colors.ink,
                                        inactiveContainerColor = colors.canvas, inactiveContentColor = colors.muted,
                                    ),
                                ) { Text(sec.title.orEmpty(), fontSize = 13.sp, maxLines = 1) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    current?.let { MarkdownDocument(it.markdown.orEmpty()) }
                    guide.version?.let {
                        Text("v$it", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                vm.error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.error.orEmpty(), color = colors.danger, fontSize = 14.sp)
                    TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry), color = colors.accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
