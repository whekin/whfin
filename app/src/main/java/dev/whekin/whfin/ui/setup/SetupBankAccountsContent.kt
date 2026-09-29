package dev.whekin.whfin.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.core.ui.WhfinAmount
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
                        Column(Modifier.fillMaxWidth().clickable { onOpen(container.key) }) {
                            WhfinLedgerRow(
                                title = container.representative.name.ifBlank { container.bank.provider },
                                supportingText = listOfNotNull(container.bank.provider, container.representative.iban?.takeLast(4)?.let { "•$it" }).joinToString(" · "),
                                supportingMaxLines = Int.MAX_VALUE,
                                trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                divider = false,
                            )
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                container.accounts.sortedBy { it.currency != "GEL" }.forEach { account ->
                                    val review = container.reviews.firstOrNull { it.account.id == account.id }
                                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(account.currency, style = MaterialTheme.typography.bodyLarge)
                                        WhfinAmount(review?.let { formatMinor(it.balance, account.currency) }
                                            ?: stringResource(R.string.setup_balance_unknown),
                                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                            style = MaterialTheme.typography.titleLarge, maxLines = 2)
                                    }
                                }
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$product · $role", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (container.cards.isNotEmpty()) Text(cards, style = MaterialTheme.typography.bodySmall)
                                if (reviewRole) Text(roleHint, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < containers.lastIndex) androidx.compose.material3.HorizontalDivider()
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

@androidx.compose.ui.tooling.preview.Preview(name = "Setup accounts light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "Setup accounts dark", widthDp = 400, heightDp = 850,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Setup accounts RU compact", widthDp = 360, heightDp = 560,
    locale = "ru", fontScale = 1.5f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SetupAccountsBalancePreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    val account = dev.whekin.whfin.data.db.AccountEntity(id = 1, name = "Everyday", currency = "GEL",
        type = dev.whekin.whfin.data.db.AccountType.BANK, groupId = 1, iban = "GE00TB0000000000000001")
    val reviews = listOf(SetupAccountReview(account, "TBC", 128740L, null, null, null, null, 0, "one"),
        SetupAccountReview(account.copy(id = 2, currency = "USD"), "TBC", -500L, null, null, null, null, 0, "two"))
    val overview = SetupOverview(reviews, emptyMap(), emptySet(), 0, 0, 0, 0, 0, 0,
        bankContainers = listOf(SetupBankContainer("tbc", dev.whekin.whfin.data.sms.BankSmsBank.TBC,
            reviews.map { it.account }, emptyList(), reviews)))
    SetupStageScreen(SetupStage.Accounts, emptyList(), {}, {},
        continueLabel = stringResource(R.string.setup_to_categories),
        content = { SetupBankAccountsContent(SetupOverviewState.Ready(overview), {}, {}) })
}
