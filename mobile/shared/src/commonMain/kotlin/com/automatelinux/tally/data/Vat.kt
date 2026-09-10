package com.automatelinux.tally.data

/**
 * VAT, taken back out at display time — never stored.
 *
 * An entry's `amount` is always what was written down, which is what was actually paid.
 * `Entry.vatIncluded` says whether VAT is sitting inside that number, and `Tally.exVat`
 * says whether this tally is currently being shown with it removed. Nothing here ever
 * changes a stored amount: the record stays the receipt, and the ex-VAT figure is a way
 * of looking at it. That is also why an entry with no VAT in it reads identically in both
 * views — a purchase from someone who does not charge VAT has no net to show.
 *
 * 18% is the Israeli rate. It lives here as one constant rather than as a per-tally
 * setting because it changes about once a decade, and a rate on every tally is a rate
 * that will disagree with itself.
 */
const val VatPercent = 18

private const val Gross = 100 + VatPercent

/** 1180 → 1000. Rounded to the agora, half up, so the parts add back up to the whole. */
fun netOf(amount: Long): Long = (amount * 100 + Gross / 2) / Gross

/** 1180 → 180. Defined as the remainder, so net + VAT is exactly the amount paid. */
fun vatOf(amount: Long): Long = amount - netOf(amount)

/**
 * What this entry shows in a given view. The subtraction happens per entry and the
 * totals are summed from these, so a column of rows adds up to the total printed
 * above it — rounding the sum instead would leave the two disagreeing by an agora.
 */
fun Entry.shownAmount(exVat: Boolean): Long =
    if (exVat && vatIncluded) netOf(amount) else amount
