package com.automatelinux.tally.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The VAT split, and the promise the screens make about it: the rows a tally shows
 * add up to the total printed above them, in either reading.
 */
class VatTest {

    private fun out(amount: Long, vat: Boolean, at: Long = 0) =
        Entry(id = "e$amount$vat$at", direction = Direction.OUT, amount = amount, at = at, vatIncluded = vat)

    private fun inn(amount: Long, vat: Boolean, at: Long = 0) =
        Entry(id = "i$amount$vat$at", direction = Direction.IN, amount = amount, at = at, vatIncluded = vat)

    @Test
    fun splitsTheStandardCase() {
        assertEquals(100_000, netOf(118_000))   // ₪1,180 paid is ₪1,000 plus
        assertEquals(18_000, vatOf(118_000))    // ₪180 of VAT
    }

    @Test
    fun roundsToTheNearestAgora() {
        // ₪100 gross is ₪84.7457… — the half-up rule lands it on ₪84.75.
        assertEquals(8_475, netOf(10_000))
        assertEquals(1_525, vatOf(10_000))
    }

    @Test
    fun theTwoPartsAlwaysMakeTheWhole() {
        // The property the rounding exists to protect: whatever it does to the net,
        // the VAT is the remainder, so no agora is invented or lost.
        for (amount in listOf(1L, 7L, 99L, 100L, 333L, 10_000L, 118_000L, 999_999L, 12_345_678L)) {
            assertEquals(amount, netOf(amount) + vatOf(amount), "split of $amount")
            assertTrue(netOf(amount) <= amount, "net of $amount exceeds it")
        }
    }

    @Test
    fun anEntryWithoutVatReadsTheSameInBothViews() {
        val e = out(5_000, vat = false)
        assertEquals(5_000, e.shownAmount(exVat = false))
        assertEquals(5_000, e.shownAmount(exVat = true))
    }

    @Test
    fun onlyTheExVatViewTakesVatOut() {
        val e = out(118_000, vat = true)
        assertEquals(118_000, e.shownAmount(exVat = false))
        assertEquals(100_000, e.shownAmount(exVat = true))
    }

    @Test
    fun totalsFollowTheView() {
        val entries = listOf(out(118_000, true), out(5_000, false), inn(236_000, true))
        val written = Tally(id = "t", name = "t", createdAt = 0, entries = entries)
        val net = written.copy(exVat = true)

        assertEquals(236_000, written.totalIn)
        assertEquals(123_000, written.totalOut)
        assertEquals(113_000, written.net)

        assertEquals(200_000, net.totalIn)
        assertEquals(105_000, net.totalOut)          // 100,000 net + the 5,000 with no VAT
        assertEquals(95_000, net.net)
    }

    @Test
    fun theRowsAddUpToTheTotalTheScreenPrints() {
        // Rounding per entry rather than per total is what makes this true; rounding
        // the sum instead would leave the column and the headline an agora apart.
        val entries = listOf(
            out(10_000, true), out(333, true), out(99, true), out(7, true), out(4_321, false),
        )
        val t = Tally(id = "t", name = "t", exVat = true, createdAt = 0, entries = entries)
        assertEquals(entries.sumOf { it.shownAmount(true) }, t.totalOut)
    }

    @Test
    fun vatIsReportedPerSide() {
        // Owed on what came in, reclaimable on what went out: one number for both
        // would be two different kinds of money added together.
        val t = Tally(
            id = "t", name = "t", createdAt = 0,
            entries = listOf(out(118_000, true), inn(236_000, true), inn(1_000, false)),
        )
        assertEquals(36_000, t.vatIn)
        assertEquals(18_000, t.vatOut)
        assertTrue(t.hasVatEntries)
        assertTrue(!t.copy(entries = listOf(out(500, false))).hasVatEntries)
    }
}
