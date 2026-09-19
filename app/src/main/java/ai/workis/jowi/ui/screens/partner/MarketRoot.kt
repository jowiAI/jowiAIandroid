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
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import androidx.compose.ui.text.style.TextOverflow
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
    /** `seat` comes from the door that opened the paper — the reader's own acceptance card. */
    data class Contract(val orderId: String, val back: MarketDest, val seat: String? = null) : MarketDest
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
            onContract = { o -> o.id?.let { dest = MarketDest.Contract(it, MarketDest.Orders, o.role) } },
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
        // The console Panel's rhythm, so the whole seat fits one screen: the
        // hero on the left two rows tall (Onaylar), two capsules on the right,
        // and — for a seller — a half row underneath. Positions are fixed per
        // seat set: a box never moves because a number changed.
        val lists = listsVm.lists
        val cat = catalogVm.page
        val inv = quotesVm.invitations
        val orders = ordersVm.orders
        val unread = ordersVm.unreadTotal
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // the hero is the one emphasis on this screen — a beige note band,
            // content and not a coloured button; the number says the state
            HeroTile(
                number = approvalsVm.data?.counts?.total,
                label = stringResource(R.string.approvals_title),
                sub = unread.takeIf { it > 0 }?.let { "$it " + stringResource(R.string.order_unread) },
                modifier = Modifier.weight(1f),
            ) { onOpen(MarketDest.Approvals) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CapsuleTile(
                    number = orders?.size,
                    label = stringResource(R.string.orders_screen_title),
                    icon = WorkisIcons.Checklist,
                ) { onOpen(MarketDest.Orders) }
                if (c.isSeller) {
                    CapsuleTile(
                        number = inv?.count { it.isOpen },
                        label = stringResource(R.string.quotes_title),
                        icon = WorkisIcons.Sparkle,
                    ) { onOpen(MarketDest.Quotes) }
                } else if (c.isBuyer) {
                    CapsuleTile(
                        number = lists?.size,
                        label = stringResource(R.string.lists_title),
                        icon = WorkisIcons.Tray,
                    ) { onOpen(MarketDest.Lists) }
                }
            }
        }
        // the bottom half-row belongs to the seller seat only
        if (c.isSeller) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (c.isBuyer) {
                    HalfTile(
                        number = lists?.size,
                        label = stringResource(R.string.lists_title),
                        sub = lists?.let { l -> "${l.count { it.status == "quoting" }} " + stringResource(R.string.ls_quoting).lowercase() },
                        icon = WorkisIcons.Tray,
                        modifier = Modifier.weight(1f),
                    ) { onOpen(MarketDest.Lists) }
                }
                HalfTile(
                    number = cat?.counts?.proposed,
                    label = stringResource(R.string.catalog_title),
                    sub = cat?.let { "${it.counts?.liveOffers ?: 0} " + stringResource(R.string.catalog_live_offers) },
                    icon = WorkisIcons.ShippingBox,
                    modifier = Modifier.weight(1f),
                ) { onOpen(MarketDest.Catalog) }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!c.isBuyer && !c.isSeller) {
            Text(stringResource(R.string.market_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(top = 20.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

/** Two rows tall, beige band: the seat's one emphasis. */
@Composable
private fun HeroTile(number: Int?, label: String, sub: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(174.dp)
            .background(BeigeBg, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(WorkisIcons.DocPlus, contentDescription = null, tint = Beige, modifier = Modifier.size(20.dp))
        Spacer(Modifier.weight(1f))
        Text(number?.toString() ?: "–", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, color = Beige)
        Text(label, fontSize = 14.sp, color = Beige)
        Text(sub ?: " ", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Beige.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Half the hero's height: icon, number, name. */
@Composable
private fun CapsuleTile(number: Int?, label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.weight(1f))
            Text(number?.toString() ?: "–", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.ink)
        }
        Spacer(Modifier.weight(1f))
        Text(label, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The seller's bottom row. */
@Composable
private fun HalfTile(number: Int?, label: String, sub: String?, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        modifier
            .height(120.dp)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Text(number?.toString() ?: "–", fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = colors.ink)
        Text(label, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub ?: " ", fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
