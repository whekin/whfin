package dev.whekin.whfin.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.R
import dev.whekin.whfin.data.crypto.CryptoAddressValidator
import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.ui.theme.WhfinTheme

private val quickCurrencies = listOf("GEL", "USD", "EUR", "RUB")

@Composable
fun AddAccountSheet(
    onDismiss: () -> Unit,
    onImportStatement: () -> Unit,
    onConfirm: (
        name: String,
        type: AccountType,
        currency: String,
        bankProvider: String?,
        openingMinor: Long?,
    ) -> Unit,
    /** Extended callback for callers that need to persist bank-product metadata on creation. */
    onConfirmWithProduct: (
        name: String,
        type: AccountType,
        currency: String,
        bankProvider: String?,
        openingMinor: Long?,
        bankProduct: BankProduct?,
    ) -> Unit = { name, type, currency, bankProvider, openingMinor, _ ->
        onConfirm(name, type, currency, bankProvider, openingMinor)
    },
    onConfirmWallet: (name: String?, network: CryptoNetwork, address: String) -> Unit = { _, _, _ -> },
    // Наличные — единственный тип, который заводится только руками: банк приходит из выписки или
    // MyCredo, а кошелёк требует адреса. Поэтому форма открывается на Cash.
    initialType: AccountType = AccountType.CASH,
    /** Setup can present the cash editor as its own step without offering unrelated account types. */
    cashOnly: Boolean = false,
    titleOverride: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var network by remember { mutableStateOf(CryptoNetwork.ETHEREUM) }
    var currency by remember { mutableStateOf("GEL") }
    var type by remember { mutableStateOf(initialType) }
    var address by remember { mutableStateOf("") }
    var customBank by remember { mutableStateOf(false) }
    var customCashName by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf("") }
    var bankProvider by remember { mutableStateOf<String?>(null) }
    var bankProduct by remember { mutableStateOf<BankProduct?>(null) }

    val addressCheck = if (type == AccountType.CRYPTO && address.isNotBlank()) {
        CryptoAddressValidator.check(network, address)
    } else {
        null
    }
    val addressProblem = (addressCheck as? CryptoAddressValidator.Result.Invalid)?.problem

    FormSheet(
        title = titleOverride ?: stringResource(R.string.accounts_add),
        onDismiss = onDismiss,
        primaryLabel = stringResource(
            if (type == AccountType.CRYPTO) R.string.crypto_wallet_track else R.string.action_save,
        ),
        primaryEnabled = if (type == AccountType.CRYPTO) {
            addressCheck is CryptoAddressValidator.Result.Valid
        } else {
            (type == AccountType.CASH || name.isNotBlank()) && currency.isNotBlank()
        },
        onPrimary = {
            if (type == AccountType.CRYPTO) {
                onConfirmWallet(name.trim().takeIf(String::isNotEmpty), network, address.trim())
            } else {
                onConfirmWithProduct(
                    name.ifBlank { "Cash" },
                    type,
                    currency,
                    bankProvider,
                    parseToMinor(opening),
                    bankProduct,
                )
            }
        },
    ) {
        if (!cashOnly) TypeSelector(
            selected = type,
            onSelect = {
                type = it
                currency = "GEL"
                // A bank's name is not a cash pile's name; switching type must not carry it across.
                name = ""
                customCashName = false
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
            WhfinChoiceRail {
                items(listOf("Credo", "TBC"), key = { it }) { bank ->
                    WhfinFilterPill(
                        label = bank,
                        selected = !customBank && bankProvider == bank,
                        onClick = { customBank = false; bankProvider = bank; if (name.isBlank()) name = bank },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.account_bank_other),
                        selected = customBank,
                        onClick = { customBank = true; bankProvider = null; name = "" },
                    )
                }
            }
            Text(stringResource(R.string.account_bank_product), style = MaterialTheme.typography.labelLarge)
            BankProductSelector(
                selected = bankProduct,
                onSelect = { bankProduct = it },
            )
        }
        if (type == AccountType.CRYPTO) {
            Text(stringResource(R.string.account_network), style = MaterialTheme.typography.labelLarge)
            WhfinChoiceRail {
                items(CryptoNetwork.entries, key = { it.chainId }) { option ->
                    WhfinFilterPill(
                        label = option.displayName,
                        selected = network == option,
                        onClick = { network = option },
                    )
                }
            }
            WhfinField(
                value = address,
                onValueChange = { address = it.filterNot(Char::isWhitespace) },
                label = stringResource(R.string.account_address),
                supportingText = when (addressProblem) {
                    CryptoAddressValidator.Problem.CHECKSUM ->
                        stringResource(R.string.account_address_checksum)
                    CryptoAddressValidator.Problem.FORMAT ->
                        stringResource(R.string.account_address_invalid, network.displayName)
                    // Assets are not a question for the person: the chain is asked which ones this
                    // address holds, and only those become ledgers.
                    else -> stringResource(R.string.crypto_wallet_discovery_hint)
                },
                isError = addressProblem != null,
                modifier = Modifier.fillMaxWidth(),
            )
            WhfinField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.crypto_wallet_name),
                supportingText = stringResource(R.string.crypto_wallet_name_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            if (type == AccountType.CASH) {
                CashNameSelector(
                    name = name,
                    custom = customCashName,
                    onSelect = { preset -> customCashName = false; name = preset },
                    onCustom = { customCashName = true; name = "" },
                    onNameChange = { name = it },
                )
            } else {
                WhfinField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(
                        if (type == AccountType.BANK) R.string.account_name_in_bank else R.string.account_name,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CurrencySelector(
                currency = currency,
                onChange = { currency = it },
                quick = quickCurrencies,
            )
            // What is already in the pocket is not income: it is the position the ledger starts from,
            // recorded exactly the way a statement's opening balance is. Leaving it blank starts at
            // zero, which is what an account nobody has counted yet honestly holds.
            WhfinField(
                value = opening,
                onValueChange = { opening = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                label = stringResource(R.string.account_opening_amount),
                supportingText = stringResource(R.string.account_opening_amount_hint),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Naming cash by choosing rather than by typing.
 *
 * A cash pile is one of a handful of things — the money on you, the money at home — and the ledger
 * holds one per currency, so there is nothing here a keyboard answers better than a list. The plain
 * option stores no name at all: the seeded placeholder already reads as "Cash" in whichever language
 * the app is in, and a typed-out "Cash" would freeze that choice in one language forever. Typing
 * stays available for the pile that is genuinely something else.
 */
@Composable
private fun CashNameSelector(
    name: String,
    custom: Boolean,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
    onNameChange: (String) -> Unit,
) {
    val presets = listOf(
        "" to stringResource(R.string.cash_name_preset_plain),
        stringResource(R.string.cash_name_preset_pocket) to stringResource(R.string.cash_name_preset_pocket),
        stringResource(R.string.cash_name_preset_home) to stringResource(R.string.cash_name_preset_home),
    )
    WhfinFieldLabel(stringResource(R.string.account_name))
    WhfinChoiceRail {
        items(presets, key = { it.second }) { (value, label) ->
            WhfinFilterPill(
                label = label,
                selected = !custom && name == value,
                onClick = { onSelect(value) },
            )
        }
        item {
            WhfinFilterPill(
                label = stringResource(R.string.cash_name_preset_custom),
                selected = custom,
                onClick = onCustom,
            )
        }
    }
    if (custom) {
        WhfinField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.account_name),
            supportingText = stringResource(R.string.cash_name_optional_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Редактирование существующего счёта: имя и валюта (например, сменить валюту кеша). */
@Composable
fun EditAccountSheet(
    account: AccountEntity,
    initialAddress: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        currency: String,
        address: String?,
        fundRole: FundRole,
        bankProduct: BankProduct?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(account.name) }
    var currency by remember { mutableStateOf(account.currency) }
    var address by remember { mutableStateOf(initialAddress.orEmpty()) }
    var fundRole by remember { mutableStateOf(account.fundRole) }
    var bankProduct by remember { mutableStateOf(account.bankProduct) }

    FormSheet(
        title = stringResource(R.string.account_edit),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = (account.type == AccountType.CASH || name.isNotBlank()) && currency.isNotBlank(),
        onPrimary = {
            onConfirm(
                name,
                currency,
                address.trim().takeIf(String::isNotEmpty),
                fundRole,
                bankProduct,
            )
        },
    ) {
        WhfinField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.account_name),
            supportingText = if (account.type == AccountType.CASH) stringResource(R.string.cash_name_optional_hint) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        when (account.type) {
            AccountType.BANK, AccountType.SAVINGS, AccountType.CASH -> {
                Text(stringResource(R.string.account_fund_role), style = MaterialTheme.typography.labelLarge)
                FundRoleSelector(
                    selected = fundRole,
                    onSelect = { fundRole = it },
                )
                if (account.type != AccountType.CASH) {
                    Text(stringResource(R.string.account_bank_product), style = MaterialTheme.typography.labelLarge)
                    BankProductSelector(
                        selected = bankProduct,
                        onSelect = { bankProduct = it },
                    )
                }
            }
            // A wallet is its address and the chain decides its assets: only the name is the
            // person's to change, so the rest is shown as what it is rather than as a field.
            AccountType.CRYPTO -> {
                WhfinFieldLabel(stringResource(R.string.account_address))
                Text(
                    address.ifBlank { stringResource(R.string.crypto_address_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.crypto_wallet_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> CurrencySelector(
                currency = currency,
                onChange = { currency = it },
                quick = quickCurrencies,
            )
        }
    }
}

@Composable
private fun FundRoleSelector(
    selected: FundRole,
    onSelect: (FundRole) -> Unit,
) {
    val options = listOf(
        FundRole.AVAILABLE to R.string.account_fund_available,
        FundRole.RESERVE to R.string.account_purpose_reserve,
    )
    WhfinChoiceRail {
        items(options, key = { it.second }) { (mode, label) ->
            WhfinFilterPill(
                label = stringResource(label),
                selected = selected == mode,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun BankProductSelector(
    selected: BankProduct?,
    onSelect: (BankProduct?) -> Unit,
) {
    val options = listOf(
        null to R.string.account_product_unspecified,
        BankProduct.CURRENT_ACCOUNT to R.string.account_product_current,
        BankProduct.DEMAND_DEPOSIT to R.string.account_product_demand_deposit,
        BankProduct.TERM_DEPOSIT to R.string.account_product_term_deposit,
    )
    WhfinChoiceRail {
        items(options, key = { it.second }) { (product, label) ->
            WhfinFilterPill(
                label = stringResource(label),
                selected = selected == product,
                onClick = { onSelect(product) },
            )
        }
    }
}

@Preview(name = "Edit demand deposit", widthDp = 400, heightDp = 700, showBackground = true)
@Preview(
    name = "Edit demand deposit dark",
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Edit demand deposit font 1.5",
    widthDp = 400,
    heightDp = 900,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(name = "Edit demand deposit compact", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
private fun EditDemandDepositPreview() {
    WhfinTheme {
        EditAccountSheet(
            account = AccountEntity(
                name = "Daily deposit",
                type = AccountType.BANK,
                groupId = 1,
                currency = "GEL",
                iban = "GE00CD0000000000000156",
                fundRole = FundRole.AVAILABLE,
                bankProduct = BankProduct.DEMAND_DEPOSIT,
            ),
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> },
        )
    }
}

@Preview(name = "Add Crypto", widthDp = 400, heightDp = 860, showBackground = true)
@Preview(name = "Add Crypto dark", widthDp = 400, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Add Crypto font 1.5", widthDp = 400, heightDp = 1000, fontScale = 1.5f, showBackground = true)
@Composable
private fun AddCryptoPreview() {
    WhfinTheme {
        AddAccountSheet({}, {}, { _, _, _, _, _ -> }, initialType = AccountType.CRYPTO)
    }
}

@Preview(name = "Add Cash", widthDp = 400, heightDp = 760, showBackground = true)
@Preview(name = "Add Cash dark", widthDp = 400, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Add Cash font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun AddCashPreview() {
    WhfinTheme {
        AddAccountSheet(
            onDismiss = {},
            onImportStatement = {},
            onConfirm = { _, _, _, _, _ -> },
            initialType = AccountType.CASH,
            cashOnly = true,
            titleOverride = stringResource(R.string.personal_setup_cash_sheet_title),
        )
    }
}

@Composable
fun BankMappingSheet(
    account: AccountEntity,
    existingCards: List<String>,
    existingVirtualCards: List<String>,
    existingPrimaryCard: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String?, List<String>, List<String>, String?) -> Unit,
) {
    var iban by remember { mutableStateOf(account.iban.orEmpty()) }
    var cards by remember(account.id, existingCards, existingVirtualCards) {
        mutableStateOf((existingCards + existingVirtualCards).distinct().joinToString(", "))
    }
    var cardTypes by remember(account.id, existingCards, existingVirtualCards) {
        mutableStateOf(
            buildMap {
                existingCards.forEach { put(it, PaymentInstrumentType.PHYSICAL_CARD) }
                existingVirtualCards.forEach { put(it, PaymentInstrumentType.VIRTUAL_CARD) }
            },
        )
    }
    var primaryCard by remember(account.id, existingPrimaryCard) { mutableStateOf(existingPrimaryCard) }
    val validCards = parseCardMasks(cards)

    FormSheet(
        title = stringResource(R.string.account_bank_mapping),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = true,
        onPrimary = {
            onConfirm(
                iban.trim().takeIf(String::isNotEmpty),
                validCards.filter { cardTypes[it] != PaymentInstrumentType.VIRTUAL_CARD },
                validCards.filter { cardTypes[it] == PaymentInstrumentType.VIRTUAL_CARD },
                primaryCard?.takeIf(validCards::contains),
            )
        },
    ) {
        Text(
            account.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinField(
            value = iban,
            onValueChange = { iban = it.uppercase().filterNot(Char::isWhitespace) },
            label = stringResource(R.string.account_iban),
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinField(
            value = cards,
            onValueChange = { value ->
                cards = value.filter { ch -> ch.isDigit() || ch == ',' || ch == ' ' }
                val masks = parseCardMasks(cards)
                cardTypes = masks.associateWith { mask ->
                    cardTypes[mask] ?: PaymentInstrumentType.PHYSICAL_CARD
                }
                if (primaryCard !in masks) primaryCard = null
            },
            label = stringResource(R.string.account_card_last4),
            supportingText = stringResource(R.string.account_card_last4_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        validCards.forEach { mask ->
            val physicalLabel = stringResource(R.string.account_card_physical)
            val virtualLabel = stringResource(R.string.account_card_virtual)
            val primaryLabel = stringResource(R.string.account_card_primary)
            WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.account_card_label, mask),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        WhfinFilterPill(
                            label = primaryLabel,
                            selected = primaryCard == mask,
                            onClick = { primaryCard = mask.takeUnless { primaryCard == mask } },
                            leadingIcon = if (primaryCard == mask) Icons.Default.Star else Icons.Outlined.StarOutline,
                            modifier = Modifier.testTag("card-$mask-primary").semantics {
                                contentDescription = primaryLabel + " ••" + mask
                                selected = primaryCard == mask
                            },
                        )
                    }
                    WhfinFieldLabel(stringResource(R.string.account_card_kind))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WhfinFilterPill(
                            label = physicalLabel,
                            selected = cardTypes[mask] != PaymentInstrumentType.VIRTUAL_CARD,
                            onClick = { cardTypes = cardTypes + (mask to PaymentInstrumentType.PHYSICAL_CARD) },
                            modifier = Modifier.weight(1f).testTag("card-$mask-physical").semantics {
                                contentDescription = physicalLabel + " ••" + mask
                                selected = cardTypes[mask] != PaymentInstrumentType.VIRTUAL_CARD
                            },
                        )
                        WhfinFilterPill(
                            label = virtualLabel,
                            selected = cardTypes[mask] == PaymentInstrumentType.VIRTUAL_CARD,
                            onClick = { cardTypes = cardTypes + (mask to PaymentInstrumentType.VIRTUAL_CARD) },
                            modifier = Modifier.weight(1f).testTag("card-$mask-virtual").semantics {
                                contentDescription = virtualLabel + " ••" + mask
                                selected = cardTypes[mask] == PaymentInstrumentType.VIRTUAL_CARD
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun parseCardMasks(value: String): List<String> = value
    .split(',', ' ')
    .map(String::trim)
    .filter { it.matches(Regex("\\d{4}")) }
    .distinct()

@Preview(name = "Bank details", widthDp = 400, heightDp = 760, showBackground = true)
@Preview(name = "Bank details dark", widthDp = 400, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Bank details font 1.5", widthDp = 400, heightDp = 980, fontScale = 1.5f, showBackground = true)
@Preview(name = "Bank details compact", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
private fun BankMappingPreview() {
    WhfinTheme {
        BankMappingSheet(
            account = AccountEntity(
                id = 1,
                name = "Everyday",
                type = AccountType.BANK,
                groupId = 1,
                currency = "GEL",
                iban = "GE00CD0000000000000001",
            ),
            existingCards = listOf("0001"),
            existingVirtualCards = listOf("0002"),
            existingPrimaryCard = "0001",
            onDismiss = {},
            onConfirm = { _, _, _, _ -> },
        )
    }
}

private data class TypeOption(val type: AccountType, val icon: ImageVector)

private val typeOptions = listOf(
    TypeOption(AccountType.BANK, Icons.Default.AccountBalance),
    TypeOption(AccountType.CASH, Icons.Default.Payments),
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
        WhfinChoiceRail {
            items(quick, key = { it }) { code ->
                WhfinFilterPill(
                    label = code,
                    selected = currency == code,
                    onClick = { onChange(code) },
                )
            }
            item {
                WhfinFilterPill(
                    label = stringResource(R.string.account_currency_other),
                    selected = isCustom,
                    onClick = { if (!isCustom) onChange("") },
                )
            }
        }
        if (isCustom) {
            WhfinField(
                value = currency,
                onValueChange = { onChange(it.take(6).uppercase().filter(Char::isLetterOrDigit)) },
                label = stringResource(R.string.account_currency),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
