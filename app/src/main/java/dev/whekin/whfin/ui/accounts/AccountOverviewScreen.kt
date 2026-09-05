package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinDistributionBar
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.rates.ExchangeRate
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatDecimal
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.NumberFormat

@Composable
fun AccountOverviewScreen(viewModel: AccountsViewModel = viewModel()) {
    val overview by viewModel.overview.collectAsState()
    when (val data = overview) {
        null -> WhfinStatePane(
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.accounts_loading),
            body = stringResource(R.string.accounts_loading_body),
            modifier = Modifier.fillMaxSize(),
        )
        else -> AccountOverviewContent(data)
    }
}

@Composable
internal fun AccountOverviewContent(data: AccountOverviewData) {
    val currency = data.split.total.currency
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val colors = listOf(
        WhfinThemeTokens.colors.bottle,
        WhfinThemeTokens.colors.clay,
        MaterialTheme.colorScheme.secondary,
        WhfinThemeTokens.colors.sage,
        MaterialTheme.colorScheme.tertiary,
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = navigationBottom + 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "net-worth") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The same reading as the headline on Accounts, in the same currency: this screen
                // exists to explain that number, so it cannot open with a different one.
                WhfinFieldLabel(
                    stringResource(R.string.account_overview_net_worth, data.split.total.currency),
                )
                WhfinAmount(
                    amountText(data.split.total.amount, currency),
                    symbol = currencySymbol(currency),
                    style = MaterialTheme.typography.displayMedium,
                )
            }
        }
        item(key = "position") {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                OverviewMetricPair(
                    firstLabel = stringResource(R.string.account_overview_assets),
                    firstValue = amountText(data.split.assets.amount, currency),
                    secondLabel = stringResource(R.string.account_overview_liabilities),
                    secondValue = amountText(data.split.liabilities.amount, currency),
                    currency = currency,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                OverviewMetricPair(
                    firstLabel = stringResource(R.string.accounts_available),
                    firstValue = amountText(data.split.available.amount, currency),
                    secondLabel = stringResource(R.string.accounts_reserve),
                    secondValue = amountText(data.split.reserve.amount, currency),
                    currency = currency,
                )
            }
        }
        item(key = "sources") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhfinSectionHeader(
                    title = stringResource(R.string.account_overview_sources),
                    supportingText = stringResource(R.string.account_overview_sources_hint),
                )
                val held = data.sources.fold(java.math.BigDecimal.ZERO) { sum, it -> sum.add(it.amount) }
                if (data.sources.isEmpty() || held.signum() <= 0) {
                    Text(
                        stringResource(R.string.account_overview_no_assets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WhfinDistributionBar(
                        data.sources.mapIndexed { index, source ->
                            WhfinDistributionSegment(source.amount.toFloat(), colors[index % colors.size])
                        },
                    )
                    Column {
                        data.sources.forEachIndexed { index, source ->
                            SourceRow(
                                source = source,
                                total = held,
                                currency = currency,
                                color = colors[index % colors.size],
                            )
                            if (index < data.sources.lastIndex) HorizontalDivider(
                                Modifier.padding(start = 22.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
        item(key = "currencies") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhfinSectionHeader(
                    title = stringResource(R.string.account_overview_other_currencies),
                    supportingText = stringResource(R.string.account_overview_other_currencies_hint),
                )
                if (data.nativeCurrencies.isEmpty()) {
                    Text(
                        stringResource(R.string.account_overview_no_other_currencies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        data.nativeCurrencies.forEachIndexed { index, balance ->
                            WhfinLedgerRow(
                                title = balance.currency,
                                trailing = {
                                    WhfinAmount(
                                        formatDecimal(balance.amount, balance.currency),
                                        symbol = currencySymbol(balance.currency),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                divider = index < data.nativeCurrencies.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A total that could not be converted is a dash, never a zero: no quote is not no money. */
private fun amountText(amount: java.math.BigDecimal?, currency: String): String =
    amount?.let { formatDecimal(it, currency) } ?: "—"

@Composable
private fun OverviewMetricPair(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    currency: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OverviewMetric(firstLabel, firstValue, currency, Modifier.weight(1f))
        OverviewMetric(secondLabel, secondValue, currency, Modifier.weight(1f))
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, currency: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WhfinAmount(value, symbol = currencySymbol(currency), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SourceRow(
    source: AccountSourceShare,
    total: java.math.BigDecimal,
    currency: String,
    color: Color,
) {
    val percentage = if (total.signum() <= 0) 0.0
    else source.amount.toDouble() / total.toDouble() * 100.0
    val formatter = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = if (percentage < 10.0) 1 else 0
        maximumFractionDigits = if (percentage < 10.0) 1 else 0
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${formatter.format(percentage)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WhfinAmount(
            formatDecimal(source.amount, currency),
            symbol = currencySymbol(currency),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val previewAccounts = listOf(
    AccountWithBalance(
        AccountEntity(id = 1, name = "Credo GEL •0001", type = AccountType.BANK, currency = "GEL"),
        559_417,
        emptyList(),
        groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 2, name = "Cash", type = AccountType.CASH, currency = "GEL"),
        34_200,
        emptyList(),
    ),
    AccountWithBalance(
        AccountEntity(
            id = 3, name = "Reserve", type = AccountType.SAVINGS, currency = "GEL",
            fundRole = FundRole.RESERVE,
        ),
        120_000,
        emptyList(),
    ),
    AccountWithBalance(
        AccountEntity(id = 4, name = "Credo USD", type = AccountType.BANK, currency = "USD"),
        12_340,
        emptyList(),
        groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 5, name = "Cash EUR", type = AccountType.CASH, currency = "EUR"),
        5_900,
        emptyList(),
    ),
)

/** Quotes a preview can convert with; without them the foreign rows would read as unconvertible. */
private val previewRates = mapOf(
    "USD" to ExchangeRate("USD", java.math.BigDecimal("2.70"), observedAt = System.currentTimeMillis()),
    "EUR" to ExchangeRate("EUR", java.math.BigDecimal("3.10"), observedAt = System.currentTimeMillis()),
)

@Preview(name = "Overview populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Overview dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Overview font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
private fun AccountOverviewPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountOverviewContent(accountOverviewData(previewAccounts, previewRates, "GEL"))
        }
    }
}

@Preview(name = "Overview empty", widthDp = 400, heightDp = 760, showBackground = true)
@Composable
private fun AccountOverviewEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountOverviewContent(accountOverviewData(emptyList(), previewRates, "GEL"))
        }
    }
}
