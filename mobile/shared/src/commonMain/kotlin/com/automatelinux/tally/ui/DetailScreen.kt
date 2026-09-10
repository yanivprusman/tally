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
import com.automatelinux.tally.data.Tally
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.data.categoryLabel
import com.automatelinux.tally.data.dayKey
import com.automatelinux.tally.data.formatDayHeader
import com.automatelinux.tally.data.formatMoney
import com.automatelinux.tally.data.formatSigned
import com.automatelinux.tally.data.formatTime
import com.automatelinux.tally.data.shownAmount
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
                    // Pushed, not replaced: back returns to the original, so the two
                    // balances can be read one after the other.
                    onDuplicate = {
                        menuOpen = false
                        store.duplicateTally(tally.id)?.let { nav.go(Route.Detail(it)) }
                    },
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
                Balance(tally) { store.setExVat(tally.id, it) }
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
                            net = dayEntries.sumOf {
                                val shown = it.shownAmount(tally.exVat)
                                if (it.direction == Direction.IN) shown else -shown
                            },
                            currency = tally.currency,
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        Box(Modifier.padding(bottom = 8.dp)) {
                            SwipeToDelete(onDelete = {
                                store.deleteEntry(tally.id, entry.id)
                                undoable.offerUndo("Entry deleted")
                            }) {
                                EntryRow(entry, tally.currency, tally.exVat) {
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

/**
 * The number the screen exists for, then the two numbers it is made of.
 *
 * Every figure here is the tally's own totals, which already answer to its VAT view —
 * so the switch beside the label moves the balance, the bar, the two cards, the day
 * headings and every row at once. There is no second number hiding behind this one.
 */
@Composable
private fun Balance(tally: Tally, onExVat: (Boolean) -> Unit) {
    val net = tally.net
    val income = tally.totalIn
    val expense = tally.totalOut
    val currency = tally.currency

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                when {
                    net < 0 -> "Down by"
                    net > 0 -> "Left over"
                    else -> "Balance"
                },
                Modifier.weight(1f),
            )
            // Nothing to switch between until something carries VAT, and a switch that
            // changes nothing teaches the wrong thing about the numbers.
            if (tally.hasVatEntries) VatViewSwitch(tally.exVat, onExVat)
        }
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
            Totals(
                "Came in", formatMoney(income, currency), T.income,
                vatCaption(tally.vatIn, tally.exVat, currency), Modifier.weight(1f),
            )
            Totals(
                "Went out", formatMoney(expense, currency), T.expense,
                vatCaption(tally.vatOut, tally.exVat, currency), Modifier.weight(1f),
            )
        }
    }
}

/** The VAT this side carries, said from the point of view of what is on screen: in the
 *  ex-VAT reading it is what was taken off, in the written one it is what is inside. */
private fun vatCaption(vat: Long, exVat: Boolean, currency: String): String? = when {
    vat <= 0L -> null
    exVat -> "+ " + formatMoney(vat, currency) + " VAT"
    else -> formatMoney(vat, currency) + " VAT inside"
}

/** Two words, both always visible, so the reading is stated rather than remembered. */
@Composable
private fun VatViewSwitch(exVat: Boolean, onExVat: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = T.surface) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            VatViewSegment("Incl. VAT", !exVat) { onExVat(false) }
            VatViewSegment("Ex. VAT", exVat) { onExVat(true) }
        }
    }
}

@Composable
private fun VatViewSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) T.brandSoft else Color.Transparent,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) T.brand else T.textFaint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Totals(label: String, value: String, color: Color, caption: String?, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = T.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = T.textDim)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = Num.medium, color = T.text)
            if (caption != null) {
                Spacer(Modifier.height(3.dp))
                Text(caption, style = MaterialTheme.typography.labelSmall, color = T.textFaint, maxLines = 1)
            }
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
private fun EntryRow(entry: Entry, currency: String, exVat: Boolean, onClick: () -> Unit) {
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
                    buildString {
                        if (entry.note.isNotBlank()) append(categoryLabel(entry.category)).append(" · ")
                        append(formatTime(entry.at))
                        // Which rows carry VAT is worth knowing in both readings — in one
                        // to see what is inside the number, in the other to see why this
                        // row moved and its neighbour did not.
                        if (entry.vatIncluded) append(if (exVat) " · ex. VAT" else " · incl. VAT")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = T.textFaint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (if (income) "+" else "−") + formatMoney(entry.shownAmount(exVat), currency),
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
