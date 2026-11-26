package ai.workis.jowi.ui.screens.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.R
import kotlinx.coroutines.launch
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.ApplicationRow
import ai.workis.jowi.data.JowiAnswered
import ai.workis.jowi.data.JowiTopic
import ai.workis.jowi.data.QuestionTopic
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.expert.ExpertKnowledgeScreen
import ai.workis.jowi.ui.screens.expert.KnowledgeSeat
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.Graph
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

sealed interface ConsoleDest {
    data object Home : ConsoleDest
    data object Pipeline : ConsoleDest
    /** The Panel's "Jowi answered" chips land here with the lane open and a filter preselected. */
    data class Questions(val openJowi: Boolean = false, val source: String? = null, val topic: String? = null) : ConsoleDest
    data class Partners(val initialSeat: String = "partner") : ConsoleDest
    data object Conversations : ConsoleDest
    data class Thread(val id: String, val partner: String?, val back: ConsoleDest = Conversations) : ConsoleDest
    data class Detail(val row: ApplicationRow) : ConsoleDest
    data object Knowledge : ConsoleDest
    /** Ürünler (Phase C, 2026-09-16): the seller picker, then a seller's catalogue with `?seller=`. */
    data object CatalogSellers : ConsoleDest
    data class Catalog(val seller: String, val name: String?) : ConsoleDest
    data class Family(val seller: String, val name: String?, val id: String) : ConsoleDest
    /** Gruplar (ledger 62): the Workis groups page, roles 3 | 4. */
    data object Groups : ConsoleDest
    /** Kargo (ledger 114): the network's control tower, page key `cargo`. */
    data object Cargo : ConsoleDest
    /** The web's six remaining pages (ledger 128): each screen draws its own bar and inner pages. */
    data object Sourcing : ConsoleDest
    data object Coverage : ConsoleDest
    data object Market : ConsoleDest
    data object Pnl : ConsoleDest
    data object Subs : ConsoleDest
    data object System : ConsoleDest
}

/**
 * The console pages this app has a screen for, in the web menu's order (ledger 113). A key
 * the server sends and the app cannot draw is skipped.
 */
private val ConsoleScreens = listOf(
    "panel", "apps", "partners", "queries", "sourcing", "coverage", "cargo", "knowledge", "products", "groups",
    "market", "pnl", "subs", "system",
)

/** The six pages of ledger 128 — drawn only from `workis.console.pages`, never by the role fallback. */
private val NewerPages = listOf("sourcing", "coverage", "market", "pnl", "subs", "system")

/** The door a page key opens; "conversations" rides with `queries` (the same page on the web). */
private fun destOf(page: String): ConsoleDest? = when (page) {
    "apps" -> ConsoleDest.Pipeline
    "partners" -> ConsoleDest.Partners()
    "queries" -> ConsoleDest.Questions()
    "conversations" -> ConsoleDest.Conversations
    "cargo" -> ConsoleDest.Cargo
    "knowledge" -> ConsoleDest.Knowledge
    "products" -> ConsoleDest.CatalogSellers
    "groups" -> ConsoleDest.Groups
    "sourcing" -> ConsoleDest.Sourcing
    "coverage" -> ConsoleDest.Coverage
    "market" -> ConsoleDest.Market
    "pnl" -> ConsoleDest.Pnl
    "subs" -> ConsoleDest.Subs
    "system" -> ConsoleDest.System
    else -> null
}

@Composable
fun ConsoleRoot(vm: ConsoleViewModel = viewModel()) {
    var dest by remember { mutableStateOf<ConsoleDest>(ConsoleDest.Home) }
    // `workis.console` from `auth/workspaces/` (ledger 113): null only while that answer is
    // pending or on a server that never sent the key — then every page, as before
    val workspaces by Graph.workspaces.value.collectAsState()
    val console = workspaces?.workis?.consoleValue
    // a server that never sent the key (role fallback) keeps the pages it always had: the six
    // newer doors answer 403 to a seat that does not hold them, so they come only from `pages`
    val pages = (console?.pages ?: (ConsoleScreens - NewerPages.toSet())).filter { it in ConsoleScreens }
    // a console without the Panel opens on the first page of its menu, once per launch
    LaunchedEffect(console) {
        if (!vm.openedFirst && console != null && "panel" !in pages) {
            pages.firstOrNull()?.let(::destOf)?.let { vm.openedFirst = true; dest = it }
        }
    }

    BackHandler(enabled = dest != ConsoleDest.Home) {
        dest = when (val d = dest) {
            is ConsoleDest.Thread -> d.back
            is ConsoleDest.Detail -> ConsoleDest.Pipeline
            else -> ConsoleDest.Home
        }
    }

    when (val d = dest) {
        is ConsoleDest.Home -> ConsoleHome(vm, console, pages) { dest = it }
        ConsoleDest.Cargo -> ConsoleSub(stringResource(R.string.cargo_net_title), { dest = ConsoleDest.Home }) { ConsoleCargoScreen() }
        ConsoleDest.Sourcing -> ConsoleSourcingScreen(onBack = { dest = ConsoleDest.Home })
        ConsoleDest.Coverage -> ConsoleCoverageScreen(onBack = { dest = ConsoleDest.Home })
        ConsoleDest.Market -> ConsoleMarketScreen(onBack = { dest = ConsoleDest.Home })
        ConsoleDest.Pnl -> ConsoleRegionPnlScreen(onBack = { dest = ConsoleDest.Home })
        ConsoleDest.Subs -> ConsoleSubscriptionsScreen(onBack = { dest = ConsoleDest.Home })
        ConsoleDest.System -> ConsoleSystemScreen(onBack = { dest = ConsoleDest.Home })
        is ConsoleDest.Pipeline -> ConsoleSub(stringResource(R.string.console_all_apps), { dest = ConsoleDest.Home }) {
            ConsolePipeline(vm) { dest = ConsoleDest.Detail(it) }
        }
        is ConsoleDest.Detail -> ApplicationDetailScreen(vm, d.row) { dest = ConsoleDest.Pipeline }
        ConsoleDest.CatalogSellers -> ConsoleSub(stringResource(R.string.catalog_title), { dest = ConsoleDest.Home }) {
            ConsoleCatalogSellersScreen { s -> dest = ConsoleDest.Catalog(s.partnerId.orEmpty(), s.name) }
        }
        is ConsoleDest.Catalog -> {
            val catalogVm: ai.workis.jowi.ui.screens.partner.CatalogViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "console-catalog-${d.seller}")
            catalogVm.seller = d.seller
            ai.workis.jowi.ui.screens.partner.CatalogScreen(catalogVm, onBack = { dest = ConsoleDest.CatalogSellers }, onOpenFamily = { dest = ConsoleDest.Family(d.seller, d.name, it) })
        }
        is ConsoleDest.Family -> ai.workis.jowi.ui.screens.partner.FamilyScreen(d.id, onBack = { dest = ConsoleDest.Catalog(d.seller, d.name) }, seller = d.seller)
        ConsoleDest.Groups -> {
            val groupsVm: GroupsViewModel = viewModel()
            ConsoleSub(stringResource(R.string.groups_title), { dest = ConsoleDest.Home }, trailing = { GroupsTitleActions(groupsVm) }) {
                ConsoleGroupsScreen(groupsVm)
            }
        }
        ConsoleDest.Knowledge -> ConsoleSub(stringResource(R.string.expert_knowledge_title), { dest = ConsoleDest.Home }) {
            ExpertKnowledgeScreen(KnowledgeSeat.Console)
        }
        is ConsoleDest.Questions -> ConsoleSub(stringResource(R.string.questions_title), { dest = ConsoleDest.Home }) {
            ConsoleQuestionsScreen(vm, openJowi = d.openJowi, jowiSource = d.source, jowiTopic = d.topic) { id, partner ->
                dest = ConsoleDest.Thread(id, partner, back = d)
            }
        }
        is ConsoleDest.Partners -> ConsoleSub(stringResource(R.string.partners_title), { dest = ConsoleDest.Home }) {
            ConsolePartnersScreen(vm, initialSeat = d.initialSeat)
        }
        is ConsoleDest.Conversations -> ConsoleSub(stringResource(R.string.conversations_title), { dest = ConsoleDest.Home }) {
            ConsoleConversationsScreen(vm) { id, partner -> dest = ConsoleDest.Thread(id, partner) }
        }
        is ConsoleDest.Thread -> ConsoleSub(d.partner ?: "", { dest = d.back }) {
            ConversationThreadScreen(vm, d.id)
        }
    }
}

/** Sub-screen scaffold: back-circle top-left + title, mirrors the iOS header pattern. */
@Composable
fun ConsoleSub(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    /** Replaces the plain bar title when given (the list's editable name) — it takes the title's weight(1f) slot itself. */
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.ink)
            }
            Spacer(Modifier.width(12.dp))
            if (titleContent != null) titleContent() else Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 22.sp,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ConsoleHome(vm: ConsoleViewModel, console: ai.workis.jowi.data.WorkspaceConsole?, pages: List<String>, onOpen: (ConsoleDest) -> Unit) {
    // a tab root: no seller screen is open (2026-09-17)
    LaunchedEffect(Unit) { ScreenContext.clear() }
    fun has(page: String) = page in pages
    val hasPanel = has("panel")

    val colors = WorkisTheme.colors
    // The menu follows the coordinator's ticks (ledger 113): `auth/workspaces/` is read again
    // every time the root appears — coming back from a page that answered 403 lands here.
    LaunchedEffect(Unit) { Graph.workspaces.refresh() }
    // only what this console may read: `console/summary/` answers 403 without the Panel page
    LaunchedEffect(hasPanel) { if (hasPanel && vm.summary == null) vm.loadSummary() }
    val s = if (hasPanel) vm.summary else null
    // Yazışmalar counts OPEN threads: the summary's field, else the open rows of the list
    // (fetched only when the summary lacks the field) — never the raw list size
    LaunchedEffect(s) { if (has("queries") && s != null && s.openConversations == null && vm.conversations == null) vm.loadConversations() }
    val openConversations = s?.openConversations ?: vm.conversations?.conversations?.count { it.open == true }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                Graph.workspaces.refresh()
                if (hasPanel) vm.loadSummary()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(colors.canvas),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.console_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (vm.loading) WorkisMark(size = 16, breathing = true)
        }
        Spacer(Modifier.height(14.dp))

        if (console?.pages?.isEmpty() == true) {
            // a pilot with nothing ticked yet
            Text(
                stringResource(R.string.console_no_pages), fontSize = 15.sp, lineHeight = 21.sp, color = colors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        } else if (pages.isEmpty()) {
            // ticked pages, none of which has a screen here: the sentence and the door to the web console
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.console_pages_on_web), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted)
                androidx.compose.material3.OutlinedButton(
                    onClick = { ai.workis.jowi.ui.components.openInApp(context, "https://workis.ai/workis/console/") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.console_open), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink) }
            }
        } else if (!hasPanel) {
            // the menu of a console that has no Panel: one door per ticked page, in the server's order
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                pages.forEach { page ->
                    PageDoor(page, onOpen)
                    if (page == "queries") PageDoor("conversations", onOpen)
                }
            }
        } else {
        // The web Panel's order (ledger 61, doors grouped by ledger 133): two big tiles
        // (applications, questions), the activity card, the door groups (Network · Supply ·
        // Catalogue · Finance, then Sistem alone), then "Jowi yanıtladı" and "/ Soru konuları".
        // Every tile IS the door to its surface — no duplicated list previews below.
        // a door is drawn only for a page this console holds (`pages`, ledger 113)
        if (has("apps") || has("queries")) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(160.dp)) {
            // the web hides the "onay bekliyor" line at zero
            if (!has("apps")) Spacer(Modifier.weight(1f)) else BigTile(
                icon = WorkisIcons.Tray, number = s?.newApplications, label = stringResource(R.string.stat_new_apps),
                sub = s?.awaitingApproval?.takeIf { it > 0 }?.let { "$it " + stringResource(R.string.stat_awaiting) },
                modifier = Modifier.weight(1f),
            ) { onOpen(ConsoleDest.Pipeline) }
            if (!has("queries")) Spacer(Modifier.weight(1f)) else BigTile(
                icon = Icons.Filled.Email, number = s?.openQuestions, label = stringResource(R.string.stat_open_q),
                sub = s?.oldestQuestionDays?.takeIf { it > 0 }?.let { stringResource(R.string.oldest_word) + " $it " + stringResource(R.string.days_word) },
                modifier = Modifier.weight(1f),
            ) { onOpen(ConsoleDest.Questions()) }
        }
        Spacer(Modifier.height(14.dp))

        // Seat activity: read, not a door (ledger 133) — ONE plain content card split by a
        // hairline: title, the fraction in mono, the detail line. In dark the figures are READ
        // and the door cards GONE THROUGH, told apart by hue: the card stands on the table's
        // warm ground with its own hairline (ledger 142). Light keeps the plain card.
        s?.activeSeats?.let { a ->
            val warm = colors.isDark
            val edge = if (warm) colors.tableBorder else colors.border
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(if (warm) colors.tableGround else colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, edge, RoundedCornerShape(20.dp)),
            ) {
                ActivityCell(
                    stringResource(R.string.tile_active_seats),
                    value = "${a.activeSeats ?: 0} / ${a.totalSeats ?: 0}",
                    detail = "${a.days ?: 7} " + stringResource(R.string.days_word),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(edge))
                ActivityCell(
                    stringResource(R.string.tile_active_firms),
                    value = "${a.activePartners ?: 0} / ${a.totalPartners ?: 0}",
                    detail = "${a.lists ?: 0} " + stringResource(R.string.lists_unit) + " · ${a.quotes ?: 0} " + stringResource(R.string.quotes_unit) + " · ${a.orders ?: 0} " + stringResource(R.string.orders_unit),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // The doors, grouped by what they are for (ledger 133, iOS c88c789): one content card
        // per group, its doors three abreast. A door is drawn only for a page this console
        // holds (`pages`, ledger 113); a group with none is not drawn. The count rows of old
        // (Partnerler · Uzmanlar · Yazışmalar) are the Network group's cells now.
        val expertsFace = stringResource(R.string.tile_experts) to WorkisIcons.GraduationCap
        fun held(page: String): PanelDoor? = if (has(page)) destOf(page)?.let { PanelDoor(page, it) } else null
        DoorGroup(stringResource(R.string.console_group_network), listOfNotNull(
            if (has("partners")) PanelDoor("partners", ConsoleDest.Partners(), s?.activePartners) else null,
            // coordinator only — null for a lead
            s?.experts?.takeIf { has("partners") }?.let { PanelDoor("experts", ConsoleDest.Partners(initialSeat = "expert"), it, expertsFace) },
            // Yazışmalar counts OPEN threads (the summary's field, else the open rows of the list)
            if (has("queries")) PanelDoor("conversations", ConsoleDest.Conversations, openConversations) else null,
        ), onOpen)
        DoorGroup(stringResource(R.string.console_group_supply), listOfNotNull(held("sourcing"), held("coverage"), held("cargo")), onOpen)
        DoorGroup(stringResource(R.string.console_group_catalog), listOfNotNull(held("products"), held("groups"), held("knowledge")), onOpen)
        DoorGroup(stringResource(R.string.console_group_finance), listOfNotNull(held("market"), held("pnl"), held("subs")), onOpen)
        DoorGroup(null, listOfNotNull(held("system")), onOpen)

        // web order: Jowi answered above the question topics (both open Sorular)
        s?.jowiAnswered?.takeIf { has("queries") && (it.total ?: 0) > 0 }?.let { ja ->
            JowiAnsweredBlock(ja) { source, topic -> onOpen(ConsoleDest.Questions(openJowi = true, source = source, topic = topic)) }
            Spacer(Modifier.height(14.dp))
        }

        // "/ Soru konuları" — the web's own section under "Jowi yanıtladı" (the count and the
        // age moved up into the big tile). Header → the FULL list; each bento cell → that topic.
        val topics = (if (has("queries")) s?.questionTopics.orEmpty() else emptyList()).filter { !it.topic.isNullOrEmpty() }.sortedByDescending { it.count ?: 0 }
        if (topics.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(ConsoleDest.Questions()) },
                ) {
                    Text("/", fontFamily = WorkisMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.accentText)
                    Text(stringResource(R.string.topics_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
                }
                // bento: cell SIZE carries the count — hero + two beside it, three small, two smaller (eight at most)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(150.dp)) {
                    TopicCell(topics[0], BentoStyle.Hero, Modifier.weight(1f).fillMaxHeight()) { onOpen(ConsoleDest.Questions(topic = topics[0].topic)) }
                    if (topics.size > 1) Column(Modifier.width(136.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TopicCell(topics[1], BentoStyle.Medium, Modifier.weight(1f)) { onOpen(ConsoleDest.Questions(topic = topics[1].topic)) }
                        if (topics.size > 2) TopicCell(topics[2], BentoStyle.Medium, Modifier.weight(1f)) { onOpen(ConsoleDest.Questions(topic = topics[2].topic)) }
                    }
                }
                if (topics.size > 3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(68.dp)) {
                    topics.drop(3).take(3).forEach { t -> TopicCell(t, BentoStyle.Small, Modifier.weight(1f).fillMaxHeight()) { onOpen(ConsoleDest.Questions(topic = t.topic)) } }
                }
                if (topics.size > 6) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(60.dp)) {
                    topics.drop(6).take(2).forEach { t -> TopicCell(t, BentoStyle.Small, Modifier.weight(1f).fillMaxHeight()) { onOpen(ConsoleDest.Questions(topic = t.topic)) } }
                }
            }
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = colors.danger, fontSize = 13.sp)
        }
        } // the Panel board
        Spacer(Modifier.height(30.dp))
    }
    }
}

/** One door of a console that has no Panel — the page's own word and icon, full width. */
@Composable
private fun PageDoor(page: String, onOpen: (ConsoleDest) -> Unit) {
    val dest = destOf(page) ?: return
    val (label, icon) = pageFace(page)
    CapsuleDoor(icon, label, Modifier.fillMaxWidth()) { onOpen(dest) }
}

/** A page's own word and icon — the door list and the Panel's door groups read the same pair. */
@Composable
private fun pageFace(page: String): Pair<String, ImageVector> = when (page) {
        "apps" -> stringResource(R.string.console_all_apps) to WorkisIcons.Tray
        "partners" -> stringResource(R.string.partners_title) to WorkisIcons.Building
        "queries" -> stringResource(R.string.questions_title) to Icons.Filled.Email
        "conversations" -> stringResource(R.string.conversations_title) to WorkisIcons.Bubble
        "cargo" -> stringResource(R.string.cargo_title) to WorkisIcons.Truck
        "knowledge" -> stringResource(R.string.expert_knowledge_title) to WorkisIcons.Pencil
        "products" -> stringResource(R.string.catalog_title) to WorkisIcons.ShippingBox
        "sourcing" -> stringResource(R.string.console_sourcing_title) to WorkisIcons.Search
        "coverage" -> stringResource(R.string.console_coverage_title) to WorkisIcons.Checklist
        "market" -> stringResource(R.string.console_market_title) to WorkisIcons.Globe
        "pnl" -> stringResource(R.string.console_pnl_title) to WorkisIcons.ChartBars
        "subs" -> stringResource(R.string.console_subs_title) to WorkisIcons.CreditCard
        "system" -> stringResource(R.string.console_system_title) to WorkisIcons.Shield
        else -> stringResource(R.string.groups_door) to WorkisIcons.Grid
}

/**
 * "Jowi yanıtladı · son 7 gün" — the web lane's block: source cells and the
 * topic bento whose headline is the VERDICT line "👍 n · 👎 m · ✓ r" (open
 * dislikes in danger, reviewed in success green); the question count is the
 * small caption. Two awaiting badges in the header. Cells deep-link into the
 * Sorular lane, filtered.
 */
@Composable
private fun JowiAnsweredBlock(ja: JowiAnswered, onFilter: (source: String?, topic: String?) -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(WorkisIcons.Sparkle, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("${ja.total ?: 0}", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = colors.ink)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.jowi_answered_title) + " · " + stringResource(R.string.jowi_answered_last) +
                    " ${ja.days ?: 7} " + stringResource(R.string.days_word),
                fontSize = 14.sp, lineHeight = 18.sp, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val staffOpen = ja.reviewOpen ?: 0
        val expertOpen = ja.dislikedOpen ?: 0
        if (staffOpen > 0 || expertOpen > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (staffOpen > 0) AwaitingBadge("$staffOpen " + stringResource(R.string.jowi_awaiting_staff))
                if (expertOpen > 0) AwaitingBadge("$expertOpen " + stringResource(R.string.jowi_awaiting_expert))
            }
        }
        // source cells: ALL sources as on the web (ledger 61), equal width, sorted by count
        val sources = ja.sources.orEmpty().filter { it.source != null && (it.count ?: 0) > 0 }
            .sortedByDescending { it.count ?: 0 }
        if (sources.isNotEmpty()) {
            // ≤3 share one row; more go two a row (a fourth in one row clipped the verdict digits)
            val rows = if (sources.size > 3) sources.chunked(2) else listOf(sources)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(96.dp)) {
                        row.forEach { src ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(colors.ink.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                                    .clickable { onFilter(src.source, null) }
                                    .padding(10.dp),
                            ) {
                                Text(
                                    (src.label ?: src.source.orEmpty()).uppercase(),
                                    fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.sp,
                                    lineHeight = 12.sp, color = colors.accentText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                VerdictLine(src.up ?: 0, src.downOpen ?: src.down ?: 0, src.downReviewed ?: 0, size = 18.sp, tint = colors.ink)
                                Text("${src.count ?: 0} " + stringResource(R.string.question_unit), fontSize = 11.sp, color = colors.faint)
                            }
                        }
                    }
                }
            }
        }
        // topics via the bento: hero + two runners-up + three small + two smaller (eight, ledger 61)
        val topics = ja.topics.orEmpty().filter { !it.topic.isNullOrEmpty() && (it.count ?: 0) > 0 }
            .sortedByDescending { it.count ?: 0 }
        if (topics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(160.dp)) {
                LaneCell(topics[0], BentoStyle.Hero, Modifier.weight(1f).fillMaxHeight()) { onFilter(null, topics[0].topic) }
                if (topics.size > 1) {
                    Column(Modifier.width(136.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LaneCell(topics[1], BentoStyle.Medium, Modifier.weight(1f)) { onFilter(null, topics[1].topic) }
                        if (topics.size > 2) LaneCell(topics[2], BentoStyle.Medium, Modifier.weight(1f)) { onFilter(null, topics[2].topic) }
                    }
                }
            }
            if (topics.size > 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(76.dp)) {
                    topics.drop(3).take(3).forEach { tp ->
                        LaneCell(tp, BentoStyle.Small, Modifier.weight(1f).fillMaxHeight()) { onFilter(null, tp.topic) }
                    }
                }
            }
            if (topics.size > 6) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(60.dp)) {
                    topics.drop(6).take(2).forEach { tp ->
                        LaneCell(tp, BentoStyle.Small, Modifier.weight(1f).fillMaxHeight()) { onFilter(null, tp.topic) }
                    }
                }
            }
        }
    }
}

private enum class BentoStyle { Hero, Medium, Small }

@Composable
private fun AwaitingBadge(text: String) {
    Text(
        text,
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Beige,
        modifier = Modifier.background(BeigeBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * "👍 n · 👎 m · ✓ r" in mono digits — the cell's headline. The big 👎 is the
 * OPEN count (danger when > 0; the glyph and the "✓ r" caption keep it
 * readable without the colour); ✓ r = reviewed, hidden at 0.
 */
@Composable
fun VerdictLine(up: Int?, downOpen: Int, downReviewed: Int, size: androidx.compose.ui.unit.TextUnit, tint: androidx.compose.ui.graphics.Color) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (up != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("👍", fontSize = size * 0.7f)
                Text("$up", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = size, color = tint)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("👎", fontSize = size * 0.7f)
            Text("$downOpen", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = size, color = if (downOpen > 0) colors.danger else tint)
        }
        if (downReviewed > 0) {
            Text("✓ $downReviewed", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = maxOf(10f, size.value * 0.55f).sp, color = SuccessGreen)
        }
    }
}

@Composable
private fun LaneCell(item: JowiTopic, style: BentoStyle, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val hero = style == BentoStyle.Hero
    Column(
        modifier = modifier
            .background(
                if (hero) BeigeBg else colors.ink.copy(alpha = if (style == BentoStyle.Medium) 0.07f else 0.05f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(if (hero) 14.dp else 10.dp),
    ) {
        Text(
            item.topic.orEmpty(),
            fontWeight = FontWeight.SemiBold,
            fontSize = when (style) { BentoStyle.Hero -> 15.sp; BentoStyle.Medium -> 13.sp; BentoStyle.Small -> 12.sp },
            color = if (hero) Beige else colors.accentText,
            maxLines = if (hero) 2 else 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        VerdictLine(
            item.up, item.downOpen ?: item.down ?: 0, item.downReviewed ?: 0,
            size = when (style) { BentoStyle.Hero -> 24.sp; BentoStyle.Medium -> 15.sp; BentoStyle.Small -> 13.sp },
            tint = if (hero) Beige else colors.ink,
        )
        Text(
            "${item.count ?: 0} " + stringResource(R.string.question_unit),
            fontSize = if (hero) 12.sp else 10.sp,
            color = if (hero) Beige.copy(alpha = 0.8f) else colors.faint,
        )
    }
}

@Composable
private fun CapsuleDoor(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
    }
}

/** The seller picker: name; right "N inceleme bekliyor" accent, "N yayındaki teklif" faint. */
@Composable
private fun ConsoleCatalogSellersScreen(onPick: (ai.workis.jowi.data.CatalogSeller) -> Unit) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    // the picker is a root too: the last seller's catalogue must not ride along
    LaunchedEffect(Unit) { ai.workis.jowi.data.ScreenContext.clear() }
    var sellers by remember { mutableStateOf<List<ai.workis.jowi.data.CatalogSeller>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (r, err) = ai.workis.jowi.data.partnerCall(lang) { Graph.api.consoleCatalogSellers() }
        if (r != null) sellers = r.sellers.orEmpty() else error = err
    }
    when {
        error != null -> Text(error.orEmpty(), fontSize = 13.sp, color = colors.danger)
        sellers == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 24, breathing = true) }
        sellers.orEmpty().isEmpty() -> Text(stringResource(R.string.catalog_sellers_empty), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
        else -> Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
        ) {
            val rows = sellers.orEmpty()
            rows.forEachIndexed { i, s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 12.dp),
                ) {
                    Text(s.name ?: s.partnerId.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.nProposed ?: 0} " + stringResource(R.string.catalog_proposed_short), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = if ((s.nProposed ?: 0) > 0) colors.accentText else colors.faint)
                        Text("${s.nOffers ?: 0} " + stringResource(R.string.catalog_live_offers), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

/** One half of the Panel's activity card (ledger 133): title, the fraction in mono, its detail line. */
@Composable
private fun ActivityCell(title: String, value: String, detail: String, modifier: Modifier) {
    val colors = WorkisTheme.colors
    // in dark the card is on the table ground (ledger 142): its quiet lines take the table's faint ink
    val quiet = if (colors.isDark) colors.tableFaint else colors.muted
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 13.sp, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
        Text(detail, fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, color = quiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A door on the Panel's group cards (ledger 133); `key` is a page key, or `experts` / `conversations`. */
private data class PanelDoor(
    val key: String,
    val dest: ConsoleDest,
    val count: Int? = null,
    val face: Pair<String, ImageVector>? = null,
)

/**
 * A group of doors (ledger 133): the heading (stored in capitals, never upper-cased here) and up
 * to three doors abreast in one content card, hairlines between the cells. More wrap to a second
 * row; fewer share the width; a lone door is a full-width row with a chevron. No doors, nothing.
 */
@Composable
private fun DoorGroup(title: String?, doors: List<PanelDoor>, onOpen: (ConsoleDest) -> Unit) {
    if (doors.isEmpty()) return
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape),
    ) {
        if (title != null) Text(
            title,
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.7.sp, color = colors.muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        doors.chunked(3).forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { n, d ->
                    if (n > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                    DoorCell(d, wide = doors.size == 1, modifier = Modifier.weight(1f).fillMaxHeight()) { onOpen(d.dest) }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun DoorCell(door: PanelDoor, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val (label, icon) = door.face ?: pageFace(door.key)
    if (wide) {
        // a lone door is a row, not a third of one
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clickable(onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint)
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                .clickable(onClick = onClick)
                .heightIn(min = 74.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(21.dp))
                Spacer(Modifier.weight(1f))
                door.count?.let { Text(it.toString(), fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink, modifier = Modifier.padding(start = 4.dp)) }
            }
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A big square tile: icon up, the mono number, its label, an accent sub-line only when there is one. */
@Composable
private fun BigTile(icon: ImageVector, number: Int?, label: String, sub: String?, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(20.dp))
        Spacer(Modifier.weight(1f))
        Text(number?.toString() ?: "—", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = colors.ink)
        Text(label, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        sub?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = WorkisMono, fontSize = 12.sp, color = colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A "/ Soru konuları" bento cell — the hero wears the one beige fill, the rest a faint ink wash. */
@Composable
private fun TopicCell(topic: QuestionTopic, style: BentoStyle, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val hero = style == BentoStyle.Hero
    Column(
        modifier = modifier
            .background(if (hero) BeigeBg else colors.ink.copy(alpha = if (style == BentoStyle.Medium) 0.07f else 0.05f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(if (hero) 14.dp else 10.dp),
    ) {
        Text(
            topic.topic.orEmpty(),
            fontWeight = FontWeight.SemiBold,
            fontSize = when (style) { BentoStyle.Hero -> 15.sp; BentoStyle.Medium -> 13.sp; BentoStyle.Small -> 12.sp },
            color = if (hero) Beige else colors.accentText,
            maxLines = if (hero) 2 else 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            (topic.count ?: 0).toString(),
            fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold,
            fontSize = when (style) { BentoStyle.Hero -> 34.sp; BentoStyle.Medium -> 18.sp; BentoStyle.Small -> 15.sp },
            color = if (hero) Beige else colors.ink,
        )
    }
}
