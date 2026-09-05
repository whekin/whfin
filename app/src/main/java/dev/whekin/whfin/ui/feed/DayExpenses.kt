package dev.whekin.whfin.ui.feed

/** What a day cost, by the currency it was spent in, plus the lari an FX purchase actually took. */
internal data class DayExpenses(
    val byCurrency: Map<String, Long>,
    val gelFromConversions: Long,
)

/**
 * The one rule for the number a day header prints.
 *
 * Three screens showed that number and each computed it differently: the feed excluded transfers and
 * debts, an account's own ledger summed every negative row it had, and the analytics list sat
 * somewhere between. On an account the difference was not cosmetic — a day whose only row was
 * "Between your accounts" printed a spending total, which is the one thing a transfer is not.
 *
 * Moving money between one's own accounts is not spending and borrowed money is not spending either,
 * on any screen. An FX purchase stores the price in the currency it was charged in, so the lari it
 * really took comes from the conversion beside it.
 */
internal fun dayExpenses(items: List<FeedItem>): DayExpenses {
    val spending = items.filter {
        !it.tx.isTransfer && it.tx.transferGroupId == null && it.tx.amountMinor < 0 && !it.isDebt
    }
    return DayExpenses(
        byCurrency = spending
            .groupBy { it.tx.currency }
            .mapValues { (_, list) -> -list.sumOf { it.tx.amountMinor } }
            .filterValues { it > 0L },
        gelFromConversions = spending
            .filter { it.tx.currency != "GEL" && it.fundedByConversionCurrency == "GEL" }
            .sumOf { it.fundedByConversionMinor ?: 0L },
    )
}
