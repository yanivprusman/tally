package com.automatelinux.tally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.Navigator
import com.automatelinux.tally.Route
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.ui.components.ScreenHeader
import com.automatelinux.tally.ui.components.SectionLabel
import com.automatelinux.tally.ui.theme.AccentPalette
import com.automatelinux.tally.ui.theme.Num
import com.automatelinux.tally.ui.theme.T
import com.automatelinux.tally.ui.theme.accentAt

private val Currencies = listOf("₪", "$", "€", "£", "₺", "¥")
private val NameSuggestions = listOf("Trip to the centre", "This week", "Work day", "September")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTallyScreen(store: TallyStore, nav: Navigator, tallyId: String?) {
    val existing = store.tally(tallyId)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "₪") }
    var accent by remember { mutableStateOf(existing?.accent ?: (store.tallies.size % AccentPalette.size)) }
    val focusRequester = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { if (existing == null) focusRequester.requestFocus() }

    val canSave = name.isNotBlank()

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = if (existing == null) "New tally" else "Edit tally",
            subtitle = if (existing == null) "Give it a name you'll recognise later" else existing.name,
            onBack = { nav.back() },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Surface(shape = RoundedCornerShape(18.dp), color = T.surface, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    if (name.isEmpty()) {
                        Text("Name this tally", style = Num.medium, color = T.textFaint)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        singleLine = true,
                        textStyle = Num.medium.copy(color = T.text),
                        cursorBrush = SolidColor(accentAt(accent)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
            }

            if (existing == null && name.isBlank()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameSuggestions.forEach { s ->
                        Surface(
                            onClick = { name = s; focus.clearFocus() },
                            shape = RoundedCornerShape(13.dp),
                            color = T.surfaceAlt,
                        ) {
                            Text(
                                s,
                                style = MaterialTheme.typography.labelLarge,
                                color = T.textDim,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Currency")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currencies.forEach { c ->
                    val selected = c == currency
                    Surface(
                        onClick = { currency = c },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) T.brandSoft else T.surface,
                        modifier = Modifier.size(50.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(c, style = Num.medium, color = if (selected) T.brand else T.textDim)
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Colour")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPalette.forEachIndexed { index, color ->
                    val selected = index == accent
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) T.text else Color.Transparent,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            onClick = { accent = index },
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (selected) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = Color(0xFF0B0F14))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Surface(
            onClick = {
                if (!canSave) return@Surface
                if (existing == null) {
                    val id = store.createTally(name, currency, accent)
                    nav.replaceTop(Route.Detail(id))
                } else {
                    store.updateTally(existing.id, name, currency, accent)
                    nav.back()
                }
            },
            enabled = canSave,
            shape = RoundedCornerShape(20.dp),
            color = if (canSave) T.brand else T.surfaceAlt,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(58.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (existing == null) "Start tally" else "Save changes",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) T.onBrand else T.textFaint,
                )
            }
        }
    }
}
