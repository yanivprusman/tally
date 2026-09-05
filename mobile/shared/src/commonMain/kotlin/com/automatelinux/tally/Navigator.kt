package com.automatelinux.tally

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.tally.data.Direction

sealed interface Route {
    data object Home : Route
    data class Detail(val tallyId: String) : Route
    data class Amount(val tallyId: String, val direction: Direction, val entryId: String? = null) : Route
    data class EditTally(val tallyId: String?) : Route
}

/**
 * A back stack, not a navigation library. Four screens do not need one, and owning the
 * stack here is what lets the Android back button and the on-screen back arrow be the
 * same single code path.
 */
class Navigator {
    private val stack = mutableStateListOf<Route>(Route.Home)

    val current: Route get() = stack.last()

    /** Which way the last transition went, so screens can animate in from the right side. */
    var forward by mutableStateOf(true)
        private set

    fun go(route: Route) {
        forward = true
        stack.add(route)
    }

    /** Replaces the top of the stack — used when a newly created tally opens straight away. */
    fun replaceTop(route: Route) {
        forward = true
        stack[stack.lastIndex] = route
    }

    /** True if it handled the gesture; false means "there is nothing left, leave the app". */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popToHome() {
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
