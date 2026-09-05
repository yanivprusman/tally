package com.automatelinux.tally.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.ui.theme.Num
import com.automatelinux.tally.ui.theme.T
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The same bar on every screen: back, title, and whatever the screen needs on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    large: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (large) androidx.compose.material3.MaterialTheme.typography.headlineMedium
                        else androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = T.text,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = T.textDim, maxLines = 1)
            }
        }
        trailing()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = tint ?: T.textDim,
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(22.dp))
        }
    }
}

/** In versus out, as one bar. Reads faster than two numbers ever will. */
@Composable
fun SplitBar(income: Long, expense: Long, modifier: Modifier = Modifier) {
    val total = (income + expense).coerceAtLeast(1L)
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(T.surfaceAlt),
    ) {
        if (income > 0) Box(Modifier.weight(income.toFloat() / total).fillMaxHeight().background(T.income))
        if (income > 0 && expense > 0) Spacer(Modifier.width(2.dp))
        if (expense > 0) Box(Modifier.weight(expense.toFloat() / total).fillMaxHeight().background(T.expense))
    }
}

fun categoryIcon(id: String): ImageVector = when (id) {
    "work" -> Icons.Rounded.Work
    "sale" -> Icons.Rounded.Sell
    "refund" -> Icons.AutoMirrored.Rounded.Undo
    "gift" -> Icons.Rounded.CardGiftcard
    "food" -> Icons.Rounded.Restaurant
    "stay" -> Icons.Rounded.Weekend
    "travel" -> Icons.Rounded.DirectionsBus
    "shop" -> Icons.Rounded.ShoppingBag
    "fun" -> Icons.Rounded.Celebration
    "bills" -> Icons.AutoMirrored.Rounded.ReceiptLong
    else -> Icons.Rounded.MoreHoriz
}

/**
 * Drag a row left to delete it. Hand-rolled rather than SwipeToDismissBox so the reveal,
 * the threshold and the fling-back all behave the same on every platform.
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // The reveal only paints once the row has actually started to move.
        if (offset.value < -1f) {
            Box(
                Modifier.matchParentSize().background(T.expenseSoft).padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    "Delete",
                    Modifier.size((18 + 8 * (abs(offset.value) / width).coerceIn(0f, 1f)).dp),
                    tint = T.expense,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width, 0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (abs(offset.value) > width * 0.35f) {
                                    offset.animateTo(-width, tween(160))
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, tween(220))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(220)) } },
                    )
                },
        ) { content() }
    }
}

/**
 * The amount pad. A real keypad rather than the system keyboard: bigger targets, no
 * layout jump when it appears, and the Add button always sits under the thumb.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch -> Key(Modifier.weight(1f), ch.toString()) { onDigit(ch) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(Modifier.weight(1f), ".") { onDigit('.') }
            Key(Modifier.weight(1f), "0") { onDigit('0') }
            Surface(
                color = T.surfaceAlt,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackspace,
                        onLongClick = onClear,
                    ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, "Backspace", Modifier.size(24.dp), tint = T.textDim)
                }
            }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = T.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.height(56.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = Num.keypad, color = T.text, textAlign = TextAlign.Center)
        }
    }
}

/** Small caps label used above groups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = T.textFaint,
        modifier = modifier,
    )
}

/** The chalk-marks motif from the launcher icon, reused wherever a screen is empty. */
@Composable
fun TallyMarkArt(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 96.dp, height = 72.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Box(Modifier.width(7.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.35f)))
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(7.dp)
                .rotate(-13f)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

/** One dialog for every irreversible act, so reset and delete always read the same way. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T.surface,
        titleContentColor = T.text,
        textContentColor = T.textDim,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) T.expense else T.brand, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = T.textDim, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** A menu row that matches the app rather than the platform default. */
@Composable
fun MenuRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, color = tint, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = tint) },
        onClick = onClick,
    )
}
