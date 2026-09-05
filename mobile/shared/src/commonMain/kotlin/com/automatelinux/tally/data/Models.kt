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
    val createdAt: Long,
    val entries: List<Entry> = emptyList(),
) {
    val totalIn: Long get() = entries.sumOf { if (it.direction == Direction.IN) it.amount else 0L }
    val totalOut: Long get() = entries.sumOf { if (it.direction == Direction.OUT) it.amount else 0L }
    val net: Long get() = totalIn - totalOut
    val lastActivity: Long get() = entries.maxOfOrNull { it.at } ?: createdAt
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
