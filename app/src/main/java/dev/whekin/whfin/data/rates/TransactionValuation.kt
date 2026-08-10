package dev.whekin.whfin.data.rates

import dev.whekin.whfin.data.db.ExchangeRateHistoryEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * Books the GEL value of a foreign-currency row once, at the rate of the day it happened.
 *
 * Monthly totals must not move when the market moves: a March expense in dollars is worth its March
 * rate forever. Storing the value also keeps statistics a plain sum instead of a conversion over
 * hundreds of historical quotes.
 *
 * A row the bank already converted never reaches here — its ledger amount is in GEL and the exact
 * amount the bank charged is more accurate than any published quote.
 */
class TransactionValuationRepository(
    private val db: WhfinDatabase,
    private val provider: HistoricalRateProvider,
    private val zone: ZoneId = ZoneId.of("Asia/Tbilisi"),
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class Result(
        val valued: Int,
        /** Currencies whose day could not be priced; their rows stay unvalued rather than guessed. */
        val unresolved: Set<String>,
        val daysFetched: Int,
    ) {
        val didWork: Boolean get() = valued > 0
    }

    /**
     * Runs passes until nothing more can be valued.
     *
     * [backfill] deliberately stops after [DEFAULT_MAX_DAYS] so ordinary paths never burst into
     * hundreds of requests; the leftovers ride along with the next import or the next look at
     * statistics. After a one-off load of several years of history there is no next time worth
     * waiting for — the numbers would trickle in over as many visits as it takes — so that path
     * finishes the job instead.
     *
     * A pass that values nothing ends it, which covers both "all done" and "the remaining days
     * cannot be priced at all". [maxPasses] is a guard against a provider that somehow always
     * reports progress, not a stop condition.
     */
    suspend fun backfillAll(maxPasses: Int = DEFAULT_MAX_PASSES, onPass: (Result) -> Unit = {}): Result {
        var valued = 0
        var daysFetched = 0
        val unresolved = sortedSetOf<String>()
        repeat(maxPasses) {
            val pass = backfill()
            valued += pass.valued
            daysFetched += pass.daysFetched
            unresolved.clear()
            unresolved += pass.unresolved
            onPass(Result(valued, unresolved, daysFetched))
            if (!pass.didWork) return Result(valued, unresolved, daysFetched)
        }
        return Result(valued, unresolved, daysFetched)
    }

    /**
     * @param maxDays bounds one pass so a first import of several years cannot turn into a burst of
     * hundreds of requests. Whatever is left is picked up by the next pass.
     */
    suspend fun backfill(maxDays: Int = DEFAULT_MAX_DAYS): Result {
        val pending = db.transactionDao().awaitingValuation(PIVOT_CURRENCY)
        if (pending.isEmpty()) return Result(0, emptySet(), 0)

        val byDate = pending.groupBy { dateOf(it.occurredAt) }
        val unresolved = sortedSetOf<String>()
        var valued = 0
        var fetched = 0

        for ((date, rows) in byDate.entries.sortedByDescending { it.key }) {
            val codes = rows.map { it.currency.uppercase() }.toSet()
            val known = codes.associateWith { code -> db.exchangeRateDao().historical(code, date.toString()) }
            if (known.values.any { it == null }) {
                if (fetched >= maxDays) {
                    codes.forEach(unresolved::add)
                    continue
                }
                fetched++
                val fetchedQuotes = try {
                    provider.quotesOn(date)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    codes.forEach(unresolved::add)
                    continue
                }
                db.exchangeRateDao().upsertHistorical(
                    fetchedQuotes.values.map { quote ->
                        ExchangeRateHistoryEntity(
                            code = quote.code,
                            onDate = date.toString(),
                            gelPerUnit = quote.gelPerUnit.toPlainString(),
                            validOn = quote.validOn,
                            observedAt = now(),
                        )
                    },
                )
            }

            for (row in rows) {
                val code = row.currency.uppercase()
                val stored = db.exchangeRateDao().historical(code, date.toString())
                if (stored == null) {
                    unresolved += code
                    continue
                }
                val rate = runCatching { BigDecimal(stored.gelPerUnit) }.getOrNull()
                if (rate == null || rate.signum() <= 0) {
                    unresolved += code
                    continue
                }
                db.transactionDao().setGelValue(
                    id = row.id,
                    gelValueMinor = gelMinor(row.amountMinor, rate),
                    gelRateOn = stored.validOn ?: date.toString(),
                )
                valued++
            }
        }
        return Result(valued, unresolved, fetched)
    }

    private fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun gelMinor(amountMinor: Long, gelPerUnit: BigDecimal): Long =
        BigDecimal(amountMinor)
            .multiply(gelPerUnit, PRECISION)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private companion object {
        const val DEFAULT_MAX_DAYS = 40

        /** Ten passes of forty days each: more history than the bank will hand over anyway. */
        const val DEFAULT_MAX_PASSES = 10
        val PRECISION = MathContext(24, RoundingMode.HALF_UP)
    }
}
