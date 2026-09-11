package com.automatelinux.tally

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.data.TallyApi
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.ui.AmountScreen
import com.automatelinux.tally.ui.DetailScreen
import com.automatelinux.tally.ui.EditTallyScreen
import com.automatelinux.tally.ui.HomeScreen
import com.automatelinux.tally.ui.theme.AppTheme
import com.automatelinux.tally.ui.theme.T
import kotlinx.coroutines.launch

/**
 * Every destructive action in the app goes through here so it can always be taken back:
 * reset, delete a tally, delete an entry. `Undoable` shows the message with an Undo action
 * and calls back into the store if the user takes it.
 */
class Undoable(
    private val host: SnackbarHostState,
    private val store: TallyStore,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    fun offerUndo(message: String) = scope.launch {
        val result = host.showSnackbar(message = message, actionLabel = "Undo")
        if (result == SnackbarResult.ActionPerformed) store.undoLast()
    }
}

val LocalUndoable = staticCompositionLocalOf<Undoable> { error("No Undoable in scope") }

@Composable
fun App(api: TallyApi, nav: Navigator) {
    AppTheme {
        val scope = rememberCoroutineScope()
        val store = remember { TallyStore(api, scope) }
        // Keeps each screen's saved state — in practice, where its list was scrolled to —
        // so that stepping into an entry and back returns you to the row you tapped
        // rather than to the top of the tally. See Route.stateKey.
        val screenState = rememberSaveableStateHolder()
        val host = remember { SnackbarHostState() }
        val undoable = remember { Undoable(host, store, scope) }

        CompositionLocalProvider(LocalUndoable provides undoable) {
            Box(Modifier.fillMaxSize().background(T.bg)) {
                val forward = nav.forward
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = {
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(260)) { (it * 0.16f * dir).toInt() } + fadeIn(tween(190)))
                            .togetherWith(slideOutHorizontally(tween(260)) { (-it * 0.16f * dir).toInt() } + fadeOut(tween(140)))
                    },
                    label = "route",
                ) { route ->
                    screenState.SaveableStateProvider(route.stateKey) {
                        when (route) {
                            is Route.Home -> HomeScreen(store, nav)
                            is Route.Detail -> DetailScreen(store, nav, route.tallyId)
                            is Route.Amount -> AmountScreen(store, nav, route.tallyId, route.direction, route.entryId)
                            is Route.EditTally -> EditTallyScreen(store, nav, route.tallyId)
                        }
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                ) {
                    store.error?.let { message ->
                        Surface(shape = RoundedCornerShape(16.dp), color = T.expenseSoft, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = T.expense,
                                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                )
                                TextButton(onClick = { store.refresh() }) {
                                    Text("Retry", style = MaterialTheme.typography.labelLarge, color = T.expense)
                                }
                                TextButton(onClick = { store.dismissError() }) {
                                    Text("Hide", style = MaterialTheme.typography.labelLarge, color = T.textDim)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                SnackbarHost(
                    hostState = host,
                    modifier = Modifier.fillMaxWidth(),
                ) { data ->
                    val offsetX = remember { mutableStateOf(0f) }
                    val alpha = (1f - (kotlin.math.abs(offsetX.value) / 400f).coerceIn(0f, 1f))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = T.surfaceAlt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = offsetX.value
                                this.alpha = alpha
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta -> offsetX.value += delta },
                                onDragStopped = {
                                    if (kotlin.math.abs(offsetX.value) > 150f) {
                                        data.dismiss()
                                    } else {
                                        offsetX.value = 0f
                                    }
                                },
                            ),
                    ) {
                        Row(
                            Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                data.visuals.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = T.text,
                                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                            )
                            data.visuals.actionLabel?.let { label ->
                                TextButton(onClick = { data.performAction() }) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = T.brand)
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
