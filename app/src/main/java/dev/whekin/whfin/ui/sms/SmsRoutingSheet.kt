package dev.whekin.whfin.ui.sms

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.sms.isCurrencyExchangeLedger
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme

data class SmsRoutingAccount(
    val account: AccountEntity,
    val groupName: String?,
) {
    val label: String
        get() = listOfNotNull(groupName, account.name.takeUnless { it == groupName })
            .joinToString(" · ")
}

internal data class SmsRoutingPair(
    val from: SmsRoutingAccount,
    val to: SmsRoutingAccount,
)

/** Candidate movements are derived from current ledgers and deliberately never persisted as scenarios. */
internal fun groupedRoutingPairs(
    diagnostic: SmsDiagnosticEntity,
    accounts: List<SmsRoutingAccount>,
): List<SmsRoutingPair> {
    val sourceCurrency = diagnostic.currency ?: return emptyList()
    val destinationCurrency = if (diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE) {
        diagnostic.secondaryCurrency ?: return emptyList()
    } else {
        sourceCurrency
    }
    val isExchange = diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
    val eligible: (AccountEntity) -> Boolean = if (isExchange) {
        ::isCurrencyExchangeLedger
    } else {
        { it.type in setOf(AccountType.BANK, AccountType.SAVINGS) }
    }
    val sources = accounts.filter { option ->
        option.account.currency == sourceCurrency &&
            eligible(option.account) &&
            (diagnostic.fromIban == null || option.account.iban == diagnostic.fromIban)
    }
    val destinations = accounts.filter { option ->
        option.account.currency == destinationCurrency &&
            eligible(option.account) &&
            (diagnostic.toIban == null || option.account.iban == diagnostic.toIban)
    }
    return sources.flatMap { from ->
        destinations.mapNotNull { to ->
            SmsRoutingPair(from, to).takeIf {
                from.account.id != to.account.id &&
                    from.account.groupId != null &&
                    from.account.groupId == to.account.groupId
            }
        }
    }
}

@Composable
fun SmsRoutingSheet(
    diagnostic: SmsDiagnosticEntity,
    accounts: List<SmsRoutingAccount>,
    onDismiss: () -> Unit,
    onResolve: (Long, PaymentInstrumentType) -> Unit,
    onResolveGroup: (Long, Long) -> Unit,
    onCreateAccount: (String, String, PaymentInstrumentType) -> Unit,
    onAddGroupedAccount: (String, String) -> Unit,
) {
    val currency = diagnostic.balanceCurrency ?: diagnostic.currency ?: "—"
    val matching = remember(accounts, currency) {
        accounts.filter { it.account.currency == currency }
    }
    val grouped = diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
        diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
    val sourceCurrency = diagnostic.currency ?: "—"
    val destinationCurrency = if (diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE) {
        diagnostic.secondaryCurrency ?: "—"
    } else {
        sourceCurrency
    }
    val groupedPairs = remember(diagnostic, accounts) { groupedRoutingPairs(diagnostic, accounts) }
    var selectedId by rememberSaveable(diagnostic.id, matching) {
        mutableLongStateOf(matching.singleOrNull()?.account?.id ?: 0L)
    }
    var cardType by rememberSaveable(diagnostic.id) {
        mutableStateOf(PaymentInstrumentType.PHYSICAL_CARD)
    }
    var creatingCurrency by rememberSaveable(diagnostic.id) { mutableStateOf<String?>(null) }
    var accountName by rememberSaveable(diagnostic.id) { mutableStateOf("") }
    val primaryEnabled = when {
        creatingCurrency != null -> accountName.isNotBlank()
        grouped -> true
        else -> selectedId != 0L
    }

    WhfinFormSheet(
        title = stringResource(
            when {
                creatingCurrency != null -> R.string.sms_routing_new_account_title
                grouped -> R.string.sms_routing_accounts_title
                else -> R.string.sms_routing_title
            },
        ),
        onDismiss = onDismiss,
        primaryLabel = stringResource(
            if (creatingCurrency != null) {
                R.string.sms_create_and_link_action
            } else if (grouped) {
                R.string.sms_close_action
            } else {
                R.string.sms_link_and_confirm_action
            },
        ),
        primaryEnabled = primaryEnabled,
        onPrimary = {
            when {
                creatingCurrency != null && grouped -> {
                    onAddGroupedAccount(accountName.trim(), requireNotNull(creatingCurrency))
                    accountName = ""
                    creatingCurrency = null
                }
                creatingCurrency != null ->
                    onCreateAccount(accountName.trim(), requireNotNull(creatingCurrency), cardType)
                grouped -> onDismiss()
                selectedId != 0L -> onResolve(selectedId, cardType)
            }
        },
    ) {
        if (!grouped) {
            Text(
                stringResource(
                    if (diagnostic.cardLast4 != null) {
                        R.string.sms_card_routing_resolver_body
                    } else {
                        R.string.sms_routing_resolver_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        diagnostic.amountMinor?.let { amount ->
            WhfinAmount(
                text = formatMinor(kotlin.math.abs(amount), diagnostic.currency ?: currency),
                symbol = currencySymbol(diagnostic.currency ?: currency),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (
            grouped &&
            diagnostic.secondaryAmountMinor != null &&
            diagnostic.secondaryCurrency != null
        ) {
            WhfinAmount(
                text = "→ ${formatMinor(
                    diagnostic.secondaryAmountMinor,
                    diagnostic.secondaryCurrency,
                )}",
                symbol = currencySymbol(diagnostic.secondaryCurrency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (creatingCurrency != null) {
            WhfinField(
                value = accountName,
                onValueChange = { accountName = it.take(40) },
                label = stringResource(R.string.account_name_in_bank),
                supportingText = stringResource(
                    R.string.sms_new_account_support,
                    requireNotNull(creatingCurrency),
                ),
                leadingIcon = Icons.Default.AccountBalance,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (grouped) {
            WhfinNotice(
                title = stringResource(R.string.sms_choose_pair_title),
                body = stringResource(R.string.sms_grouped_routing_pair_body),
                kind = WhfinNoticeKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
            if (groupedPairs.isNotEmpty()) {
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    groupedPairs.forEachIndexed { index, pair ->
                        WhfinLedgerRow(
                            title = "${pair.from.account.name} → ${pair.to.account.name}",
                            supportingText = listOf(
                                listOfNotNull(
                                    pair.from.account.iban?.takeLast(4)?.let { "••$it" },
                                    pair.from.account.currency,
                                ).joinToString(" · "),
                                listOfNotNull(
                                    pair.to.account.iban?.takeLast(4)?.let { "••$it" },
                                    pair.to.account.currency,
                                ).joinToString(" · "),
                            ).joinToString(" → "),
                            icon = Icons.Default.AccountBalance,
                            onClick = {
                                onResolveGroup(pair.from.account.id, pair.to.account.id)
                            },
                            divider = index != groupedPairs.lastIndex,
                        )
                    }
                }
            }
            WhfinChoiceRail {
                item {
                    WhfinButton(
                        label = stringResource(R.string.sms_add_currency_account, sourceCurrency),
                        onClick = { creatingCurrency = sourceCurrency },
                        style = WhfinActionStyle.Quiet,
                        leadingIcon = Icons.Default.Add,
                    )
                }
                if (destinationCurrency != sourceCurrency) item {
                    WhfinButton(
                        label = stringResource(R.string.sms_add_currency_account, destinationCurrency),
                        onClick = { creatingCurrency = destinationCurrency },
                        style = WhfinActionStyle.Quiet,
                        leadingIcon = Icons.Default.Add,
                    )
                }
            }
        } else if (matching.isEmpty()) {
            WhfinNotice(
                title = stringResource(R.string.sms_no_bank_accounts_title),
                body = stringResource(R.string.sms_no_matching_accounts, currency),
                kind = WhfinNoticeKind.Unavailable,
                icon = Icons.Default.AccountBalance,
                actionLabel = stringResource(R.string.accounts_add),
                onAction = { creatingCurrency = currency },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WhfinFieldLabel(stringResource(R.string.sms_account_for_operation, currency))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                matching.forEachIndexed { index, option ->
                    WhfinLedgerRow(
                        title = option.label,
                        supportingText = listOfNotNull(
                            option.account.iban?.takeLast(4)?.let { "••$it" },
                            option.account.currency,
                        ).joinToString(" · "),
                        icon = if (diagnostic.cardLast4 != null) {
                            Icons.Default.CreditCard
                        } else {
                            Icons.Default.AccountBalance
                        },
                        trailing = if (selectedId == option.account.id) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = { selectedId = option.account.id },
                        divider = index != matching.lastIndex,
                    )
                }
            }
        }
        if (!grouped && diagnostic.cardLast4 != null) {
            WhfinFieldLabel(stringResource(R.string.sms_card_type))
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.sms_card_physical),
                        selected = cardType == PaymentInstrumentType.PHYSICAL_CARD,
                        onClick = { cardType = PaymentInstrumentType.PHYSICAL_CARD },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.sms_card_virtual),
                        selected = cardType == PaymentInstrumentType.VIRTUAL_CARD,
                        onClick = { cardType = PaymentInstrumentType.VIRTUAL_CARD },
                    )
                }
            }
        }
    }
}

private val previewDiagnostic = SmsDiagnosticEntity(
    id = 31,
    externalKey = "preview-unrouted",
    kind = SmsDiagnosticKind.CARD_PAYMENT,
    outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
    receivedAt = System.currentTimeMillis(),
    occurredAt = System.currentTimeMillis(),
    amountMinor = 7_490,
    currency = "USD",
    balanceCurrency = "GEL",
    cardLast4 = "0001",
    counterparty = "OPENAI *CHATGPT SUBSCR",
    updatedAt = System.currentTimeMillis(),
)

private val previewRoutingAccounts = listOf(
    SmsRoutingAccount(
        AccountEntity(
            id = 1,
            name = "Everyday",
            type = AccountType.BANK,
            groupId = 1,
            currency = "GEL",
            iban = "GE00CD0000000000000001",
        ),
        groupName = "Credo",
    ),
)

private val previewExchangeDiagnostic = SmsDiagnosticEntity(
    id = 32,
    externalKey = "preview-exchange",
    kind = SmsDiagnosticKind.CURRENCY_EXCHANGE,
    outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
    receivedAt = System.currentTimeMillis(),
    occurredAt = System.currentTimeMillis(),
    amountMinor = 5_000,
    currency = "GEL",
    secondaryAmountMinor = 1_800,
    secondaryCurrency = "USD",
    balanceMinor = 1_800,
    balanceCurrency = "USD",
    updatedAt = System.currentTimeMillis(),
)

private val previewExchangeAccounts = listOf(
    previewRoutingAccounts.single(),
    SmsRoutingAccount(
        AccountEntity(
            id = 2,
            name = "Dollar",
            type = AccountType.BANK,
            groupId = 1,
            currency = "USD",
        ),
        groupName = "Credo",
    ),
)

@Preview(name = "SMS routing", widthDp = 400, heightDp = 700, showBackground = true)
@Preview(
    name = "SMS routing dark font 1.5",
    widthDp = 400,
    heightDp = 800,
    fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SmsRoutingSheetPreview() {
    WhfinTheme {
        SmsRoutingSheet(
            diagnostic = previewDiagnostic,
            accounts = previewRoutingAccounts,
            onDismiss = {},
            onResolve = { _, _ -> },
            onResolveGroup = { _, _ -> },
            onCreateAccount = { _, _, _ -> },
            onAddGroupedAccount = { _, _ -> },
        )
    }
}

@Preview(
    name = "Grouped SMS routing dark font 1.5",
    widthDp = 400,
    heightDp = 800,
    fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GroupedSmsRoutingSheetPreview() {
    WhfinTheme {
        SmsRoutingSheet(
            diagnostic = previewExchangeDiagnostic,
            accounts = previewExchangeAccounts,
            onDismiss = {},
            onResolve = { _, _ -> },
            onResolveGroup = { _, _ -> },
            onCreateAccount = { _, _, _ -> },
            onAddGroupedAccount = { _, _ -> },
        )
    }
}
