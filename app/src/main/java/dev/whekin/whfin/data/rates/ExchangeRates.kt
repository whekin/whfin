package dev.whekin.whfin.data.rates

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The pivot every quote is stored against. */
const val PIVOT_CURRENCY = "GEL"

/**
 * Value of one unit of [code] in GEL at the moment it was read.
 *
 * Everything is quoted against one pivot on purpose: converting between any two currencies is then a
 * single multiply and divide, and a display switch cannot chain two independently stale quotes.
 */
data class ExchangeRate(
    val code: String,
    val gelPerUnit: BigDecimal,
    val observedAt: Long,
    /** Day the source itself considers the quote valid for, when it states one. */
    val validOn: String? = null,
    val source: String? = null,
)

/** What a conversion could and could not cover, so the UI never implies a complete total. */
data class ConvertedTotal(
    val currency: String,
    val amount: BigDecimal?,
    /** Currencies with no usable quote; their money is deliberately left out of [amount]. */
    val missing: Set<String>,
    /** Oldest quote the total depends on. */
    val asOf: Long?,
    val validOn: String? = null,
) {
    val isComplete: Boolean get() = amount != null && missing.isEmpty()
}

/**
 * Pure conversion over a snapshot of quotes.
 *
 * A missing quote is never treated as zero and never silently dropped from the story: the amount
 * covers what could be converted and [ConvertedTotal.missing] names the rest.
 */
object MoneyConverter {

    private val precision = MathContext(24, RoundingMode.HALF_UP)

    fun convert(
        amountsByCurrency: Map<String, BigDecimal>,
        displayCurrency: String,
        rates: Map<String, ExchangeRate>,
    ): ConvertedTotal {
        val display = displayCurrency.uppercase()
        val displayRate = rateFor(display, rates)
            ?: return ConvertedTotal(display, null, amountsByCurrency.keys, null)

        var totalGel = BigDecimal.ZERO
        val missing = sortedSetOf<String>()
        var oldest: Long? = null
        var validOn: String? = null

        amountsByCurrency.forEach { (currency, amount) ->
            val code = currency.uppercase()
            if (amount.signum() == 0 && code != display) return@forEach
            val rate = rateFor(code, rates)
            if (rate == null) {
                missing += code
                return@forEach
            }
            totalGel = totalGel.add(amount.multiply(rate.gelPerUnit, precision))
            if (code != PIVOT_CURRENCY) {
                if (oldest == null || rate.observedAt < oldest!!) {
                    oldest = rate.observedAt
                    validOn = rate.validOn
                }
            }
        }

        if (display != PIVOT_CURRENCY) {
            val displayObserved = displayRate.observedAt
            if (oldest == null || displayObserved < oldest!!) {
                oldest = displayObserved
                validOn = displayRate.validOn
            }
        }

        return ConvertedTotal(
            currency = display,
            amount = totalGel.divide(displayRate.gelPerUnit, precision).setScale(2, RoundingMode.HALF_UP),
            missing = missing,
            asOf = oldest,
            validOn = validOn,
        )
    }

    private fun rateFor(code: String, rates: Map<String, ExchangeRate>): ExchangeRate? = when (code) {
        PIVOT_CURRENCY -> ExchangeRate(PIVOT_CURRENCY, BigDecimal.ONE, observedAt = Long.MAX_VALUE)
        else -> rates[code]
    }
}
