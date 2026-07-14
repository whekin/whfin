package dev.whekin.whfin.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice

private val quickCurrencies = listOf("GEL", "USD", "EUR", "RUB")
private val cryptoQuickCurrencies = listOf("USDT", "BTC", "ETH", "TON")

@Composable
fun AddAccountSheet(
    onDismiss: () -> Unit,
    onImportStatement: () -> Unit,
    onConfirm: (name: String, type: AccountType, currency: String, address: String?, bankProvider: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("GEL") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var address by remember { mutableStateOf("") }
    var customBank by remember { mutableStateOf(false) }
    var bankProvider by remember { mutableStateOf<String?>(null) }

    FormSheet(
        title = stringResource(R.string.accounts_add),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = name.isNotBlank() && currency.isNotBlank() &&
            (type != AccountType.CRYPTO || address.isNotBlank()),
        onPrimary = { onConfirm(name, type, currency, address.trim().takeIf(String::isNotEmpty), bankProvider) },
    ) {
        TypeSelector(
            selected = type,
            onSelect = {
                type = it
                // Осмысленный дефолт валюты при смене типа
                currency = if (it == AccountType.CRYPTO) "USDT" else "GEL"
            },
        )
        if (type == AccountType.BANK) {
            WhfinNotice(
                title = stringResource(R.string.account_create_from_statement_title),
                body = stringResource(R.string.account_create_from_statement_summary),
                actionLabel = stringResource(R.string.statements_upload),
                onAction = onImportStatement,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.account_bank_provider), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Credo", "TBC").forEach { bank ->
                    FilterChip(
                        selected = !customBank && bankProvider == bank,
                        onClick = { customBank = false; bankProvider = bank; if (name.isBlank()) name = bank },
                        label = { Text(bank) },
                    )
                }
                FilterChip(
                    selected = customBank,
                    onClick = { customBank = true; bankProvider = null; name = "" },
                    label = { Text(stringResource(R.string.account_bank_other)) },
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(if (type == AccountType.BANK) R.string.account_name_in_bank else R.string.account_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        CurrencySelector(
            currency = currency,
            onChange = { currency = it },
            quick = if (type == AccountType.CRYPTO) cryptoQuickCurrencies else quickCurrencies,
        )
        if (type == AccountType.CRYPTO) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filterNot(Char::isWhitespace) },
                label = { Text(stringResource(R.string.account_address)) },
                supportingText = { Text(stringResource(R.string.account_address_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Редактирование существующего счёта: имя и валюта (например, сменить валюту кеша). */
@Composable
fun EditAccountSheet(
    account: AccountEntity,
    initialAddress: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, currency: String, address: String?) -> Unit,
) {
    var name by remember { mutableStateOf(account.name) }
    var currency by remember { mutableStateOf(account.currency) }
    var address by remember { mutableStateOf(initialAddress.orEmpty()) }

    FormSheet(
        title = stringResource(R.string.account_edit),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = name.isNotBlank() && currency.isNotBlank(),
        onPrimary = { onConfirm(name, currency, address.trim().takeIf(String::isNotEmpty)) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.account_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        CurrencySelector(
            currency = currency,
            onChange = { currency = it },
            quick = if (account.type == AccountType.CRYPTO) cryptoQuickCurrencies else quickCurrencies,
        )
        if (account.type == AccountType.CRYPTO) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filterNot(Char::isWhitespace) },
                label = { Text(stringResource(R.string.account_address)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BankMappingSheet(
    account: AccountEntity,
    existingCards: List<String>,
    existingVirtualCards: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String?, List<String>, Boolean) -> Unit,
) {
    var iban by remember { mutableStateOf(account.iban.orEmpty()) }
    var cards by remember(account.id, existingCards, existingVirtualCards) {
        mutableStateOf((existingCards + existingVirtualCards).distinct().joinToString(", "))
    }
    var isVirtual by remember(account.id, existingCards, existingVirtualCards) {
        mutableStateOf(existingVirtualCards.isNotEmpty() && existingCards.isEmpty())
    }

    FormSheet(
        title = stringResource(R.string.account_bank_mapping),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = true,
        onPrimary = {
            onConfirm(
                iban.trim().takeIf(String::isNotEmpty),
                cards.split(',', ' ').map(String::trim).filter { it.length == 4 },
                isVirtual,
            )
        },
    ) {
        Text(
            account.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = iban,
            onValueChange = { iban = it.uppercase().filterNot(Char::isWhitespace) },
            label = { Text(stringResource(R.string.account_iban)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cards,
            onValueChange = { cards = it.filter { ch -> ch.isDigit() || ch == ',' || ch == ' ' } },
            label = { Text(stringResource(R.string.account_card_last4)) },
            supportingText = { Text(stringResource(R.string.account_card_last4_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.account_card_kind), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isVirtual,
                onClick = { isVirtual = false },
                label = { Text(stringResource(R.string.account_card_physical)) },
            )
            FilterChip(
                selected = isVirtual,
                onClick = { isVirtual = true },
                label = { Text(stringResource(R.string.account_card_virtual)) },
            )
        }
    }
}

private data class TypeOption(val type: AccountType, val icon: ImageVector)

private val typeOptions = listOf(
    TypeOption(AccountType.BANK, Icons.Default.AccountBalance),
    TypeOption(AccountType.CASH, Icons.Default.Payments),
    TypeOption(AccountType.SAVINGS, Icons.Default.Savings),
    TypeOption(AccountType.CRYPTO, Icons.Default.CurrencyBitcoin),
)

/** Крупные тап-таргеты вместо мелких чипов: иконка + подпись, ряд карточек. */
@Composable
private fun TypeSelector(selected: AccountType, onSelect: (AccountType) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        typeOptions.forEach { option ->
            val isSelected = selected == option.type
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                border = if (isSelected) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option.type) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 14.dp),
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        accountTypeLabel(option.type),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Частые валюты одним тапом + чип «Другая» раскрывает поле для любого кода/тикера. */
@Composable
private fun CurrencySelector(
    currency: String,
    onChange: (String) -> Unit,
    quick: List<String> = quickCurrencies,
) {
    val isCustom = currency !in quick
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            quick.forEach { code ->
                FilterChip(
                    selected = currency == code,
                    onClick = { onChange(code) },
                    label = { Text(code) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
            FilterChip(
                selected = isCustom,
                onClick = { if (!isCustom) onChange("") },
                label = { Text(stringResource(R.string.account_currency_other)) },
            )
        }
        if (isCustom) {
            OutlinedTextField(
                value = currency,
                onValueChange = { onChange(it.take(6).uppercase().filter(Char::isLetterOrDigit)) },
                label = { Text(stringResource(R.string.account_currency)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Меню действий по счёту: корректировка баланса (все) + реквизиты (банк). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountActionsSheet(
    item: AccountWithBalance,
    onDismiss: () -> Unit,
    onAdjustBalance: () -> Unit,
    onBankMapping: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleReserve: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                item.account.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                ActionRow(
                    icon = Icons.Default.Tune,
                    label = stringResource(R.string.action_adjust_balance),
                    onClick = onAdjustBalance,
                    divider = true,
                )
                ActionRow(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.account_edit),
                    onClick = onEdit,
                    divider = true,
                )
                if (item.account.type == AccountType.BANK || item.account.type == AccountType.SAVINGS) {
                ActionRow(
                    icon = Icons.Default.Savings,
                    label = stringResource(
                        if (item.account.savingsMode == null) R.string.account_mark_reserve
                        else R.string.account_remove_reserve,
                    ),
                    onClick = onToggleReserve,
                    divider = true,
                )
                }
                if (item.account.type == AccountType.BANK) {
                ActionRow(
                    icon = Icons.Default.AccountBalance,
                    label = stringResource(R.string.account_bank_mapping),
                    onClick = onBankMapping,
                    divider = true,
                )
                }
                ActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = stringResource(R.string.account_delete),
                    onClick = onDelete,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    divider: Boolean = false,
    destructive: Boolean = false,
) {
    WhfinLedgerRow(
        title = label,
        icon = icon,
        iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        titleColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
        divider = divider,
    )
}

internal fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.BANK -> Icons.Default.AccountBalance
    AccountType.CASH -> Icons.Default.Payments
    AccountType.SAVINGS -> Icons.Default.Savings
    AccountType.CRYPTO -> Icons.Default.CurrencyBitcoin
    AccountType.PERSON -> Icons.Default.Payments
}

@Composable
internal fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.BANK -> R.string.account_type_bank
        AccountType.CASH -> R.string.account_type_cash
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CRYPTO -> R.string.account_type_crypto
        AccountType.PERSON -> R.string.account_type_person
    },
)
