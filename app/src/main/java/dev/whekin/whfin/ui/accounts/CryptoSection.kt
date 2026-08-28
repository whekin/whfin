package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinTotalRule
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.ui.convertedTotalLabel
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatBaseUnits
import dev.whekin.whfin.ui.formatDecimal
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.math.BigDecimal
import java.math.BigInteger

/**
 * The chain part of the ledger, read as one thing.
 *
 * Money is grouped by what it is rather than by where it sits: USDT held in three wallets is one
 * line with one number, and the wallets are underneath it for anyone who needs the breakdown. The
 * subtotal follows the display currency the headline uses, so the two can never disagree.
 */
@Composable
internal fun CryptoPortfolioSection(
    portfolio: CryptoPortfolio,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRotateCurrency: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val walletCount = portfolio.assets.flatMap { group ->
        group.holdings.map { it.address ?: it.walletName }
    }.distinct().size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Crypto is a peer of the bank sections, not a bigger thing: it carries the same caps label
        // they do. Set as an editorial heading it read as the more important half of the screen.
        WhfinSectionLabel(
            stringResource(R.string.crypto_section),
            icon = Icons.Outlined.CurrencyBitcoin,
        )
        // The subtotal is already inside the balance at the top of the screen, so it is a reading of
        // this section, not a second headline: it sits on the section's own line, at the section's
        // own weight, where it can still be tapped to change display currency.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildList {
                    add(pluralStringResource(R.plurals.crypto_wallet_count, walletCount, walletCount))
                    // Freshness is the whole story of a watch-only number, so it is never implied.
                    portfolio.lastObservedAt?.let { add(relativeTime(it)) }
                        ?: add(stringResource(R.string.crypto_never_refreshed))
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CryptoSubtotal(portfolio.total, onRotateCurrency)
            WhfinIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.crypto_refresh),
                onClick = onRefresh,
                outlined = false,
                enabled = !refreshing,
            )
        }
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            portfolio.assets.forEachIndexed { index, group ->
                CryptoAssetRow(
                    group = group,
                    displayCurrency = portfolio.displayCurrency,
                    expanded = group.key in expanded,
                    onToggle = {
                        expanded = if (group.key in expanded) expanded - group.key else expanded + group.key
                    },
                )
                AnimatedVisibility(visible = group.key in expanded) {
                    Column(Modifier.fillMaxWidth()) {
                        group.holdings.forEach { holding ->
                            CryptoHoldingRow(holding) { onOpenHolding(holding.accountId) }
                        }
                    }
                }
                if (index != portfolio.assets.lastIndex) HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                )
            }
        }
    }
}

/** Stable per-row identity: a ticker can appear twice when two chains disagree about decimals. */
private val CryptoAssetGroup.key: String get() = "$symbol-$decimals"

@Composable
private fun CryptoSubtotal(total: ConvertedTotal?, onRotateCurrency: () -> Unit) {
    // Nothing priced means nothing to total: a zero here would read as an empty wallet rather than
    // as a missing quote.
    val amount = total?.amount?.takeUnless { it.signum() == 0 && total.missing.isNotEmpty() }
    WhfinAmount(
        if (amount == null) "—" else formatDecimal(amount, total.currency),
        symbol = total?.currency?.let(::currencySymbol),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.clickable(onClick = onRotateCurrency).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun CryptoAssetRow(
    group: CryptoAssetGroup,
    displayCurrency: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // No marker: the currency rows above dropped theirs, and a bar that says nothing about the
        // asset was decoration standing where a meaning should be.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                group.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                assetSupportingText(group),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Never read is not zero: an unread pile shows a dash and says so underneath.
            WhfinAmount(
                if (group.baseUnits == null) "—" else formatBaseUnits(group.baseUnits.toString(), group.decimals),
                style = MaterialTheme.typography.titleMedium,
            )
            group.converted?.let { converted ->
                Text(
                    stringResource(
                        R.string.crypto_approx_value,
                        formatDecimal(converted, displayCurrency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun assetSupportingText(group: CryptoAssetGroup): String = buildList {
    add(pluralStringResource(R.plurals.crypto_wallet_count, group.walletCount, group.walletCount))
    group.holdings.mapNotNull { it.networkName }.distinct().takeIf { it.isNotEmpty() }
        ?.let { add(it.joinToString(", ")) }
    if (group.unreadCount > 0) add(stringResource(R.string.crypto_never_refreshed))
}.joinToString(" · ")

@Composable
private fun CryptoHoldingRow(holding: CryptoHolding, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 44.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                holding.walletName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(holding.networkName, holding.address?.let(::shortAddress)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        WhfinAmount(
            if (holding.baseUnits == null) "—" else formatBaseUnits(holding.baseUnits.toString(), holding.decimals),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** An address is verified elsewhere; here it only has to be recognisable. */
internal fun shortAddress(address: String): String =
    if (address.length <= 12) address else "${address.take(6)}…${address.takeLast(4)}"

@Preview(name = "Crypto section", widthDp = 400, showBackground = true)
@Preview(name = "Crypto section dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Crypto section font 1.5", widthDp = 400, fontScale = 1.5f, showBackground = true)
@Composable
private fun CryptoPortfolioSectionPreview() {
    val usdt = CryptoAssetGroup(
        symbol = "USDT",
        decimals = 6,
        baseUnits = BigInteger("1834260000"),
        converted = BigDecimal("4962.13"),
        holdings = listOf(
            CryptoHolding(
                1, "Tron daily", "Tron", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", "USDT", 6,
                BigInteger("1200000000"), 1_770_000_000_000, BigDecimal("3246.00"),
            ),
            CryptoHolding(
                2, "Cold wallet", "Ethereum", "0x0000000000000000000000000000000000000001", "USDT", 6,
                BigInteger("634260000"), 1_770_000_000_000, BigDecimal("1716.13"),
            ),
        ),
    )
    val trx = CryptoAssetGroup(
        symbol = "TRX",
        decimals = 6,
        baseUnits = BigInteger("512400000"),
        converted = BigDecimal("341.20"),
        holdings = listOf(
            CryptoHolding(
                3, "Tron daily", "Tron", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", "TRX", 6,
                BigInteger("512400000"), 1_770_000_000_000, BigDecimal("341.20"),
            ),
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CryptoPortfolioSection(
                portfolio = CryptoPortfolio(
                    assets = listOf(usdt, trx),
                    total = ConvertedTotal("GEL", BigDecimal("5303.33"), emptySet(), 1_770_000_000_000),
                    lastObservedAt = 1_770_000_000_000,
                    displayCurrency = "GEL",
                ),
                refreshing = false,
                onRefresh = {},
                onRotateCurrency = {},
                onOpenHolding = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
