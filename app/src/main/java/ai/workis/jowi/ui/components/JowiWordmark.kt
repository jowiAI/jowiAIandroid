package ai.workis.jowi.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.workis.jowi.ui.theme.WorkisDarkColors
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * The PUBLIC brand door (`https://workis.ai/workis/brand/<file>`, contract "Panel
 * renewal"): a 302 to wherever static lives — never the bucket by name. The folder
 * is versioned by name, so a file once on disk is never fetched again. A plain
 * client on purpose: these are public files and must not carry the session token
 * (`ImageCache` goes through the API client and does).
 */
object BrandImages {
    private const val BASE = "https://workis.ai/workis/brand/"
    private val client by lazy { OkHttpClient() }
    private val memory = LruCache<String, Bitmap>(8)
    private val locks = HashMap<String, Mutex>()

    fun url(file: String) = BASE + file

    suspend fun load(context: Context, url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val lock = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        return lock.withLock {
            memory.get(url)?.let { return@withLock it }
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "brand").apply { mkdirs() }
                    val file = File(dir, sha1(url))
                    if (!file.exists()) {
                        val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                            if (!r.isSuccessful) return@runCatching null
                            r.body.bytes()
                        }
                        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) return@runCatching null
                        // written whole, then renamed: a half file never reads as the mark
                        val tmp = File(dir, file.name + ".tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(file)
                    }
                    BitmapFactory.decodeFile(file.path)
                }.getOrNull()
            }
            if (bmp != null) memory.put(url, bmp)
            bmp
        }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Jowi's handwritten wordmark — the real lettering, an outline raster the server
 * serves (ink stroke, transparent inside, the AI star on the "i" already in the
 * file). Two tints: "ink-dark" for a LIGHT scheme, "ink-light" for a DARK one —
 * read off the Workis palette in force, so the app's own appearance override counts.
 *
 * No image, no mark: nothing is laid out until the file is in hand, and a failed
 * fetch stays invisible (a font stand-in would be the app drawing a logo).
 * Never a background.
 *
 * [write]: the mark "writes itself" once, left to right — a mask sweep with a soft
 * leading edge, the reference GIF's motion done locally (iOS `WorkisJowiWordmark`).
 * With the system's animator scale at 0 (reduced motion) it is drawn whole at once.
 * 72 dp on the empty tab; 40 dp, still, as the letterhead over a running thread.
 */
@Composable
fun JowiWordmark(height: Dp, modifier: Modifier = Modifier, write: Boolean = false) {
    val context = LocalContext.current
    val dark = WorkisTheme.colors == WorkisDarkColors
    val url = BrandImages.url(if (dark) "jowi-wordmark-star-ink-light@3x.png" else "jowi-wordmark-star-ink-dark@3x.png")
    val bitmap by produceState<Bitmap?>(initialValue = null, url) { value = BrandImages.load(context.applicationContext, url) }
    val image = bitmap ?: return

    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val sweep = remember { Animatable(if (write && !reduceMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (sweep.value < 1f) {
            delay(300)
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
    }
    Image(
        image.asImageBitmap(),
        contentDescription = "Jowi",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .width(height * (image.width.toFloat() / image.height))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val p = sweep.value
                if (p >= 1f) return@drawWithContent
                // the mask is 1.1× the mark wide, opaque to 92 % and fading out;
                // it slides in from the left until it covers the whole mark
                val w = size.width * 1.1f
                val left = -w + w * p
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent,
                        startX = left, endX = left + w,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}
