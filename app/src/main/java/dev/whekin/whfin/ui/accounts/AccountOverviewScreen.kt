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
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.NumberFormat

internal data class AccountSourceShare(
    val name: String,
    val balanceMinor: Long,
)

internal data class NativeCurrencyBalance(
    val currency: String,
    val balanceMinor: Long,
)

internal data class AccountOverviewData(
    val netWorthMinor: Long,
    val assetsMinor: Long,
    val liabilitiesMinor: Long,
    val availableMinor: Long,
    val reserveMinor: Long,
    val sources: List<AccountSourceShare>,
    val otherCurrencies: List<NativeCurrencyBalance>,
)

internal fun accountOverviewData(accounts: List<AccountWithBalance>): AccountOverviewData {
    val gel = accounts.filter { it.account.currency == "GEL" }
    val assets = gel.sumOf { it.balanceMinor.coerceAtLeast(0L) }
    val liabilities = -gel.sumOf { it.balanceMinor.coerceAtMost(0L) }
    val available = gel
        .filter { it.account.savingsMode == null && it.account.type != AccountType.SAVINGS }
        .sumOf { it.balanceMinor }
    val reserve = gel
        .filter { it.account.savingsMode != null || it.account.type == AccountType.SAVINGS }
        .sumOf { it.balanceMinor }
    val sources = gel
        .filter { it.balanceMinor > 0L }
        .groupBy { it.groupName ?: it.account.name }
        .map { (name, values) -> AccountSourceShare(name, values.sumOf { it.balanceMinor }) }
        .sortedByDescending { it.balanceMinor }
    val otherCurrencies = accounts
        .filter { it.account.currency != "GEL" }
        .groupBy { it.account.currency }
        .map { (currency, values) -> NativeCurrencyBalance(currency, values.sumOf { it.balanceMinor }) }
        .sortedBy { it.currency }
    return AccountOverviewData(
        netWorthMinor = gel.sumOf { it.balanceMinor },
        assetsMinor = assets,
        liabilitiesMinor = liabilities,
        availableMinor = available,
        reserveMinor = reserve,
        sources = sources,
        otherCurrencies = otherCurrencies,
    )
}

@Composable
fun AccountOverviewScreen(viewModel: AccountsViewModel = viewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    AccountOverviewContent(accountOverviewData(accounts))
}

@Composable
internal fun AccountOverviewContent(data: AccountOverviewData) {
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
                WhfinSectionLabel(stringResource(R.string.account_overview_net_worth))
                Text(
                    formatMinor(data.netWorthMinor, "GEL"),
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        item(key = "position") {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                OverviewMetricPair(
                    firstLabel = stringResource(R.string.account_overview_assets),
                    firstValue = formatMinor(data.assetsMinor, "GEL"),
                    secondLabel = stringResource(R.string.account_overview_liabilities),
                    secondValue = formatMinor(-data.liabilitiesMinor, "GEL"),
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                OverviewMetricPair(
                    firstLabel = stringResource(R.string.accounts_available),
                    firstValue = formatMinor(data.availableMinor, "GEL"),
                    secondLabel = stringResource(R.string.accounts_reserve),
                    secondValue = formatMinor(data.reserveMinor, "GEL"),
                )
            }
        }
        item(key = "sources") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhfinSectionHeader(
                    title = stringResource(R.string.account_overview_sources),
                    supportingText = stringResource(R.string.account_overview_sources_hint),
                )
                if (data.sources.isEmpty() || data.assetsMinor <= 0L) {
                    Text(
                        stringResource(R.string.account_overview_no_assets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WhfinDistributionBar(
                        data.sources.mapIndexed { index, source ->
                            WhfinDistributionSegment(source.balanceMinor.toFloat(), colors[index % colors.size])
                        },
                    )
                    Column {
                        data.sources.forEachIndexed { index, source ->
                            SourceRow(
                                source = source,
                                totalMinor = data.assetsMinor,
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
                if (data.otherCurrencies.isEmpty()) {
                    Text(
                        stringResource(R.string.account_overview_no_other_currencies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        data.otherCurrencies.forEachIndexed { index, currency ->
                            WhfinLedgerRow(
                                title = currency.currency,
                                trailing = {
                                    Text(
                                        formatMinor(currency.balanceMinor, currency.currency),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                                divider = index < data.otherCurrencies.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricPair(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OverviewMetric(firstLabel, firstValue, Modifier.weight(1f))
        OverviewMetric(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun SourceRow(source: AccountSourceShare, totalMinor: Long, color: Color) {
    val percentage = if (totalMinor <= 0L) 0.0 else source.balanceMinor.toDouble() / totalMinor * 100.0
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
        Text(
            formatMinor(source.balanceMinor, "GEL"),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
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
        AccountEntity(id = 3, name = "Reserve", type = AccountType.SAVINGS, currency = "GEL"),
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

@Preview(name = "Overview populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Overview dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Overview font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
private fun AccountOverviewPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountOverviewContent(accountOverviewData(previewAccounts))
        }
    }
}

@Preview(name = "Overview empty", widthDp = 400, heightDp = 760, showBackground = true)
@Composable
private fun AccountOverviewEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountOverviewContent(accountOverviewData(emptyList()))
        }
    }
}
