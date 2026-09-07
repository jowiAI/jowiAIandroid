package ai.workis.jowi.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val MAX_PAGE_WIDTH_PX = 1000
private const val MAX_PAGES = 40

/** Sniff the header — a 200 that is an HTML error page must not reach the renderer. */
fun looksLikePdf(bytes: ByteArray): Boolean =
    bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

/** Rasterize every page at the given width (PdfRenderer needs a seekable file, hence the temp copy). */
suspend fun renderPdfPages(context: Context, data: ByteArray, widthPx: Int): List<Bitmap> =
    withContext(Dispatchers.IO) {
        val file = File.createTempFile("workis-pdf", ".pdf", context.cacheDir)
        try {
            file.writeBytes(data)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    (0 until minOf(renderer.pageCount, MAX_PAGES)).map { i ->
                        renderer.openPage(i).use { page ->
                            val h = (widthPx * page.height / page.width.toFloat()).roundToInt().coerceAtLeast(1)
                            Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

/** A PDF as a vertical stack of page bitmaps scrolling inside `modifier`'s bounds (paper body / viewer). */
@Composable
fun PdfPages(data: ByteArray, modifier: Modifier = Modifier, spinnerColor: Color = Color.Gray) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            .coerceIn(1, MAX_PAGE_WIDTH_PX)
        val context = LocalContext.current
        val pages by produceState<List<Bitmap>?>(initialValue = null, data, widthPx) {
            value = runCatching { renderPdfPages(context, data, widthPx) }.getOrElse { emptyList() }
        }
        val p = pages
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = spinnerColor)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(p) { bmp ->
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}
