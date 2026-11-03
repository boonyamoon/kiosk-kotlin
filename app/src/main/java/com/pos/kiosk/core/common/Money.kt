package com.pos.kiosk.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val wholeFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

/** "฿45" for whole amounts, "฿45.50" otherwise. */
fun formatBaht(amount: Double): String {
    val isWhole = abs(amount - amount.roundToLong()) < 0.005
    return "฿" + if (isWhole) wholeFormat.format(amount.roundToLong()) else decimalFormat.format(amount)
}

/** Always two decimals — for receipts. */
fun formatAmount(amount: Double): String = decimalFormat.format(amount)

/** "฿45.00" — every amount from the order review onwards, where the money is what gets paid. */
fun formatBahtExact(amount: Double): String = "฿" + decimalFormat.format(amount)

/** A percentage rate without a needless ".0": 7.0 → "7", 7.5 → "7.5". */
fun formatRate(rate: Double): String = if (rate % 1.0 == 0.0) rate.toLong().toString() else rate.toString()

fun roundMoney(amount: Double): Double = (amount * 100).roundToLong() / 100.0
