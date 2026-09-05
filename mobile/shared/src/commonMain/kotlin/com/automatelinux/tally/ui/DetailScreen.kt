package com.automatelinux.tally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.LocalUndoable
import com.automatelinux.tally.Navigator
import com.automatelinux.tally.Route
import com.automatelinux.tally.data.Direction
import com.automatelinux.tally.data.Entry
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.data.categoryLabel
import com.automatelinux.tally.data.dayKey
import com.automatelinux.tally.data.formatDayHeader
import com.automatelinux.tally.data.formatMoney
import com.automatelinux.tally.data.formatSigned
import com.automatelinux.tally.data.formatTime
import com.automatelinux.tally.ui.components.ConfirmDialog
import com.automatelinux.tally.ui.components.RoundIconButton
import com.automatelinux.tally.ui.components.ScreenHeader
import com.automatelinux.tally.ui.components.SectionLabel
import com.automatelinux.tally.ui.components.SplitBar
import com.automatelinux.tally.ui.components.SwipeToDelete
import com.automatelinux.tally.ui.components.TallyMarkArt
import com.automatelinux.tally.ui.components.categoryIcon
import com.automatelinux.tally.ui.theme.Num
import com.automatelinux.tally.ui.theme.T

@Composable
fun DetailScreen(store: TallyStore, nav: Navigator, tallyId: String) {
    val tally = store.tally(tallyId)
    if (tally == null) {
        // The tally was deleted under us (undo of a create, say) — go back rather than crash.
        nav.popToHome()
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val undoable = LocalUndoable.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tally.name,
            subtitle = when (tally.entries.size) {
                0 -> "No entries yet"
                1 -> "1 entry"
                else -> "${tally.entries.size} entries"
            },
            onBack = { nav.back() },
        ) {
            Box {
                RoundIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true })
                TallyMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onEdit = { menuOpen = false; nav.go(Route.EditTally(tally.id)) },
                    onReset = { menuOpen = false; confirmReset = true },
                    onDelete = { menuOpen = false; confirmDelete = true },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item {
                Balance(tally.net, tally.totalIn, tally.totalOut, tally.currency)
                Spacer(Modifier.height(22.dp))
            }

            if (tally.entries.isEmpty()) {
                item { EmptyEntries() }
            } else {
                val byDay = tally.entries.sortedByDescending { it.at }.groupBy { dayKey(it.at) }
                byDay.forEach { (_, dayEntries) ->
                    item(key = "h" + dayEntries.first().id) {
                        DayHeader(
                            label = formatDayHeader(dayEntries.first().at),
                            net = dayEntries.sumOf { if (it.direction == Direction.IN) it.amount else -it.amount },
                            currency = tally.currency,
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        Box(Modifier.padding(bottom = 8.dp)) {
                            SwipeToDelete(onDelete = {
                                store.deleteEntry(tally.id, entry.id)
                                undoable.offerUndo("Entry deleted")
                            }) {
                                EntryRow(entry, tally.currency) {
                                    nav.go(Route.Amount(tally.id, entry.direction, entry.id))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(T.bg)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Out sits on the left, and the toggle on the amount screen matches it — press
            // the left button and the left segment is the one that lights up.
            FlowButton(
                label = "Money out",
                icon = Icons.Rounded.SouthWest,
                color = T.expense,
                container = T.expenseSoft,
                modifier = Modifier.weight(1f),
            ) { nav.go(Route.Amount(tally.id, Direction.OUT)) }
            FlowButton(
                label = "Money in",
                icon = Icons.Rounded.NorthEast,
                color = T.income,
                container = T.incomeSoft,
                modifier = Modifier.weight(1f),
            ) { nav.go(Route.Amount(tally.id, Direction.IN)) }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset ${tally.name}?",
            body = "Clears all ${tally.entries.size} entries and puts the balance back to zero. The tally itself stays.",
            confirmLabel = "Reset",
            onConfirm = { store.resetTally(tally.id); confirmReset = false; undoable.offerUndo("${tally.name} reset") },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${tally.name}?",
            body = "Removes the tally and its ${tally.entries.size} entries.",
            confirmLabel = "Delete",
            onConfirm = {
                store.deleteTally(tally.id); confirmDelete = false
                nav.popToHome()
                undoable.offerUndo("${tally.name} deleted")
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The number the screen exists for, then the two numbers it is made of. */
@Composable
private fun Balance(net: Long, income: Long, expense: Long, currency: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        SectionLabel(
            when {
                net < 0 -> "Down by"
                net > 0 -> "Left over"
                else -> "Balance"
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            formatSigned(net, currency),
            style = Num.hero,
            color = when {
                net > 0 -> T.income
                net < 0 -> T.expense
                else -> T.textDim
            },
        )
        Spacer(Modifier.height(18.dp))
        SplitBar(income, expense)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Totals("Came in", formatMoney(income, currency), T.income, Modifier.weight(1f))
            Totals("Went out", formatMoney(expense, currency), T.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Totals(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = T.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = T.textDim)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = Num.medium, color = T.text)
        }
    }
}

@Composable
private fun DayHeader(label: String, net: Long, currency: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label, Modifier.weight(1f))
        Text(
            formatSigned(net, currency),
            style = MaterialTheme.typography.labelMedium,
            color = if (net < 0) T.expense else T.income,
        )
    }
}

@Composable
private fun EntryRow(entry: Entry, currency: String, onClick: () -> Unit) {
    val income = entry.direction == Direction.IN
    val color = if (income) T.income else T.expense
    Surface(onClick = onClick, color = T.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (income) T.incomeSoft else T.expenseSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(entry.category), null, Modifier.size(19.dp), tint = color)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.note.ifBlank { categoryLabel(entry.category) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = T.text,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (entry.note.isBlank()) formatTime(entry.at)
                    else categoryLabel(entry.category) + " · " + formatTime(entry.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = T.textFaint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (if (income) "+" else "−") + formatMoney(entry.amount, currency),
                style = Num.medium,
                color = color,
            )
        }
    }
}

@Composable
private fun FlowButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    container: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = container, modifier = modifier.height(58.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun EmptyEntries() {
    Column(
        Modifier.fillMaxWidth().padding(top = 54.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TallyMarkArt(T.textFaint)
        Spacer(Modifier.height(24.dp))
        Text("Nothing on this tally yet", style = MaterialTheme.typography.titleMedium, color = T.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add what you earned or what you paid — the balance builds itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = T.textDim,
            textAlign = TextAlign.Center,
        )
    }
}
