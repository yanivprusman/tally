package com.automatelinux.tally.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.Navigator
import com.automatelinux.tally.data.Direction
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.data.categoriesFor
import com.automatelinux.tally.data.formatAmount
import com.automatelinux.tally.ui.components.Keypad
import com.automatelinux.tally.ui.components.ScreenHeader
import com.automatelinux.tally.ui.components.SectionLabel
import com.automatelinux.tally.ui.components.categoryIcon
import com.automatelinux.tally.ui.theme.Num
import com.automatelinux.tally.ui.theme.T

/**
 * One screen for both adding and editing. The amount is typed on the app's own keypad,
 * which is why this is a screen and not a sheet: the pad, the note and the confirm button
 * all need to be reachable at once, and the system keyboard is only borrowed for the note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountScreen(
    store: TallyStore,
    nav: Navigator,
    tallyId: String,
    initialDirection: Direction,
    entryId: String?,
) {
    val tally = store.tally(tallyId)
    if (tally == null) {
        nav.popToHome()
        return
    }
    val existing = entryId?.let { id -> tally.entries.firstOrNull { it.id == id } }

    var direction by remember { mutableStateOf(existing?.direction ?: initialDirection) }
    var raw by remember { mutableStateOf(existing?.let { minorToRaw(it.amount) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: defaultCategory(existing?.direction ?: initialDirection)) }
    var noteFocused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val accent = if (direction == Direction.IN) T.income else T.expense
    val accentSoft = if (direction == Direction.IN) T.incomeSoft else T.expenseSoft
    val amount = rawToMinor(raw)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = tally.name,
            subtitle = if (existing == null) "New entry" else "Edit entry",
            onBack = { nav.back() },
        )

        Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
            DirectionToggle(direction) { picked ->
                direction = picked
                if (categoriesFor(picked).none { it.id == category }) category = defaultCategory(picked)
            }

            Spacer(Modifier.height(26.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    tally.currency,
                    style = Num.large,
                    color = if (raw.isEmpty()) T.textFaint else accent,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (raw.isEmpty()) "0" else displayRaw(raw),
                    style = Num.hero,
                    color = if (raw.isEmpty()) T.textFaint else accent,
                )
            }
            Spacer(Modifier.height(24.dp))

            NoteField(
                value = note,
                onValueChange = { note = it },
                placeholder = if (direction == Direction.IN) "What for? e.g. day at the vineyard" else "What for? e.g. dinner",
                onFocusChanged = { noteFocused = it },
                onDone = { focus.clearFocus() },
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("Category")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categoriesFor(direction).forEach { c ->
                    CategoryChip(
                        label = c.label,
                        icon = categoryIcon(c.id),
                        selected = c.id == category,
                        accent = accent,
                        accentSoft = accentSoft,
                    ) { category = c.id }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // The pad steps aside while the system keyboard owns the bottom of the screen.
        AnimatedVisibility(visible = !noteFocused) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Keypad(
                    onDigit = { raw = appendDigit(raw, it) },
                    onBackspace = { raw = raw.dropLast(1) },
                    onClear = { raw = "" },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Surface(
            onClick = {
                if (amount > 0) {
                    if (existing == null) store.addEntry(tally.id, direction, amount, note, category)
                    else store.updateEntry(tally.id, existing.id, direction, amount, note, category)
                    nav.back()
                }
            },
            enabled = amount > 0,
            shape = RoundedCornerShape(20.dp),
            color = if (amount > 0) accent else T.surfaceAlt,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(58.dp),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    Modifier.size(20.dp),
                    tint = if (amount > 0) onAccent() else T.textFaint,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        amount <= 0 -> "Enter an amount"
                        existing != null -> "Save " + tally.currency + formatAmount(amount)
                        direction == Direction.IN -> "Add " + tally.currency + formatAmount(amount) + " in"
                        else -> "Add " + tally.currency + formatAmount(amount) + " out"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (amount > 0) onAccent() else T.textFaint,
                )
            }
        }
    }
}

/**
 * The accents invert between themes — pastel on slate, saturated on paper — so the text
 * that sits on top of them has to invert with them.
 */
@Composable
private fun onAccent(): Color = if (T.isDark) Color(0xFF0B0F14) else Color.White

@Composable
private fun DirectionToggle(selected: Direction, onSelect: (Direction) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = T.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Segment(
                label = "Money in",
                icon = Icons.Rounded.NorthEast,
                selected = selected == Direction.IN,
                color = T.income,
                container = T.incomeSoft,
                modifier = Modifier.weight(1f),
            ) { onSelect(Direction.IN) }
            Segment(
                label = "Money out",
                icon = Icons.Rounded.SouthWest,
                selected = selected == Direction.OUT,
                color = T.expense,
                container = T.expenseSoft,
                modifier = Modifier.weight(1f),
            ) { onSelect(Direction.OUT) }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    color: Color,
    container: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) container else Color.Transparent,
        modifier = modifier.height(46.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (selected) color else T.textFaint)
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) color else T.textDim)
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFocusChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = T.surface, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = T.textFaint, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = T.text),
                cursorBrush = SolidColor(T.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    accentSoft: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accentSoft else T.surface,
        modifier = Modifier.height(42.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (selected) accent else T.textDim)
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) accent else T.textDim)
        }
    }
}

// ---- amount typing ------------------------------------------------------------------

private fun defaultCategory(direction: Direction) = if (direction == Direction.IN) "work" else "food"

private fun appendDigit(raw: String, ch: Char): String {
    if (ch == '.') return if (raw.contains('.')) raw else if (raw.isEmpty()) "0." else "$raw."
    val dot = raw.indexOf('.')
    if (dot >= 0 && raw.length - dot > 2) return raw          // two decimals is all money has
    if (dot < 0 && raw.length >= 9) return raw                 // and nine digits is more than enough
    if (raw == "0") return ch.toString()
    return raw + ch
}

private fun rawToMinor(raw: String): Long {
    if (raw.isBlank()) return 0L
    val parts = raw.split('.')
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return 0L
    val cents = when {
        parts.size < 2 || parts[1].isEmpty() -> 0L
        parts[1].length == 1 -> parts[1].toLong() * 10
        else -> parts[1].take(2).toLong()
    }
    return whole * 100 + cents
}

private fun minorToRaw(minor: Long): String {
    val cents = (minor % 100).toInt()
    val whole = minor / 100
    return if (cents == 0) whole.toString() else whole.toString() + "." + (if (cents < 10) "0$cents" else "$cents")
}

/** Groups thousands while typing, so a five-figure amount stays readable mid-entry. */
private fun displayRaw(raw: String): String {
    val dot = raw.indexOf('.')
    val whole = if (dot < 0) raw else raw.substring(0, dot)
    val rest = if (dot < 0) "" else raw.substring(dot)
    val n = whole.toLongOrNull() ?: return raw
    return formatAmount(n * 100) + rest
}
