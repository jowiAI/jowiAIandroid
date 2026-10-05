package ai.workis.jowi.ui.screens.partner

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.PurchaseListRow
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ListsViewModel : ViewModel() {
    var lists by mutableStateOf<List<PurchaseListRow>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    private val lang get() = Graph.language.value

    /** "Arşivlenen taslaklar" — the same screen over `?archived=1`. */
    var archived by mutableStateOf<List<PurchaseListRow>?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.lists() }
            if (r != null) { lists = r.lists.orEmpty(); error = null } else error = err
        }
    }

    fun loadArchived() {
        viewModelScope.launch {
            val (r, _) = partnerCall(lang) { Graph.api.lists(archived = 1) }
            if (r != null) archived = r.lists.orEmpty()
        }
    }

    /**
     * archive · unarchive · retry — the server decides (canArchive / canRetry); a sent
     * list answers 409 and its sentence is shown as is. Nothing is deleted.
     */
    fun act(row: PurchaseListRow, action: String) {
        val id = row.id ?: return
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                when (action) {
                    "archive" -> Graph.api.listArchive(id)
                    "unarchive" -> Graph.api.listUnarchive(id)
                    else -> Graph.api.listRetry(id)
                }
            }
            busy = false
            if (r == null || !r.success) note = r?.error ?: r?.message ?: err
            load()
            if (archived != null) loadArchived()
        }
    }

    fun upload(bytes: ByteArray, name: String) {
        busy = true; note = null
        viewModelScope.launch {
            val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody("application/pdf".toMediaType()))
            val (r, err) = partnerCall(lang) { Graph.api.listUpload(part) }
            busy = false
            val msg = r?.message ?: r?.error ?: err
            if (msg != null && lists.isNullOrEmpty()) note = msg
            load()
        }
    }

    fun sample() {
        busy = true; note = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.listSample() }
            busy = false
            val msg = r?.message ?: r?.error ?: err
            if (msg != null && lists.isNullOrEmpty()) note = msg
            load()
        }
    }
}

/** The "+" menu: upload a spec PDF, or try the example project. */
@Composable
fun ListsMenu(vm: ListsViewModel) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.upload(bytes, "spec.pdf")
    }
    Box {
        IconButton(onClick = { open = true }, enabled = !vm.busy) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
            else Icon(Icons.Filled.Add, contentDescription = null, tint = colors.ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("📄 " + stringResource(R.string.upload_spec), color = colors.ink, fontSize = 14.sp) }, onClick = { open = false; picker.launch(arrayOf("application/pdf")) })
            DropdownMenuItem(text = { Text("🎓 " + stringResource(R.string.try_sample), color = colors.ink, fontSize = 14.sp) }, onClick = { open = false; vm.sample() })
        }
    }
}

/** Listeler — rows; lists still in the pipeline re-fetch every 4 s (the web polls the same way). */
@Composable
fun ListsScreen(vm: ListsViewModel, onOpen: (String) -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ai.workis.jowi.data.ScreenContext.setPage("lists"); vm.load() } // Jowi's page (2026-09-19)
    // changes/ (2026-10-03): the `lists` stamp moved → re-read. Nothing is typed here, so the
    // pill never draws and the watcher may sit outside the column.
    ChangesWatch("lists") { vm.load() }
    val lists = vm.lists
    // a stuck row is still in a working stage; the poll goes on, the row says "Takıldı"
    LaunchedEffect(lists?.any { it.isWorking && it.stuck != true }) {
        while (lists?.any { it.isWorking && it.stuck != true } == true) { delay(4_000); vm.load() }
    }
    // "Arşivlenen taslaklar" is the same screen over the archived drafts
    var showArchived by remember { mutableStateOf(false) }
    BackHandler(enabled = showArchived) { showArchived = false }
    if (showArchived) {
        LaunchedEffect(Unit) { vm.loadArchived() }
        val archived = vm.archived
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showArchived = false }.padding(vertical = 2.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lists_archived_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                }
            }
            item { NoteBand(vm.note) }
            when {
                archived != null -> {
                    if (archived.isEmpty()) item { Text(stringResource(R.string.lists_archived_empty), fontSize = 13.sp, color = colors.faint) }
                    items(archived, key = { it.id ?: it.hashCode() }) { row ->
                        SwipeRow(
                            label = stringResource(R.string.list_unarchive), icon = WorkisIcons.Archive, tint = colors.blue,
                            enabled = !vm.busy, onSwipe = { vm.act(row, "unarchive") },
                        ) { ListRow(row, vm) { row.id?.let(onOpen) } }
                    }
                }
                else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.lists_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        item { NoteBand(vm.note) }
        when {
            lists != null -> {
                if (lists.isEmpty()) item { Text(stringResource(R.string.lists_empty), fontSize = 13.sp, color = colors.faint) }
                items(lists, key = { it.id ?: it.hashCode() }) { row ->
                    // A real List row — the swipe belongs here. Archive is NOT delete (neutral
                    // tint, no bin): an unsent draft leaves this screen, still opens from the
                    // Jowi turn that made it, and comes back from "Arşivlenen taslaklar". A
                    // sent list is a record and the server offers neither (canArchive false).
                    when {
                        row.canArchive == true -> SwipeRow(
                            label = stringResource(R.string.list_archive), icon = WorkisIcons.Archive, tint = colors.muted,
                            enabled = !vm.busy, onSwipe = { vm.act(row, "archive") },
                        ) { ListRow(row, vm) { row.id?.let(onOpen) } }
                        row.canRetry == true -> SwipeRow(
                            label = stringResource(R.string.list_retry), icon = WorkisIcons.Retry, tint = colors.blue,
                            enabled = !vm.busy, onSwipe = { vm.act(row, "retry") },
                        ) { ListRow(row, vm) { row.id?.let(onOpen) } }
                        else -> ListRow(row, vm) { row.id?.let(onOpen) }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showArchived = true }.padding(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Icon(WorkisIcons.Archive, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.lists_archived_title), fontSize = 14.sp, color = colors.muted, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
                    }
                }
            }
            vm.error != null -> item { Text(vm.error.orEmpty(), color = colors.danger, fontSize = 13.sp) }
            else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

/**
 * The system swipe (end → start) reveals ONE action and fires it; the row snaps back,
 * nothing leaves the list until the server has answered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onSwipe: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart && enabled) onSwipe()
            false // never dismiss: the list re-reads from the server
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().padding(end = 22.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint)
            }
        },
    ) { content() }
}

@Composable
private fun ListRow(row: PurchaseListRow, vm: ListsViewModel, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.title ?: "—", fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink, maxLines = 2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            // the screens' words come from the server; the app's map is the fallback
            StatusPill(row.statusLabel ?: listStatusText(row.status), if (row.status == "quoting") colors.accentText else colors.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                row.stuck == true -> {
                    // spinning forever was the bug; say it is stuck (icon + word) and keep
                    // the way out in sight — the swipe alone is too hidden for this
                    Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.list_stuck), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Beige)
                }
                row.isWorking -> {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = colors.faint)
                    Text(row.stageLabel ?: listStageWord(row.stage) ?: stringResource(R.string.processing), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                }
                else -> {
                    Text("${row.nMatched ?: 0}/${row.nItems ?: 0} " + stringResource(R.string.matched_word), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                    row.createdAt?.let { Text("· " + it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
                }
            }
            if (row.canRetry == true) {
                Text(
                    stringResource(R.string.list_retry), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (vm.busy) colors.faint else colors.accentText,
                    modifier = Modifier.clickable(enabled = !vm.busy) { vm.act(row, "retry") },
                )
            }
            if (row.demo == true) StatusPill(stringResource(R.string.demo_tag))
            if (row.held != null) StatusPill(stringResource(R.string.held_tag), Beige)
        }
    }
}

/** The stage word: the shared map, plus `checking` — Jowi re-reading a list built from a need (2026-10-03). */
@Composable
internal fun listStageWord(s: String?): String? =
    if (s == "checking") stringResource(R.string.ls_checking) else listStageText(s)
