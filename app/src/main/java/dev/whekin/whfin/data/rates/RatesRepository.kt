package dev.whekin.whfin.data.rates

import dev.whekin.whfin.data.db.ExchangeRateEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

/**
 * Keeps the local quote snapshot fresh.
 *
 * Providers run in order because the crypto price needs the fiat USD quote to reach GEL. A failing
 * provider leaves the previous quotes untouched: a stale rate that is labelled stale is far better
 * than a total that silently loses a currency.
 */
class RatesRepository(
    private val db: WhfinDatabase,
    private val providers: List<RateProvider>,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class RefreshResult(val updated: Int, val failed: Int) {
        val succeeded: Boolean get() = failed == 0 && updated > 0
    }

    suspend fun refresh(): RefreshResult {
        var known = current().associateBy { it.code }
        var updated = 0
        var failed = 0

        providers.forEach { provider ->
            try {
                val quotes = provider.quotes(RateContext(now(), known))
                if (quotes.isEmpty()) {
                    failed++
                    return@forEach
                }
                db.exchangeRateDao().upsert(quotes.map(::toEntity))
                known = known + quotes.associateBy { it.code }
                updated += quotes.size
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed++
            }
        }
        return RefreshResult(updated, failed)
    }

    /** Re-reads only when the snapshot aged out, so opening a screen is not a network burst. */
    suspend fun refreshIfStale(): RefreshResult? {
        val observedAt = observedAt()
        val stale = observedAt == null || now() - observedAt > STALE_AFTER_MILLIS
        return if (stale) refresh() else null
    }

    suspend fun current(): List<ExchangeRate> = db.exchangeRateDao().all().map(::toRate)

    /** Oldest quote in the snapshot, or null when nothing has been read yet. */
    suspend fun observedAt(): Long? = current().minOfOrNull { it.observedAt }

    private fun toEntity(rate: ExchangeRate) = ExchangeRateEntity(
        code = rate.code,
        gelPerUnit = rate.gelPerUnit.toPlainString(),
        observedAt = rate.observedAt,
        validOn = rate.validOn,
        source = rate.source,
    )

    companion object {
        /** Rates are published once per banking day, so re-reading more often is pure noise. */
        const val STALE_AFTER_MILLIS = 6L * 60 * 60 * 1000
    }
}

fun toRate(entity: ExchangeRateEntity) = ExchangeRate(
    code = entity.code,
    gelPerUnit = runCatching { BigDecimal(entity.gelPerUnit) }.getOrDefault(BigDecimal.ZERO),
    observedAt = entity.observedAt,
    validOn = entity.validOn,
    source = entity.source,
)
