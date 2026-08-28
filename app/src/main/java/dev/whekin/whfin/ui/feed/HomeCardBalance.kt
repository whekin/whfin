package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.notifications.PhysicalCardBalanceStatus
import dev.whekin.whfin.data.notifications.physicalCardBalanceStatus
import dev.whekin.whfin.ui.bank.SupportedBankApp
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.demo.isDemoWorkspaceActive
import dev.whekin.whfin.ui.formatMinor

/** Card identity and balance lead; navigation, bank launch and alert consent are distinct actions. */
@Composable
internal fun HomePhysicalCardBalance(
    balances: List<PhysicalCardHomeBalance>,
    notificationsEnabled: Boolean,
    onOpenAccounts: () -> Unit,
    onEnableNotifications: () -> Unit,
    isBankLaunchable: (SupportedBankApp) -> Boolean = { false },
    onOpenBank: (SupportedBankApp) -> Boolean = { false },
) {
    val demo = isDemoWorkspaceActive()
    val shown = balances.filter { physicalCardBalanceStatus(it.balanceMinor) != PhysicalCardBalanceStatus.Enough }.take(2)
    if (shown.isEmpty()) return
    WhfinLedgerGroup(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("home-card-balance")) {
        shown.forEachIndexed { index, balance ->
            val critical = physicalCardBalanceStatus(balance.balanceMinor) == PhysicalCardBalanceStatus.Critical
            val accent = if (critical) MaterialTheme.colorScheme.error else WhfinThemeTokens.colors.warning
            val masks = balance.cardLast4s.joinToString(" · ") { "\u2022\u2022$it" }
            val bank = balance.bankApp?.takeIf { !demo && isBankLaunchable(it) }
            var launchFailed by remember(bank) { mutableStateOf(false) }
            // A shared ledger row centres its trailing value against the whole text block. That is
            // right for one or two lines and wrong here: the sentence explaining the risk wraps, so
            // the balance floated against the middle of a paragraph and the second line ran on
            // underneath it, leaving a notch where the number should have been anchored.
            //
            // The card is built to its own shape instead — the balance is pinned to the line that
            // names the card, the sentence spans the full width beneath both, and the bank action
            // starts on the same left edge as the text rather than on a third one.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccounts)
                    .testTag("home-card-${balance.accountId}")
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(WhfinThemeTokens.sizes.iconContainer)
                            .background(accent.copy(alpha = .11f), MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CreditCard,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.home_card_balance_identity,
                                    balance.bankName ?: balance.accountName,
                                    masks,
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            WhfinAmount(
                                formatMinor(balance.balanceMinor, "GEL"),
                                symbol = currencySymbol("GEL"),
                                color = accent,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            stringResource(
                                if (critical) R.string.home_card_balance_critical
                                else R.string.home_card_balance_low,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (bank != null) {
                            WhfinButton(
                                label = stringResource(R.string.home_card_open_bank, bank.displayName),
                                onClick = { launchFailed = !onOpenBank(bank) },
                                style = WhfinActionStyle.Quiet,
                                leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (launchFailed) Text(
                                stringResource(R.string.home_card_bank_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (index != shown.lastIndex) HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (!notificationsEnabled && !demo) {
            WhfinButton(
                label = stringResource(R.string.home_card_balance_enable_alerts),
                onClick = onEnableNotifications,
                style = WhfinActionStyle.Quiet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
