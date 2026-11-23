package ai.workis.jowi.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Asking Jowi with a photo (ledger 119, Django `docs/ios_notes/2026-11-23_jowi_fotograf.md`).
 * A photo chosen and not sent yet: the JPEG that travels and the picture drawn while it waits
 * and in the asker's bubble.
 */
class StagedPhoto(val jpeg: ByteArray, val image: Bitmap)

/**
 * The picture is made a JPEG with its long edge at most 2000 px (quality 80) and turned the way
 * it was taken — the server reads the type from the bytes, so whatever the picker handed over
 * (HEIC included) leaves as a JPEG. Null = the file could not be opened.
 */
suspend fun stagePhoto(context: Context, uri: Uri): StagedPhoto? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PhotoLongEdge) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val turn = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = (PhotoLongEdge.toFloat() / maxOf(raw.width, raw.height)).coerceAtMost(1f)
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (turn != 0f) postRotate(turn) }
        val bmp = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val out = ByteArrayOutputStream()
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)) return@runCatching null
        StagedPhoto(out.toByteArray(), bmp)
    }.getOrNull()
}

private const val PhotoLongEdge = 2000

/** The JPEG as a request body that says when its last byte has left ("yükleniyor" → "Jowi fotoğrafa bakıyor"). */
class PhotoBody(private val bytes: ByteArray, private val onSent: () -> Unit) : RequestBody() {
    override fun contentType(): MediaType = "image/jpeg".toMediaType()
    override fun contentLength(): Long = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        sink.write(bytes)
        sink.flush()
        onSent()
    }
}

/**
 * The door: a "+" inside the ask field, at its leading edge (owner, 2026-11-23 — the composer
 * grammar of every messaging app). Its menu: the system camera (not offered on a device without
 * one) · the system photo picker, images only. Neither needs a permission of the app's own.
 */
@Composable
fun PhotoDoor(enabled: Boolean, onStaged: (StagedPhoto?) -> Unit) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    // the camera writes into the app's own cache; the path outlives a process death
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onStaged(stagePhoto(context, uri)) }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = shot
        shot = null
        if (ok && path != null) scope.launch {
            val file = File(path)
            onStaged(stagePhoto(context, Uri.fromFile(file)))
            file.delete()
        }
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.jowi_photo_add), tint = if (enabled) colors.ink else colors.faint)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (hasCamera) {
                DropdownMenuItem(
                    leadingIcon = { Icon(WorkisIcons.Camera, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp)) },
                    text = { Text(stringResource(R.string.jowi_photo_take), color = colors.ink, fontSize = 14.sp) },
                    onClick = {
                        menu = false
                        runCatching {
                            val dir = File(context.cacheDir, "photo").apply { mkdirs() }
                            val file = File(dir, "ask.jpg")
                            shot = file.absolutePath
                            take.launch(FileProvider.getUriForFile(context, context.packageName + ".files", file))
                        }.onFailure { shot = null; onStaged(null) }
                    },
                )
            }
            DropdownMenuItem(
                leadingIcon = { Icon(WorkisIcons.Photo, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp)) },
                text = { Text(stringResource(R.string.jowi_photo_choose), color = colors.ink, fontSize = 14.sp) },
                onClick = {
                    menu = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/**
 * The photo waits above the ask field: thumbnail · the hint · ✕ · "Gönder" — the screen's one
 * filled action while it stands. "Gönder" sends the photo with whatever is typed (nothing typed
 * = the photo alone).
 */
@Composable
fun StagedPhotoCard(photo: StagedPhoto, busy: Boolean, onRemove: () -> Unit, onSend: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(10.dp),
    ) {
        Image(
            photo.image.asImageBitmap(), contentDescription = stringResource(R.string.jowi_photo_label),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(stringResource(R.string.jowi_photo_hint), fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(WorkisIcons.Cross, contentDescription = stringResource(R.string.jowi_photo_remove), tint = colors.muted, modifier = Modifier.size(16.dp))
        }
        Button(
            onClick = onSend, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(36.dp),
        ) { Text(stringResource(R.string.send), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** The app's own sentence for a file that could not be opened — nothing reached the server. */
@Composable
fun PhotoNote(text: String) {
    Text(
        text, fontSize = 12.sp, lineHeight = 16.sp, color = Beige,
        modifier = Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
