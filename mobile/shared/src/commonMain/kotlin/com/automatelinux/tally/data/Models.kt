package com.automatelinux.tally.data

import kotlinx.serialization.Serializable

/** Which way the money moved. */
@Serializable
enum class Direction { IN, OUT }

/**
 * One movement of money inside a tally. Amounts are kept in minor units (agorot/cents)
 * so no rounding error can ever creep into a running balance.
 */
@Serializable
data class Entry(
    val id: String,
    val direction: Direction,
    val amount: Long,
    val note: String = "",
    val category: String = "other",
    val at: Long,
    /** VAT sits inside `amount`. What was paid is what is stored, either way — see Vat.kt. */
    val vatIncluded: Boolean = false,
)

/**
 * An independent tracking: a trip, a job, a month. Tallies never mix — each keeps its
 * own entries, its own currency and its own running balance.
 */
@Serializable
data class Tally(
    val id: String,
    val name: String,
    val currency: String = "₪",
    val accent: Int = 0,
    /** Show the amounts with VAT taken back out of the entries that carry it. */
    val exVat: Boolean = false,
    val createdAt: Long,
    val entries: List<Entry> = emptyList(),
) {
    // Every total goes through the current view, so the switch moves the balance, the
    // day headings and the tally card together — there is no second, "real" number
    // hiding behind the one on screen.
    val totalIn: Long get() = sum(Direction.IN)
    val totalOut: Long get() = sum(Direction.OUT)
    val net: Long get() = totalIn - totalOut
    val lastActivity: Long get() = entries.maxOfOrNull { it.at } ?: createdAt

    /** The VAT this view took out, per side: money you owe on what came in, money you
     *  can claim back on what went out. Summing the two together would be meaningless. */
    val vatIn: Long get() = vat(Direction.IN)
    val vatOut: Long get() = vat(Direction.OUT)
    val hasVatEntries: Boolean get() = entries.any { it.vatIncluded }

    private fun sum(d: Direction) =
        entries.sumOf { if (it.direction == d) it.shownAmount(exVat) else 0L }

    private fun vat(d: Direction) =
        entries.sumOf { if (it.direction == d && it.vatIncluded) vatOf(it.amount) else 0L }
}

/** A labelled bucket offered on the amount screen. Free text always wins over the label. */
data class Category(val id: String, val label: String)

val IncomeCategories = listOf(
    Category("work", "Work"),
    Category("sale", "Sale"),
    Category("refund", "Refund"),
    Category("gift", "Gift"),
    Category("other", "Other"),
)

val ExpenseCategories = listOf(
    Category("food", "Food"),
    Category("stay", "Stay"),
    Category("travel", "Travel"),
    Category("shop", "Shop"),
    Category("fun", "Fun"),
    Category("bills", "Bills"),
    Category("other", "Other"),
)

fun categoriesFor(direction: Direction): List<Category> =
    if (direction == Direction.IN) IncomeCategories else ExpenseCategories

fun categoryLabel(id: String): String =
    (IncomeCategories + ExpenseCategories).firstOrNull { it.id == id }?.label ?: "Other"
