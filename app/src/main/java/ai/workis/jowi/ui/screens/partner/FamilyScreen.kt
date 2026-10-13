package ai.workis.jowi.ui.screens.partner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import ai.workis.jowi.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CatalogFamilyDetail
import ai.workis.jowi.data.CatalogMedia
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ai.workis.jowi.data.CatalogVariant
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.VisStyle
import ai.workis.jowi.data.vis
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.catalogImageUrl
import ai.workis.jowi.data.decodeFamily
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.jsonError
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.AuthImage
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.components.openInApp
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * Family preview — one proposed / approved family: photo, editable name and
 * description (cell-exit → `cell/`), rows, specs, the maker's web page, the
 * gallery, what Jowi learned, teach; the ONE tinted action approves (or
 * withdraws a live family), each behind a confirm.
 */
class FamilyViewModel : ViewModel() {
    var detail by mutableStateOf<CatalogFamilyDetail?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var tick by mutableStateOf<String?>(null)
        private set
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var webUrl by mutableStateOf("")
    var teach by mutableStateOf("")
    /** Set once the family is approved / rejected / withdrawn — the caller leaves. */
    var done by mutableStateOf(false)
        private set
    private var id = ""
    var seller: String? = null
        private set
    private val lang get() = Graph.language.value

    fun load(familyId: String, seller: String? = null) {
        id = familyId
        this.seller = seller
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.catalogFamily(familyId, seller) }
            val fam = r?.let { decodeFamily(it) }
            if (fam != null) { detail = fam; name = fam.name.orEmpty(); description = fam.description.orEmpty(); loadError = null }
            else loadError = err ?: jsonError(r) ?: ai.workis.jowi.data.statusMessage(-1, lang)
        }
    }

    private fun flash(key: String) { tick = key; viewModelScope.launch { kotlinx.coroutines.delay(1400); if (tick == key) tick = null } }

    /** One cell → the full family back (re-render without a second call); returns the error sentence. */
    suspend fun cell(body: JsonObject, tickKey: String? = null): String? {
        busy = true; note = null
        val (r, err) = partnerCall(lang) { Graph.api.catalogFamilyCell(id, body, seller) }
        busy = false
        val fam = r?.let { decodeFamily(it) }
        return if (fam != null) { detail = fam; tickKey?.let { flash(it) }; null }
        else (jsonError(r) ?: err).also { note = it }
    }

    fun commitName() { if (name != detail?.name.orEmpty()) viewModelScope.launch { cell(jsonBody("field" to "name", "value" to name), "name") } }
    fun commitDescription() { if (description != detail?.description.orEmpty()) viewModelScope.launch { cell(jsonBody("field" to "description", "value" to description), "description") } }
    fun deleteRow(code: String) { viewModelScope.launch { cell(jsonBody("field" to "delete", "code" to code)) } }
    fun deleteSpec(key: String) { viewModelScope.launch { cell(jsonBody("field" to "specDel", "key" to key)) } }

    private fun simple(block: suspend () -> ai.workis.jowi.data.SimpleResult, leave: Boolean) {
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang, block)
            busy = false
            if (r?.success == true) { if (leave) done = true else load(id, seller) } else note = r?.error ?: r?.message ?: err
        }
    }

    fun approve() = simple({ Graph.api.catalogFamilyApprove(id, seller) }, leave = true)
    fun reject() = simple({ Graph.api.catalogFamilyReject(id, seller) }, leave = true)
    fun withdraw() = simple({ Graph.api.catalogFamilyWithdraw(id, seller) }, leave = true)

    fun resolveWeb() = simple({ Graph.api.catalogFamilyWeb(id, jsonBody("url" to webUrl.trim().ifBlank { null }), seller) }, leave = false)

    fun teachNow(unitsWord: String) {
        val text = teach.trim()
        if (text.length < 10) return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.catalogFamilyTeach(id, jsonBody("text" to text), seller) }
            busy = false
            if (r?.count != null) { teach = ""; note = "+${r.count} $unitsWord"; load(id, seller) }
            else note = r?.error ?: err
        }
    }

    /** One gallery item: primary · retire · note — the family door serves a live family too. */
    fun mediaAction(mediaId: String, action: String, note: String? = null, onDone: (String?) -> Unit) {
        if (mediaId.isEmpty()) return
        busy = true; this.note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.catalogFamilyMediaAction(id, mediaId, action, jsonBody("note" to note), seller)
            }
            busy = false
            if (r?.success == true) { load(id, seller); onDone(null) } else onDone(r?.error ?: r?.message ?: err ?: "")
        }
    }

    /** Adds to the gallery: a photo from the device, or a YouTube link. */
    fun addMedia(bytes: ByteArray? = null, youtubeUrl: String? = null) {
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = if (bytes != null) {
                val jpeg = withContext(Dispatchers.IO) { normalizedJpeg(bytes) }
                if (jpeg == null) { busy = false; note = ai.workis.jowi.data.applyErrorMessage("bad_file", lang); return@launch }
                val part = MultipartBody.Part.createFormData("image", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
                partnerCall(lang) { Graph.api.catalogFamilyMediaUpload(id, part, seller) }
            } else {
                partnerCall(lang) { Graph.api.catalogFamilyMediaLink(id, jsonBody("youtubeUrl" to youtubeUrl.orEmpty()), seller) }
            }
            busy = false
            if (r?.success == true) load(id, seller) else note = r?.error ?: r?.message ?: err
        }
    }

    fun uploadPhoto(bytes: ByteArray) {
        busy = true; note = null
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) { normalizedJpeg(bytes) }
            if (jpeg == null) { busy = false; note = ai.workis.jowi.data.applyErrorMessage("bad_file", lang); return@launch }
            val part = MultipartBody.Part.createFormData("image", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            val (r, err) = partnerCall(lang) { Graph.api.catalogFamilyImage(id, part, seller) }
            busy = false
            if (r?.success == true) load(id) else note = r?.error ?: r?.message ?: err
        }
    }
}

/** Long edge ≤ 2000 px, JPEG 0.8 — under the server's size walls (shared rule with iOS). */
fun normalizedJpeg(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return out.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(familyId: String, onBack: () -> Unit, seller: String? = null) {
    val vm: FamilyViewModel = viewModel(key = "family-$familyId-${seller ?: ""}")
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val lang by Graph.language.collectAsState()
    LaunchedEffect(familyId, seller) { vm.load(familyId, seller) }
    LaunchedEffect(vm.done) { if (vm.done) onBack() }
    var confirm by remember { mutableStateOf<String?>(null) } // approve | reject | withdraw
    var editVariant by remember { mutableStateOf<CatalogVariant?>(null) }
    var showAddRow by remember { mutableStateOf(false) }
    var fullImage by remember { mutableStateOf<String?>(null) }
    var openMedia by remember { mutableStateOf<CatalogMedia?>(null) }
    var showAddVideo by remember { mutableStateOf(false) }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.addMedia(bytes = bytes)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.uploadPhoto(bytes)
    }
    val d = vm.detail
    val isLive = d?.isLive == true

    // Jowi's screen context: which product is open, its warnings and web badges
    val noticeChip = if (d?.imageQuality == "low" || d?.imageQuality == "page") photoChip(null, d?.imageQuality)?.text else null
    val webChipText = d?.web?.let { webChip(it)?.text }
    val screenLabels = buildList {
        // the one tinted action on this screen is the toolbar's approve; withdraw is a plain text action
        if (isLive) add(vis(stringResource(R.string.withdraw_family), VisStyle.SECONDARY))
        else add(vis(stringResource(R.string.approve_publish), VisStyle.PRIMARY))
        add(vis(stringResource(R.string.photo_replace), VisStyle.SECONDARY)); add(vis(stringResource(R.string.description_field)))
        add(vis(stringResource(R.string.variants_title))); add(vis(stringResource(R.string.add_row), VisStyle.SECONDARY))
        add(vis(stringResource(R.string.edit_row), VisStyle.SECONDARY)); add(vis(stringResource(R.string.delete_row), VisStyle.DESTRUCTIVE))
        add(vis(stringResource(R.string.price_on_request))); add(vis(stringResource(R.string.specs_title)))
        add(vis(stringResource(R.string.web_title))); add(vis(stringResource(R.string.web_url_field)))
        add(vis(stringResource(R.string.web_resolve), VisStyle.SECONDARY)); add(vis(stringResource(R.string.media_title)))
        add(vis(stringResource(R.string.knowledge_title))); add(vis(stringResource(R.string.teach_title)))
        add(vis(stringResource(R.string.teach_action), VisStyle.SECONDARY))
        add(vis(stringResource(R.string.photo_add), VisStyle.SECONDARY)); add(vis(stringResource(R.string.media_add_video), VisStyle.SECONDARY))
        add(vis(stringResource(R.string.media_primary), VisStyle.SECONDARY)); add(vis(stringResource(R.string.media_note), VisStyle.SECONDARY))
        add(vis(stringResource(R.string.media_remove), VisStyle.DESTRUCTIVE))
        if (!isLive) add(vis(stringResource(R.string.reject_family), VisStyle.DESTRUCTIVE))
        noticeChip?.let { add(vis(it, VisStyle.CHIP)) }
        webChipText?.let { add(vis(it, VisStyle.CHIP)) }
    }
    LaunchedEffect(isLive, d?.name, d?.brand, noticeChip, webChipText, seller) {
        ScreenContext.set(
            if (isLive) "seller.product" else "seller.product_preview",
            jsonBody(
                "product" to d?.name?.takeIf { it.isNotEmpty() },
                "brand" to d?.brand?.takeIf { it.isNotEmpty() },
                "notices" to listOfNotNull(noticeChip).takeIf { it.isNotEmpty() },
                "web" to listOfNotNull(webChipText).takeIf { it.isNotEmpty() },
            ),
            screenLabels, seller,
        )
    }

    ConsoleSub(d?.name ?: "", onBack, trailing = {
        if (d != null) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            else if (isLive) TextButton(onClick = { confirm = "withdraw" }) { Text(stringResource(R.string.withdraw_family), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText) }
            else Button(
                onClick = { confirm = "approve" },
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.approve_publish), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }) {
        when {
            d == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            d == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NoteBand(vm.note)
                // header: photo · status · name · brand · code · photo replace · description · reject
                SurfaceCard {
                    Row(verticalAlignment = Alignment.Top) {
                        AuthImage(catalogImageUrl(d.imageKey, "card", seller), Modifier.size(96.dp).clickable { fullImage = catalogImageUrl(d.imageKey, "card", seller) }, corner = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(stringResource(if (isLive) R.string.family_live else R.string.family_proposed), if (isLive) SuccessGreen else Beige)
                                if (d.imageQuality == "low" || d.imageQuality == "page") photoChip(null, d.imageQuality)?.let { WorkisChip(it) }
                            }
                            Text(listOfNotNull(d.brand?.takeIf { it.isNotEmpty() }, d.code?.takeIf { it.isNotEmpty() }).joinToString("  "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !vm.busy) { photoPicker.launch(arrayOf("image/*")) }) {
                                Icon(WorkisIcons.Camera, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.photo_replace), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                            }
                        }
                    }
                }
                FormSection(null) {
                    FormRow(stringResource(R.string.product_name), vm.name, { vm.name = it }, { vm.commitName() }, vm.tick == "name", stacked = true, maxLines = 3)
                    FormRow(stringResource(R.string.description_field), vm.description, { vm.description = it }, { vm.commitDescription() }, vm.tick == "description", stacked = true, minLines = 1, maxLines = 6)
                    if (!isLive) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { confirm = "reject" }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reject_family), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.danger)
                        }
                    }
                }

                // rows
                val variants = d.variants.orEmpty()
                FormSection(stringResource(R.string.variants_title).uppercase() + " · ${variants.size}") {
                    variants.forEachIndexed { i, v ->
                        SwipeDelete(stringResource(R.string.delete_row), { v.code?.let { vm.deleteRow(it) } }) {
                            Column(Modifier.fillMaxWidth().background(colors.surface).clickable { editVariant = v }.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(v.label ?: v.code ?: "—", fontSize = 13.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(v.code.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (v.priceOnRequest == true || v.price == null) stringResource(R.string.price_on_request) else WorkisMoney.text(v.price, v.currency),
                                        fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.ink,
                                    )
                                }
                                // every attribute, under the price, as the web draws it
                                v.attrs?.rows?.takeIf { it.isNotEmpty() }?.let { AttrLines(it) }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showAddRow = true }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_row), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                    }
                }

                // specs
                val specs = d.specAttrs.orEmpty()
                if (specs.isNotEmpty()) FormSection(stringResource(R.string.specs_title).uppercase()) {
                    specs.forEachIndexed { i, s ->
                        SwipeDelete(stringResource(R.string.delete_row), { s.key?.let { vm.deleteSpec(it) } }) {
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(s.label ?: s.key.orEmpty(), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(listOfNotNull(s.value, s.unit?.takeIf { it.isNotEmpty() }).joinToString(" "), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink)
                            }
                        }
                        if (i < specs.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    }
                }

                // the maker's web page
                FormSection(stringResource(R.string.web_title).uppercase()) {
                    val w = d.web
                    val url = w?.url
                    if (w != null && !url.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            WorkisChip(webChip(w) ?: ChipSpec(WorkisIcons.Globe, stringResource(R.string.web_confidence_none), colors.muted))
                            w.codesMatched?.takeIf { it.isNotEmpty() }?.let { Text("${it.size} " + stringResource(R.string.code_field).lowercase(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
                        }
                        Text("↗ $url", fontSize = 12.sp, color = colors.blue, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { openInApp(context, url) }.padding(horizontal = 14.dp, vertical = 6.dp))
                        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    }
                    FormRow("", vm.webUrl, { vm.webUrl = it }, {}, false, placeholder = stringResource(R.string.web_url_field), stacked = true, keyboardType = KeyboardType.Uri)
                    Text(
                        stringResource(R.string.web_resolve), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (vm.busy) colors.faint else colors.accentText,
                        modifier = Modifier.clickable(enabled = !vm.busy) { vm.resolveWeb() }.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }

                // gallery
                val media = d.media.orEmpty()
                val mediaTitle = stringResource(R.string.media_title)
                val primaryWord = stringResource(R.string.media_is_primary)
                FormSection(mediaTitle.uppercase()) {
                    if (media.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            media.forEachIndexed { i, m ->
                                // the badges are decoration; the button says what the item IS,
                                // so TalkBack reads "Fotoğraf ve video 2 · Ana fotoğraf · <not>"
                                val spoken = listOfNotNull(
                                    "$mediaTitle ${i + 1}",
                                    primaryWord.takeIf { m.isPrimary == true },
                                    m.note?.takeIf { it.isNotEmpty() },
                                ).joinToString(" · ")
                                Box(
                                    Modifier
                                        .size(72.dp)
                                        .clickable { openMedia = m }
                                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                                ) {
                                    if (m.key != null) {
                                        AuthImage(catalogImageUrl(m.key, "thumb", seller), Modifier.size(72.dp))
                                    } else {
                                        Box(Modifier.size(72.dp).background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                            Icon(WorkisIcons.Play, contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    if (m.isPrimary == true) {
                                        Text("★", fontSize = 10.sp, color = Beige, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clearAndSetSemantics {})
                                    }
                                    if (!m.note.isNullOrEmpty()) {
                                        Text("✎", fontSize = 10.sp, color = colors.muted, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clearAndSetSemantics {})
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { galleryPicker.launch(arrayOf("image/*")) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(WorkisIcons.Camera, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.photo_add), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { showAddVideo = true }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(WorkisIcons.Play, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_add_video), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
                    }
                }

                // what Jowi learned
                // the server sends "" (not null) for the missing language — fall through to the other one
                val knowledge = d.knowledge.orEmpty().mapNotNull { u ->
                    val en = u.textEn?.takeIf { it.isNotBlank() }; val tr = u.textTr?.takeIf { it.isNotBlank() }
                    if (lang == "en") en ?: tr else tr ?: en
                }
                if (knowledge.isNotEmpty()) FormSection(stringResource(R.string.knowledge_title).uppercase() + " · ${knowledge.size}") {
                    knowledge.forEachIndexed { i, text ->
                        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = colors.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        if (i < knowledge.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
                    }
                }

                // teach
                FormSection(stringResource(R.string.teach_title).uppercase()) {
                    FormRow("", vm.teach, { vm.teach = it }, {}, false, placeholder = stringResource(R.string.teach_placeholder), stacked = true, minLines = 2, maxLines = 6)
                    val can = !vm.busy && vm.teach.trim().length >= 10
                    val unitsWord = stringResource(R.string.units_word)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = can) { vm.teachNow(unitsWord) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(WorkisIcons.Sparkle, contentDescription = null, tint = if (can) colors.accentText else colors.faint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.teach_action), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (can) colors.accentText else colors.faint)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    confirm?.let { which ->
        val title = stringResource(when (which) { "approve" -> R.string.approve_publish; "reject" -> R.string.reject_family; else -> R.string.withdraw_family })
        val message = when (which) { "approve" -> d?.name.orEmpty(); "reject" -> stringResource(R.string.reject_confirm); else -> stringResource(R.string.withdraw_confirm) }
        ConfirmDialog(title, message, destructive = which != "approve", onDismiss = { confirm = null }) {
            when (which) { "approve" -> vm.approve(); "reject" -> vm.reject(); else -> vm.withdraw() }
        }
    }
    if (showAddRow) VariantSheet(vm, null) { showAddRow = false }
    editVariant?.let { v -> VariantSheet(vm, v) { editVariant = null } }
    fullImage?.let { SourcePageDialog(it, stringResource(R.string.media_title)) { fullImage = null } }
    openMedia?.let { m -> MediaSheet(m, vm, seller, onFull = { fullImage = it }) { openMedia = null } }
    if (showAddVideo) AddVideoSheet(vm) { showAddVideo = false }
}

/**
 * One gallery item as its own page: the web's three actions verbatim —
 * ★ Birincil yap (the page's ONE tinted action, images only) · ✎ Not · ✕ Kaldır
 * (destructive, asks first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSheet(media: CatalogMedia, vm: FamilyViewModel, seller: String?, onFull: (String?) -> Unit, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var note by remember(media.id) { mutableStateOf(media.note.orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showRemove by remember { mutableStateOf(false) }
    val id = media.id.orEmpty()
    val isImage = media.key != null
    fun act(action: String, text: String? = null) {
        vm.mediaAction(id, action, text) { err -> if (err.isNullOrEmpty()) onClose() else errorText = err }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.media_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            if (isImage) {
                AuthImage(
                    catalogImageUrl(media.key, "card", seller),
                    Modifier.fillMaxWidth().height(220.dp).clickable { onFull(catalogImageUrl(media.key, "card", seller)) },
                    corner = 12.dp,
                )
            } else media.externalUrl?.takeIf { it.isNotEmpty() }?.let { ext ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { openInApp(context, ext) }) {
                    Icon(WorkisIcons.Play, contentDescription = null, tint = colors.blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ext, fontSize = 13.sp, color = colors.blue, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // status is never colour alone — star + words
            if (media.isPrimary == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★ ", fontSize = 13.sp, color = Beige)
                    Text(stringResource(R.string.media_is_primary), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Beige)
                }
            } else if (isImage) {
                Button(
                    onClick = { act("primary") },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.media_primary), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
            FormSection(stringResource(R.string.media_note).uppercase()) {
                FormRow("", note, { note = it }, {}, false, placeholder = stringResource(R.string.media_note_placeholder), stacked = true, minLines = 1, maxLines = 5)
                val canSave = !vm.busy && note != media.note.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = canSave) { act("note", note) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (canSave) colors.accentText else colors.faint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.media_save), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (canSave) colors.accentText else colors.faint)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { showRemove = true }.padding(vertical = 8.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.media_remove), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.danger)
            }
            errorText?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
        }
    }
    if (showRemove) {
        ConfirmDialog(stringResource(R.string.media_remove), stringResource(R.string.media_remove_confirm), destructive = true, onDismiss = { showRemove = false }) {
            act("retire")
        }
    }
}

/** A YouTube link straight into the gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVideoSheet(vm: FamilyViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    var url by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.media_add_video), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                TextButton(enabled = url.isNotBlank(), onClick = { vm.addMedia(youtubeUrl = url.trim()); onClose() }) {
                    Text(stringResource(R.string.media_save), color = if (url.isBlank()) colors.faint else colors.accentText, fontWeight = FontWeight.SemiBold)
                }
            }
            FormSection(null) {
                FormRow("", url, { url = it }, {}, false, placeholder = stringResource(R.string.media_video_url), stacked = true, keyboardType = KeyboardType.Uri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDelete(label: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) onDelete(); false })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(colors.danger.copy(alpha = 0.18f)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.danger)
            }
        },
    ) { content() }
}

/** One row of a family: add (variantAdd) or edit (label / price / currency). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSheet(vm: FamilyViewModel, variant: CatalogVariant?, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(variant?.code.orEmpty()) }
    var label by remember { mutableStateOf(variant?.label.orEmpty()) }
    var price by remember { mutableStateOf(WorkisMoney.qty(variant?.price)) }
    var currency by remember { mutableStateOf(variant?.currency ?: "TRY") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (variant == null) R.string.add_row else R.string.edit_row), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                else TextButton(enabled = code.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true; errorText = null
                        val c = code.trim()
                        val p = price.replace(',', '.')
                        val err: String? = if (variant == null) {
                            vm.cell(jsonBody("field" to "variantAdd", "code" to c, "label" to label, "price" to p, "currency" to currency))
                        } else {
                            val steps = buildList {
                                if (label != variant.label.orEmpty()) add(jsonBody("field" to "label", "code" to c, "value" to label))
                                if (price != WorkisMoney.qty(variant.price)) add(jsonBody("field" to "price", "code" to c, "value" to p))
                                if (currency != (variant.currency ?: "TRY")) add(jsonBody("field" to "currency", "code" to c, "value" to currency))
                            }
                            var first: String? = null
                            for (s in steps) { val e = vm.cell(s); if (e != null) { first = e; break } }
                            first
                        }
                        busy = false
                        if (err == null) onClose() else errorText = err
                    }
                }) { Text(stringResource(R.string.done), color = if (code.isBlank()) colors.faint else colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            FormSection(null) {
                FormRow(stringResource(R.string.code_field), code, { code = it }, {}, false, placeholder = stringResource(R.string.code_field), mono = true, locked = variant != null)
                FormRow("", label, { label = it }, {}, false, placeholder = stringResource(R.string.label_field), stacked = true, maxLines = 3)
                FormRow(stringResource(R.string.unit_price_field), price, { price = it }, {}, false, placeholder = stringResource(R.string.price_on_request), mono = true, keyboardType = KeyboardType.Decimal)
                PickerRowSimple("CUR", currency, listOf("TRY", "USD", "EUR")) { currency = it }
            }
            errorText?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
        }
    }
}
