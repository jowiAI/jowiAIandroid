package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme

@Composable
fun ConsolePartnersScreen(vm: ConsoleViewModel) {
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { if (vm.partners == null) vm.loadPartners() }

    var seat by remember { mutableStateOf("partner") }
    var query by remember { mutableStateOf("") }

    val all = vm.partners?.partners.orEmpty()
    val rows = all.filter { (it.seat ?: "partner") == seat }
        .filter {
            query.isBlank() ||
                (it.name ?: "").contains(query, ignoreCase = true) ||
                (it.shortName ?: "").contains(query, ignoreCase = true)
        }

    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val seats = listOf(
                "partner" to stringResource(R.string.seat_partner),
                "virtual" to stringResource(R.string.seat_virtual),
                "expert" to stringResource(R.string.seat_expert),
            )
            seats.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = seat == value,
                    onClick = { seat = value },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = seats.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.surface,
                        activeContentColor = colors.ink,
                        inactiveContentColor = colors.muted,
                    ),
                ) { Text(label, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.partners_filter_prompt), color = colors.faint) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Kiremit500,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                cursorColor = colors.blue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(rows) { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Row {
                        Text(
                            p.shortName ?: p.name ?: "—",
                            color = colors.ink,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        // unread/open indicator is success-green: "someone wrote to you"
                        if ((p.openConversations ?: 0) > 0 || (p.openQuestions ?: 0) > 0) {
                            Text("●", color = SuccessGreen, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        listOfNotNull(p.city, p.sector, p.status).forEach {
                            Text("$it  ", color = colors.muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        p.lastActivityDays?.let {
                            Text(
                                "$it " + stringResource(R.string.days_word),
                                color = colors.faint,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    // the seats line (2026-09-13): who signs in, what moved
                    p.activity?.takeIf { !it.seats.isNullOrEmpty() }?.let { a ->
                        val seats = a.seats.orEmpty()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "👥 ${a.activeSeats ?: 0}/${seats.size} " + stringResource(R.string.active_seats_pill) + " · " +
                                seats.take(3).joinToString(", ") { (it.name ?: it.email.orEmpty()) + (if (it.active == true) " ✓" else "") } +
                                " · ${a.lists ?: 0} " + stringResource(R.string.lists_unit) + " · ${a.quotes ?: 0} " + stringResource(R.string.quotes_unit) + " · ${a.orders ?: 0} " + stringResource(R.string.orders_unit),
                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = colors.faint, maxLines = 2,
                        )
                    }
                }
            }
            item {
                vm.error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
                if (rows.isEmpty() && vm.partners != null && vm.error == null) {
                    Text(stringResource(R.string.partners_empty), color = colors.muted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
