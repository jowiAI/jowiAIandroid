package ai.workis.jowi.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.workis.jowi.ui.theme.PaperWhite
import ai.workis.jowi.Graph
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Token-authorized image cache for the seat-walled renditions
 * (`image/?k=&r=`, `source/{iid}/{page}/`): keys never change, so a
 * fetched bitmap stays for the process lifetime. The shared client sends
 * the session header; decoding caps the long edge so a source page never
 * blows the heap.
 */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val locks = HashMap<String, Mutex>()

    suspend fun load(url: String, maxEdge: Int = 1600): Bitmap? {
        cache.get(url)?.let { return it }
        val lock = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        return lock.withLock {
            cache.get(url)?.let { return@withLock it }
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = Graph.api.download(url).use { it.bytes() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            }
            if (bmp != null) cache.put(url, bmp)
            bmp
        }
    }
}

/** A seat-walled image on a white tile; a quiet placeholder while loading or when the key is empty. */
@Composable
fun AuthImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp, contentScale: ContentScale = ContentScale.Fit) {
    val colors = WorkisTheme.colors
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url == null) null else ImageCache.load(url)
    }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(PaperWhite.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize().padding(2.dp))
        else Icon(WorkisIcons.Photo, contentDescription = null, tint = colors.faint, modifier = Modifier.size(14.dp))
    }
}
