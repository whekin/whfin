package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinActionMenu
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.feed.DayHeader
import dev.whekin.whfin.ui.feed.FeedItem
import dev.whekin.whfin.ui.feed.FeedRow
import dev.whekin.whfin.ui.feed.TransactionDetailsSheet
import dev.whekin.whfin.ui.feed.TransactionStatusSheet
import dev.whekin.whfin.ui.feed.CategoryPickerSheet
import dev.whekin.whfin.ui.feed.DebtPersonSheet
import dev.whekin.whfin.ui.feed.AddTransactionSheet
import dev.whekin.whfin.ui.feed.FeedViewModel
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatBaseUnits
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AccountTransactionsScreen(
    accountId: Long,
    onBack: () -> Unit,
    viewModel: AccountTransactionsViewModel = viewModel(),
    accountsViewModel: AccountsViewModel = viewModel(),
    feedViewModel: FeedViewModel = viewModel(),
) {
    LaunchedEffect(accountId) { viewModel.bind(accountId) }
    val state by viewModel.uiState.collectAsState()
    val accountRowsState by accountsViewModel.accountRowsState.collectAsState()
    val accounts by feedViewModel.accounts.collectAsState()
    val categories by feedViewModel.categories.collectAsState()
    val categoriesByUsage by feedViewModel.categoriesByUsage.collectAsState()
    val people by feedViewModel.people.collectAsState()
    val selectedRow = (accountRowsState as? AccountRowsState.Ready)?.accounts?.firstOrNull {
        it.account.id == accountId
    }
    val containerRows = selectedRow?.let { selected ->
        (accountRowsState as? AccountRowsState.Ready)?.accounts.orEmpty().filter { candidate ->
            val account = selected.account
            if (account.groupId != null && account.iban != null) {
                candidate.account.groupId == account.groupId && candidate.account.iban == account.iban
            } else {
                candidate.account.id == account.id
            }
        }
    }.orEmpty()
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var categoryFor by remember { mutableStateOf<FeedItem?>(null) }
    var statusFor by remember { mutableStateOf<FeedItem?>(null) }
    var editTransactionFor by remember { mutableStateOf<FeedItem?>(null) }
    var correctTransactionFor by remember { mutableStateOf<FeedItem?>(null) }
    var deleteTransactionFor by remember { mutableStateOf<FeedItem?>(null) }
    var debtFor by remember { mutableStateOf<FeedItem?>(null) }
    var editAccount by remember { mutableStateOf(false) }
    var adjustBalance by remember { mutableStateOf(false) }
    var bankMapping by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }

    val contentCallbacks = AccountActivityCallbacks(
        onTransaction = { details = it },
        onEditAccount = { editAccount = true },
        onAdjustBalance = { adjustBalance = true },
        onBankMapping = { bankMapping = true },
        onDeleteAccount = { deleteAccount = true },
    )
    when (val value = state) {
        AccountTransactionsUiState.Loading -> AccountTransactionsState(
            paneState = WhfinPaneState.Loading,
            title = stringResource(R.string.account_transactions_loading),
            body = stringResource(R.string.account_transactions_loading_body),
            onBack = onBack,
        )
        is AccountTransactionsUiState.Empty -> AccountTransactionsContent(
            account = value.account,
            balanceMinor = value.balanceMinor,
            items = emptyList(),
            accountRow = selectedRow,
            onBack = onBack,
            callbacks = contentCallbacks,
            empty = true,
        )
        is AccountTransactionsUiState.Content -> AccountTransactionsContent(
            account = value.account,
            balanceMinor = value.balanceMinor,
            items = value.items,
            accountRow = selectedRow,
            onBack = onBack,
            callbacks = contentCallbacks,
        )
        AccountTransactionsUiState.Error -> AccountTransactionsState(
            paneState = WhfinPaneState.Error,
            title = stringResource(R.string.account_transactions_error),
            body = stringResource(R.string.account_transactions_error_body),
            onBack = onBack,
        )
    }

    details?.let { item ->
        TransactionDetailsSheet(
            item = item,
            onDismiss = { details = null },
            onChangeCategory = {
                details = null
                categoryFor = item
            },
            onDelete = if (item.tx.source == TxSource.MANUAL) {{
                details = null
                deleteTransactionFor = item
            }} else null,
            onCorrect = if (item.tx.source in setOf(TxSource.STATEMENT, TxSource.SMS)) {{
                details = null
                correctTransactionFor = item
            }} else null,
            onEdit = if (item.tx.source == TxSource.MANUAL) {{
                details = null
                editTransactionFor = item
            }} else null,
            onDebt = if (item.tx.amountMinor < 0) {{ details = null; debtFor = item }} else null,
            onClearDebt = if (item.isDebt) {{ feedViewModel.clearAllocations(item); details = null }} else null,
            onChangeStatus = {
                details = null
                statusFor = item
            },
        )
    }
    statusFor?.let { item ->
        TransactionStatusSheet(item.tx.status, { statusFor = null }) { status ->
            feedViewModel.updateStatus(item, status)
            statusFor = null
        }
    }
    categoryFor?.let { item ->
        val suggester by feedViewModel.categorySuggester.collectAsState()
        CategoryPickerSheet(
            item = item,
            categories = androidx.compose.runtime.remember(categories, suggester, item.tx.id) {
                suggester?.rankCategories(categories, item.tx.amountMinor, item.tx.currency) ?: categories
            },
            onDismiss = { categoryFor = null },
            onSelect = { category ->
                feedViewModel.assignCategory(item, category.id)
                categoryFor = null
            },
            onCreateCategory = feedViewModel::createCategory,
        )
    }
    editTransactionFor?.let { item ->
        AddTransactionSheet(
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            editing = item,
            onDismiss = { editTransactionFor = null },
            onSave = {},
            onSaveDebt = {},
            onUpdate = { original, value ->
                feedViewModel.updateManual(original, value)
                editTransactionFor = null
            },
            onCreateCategory = feedViewModel::createCategory,
            onCreateCashCurrency = feedViewModel::createCashCurrency,
        )
    }
    deleteTransactionFor?.let { item ->
        WhfinConfirmDialog(
            title = stringResource(R.string.transaction_delete),
            body = stringResource(
                if (item.tx.transferGroupId != null) R.string.transaction_delete_transfer_body
                else R.string.transaction_delete_body,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                    feedViewModel.deleteManual(item)
                    deleteTransactionFor = null
            },
            onDismiss = { deleteTransactionFor = null },
        )
    }
    correctTransactionFor?.let { item ->
        WhfinConfirmDialog(
            title = stringResource(R.string.transaction_correct),
            body = stringResource(R.string.transaction_correct_body),
            confirmLabel = stringResource(R.string.transaction_correct),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                feedViewModel.correctImported(item)
                correctTransactionFor = null
            },
            onDismiss = { correctTransactionFor = null },
        )
    }
    debtFor?.let { item ->
        DebtPersonSheet(
            item = item,
            people = people,
            onDismiss = { debtFor = null },
            onSelect = { feedViewModel.assignDebt(item, it.id); debtFor = null },
            onAdd = { feedViewModel.addPersonAndAssignDebt(item, it); debtFor = null },
        )
    }

    selectedRow?.let { item ->
        if (editAccount) EditAccountSheet(
            account = item.account,
            initialAddress = item.address,
            onDismiss = { editAccount = false },
            onConfirm = { name, currency, address, fundRole, bankProduct ->
                accountsViewModel.editAccount(item.account, name, currency, address, fundRole, bankProduct)
                editAccount = false
            },
        )
        if (adjustBalance) AdjustBalanceSheet(
            item = item,
            onDismiss = { adjustBalance = false },
            onConfirm = { delta ->
                accountsViewModel.adjustBalance(item, delta)
                adjustBalance = false
            },
        )
        if (bankMapping) BankMappingSheet(
            account = item.account,
            existingCards = containerRows.flatMap { it.cardMasks }.distinct(),
            existingVirtualCards = containerRows.flatMap { it.virtualCardMasks }.distinct(),
            existingPrimaryCard = containerRows.flatMap { it.primaryCardMasks }.firstOrNull(),
            onDismiss = { bankMapping = false },
            onConfirm = { iban, physicalCards, virtualCards, primaryCard ->
                accountsViewModel.updateBankMapping(
                    containerRows.map { it.account },
                    iban,
                    physicalCards,
                    virtualCards,
                    primaryCard,
                )
                bankMapping = false
            },
        )
        // Deleting one asset row of a wallet would come back with the next discovery pass, so a
        // watch-only ledger is removed as the whole address it belongs to.
        val isWallet = item.account.type == AccountType.CRYPTO
        if (deleteAccount) WhfinConfirmDialog(
            title = stringResource(if (isWallet) R.string.crypto_wallet_delete else R.string.account_delete),
            body = stringResource(
                if (isWallet) R.string.crypto_wallet_delete_confirmation
                else R.string.account_delete_confirmation,
                item.account.name,
            ),
            confirmLabel = stringResource(
                if (isWallet) R.string.crypto_wallet_delete else R.string.account_delete,
            ),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                    if (isWallet) accountsViewModel.archiveCryptoWallet(item.account)
                    else accountsViewModel.archiveAccountContainer(containerRows.map { it.account })
                    deleteAccount = false
                    onBack()
            },
            onDismiss = { deleteAccount = false },
        )
    }
}

private data class AccountActivityCallbacks(
    val onTransaction: (FeedItem) -> Unit,
    val onEditAccount: () -> Unit,
    val onAdjustBalance: () -> Unit,
    val onBankMapping: () -> Unit,
    val onDeleteAccount: () -> Unit,
)

@Composable
private fun AccountTransactionsState(
    paneState: WhfinPaneState,
    title: String,
    body: String,
    onBack: () -> Unit,
    account: AccountEntity? = null,
    balanceMinor: Long = 0,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { AccountTransactionsHeader(onBack) }
            if (account != null) item {
                AccountTransactionsScope(account, balanceMinor, null, {}, {}, {}, {})
            }
            item { WhfinStatePane(paneState, title, body, Modifier.fillMaxWidth()) }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AccountTransactionsContent(
    account: AccountEntity,
    balanceMinor: Long,
    items: List<FeedItem>,
    accountRow: AccountWithBalance?,
    onBack: () -> Unit,
    callbacks: AccountActivityCallbacks,
    empty: Boolean = false,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var expandedDays by remember { mutableStateOf(emptySet<LocalDate>()) }
    val grouped = items.groupBy { it.day }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navigationBottom + 24.dp),
        ) {
            item { AccountTransactionsHeader(onBack) }
            item {
                AccountTransactionsScope(
                    account = account,
                    balanceMinor = balanceMinor,
                    accountRow = accountRow,
                    onEdit = callbacks.onEditAccount,
                    onAdjust = callbacks.onAdjustBalance,
                    onBankMapping = callbacks.onBankMapping,
                    onDelete = callbacks.onDeleteAccount,
                )
            }
            if (empty) item(key = "account-transactions-empty") {
                val chain = account.type == AccountType.CRYPTO
                WhfinStatePane(
                    WhfinPaneState.Empty,
                    stringResource(
                        if (chain) R.string.crypto_activity_empty else R.string.account_transactions_empty,
                    ),
                    stringResource(
                        if (chain) R.string.crypto_activity_empty_body
                        else R.string.account_transactions_empty_body,
                    ),
                    Modifier.fillMaxWidth(),
                )
            }
            grouped.forEach { (day, dayItems) ->
                item(key = "account-transactions-day-$day") {
                    val expenses = dayItems.groupBy { it.tx.currency }
                        .mapValues { (_, values) -> -values.sumOf { it.tx.amountMinor.coerceAtMost(0L) } }
                        .filterValues { it > 0L }
                    DayHeader(
                        day = day,
                        expensesByCurrency = expenses,
                        gelFromConversions = 0L,
                        expanded = day in expandedDays,
                        onToggle = {
                            expandedDays = if (day in expandedDays) expandedDays - day else expandedDays + day
                        },
                    )
                }
                items(dayItems, key = { "account-transaction-${it.tx.id}" }) { item ->
                    FeedRow(item, onClick = { callbacks.onTransaction(item) })
                }
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AccountTransactionsHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(stringResource(R.string.account_transactions_title), style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AccountTransactionsScope(
    account: AccountEntity,
    balanceMinor: Long,
    accountRow: AccountWithBalance?,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onBankMapping: () -> Unit,
    onDelete: () -> Unit,
) {
    val isChain = account.type == AccountType.CRYPTO
    val accountDetail = when {
        // A watch-only row is one asset at one address: naming the chain says more than a ticker.
        isChain -> listOfNotNull(
            accountRow?.chainId?.let { CryptoNetwork.byChainId(it)?.displayName },
            account.currency,
            accountRow?.address?.let(::shortAddress),
        ).joinToString(" · ")
        account.iban != null ->
            stringResource(R.string.account_transactions_iban_currency, account.iban.takeLast(4), account.currency)
        else -> account.currency
    }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhfinSectionHeader(
            // A wallet is named once, at the address; the ticker belongs to the line under it.
            title = if (isChain) accountRow?.groupName ?: account.name else account.name,
            supportingText = accountDetail,
            trailing = if (accountRow == null) null else {
                {
                    Box {
                        WhfinIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.account_actions),
                            onClick = { accountMenuExpanded = true },
                            outlined = false,
                        )
                        WhfinActionMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            if (account.type == AccountType.BANK) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.account_bank_mapping)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                                    },
                                    onClick = {
                                        accountMenuExpanded = false
                                        onBankMapping()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (isChain) R.string.crypto_wallet_delete
                                            else R.string.account_delete,
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            },
        )
        // A chain balance is the last reading of the address, never a sum of local rows: showing the
        // transactions total here would print a confident zero for money that is plainly there.
        val onChain = accountRow?.onChain.takeIf { isChain }
        WhfinFieldLabel(
            if (isChain) {
                onChain?.let { stringResource(R.string.crypto_observed_at, relativeTime(it.observedAt)) }
                    ?: stringResource(R.string.crypto_never_refreshed)
            } else {
                stringResource(R.string.account_transactions_balance)
            },
        )
        val formattedBalance = when {
            !isChain -> formatMinor(balanceMinor, account.currency)
            onChain != null -> "${formatBaseUnits(onChain.baseUnits, onChain.decimals)} ${account.currency}"
            else -> "—"
        }
        if (!isChain && accountRow != null) {
            Surface(
                onClick = onAdjust,
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WhfinAmount(
                        formattedBalance,
                        symbol = currencySymbol(account.currency),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.action_adjust_balance),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            WhfinAmount(
                formattedBalance,
                symbol = if (isChain) account.currency.takeIf { onChain != null } else currencySymbol(account.currency),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        if (accountRow != null) {
            AccountActivityAction(
                icon = Icons.Default.Edit,
                label = stringResource(if (isChain) R.string.crypto_wallet_rename else R.string.account_edit),
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AccountActivityAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 2)
        }
    }
}

@Preview(name = "Account transactions", widthDp = 400, heightDp = 880, showBackground = true)
@Preview(name = "Account transactions dark", widthDp = 400, heightDp = 880, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Account transactions font 1.5", widthDp = 400, heightDp = 1080, fontScale = 1.5f, showBackground = true)
@Composable
private fun AccountTransactionsPreview() {
    val account = AccountEntity(
        id = 1,
        name = "EVO",
        type = AccountType.BANK,
        groupId = 1,
        currency = "GEL",
        iban = "GE00CD0000000000000001",
    )
    val now = LocalDate.of(2026, 7, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val items = listOf(
        FeedItem(
            tx = TransactionEntity(
                id = 1,
                accountId = 1,
                amountMinor = -4_800,
                currency = "GEL",
                occurredAt = now,
                rawCounterparty = "Coffee Lab",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                createdAt = now,
            ),
            merchant = null,
            category = null,
            account = account,
            cardHint = "2533",
            day = LocalDate.of(2026, 7, 14),
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountTransactionsContent(
                account = account,
                balanceMinor = 163_18,
                items = items,
                accountRow = AccountWithBalance(account, 163_18, emptyList()),
                onBack = {},
                callbacks = AccountActivityCallbacks({}, {}, {}, {}, {}),
            )
        }
    }
}
