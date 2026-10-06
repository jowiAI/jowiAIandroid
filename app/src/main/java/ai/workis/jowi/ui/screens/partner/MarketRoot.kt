package ai.workis.jowi.ui.screens.partner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.PartnerBadges
import ai.workis.jowi.data.PartnerCompany
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.components.ChangesWatch
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

/** The partner Panel ("bugünün masası", 2026-10-05) and the doors it opens by seat. */
sealed interface MarketDest {
    data object Home : MarketDest
    data object Lists : MarketDest
    /** `back`: Listeler, Siparişler when its quote-stage strip opened the list, the Panel from a "Sizde" row. */
    data class ListDetail(val id: String, val back: MarketDest = Lists) : MarketDest
    data object Quotes : MarketDest
    data class Compose(val invitationId: String) : MarketDest
    data object Catalog : MarketDest
    data class Family(val id: String) : MarketDest
    data object Orders : MarketDest
    /** One order's stage (`orders/{id}/stage/`) — opened from the card's title row. */
    data class Stage(val orderId: String, val back: MarketDest) : MarketDest
    data object Approvals : MarketDest
    data class Thread(val door: ThreadDoor, val back: MarketDest) : MarketDest
    /** `seat` comes from the door that opened the paper — the reader's own acceptance card. */
    data class Contract(val orderId: String, val back: MarketDest, val seat: String? = null) : MarketDest
    data object Ledger : MarketDest
    data object Calendar : MarketDest
    /** The carrier seat's board. */
    data object Cargo : MarketDest
    /** Rotalar — the carrier's runs per vehicle and day, off the Kargo toolbar (2026-10-03). */
    data object CargoRuns : MarketDest
}

/**
 * The menu circles (`GET badges/`, the web poll's one reading) — read each
 * time Pazar opens, never on a timer.
 */
class BadgesViewModel : ViewModel() {
    var badges by mutableStateOf<PartnerBadges?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            val (r, _) = partnerCall(Graph.language.value) { Graph.api.badges() }
            if (r != null) badges = r
        }
    }

    fun page(tag: String): Int? = badges?.pages?.get(tag)
}

/** `onAskJowi`: Jowi's line on the desk opens the Jowi tab (MainScreen switches tabs). */
@Composable
fun MarketRoot(onAskJowi: () -> Unit = {}) {
    var dest by remember { mutableStateOf<MarketDest>(MarketDest.Home) }
    val listsVm: ListsViewModel = viewModel()
    val quotesVm: QuotesViewModel = viewModel()
    val catalogVm: CatalogViewModel = viewModel()
    val ordersVm: OrdersViewModel = viewModel()
    val approvalsVm: ApprovalsViewModel = viewModel()
    val badgesVm: BadgesViewModel = viewModel()
    val ledgerVm: LedgerViewModel = viewModel()
    val calendarVm: CalendarViewModel = viewModel()
    val cargoVm: CargoViewModel = viewModel()
    val deskVm: PanelDeskViewModel = viewModel()
    BackHandler(enabled = dest != MarketDest.Home) {
        dest = when (val d = dest) {
            is MarketDest.ListDetail -> d.back
            is MarketDest.Compose -> MarketDest.Quotes
            is MarketDest.Family -> MarketDest.Catalog
            is MarketDest.Thread -> d.back
            is MarketDest.Contract -> d.back
            is MarketDest.Stage -> d.back
            MarketDest.CargoRuns -> MarketDest.Cargo
            else -> MarketDest.Home
        }
    }
    when (val d = dest) {
        MarketDest.Home -> MarketHome(listsVm, quotesVm, catalogVm, ordersVm, approvalsVm, badgesVm, cargoVm, deskVm, onAskJowi) { dest = it }
        MarketDest.Ledger -> LedgerScreen(ledgerVm, onBack = { dest = MarketDest.Home })
        MarketDest.Calendar -> CalendarScreen(calendarVm, onBack = { dest = MarketDest.Home })
        MarketDest.Cargo -> CarrierScreen(cargoVm, onBack = { dest = MarketDest.Home }, onRuns = { dest = MarketDest.CargoRuns })
        MarketDest.CargoRuns -> CarrierRunsScreen(onBack = { dest = MarketDest.Cargo })
        MarketDest.Orders -> OrdersScreen(
            ordersVm,
            onBack = { dest = MarketDest.Home },
            onThread = { o -> o.id?.let { dest = MarketDest.Thread(ThreadDoor.Order(it), MarketDest.Orders) } },
            onContract = { o -> o.id?.let { dest = MarketDest.Contract(it, MarketDest.Orders, o.role) } },
            onStage = { o -> o.id?.let { dest = MarketDest.Stage(it, MarketDest.Orders) } },
            onList = { dest = MarketDest.ListDetail(it, back = MarketDest.Orders) },
        )
        is MarketDest.Stage -> OrderStageScreen(
            d.orderId,
            ordersVm,
            onBack = { dest = d.back },
            // the stage's yazışma / contract doors come back to the stage
            onThread = { o -> o.id?.let { dest = MarketDest.Thread(ThreadDoor.Order(it), d) } },
            onContract = { o -> o.id?.let { dest = MarketDest.Contract(it, d, o.role) } },
        )
        MarketDest.Approvals -> ApprovalsScreen(
            approvalsVm,
            onBack = { dest = MarketDest.Home },
            onList = { dest = MarketDest.ListDetail(it) },
        )
        is MarketDest.Thread -> ThreadScreen(
            d.door,
            onBack = { dest = d.back },
            onContract = { id, seat -> dest = MarketDest.Contract(id, MarketDest.Thread(d.door, d.back), seat) },
        )
        is MarketDest.Contract -> OrderContractScreen(d.orderId, d.seat, onBack = { dest = d.back })
        MarketDest.Catalog -> CatalogScreen(catalogVm, onBack = { dest = MarketDest.Home }, onOpenFamily = { dest = MarketDest.Family(it) })
        is MarketDest.Family -> FamilyScreen(d.id, onBack = { dest = MarketDest.Catalog; catalogVm.load() })
        MarketDest.Lists -> ConsoleSub(stringResource(R.string.lists_title), { dest = MarketDest.Home }, trailing = { ListsMenu(listsVm) }) {
            ListsScreen(listsVm) { dest = MarketDest.ListDetail(it) }
        }
        is MarketDest.ListDetail -> ListDetailScreen(
            d.id,
            // the lists row's `canWithdraw` (the detail's `list` lacks it) until the detail reads its own
            canWithdraw = listsVm.lists?.firstOrNull { it.id == d.id }?.canWithdraw == true,
            onBack = { dest = d.back; listsVm.load() },
            onThread = { invId -> dest = MarketDest.Thread(ThreadDoor.Invitation(invId), d) },
        )
        MarketDest.Quotes -> ConsoleSub(stringResource(R.string.quotes_title), { dest = MarketDest.Home }) {
            QuotesScreen(
                quotesVm,
                onCompose = { dest = MarketDest.Compose(it) },
                onThread = { invId -> dest = MarketDest.Thread(ThreadDoor.Invitation(invId), MarketDest.Quotes) },
            )
        }
        is MarketDest.Compose -> QuoteComposerScreen(quotesVm, d.invitationId) { dest = MarketDest.Quotes }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketHome(
    listsVm: ListsViewModel,
    quotesVm: QuotesViewModel,
    catalogVm: CatalogViewModel,
    ordersVm: OrdersViewModel,
    approvalsVm: ApprovalsViewModel,
    badgesVm: BadgesViewModel,
    cargoVm: CargoViewModel,
    deskVm: PanelDeskViewModel,
    onAskJowi: () -> Unit,
    onOpen: (MarketDest) -> Unit,
) {
    // a tab root: no seller screen is open (2026-09-17)
    LaunchedEffect(Unit) { ScreenContext.clear() }
    val colors = WorkisTheme.colors
    val seat by Graph.partner.seat.collectAsState()
    val c = (seat as? PartnerSeatState.Seat)?.company
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // the whole desk, read on appear, on pull and when a watched scope moved — no timer
    fun load(company: PartnerCompany, onHistory: () -> Unit = {}) {
        // the menu circles, one reading per visit (`feed.badges`)
        badgesVm.load()
        if (company.isCarrier) cargoVm.load()
        if (company.isBuyer) listsVm.load()
        if (company.isSeller) { quotesVm.load(); catalogVm.load() }
        // the order loop is every seat's — `orders/` also carries the buyer's `quoting[]`
        ordersVm.load(); approvalsVm.load()
        // Jowi's line + the empty day's last move: the Jowi thread itself
        scope.launch { deskVm.loadHistory(); onHistory() }
    }
    LaunchedEffect(c?.isBuyer, c?.isSeller, c?.isCarrier) { if (c != null) load(c) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (c == null) { scope.launch { Graph.partner.refresh() }; return@PullToRefreshBox }
            refreshing = true
            // the history is the slowest door of the page: the spinner rides on it
            load(c) { refreshing = false }
        },
        modifier = Modifier.fillMaxSize().background(colors.canvas),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // the first page's title: the lone kiremit slash (2026-10-05)
            PanelSlash()
            if (c == null) {
                // verdict pending (company/ not yet answered): the loading idiom, not an empty door
                if (seat is PartnerSeatState.Unknown) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 28, breathing = true) }
                }
                return@Column
            }
            // the other party moved something (changes/ stamps, 20 s while resumed, or a push)
            ChangesWatch("orders") { load(c) }
            ChangesWatch("quotes") { load(c) }
            ChangesWatch("lists") { load(c) }
            if (c.isCarrier) ChangesWatch("cargo") { load(c) }

            val firm = c.company
            IdentityRow(
                name = firm?.name ?: c.shortLabel,
                markName = firm?.shortName ?: firm?.name ?: c.shortLabel,
                logoUrl = firm?.logoUrl,
                seats = buildList {
                    if (c.isBuyer) add(stringResource(R.string.seat_buyer))
                    if (c.isSeller) add(stringResource(R.string.seat_seller))
                    if (c.isCarrier) add(stringResource(R.string.seat_carrier))
                },
            )

            val lists = listsVm.lists
            val orders = ordersVm.orders
            val openInv = quotesVm.invitations?.count { it.isOpen }
            val nApprovals = approvalsVm.data?.counts?.total ?: 0
            val rows = mineRows(orders, ordersVm.quoting, quotesVm.invitations, c.isBuyer, c.isSeller)
            val footer = buildList {
                add(DeskCount(nApprovals, stringResource(R.string.panel_approvals_word)))
                add(DeskCount(orders?.size ?: 0, stringResource(R.string.orders_unit)))
                if (c.isBuyer) add(DeskCount(lists?.size ?: 0, stringResource(R.string.lists_unit)))
                if (c.isSeller) {
                    add(DeskCount(openInv ?: 0, stringResource(R.string.panel_quote_requests_word)))
                    add(DeskCount(catalogVm.page?.counts?.proposed ?: 0, stringResource(R.string.panel_in_review_word)))
                }
                if (c.isCarrier) add(DeskCount(cargoVm.board?.jobs?.size ?: 0, stringResource(R.string.cargo_title).lowercase()))
            }
            MineCard(rows, badgesVm.badges?.invUnread, footer, deskVm.lastMove) { row ->
                when (row) {
                    is MineRow.Order -> row.o.id?.let { onOpen(MarketDest.Stage(it, MarketDest.Home)) }
                    is MineRow.Quoting -> row.q.listId?.let { onOpen(MarketDest.ListDetail(it, back = MarketDest.Home)) }
                    is MineRow.Invite -> onOpen(MarketDest.Quotes)
                }
            }

            // the server's badge when it came; the cards' own counts until then
            val unread = badgesVm.badges?.ordersUnread ?: ordersVm.unreadTotal
            val unreadWord = stringResource(R.string.order_unread)
            val doors = buildList {
                // Onaylar leads — and only when something waits there
                if (nApprovals > 0) add(DeskDoor(stringResource(R.string.approvals_title), WorkisIcons.Checklist, nApprovals, null, MarketDest.Approvals))
                if (c.isSeller) add(DeskDoor(stringResource(R.string.quotes_title), WorkisIcons.Sparkle, openInv, newLine(badgesVm.page("quotes")), MarketDest.Quotes))
                if (c.isCarrier) add(DeskDoor(stringResource(R.string.cargo_title), WorkisIcons.Truck, cargoVm.board?.jobs?.size, newLine(badgesVm.page("kargo")), MarketDest.Cargo))
                add(DeskDoor(stringResource(R.string.orders_screen_title), WorkisIcons.DocText, orders?.size, unread.takeIf { it > 0 }?.let { "$it $unreadWord" }, MarketDest.Orders))
                if (c.isBuyer) add(DeskDoor(stringResource(R.string.panel_lists_door), WorkisIcons.Tray, lists?.size, newLine(badgesVm.page("lists")), MarketDest.Lists))
                if (c.isSeller) add(DeskDoor(stringResource(R.string.catalog_title), WorkisIcons.ShippingBox, catalogVm.page?.counts?.proposed, null, MarketDest.Catalog))
                // every partner seat: the money it owes / is owed, and its commitments by day
                add(DeskDoor(stringResource(R.string.cari_title), WorkisIcons.CreditCard, null, null, MarketDest.Ledger))
                add(DeskDoor(stringResource(R.string.calendar_title), WorkisIcons.Calendar, null, null, MarketDest.Calendar))
            }
            DoorRow(doors, onOpen)

            JowiLineCard(deskVm.jowiRow, onAskJowi)

            if (!c.isBuyer && !c.isSeller && !c.isCarrier) {
                Text(stringResource(R.string.market_empty), fontSize = 14.sp, color = colors.muted)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** "3 yeni" — a page's unread circle in words (a count is never colour alone). */
@Composable
private fun newLine(n: Int?): String? =
    n?.takeIf { it > 0 }?.let { "$it " + stringResource(R.string.badge_new_word) }
