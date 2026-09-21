package dev.whekin.whfin.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Exact signed minor units; rejects zero, fractional minor units and overflow. */
fun parseToMinor(text: String, allowZero: Boolean = false): Long? {
    val cleaned = text.filterNot { it.isWhitespace() || it == '\u00A0' }.replace(',', '.')
    if (cleaned.length !in 1..64 || !MONEY_INPUT.matches(cleaned)) return null
    return try {
        cleaned.toBigDecimal().movePointRight(2).longValueExact()
            .takeIf { (allowZero || it != 0L) && it != Long.MIN_VALUE }
    } catch (_: ArithmeticException) {
        null
    }
}

private val MONEY_INPUT = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")

/**
 * The gap between an amount and its sign, which no line break may fall into.
 *
 * "166.64 ₾ · average 64.47 ₾" inside a row wrapped after the number and left the lari sign alone on
 * the next line. An amount and its currency are one word; the sentence around them still has plenty
 * of ordinary spaces to break at.
 */
private const val NBSP = '\u00A0'

/** Символ валюты, который [formatMinor] подставляет в строку; нужен для тихого набора символа. */
fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "GEL" -> "₾"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "RUB" -> "₽"
    else -> currency
}

/**
 * Exact on-chain base units as a readable amount.
 *
 * Chain amounts are not fiat minor units: 18 decimals of wei must not be rounded into two. The value
 * is scaled exactly and only the display is shortened, so a dust balance still reads as more than zero.
 */
fun formatBaseUnits(baseUnits: String, decimals: Int, maxFractionDigits: Int = 8): String {
    val exact = runCatching { BigDecimal(baseUnits).movePointLeft(decimals) }.getOrNull()
        ?: return baseUnits
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = maxFractionDigits.coerceAtLeast(2)
    }
    return formatter.format(exact)
}

/** Same typography as [formatMinor], for a value that is already scaled rather than minor units. */
fun formatDecimal(amount: BigDecimal, currency: String): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val sign = if (amount.signum() < 0) "-" else ""
    val text = formatter.format(amount.abs())
    return when (currency.uppercase()) {
        "USD", "GBP" -> "$sign${currencySymbol(currency)}$text"
        else -> "$sign$text$NBSP${currencySymbol(currency)}"
    }
}

fun formatMinor(amountMinor: Long, currency: String, withSign: Boolean = false): String {
    val value = BigDecimal(amountMinor).movePointLeft(2).abs()
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val sign = when {
        amountMinor < 0 -> "-"
        withSign && amountMinor > 0 -> "+"
        else -> ""
    }
    val normalizedCurrency = currency.uppercase()
    val symbol = currencySymbol(currency)
    val amount = formatter.format(value)
    return when (normalizedCurrency) {
        "USD", "GBP" -> "$sign$symbol$amount"
        else -> "$sign$amount$NBSP$symbol"
    }
}
