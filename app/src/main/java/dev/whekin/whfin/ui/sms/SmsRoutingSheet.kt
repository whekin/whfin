package dev.whekin.whfin.ui.sms

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
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
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
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

@Composable
fun SmsRoutingSheet(
    diagnostic: SmsDiagnosticEntity,
    accounts: List<SmsRoutingAccount>,
    onDismiss: () -> Unit,
    onResolve: (Long, PaymentInstrumentType) -> Unit,
    onCreateAccount: (String, String, PaymentInstrumentType) -> Unit,
) {
    val currency = diagnostic.balanceCurrency ?: diagnostic.currency ?: "—"
    val matching = remember(accounts, currency) {
        accounts.filter { it.account.currency == currency }
    }
    val grouped = diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
        diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
    var selectedId by rememberSaveable(diagnostic.id, matching) {
        mutableLongStateOf(matching.singleOrNull()?.account?.id ?: 0L)
    }
    var cardType by rememberSaveable(diagnostic.id) {
        mutableStateOf(PaymentInstrumentType.PHYSICAL_CARD)
    }
    var creatingAccount by rememberSaveable(diagnostic.id) { mutableStateOf(false) }
    var accountName by rememberSaveable(diagnostic.id) { mutableStateOf("") }
    val primaryEnabled = when {
        grouped -> false
        creatingAccount -> accountName.isNotBlank()
        else -> selectedId != 0L
    }

    WhfinFormSheet(
        title = stringResource(
            when {
                grouped -> R.string.sms_routing_accounts_title
                creatingAccount -> R.string.sms_routing_new_account_title
                else -> R.string.sms_routing_title
            },
        ),
        onDismiss = onDismiss,
        primaryLabel = stringResource(
            if (creatingAccount) R.string.sms_create_and_link_action else R.string.action_done,
        ),
        primaryEnabled = primaryEnabled,
        onPrimary = {
            when {
                grouped -> Unit
                creatingAccount -> onCreateAccount(accountName.trim(), currency, cardType)
                selectedId != 0L -> onResolve(selectedId, cardType)
            }
        },
    ) {
        Text(
            stringResource(R.string.sms_routing_resolver_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        diagnostic.amountMinor?.let { amount ->
            WhfinAmount(
                text = formatMinor(kotlin.math.abs(amount), diagnostic.currency ?: currency),
                symbol = currencySymbol(diagnostic.currency ?: currency),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (grouped) {
            WhfinNotice(
                title = stringResource(R.string.sms_choose_accounts_title),
                body = stringResource(R.string.sms_grouped_routing_pending_body),
                kind = WhfinNoticeKind.Attention,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (matching.isEmpty() && !creatingAccount) {
            WhfinNotice(
                title = stringResource(R.string.sms_no_bank_accounts_title),
                body = stringResource(R.string.sms_no_matching_accounts, currency),
                kind = WhfinNoticeKind.Unavailable,
                icon = Icons.Default.AccountBalance,
                actionLabel = stringResource(R.string.accounts_add),
                onAction = { creatingAccount = true },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (creatingAccount) {
            WhfinField(
                value = accountName,
                onValueChange = { accountName = it.take(40) },
                label = stringResource(R.string.account_name_in_bank),
                supportingText = stringResource(R.string.sms_new_account_support, currency),
                leadingIcon = Icons.Default.AccountBalance,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WhfinSectionLabel(stringResource(R.string.sms_account_for_operation, currency))
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
            WhfinSectionLabel(stringResource(R.string.sms_card_type))
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
            currency = "GEL",
            iban = "GE00CD0000000000000001",
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
            onCreateAccount = { _, _, _ -> },
        )
    }
}
