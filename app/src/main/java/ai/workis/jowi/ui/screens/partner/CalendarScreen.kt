package ai.workis.jowi.ui.screens.partner

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import ai.workis.jowi.ui.theme.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.CalendarBoard
import ai.workis.jowi.data.CalendarEvent
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Takvim — the web /workis/panel/takvim/ twin (`GET calendar/`, 2026-09-30):
 * every event anchors on a platform object (deliveries, quote validity ends,
 * invoice due days, warranty ends, order reminders). Read-only; grouped by
 * local day. The server's colour may tint a small dot, but the kind is always
 * a word.
 */
class CalendarViewModel : ViewModel() {
    var board by mutableStateOf<CalendarBoard?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            val (r, err) = partnerCall(Graph.language.value) { Graph.api.calendar() }
            refreshing = false
            if (r != null) { board = r; loadError = null } else if (board == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }
}

/** The instant of a datetime `start`, read in the device's zone; null for a bare date. */
private fun moment(s: String?): java.time.ZonedDateTime? {
    if (s == null || s.length <= 10) return null
    return runCatching { OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
}

/** The local day of an event: a datetime in the device's zone, a bare date as the server wrote it. */
private fun dayOf(s: String): LocalDate? =
    moment(s)?.toLocalDate() ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, onBack: () -> Unit) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    val lang by Graph.language.collectAsState()
    ConsoleSub(stringResource(R.string.calendar_title), onBack) {
        val board = vm.board
        when {
            board == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            board == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> {
                // days ascending; inside a day, a bare date first, then by time
                val days = board.events.orEmpty()
                    .mapNotNull { e -> e.start?.let(::dayOf)?.let { it to e } }
                    .groupBy({ it.first }, { it.second })
                    .toSortedMap()
                    .map { (d, evs) -> d to evs.sortedBy { moment(it.start)?.toInstant() ?: java.time.Instant.MIN } }
                val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(if (lang == "en") Locale.US else Locale.forLanguageTag("tr-TR"))
                PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (days.isEmpty()) item {
                            Text(stringResource(R.string.calendar_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(4.dp))
                        }
                        items(days, key = { it.first.toString() }) { (day, events) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(day.format(dayFmt), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                                SurfaceCard {
                                    events.forEachIndexed { i, e ->
                                        if (i > 0) HorizontalDivider(color = colors.border)
                                        EventRow(e)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(30.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEvent) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 6.dp).size(8.dp)
                .background(hexColor(e.color) ?: colors.faint, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.title.orEmpty(), fontSize = 14.sp, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val overdue = if (e.overdue == true) stringResource(R.string.cal_overdue) else null
            Text(
                listOfNotNull(kindWord(e.kind), e.status, overdue).filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 12.sp, color = colors.muted,
            )
        }
        // "HH:mm" (local) when the server sent a datetime, nothing for a bare date
        moment(e.start)?.let { m ->
            Spacer(Modifier.width(6.dp))
            Text(m.format(DateTimeFormatter.ofPattern("HH:mm")), fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.muted)
        }
    }
}

@Composable
private fun kindWord(kind: String?): String? = when (kind) {
    "delivery" -> stringResource(R.string.cal_kind_delivery)
    "quote" -> stringResource(R.string.cal_kind_quote)
    "invoice" -> stringResource(R.string.cal_kind_invoice)
    "warranty" -> stringResource(R.string.cal_kind_warranty)
    "reminder" -> stringResource(R.string.cal_kind_reminder)
    else -> kind
}

/** "#D95F2E" → Color; null for anything else. */
private fun hexColor(hex: String?): Color? {
    val s = hex?.trim()?.takeIf { it.startsWith("#") && it.length == 7 } ?: return null
    val v = s.drop(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}
