package ai.workis.jowi.ui.screens.partner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.ui.components.WorkisMark
import androidx.compose.foundation.layout.Box
import ai.workis.jowi.data.PartnerSeatState
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

/** Pazar — the doors by seat: Listeler (buyer), Teklifler (seller). */
sealed interface MarketDest {
    data object Home : MarketDest
    data object Lists : MarketDest
    data class ListDetail(val id: String) : MarketDest
    data object Quotes : MarketDest
    data class Compose(val invitationId: String) : MarketDest
    data object Catalog : MarketDest
    data class Family(val id: String) : MarketDest
    data object Orders : MarketDest
    data object Approvals : MarketDest
    data class Thread(val door: ThreadDoor, val back: MarketDest) : MarketDest
    data class Contract(val orderId: String, val back: MarketDest) : MarketDest
}

@Composable
fun MarketRoot() {
    var dest by remember { mutableStateOf<MarketDest>(MarketDest.Home) }
    val listsVm: ListsViewModel = viewModel()
    val quotesVm: QuotesViewModel = viewModel()
    val catalogVm: CatalogViewModel = viewModel()
    val ordersVm: OrdersViewModel = viewModel()
    val approvalsVm: ApprovalsViewModel = viewModel()
    BackHandler(enabled = dest != MarketDest.Home) {
        dest = when (val d = dest) {
            is MarketDest.ListDetail -> MarketDest.Lists
            is MarketDest.Compose -> MarketDest.Quotes
            is MarketDest.Family -> MarketDest.Catalog
            is MarketDest.Thread -> d.back
            is MarketDest.Contract -> d.back
            else -> MarketDest.Home
        }
    }
    when (val d = dest) {
        MarketDest.Home -> MarketHome(listsVm, quotesVm, catalogVm, ordersVm, approvalsVm) { dest = it }
        MarketDest.Orders -> OrdersScreen(
            ordersVm,
            onBack = { dest = MarketDest.Home },
            onThread = { o -> o.id?.let { dest = MarketDest.Thread(ThreadDoor.Order(it), MarketDest.Orders) } },
            onContract = { o -> o.id?.let { dest = MarketDest.Contract(it, MarketDest.Orders) } },
        )
        MarketDest.Approvals -> ApprovalsScreen(
            approvalsVm,
            onBack = { dest = MarketDest.Home },
            onList = { dest = MarketDest.ListDetail(it) },
        )
        is MarketDest.Thread -> ThreadScreen(
            d.door,
            onBack = { dest = d.back },
            onContract = { id -> dest = MarketDest.Contract(id, MarketDest.Thread(d.door, d.back)) },
        )
        is MarketDest.Contract -> OrderContractScreen(d.orderId, onBack = { dest = d.back })
        MarketDest.Catalog -> CatalogScreen(catalogVm, onBack = { dest = MarketDest.Home }, onOpenFamily = { dest = MarketDest.Family(it) })
        is MarketDest.Family -> FamilyScreen(d.id, onBack = { dest = MarketDest.Catalog; catalogVm.load() })
        MarketDest.Lists -> ConsoleSub(stringResource(R.string.lists_title), { dest = MarketDest.Home }, trailing = { ListsMenu(listsVm) }) {
            ListsScreen(listsVm) { dest = MarketDest.ListDetail(it) }
        }
        is MarketDest.ListDetail -> ListDetailScreen(
            d.id,
            onBack = { dest = MarketDest.Lists; listsVm.load() },
            onThread = { invId -> dest = MarketDest.Thread(ThreadDoor.Invitation(invId), MarketDest.ListDetail(d.id)) },
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

@Composable
private fun MarketHome(
    listsVm: ListsViewModel,
    quotesVm: QuotesViewModel,
    catalogVm: CatalogViewModel,
    ordersVm: OrdersViewModel,
    approvalsVm: ApprovalsViewModel,
    onOpen: (MarketDest) -> Unit,
) {
    // a tab root: no seller screen is open (2026-09-17)
    LaunchedEffect(Unit) { ScreenContext.clear() }
    val colors = WorkisTheme.colors
    val seat by Graph.partner.seat.collectAsState()
    val c = (seat as? PartnerSeatState.Seat)?.company
    LaunchedEffect(c?.isBuyer, c?.isSeller) {
        if (c?.isBuyer == true) listsVm.load()
        if (c?.isSeller == true) { quotesVm.load(); catalogVm.load() }
        // the order loop is every seat's — a carrier sees only these two
        if (c != null) { ordersVm.load(); approvalsVm.load() }
    }
    Column(
        Modifier.fillMaxSize().background(colors.canvas).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.market_tab), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
        Spacer(Modifier.height(14.dp))
        if (c == null) {
            // verdict pending (company/ not yet answered): the loading idiom, not an empty door
            if (seat is PartnerSeatState.Unknown) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { WorkisMark(size = 28, breathing = true) }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.shortLabel, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.ink)
            if (c.isBuyer) StatusPill(stringResource(R.string.seat_buyer))
            if (c.isSeller) StatusPill(stringResource(R.string.seat_seller))
            if (c.isCarrier) StatusPill(stringResource(R.string.seat_carrier))
        }
        Spacer(Modifier.height(14.dp))
        if (c.isBuyer) {
            val lists = listsVm.lists
            BigTile(
                number = lists?.size,
                label = stringResource(R.string.lists_title),
                sub = lists?.let { l -> "${l.count { it.status == "quoting" }} " + stringResource(R.string.ls_quoting).lowercase() },
                icon = WorkisIcons.Tray,
            ) { onOpen(MarketDest.Lists) }
            Spacer(Modifier.height(14.dp))
        }
        if (c.isSeller) {
            // Ürünler (2026-09-15): families awaiting review, live offers under
            val cat = catalogVm.page
            BigTile(
                number = cat?.counts?.proposed,
                label = stringResource(R.string.catalog_title),
                sub = cat?.let { "${it.counts?.liveOffers ?: 0} " + stringResource(R.string.catalog_live_offers) },
                icon = WorkisIcons.ShippingBox,
            ) { onOpen(MarketDest.Catalog) }
            Spacer(Modifier.height(14.dp))
            val inv = quotesVm.invitations
            BigTile(
                number = inv?.count { it.isOpen },
                label = stringResource(R.string.quotes_title),
                sub = inv?.let { i -> "${i.size} · ${i.count { it.isOpen }} " + stringResource(R.string.open_invitations) },
                icon = WorkisIcons.Sparkle,
            ) { onOpen(MarketDest.Quotes) }
            Spacer(Modifier.height(14.dp))
        }
        // Siparişler and Onaylar sit on every seat (Django §5, §8)
        val orders = ordersVm.orders
        BigTile(
            number = orders?.size,
            label = stringResource(R.string.orders_screen_title),
            sub = orders?.let {
                val unread = ordersVm.unreadTotal
                if (unread > 0) "$unread " + stringResource(R.string.order_unread)
                else ordersVm.missingTermsOrders.takeIf { n -> n > 0 }?.let { n -> "$n " + stringResource(R.string.order_terms_missing) } ?: " "
            },
            icon = WorkisIcons.Checklist,
        ) { onOpen(MarketDest.Orders) }
        Spacer(Modifier.height(14.dp))
        BigTile(
            number = approvalsVm.data?.counts?.total,
            label = stringResource(R.string.approvals_title),
            sub = approvalsVm.data?.let { " " },
            icon = WorkisIcons.DocPlus,
        ) { onOpen(MarketDest.Approvals) }
        Spacer(Modifier.height(14.dp))
        if (!c.isBuyer && !c.isSeller) {
            Text(stringResource(R.string.market_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(top = 20.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun BigTile(number: Int?, label: String, sub: String?, icon: ImageVector, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.weight(1f))
        Text(number?.toString() ?: "–", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = colors.ink)
        Text(label, fontSize = 14.sp, color = colors.muted)
        Text(sub ?: " ", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.faint)
    }
}
