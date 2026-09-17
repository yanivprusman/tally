package com.automatelinux.tally

import com.automatelinux.tally.data.Direction
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each test ends by starting a second Navigator on the same settings — that is the app
 * being closed and opened again.
 */
class NavigatorTest {
    private val settings = MapSettings()
    private fun reopen() = Navigator(settings)

    @Test
    fun firstLaunchOpensOnTheList() {
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun opensBackInsideTheTallyYouWereIn() {
        reopen().go(Route.Detail("a"))
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun backFromTheReopenedTallyGoesToTheListNotOutOfTheApp() {
        reopen().go(Route.Detail("a"))
        val nav = reopen()
        assertTrue(nav.back())
        assertEquals(Route.Home, nav.current)
        assertFalse(nav.back())
    }

    @Test
    fun leavingToTheListIsRememberedToo() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.back()
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun anEntryScreenReopensAsItsTallyNotAsTheHalfTypedEntry() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Amount("a", Direction.OUT))
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun renamingFromACardOnTheListIsNotBeingInATally() {
        reopen().go(Route.EditTally("a"))
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun aNewTallyIsRememberedOnceItOpens() {
        val nav = reopen()
        nav.go(Route.EditTally(null))
        nav.replaceTop(Route.Detail("new"))
        assertEquals(Route.Detail("new"), reopen().current)
    }

    @Test
    fun aDuplicateIsTheTallyYouAreInAndBackReturnsToTheOriginal() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Detail("a-copy"))
        assertEquals(Route.Detail("a-copy"), reopen().current)
        nav.back()
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun aDeletedTallyIsForgotten() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Amount("a", Direction.IN))
        nav.popToHome()
        assertEquals(Route.Home, reopen().current)
    }
}
