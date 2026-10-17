package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.GroupHeading
import ai.workis.jowi.data.GroupNode
import ai.workis.jowi.data.GroupUnboundHeading
import ai.workis.jowi.data.GroupsAddBody
import ai.workis.jowi.data.GroupsAliasRemoveBody
import ai.workis.jowi.data.GroupsHeadingBindBody
import ai.workis.jowi.data.GroupsLeadsWriteBody
import ai.workis.jowi.data.GroupsMergeBody
import ai.workis.jowi.data.GroupsPage
import ai.workis.jowi.data.GroupsRenameBody
import ai.workis.jowi.data.GroupsSectorAddBody
import ai.workis.jowi.data.GroupsSectorOnlyBody
import ai.workis.jowi.data.GroupsSellerSectorBody
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.serverSentence
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.partner.ConfirmDialog
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.text.Normalizer
import java.util.Locale

// Console → Gruplar (ledger 62): the Workis groups page. One page, one door — `GET console/groups/`
// is the whole page and every write answers the page re-read, so the screen's state IS the last
// answer. Sector · the coordinator's switch · the sector's sellers · the two-level tree with the
// catalogue headings under each row · the headings no group answers to. Search and collapse are
// the list's own (client-side); every write carries the slug on screen as `sector`.

/** A text ask (alert with one field), one per task. */
sealed interface GroupsPrompt {
    data object SectorAdd : GroupsPrompt
    data object GroupAdd : GroupsPrompt
    data class Subgroup(val parent: GroupNode) : GroupsPrompt
    data class Rename(val node: GroupNode) : GroupsPrompt
    data class Make(val heading: GroupUnboundHeading) : GroupsPrompt
}

/** A picker sheet over the server's lists. */
sealed interface GroupsPick {
    data class Merge(val node: GroupNode) : GroupsPick
    data class Bind(val node: GroupNode) : GroupsPick
    data class Attach(val heading: GroupUnboundHeading) : GroupsPick
    data class Move(val node: GroupNode) : GroupsPick
}

/** A write that asks first: the web's sentence, the target's name beside it, then [run]. */
data class GroupsConfirm(
    val titleRes: Int,
    val sentenceRes: Int,
    val target: String?,
    val destructive: Boolean,
    val run: () -> Unit,
)

class GroupsViewModel : ViewModel() {
    var page by mutableStateOf<GroupsPage?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** The web's one-line `msg` under the title — the last answer's sentence, gone after 8 s. */
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
    /** Top rows the person closed; everything else is open (default expanded). */
    var collapsed by mutableStateOf<Set<String>>(emptySet())

    var prompt by mutableStateOf<GroupsPrompt?>(null)
    var pick by mutableStateOf<GroupsPick?>(null)
    var confirm by mutableStateOf<GroupsConfirm?>(null)

    private var messageJob: Job? = null
    private val lang get() = Graph.language.value
    private val slug get() = page?.sector?.slug.orEmpty()

    fun expandAll() { collapsed = emptySet() }
    fun collapseAll() { collapsed = page?.groups.orEmpty().mapNotNull { it.id }.toSet() }
    fun toggle(id: String) { collapsed = if (id in collapsed) collapsed - id else collapsed + id }

    /** Both doors (GET and POST) end here: the page, or the server's own sentence. */
    private suspend fun exchange(call: suspend () -> GroupsPage): ApiResult<GroupsPage> =
        try {
            val p = call()
            if (p.success == false) ApiResult.Err(null, p.error ?: statusMessage(-1, lang))
            else ApiResult.Ok(p)
        } catch (e: HttpException) {
            ApiResult.Err(e.code(), serverSentence(e) ?: statusMessage(e.code(), lang))
        } catch (e: java.io.IOException) {
            ApiResult.Err(null, statusMessage(null, lang))
        } catch (e: Exception) {
            android.util.Log.e("Groups", "unexpected error", e)
            ApiResult.Err(null, statusMessage(-1, lang))
        }

    fun load(sector: String? = page?.sector?.slug) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = exchange { Graph.api.consoleGroups(sector?.takeIf { it.isNotEmpty() }) }) {
                is ApiResult.Ok -> { page = r.value; loadError = null }
                is ApiResult.Err -> if (page == null) loadError = r.message else show(r.message, true)
            }
            busy = false
        }
    }

    /** Every write: one at a time; the answer replaces the page, `message` shows under the title. */
    private fun send(call: suspend (sector: String) -> GroupsPage) {
        if (busy || page == null) return
        val sector = slug
        busy = true
        viewModelScope.launch {
            when (val r = exchange { call(sector) }) {
                is ApiResult.Ok -> { page = r.value; show(r.value.message.orEmpty(), false) }
                is ApiResult.Err -> show(r.message, true)
            }
            busy = false
        }
    }

    private fun show(text: String, error: Boolean) {
        if (text.isBlank()) return
        message = text
        messageIsError = error
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(8_000)
            if (message == text) message = null
        }
    }

    // --- the doors ---
    fun addSector(name: String) = send { Graph.api.consoleGroupsSectorAdd(GroupsSectorAddBody(name)) }
    fun setLeadsWrite(on: Boolean) = send { s -> Graph.api.consoleGroupsLeadsWrite(GroupsLeadsWriteBody(s, on)) }
    fun moveSeller(sellerId: String, toSectorId: String) =
        send { s -> Graph.api.consoleGroupsSellerSector(sellerId, GroupsSellerSectorBody(s, toSectorId)) }
    fun addGroup(name: String, parentId: String? = null) =
        send { s -> Graph.api.consoleGroupsAdd(GroupsAddBody(s, name, parentId)) }
    fun rename(id: String, name: String) = send { s -> Graph.api.consoleGroupsRename(id, GroupsRenameBody(s, name)) }

    /** Within the sector: `parentId` null = top level, and the null is SENT (WorkisJson drops nulls). */
    fun moveWithin(id: String, parentId: String?) = send { s ->
        Graph.api.consoleGroupsMove(id, buildJsonObject {
            put("sector", s)
            if (parentId != null) put("parentId", parentId) else put("parentId", JsonNull)
        })
    }

    /** Across sectors: `{sector, toSectorId, parentId?}`. */
    fun moveToSector(id: String, toSectorId: String, parentId: String?) = send { s ->
        Graph.api.consoleGroupsMove(id, buildJsonObject {
            put("sector", s)
            put("toSectorId", toSectorId)
            if (parentId != null) put("parentId", parentId)
        })
    }

    fun merge(id: String, into: String) = send { s -> Graph.api.consoleGroupsMerge(id, GroupsMergeBody(s, into)) }
    fun delete(id: String) = send { s -> Graph.api.consoleGroupsDelete(id, GroupsSectorOnlyBody(s)) }
    fun bindHeading(key: String, groupId: String) =
        send { s -> Graph.api.consoleGroupsHeadingBind(GroupsHeadingBindBody(s, key, groupId = groupId)) }
    fun makeFromHeading(key: String, name: String) =
        send { s -> Graph.api.consoleGroupsHeadingBind(GroupsHeadingBindBody(s, key, name = name)) }
    fun forgetAlias(groupId: String, alias: String) =
        send { s -> Graph.api.consoleGroupsAliasRemove(groupId, GroupsAliasRemoveBody(s, alias)) }
}

// MARK: - search fold

/** Diacritic-insensitive, locale lower-case (Turkish dotless ı folds to i). */
private fun fold(s: String, locale: Locale): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(locale)
        .replace('ı', 'i')

private fun String.capFirst(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

private fun allNodes(groups: List<GroupNode>): List<GroupNode> = groups.flatMap { listOf(it) + it.children.orEmpty() }

// MARK: - the title row's overflow (expand / collapse all) + the busy slash

@Composable
fun GroupsTitleActions(vm: GroupsViewModel) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (vm.busy) WorkisMark(size = 16, breathing = true)
        Box {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.groups_collapse_all), tint = colors.ink)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.groups_expand_all), color = colors.ink) },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.muted) },
                    onClick = { open = false; vm.expandAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.groups_collapse_all), color = colors.ink) },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.muted) },
                    onClick = { open = false; vm.collapseAll() },
                )
            }
        }
    }
}

// MARK: - the screen

@Composable
fun ConsoleGroupsScreen(vm: GroupsViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    val locale = remember(lang) { Locale.forLanguageTag(lang) }
    LaunchedEffect(Unit) {
        ScreenContext.clear()
        if (vm.page == null) vm.load(null)
    }

    Column(Modifier.fillMaxSize()) {
        // the line under the title: the last answer's sentence, 8 s
        vm.message?.let { m ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, start = 4.dp, end = 4.dp)) {
                val tint = if (vm.messageIsError) colors.danger else SuccessGreen
                Icon(if (vm.messageIsError) WorkisIcons.Cross else WorkisIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
                Text(m, fontSize = 13.sp, lineHeight = 18.sp, color = tint)
            }
        }
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Column(Modifier.padding(4.dp)) {
                Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.retry), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText,
                    modifier = Modifier.clickable(enabled = !vm.busy) { vm.load(null) }.padding(vertical = 6.dp),
                )
            }
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                WorkisMark(size = 24, breathing = true)
            }
            else -> {
                GroupsField(vm.search, { vm.search = it }, stringResource(R.string.groups_search_prompt), leadingSearch = true)
                Spacer(Modifier.height(10.dp))
                GroupsList(vm, page, locale)
            }
        }
    }
    GroupsOverlays(vm, locale)
}

@Composable
private fun GroupsList(vm: GroupsViewModel, page: GroupsPage, locale: Locale) {
    val colors = WorkisTheme.colors
    val canWrite = page.canWrite == true
    val isCoord = page.isCoordinator == true
    val q = fold(vm.search.trim(), locale)
    val searching = q.isNotEmpty()
    fun hit(s: String?) = s != null && fold(s, locale).contains(q)
    fun hits(g: GroupNode): Boolean =
        hit(g.name) || g.headings.orEmpty().any { hit(it.label) } || g.children.orEmpty().any { hits(it) }
    val groups = page.groups.orEmpty()
    val shown = if (searching) groups.filter { hits(it) } else groups
    val unbound = page.unboundHeadings.orEmpty().let { u -> if (searching) u.filter { hit(it.label) } else u }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // 1. Sector
        item(key = "sector") {
            Eyebrow(stringResource(R.string.groups_sector), locale)
            GroupsCard {
                SectorPicker(vm, page)
                if (isCoord) {
                    Spacer(Modifier.height(6.dp))
                    AddRow(stringResource(R.string.groups_sector_add), enabled = !vm.busy) { vm.prompt = GroupsPrompt.SectorAdd }
                }
            }
            Footer(stringResource(R.string.groups_intro))
        }
        // 2. Permission
        if (isCoord) {
            item(key = "perm") {
                GroupsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.groups_leads_write_title), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                            Spacer(Modifier.height(3.dp))
                            Text(stringResource(R.string.groups_leads_write_sub), fontSize = 12.sp, lineHeight = 16.sp, color = colors.faint)
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = page.leadsWrite == true,
                            onCheckedChange = { vm.setLeadsWrite(it) },
                            enabled = !vm.busy,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.blue),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        } else if (!canWrite) {
            item(key = "perm") {
                GroupsCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(WorkisIcons.Lock, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.groups_read_only), fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        // 3. Sellers
        if (page.sellers.orEmpty().isNotEmpty()) {
            item(key = "sellers") {
                Eyebrow(stringResource(R.string.groups_sellers), locale)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                ) {
                    page.sellers.orEmpty().forEach { s -> SellerChip(vm, page, s) }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        // 4. Tree
        item(key = "tree-head") {
            Eyebrow("${page.sector?.name.orEmpty()} · ${stringResource(R.string.groups_title)}", locale)
            if (canWrite) {
                GroupsCard {
                    AddRow(stringResource(R.string.groups_add), enabled = !vm.busy) { vm.prompt = GroupsPrompt.GroupAdd }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (groups.isEmpty()) Muted(stringResource(R.string.groups_empty))
            else if (shown.isEmpty()) Muted(stringResource(R.string.groups_no_match))
        }
        items(shown, key = { "g-" + (it.id ?: it.name.orEmpty()) }) { g ->
            val id = g.id.orEmpty()
            val open = searching || id !in vm.collapsed
            GroupsCard(Modifier.padding(bottom = 8.dp)) {
                NodeRow(vm, page, g, top = true, open = open, locale = locale) { vm.toggle(id) }
                if (open) {
                    NodeBody(vm, page, g, q, locale)
                    g.children.orEmpty().forEach { k ->
                        Column(Modifier.padding(start = 16.dp, top = 8.dp)) {
                            NodeRow(vm, page, k, top = false, open = true, locale = locale, onToggle = null)
                            NodeBody(vm, page, k, q, locale)
                        }
                    }
                }
            }
        }
        item(key = "tree-foot") {
            Footer(stringResource(R.string.groups_tree_hint))
        }
        // 5. Unbound headings
        if (canWrite && page.unboundHeadings.orEmpty().isNotEmpty()) {
            item(key = "unbound") {
                Eyebrow(stringResource(R.string.groups_unbound_title), locale)
                if (unbound.isEmpty()) Muted(stringResource(R.string.groups_no_match))
                else GroupsCard {
                    unbound.forEachIndexed { i, h ->
                        UnboundRow(vm, h, locale)
                        if (i < unbound.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectorPicker(vm: GroupsViewModel, page: GroupsPage) {
    val colors = WorkisTheme.colors
    val sectors = page.sectors.orEmpty()
    val current = page.sector?.slug
    fun pick(slug: String?) { if (slug != null && slug != current) vm.load(slug) }
    if (sectors.size <= 3) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            sectors.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = s.slug == current,
                    onClick = { pick(s.slug) },
                    enabled = !vm.busy,
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = sectors.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.canvas,
                        activeContentColor = colors.ink,
                        inactiveContainerColor = colors.surface,
                        inactiveContentColor = colors.muted,
                    ),
                ) { Text(s.name.orEmpty(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    } else {
        var open by remember { mutableStateOf(false) }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { open = true }.padding(vertical = 8.dp),
            ) {
                Text(page.sector?.name.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.ink, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.faint)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                sectors.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name.orEmpty(), color = colors.ink, fontWeight = if (s.slug == current) FontWeight.SemiBold else null) },
                        trailingIcon = if (s.slug == current) ({ Icon(WorkisIcons.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp)) }) else null,
                        onClick = { open = false; pick(s.slug) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerChip(vm: GroupsViewModel, page: GroupsPage, s: ai.workis.jowi.data.GroupSeller) {
    val colors = WorkisTheme.colors
    val others = page.otherSectors.orEmpty()
    val menu = page.isCoordinator == true && others.isNotEmpty() && s.id != null
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(colors.ink.copy(alpha = 0.07f), CircleShape)
                .clickable(enabled = menu && !vm.busy) { open = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(s.shortName ?: s.name.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("· ${s.familyCount ?: 0} " + stringResource(R.string.groups_family_unit), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint, maxLines = 1)
        }
        if (menu) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                MenuHeader(stringResource(R.string.groups_seller_move))
                others.forEach { x ->
                    DropdownMenuItem(
                        text = { Text(x.name.orEmpty(), color = colors.ink) },
                        onClick = {
                            open = false
                            val sid = s.id.orEmpty(); val to = x.id.orEmpty()
                            vm.confirm = GroupsConfirm(R.string.groups_move_to_sector, R.string.groups_seller_move_confirm, x.name, false) { vm.moveSeller(sid, to) }
                        },
                    )
                }
            }
        }
    }
}

/** Name + the "N ÜRÜN" badge + the row's "…" (never on Genel, never without canWrite). */
@Composable
private fun NodeRow(
    vm: GroupsViewModel,
    page: GroupsPage,
    g: GroupNode,
    top: Boolean,
    open: Boolean,
    locale: Locale,
    onToggle: (() -> Unit)?,
) {
    val colors = WorkisTheme.colors
    val n = (if (top) g.totalCount else g.productCount) ?: 0
    val actions = page.canWrite == true && g.isGenel != true && g.id != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        if (top) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            g.name.orEmpty(),
            fontSize = if (top) 15.sp else 14.sp,
            fontWeight = if (top) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            ("$n " + stringResource(R.string.groups_product_unit)).uppercase(locale),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp,
            color = if (n > 0) colors.accentText else colors.faint,
            modifier = Modifier.background(colors.ink.copy(alpha = 0.07f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
        )
        if (actions) NodeActions(vm, page, g, top, locale)
    }
}

@Composable
private fun NodeActions(vm: GroupsViewModel, page: GroupsPage, g: GroupNode, top: Boolean, locale: Locale) {
    val colors = WorkisTheme.colors
    val id = g.id.orEmpty()
    val renamable = g.renamable == true
    val movable = g.movable == true
    val subgroup = top
    val deletable = g.deletable == true
    if (!renamable && !movable && !subgroup && !deletable) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !vm.busy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            @Composable
            fun item(res: Int, danger: Boolean = false, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(stringResource(res).capFirst(locale), color = if (danger) colors.danger else colors.ink) },
                onClick = { open = false; onClick() },
            )
            if (renamable) item(R.string.groups_rename) { vm.prompt = GroupsPrompt.Rename(g) }
            if (movable) {
                item(R.string.groups_parent) { vm.pick = GroupsPick.Move(g) }
                item(R.string.groups_merge) { vm.pick = GroupsPick.Merge(g) }
            }
            if (subgroup) item(R.string.groups_subgroup_add) { vm.prompt = GroupsPrompt.Subgroup(g) }
            if (deletable) item(R.string.groups_delete, danger = true) {
                vm.confirm = GroupsConfirm(R.string.groups_delete, R.string.groups_delete_confirm, g.name, true) { vm.delete(id) }
            }
        }
    }
}

/** Under a row: the catalogue headings bound to it (× on alias chips) and "+ başlık bağla". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeBody(vm: GroupsViewModel, page: GroupsPage, g: GroupNode, q: String, locale: Locale) {
    val colors = WorkisTheme.colors
    val canWrite = page.canWrite == true
    val heads = g.headings.orEmpty().filter { q.isEmpty() || fold(it.label.orEmpty(), locale).contains(q) }
    val bind = canWrite && g.isGenel != true && page.unboundHeadings.orEmpty().isNotEmpty() && g.id != null
    if (heads.isEmpty() && !bind) return
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Text(
            stringResource(R.string.groups_headings).uppercase(locale),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp, color = colors.faint,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            heads.forEach { h -> HeadingChip(vm, h, g, canWrite) }
            if (bind) {
                val border = colors.border
                // its own clickable, sized to its content — a tap elsewhere on the row never fires it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .drawBehind {
                            drawRoundRect(
                                color = border,
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        }
                        .clip(CircleShape)
                        .clickable(enabled = !vm.busy) { vm.pick = GroupsPick.Bind(g) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.groups_heading_bind), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentText, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HeadingChip(vm: GroupsViewModel, h: GroupHeading, g: GroupNode, canWrite: Boolean) {
    val colors = WorkisTheme.colors
    val alias = h.alias?.takeIf { canWrite && it.isNotEmpty() && g.id != null }
    val forget = stringResource(R.string.groups_heading_forget)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(if (h.idle == true) 0.55f else 1f)
            .background(colors.ink.copy(alpha = 0.07f), CircleShape)
            .padding(start = 9.dp, end = if (alias != null) 3.dp else 9.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(h.label.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1)
        val n = h.productCount ?: 0
        if (n > 0) {
            Spacer(Modifier.width(5.dp))
            Text("$n", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint)
        }
        if (alias != null) {
            Spacer(Modifier.width(2.dp))
            // the × alone is the button — the chip and the row are not
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !vm.busy) { vm.forgetAlias(g.id.orEmpty(), alias) }
                    .semantics { contentDescription = forget },
            ) {
                Icon(WorkisIcons.Cross, contentDescription = null, tint = colors.faint, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun UnboundRow(vm: GroupsViewModel, h: GroupUnboundHeading, locale: Locale) {
    val colors = WorkisTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(h.label.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
            val sellers = h.sellers.orEmpty()
            if (sellers.isNotEmpty()) {
                Text(sellers.joinToString(" · "), fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${h.productCount ?: 0} " + stringResource(R.string.groups_product_unit).lowercase(locale),
            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint,
        )
        if (h.key != null) Box {
            IconButton(onClick = { open = true }, enabled = !vm.busy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.groups_unbound_make), color = colors.ink) },
                    onClick = { open = false; vm.prompt = GroupsPrompt.Make(h) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.groups_unbound_attach), color = colors.ink) },
                    onClick = { open = false; vm.pick = GroupsPick.Attach(h) },
                )
            }
        }
    }
}

// MARK: - prompts, pickers, confirms

@Composable
private fun GroupsOverlays(vm: GroupsViewModel, locale: Locale) {
    val page = vm.page ?: return
    val productUnit = stringResource(R.string.groups_product_unit)
    when (val p = vm.prompt) {
        GroupsPrompt.SectorAdd -> TextPromptDialog(
            stringResource(R.string.groups_sector_add), stringResource(R.string.groups_sector_new), "",
            confirmLabel = R.string.groups_sector_add, onDismiss = { vm.prompt = null },
        ) { vm.prompt = null; vm.addSector(it) }
        GroupsPrompt.GroupAdd -> TextPromptDialog(
            stringResource(R.string.groups_add), stringResource(R.string.groups_new_placeholder), "",
            confirmLabel = R.string.groups_add, onDismiss = { vm.prompt = null },
        ) { vm.prompt = null; vm.addGroup(it) }
        is GroupsPrompt.Subgroup -> TextPromptDialog(
            stringResource(R.string.groups_subgroup_add).capFirst(locale), stringResource(R.string.groups_subgroup_new), "",
            message = p.parent.name, confirmLabel = R.string.groups_add, onDismiss = { vm.prompt = null },
        ) { vm.prompt = null; vm.addGroup(it, p.parent.id) }
        is GroupsPrompt.Rename -> TextPromptDialog(
            stringResource(R.string.groups_rename).capFirst(locale), stringResource(R.string.groups_word), p.node.name.orEmpty(),
            message = p.node.name, onDismiss = { vm.prompt = null },
        ) { vm.prompt = null; vm.rename(p.node.id.orEmpty(), it) }
        is GroupsPrompt.Make -> TextPromptDialog(
            stringResource(R.string.groups_unbound_make), stringResource(R.string.groups_new_placeholder), p.heading.suggestedName.orEmpty(),
            message = p.heading.label, confirmLabel = R.string.groups_add, onDismiss = { vm.prompt = null },
        ) { vm.prompt = null; vm.makeFromHeading(p.heading.key.orEmpty(), it) }
        null -> {}
    }

    when (val k = vm.pick) {
        is GroupsPick.Merge -> SearchSheet(
            title = stringResource(R.string.groups_merge).capFirst(locale),
            prompt = stringResource(R.string.groups_merge_prompt),
            items = page.mergeTargets.orEmpty().filter { it.id != null && it.id != k.node.id },
            label = { it.name.orEmpty() },
            subtitle = { listOfNotNull(it.parent, it.sector).joinToString(" · ") },
            locale = locale,
            onDismiss = { vm.pick = null },
        ) { t ->
            vm.pick = null
            val id = k.node.id.orEmpty(); val into = t.id.orEmpty()
            vm.confirm = GroupsConfirm(R.string.groups_merge, R.string.groups_merge_confirm, t.name, false) { vm.merge(id, into) }
        }
        is GroupsPick.Bind -> SearchSheet(
            title = stringResource(R.string.groups_heading_bind).capFirst(locale),
            prompt = stringResource(R.string.groups_heading_prompt),
            items = page.unboundHeadings.orEmpty().filter { it.key != null },
            label = { it.label.orEmpty() },
            subtitle = { h -> (listOf("${h.productCount ?: 0} $productUnit") + h.sellers.orEmpty().joinToString(" · ").let { if (it.isEmpty()) emptyList() else listOf(it) }).joinToString(" · ") },
            locale = locale,
            onDismiss = { vm.pick = null },
        ) { h -> vm.pick = null; vm.bindHeading(h.key.orEmpty(), k.node.id.orEmpty()) }
        is GroupsPick.Attach -> {
            val subLabel = stringResource(R.string.groups_subgroup_add)
            val topLabel = stringResource(R.string.groups_word)
            SearchSheet(
                title = stringResource(R.string.groups_unbound_attach),
                prompt = stringResource(R.string.groups_merge_prompt),
                items = allNodes(page.groups.orEmpty()).filter { it.isGenel != true && it.id != null },
                label = { it.name.orEmpty() },
                subtitle = { if (it.parentId == null) topLabel else subLabel },
                locale = locale,
                onDismiss = { vm.pick = null },
            ) { g -> vm.pick = null; vm.bindHeading(k.heading.key.orEmpty(), g.id.orEmpty()) }
        }
        is GroupsPick.Move -> MoveSheet(vm, page, k.node, locale)
        null -> {}
    }

    vm.confirm?.let { c ->
        val sentence = stringResource(c.sentenceRes)
        ConfirmDialog(
            title = stringResource(c.titleRes).capFirst(locale),
            message = if (c.target.isNullOrBlank()) sentence else "$sentence\n\n${c.target}",
            destructive = c.destructive,
            onDismiss = { vm.confirm = null },
            onConfirm = c.run,
        )
    }
}

/** The parent pick: top level (sub-rows) · a top group of this sector · another sector (confirmed). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(vm: GroupsViewModel, page: GroupsPage, g: GroupNode, locale: Locale) {
    val colors = WorkisTheme.colors
    val id = g.id.orEmpty()
    val topLevel = stringResource(R.string.groups_top_level)
    val toSector = stringResource(R.string.groups_move_to_sector)
    val close = { vm.pick = null }
    ModalBottomSheet(onDismissRequest = close, containerColor = colors.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            item {
                Text(stringResource(R.string.groups_parent), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                Text(g.name.orEmpty(), fontSize = 13.sp, color = colors.muted)
                Spacer(Modifier.height(10.dp))
            }
            if (g.parentId != null) item { SheetRow(topLevel, null) { close(); vm.moveWithin(id, null) } }
            items(page.parents.orEmpty().filter { it.id != null && it.id != g.id && it.id != g.parentId }) { p ->
                SheetRow(p.name.orEmpty(), null) { close(); vm.moveWithin(id, p.id) }
            }
            page.otherSectors.orEmpty().filter { it.id != null }.forEach { x ->
                item {
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("$toSector ${x.name.orEmpty()}", locale)
                }
                val sid = x.id.orEmpty()
                item {
                    SheetRow(topLevel, null) {
                        close()
                        vm.confirm = GroupsConfirm(R.string.groups_move_to_sector, R.string.groups_move_sector_confirm, x.name, false) { vm.moveToSector(id, sid, null) }
                    }
                }
                items(x.parents.orEmpty().filter { it.id != null }) { p ->
                    SheetRow(p.name.orEmpty(), null) {
                        close()
                        val pid = p.id
                        vm.confirm = GroupsConfirm(R.string.groups_move_to_sector, R.string.groups_move_sector_confirm, "${x.name.orEmpty()} · ${p.name.orEmpty()}", false) { vm.moveToSector(id, sid, pid) }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

/** An alert with one field: one tinted action + cancel. */
@Composable
fun TextPromptDialog(
    title: String,
    placeholder: String,
    initial: String,
    message: String? = null,
    confirmLabel: Int = R.string.done,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = WorkisTheme.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.ink) },
        text = {
            Column {
                message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, color = colors.muted)
                    Spacer(Modifier.height(10.dp))
                }
                GroupsField(text, { text = it }, placeholder)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(confirmLabel), color = if (text.isNotBlank()) colors.accentText else colors.faint, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}

/** A searchable bottom sheet over a list (merge target · heading to bind · group to attach to). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchSheet(
    title: String,
    prompt: String,
    items: List<T>,
    label: (T) -> String,
    subtitle: (T) -> String,
    locale: Locale,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    val colors = WorkisTheme.colors
    var query by remember { mutableStateOf("") }
    val q = fold(query.trim(), locale)
    val shown = if (q.isEmpty()) items else items.filter { fold(label(it), locale).contains(q) || fold(subtitle(it), locale).contains(q) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Spacer(Modifier.height(10.dp))
            GroupsField(query, { query = it }, prompt, leadingSearch = true)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                if (shown.isEmpty()) item { Muted(stringResource(R.string.groups_no_match)) }
                items(shown) { t -> SheetRow(label(t), subtitle(t).takeIf { it.isNotBlank() }) { onPick(t) } }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

// MARK: - small pieces

@Composable
private fun SheetRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
        subtitle?.let { Text(it, fontSize = 12.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun GroupsField(value: String, onChange: (String) -> Unit, placeholder: String, leadingSearch: Boolean = false) {
    val colors = WorkisTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = colors.faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (leadingSearch) ({ Icon(WorkisIcons.Search, contentDescription = null, tint = colors.faint, modifier = Modifier.size(18.dp)) }) else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
            focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
            focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GroupsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun Eyebrow(text: String, locale: Locale) {
    Text(
        text.uppercase(locale),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
        color = WorkisTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun Footer(text: String) {
    Text(
        text, fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.muted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 18.dp),
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, fontSize = 13.sp, color = WorkisTheme.colors.faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text, fontSize = 12.sp, color = WorkisTheme.colors.faint,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AddRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accentText)
    }
}
