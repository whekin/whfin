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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.whekin.whfin.core.ui.WhfinSkeleton
import dev.whekin.whfin.core.ui.WhfinSkeletonBlock
import dev.whekin.whfin.core.ui.WhfinSkeletonLedgerRow
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
    onOpenSavings: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAccountTransactions: (Long) -> Unit = {},
    viewModel: AccountsViewModel = viewModel(),
    statementsViewModel: BankStatementsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsState()
    val restoring by viewModel.restoring.collectAsState()
    // Mid-restore the tables are empty on purpose; the list of accounts is not yet a fact.
    val readyState = (screenState as? AccountsScreenState.Ready)?.takeIf { !restoring }
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
                // Reading a local database takes milliseconds, and a sentence about it was the wrong
                // shape for that moment: it announced work instead of showing what is arriving. The
                // silhouette of the rows about to land says the same thing and does not move the
                // screen when they do.
                WhfinSkeleton(
                    contentDescription = stringResource(R.string.accounts_loading),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    WhfinSkeletonBlock(Modifier.fillMaxWidth(.3f), height = 11.dp)
                    repeat(3) { WhfinSkeletonLedgerRow() }
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
                            AccountsSummary(ledgerAccounts, onOpenSavings)
                        }
                    }
                    listOf(
                        Triple(
                            R.string.accounts_everyday_section,
                            Icons.Outlined.CreditCard,
                            everydayAccounts,
                        ),
                        Triple(
                            R.string.accounts_savings_section,
                            Icons.Outlined.Savings,
                            savingsAccounts,
                        ),
                    ).forEach { (sectionLabel, sectionIcon, sectionAccounts) ->
                        if (sectionAccounts.isNotEmpty()) {
                            item(key = "account-section-$sectionLabel") {
                                // Uniform gaps put the same distance between two accounts of one
                                // bank and between everyday money and savings. A section is the
                                // larger break of the two and now reads as one.
                                WhfinSectionLabel(
                                    stringResource(sectionLabel),
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 20.dp, bottom = 2.dp),
                                    icon = sectionIcon,
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
                                WhfinSectionLabel(
                                    stringResource(R.string.accounts_archived_section),
                                    icon = Icons.Outlined.Inventory2,
                                )
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
            onConfirmWithProduct = { name, type, currency, bankProvider, openingMinor, bankProduct ->
                viewModel.addAccount(name, type, currency, bankProvider, openingMinor, bankProduct)
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
            currencies = rows.map { it.account.currency }.distinct().sorted(),
            onDismiss = { bankDetailsFor = null },
            onConfirm = { name, fundRole, iban, bankProduct, physicalCards, virtualCards, primaryCard ->
                viewModel.updateBankMapping(
                    rows.map { it.account },
                    name,
                    fundRole,
                    iban,
                    bankProduct,
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
            onConfirm = { name, currency, address, fundRole ->
                viewModel.editAccount(item.account, name, currency, address, fundRole)
                editAccountFor = null
            },
        )
    }

}

@Composable
private fun AccountsSummary(accounts: List<AccountWithBalance>, onOpenSavings: (() -> Unit)? = null) {
    // Chain balances are read, not summed from transactions, so they have their own reading below
    // and never mix into these currency chips.
    val all = accounts.groupBy { it.account.currency }
        .mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val available = accounts.filter { it.account.fundRole == FundRole.AVAILABLE }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    val reserve = accounts.filter { it.account.fundRole == FundRole.RESERVE }
        .groupBy { it.account.currency }.mapValues { (_, list) -> list.sumOf { it.balanceMinor } }
    // Two naked columns set in the same size as a balance four levels below them: the two numbers
    // the screen exists to answer had the least weight on it. They get a surface of their own and a
    // step up in size, so the descent from here down is readable — net worth, then these, then an
    // account, then a ledger.
    WhfinLedgerGroup(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), tonal = true) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryColumn(
                stringResource(R.string.accounts_available),
                formatMinor(available["GEL"] ?: 0L, "GEL"),
                currencySymbol("GEL"),
                Icons.Outlined.AccountBalanceWallet,
                Modifier.weight(1f),
            )
            VerticalDivider(
                Modifier.height(44.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SummaryColumn(
                stringResource(R.string.accounts_reserve),
                reserve["GEL"]?.let { formatMinor(it, "GEL") } ?: "—",
                reserve["GEL"]?.let { currencySymbol("GEL") },
                Icons.Outlined.Savings,
                Modifier.weight(1f),
                onClick = onOpenSavings?.takeIf { reserve.isNotEmpty() },
            )
        }
        // Available and reserve are lari only, so on a multi-currency ledger they cannot add up to
        // the total above. Naming what is missing turns three numbers that seem to disagree into
        // three numbers that explain each other; chips repeating the rows below did not.
        // A currency holding nothing explains no part of the gap between these two numbers and the
        // total above them, so listing it only lengthens the sentence: "0.00 € · $4.97".
        val unconverted = all.entries
            .filter { it.key != "GEL" && it.value != 0L }
            .sortedBy { it.key }
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
}

@Composable
private fun SummaryColumn(
    label: String,
    value: String,
    symbol: String?,
    icon: ImageVector,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Both labels sit on one line whether or not the column can be opened: the chevron is
            // taller than the label it stands beside, so a row that merely wrapped its content put
            // the two headings — and the two amounts under them — at different heights.
            Row(
                Modifier.height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Two adjacent columns of the same size answer two different questions; the mark
                // says which is which before the words are read.
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WhfinFieldLabel(label, Modifier.weight(1f))
                if (onClick != null) Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WhfinAmount(value, symbol = symbol, style = MaterialTheme.typography.headlineSmall)
        }
    }
    if (onClick == null) Box(modifier, contentAlignment = Alignment.CenterStart) {
        content()
    } else Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        content = content,
    )
}

@Composable
private fun AccountGroupCard(
    name: String,
    accounts: List<AccountWithBalance>,
    containerTotals: Map<String, ConvertedTotal> = emptyMap(),
    onOpenTransactions: (AccountWithBalance) -> Unit,
    onOpenAccountDetails: (List<AccountWithBalance>) -> Unit,
    onOpenGroupDetails: () -> Unit,
) {
    val containers = orderedAccountContainers(accounts)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A bank is not a screen title. Set in the editorial serif it outweighed the balance it
            // belongs to, and repeated once per section it made one bank read as two — the serif is
            // reserved for screen titles, key totals and rare landmarks.
            Row(
                Modifier.fillMaxWidth().padding(start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The mark that says what kind of money this is belongs to the source, which is the
                // level where the answer changes. Repeated on every account of one bank it said
                // "bank" three times under a heading already reading "Credo", and it left the
                // deepest level of the screen as the only one with a colour anchor on it.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            accountTypeIcon(accounts.first().account.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
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
) {
    val sorted = accounts.sortedWith(
        compareBy<AccountWithBalance> { if (it.account.currency == "GEL") 0 else 1 }
            .thenBy { it.account.currency },
    )
    val containerTitle = accountContainerTitle(sorted, sourceName)
    val payingCard = payingCardMask(sorted)
    // An account holding one currency is one balance, and printing it under a heading that repeats
    // the same thing spent two rows saying it once. The heading becomes that balance's own row.
    val single = sorted.singleOrNull()
    val singleStatus = single?.let { cardBalanceStatus(it) }
    val title = when {
        single == null -> containerTitle
        // A pile of cash under the heading "Cash" is named by the only thing that tells it apart.
        containerTitle.equals(sourceName, ignoreCase = true) -> single.account.currency
        else -> containerTitle
    }
    val headlineAmount = when {
        single != null -> formatMinor(single.balanceMinor, single.account.currency) to single.account.currency
        // A sum is worth printing when it is a sum, and only when every currency in it has a rate;
        // a partial sum would be a wrong number, and the strip below says each one honestly.
        total?.isComplete == true && total.amount != null ->
            formatDecimal(total.amount, total.currency) to total.currency
        else -> null
    }
    // Outlined on outlined on outlined, every level of this screen was a 1 dp rectangle on the same
    // ground, and the eye had no way to tell which level it was reading. An account is the thing you
    // actually point at here, so it is the one that gets a surface.
    WhfinLedgerGroup(tonal = true) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        // Tapping a balance opens its ledger; the pencil beside it opens the
                        // account. Where the heading is not itself a balance it is not a door.
                        if (single == null) Modifier else Modifier.clickable(
                            onClickLabel = stringResource(R.string.account_transactions_title),
                            onClick = { onOpenTransactions(single) },
                        ),
                    )
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // One line, and only about the card this account is actually paid with. The full
                    // list of masks, the virtual cards and the IBAN belong to the account's own
                    // screen: enumerated here they wrapped the heading onto a third line and told
                    // the owner nothing they did not already know.
                    val supporting = listOfNotNull(
                        payingCard?.let { "••$it" },
                        singleStatus?.let { balanceStatusLabel(it) },
                    ).joinToString(" · ")
                    if (supporting.isNotEmpty()) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (payingCard != null) Icon(
                            Icons.Outlined.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (headlineAmount != null) WhfinAmount(
                    text = headlineAmount.first,
                    symbol = currencySymbol(headlineAmount.second),
                    style = MaterialTheme.typography.titleLarge,
                    color = single?.let { balanceAmountColor(it, singleStatus!!) } ?: Color.Unspecified,
                )
                // A chevron promises a page; this opens the account's editor. The pencil says which
                // of the two doors on this card is being pointed at — the other one is the money.
                WhfinIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.account_edit),
                    onClick = { onOpenAccountDetails(accounts) },
                    outlined = false,
                )
            }
            if (single == null) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                AccountCurrencies(
                    accounts = sorted,
                    containerTitle = containerTitle,
                    sourceName = sourceName,
                    onOpen = onOpenTransactions,
                )
            }
        }
    }
}

/**
 * The balances of one account, as compactly as they can still be read.
 *
 * An account rarely holds more than three currencies, and each of them is one short number, so a
 * full ledger row each spent a third of the screen restating what the card above had already said.
 * Two or three fit side by side as a strip; one, four, or a large font scale keep the stacked rows,
 * because a compact cell would have to truncate the very number it exists to show.
 */
@Composable
private fun AccountCurrencies(
    accounts: List<AccountWithBalance>,
    containerTitle: String,
    sourceName: String,
    onOpen: (AccountWithBalance) -> Unit,
) {
    val compact = accounts.size in 2..MAX_CURRENCY_CELLS &&
        LocalDensity.current.fontScale < COMPACT_CURRENCY_FONT_SCALE
    if (compact) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            accounts.forEachIndexed { index, item ->
                if (index > 0) VerticalDivider(
                    Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
                )
                CurrencyCell(
                    item = item,
                    label = currencyLedgerLabel(item, containerTitle, sourceName),
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen(item) },
                )
            }
        }
        return
    }
    accounts.forEachIndexed { index, item ->
        CurrencyLedgerRow(
            item = item,
            label = currencyLedgerLabel(item, containerTitle, sourceName),
            onClick = { onOpen(item) },
        )
        if (index != accounts.lastIndex) HorizontalDivider(
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
        )
    }
}

@Composable
private fun CurrencyCell(
    item: AccountWithBalance,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val status = cardBalanceStatus(item)
    val amount = formatMinor(item.balanceMinor, item.account.currency)
    val statusLabel = balanceStatusLabel(status)
    val description = listOfNotNull(label, item.account.currency.takeUnless { it == label }, amount, statusLabel)
        .joinToString(", ")
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().semantics(mergeDescendants = true) {
            contentDescription = description
        },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Column(
            Modifier
                .heightIn(min = WhfinThemeTokens.sizes.minTouchTarget)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Colour alone must not carry "this card is nearly empty"; at this size the mark is
                // also the only room the sentence would have had.
                if (status != PhysicalCardBalanceStatus.Enough) Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = balanceStatusColor(status),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WhfinAmount(
                amount,
                symbol = currencySymbol(item.account.currency),
                style = MaterialTheme.typography.titleMedium,
                color = balanceAmountColor(item, status),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CurrencyLedgerRow(
    item: AccountWithBalance,
    label: String,
    onClick: () -> Unit,
) {
    val status = cardBalanceStatus(item)
    val detail = listOfNotNull(
        item.account.currency.takeUnless { it == label },
        balanceStatusLabel(status),
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.account_transactions_title), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotEmpty()) Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WhfinAmount(
            formatMinor(item.balanceMinor, item.account.currency),
            symbol = currencySymbol(item.account.currency),
            style = MaterialTheme.typography.titleMedium,
            color = balanceAmountColor(item, status),
        )
    }
}

/** Whether this ledger is the one a physical card pays from, and therefore whether it can run dry. */
@Composable
private fun cardBalanceStatus(item: AccountWithBalance): PhysicalCardBalanceStatus {
    val isFocusedPhysicalCardLedger = item.cardMasks.isNotEmpty() && (
        !item.primaryCardConfigured || item.primaryCardMasks.any(item.cardMasks::contains)
    )
    return if (
        item.account.currency.equals("GEL", ignoreCase = true) && isFocusedPhysicalCardLedger
    ) physicalCardBalanceStatus(item.balanceMinor) else PhysicalCardBalanceStatus.Enough
}

@Composable
private fun balanceStatusColor(status: PhysicalCardBalanceStatus): Color = when (status) {
    PhysicalCardBalanceStatus.Enough -> Color.Unspecified
    PhysicalCardBalanceStatus.Low -> WhfinThemeTokens.colors.warning
    PhysicalCardBalanceStatus.Critical -> MaterialTheme.colorScheme.error
}

@Composable
private fun balanceStatusLabel(status: PhysicalCardBalanceStatus): String? = when (status) {
    PhysicalCardBalanceStatus.Enough -> null
    PhysicalCardBalanceStatus.Low -> stringResource(R.string.low_balance_status_low)
    PhysicalCardBalanceStatus.Critical -> stringResource(R.string.low_balance_status_critical)
}

/** An empty ledger is a fact worth stating quietly; the eye should land on the money that exists. */
@Composable
private fun balanceAmountColor(
    item: AccountWithBalance,
    status: PhysicalCardBalanceStatus,
): Color = balanceStatusColor(status).takeOrElse {
    if (item.balanceMinor == 0L) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
}

/**
 * What names this account under a bank heading that has already named the bank.
 *
 * A statement names every ledger it creates "<Bank> <CUR> •<last4>", so an account holding three
 * currencies printed its bank and its account number three times below a heading carrying both.
 * What survives that removal is the part a person chose; when nothing does, the account number is
 * the honest name, because that is all the bank ever gave it.
 */
@Composable
private fun accountContainerTitle(accounts: List<AccountWithBalance>, sourceName: String): String {
    val primary = accounts.firstOrNull { it.account.currency == "GEL" } ?: accounts.first()
    val own = accounts.asSequence()
        .sortedByDescending { it.account.id == primary.account.id }
        .mapNotNull { ledgerOwnName(it, sourceName) }
        .firstOrNull()
    val mask = primary.account.iban?.takeLast(4)
    return own
        ?: mask?.let { stringResource(R.string.account_iban_short, it) }
        ?: accountProductLabel(primary.account.bankProduct)
        ?: accountTypeLabel(primary.account.type)
}

/** The label of one currency inside a card that has already said everything the two share. */
@Composable
private fun currencyLedgerLabel(
    item: AccountWithBalance,
    containerTitle: String,
    sourceName: String,
): String = ledgerOwnName(item, sourceName)
    ?.takeUnless { it.equals(containerTitle, ignoreCase = true) }
    ?: item.account.currency

/**
 * The part of a ledger's name that the screen has not already printed above it, or null.
 *
 * WHFIN writes the seeded cash names itself, in whatever language was current at the time, so such
 * a name can appear in one language under a heading written in another — the same word twice.
 */
internal fun ledgerOwnName(item: AccountWithBalance, sourceName: String?): String? {
    val account = item.account
    var name = account.name
    if (!sourceName.isNullOrBlank()) name = name.replace(sourceName, " ", ignoreCase = true)
    name = name.replace(
        Regex("(?<!\\p{L})${Regex.escape(account.currency)}(?!\\p{L})", RegexOption.IGNORE_CASE),
        " ",
    )
    account.iban?.takeLast(4)?.let { tail ->
        name = name.replace(Regex("[•·]?\\s*${Regex.escape(tail)}"), " ")
    }
    return name.replace(Regex("[\\s•·,\\-]+"), " ").trim()
        .takeIf { it.isNotBlank() && it !in SEEDED_CASH_NAMES }
}

/** Beyond three, side-by-side cells stop being readable and the stacked rows say it better. */
private const val MAX_CURRENCY_CELLS = 3

/** Above this scale a cell would have to truncate its own amount, so the rows take over. */
private const val COMPACT_CURRENCY_FONT_SCALE = 1.3f

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

@Composable
private fun accountProductLabel(product: BankProduct?): String? = when (product) {
    BankProduct.CURRENT_ACCOUNT -> stringResource(R.string.account_product_current)
    BankProduct.DEMAND_DEPOSIT -> stringResource(R.string.account_product_demand_deposit)
    BankProduct.TERM_DEPOSIT -> stringResource(R.string.account_product_term_deposit)
    null -> null
}

@Preview(name = "Accounts populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Accounts dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Accounts font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsContentPreview() {
    // A statement names every ledger it creates after its bank, its currency and its account number,
    // so the first account here is what an imported one really looks like: nothing in those three
    // names belongs to this card alone. The second one was renamed by hand and keeps that name.
    val accounts = listOf(
        AccountWithBalance(
            AccountEntity(id = 1, name = "Credo GEL •0001", type = AccountType.BANK, groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001"),
            5_761, listOf("0001"), primaryCardMasks = listOf("0001"), primaryCardConfigured = true,
            groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(id = 2, name = "Credo EUR •0001", type = AccountType.BANK, groupId = 1, currency = "EUR", iban = "GE00CD0000000000000001"),
            0, emptyList(), primaryCardMasks = listOf("0001"), primaryCardConfigured = true,
            groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(id = 3, name = "Credo USD •0001", type = AccountType.BANK, groupId = 1, currency = "USD", iban = "GE00CD0000000000000001"),
            2_360, emptyList(), primaryCardMasks = listOf("0001"), primaryCardConfigured = true,
            groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(
                id = 4, name = "Groceries backup", type = AccountType.BANK, groupId = 1,
                currency = "GEL", iban = "GE00CD0000000000000003",
                bankProduct = BankProduct.CURRENT_ACCOUNT,
            ),
            9_500, listOf("0002"), groupName = "Credo",
        ),
        AccountWithBalance(
            AccountEntity(
                id = 5, name = "Travel", type = AccountType.BANK, groupId = 1,
                currency = "EUR", iban = "GE00CD0000000000000003",
                bankProduct = BankProduct.CURRENT_ACCOUNT,
            ),
            16_930, emptyList(), groupName = "Credo",
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
                    WhfinSectionLabel(
                        stringResource(R.string.accounts_everyday_section),
                        icon = Icons.Outlined.CreditCard,
                    )
                    AccountGroupCard(
                        name = "Credo",
                        accounts = accounts,
                        onOpenTransactions = {},
                        onOpenAccountDetails = {},
                        onOpenGroupDetails = {},
                    )
                    DebtsSummary(emptyList(), {})
                }
            }
        }
    }
}
