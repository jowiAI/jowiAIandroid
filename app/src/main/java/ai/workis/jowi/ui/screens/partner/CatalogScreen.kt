package ai.workis.jowi.ui.screens.partner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CatalogFamilyRow
import ai.workis.jowi.data.CatalogIngestion
import ai.workis.jowi.data.CatalogLiveRow
import ai.workis.jowi.data.CatalogPage
import ai.workis.jowi.data.CatalogWeb
import ai.workis.jowi.data.WorkisMoney
import ai.workis.jowi.data.catalogImageUrl
import ai.workis.jowi.data.catalogSourceUrl
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.jsonError
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.AuthImage
import ai.workis.jowi.ui.components.FormRow
import ai.workis.jowi.ui.components.FormSection
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Ürünler — the seller's catalogue (web /workis/panel/urunler/): three
 * segments (İnceleme bekliyor · Canlı katalog · Yükleme ve belgeler) over
 * `GET seller/catalog/`. Running uploads / web fetches re-fetch every 4 s.
 * Chips: an icon + two to four words, no emoji.
 */
class CatalogViewModel : ViewModel() {
    var page by mutableStateOf<CatalogPage?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    private val lang get() = Graph.language.value

    val isBusyServerSide: Boolean
        get() = page?.ingestions.orEmpty().any { it.isRunning || it.webFetch?.status == "running" }

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.catalog() }
            if (p != null) { page = p; loadError = null } else if (page == null) loadError = err
        }
    }

    private fun act(block: suspend () -> Pair<Boolean, String?>) {
        busy = true; note = null
        viewModelScope.launch {
            val (ok, msg) = block()
            busy = false
            note = msg
            if (ok) load()
        }
    }

    fun reject(id: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.catalogFamilyReject(id) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun withdraw(familyId: String) = act {
        val (r, err) = partnerCall(lang) { Graph.api.catalogFamilyWithdraw(familyId) }
        (r?.success == true) to (r?.error ?: r?.message ?: err)
    }

    fun webFetch(ingestionId: String, resume: Boolean) = act {
        val (r, err) = partnerCall(lang) { Graph.api.catalogIngestionWebStart(ingestionId, jsonBody("resume" to (if (resume) true else null))) }
        (r != null && r.success != false) to (r?.error ?: err)
    }

    fun upload(bytes: ByteArray, name: String, kind: String, pages: String, brand: String) = act {
        val fields = buildMap {
            if (kind != "auto") put("kind", kind.toRequestBody("text/plain".toMediaType()))
            if (pages.isNotBlank()) put("pages", pages.trim().toRequestBody("text/plain".toMediaType()))
            if (brand.isNotBlank()) put("brand", brand.trim().toRequestBody("text/plain".toMediaType()))
        }
        val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody("application/pdf".toMediaType()))
        val (r, err) = partnerCall(lang) { Graph.api.catalogUpload(part, fields) }
        (r != null && r.success != false) to (r?.error ?: r?.message ?: err)
    }

    /** → the error sentence, or null when created. */
    suspend fun createFamily(name: String, brand: String, category: String): String? {
        val (r, err) = partnerCall(lang) {
            Graph.api.catalogFamilyCreate(jsonBody("name" to name, "brand" to brand.ifBlank { null }, "category" to category.ifBlank { null }))
        }
        if (r == null) return err
        val e = jsonError(r)
        if (r["family"] == null && e != null) return e
        load()
        return null
    }

    /** One live cell → the error sentence, or null. */
    suspend fun offerCell(offerId: String, field: String, value: Any): String? {
        val (r, err) = partnerCall(lang) { Graph.api.catalogOfferCell(offerId, jsonBody("field" to field, "value" to value)) }
        return if (r?.success == true) null else (r?.error ?: r?.message ?: err)
    }
}

// MARK: - chips (the web's table, 2026-09-16)

data class ChipSpec(val icon: ImageVector, val text: String, val tint: Color)

@Composable
fun webChip(w: CatalogWeb?, pasted: Boolean? = null): ChipSpec? {
    val colors = WorkisTheme.colors
    if (w == null) return null
    val isPasted = w.pasted ?: pasted ?: false
    return when (w.confidence) {
        "exact" -> {
            val noPhoto = w.images == 0 || (w.imageList != null && w.imageList.isEmpty())
            ChipSpec(WorkisIcons.Globe, stringResource(if (noPhoto) R.string.chip_web_no_photo else R.string.chip_web_verified), SuccessGreen)
        }
        "name" -> if (isPasted) ChipSpec(WorkisIcons.Link, stringResource(R.string.chip_web_link_confirmed), SuccessGreen)
        else ChipSpec(WorkisIcons.Globe, stringResource(R.string.chip_web_name), colors.muted)
        "none" -> ChipSpec(WorkisIcons.Globe, stringResource(R.string.chip_page_not_found), colors.faint)
        else -> null
    }
}

@Composable
fun photoChip(src: String?, quality: String?): ChipSpec? {
    val colors = WorkisTheme.colors
    return when {
        quality == "low" -> ChipSpec(WorkisIcons.Photo, stringResource(R.string.chip_low_res), Beige)
        quality == "page" -> ChipSpec(WorkisIcons.DocText, stringResource(R.string.chip_page_image), Beige)
        src == "web" -> ChipSpec(WorkisIcons.Camera, stringResource(R.string.chip_photo_web), colors.muted)
        src == "catalog" -> ChipSpec(WorkisIcons.Camera, stringResource(R.string.chip_photo_catalog), colors.muted)
        src == "upload" -> ChipSpec(WorkisIcons.Camera, stringResource(R.string.chip_photo_upload), colors.muted)
        else -> null
    }
}

/** Chip: icon + two to four words (the web's rule). */
@Composable
fun WorkisChip(spec: ChipSpec) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(colors.ink.copy(alpha = 0.06f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Icon(spec.icon, contentDescription = null, tint = spec.tint, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(spec.text, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = spec.tint, maxLines = 1)
    }
}

@Composable
fun ingestionStatusText(s: String?): String = when (s) {
    "created" -> stringResource(R.string.ing_created)
    "running" -> stringResource(R.string.ing_running)
    "review" -> stringResource(R.string.ing_review)
    "failed" -> stringResource(R.string.ing_failed)
    else -> s.orEmpty()
}

// MARK: - Ürünler

@Composable
fun CatalogScreen(vm: CatalogViewModel, onBack: () -> Unit, onOpenFamily: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var segment by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.load() }
    // uploads still extracting (or a web fetch still running) refresh every 4 s
    LaunchedEffect(vm.isBusyServerSide) {
        while (vm.isBusyServerSide) { delay(4_000); vm.load() }
    }
    ConsoleSub(stringResource(R.string.catalog_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountTile(page.counts?.proposed ?: 0, stringResource(R.string.catalog_proposed), Modifier.weight(1f))
                    CountTile(page.counts?.approved ?: 0, stringResource(R.string.catalog_approved), Modifier.weight(1f))
                    CountTile(page.counts?.liveOffers ?: 0, stringResource(R.string.catalog_live_offers), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                NoteBand(vm.note)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val segs = listOf(stringResource(R.string.seg_review), stringResource(R.string.seg_live), stringResource(R.string.seg_uploads))
                    segs.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = segment == i,
                            onClick = { segment = i },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = segs.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.surface, activeContentColor = colors.ink, inactiveContentColor = colors.muted),
                        ) { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                when (segment) {
                    0 -> ReviewSections(page, vm, onOpenFamily)
                    1 -> LiveSections(page, vm)
                    else -> UploadSections(page, vm)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun CountTile(n: Int, label: String, modifier: Modifier) {
    val colors = WorkisTheme.colors
    Column(modifier.height(78.dp).background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text("$n", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
        color = WorkisTheme.colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** A row that reveals one destructive action on a trailing swipe; the swipe snaps back and asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(actionLabel: String, actionTint: Color, onAction: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) onAction()
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(actionTint.copy(alpha = 0.18f)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = actionTint)
            }
        },
    ) { content() }
}

// MARK: - İnceleme bekliyor

@Composable
private fun ReviewSections(page: CatalogPage, vm: CatalogViewModel, onOpenFamily: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var rejectTarget by remember { mutableStateOf<CatalogFamilyRow?>(null) }
    val groups = page.families.orEmpty().groupBy { it.category?.takeIf { c -> c.isNotEmpty() } ?: "—" }
    Text(stringResource(R.string.review_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Spacer(Modifier.height(6.dp))
    if (groups.isEmpty()) Text(stringResource(R.string.review_empty), fontSize = 13.sp, color = colors.faint, modifier = Modifier.padding(4.dp))
    groups.forEach { (cat, rows) ->
        SectionHeader("${cat.uppercase()} · ${rows.size}")
        SurfaceCard {
            rows.forEachIndexed { i, f ->
                SwipeRow(stringResource(R.string.reject_family), colors.danger, { rejectTarget = f }) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().background(colors.surface).clickable { f.id?.let(onOpenFamily) }.padding(vertical = 8.dp),
                    ) {
                        AuthImage(catalogImageUrl(f.img, "thumb"), Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(f.name ?: f.code ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            // chips scroll rather than truncate ("web doğr…" read wrong)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                webChip(f.web)?.let { WorkisChip(it) }
                                photoChip(f.imgSrc, f.imgQuality)?.let { WorkisChip(it) }
                                f.nVariants?.let { WorkisChip(ChipSpec(WorkisIcons.Table, "$it " + stringResource(R.string.variants_word), colors.muted)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                f.code?.takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                                f.page?.let { Text(stringResource(R.string.source_page) + " $it", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint) }
                            }
                        }
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
                    }
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    rejectTarget?.let { f ->
        ConfirmDialog(stringResource(R.string.reject_family), stringResource(R.string.reject_confirm), destructive = true, onDismiss = { rejectTarget = null }) {
            f.id?.let { vm.reject(it) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, destructive: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = WorkisTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.ink) },
        text = { Text(message, color = colors.muted) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(title, color = if (destructive) colors.danger else colors.accentText, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}

// MARK: - Canlı katalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSections(page: CatalogPage, vm: CatalogViewModel) {
    val colors = WorkisTheme.colors
    var editTarget by remember { mutableStateOf<CatalogLiveRow?>(null) }
    var withdrawTarget by remember { mutableStateOf<CatalogLiveRow?>(null) }
    val groups = page.live.orEmpty().groupBy { (it.group ?: it.mainGroup)?.takeIf { g -> g.isNotEmpty() } ?: "—" }
    Text(stringResource(R.string.live_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Spacer(Modifier.height(6.dp))
    if (groups.isEmpty()) Text(stringResource(R.string.live_empty), fontSize = 13.sp, color = colors.faint, modifier = Modifier.padding(4.dp))
    groups.forEach { (g, rows) ->
        val main = rows.firstOrNull()?.mainGroup?.takeIf { it.isNotEmpty() && it != g }
        SectionHeader("${g.uppercase()} · ${rows.size}" + (main?.let { " · $it" } ?: ""))
        SurfaceCard {
            rows.forEachIndexed { i, r ->
                val row: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().background(colors.surface).clickable { editTarget = r }.padding(vertical = 8.dp),
                    ) {
                        AuthImage(catalogImageUrl(r.img, "thumb"), Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(r.name ?: r.sku ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (r.published == false) colors.faint else colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(6.dp))
                                Text(WorkisMoney.text(r.price, r.currency), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
                            }
                            Text(
                                listOfNotNull(
                                    r.sku,
                                    r.leadTimeDays?.let { "· $it " + stringResource(R.string.vade_days_word) + " " + stringResource(R.string.lead_time_short) },
                                    r.stock?.takeIf { it.isNotEmpty() && it != "0" }?.let { "· " + stringResource(R.string.stock_label) + " $it" },
                                    if (r.published == false) "· " + stringResource(R.string.published_label).lowercase() + ": ✗" else null,
                                ).joinToString(" "),
                                fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (r.familyId != null) SwipeRow(stringResource(R.string.withdraw_family), Beige, { withdrawTarget = r }) { row() } else row()
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    withdrawTarget?.let { r ->
        ConfirmDialog(stringResource(R.string.withdraw_family), stringResource(R.string.withdraw_confirm), destructive = true, onDismiss = { withdrawTarget = null }) {
            r.familyId?.let { vm.withdraw(it) }
        }
    }
    editTarget?.let { r ->
        OfferEditSheet(r, vm) { editTarget = null }
    }
}

/** Live offer cells: price · currency · name · lead time · stock · visible — currency/toggle at once, text cells on Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferEditSheet(row: CatalogLiveRow, vm: CatalogViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var price by remember { mutableStateOf(WorkisMoney.qty(row.price)) }
    var currency by remember { mutableStateOf(row.currency ?: "TRY") }
    var name by remember { mutableStateOf(row.name.orEmpty()) }
    var leadTime by remember { mutableStateOf(row.leadTimeDays?.toString().orEmpty()) }
    var stock by remember { mutableStateOf(row.stock.orEmpty()) }
    var published by remember { mutableStateOf(row.published ?: true) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val id = row.offerId.orEmpty()
    fun saveNow(field: String, value: Any) {
        scope.launch { busy = true; errorText = vm.offerCell(id, field, value); busy = false }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_row), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                else TextButton(onClick = {
                    scope.launch {
                        busy = true
                        val pending = buildList<Pair<String, Any>> {
                            if (price != WorkisMoney.qty(row.price)) add("price" to price.replace(',', '.'))
                            if (name != row.name.orEmpty()) add("displayName" to name)
                            if (leadTime != row.leadTimeDays?.toString().orEmpty()) add("leadTimeDays" to leadTime)
                            if (stock != row.stock.orEmpty()) add("stock" to stock)
                        }
                        var first: String? = null
                        for ((f, v) in pending) { val e = vm.offerCell(id, f, v); if (e != null && first == null) first = e }
                        busy = false
                        if (first != null) errorText = first else { vm.load(); onClose() }
                    }
                }) { Text(stringResource(R.string.done), color = colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            Text(row.sku.orEmpty(), fontFamily = WorkisMono, fontSize = 11.sp, color = colors.faint)
            FormSection(null) {
                FormRow(stringResource(R.string.unit_price_field), price, { price = it }, {}, false, placeholder = "—", mono = true, keyboardType = KeyboardType.Decimal)
                PickerRowSimple("CUR", currency, listOf("TRY", "USD", "EUR")) { currency = it; if (it != row.currency) saveNow("currency", it) }
                FormRow(stringResource(R.string.product_name), name, { name = it }, {}, false, stacked = true, maxLines = 3)
                FormRow(stringResource(R.string.lead_time_field), leadTime, { leadTime = it }, {}, false, placeholder = "—", mono = true, keyboardType = KeyboardType.Number)
                FormRow(stringResource(R.string.stock_label).replaceFirstChar { it.uppercase() }, stock, { stock = it }, {}, false, placeholder = "—", mono = true, keyboardType = KeyboardType.Number)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.published_label), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                    Switch(checked = published, onCheckedChange = { published = it; saveNow("published", it) }, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue))
                }
            }
            errorText?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
        }
    }
}

@Composable
fun PickerRowSimple(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(value.ifEmpty { "—" }, fontSize = 14.sp, color = colors.accentText)
            Text(" ⌄", color = colors.faint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
        }
        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border).align(Alignment.BottomStart))
    }
}

// MARK: - Yükleme ve belgeler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSections(page: CatalogPage, vm: CatalogViewModel) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var kind by remember { mutableStateOf("auto") }
    var pages by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf<String?>(null) }
    val kinds = listOf(
        "auto" to stringResource(R.string.kind_auto), "price_list" to stringResource(R.string.kind_price_list),
        "catalog" to stringResource(R.string.kind_catalog), "mixed" to stringResource(R.string.kind_mixed),
        "flyer" to stringResource(R.string.kind_flyer), "manual" to stringResource(R.string.kind_manual),
        "spare_parts_list" to stringResource(R.string.kind_spare_parts), "certificate" to stringResource(R.string.kind_certificate),
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        var name = "katalog.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
        vm.upload(bytes, name, kind, pages, brand)
    }

    SectionHeader(stringResource(R.string.upload_title).uppercase())
    FormSection(null) {
        val kindLabel = kinds.firstOrNull { it.first == kind }?.second ?: kind
        var open by remember { mutableStateOf(false) }
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.upload_kind), fontSize = 14.sp, color = colors.ink)
                Spacer(Modifier.weight(1f))
                Text(kindLabel, fontSize = 14.sp, color = colors.accentText)
                Text(" ⌄", color = colors.faint)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                kinds.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; kind = v }) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
        FormRow(stringResource(R.string.upload_pages), pages, { pages = it }, {}, false, placeholder = stringResource(R.string.upload_pages), mono = true, stacked = true)
        FormRow(stringResource(R.string.upload_brand), brand, { brand = it }, {}, false, placeholder = stringResource(R.string.upload_brand), stacked = true)
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { picker.launch(arrayOf("application/pdf")) }.padding(vertical = 12.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            else {
                Icon(WorkisIcons.DocPlus, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upload_action), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.border))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showNew = true }.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_product), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
        }
    }
    Text(stringResource(R.string.upload_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))

    val ings = page.ingestions.orEmpty()
    if (ings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.ingestions_title).uppercase())
        SurfaceCard {
            ings.forEachIndexed { i, ing ->
                IngestionRow(ing, vm) { p -> ing.id?.let { sourceUrl = catalogSourceUrl(it, p) } }
                if (i < ings.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }

    if (showNew) NewProductSheet(vm) { showNew = false }
    sourceUrl?.let { SourcePageDialog(it, stringResource(R.string.source_page)) { sourceUrl = null } }
}

@Composable
private fun IngestionRow(ing: CatalogIngestion, vm: CatalogViewModel, onPage: (Int) -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(ing.filename ?: ing.id.orEmpty(), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            if (ing.isRunning) { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.faint); Spacer(Modifier.width(6.dp)) }
            Text(
                ingestionStatusText(ing.status), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                color = when (ing.status) { "review" -> SuccessGreen; "failed" -> colors.danger; else -> colors.faint },
            )
        }
        Text(
            listOfNotNull(
                ing.kind?.takeIf { it.isNotEmpty() },
                if (ing.currentPage != null && ing.totalPages != null) "· " + stringResource(R.string.page_word) + " ${ing.currentPage}/${ing.totalPages}" else null,
                ing.brandHint?.takeIf { it.isNotEmpty() }?.let { "· $it" },
            ).joinToString(" "),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint,
        )
        val wf = ing.webFetch
        if (wf != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(WorkisIcons.Globe, contentDescription = null, tint = colors.faint, modifier = Modifier.size(11.dp))
                Text("${wf.found ?: 0}/${wf.total ?: 0} " + stringResource(R.string.web_fetch_title), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
                if (wf.status == "running") CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = colors.faint)
                if (wf.status == "stalled") {
                    Text("· " + stringResource(R.string.web_fetch_stalled), fontFamily = WorkisMono, fontSize = 10.sp, color = Beige)
                    Text(stringResource(R.string.web_fetch_resume), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { ing.id?.let { vm.webFetch(it, resume = true) } })
                }
            }
        } else if (ing.status == "review") {
            Text(stringResource(R.string.web_fetch_start), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, modifier = Modifier.clickable { ing.id?.let { vm.webFetch(it, resume = false) } })
        }
        // the page map as a horizontal strip — each chip opens the source page
        val map = ing.pageMap.orEmpty()
        if (map.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                map.forEach { m ->
                    val filled = (m.fams ?: 0) > 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(colors.ink.copy(alpha = if (filled) 0.09f else 0.04f), RoundedCornerShape(8.dp))
                            .clickable { m.page?.let(onPage) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("${m.page ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (filled) colors.ink else colors.faint)
                        Text(m.archetype.orEmpty().take(8), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 8.sp, color = if (filled) colors.ink else colors.faint)
                    }
                }
            }
        }
    }
}

/** The source PDF page rendered by the server (side-by-side verification) — also the full-size family image. */
@Composable
fun SourcePageDialog(url: String, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFFFFFFFF))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done), color = Color(0xFF1A1A1F), fontWeight = FontWeight.SemiBold) }
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A1F), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp)) {
                AuthImage(url, Modifier.size(width = 720.dp, height = 1000.dp), corner = 0.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProductSheet(vm: CatalogViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.new_product), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
                else TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        val e = vm.createFamily(name.trim(), brand, category)
                        busy = false
                        if (e == null) onClose() else errorText = e
                    }
                }) { Text(stringResource(R.string.create), color = if (name.isBlank()) colors.faint else colors.accentText, fontWeight = FontWeight.SemiBold) }
            }
            FormSection(null) {
                FormRow(stringResource(R.string.product_name), name, { name = it }, {}, false, placeholder = stringResource(R.string.product_name), stacked = true)
                FormRow(stringResource(R.string.brand_field), brand, { brand = it }, {}, false, placeholder = stringResource(R.string.brand_field), stacked = true)
                FormRow(stringResource(R.string.category_field), category, { category = it }, {}, false, placeholder = stringResource(R.string.category_field), stacked = true)
            }
            Text(errorText ?: stringResource(R.string.new_product_sub), fontSize = 12.sp, lineHeight = 16.sp, color = if (errorText == null) colors.faint else colors.danger)
        }
    }
}
