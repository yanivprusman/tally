package com.automatelinux.tally.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/**
 * Money and time formatting, hand-rolled because commonMain has no java.text and the
 * output here is deliberately terser than a locale formatter would give: whole shekels
 * stay whole, so a list of round numbers reads as round numbers.
 */

/** 20000 -> "200", 20050 -> "200.50". Never shows a sign; callers decide how to mark it. */
fun formatAmount(minor: Long): String {
    val v = abs(minor)
    val whole = v / 100
    val cents = (v % 100).toInt()
    val grouped = groupThousands(whole)
    return if (cents == 0) grouped else grouped + "." + (if (cents < 10) "0$cents" else "$cents")
}

fun formatMoney(minor: Long, currency: String): String = currency + formatAmount(minor)

fun formatSigned(minor: Long, currency: String): String = when {
    minor > 0 -> "+" + currency + formatAmount(minor)
    minor < 0 -> "−" + currency + formatAmount(minor)
    else -> currency + "0"
}

private fun groupThousands(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val out = StringBuilder()
    var count = 0
    for (i in s.lastIndex downTo 0) {
        out.append(s[i])
        count++
        if (count % 3 == 0 && i != 0) out.append(',')
    }
    return out.reverse().toString()
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun dateOf(epochMillis: Long): LocalDate =
    kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

fun dayKey(epochMillis: Long): String = dateOf(epochMillis).toString()

/** "Today" / "Yesterday" / "Wed 3 Sep" — the header above a day's entries. */
fun formatDayHeader(epochMillis: Long): String {
    val d = dateOf(epochMillis)
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val diff = today.toEpochDays() - d.toEpochDays()
    return when (diff) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> DAYS[d.dayOfWeek.ordinal] + " " + d.dayOfMonth + " " + MONTHS[d.monthNumber - 1]
    }
}

/** "14:05" */
fun formatTime(epochMillis: Long): String {
    val t = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val m = t.minute
    return "${t.hour}:" + (if (m < 10) "0$m" else "$m")
}

/** "just now" / "3h ago" / "2 Sep" — the quiet line on a tally card. */
fun formatRelative(epochMillis: Long): String {
    val delta = Clock.System.now().toEpochMilliseconds() - epochMillis
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> {
            val d = dateOf(epochMillis)
            "${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]}"
        }
    }
}
