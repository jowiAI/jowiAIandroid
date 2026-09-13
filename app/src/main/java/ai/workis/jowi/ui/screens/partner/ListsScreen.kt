package ai.workis.jowi.ui.screens.partner

import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.Kiremit500
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

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.lists() }
            if (r != null) { lists = r.lists.orEmpty(); error = null } else error = err
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
    LaunchedEffect(Unit) { vm.load() }
    val lists = vm.lists
    LaunchedEffect(lists?.any { it.isWorking }) {
        while (lists?.any { it.isWorking } == true) { delay(4_000); vm.load() }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.lists_sub), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted) }
        item { NoteBand(vm.note) }
        when {
            lists != null -> {
                if (lists.isEmpty()) item { Text(stringResource(R.string.lists_empty), fontSize = 13.sp, color = colors.faint) }
                items(lists, key = { it.id ?: it.hashCode() }) { row -> ListRow(row) { row.id?.let(onOpen) } }
            }
            vm.error != null -> item { Text(vm.error.orEmpty(), color = colors.danger, fontSize = 13.sp) }
            else -> item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 26, breathing = true) } }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun ListRow(row: PurchaseListRow, onClick: () -> Unit) {
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
            StatusPill(listStatusText(row.status), if (row.status == "quoting") colors.accentText else colors.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.isWorking) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = colors.faint)
                Text(listStageText(row.stage) ?: stringResource(R.string.processing), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
            } else {
                Text("${row.nMatched ?: 0}/${row.nItems ?: 0} " + stringResource(R.string.matched_word), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                row.createdAt?.let { Text("· " + it.take(10), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint) }
            }
            if (row.demo == true) StatusPill(stringResource(R.string.demo_tag))
            if (row.held != null) StatusPill(stringResource(R.string.held_tag), Beige)
        }
    }
}
