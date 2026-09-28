package dev.whekin.whfin.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinLoadingIndicator
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.PaymentInstrumentType

/** The bank's contracts and the owner's choices, one row per IBAN rather than per currency. */
@Composable
internal fun SetupBankAccountsContent(
    state: SetupOverviewState,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    when (state) {
        SetupOverviewState.Loading -> WhfinLoadingIndicator()
        SetupOverviewState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_read_failed))
            WhfinButton(stringResource(R.string.action_retry), onRetry, style = WhfinActionStyle.Secondary)
        }
        is SetupOverviewState.Ready -> {
            val containers = state.value.bankContainers
            if (containers.isEmpty()) Text(stringResource(R.string.setup_bank_accounts_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    containers.forEachIndexed { index, container ->
                        val product = stringResource(when (container.bankProduct) {
                            BankProduct.CURRENT_ACCOUNT -> R.string.account_product_current
                            BankProduct.DEMAND_DEPOSIT -> R.string.account_product_demand_deposit
                            BankProduct.TERM_DEPOSIT -> R.string.account_product_term_deposit
                            null -> R.string.setup_bank_product_missing
                        })
                        val role = stringResource(when (container.fundRole) {
                            FundRole.AVAILABLE -> R.string.account_fund_available
                            FundRole.RESERVE -> R.string.account_purpose_reserve
                            null -> R.string.setup_bank_role_mixed
                        })
                        val physical = stringResource(R.string.account_card_physical)
                        val virtual = stringResource(R.string.account_card_virtual)
                        val unclassified = stringResource(R.string.account_card_unclassified)
                        val primary = stringResource(R.string.account_card_primary)
                        val cards = if (container.cards.isEmpty()) stringResource(R.string.setup_bank_cards_missing)
                            else container.cards.joinToString(" · ") { card ->
                                val kind = when (card.type) {
                                    PaymentInstrumentType.PHYSICAL_CARD -> physical
                                    PaymentInstrumentType.VIRTUAL_CARD -> virtual
                                    PaymentInstrumentType.UNCLASSIFIED_CARD -> unclassified
                                }
                                "$kind •${card.last4}" + if (card.isPrimary) " · $primary" else ""
                            }
                        val reviewRole = container.bankProduct in setOf(
                            BankProduct.DEMAND_DEPOSIT, BankProduct.TERM_DEPOSIT) &&
                            container.fundRole == FundRole.AVAILABLE
                        val roleHint = stringResource(R.string.setup_deposit_role_hint)
                        val supporting = buildList {
                            add("$product · $role")
                            add(cards)
                            if (reviewRole) add(roleHint)
                        }.joinToString("\n")
                        WhfinLedgerRow(
                            title = stringResource(R.string.setup_bank_account_label, container.bank.provider,
                                container.representative.iban?.takeLast(4) ?: container.representative.name),
                            supportingText = supporting,
                            supportingMaxLines = 4,
                            onClick = { onOpen(container.key) },
                            trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            divider = index < containers.lastIndex,
                        )
                    }
                }
                if (containers.any { it.cards.isNotEmpty() } && containers.none { group ->
                        group.cards.any { it.isPrimary }
                    }) Text(stringResource(R.string.setup_primary_card_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
