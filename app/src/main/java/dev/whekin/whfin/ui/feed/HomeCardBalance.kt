package dev.whekin.whfin.ui.feed

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
import dev.whekin.whfin.core.ui.WhfinLedgerRow
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
            val masks = balance.cardLast4s.joinToString(" · ") { "••$it" }
            WhfinLedgerRow(
                modifier = Modifier.testTag("home-card-${balance.accountId}"),
                title = stringResource(R.string.home_card_balance_identity, balance.bankName ?: balance.accountName, masks),
                titleMaxLines = Int.MAX_VALUE,
                supportingText = stringResource(if (critical) R.string.home_card_balance_critical else R.string.home_card_balance_low),
                supportingMaxLines = Int.MAX_VALUE,
                icon = Icons.Outlined.CreditCard,
                iconTint = accent,
                trailing = {
                    WhfinAmount(formatMinor(balance.balanceMinor, "GEL"), symbol = currencySymbol("GEL"),
                        color = accent, style = MaterialTheme.typography.titleLarge)
                },
                onClick = onOpenAccounts,
            )
            val bank = balance.bankApp?.takeIf { !demo && isBankLaunchable(it) }
            if (bank != null) {
                var launchFailed by remember(bank) { mutableStateOf(false) }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    WhfinButton(
                        label = stringResource(R.string.home_card_open_bank, bank.displayName),
                        onClick = { launchFailed = !onOpenBank(bank) },
                        style = WhfinActionStyle.Quiet,
                        leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    )
                    if (launchFailed) Text(stringResource(R.string.home_card_bank_unavailable),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            if (index != shown.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant)
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
