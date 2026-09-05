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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.LocalUndoable
import com.automatelinux.tally.Navigator
import com.automatelinux.tally.Route
import com.automatelinux.tally.data.Tally
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.data.formatMoney
import com.automatelinux.tally.data.formatRelative
import com.automatelinux.tally.data.formatSigned
import com.automatelinux.tally.ui.components.ConfirmDialog
import com.automatelinux.tally.ui.components.MenuRow
import com.automatelinux.tally.ui.components.RoundIconButton
import com.automatelinux.tally.ui.components.ScreenHeader
import com.automatelinux.tally.ui.components.SplitBar
import com.automatelinux.tally.ui.components.TallyMarkArt
import com.automatelinux.tally.ui.theme.Num
import com.automatelinux.tally.ui.theme.T
import com.automatelinux.tally.ui.theme.accentAt

@Composable
fun HomeScreen(store: TallyStore, nav: Navigator) {
    val tallies = store.tallies.sortedByDescending { it.lastActivity }
    var confirmReset by remember { mutableStateOf<Tally?>(null) }
    var confirmDelete by remember { mutableStateOf<Tally?>(null) }
    val undoable = LocalUndoable.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Tally",
                subtitle = when {
                    store.loading -> "Loading…"
                    else -> when (tallies.size) {
                    0 -> "Nothing running yet"
                    1 -> "1 tally running"
                    else -> "${tallies.size} tallies running"
                    }
                },
                large = true,
            )
            if (store.loading) {
                // An empty list before the first answer is not "no tallies" — saying so
                // would invite the user to create a duplicate of one they already have.
                LoadingHome()
            } else if (tallies.isEmpty()) {
                EmptyHome()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tallies, key = { it.id }) { tally ->
                        TallyCard(
                            tally = tally,
                            onOpen = { nav.go(Route.Detail(tally.id)) },
                            onEdit = { nav.go(Route.EditTally(tally.id)) },
                            onReset = { confirmReset = tally },
                            onDelete = { confirmDelete = tally },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { nav.go(Route.EditTally(null)) },
            containerColor = T.brand,
            contentColor = T.onBrand,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New tally", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    confirmReset?.let { t ->
        ConfirmDialog(
            title = "Reset ${t.name}?",
            body = "Clears all ${t.entries.size} entries and puts the balance back to zero. The tally itself stays.",
            confirmLabel = "Reset",
            onConfirm = {
                store.resetTally(t.id); confirmReset = null
                undoable.offerUndo("${t.name} reset")
            },
            onDismiss = { confirmReset = null },
        )
    }
    confirmDelete?.let { t ->
        ConfirmDialog(
            title = "Delete ${t.name}?",
            body = "Removes the tally and its ${t.entries.size} entries.",
            confirmLabel = "Delete",
            onConfirm = {
                store.deleteTally(t.id); confirmDelete = null
                undoable.offerUndo("${t.name} deleted")
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun TallyCard(
    tally: Tally,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = accentAt(tally.accent)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = T.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    tally.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = T.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    RoundIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true })
                    TallyMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onEdit = { menuOpen = false; onEdit() },
                        onReset = { menuOpen = false; onReset() },
                        onDelete = { menuOpen = false; onDelete() },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatSigned(tally.net, tally.currency),
                style = Num.large,
                color = when {
                    tally.net > 0 -> T.income
                    tally.net < 0 -> T.expense
                    else -> T.textDim
                },
                modifier = Modifier.padding(end = 10.dp),
            )
            Spacer(Modifier.height(14.dp))
            SplitBar(tally.totalIn, tally.totalOut, Modifier.padding(end = 10.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                Flow(label = "in", value = formatMoney(tally.totalIn, tally.currency), color = T.income)
                Spacer(Modifier.width(18.dp))
                Flow(label = "out", value = formatMoney(tally.totalOut, tally.currency), color = T.expense)
                Spacer(Modifier.weight(1f))
                Text(
                    if (tally.entries.isEmpty()) "empty"
                    else "${tally.entries.size} ${if (tally.entries.size == 1) "entry" else "entries"} · ${formatRelative(tally.lastActivity)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = T.textFaint,
                )
            }
        }
    }
}

@Composable
private fun Flow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = T.text)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = T.textFaint)
    }
}

@Composable
fun TallyMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = T.surfaceAlt,
        shape = RoundedCornerShape(18.dp),
    ) {
        MenuRow("Rename & style", Icons.Rounded.Tune, T.text, onEdit)
        MenuRow("Reset to zero", Icons.Rounded.Restore, T.text, onReset)
        MenuRow("Delete tally", Icons.Rounded.DeleteOutline, T.expense, onDelete)
    }
}

@Composable
private fun LoadingHome() {
    Column(
        Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TallyMarkArt(T.textFaint)
    }
}

@Composable
private fun EmptyHome() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TallyMarkArt(T.brand)
        Spacer(Modifier.height(28.dp))
        Text("Start a tally", style = MaterialTheme.typography.headlineMedium, color = T.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "One for the trip, one for the job, one for the month. They run side by side and never mix.",
            style = MaterialTheme.typography.bodyLarge,
            color = T.textDim,
            textAlign = TextAlign.Center,
        )
    }
}
