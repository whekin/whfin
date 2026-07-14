package dev.whekin.whfin.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** "12.5" / "12,50" / "1 083.20" -> minor units; null если не парсится или 0. */
fun parseToMinor(text: String): Long? {
    val cleaned = text.replace(" ", "").replace(',', '.')
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
    return cleaned.toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.toLong()
        ?.takeIf { it != 0L }
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
    val symbol = when (normalizedCurrency) {
        "GEL" -> "₾"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> currency
    }
    val amount = formatter.format(value)
    return when (normalizedCurrency) {
        "USD", "GBP" -> "$sign$symbol$amount"
        else -> "$sign$amount $symbol"
    }
}
