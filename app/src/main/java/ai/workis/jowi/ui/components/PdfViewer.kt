package ai.workis.jowi.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.absoluteWorkisUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PaperWhite = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF1A1A1F)

/** Hand a PDF to other apps through the system sheet (save / print / send). */
fun sharePdf(context: Context, data: ByteArray, fileName: String) {
    val file = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, fileName) }.apply { writeBytes(data) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/**
 * A server PDF opened natively (token-authorized — the shared client sends the
 * session header), rendered page by page, shareable. `data` skips the fetch
 * (an in-memory preview). White in both schemes: pen-and-paper.
 */
@Composable
fun PdfViewerDialog(title: String, url: String? = null, data: ByteArray? = null, fileName: String = "workis.pdf", onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bytes by remember { mutableStateOf(data) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (bytes == null && url != null) {
            val b = runCatching { withContext(Dispatchers.IO) { Graph.api.download(absoluteWorkisUrl(url)).use { it.bytes() } } }.getOrNull()
            if (b != null && looksLikePdf(b)) bytes = b else failed = true
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(PaperWhite).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.done), tint = PaperInk) }
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PaperInk, modifier = Modifier.weight(1f))
                bytes?.let { b ->
                    IconButton(onClick = { sharePdf(context, b, fileName) }) { Icon(Icons.Filled.Share, contentDescription = null, tint = PaperInk) }
                }
            }
            val b = bytes
            when {
                b != null -> PdfPages(b, Modifier.fillMaxSize(), spinnerColor = PaperInk)
                failed -> Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.contract_load_fail), color = PaperInk.copy(alpha = 0.7f), fontSize = 14.sp)
                    TextButton(onClick = { failed = false; bytes = null }) { Text(stringResource(R.string.retry), color = PaperInk) }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PaperInk) }
            }
        }
    }
}
