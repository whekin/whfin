package dev.whekin.whfin.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.rates.ConvertedTotal

/**
 * Says where a converted headline comes from.
 *
 * A total in one currency is the only number in WHFIN that is not literally true, so its label
 * carries the quote's age, and a currency that could not be converted is named rather than quietly
 * dropped out of the sum.
 */
@Composable
fun convertedTotalLabel(base: String, total: ConvertedTotal?): String = when {
    total == null -> base
    total.amount == null -> stringResource(R.string.net_worth_no_rate)
    total.missing.isNotEmpty() -> stringResource(
        R.string.net_worth_partial,
        total.missing.take(2).joinToString(", ") +
            if (total.missing.size > 2) " +${total.missing.size - 2}" else "",
    )
    // A fresh quote is the normal case and needs no announcement; only an ageing one is news.
    total.asOf == null || System.currentTimeMillis() - total.asOf < STALE_AFTER_MILLIS -> base
    else -> stringResource(
        R.string.net_worth_as_of,
        DateUtils.getRelativeTimeSpanString(
            total.asOf,
            System.currentTimeMillis(),
            DateUtils.HOUR_IN_MILLIS,
        ).toString(),
    )
}

/** Official rates are published once per banking day, so a same-day quote is simply current. */
private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
