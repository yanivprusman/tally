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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.tally.data.TallyStore
import com.automatelinux.tally.ui.AmountScreen
import com.automatelinux.tally.ui.DetailScreen
import com.automatelinux.tally.ui.EditTallyScreen
import com.automatelinux.tally.ui.HomeScreen
import com.automatelinux.tally.ui.theme.AppTheme
import com.automatelinux.tally.ui.theme.T
import com.russhwolf.settings.Settings
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
fun App(settings: Settings, nav: Navigator) {
    AppTheme {
        val store = remember { TallyStore(settings) }
        val host = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
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
                    when (route) {
                        is Route.Home -> HomeScreen(store, nav)
                        is Route.Detail -> DetailScreen(store, nav, route.tallyId)
                        is Route.Amount -> AmountScreen(store, nav, route.tallyId, route.direction, route.entryId)
                        is Route.EditTally -> EditTallyScreen(store, nav, route.tallyId)
                    }
                }

                SnackbarHost(
                    hostState = host,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                ) { data ->
                    // Hand-built rather than the stock Snackbar: the message must never be
                    // overlapped by the action, and the action is the whole point of it.
                    Surface(shape = RoundedCornerShape(16.dp), color = T.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
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
