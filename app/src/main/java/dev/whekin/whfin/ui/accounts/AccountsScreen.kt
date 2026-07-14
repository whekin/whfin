package dev.whekin.whfin.ui.accounts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.components.LedgerIconButton
import dev.whekin.whfin.ui.components.LedgerButton
import dev.whekin.whfin.ui.settings.BankStatementsViewModel
import dev.whekin.whfin.ui.settings.StatementImportStatusSheet
import dev.whekin.whfin.ui.settings.StatementImportUiState
import dev.whekin.whfin.ui.settings.statementFileName
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.SavingsMode
import dev.whekin.whfin.ui.theme.WhfinTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenStatements: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAccountTransactions: (Long) -> Unit = {},
    viewModel: AccountsViewModel = viewModel(),
    statementsViewModel: BankStatementsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val people by viewModel.people.collectAsState()
    val message by viewModel.message.collectAsState()
    val importState by statementsViewModel.importState.collectAsState()
    val gelBalance = accounts.filter { it.account.currency == "GEL" }.sumOf { it.balanceMinor }
    val accountContainers = accounts.groupBy { item ->
        item.account.groupId to (item.account.iban ?: "account-${item.account.id}")
    }.values
    val everydayAccounts = accountContainers.filterNot { container ->
        container.any { it.account.type == AccountType.SAVINGS || it.account.savingsMode != null }
    }.flatten()
    val savingsAccounts = accountContainers.filter { container ->
        container.any { it.account.type == AccountType.SAVINGS || it.account.savingsMode != null }
    }.flatten()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var adjustFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var editFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var configureAccount by remember { mutableStateOf<AccountWithBalance?>(null) }
    var deleteFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showImportStatus by remember { mutableStateOf(false) }
    var showDebts by remember { mutableStateOf(false) }
    val headerScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val statementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            showImportStatus = true
            statementsViewModel.importStatements(uris.map { uri ->
                statementFileName(context, uri) to { context.contentResolver.openInputStream(uri) }
            })
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(headerScrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WhfinContextHeader(
                label = stringResource(R.string.accounts_net_worth),
                value = formatMinor(gelBalance, "GEL"),
                scrollBehavior = headerScrollBehavior,
            ) {
                WhfinIconButton(
                    Icons.Default.Add,
                    stringResource(R.string.accounts_add),
                    { showAdd = true },
                    outlined = false,
                )
                WhfinIconButton(
                    Icons.Default.BarChart,
                    stringResource(R.string.account_overview_action),
                    onOpenOverview,
                    outlined = false,
                )
                WhfinIconButton(
                    Icons.Default.Settings,
                    stringResource(R.string.settings_title),
                    onOpenSettings,
                    outlined = false,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (accounts.isEmpty() && debts.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    WhfinStatePane(
                        state = WhfinPaneState.Empty,
                        title = stringResource(R.string.tab_accounts),
                        body = stringResource(R.string.accounts_empty),
                        actionLabel = stringResource(R.string.accounts_add),
                        onAction = { showAdd = true },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                ) {
                    item(key = "accounts-summary") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { AccountsSummary(accounts) }
                    }
                    listOf(
                        R.string.accounts_everyday_section to everydayAccounts,
                        R.string.accounts_savings_section to savingsAccounts,
                    ).forEach { (sectionLabel, sectionAccounts) ->
                        if (sectionAccounts.isNotEmpty()) {
                            item(key = "account-section-$sectionLabel") {
                                WhfinSectionLabel(
                                    stringResource(sectionLabel),
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                            sectionAccounts.groupBy {
                                it.groupName ?: it.account.type.name.lowercase().replaceFirstChar(Char::titlecase)
                            }.forEach { (groupName, groupAccounts) ->
                                item(key = "group-$sectionLabel-$groupName") {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                        AccountGroupCard(
                                            name = groupName,
                                            accounts = groupAccounts,
                                            onOpenTransactions = { onOpenAccountTransactions(it.account.id) },
                                            onAccountActions = { actionsFor = it },
                                            onEditContainer = { editFor = it },
                                            onOpenStatements = onOpenStatements.takeIf {
                                                groupAccounts.any { account -> account.account.type == AccountType.BANK }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "debts") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            DebtsSummary(debts, onClick = { showDebts = true })
                        }
                    }
                }
            }
        }
    }

    if (showDebts) DebtLedgerDialog(
        debts = debts, people = people, accounts = accounts.map { it.account },
        onDismiss = { showDebts = false }, onOpen = viewModel::openDebt, onSettle = viewModel::settleDebt,
    )

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (showAdd) {
        AddAccountSheet(
            onDismiss = { showAdd = false },
            onImportStatement = {
                showAdd = false
                statementPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            },
            onConfirm = { name, type, currency, address, bankProvider ->
                viewModel.addAccount(name, type, currency, address, bankProvider)
                showAdd = false
            },
        )
    }

    if (showImportStatus && importState !is StatementImportUiState.Idle) {
        StatementImportStatusSheet(
            state = importState,
            onDismiss = {
                statementsViewModel.dismissResult()
                showImportStatus = false
            },
        )
    }

    actionsFor?.let { item ->
        AccountActionsSheet(
            item = item,
            onDismiss = { actionsFor = null },
            onAdjustBalance = { adjustFor = item; actionsFor = null },
            onBankMapping = { configureAccount = item; actionsFor = null },
            onEdit = { editFor = item; actionsFor = null },
            onDelete = { deleteFor = item; actionsFor = null },
        )
    }

    editFor?.let { item ->
        EditAccountSheet(
            account = item.account,
            initialAddress = item.address,
            onDismiss = { editFor = null },
            onConfirm = { name, currency, address, savingsMode ->
                viewModel.editAccount(item.account, name, currency, address, savingsMode)
                editFor = null
            },
        )
    }

    adjustFor?.let { item ->
        AdjustBalanceSheet(
            item = item,
            onDismiss = { adjustFor = null },
            onConfirm = { delta ->
                viewModel.adjustBalance(item, delta)
                adjustFor = null
            },
        )
    }

    configureAccount?.let { item ->
        BankMappingSheet(
            account = item.account,
            existingCards = item.cardMasks,
            existingVirtualCards = item.virtualCardMasks,
            onDismiss = { configureAccount = null },
            onConfirm = { iban, cards, isVirtual ->
                viewModel.updateBankMapping(
                    item.account,
                    iban,
                    if (isVirtual) emptyList() else cards,
                    if (isVirtual) cards else emptyList(),
                )
                configureAccount = null
            },
        )
    }

    deleteFor?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.account_delete)) },
            text = { Text(stringResource(R.string.account_delete_confirmation, item.account.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(item.account)
                    deleteFor = null
                }) { Text(stringResource(R.string.account_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsSummary(accounts: List<AccountWithBalance>) {
    val all = accounts.groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val available = accounts.filter { it.account.savingsMode == null && it.account.type != AccountType.SAVINGS }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val reserve = accounts.filter { it.account.savingsMode != null || it.account.type == AccountType.SAVINGS }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (all.keys.any { it != "GEL" }) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                all.entries.filter { it.key != "GEL" }.sortedBy { it.key }
                    .forEach { (currency, amount) ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                "$currency   ${formatMinor(amount, currency)}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
            }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryColumn(
                    stringResource(R.string.accounts_available),
                    formatMinor(available["GEL"] ?: 0L, "GEL"),
                    Modifier.weight(1f),
                )
                SummaryColumn(
                    stringResource(R.string.accounts_reserve),
                    reserve["GEL"]?.let { formatMinor(it, "GEL") } ?: "—",
                    Modifier.weight(1f),
                )
            }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun AccountGroupCard(
    name: String,
    accounts: List<AccountWithBalance>,
    onOpenTransactions: (AccountWithBalance) -> Unit,
    onAccountActions: (AccountWithBalance) -> Unit,
    onEditContainer: (AccountWithBalance) -> Unit,
    onOpenStatements: (() -> Unit)?,
) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accounts.any { it.account.groupId != null }) {
            WhfinSectionHeader(
                title = name,
                supportingText = run {
                    val accountCount = accounts.map { it.account.iban ?: "account-${it.account.id}" }.distinct().size
                    val currencyCount = accounts.map { it.account.currency }.distinct().size
                    pluralStringResource(R.plurals.accounts_container_count, accountCount, accountCount) +
                        " · " + pluralStringResource(R.plurals.accounts_currency_count, currencyCount, currencyCount)
                },
                trailing = onOpenStatements?.let { open ->
                    { TextButton(onClick = open) { Text(stringResource(R.string.statements_title)) } }
                },
            )
            }
            accounts
                .groupBy { it.account.iban ?: "account-${it.account.id}" }
                .toList()
                .sortedBy { (_, values) -> values.first().account.iban?.takeLast(4) }
                .forEach { (_, ibanAccounts) ->
                    IbanCard(ibanAccounts, onOpenTransactions, onAccountActions, onEditContainer)
                }
    }
}

@Composable
private fun IbanCard(
    accounts: List<AccountWithBalance>,
    onOpenTransactions: (AccountWithBalance) -> Unit,
    onAccountActions: (AccountWithBalance) -> Unit,
    onEditContainer: (AccountWithBalance) -> Unit,
) {
    val iban = accounts.first().account.iban
    WhfinLedgerGroup {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            accountTypeIcon(accounts.first().account.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        accounts.first().account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (iban != null) Text(
                        iban,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(accounts.joinToString(" · ") { it.account.currency },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WhfinIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.account_edit),
                    onClick = { onEditContainer(accounts.first()) },
                    outlined = false,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            accounts.sortedWith(compareBy<AccountWithBalance> { if (it.account.currency == "GEL") 0 else 1 }
                .thenBy { it.account.currency }).forEachIndexed { index, item ->
                CurrencyAccountRow(
                    item = item,
                    onClick = { onOpenTransactions(item) },
                    onActions = { onAccountActions(item) },
                )
                if (index != accounts.lastIndex) HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                )
            }
        }
    }
}

@Composable
private fun CurrencyAccountRow(
    item: AccountWithBalance,
    onClick: () -> Unit,
    onActions: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).background(
            if (item.account.savingsMode != null) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.outlineVariant,
            CircleShape,
        ))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(item.account.currency, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val cards = item.cardMasks.map { "••$it" } + item.virtualCardMasks.map { "${stringResource(R.string.account_card_virtual)} ••$it" }
            val detail = cards.joinToString(" · ").ifBlank {
                when (item.account.savingsMode) {
                    SavingsMode.FLEXIBLE_RESERVE -> stringResource(R.string.account_purpose_reserve)
                    SavingsMode.TERM_DEPOSIT -> stringResource(R.string.account_purpose_deposit)
                    SavingsMode.GOAL -> stringResource(R.string.account_purpose_goal)
                    null -> item.account.name.takeIf { it != item.account.currency }
                        ?: stringResource(R.string.accounts_currency_balance)
                }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatMinor(item.balanceMinor, item.account.currency),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
        )
        WhfinIconButton(
            icon = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.account_actions),
            onClick = onActions,
            modifier = Modifier.padding(start = 4.dp),
            outlined = false,
        )
    }
}

@Composable
private fun AccountCard(item: AccountWithBalance, title: String = item.account.name, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        accountTypeIcon(item.account.type),
                        contentDescription = accountTypeLabel(item.account.type),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                val detail = when {
                    item.cardMasks.isNotEmpty() || item.virtualCardMasks.isNotEmpty() ->
                        (item.cardMasks.map { "••$it" } + item.virtualCardMasks.map { "virtual ••$it" }).joinToString(" · ")
                    item.account.type == AccountType.CRYPTO && item.address != null ->
                        item.address.let { "${it.take(6)}…${it.takeLast(4)}" }
                    else -> accountTypeLabel(item.account.type)
                }
                Text(
                    detail + if (item.account.savingsMode != null) " · ${stringResource(R.string.accounts_reserve)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMinor(item.balanceMinor, item.account.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(name = "Accounts populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Accounts dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Accounts font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsContentPreview() {
    val accounts = listOf(
        AccountWithBalance(
            AccountEntity(id = 1, name = "Credo GEL •0001", type = AccountType.BANK, groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001"),
            500_000, listOf("0001"), groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(id = 2, name = "Credo USD •0001", type = AccountType.BANK, groupId = 1, currency = "USD", iban = "GE00CD0000000000000001"),
            2_360, emptyList(), groupName = "Credo",
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WhfinContextHeader(stringResource(R.string.accounts_net_worth), formatMinor(559_417, "GEL")) {
                    WhfinIconButton(Icons.Default.Add, "Add", {}, outlined = false)
                    WhfinIconButton(Icons.Default.BarChart, "Overview", {}, outlined = false)
                    WhfinIconButton(Icons.Default.Settings, "Settings", {}, outlined = false)
                }
                Column(Modifier.padding(20.dp)) {
                    AccountsSummary(accounts)
                    WhfinSectionLabel(stringResource(R.string.accounts_everyday_section))
                    AccountGroupCard("Credo", accounts, {}, {}, {}, {})
                    DebtsSummary(emptyList(), {})
                }
            }
        }
    }
}
