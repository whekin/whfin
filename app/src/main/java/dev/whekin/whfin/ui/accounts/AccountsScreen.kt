package dev.whekin.whfin.ui.accounts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.ui.convertedTotalLabel
import dev.whekin.whfin.ui.formatDecimal
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.settings.BankStatementsViewModel
import dev.whekin.whfin.ui.settings.StatementImportStatusSheet
import dev.whekin.whfin.ui.settings.StatementImportUiState
import dev.whekin.whfin.ui.settings.statementFileName
import dev.whekin.whfin.core.ui.WhfinLoadingIndicator
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.notifications.PhysicalCardBalanceStatus
import dev.whekin.whfin.data.notifications.physicalCardBalanceStatus
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** "just now" beats a precise timestamp for a value the user just pulled themselves. */
@Composable
internal fun relativeTime(millis: Long): String = android.text.format.DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    android.text.format.DateUtils.MINUTE_IN_MILLIS,
).toString()

private data class AccountGroupSelection(
    val name: String,
    val accounts: List<AccountWithBalance>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    addRequestKey: Int = 0,
    onAddRequestConsumed: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAccountTransactions: (Long) -> Unit = {},
    viewModel: AccountsViewModel = viewModel(),
    statementsViewModel: BankStatementsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsState()
    val readyState = screenState as? AccountsScreenState.Ready
    val accounts = readyState?.accounts.orEmpty()
    val debts = readyState?.debts.orEmpty()
    val archivedAccounts = readyState?.archivedAccounts.orEmpty()
    val people by viewModel.people.collectAsState()
    val message by viewModel.message.collectAsState()
    val cryptoRefreshing by viewModel.cryptoRefreshing.collectAsState()
    val netWorth by viewModel.netWorth.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val accountContainerTotals by viewModel.accountContainerTotals.collectAsState()
    val importState by statementsViewModel.importState.collectAsState()
    val cryptoPortfolio by viewModel.cryptoPortfolio.collectAsState()
    // A watch-only wallet is not an everyday account: it has its own reading below, grouped by asset
    // instead of by container, so it never lands in the fiat sections.
    val ledgerAccounts = accounts.filterNot { it.account.type == AccountType.CRYPTO }
    val accountContainers = ledgerAccounts.groupBy { item ->
        item.account.groupId to (item.account.iban ?: "account-${item.account.id}")
    }.values
    val everydayAccounts = accountContainers.filterNot { container ->
        container.any { it.account.fundRole == FundRole.RESERVE }
    }.flatten()
    val savingsAccounts = accountContainers.filter { container ->
        container.any { it.account.fundRole == FundRole.RESERVE }
    }.flatten()
    // A ledger row puts its name at one edge and its amount at the other. Left to fill a tablet, the
    // two end up an arm's length apart and stop reading as one line, so the content keeps a width a
    // person can actually scan and sits centred in whatever is left.
    val readableWidth = Modifier.widthIn(max = 640.dp)

    // A cash ledger has no bank behind it, and the enum name printed as its heading stayed English
    // on a Russian screen.
    val cashHeading = stringResource(R.string.account_type_cash)
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var groupDetailsFor by remember { mutableStateOf<AccountGroupSelection?>(null) }
    var bankDetailsFor by remember { mutableStateOf<List<AccountWithBalance>?>(null) }
    var editAccountFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var adjustBalanceFor by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showImportStatus by remember { mutableStateOf(false) }
    var showDebts by remember { mutableStateOf(false) }
    LaunchedEffect(addRequestKey) {
        if (addRequestKey > 0) {
            showAdd = true
            onAddRequestConsumed()
        }
    }
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
            val total = netWorth
            val totalAmount = total?.amount
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            WhfinContextHeader(
                modifier = readableWidth,
                label = convertedTotalLabel(
                    base = stringResource(R.string.accounts_net_worth),
                    total = total.takeIf { readyState != null },
                ),
                value = if (readyState == null || totalAmount == null) "—"
                else formatDecimal(totalAmount, total.currency),
                valueSymbol = currencySymbol(total?.currency ?: displayCurrency),
                scrollBehavior = headerScrollBehavior,
                onValueClick = viewModel::rotateDisplayCurrency,
                valueClickLabel = stringResource(R.string.net_worth_rotate),
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
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (readyState == null) {
                // Reading a local database takes milliseconds. A full-height pane announcing it left
                // most of the screen empty for a moment that is normally invisible; a quiet line is
                // enough to keep the state honest without pretending something is happening.
                Row(
                    Modifier.fillMaxWidth().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WhfinLoadingIndicator(Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.accounts_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (accounts.isEmpty() && debts.isEmpty() && archivedAccounts.isEmpty()) {
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
                        .fillMaxHeight()
                        .align(Alignment.TopCenter)
                        .then(readableWidth)
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                ) {
                    item(key = "accounts-summary") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            AccountsSummary(ledgerAccounts)
                        }
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
                            sectionAccounts.groupBy { it.groupName ?: cashHeading }
                                .forEach { (groupName, groupAccounts) ->
                                item(key = "group-$sectionLabel-$groupName") {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                        AccountGroupCard(
                                            name = groupName,
                                            accounts = groupAccounts,
                                            containerTotals = accountContainerTotals,
                                            onOpenTransactions = { onOpenAccountTransactions(it.account.id) },
                                            onOpenAccountDetails = { items ->
                                                val representative = items.firstOrNull { it.account.currency == "GEL" }
                                                    ?: items.firstOrNull()
                                                when (representative?.account?.type) {
                                                    AccountType.BANK, AccountType.SAVINGS -> bankDetailsFor = items
                                                    null -> Unit
                                                    else -> editAccountFor = representative
                                                }
                                            },
                                            onAdjustBalance = { adjustBalanceFor = it },
                                            onOpenGroupDetails = {
                                                val seed = groupAccounts.first().account
                                                val related = when {
                                                    seed.groupId != null -> ledgerAccounts.filter { it.account.groupId == seed.groupId }
                                                    seed.type == AccountType.CASH -> ledgerAccounts.filter { it.account.type == AccountType.CASH }
                                                    else -> groupAccounts
                                                }
                                                groupDetailsFor = AccountGroupSelection(groupName, related)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    cryptoPortfolio?.takeIf { !it.isEmpty }?.let { portfolio ->
                        item(key = "crypto-portfolio") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                CryptoPortfolioSection(
                                    portfolio = portfolio,
                                    refreshing = cryptoRefreshing,
                                    onRefresh = viewModel::refreshCryptoBalances,
                                    onRotateCurrency = viewModel::rotateDisplayCurrency,
                                    onOpenHolding = onOpenAccountTransactions,
                                )
                            }
                        }
                    }
                    // Nothing owed in either direction is not a state worth a section: on a fresh
                    // install it filled as much of the screen as the only real account, with two
                    // dashes in it. Debts are created from the composer, not from here.
                    if (debts.isNotEmpty()) {
                        item(key = "debts") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                DebtsSummary(debts, onClick = { showDebts = true })
                            }
                        }
                    }
                    if (archivedAccounts.isNotEmpty()) {
                        item(key = "archived-accounts") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WhfinSectionLabel(stringResource(R.string.accounts_archived_section))
                                // An archived account is still an account: it belongs in the same
                                // outlined group as the rest, not on a tonal slab of its own, and it
                                // named its type with an untranslated enum constant.
                                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                                    archivedAccounts.forEachIndexed { index, account ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(account.name, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "${accountTypeLabel(account.type)} · ${account.currency}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            WhfinIconButton(
                                                icon = Icons.Outlined.Unarchive,
                                                contentDescription = stringResource(R.string.account_restore),
                                                onClick = { viewModel.restoreAccount(account) },
                                                outlined = false,
                                            )
                                        }
                                        if (index < archivedAccounts.lastIndex) HorizontalDivider(
                                            Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                                        )
                                    }
                                }
                            }
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
            onConfirm = { name, type, currency, bankProvider, openingMinor ->
                viewModel.addAccount(name, type, currency, bankProvider, openingMinor)
                showAdd = false
            },
            onConfirmWallet = { name, network, address ->
                viewModel.addCryptoWallet(name, network, address)
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
            onConfirm = statementsViewModel::confirmImport,
            onCancel = {
                statementsViewModel.cancelImport()
                showImportStatus = false
            },
        )
    }

    groupDetailsFor?.let { selection ->
        AccountGroupDetailsDialog(
            name = selection.name,
            accounts = selection.accounts,
            onDismiss = { groupDetailsFor = null },
            onOpenStatements = onOpenStatements.takeIf {
                selection.accounts.any { it.account.type == AccountType.BANK }
            },
            onOpenAccountActivity = { items ->
                groupDetailsFor = null
                items.firstOrNull()?.let { onOpenAccountTransactions(it.account.id) }
            },
        )
    }

    bankDetailsFor?.let { rows ->
        val representative = rows.firstOrNull { it.account.currency == "GEL" } ?: rows.first()
        BankMappingSheet(
            account = representative.account,
            existingCards = rows.flatMap { it.cardMasks }.distinct(),
            existingVirtualCards = rows.flatMap { it.virtualCardMasks }.distinct(),
            existingPrimaryCard = rows.flatMap { it.primaryCardMasks }.firstOrNull(),
            onDismiss = { bankDetailsFor = null },
            onConfirm = { iban, physicalCards, virtualCards, primaryCard ->
                viewModel.updateBankMapping(
                    rows.map { it.account },
                    iban,
                    physicalCards,
                    virtualCards,
                    primaryCard,
                )
                bankDetailsFor = null
            },
        )
    }

    editAccountFor?.let { item ->
        EditAccountSheet(
            account = item.account,
            initialAddress = item.address,
            onDismiss = { editAccountFor = null },
            onConfirm = { name, currency, address, fundRole, bankProduct ->
                viewModel.editAccount(item.account, name, currency, address, fundRole, bankProduct)
                editAccountFor = null
            },
        )
    }

    adjustBalanceFor?.let { item ->
        AdjustBalanceSheet(
            item = item,
            onDismiss = { adjustBalanceFor = null },
            onConfirm = { delta ->
                viewModel.adjustBalance(item, delta)
                adjustBalanceFor = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsSummary(accounts: List<AccountWithBalance>) {
    // Chain balances are read, not summed from transactions, so they have their own reading below
    // and never mix into these currency chips.
    val all = accounts.groupBy { it.account.currency }
        .mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val available = accounts.filter { it.account.fundRole == FundRole.AVAILABLE }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val reserve = accounts.filter { it.account.fundRole == FundRole.RESERVE }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryColumn(
                stringResource(R.string.accounts_available),
                formatMinor(available["GEL"] ?: 0L, "GEL"),
                currencySymbol("GEL"),
                Modifier.weight(1f),
            )
            SummaryColumn(
                stringResource(R.string.accounts_reserve),
                reserve["GEL"]?.let { formatMinor(it, "GEL") } ?: "—",
                reserve["GEL"]?.let { currencySymbol("GEL") },
                Modifier.weight(1f),
            )
        }
        // Available and reserve are lari only, so on a multi-currency ledger they cannot add up to
        // the total above. Naming what is missing turns three numbers that seem to disagree into
        // three numbers that explain each other; chips repeating the rows below did not.
        val unconverted = all.entries.filter { it.key != "GEL" }.sortedBy { it.key }
        if (unconverted.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(
                    R.string.accounts_not_in_gel,
                    unconverted.joinToString(" · ") { (currency, amount) -> formatMinor(amount, currency) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String, symbol: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WhfinFieldLabel(label)
        WhfinAmount(value, symbol = symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AccountGroupCard(
    name: String,
    accounts: List<AccountWithBalance>,
    containerTotals: Map<String, ConvertedTotal> = emptyMap(),
    onOpenTransactions: (AccountWithBalance) -> Unit,
    onOpenAccountDetails: (List<AccountWithBalance>) -> Unit,
    onAdjustBalance: (AccountWithBalance) -> Unit,
    onOpenGroupDetails: () -> Unit,
) {
    val containers = orderedAccountContainers(accounts)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A bank is not a screen title. Set in the editorial serif it outweighed the balance it
            // belongs to, and repeated once per section it made one bank read as two — the serif is
            // reserved for screen titles, key totals and rare landmarks.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    // Счётчик показываем только когда у банка правда несколько счетов: «1 счёт ·
                    // 2 валюты» ничего не добавляет к строкам, которые тут же перечислены ниже.
                    containers.size.takeIf { it > 1 }?.let { count ->
                        Text(
                            pluralStringResource(R.plurals.accounts_container_count, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                WhfinIconButton(
                    // Filled, the glyph is a solid disc: the heaviest mark in the row, outweighing
                    // the bank it belongs to. Outlined matches the rest of WHFIN's iconography.
                    icon = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.account_source_details),
                    onClick = onOpenGroupDetails,
                    outlined = false,
                )
            }
            containers.forEach { ibanAccounts ->
                IbanCard(
                    sourceName = name,
                    accounts = ibanAccounts,
                    total = containerTotals[accountContainerKey(ibanAccounts.first().account)],
                    onOpenTransactions = onOpenTransactions,
                    onOpenAccountDetails = onOpenAccountDetails,
                    onAdjustBalance = onAdjustBalance,
                )
            }
    }
}

/** Card-backed/current money leads; imported IBAN order is not a statement of daily importance. */
internal fun orderedAccountContainers(accounts: List<AccountWithBalance>): List<List<AccountWithBalance>> =
    accounts.groupBy { accountContainerKey(it.account) }
        .values
        .sortedWith(
            compareBy<List<AccountWithBalance>> { container ->
                if (container.any { it.primaryCardMasks.isNotEmpty() }) 0 else 1
            }.thenBy { container ->
                if (container.any { it.cardMasks.isNotEmpty() }) 0 else 1
            }.thenBy { container ->
                if (container.any { it.account.bankProduct == BankProduct.CURRENT_ACCOUNT }) 0 else 1
            }.thenBy { container ->
                container.minOf { it.account.sortOrder }
            }.thenBy { container ->
                container.first().account.iban?.takeLast(4).orEmpty()
            },
        )

@Composable
private fun AccountGroupDetailsDialog(
    name: String,
    accounts: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    onOpenStatements: (() -> Unit)?,
    onOpenAccountActivity: (List<AccountWithBalance>) -> Unit,
) {
    val containers = accounts
        .groupBy { it.account.iban ?: "account-${it.account.id}" }
        .values
        .toList()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        WhfinDialogSystemBars()
        DemoWorkspaceFrame {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WhfinIconButton(Icons.Default.Close, stringResource(R.string.action_cancel), onDismiss, outlined = false)
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                LazyColumn(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onOpenStatements != null) item(key = "group-statements") {
                        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                            WhfinLedgerRow(
                                title = stringResource(R.string.statements_title),
                                supportingText = stringResource(R.string.account_statements_hint),
                                icon = Icons.Default.Description,
                                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                onClick = {
                                    onDismiss()
                                    onOpenStatements()
                                },
                            )
                        }
                    }
                    item(key = "group-accounts-label") {
                        WhfinSectionLabel(stringResource(R.string.account_group_accounts))
                    }
                    items(containers, key = { container ->
                        val first = container.first().account
                        first.iban ?: "account-${first.id}"
                    }) { container ->
                        val first = container.first()
                        val cards = (container.flatMap { it.cardMasks }.map { "••$it" } +
                            container.flatMap { it.virtualCardMasks }.map { "${stringResource(R.string.account_card_virtual)} ••$it" })
                            .distinct()
                        val supporting = buildList {
                            first.account.iban?.let(::add)
                            add(container.joinToString(" · ") { it.account.currency })
                            if (cards.isNotEmpty()) add(stringResource(R.string.account_cards) + ": " + cards.joinToString(" · "))
                        }.joinToString("\n")
                        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                            WhfinLedgerRow(
                                title = first.account.name,
                                supportingText = supporting,
                                supportingMaxLines = 4,
                                icon = accountTypeIcon(first.account.type),
                                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                onClick = { onOpenAccountActivity(container) },
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun IbanCard(
    sourceName: String,
    accounts: List<AccountWithBalance>,
    total: ConvertedTotal?,
    onOpenTransactions: (AccountWithBalance) -> Unit,
    onOpenAccountDetails: (List<AccountWithBalance>) -> Unit,
    onAdjustBalance: (AccountWithBalance) -> Unit,
) {
    val iban = accounts.first().account.iban
    WhfinLedgerGroup {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = { onOpenAccountDetails(accounts) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                accounts.first().account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // One line, and only about the card this account is actually paid with.
                            // The full list of masks, the virtual cards and the IBAN belong to the
                            // account's own screen: enumerated here they wrapped the heading onto a
                            // third line and told the owner nothing they did not already know.
                            val payingCard = payingCardMask(accounts)
                            if (payingCard != null) Text(
                                "•$payingCard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        val headlineTotal = when {
                            total?.isComplete == true && total.amount != null ->
                                formatDecimal(total.amount, total.currency) to total.currency
                            accounts.size == 1 -> accounts.first().let { only ->
                                formatMinor(only.balanceMinor, only.account.currency) to only.account.currency
                            }
                            // Several currencies without a full set of rates: the rows below say
                            // each one honestly, and a partial sum here would be a wrong number.
                            else -> null
                        }
                        if (headlineTotal != null) WhfinAmount(
                            text = headlineTotal.first,
                            symbol = currencySymbol(headlineTotal.second),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.account_bank_mapping),
                        )
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            val sorted = accounts.sortedWith(
                compareBy<AccountWithBalance> { if (it.account.currency == "GEL") 0 else 1 }
                    .thenBy { it.account.currency },
            )
            // A bank routinely names every currency ledger of one account the same. When it does,
            // the code moves up into the title: two rows reading "Everyday" differ only by a line
            // the eye skips. The choice is made once for the whole card, so its rows keep one
            // height instead of alternating between one and two lines.
            val titles = sorted.map { accountRowTitle(it) }
            val currencyInTitle = titles.distinct().size < titles.size
            sorted.forEachIndexed { index, item ->
                CurrencyAccountRow(
                    item = item,
                    currencyInTitle = currencyInTitle,
                    sourceName = sourceName,
                    containerTitle = accounts.first().account.name,
                    onClick = { onOpenTransactions(item) },
                    onAdjustBalance = { onAdjustBalance(item) },
                )
                if (index != accounts.lastIndex) HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                )
            }
        }
    }
}

/**
 * The card this account is actually paid with, if one can be named without guessing.
 *
 * A primary card answers it outright; a single physical card answers it by having no rival. More
 * than one physical card and no primary is a question the heading must not invent an answer to.
 */
private fun payingCardMask(accounts: List<AccountWithBalance>): String? {
    val physical = accounts.flatMap { it.cardMasks }.distinct()
    val primary = accounts.flatMap { it.primaryCardMasks }.distinct()
    return physical.firstOrNull { it in primary }
        ?: physical.singleOrNull()
}

/**
 * The names WHFIN writes for a cash ledger nobody has renamed.
 *
 * They are placeholders, not descriptions: the source heading already says the same thing, in the
 * language the screen is actually being read in.
 */
private val SEEDED_CASH_NAMES = setOf("Cash", "Наличные")

/** What identifies this ledger: its own name, else the bank product, else the bare currency. */
@Composable
private fun accountRowTitle(item: AccountWithBalance): String =
    item.account.name.takeIf { it.isNotBlank() && it != item.account.currency }
        ?: accountProductLabel(item.account.bankProduct)
        ?: item.account.currency

@Composable
private fun accountProductLabel(product: BankProduct?): String? = when (product) {
    BankProduct.CURRENT_ACCOUNT -> stringResource(R.string.account_product_current)
    BankProduct.DEMAND_DEPOSIT -> stringResource(R.string.account_product_demand_deposit)
    BankProduct.TERM_DEPOSIT -> stringResource(R.string.account_product_term_deposit)
    null -> null
}

@Composable
private fun CurrencyAccountRow(
    item: AccountWithBalance,
    currencyInTitle: Boolean,
    sourceName: String,
    containerTitle: String? = null,
    onClick: () -> Unit,
    onAdjustBalance: () -> Unit,
) {
    val isFocusedPhysicalCardLedger = item.cardMasks.isNotEmpty() && (
        !item.primaryCardConfigured || item.primaryCardMasks.any(item.cardMasks::contains)
    )
    val cardBalanceStatus = if (
        item.account.currency.equals("GEL", ignoreCase = true) && isFocusedPhysicalCardLedger
    ) physicalCardBalanceStatus(item.balanceMinor) else PhysicalCardBalanceStatus.Enough
    val balanceColor = when (cardBalanceStatus) {
        PhysicalCardBalanceStatus.Enough -> Color.Unspecified
        PhysicalCardBalanceStatus.Low -> WhfinThemeTokens.colors.warning
        PhysicalCardBalanceStatus.Critical -> MaterialTheme.colorScheme.error
    }
    // What names this row is what the money is for — "Everyday", "Travel", "Депозит". The currency
    // used to lead it, set in bold, while the amount beside it already carried the same currency in
    // its symbol: the loudest word on the row was the one word it did not need.
    // WHFIN names the cash ledger it seeds itself, in whatever language was current at the time, so
    // that name can end up in one language under a heading written in another — "Наличные / Cash",
    // the same word twice. Those names are our own output, not something the user typed, so a row
    // carrying one has nothing of its own to say and leads with its currency instead.
    // The card heading directly above prints the account's name once. A ledger that carries the
    // same name has nothing left to add and leads with its currency: "Everyday / Everyday · GEL /
    // Everyday · USD" said one word three times and hid the only difference at the end of the line.
    val ownName = accountRowTitle(item)
        .takeUnless {
            it.equals(sourceName, ignoreCase = true) ||
                it.equals(containerTitle, ignoreCase = true) ||
                it in SEEDED_CASH_NAMES
        }
    val name = ownName ?: item.account.currency
    val title = if (currencyInTitle && ownName != null) "$name · ${item.account.currency}" else name
    // The purpose is not repeated beside a named account: the section heading above already says
    // whether this is everyday money or savings, and an account called "Term deposit" does not need
    // "Deposit" under it. It only surfaces as the title of a ledger with no name of its own.
    val balanceStatusLabel = when (cardBalanceStatus) {
        PhysicalCardBalanceStatus.Enough -> null
        PhysicalCardBalanceStatus.Low -> stringResource(R.string.low_balance_status_low)
        PhysicalCardBalanceStatus.Critical -> stringResource(R.string.low_balance_status_critical)
    }
    val detail = listOfNotNull(
        item.account.currency.takeUnless { currencyInTitle || ownName == null },
        balanceStatusLabel,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.account_transactions_title), onClick = onClick)
                .padding(end = 8.dp).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotEmpty()) Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onAdjustBalance,
            shape = MaterialTheme.shapes.small,
            color = Color.Transparent,
        ) {
            WhfinAmount(
                formatMinor(item.balanceMinor, item.account.currency),
                symbol = currencySymbol(item.account.currency),
                style = MaterialTheme.typography.titleMedium,
                color = balanceColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
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
                    detail + if (item.account.fundRole == FundRole.RESERVE) {
                        " · ${stringResource(R.string.accounts_reserve)}"
                    } else "",
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
    // A bank names every currency ledger of one account the same, which is what makes the currency
    // move into the title; the savings row has no name of its own and falls back to its purpose.
    val accounts = listOf(
        AccountWithBalance(
            AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001"),
            12_500, listOf("0001"), primaryCardMasks = listOf("0001"), primaryCardConfigured = true,
            groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(id = 2, name = "Everyday", type = AccountType.BANK, groupId = 1, currency = "USD", iban = "GE00CD0000000000000001"),
            2_360, emptyList(), primaryCardMasks = listOf("0001"), primaryCardConfigured = true,
            groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(
                id = 3, name = "EUR", type = AccountType.SAVINGS, groupId = 1, currency = "EUR",
                iban = "GE00CD0000000000000002", fundRole = FundRole.RESERVE,
                bankProduct = BankProduct.DEMAND_DEPOSIT,
            ),
            81_500, emptyList(), groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(
                id = 4, name = "Groceries backup", type = AccountType.BANK, groupId = 1,
                currency = "GEL", iban = "GE00CD0000000000000003",
                bankProduct = BankProduct.CURRENT_ACCOUNT,
            ),
            9_500, listOf("0002"), groupName = "Credo",
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WhfinContextHeader(
                    stringResource(R.string.accounts_net_worth),
                    formatMinor(559_417, "GEL"),
                    valueSymbol = currencySymbol("GEL"),
                ) {
                    WhfinIconButton(Icons.Default.Add, "Add", {}, outlined = false)
                    WhfinIconButton(Icons.Default.BarChart, "Overview", {}, outlined = false)
                    WhfinIconButton(Icons.Default.Settings, "Settings", {}, outlined = false)
                }
                Column(Modifier.padding(20.dp)) {
                    AccountsSummary(accounts)
                    WhfinSectionLabel(stringResource(R.string.accounts_everyday_section))
                    AccountGroupCard(
                        name = "Credo",
                        accounts = accounts,
                        onOpenTransactions = {},
                        onOpenAccountDetails = {},
                        onAdjustBalance = {},
                        onOpenGroupDetails = {},
                    )
                    DebtsSummary(emptyList(), {})
                }
            }
        }
    }
}
