package dev.whekin.whfin.data.rates

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import org.json.JSONArray

/** Official GEL quotes for a past day. */
interface HistoricalRateProvider {
    /** Every currency published for [date], keyed by code. Throws [RateException] on failure. */
    suspend fun quotesOn(date: LocalDate): Map<String, HistoricalRate>
}

data class HistoricalRate(
    val code: String,
    val gelPerUnit: BigDecimal,
    /** The day the source itself considers this quote valid for. */
    val validOn: String?,
)

/**
 * National Bank of Georgia, asked for one specific day.
 *
 * The API answers a weekend or holiday with the previous banking day, which is exactly what a bank
 * would use, so the response states the day it actually applies to and that is what gets stored.
 * Every currency is requested rather than the ones held, so the call says nothing about this ledger.
 */
class NbgHistoricalRateProvider(
    private val transport: RateHttpTransport = UrlConnectionRateTransport(),
    private val endpoint: String = NbgFiatRateProvider.DEFAULT_ENDPOINT,
) : HistoricalRateProvider {

    override suspend fun quotesOn(date: LocalDate): Map<String, HistoricalRate> {
        val body = transport.get("$endpoint?date=$date")
        val root = runCatching { JSONArray(body) }.getOrElse { throw RateException("UNREADABLE") }
        val day = root.optJSONObject(0) ?: throw RateException("EMPTY")
        val validOn = day.optString("date").take(10).takeIf { it.isNotBlank() }
        val currencies = day.optJSONArray("currencies") ?: throw RateException("EMPTY")

        return (0 until currencies.length()).mapNotNull { index ->
            val item = currencies.optJSONObject(index) ?: return@mapNotNull null
            val code = item.optString("code").uppercase().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val rate = item.optDouble("rate", Double.NaN)
            // Some currencies are quoted per 100 units; ignoring quantity inflates them a hundredfold.
            val quantity = item.optInt("quantity", 1).takeIf { it > 0 } ?: 1
            if (rate.isNaN() || rate <= 0.0) return@mapNotNull null
            code to HistoricalRate(
                code = code,
                gelPerUnit = BigDecimal.valueOf(rate).divide(BigDecimal(quantity), PRECISION),
                validOn = validOn,
            )
        }.toMap().also { if (it.isEmpty()) throw RateException("EMPTY") }
    }

    private companion object {
        val PRECISION = MathContext(24)
    }
}
