package dev.whekin.whfin.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.ui.parseToMinor
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.components.CategoryGrid
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.data.db.CategoryKind
import androidx.compose.ui.text.style.TextOverflow
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.convertedTotalLabel
import dev.whekin.whfin.ui.formatDecimal
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinActionMenu
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinTotalRule
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.mutation.MutationRejection
import androidx.compose.material.icons.filled.ReportProblem
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.notifications.PhysicalCardBalanceStatus
import dev.whekin.whfin.data.notifications.physicalCardBalanceStatus
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.sms.SmsRoutingSheet
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame
import dev.whekin.whfin.ui.analytics.AnalyticsUiModel
import dev.whekin.whfin.ui.analytics.AnalyticsCurrencyValue
import dev.whekin.whfin.ui.analytics.AnalyticsUiState
import dev.whekin.whfin.ui.analytics.AnalyticsViewModel

internal sealed interface FeedTimelineEntry {
    val day: LocalDate
    val occurredAt: Long
    val amountMinor: Long

    data class Transaction(val item: FeedItem) : FeedTimelineEntry {
        override val day: LocalDate = item.day
        override val occurredAt: Long = item.tx.occurredAt
        override val amountMinor: Long = item.tx.amountMinor
    }

    data class Unrouted(val operation: UnroutedOperation) : FeedTimelineEntry {
        override val day: LocalDate = operation.day
        override val occurredAt: Long = operation.diagnostic.occurredAt
            ?: operation.diagnostic.receivedAt
        override val amountMinor: Long = operation.diagnostic.amountMinor ?: 0L
    }
}

enum class FeedMode { HOME, HISTORY }

internal data class TransactionPresentationAmount(val minor: Long, val currency: String)

/**
 * A foreign card SMS knows the purchase amount before it knows the account charge. The ledger keeps
 * that charge at zero until the statement supplies bank truth; presentation must not turn the known
 * purchase into a fictional 0 GEL operation in the meantime.
 */
internal fun transactionPresentationAmount(tx: TransactionEntity): TransactionPresentationAmount =
    if (tx.amountMinor == 0L && tx.origAmountMinor != null && tx.origCurrency != null) {
        TransactionPresentationAmount(tx.origAmountMinor, tx.origCurrency)
    } else {
        TransactionPresentationAmount(tx.amountMinor, tx.currency)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    mode: FeedMode = FeedMode.HISTORY,
    showSmsOnboarding: Boolean,
    onEnableSms: () -> Unit,
    onDismissSmsOnboarding: () -> Unit,
    showCredoSyncReminder: Boolean = true,
    showSetupInvitation: Boolean = false,
    onResumeSetup: () -> Unit = {},
    onDismissSetupInvitation: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenCredoSync: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    hasLowBalanceNotificationPermission: Boolean = true,
    onRequestLowBalanceNotificationPermission: () -> Unit = {},
    addRequestKey: Int = 0,
    onAddRequestConsumed: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
) {
    val homeAnalyticsState = collectHomeAnalyticsState(mode == FeedMode.HOME)
    val items by viewModel.items.collectAsState()
    val netWorth by viewModel.netWorth.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoriesByUsage by viewModel.categoriesByUsage.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val people by viewModel.people.collectAsState()
    val unroutedOperations by viewModel.unroutedOperations.collectAsState()
    val rejected by viewModel.rejected.collectAsState()
    val integrityIssues by viewModel.integrityIssues.collectAsState()
    val credoReminder by viewModel.credoSyncReminder.collectAsState()
    val physicalCardBalances by viewModel.physicalCardBalances.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // A refused change is never silent: the data stayed as it was, and saying why beats leaving the
    // user to discover later that the row they picked is still there.
    val rejectionMessages = mapOf(
        MutationRejection.IMPORTED_IS_PROTECTED to stringResource(R.string.mutation_rejected_imported),
        MutationRejection.DEBT_LINKED to stringResource(R.string.mutation_rejected_debt),
        MutationRejection.ALLOCATIONS_LOCK_AMOUNT to stringResource(R.string.mutation_rejected_allocations),
        MutationRejection.ALREADY_CORRECTED to stringResource(R.string.mutation_rejected_already_corrected),
        MutationRejection.INCOME_NOT_SHAREABLE to stringResource(R.string.mutation_rejected_income),
        MutationRejection.INVALID_INPUT to stringResource(R.string.mutation_rejected),
    )
    LaunchedEffect(rejected) {
        rejected?.let { reason ->
            snackbarHostState.showSnackbar(rejectionMessages.getValue(reason))
            viewModel.dismissRejection()
        }
    }
    val smsRoutingAccounts by viewModel.smsRoutingAccounts.collectAsState()
    // Confirming a draft is reversible from the transaction details, so it needs no ceremony here.
    val confirmPending: (FeedItem) -> Unit = { viewModel.updateStatus(it, TxStatus.CONFIRMED) }
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var routingFor by remember { mutableStateOf<UnroutedOperation?>(null) }
    var categoryFor by remember { mutableStateOf<FeedItem?>(null) }
    var deleteFor by remember { mutableStateOf<FeedItem?>(null) }
    var correctFor by remember { mutableStateOf<FeedItem?>(null) }
    var debtFor by remember { mutableStateOf<FeedItem?>(null) }
    var splitFor by remember { mutableStateOf<FeedItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf<ManualPrefill?>(null) }
    var editFor by remember { mutableStateOf<FeedItem?>(null) }
    var statusFor by remember { mutableStateOf<FeedItem?>(null) }
    var expandedTransferDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var expandedExpenseDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FeedFilter.ALL) }
    var sort by remember { mutableStateOf(FeedSort.NEWEST) }
    var categoryFilters by remember { mutableStateOf(emptySet<Long>()) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchStatus by remember { mutableStateOf(false) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var noticesExpanded by remember { mutableStateOf(false) }
    val monthFlow by viewModel.monthFlow.collectAsState()
    val attention by viewModel.attention.collectAsState()
    val recent by viewModel.recentActivity.collectAsState()
    val spendable by viewModel.spendable.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()
    val recurringDue by viewModel.recurringDue.collectAsState()
    val debtsOwed by viewModel.debtsOwed.collectAsState()
    val feedLoaded by viewModel.feedLoaded.collectAsState()
    val incomeSources by viewModel.incomeSources.collectAsState()
    val homeAnalytics = (homeAnalyticsState?.state as? AnalyticsUiState.Content)?.data
    val income = homeAnalytics?.incomeMinor ?: monthFlow.incomeMinor
    val expenses = homeAnalytics?.expenseMinor ?: monthFlow.expenseMinor
    val homeInsights = homeAnalytics?.let(::deriveHomeInsights).orEmpty()
    val runway = remember(spendable, homeAnalytics, incomeSources) {
        homeRunway(spendable?.pivotMinor, homeAnalytics, incomeSources, LocalDate.now())
    }
    val visibleItems = items.filter { item ->
        val matchesType = when (filter) {
            FeedFilter.ALL -> true
            FeedFilter.EXPENSES -> !item.tx.isTransfer && item.tx.amountMinor < 0 && !item.isDebt
            FeedFilter.INCOME -> !item.tx.isTransfer && item.tx.amountMinor > 0
            FeedFilter.TRANSFERS -> item.tx.isTransfer || item.tx.transferGroupId != null
            FeedFilter.DRAFTS -> item.tx.status == TxStatus.PENDING
        }
        val haystack = listOfNotNull(
            item.transferSummary, item.merchant?.displayName, item.tx.rawCounterparty,
            item.tx.note, item.account?.name, item.account?.iban, item.category?.name,
            item.day.toString(),
            item.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            item.day.month.name,
            item.tx.currency,
            (kotlin.math.abs(item.tx.amountMinor) / 100.0).toString(),
        ).joinToString(" ")
        val matchesCategory = categoryFilters.isEmpty() || item.tx.categoryId in categoryFilters
        matchesType && matchesCategory && (search.isBlank() || haystack.contains(search.trim(), ignoreCase = true))
    }
    val visibleUnrouted = unroutedOperations.filter { operation ->
        val diagnostic = operation.diagnostic
        val matchesType = when (filter) {
            FeedFilter.ALL -> true
            FeedFilter.EXPENSES -> diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT
            FeedFilter.INCOME -> diagnostic.kind == SmsDiagnosticKind.INCOMING_TRANSFER
            FeedFilter.TRANSFERS -> diagnostic.kind == SmsDiagnosticKind.OUTGOING_TRANSFER ||
                diagnostic.kind == SmsDiagnosticKind.DEPOSIT_TOP_UP ||
                diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
                diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
            // An unrouted message is not a draft to confirm: it still needs an account first.
            FeedFilter.DRAFTS -> false
        }
        val haystack = listOfNotNull(
            diagnostic.counterparty,
            diagnostic.kind.name,
            diagnostic.currency,
            diagnostic.balanceCurrency,
            diagnostic.cardLast4,
            operation.day.toString(),
            operation.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            diagnostic.amountMinor?.let { (kotlin.math.abs(it) / 100.0).toString() },
        ).joinToString(" ")
        matchesType && categoryFilters.isEmpty() &&
            (search.isBlank() || haystack.contains(search.trim(), ignoreCase = true))
    }
    val timelineEntries = visibleItems.map(FeedTimelineEntry::Transaction) +
        visibleUnrouted.map(FeedTimelineEntry::Unrouted)
    val sortedEntries = when (sort) {
        FeedSort.NEWEST -> timelineEntries.sortedByDescending(FeedTimelineEntry::occurredAt)
        FeedSort.OLDEST -> timelineEntries.sortedBy(FeedTimelineEntry::occurredAt)
        FeedSort.AMOUNT -> timelineEntries.sortedWith(
            compareByDescending<FeedTimelineEntry> { it.day }
                .thenByDescending { kotlin.math.abs(it.amountMinor) },
        )
    }
    val grouped = sortedEntries.groupBy(FeedTimelineEntry::day)
    val selectedItems = items.filter { it.tx.id in selectedIds }
    val selectionMode = selectedIds.isNotEmpty()
    val allSelectedPending = selectedItems.isNotEmpty() && selectedItems.all { it.tx.status == TxStatus.PENDING }
    val headerScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(items) {
        val availableIds = items.mapTo(mutableSetOf()) { it.tx.id }
        selectedIds = selectedIds.intersect(availableIds)
    }

    AddRequestEffect(
        requestKey = addRequestKey,
        onConsumed = onAddRequestConsumed,
    ) {
            selectedIds = emptySet()
            addPrefill = null
            showAdd = true
    }

    fun toggleSelection(item: FeedItem) {
        selectedIds = if (item.tx.id in selectedIds) selectedIds - item.tx.id else selectedIds + item.tx.id
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(if (selectionMode) Modifier else Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection)),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                WhfinContextHeader(
                    label = stringResource(R.string.transactions_selected),
                    value = selectedIds.size.toString(),
                ) {
                    // Hundreds of drafts are cleared by narrowing the feed and taking the lot,
                    // not by tapping each row: whatever the filter shows, this selects.
                    if (selectedIds.size < visibleItems.size) WhfinIconButton(
                        icon = Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.transactions_select_all_action),
                        onClick = { selectedIds = visibleItems.map { it.tx.id }.toSet() },
                        outlined = false,
                    )
                    WhfinIconButton(
                        icon = if (allSelectedPending) Icons.Default.CheckCircle else Icons.Default.TaskAlt,
                        contentDescription = stringResource(
                            if (allSelectedPending) R.string.transactions_confirm_selected
                            else R.string.transactions_change_status,
                        ),
                        onClick = {
                            if (allSelectedPending) {
                                viewModel.updateStatuses(selectedItems, TxStatus.CONFIRMED)
                                selectedIds = emptySet()
                            } else {
                                showBatchStatus = true
                            }
                        },
                        outlined = false,
                    )
                    WhfinIconButton(
                        icon = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.transactions_delete_selected),
                        onClick = { showBatchDelete = true },
                        outlined = false,
                        style = WhfinActionStyle.Destructive,
                    )
                    WhfinIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.transactions_selection_close),
                        onClick = { selectedIds = emptySet() },
                        outlined = false,
                    )
                }
            } else {
                // Home leads with money that can be spent today; what is owned is the Accounts
                // page's headline. The pager's two peers answer two different questions, and one
                // number shown twice answered neither.
                val total = if (mode == FeedMode.HOME) spendable?.total else netWorth
                val totalAmount = total?.amount
                val headline = stringResource(
                    if (mode == FeedMode.HOME) R.string.home_spendable else R.string.balance_total,
                )
                WhfinContextHeader(
                    label = convertedTotalLabel(headline, total),
                    value = if (totalAmount == null) "—" else formatDecimal(totalAmount, total.currency),
                    valueSymbol = currencySymbol(total?.currency ?: displayCurrency),
                    scrollBehavior = headerScrollBehavior,
                    onValueClick = viewModel::rotateDisplayCurrency,
                    valueClickLabel = stringResource(R.string.net_worth_rotate),
                ) {
                    if (mode == FeedMode.HOME) {
                        WhfinIconButton(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = stringResource(R.string.analytics_open),
                            onClick = onOpenAnalytics,
                            outlined = false,
                        )
                        WhfinIconButton(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = stringResource(R.string.transactions_history_title),
                            onClick = onOpenHistory,
                            outlined = false,
                        )
                    } else {
                        WhfinIconButton(
                            icon = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(if (showSearch) R.string.feed_search_close else R.string.feed_search_open),
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) search = ""
                            },
                            outlined = false,
                            selected = showSearch,
                        )
                        WhfinIconButton(
                            icon = Icons.Default.FilterAlt,
                            contentDescription = stringResource(R.string.feed_filter_sort),
                            onClick = { showFilterSheet = true },
                            outlined = false,
                            selected = filter != FeedFilter.ALL || sort != FeedSort.NEWEST || categoryFilters.isNotEmpty(),
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 28.dp),
        ) {
        if (mode == FeedMode.HOME && !selectionMode) {
            item(key = "summary") {
                MonthlyFlowSummary(
                    income = income,
                    expenses = expenses,
                    onClick = onOpenAnalytics,
                    unconverted = homeAnalytics?.otherCurrencyExpenses.orEmpty(),
                )
            }
            runway?.let { reading ->
                item(key = "runway") { HomeRunwayRow(reading, onOpenAccounts) }
            }
            if (recurringDue.isNotEmpty()) item(key = "recurring") {
                HomeRecurringRow(recurringDue)
            }
            if (debtsOwed.isNotEmpty()) item(key = "debts-owed") {
                HomeDebtsOwedRow(debtsOwed, onOpenAccounts)
            }
            val lowCardBalances = physicalCardBalances.filter {
                physicalCardBalanceStatus(it.balanceMinor) != PhysicalCardBalanceStatus.Enough
            }
            // Every standing condition is a block competing for the same first screenful, so they
            // are ranked and capped rather than stacked in the order the code happens to know them.
            val presentNotices = buildSet {
                if (lowCardBalances.isNotEmpty()) add(HomeNotice.CARD_BALANCE)
                if (showSetupInvitation) add(HomeNotice.SETUP)
                if (integrityIssues > 0) add(HomeNotice.INTEGRITY)
                if (credoReminder != null && showCredoSyncReminder) add(HomeNotice.CREDO_SYNC)
                if (showSmsOnboarding) add(HomeNotice.SMS_ONBOARDING)
            }
            val triage = triageHomeNotices(presentNotices, expanded = noticesExpanded)
            items(triage.visible, key = { "notice-${it.name}" }) { notice ->
                when (notice) {
                    HomeNotice.CARD_BALANCE -> HomePhysicalCardBalance(
                        balances = lowCardBalances,
                        notificationsEnabled = hasLowBalanceNotificationPermission,
                        onOpenAccounts = onOpenAccounts,
                        onEnableNotifications = onRequestLowBalanceNotificationPermission,
                        onTopUp = { balance ->
                            accounts.firstOrNull { it.id == balance.accountId }?.let { target ->
                                addPrefill = ManualPrefill(
                                    fromAccountId = cardTopUpSource(accounts, accountBalances, target)?.id,
                                    toAccountId = target.id,
                                )
                                showAdd = true
                            }
                        },
                    )
                    HomeNotice.SETUP -> SetupInvitationCard(onResumeSetup, onDismissSetupInvitation)
                    // Technical state stays quiet unless the ledger contradicts itself; everything
                    // else about it lives in Data health.
                    HomeNotice.INTEGRITY -> WhfinNotice(
                        title = stringResource(R.string.home_integrity_title),
                        body = pluralStringResource(
                            R.plurals.home_integrity_body,
                            integrityIssues,
                            integrityIssues,
                        ),
                        icon = Icons.Default.ReportProblem,
                        kind = WhfinNoticeKind.Info,
                        actionLabel = stringResource(R.string.data_health_title),
                        onAction = onOpenDataHealth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HomeNotice.CREDO_SYNC -> credoReminder?.let { reminder ->
                        CredoSyncReminderCard(reminder, onOpenCredoSync)
                    }
                    HomeNotice.SMS_ONBOARDING -> SmsOnboardingCard(onEnableSms, onDismissSmsOnboarding)
                }
            }
            if (triage.foldable > 0) item(key = "notices-fold") {
                HomeNoticesFold(
                    count = triage.foldable,
                    expanded = noticesExpanded,
                    onToggle = { noticesExpanded = !noticesExpanded },
                )
            }

            if (attention.isNotEmpty()) {
                item(key = "attention-header") {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_needs_attention),
                        action = stringResource(R.string.home_review_all),
                        onAction = onOpenHistory,
                    )
                }
                items(attention.take(3), key = {
                    when (it) {
                        is FeedTimelineEntry.Transaction -> "home-pending-${it.item.tx.id}"
                        is FeedTimelineEntry.Unrouted -> "home-unrouted-${it.operation.diagnostic.id}"
                    }
                }) { entry ->
                    when (entry) {
                        is FeedTimelineEntry.Transaction -> FeedRow(
                            item = entry.item,
                            onClick = { details = entry.item },
                            onConfirmPending = { confirmPending(entry.item) },
                        )
                        is FeedTimelineEntry.Unrouted -> UnroutedOperationRow(
                            operation = entry.operation,
                            onClick = { routingFor = entry.operation },
                        )
                    }
                }
            }

            if (homeInsights.isNotEmpty()) {
                item(key = "insights") {
                    HomeInsightsSection(homeInsights, onOpenAnalytics)
                }
            }

            if (recent.items.isNotEmpty()) {
                item(key = "recent-header") {
                    HomeSectionHeader(
                        title = stringResource(
                            if (recent.isToday) R.string.home_today else R.string.home_recent_activity,
                        ),
                        action = stringResource(R.string.home_all_transactions),
                        onAction = onOpenHistory,
                        metricMinor = recent.expenseMinor,
                    )
                }
                items(recent.items, key = { "home-recent-${it.tx.id}" }) { item ->
                    FeedRow(item = item, onClick = { details = item })
                }
            }
            if (homeNothingRecorded(feedLoaded, items, unroutedOperations, recurringDue, debtsOwed)) {
                item(key = "empty") {
                    WhfinStatePane(
                        state = WhfinPaneState.Empty,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.feed_empty),
                    )
                }
            }
        } else if (mode == FeedMode.HISTORY) {
            if (!selectionMode) {
                item(key = "feed-tools") {
                    FeedSearch(
                        search = search,
                        onSearchChange = { search = it },
                        searchVisible = showSearch,
                    )
                }
            }
            if (feedLoaded && items.isEmpty() && unroutedOperations.isEmpty()) {
                item(key = "empty") {
                    WhfinStatePane(
                        state = WhfinPaneState.Empty,
                        title = stringResource(R.string.transactions_history_title),
                        body = stringResource(R.string.feed_empty),
                    )
                }
            }
        grouped.forEach { (day, dayEntries) ->
            val dayItems = dayEntries.mapNotNull { (it as? FeedTimelineEntry.Transaction)?.item }
            item(key = "header-$day") {
                // Расходы дня: GEL показываем сразу, остальные валюты раскрываются по тапу.
                val expensesByCurrency = dayItems
                    .filter { !it.tx.isTransfer && it.tx.transferGroupId == null && it.tx.amountMinor < 0 && !it.isDebt }
                    .groupBy { it.tx.currency }
                    .mapValues { (_, list) -> -list.sumOf { it.tx.amountMinor } }
                    .filterValues { it > 0L }
                // Для FX-покупки Transaction хранит цену покупки (например USD), а связанная
                // авто-конвертация — фактическую стоимость в GEL. В базовом итоге нужна именно она.
                val gelFromConversions = dayItems
                    .filter { !it.tx.isTransfer && it.tx.transferGroupId == null && it.tx.amountMinor < 0 &&
                        it.tx.currency != "GEL" && it.fundedByConversionCurrency == "GEL" }
                    .sumOf { it.fundedByConversionMinor ?: 0L }
                DayHeader(
                    day = day,
                    expensesByCurrency = expensesByCurrency,
                    gelFromConversions = gelFromConversions,
                    expanded = day in expandedExpenseDays,
                    onToggle = {
                        expandedExpenseDays = if (day in expandedExpenseDays) expandedExpenseDays - day
                            else expandedExpenseDays + day
                    },
                )
            }
            val transfers = dayItems.filter { it.tx.isTransfer }
            val regular = dayItems.filterNot { it.tx.isTransfer }
            if (transfers.size >= 3 && dayEntries.size == dayItems.size && day !in expandedTransferDays) {
                item(key = "transfer-bundle-$day") {
                    TransferBundleRow(transfers.size) {
                        expandedTransferDays = expandedTransferDays + day
                    }
                }
                items(regular, key = { it.tx.id }) { item ->
                    FeedRow(
                        item = item,
                        selected = item.tx.id in selectedIds,
                        onClick = { if (selectionMode) toggleSelection(item) else details = item },
                        onLongClick = { toggleSelection(item) },
                        onConfirmPending = { confirmPending(item) }.takeUnless { selectionMode },
                    )
                }
            } else {
                items(
                    dayEntries,
                    key = { entry ->
                        when (entry) {
                            is FeedTimelineEntry.Transaction -> "transaction-${entry.item.tx.id}"
                            is FeedTimelineEntry.Unrouted -> "unrouted-${entry.operation.diagnostic.id}"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is FeedTimelineEntry.Transaction -> FeedRow(
                            item = entry.item,
                            selected = entry.item.tx.id in selectedIds,
                            onClick = {
                                if (selectionMode) toggleSelection(entry.item) else details = entry.item
                            },
                            onLongClick = { toggleSelection(entry.item) },
                            onConfirmPending = { confirmPending(entry.item) }.takeUnless { selectionMode },
                        )
                        is FeedTimelineEntry.Unrouted -> UnroutedOperationRow(
                            operation = entry.operation,
                            onClick = { if (!selectionMode) routingFor = entry.operation },
                        )
                    }
                }
            }
        }
        }
        }
    }

    if (showFilterSheet) FeedFilterSheet(
        filter = filter,
        sort = sort,
        categories = categoriesByUsage.filterNot { it.isSystem },
        selectedCategoryIds = categoryFilters,
        onApply = { newFilter, newSort, newCategories ->
            filter = newFilter
            sort = newSort
            categoryFilters = newCategories
            showFilterSheet = false
        },
        onDismiss = { showFilterSheet = false },
    )

    routingFor?.let { operation ->
        SmsRoutingSheet(
            diagnostic = operation.diagnostic,
            accounts = smsRoutingAccounts,
            onDismiss = { routingFor = null },
            onResolve = { accountId, cardType ->
                viewModel.resolveUnrouted(operation.diagnostic.id, accountId, cardType)
                routingFor = null
            },
            onResolveGroup = { fromAccountId, toAccountId ->
                viewModel.resolveGroupedUnrouted(
                    operation.diagnostic.id,
                    fromAccountId,
                    toAccountId,
                )
                routingFor = null
            },
            onCreateAccount = { name, currency, cardType ->
                viewModel.createCredoAccountAndResolve(
                    operation.diagnostic.id,
                    name,
                    currency,
                    cardType,
                )
                routingFor = null
            },
            onAddGroupedAccount = { name, currency ->
                viewModel.addCredoAccount(name, currency)
            },
        )
    }

    if (showBatchStatus) TransactionStatusSheet(
        current = selectedItems.map { it.tx.status }.distinct().singleOrNull(),
        onDismiss = { showBatchStatus = false },
        onSelect = { status ->
            viewModel.updateStatuses(selectedItems, status)
            showBatchStatus = false
            selectedIds = emptySet()
        },
    )

    if (showBatchDelete) {
        // Bank truth survives a bulk delete, so the count that matters is the one that will actually
        // go. Saying it before the tap beats a dialog that promises more than it can do.
        val deletable = selectedItems.count { it.tx.source == TxSource.MANUAL }
        WhfinConfirmDialog(
        title = stringResource(R.string.transactions_delete_selected),
        body = if (deletable == selectedItems.size) {
            stringResource(R.string.transactions_delete_selected_body, selectedItems.size)
        } else {
            stringResource(R.string.transactions_delete_selected_partial_body, deletable, selectedItems.size)
        },
        confirmLabel = stringResource(R.string.action_delete),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = {
                viewModel.deleteItems(selectedItems)
                showBatchDelete = false
                selectedIds = emptySet()
        },
        onDismiss = { showBatchDelete = false },
        )
    }

    // Форма получает то же умное ранжирование, что quick-entry: подсказки пере-считываются
    // по введённой сумме и валюте выбранного ledger'а.
    val suggester by viewModel.categorySuggester.collectAsState()
    val rankCategories: CategoryRanker = remember(suggester) {
        { list, amountMinor, currency ->
            suggester?.rankCategories(list, amountMinor?.let { -kotlin.math.abs(it) }, currency) ?: list
        }
    }

    if (showAdd) {
        AddTransactionSheet(
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            prefill = addPrefill,
            onDismiss = { showAdd = false; addPrefill = null },
            onSave = { manual ->
                viewModel.addManual(manual)
                showAdd = false
                addPrefill = null
            },
            onSaveDebt = { debt -> viewModel.addDebt(debt); showAdd = false; addPrefill = null },
            onCreateCategory = viewModel::createCategory,
            onCreateCashCurrency = viewModel::createCashCurrency,
            rankCategories = rankCategories,
        )
    }

    editFor?.let { item ->
        AddTransactionSheet(
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            editing = item,
            onDismiss = { editFor = null },
            onSave = {},
            onSaveDebt = {},
            onUpdate = { original, value -> viewModel.updateManual(original, value); editFor = null },
            onCreateCategory = viewModel::createCategory,
            onCreateCashCurrency = viewModel::createCashCurrency,
            rankCategories = rankCategories,
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
            onDelete = if (item.tx.source == dev.whekin.whfin.data.db.TxSource.MANUAL) {{
                details = null
                deleteFor = item
            }} else null,
            onCorrect = if (item.tx.source in setOf(dev.whekin.whfin.data.db.TxSource.STATEMENT, dev.whekin.whfin.data.db.TxSource.SMS)) {{
                details = null
                correctFor = item
            }} else null,
            onEdit = if (item.tx.source == dev.whekin.whfin.data.db.TxSource.MANUAL) {{
                details = null
                editFor = item
            }} else null,
            onDebt = if (item.tx.amountMinor < 0 && item.splitOnPeople.isEmpty()) {{ details = null; debtFor = item }} else null,
            onClearDebt = if (item.isDebt) {{ viewModel.clearAllocations(item); details = null }} else null,
            onSplit = if (item.tx.amountMinor < 0 && !item.isDebt) {{ details = null; splitFor = item }} else null,
            onClearSplit = if (item.splitOnPeople.isNotEmpty()) {{ viewModel.clearAllocations(item); details = null }} else null,
            onChangeStatus = {
                details = null
                statusFor = item
            },
            onConfirm = {
                viewModel.updateStatus(item, TxStatus.CONFIRMED)
                details = null
            },
        )
    }

    statusFor?.let { item ->
        TransactionStatusSheet(
            current = item.tx.status,
            onDismiss = { statusFor = null },
            onSelect = { status ->
                viewModel.updateStatus(item, status)
                statusFor = null
            },
        )
    }

    categoryFor?.let { item ->
        CategoryPickerSheet(
            item = item,
            // Сумма и валюта операции известны — пикер ранжируется умными подсказками.
            categories = remember(categories, suggester, item.tx.id) {
                suggester?.rankCategories(categories, item.tx.amountMinor, item.tx.currency) ?: categories
            },
            onDismiss = { categoryFor = null },
            onSelect = { category ->
                viewModel.assignCategory(item, category.id)
                categoryFor = null
            },
            onCreateCategory = viewModel::createCategory,
        )
    }

    deleteFor?.let { item ->
        WhfinConfirmDialog(
            title = stringResource(R.string.transaction_delete),
            body = stringResource(
                if (item.tx.transferGroupId != null) R.string.transaction_delete_transfer_body
                else R.string.transaction_delete_body,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.deleteManual(item); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
    correctFor?.let { item ->
        WhfinConfirmDialog(
            title = stringResource(R.string.transaction_correct),
            body = stringResource(R.string.transaction_correct_body),
            confirmLabel = stringResource(R.string.transaction_correct),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                viewModel.correctImported(item)
                correctFor = null
            },
            onDismiss = { correctFor = null },
        )
    }
    debtFor?.let { item ->
        DebtPersonSheet(
            item = item,
            people = people,
            onDismiss = { debtFor = null },
            onSelect = { viewModel.assignDebt(item, it.id); debtFor = null },
            onAdd = { viewModel.addPersonAndAssignDebt(item, it); debtFor = null },
        )
    }
    splitFor?.let { item ->
        SplitSheet(
            item = item,
            people = people,
            onDismiss = { splitFor = null },
            onAddPerson = { name, then -> viewModel.addPerson(name, then) },
            onSave = { shares -> viewModel.saveSplit(item, shares); splitFor = null },
        )
    }
}

@Composable
internal fun HomePhysicalCardBalance(
    balances: List<PhysicalCardHomeBalance>,
    notificationsEnabled: Boolean,
    onOpenAccounts: () -> Unit,
    onEnableNotifications: () -> Unit,
    onTopUp: ((PhysicalCardHomeBalance) -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            balances.take(2).forEachIndexed { index, balance ->
                val status = physicalCardBalanceStatus(balance.balanceMinor)
                val accent = when (status) {
                    PhysicalCardBalanceStatus.Critical -> MaterialTheme.colorScheme.error
                    PhysicalCardBalanceStatus.Low -> WhfinThemeTokens.colors.warning
                    PhysicalCardBalanceStatus.Enough -> MaterialTheme.colorScheme.onSurface
                }
                val title = stringResource(
                    if (status == PhysicalCardBalanceStatus.Critical) {
                        R.string.home_card_balance_critical
                    } else {
                        R.string.home_card_balance_low
                    },
                )
                val masks = balance.cardLast4s.joinToString(" · ") { "••$it" }
                val identity = stringResource(
                    R.string.home_card_balance_identity,
                    balance.accountName,
                    masks,
                )
                val supporting = if (notificationsEnabled) identity else {
                    "$identity · ${stringResource(R.string.home_card_balance_enable_alerts)}"
                }
                Column(
                    Modifier.fillMaxWidth().clickable(
                        onClick = if (notificationsEnabled) onOpenAccounts else onEnableNotifications,
                    ),
                ) {
                    WhfinLedgerRow(
                        title = title,
                        titleMaxLines = 3,
                        supportingText = supporting,
                        supportingMaxLines = 4,
                        icon = Icons.Outlined.CreditCard,
                        iconTint = accent,
                        markerColor = accent,
                    )
                    // The answer to an empty card is always to move money onto it, so the row
                    // carries that action rather than sending the reader to Accounts to find it.
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onTopUp != null) {
                            WhfinButton(
                                label = stringResource(R.string.home_card_top_up),
                                onClick = { onTopUp(balance) },
                                style = WhfinActionStyle.Secondary,
                                leadingIcon = Icons.AutoMirrored.Filled.CallMade,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        WhfinAmount(
                            text = formatMinor(balance.balanceMinor, "GEL"),
                            symbol = currencySymbol("GEL"),
                            color = accent,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (index != balances.take(2).lastIndex) HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun collectHomeAnalyticsState(enabled: Boolean): AnalyticsUiModel? {
    if (!enabled) return null
    val analyticsViewModel: AnalyticsViewModel = viewModel(key = "home-analytics")
    val state by analyticsViewModel.uiState.collectAsState()
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsSheet(
    item: FeedItem,
    onDismiss: () -> Unit,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCorrect: (() -> Unit)? = null,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        TransactionDetailsContent(
            item = item,
            modifier = Modifier.navigationBarsPadding(),
            onChangeCategory = onChangeCategory,
            onDelete = onDelete,
            onCorrect = onCorrect,
            onEdit = onEdit,
            onDebt = onDebt,
            onClearDebt = onClearDebt,
            onSplit = onSplit,
            onClearSplit = onClearSplit,
            onChangeStatus = onChangeStatus,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun TransactionDetailsContent(
    item: FeedItem,
    modifier: Modifier = Modifier,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCorrect: (() -> Unit)? = null,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
) {
    val tx = item.tx
    val presentationAmount = transactionPresentationAmount(tx)
    val isTransfer = tx.isTransfer || tx.transferGroupId != null
    var showBankDetails by remember(tx.id) { mutableStateOf(false) }
    var actionMenuExpanded by remember(tx.id) { mutableStateOf(false) }
    val genericTitle = stringResource(
        when {
            isTransfer -> R.string.tx_transfer
            tx.amountMinor >= 0 -> R.string.tx_income
            else -> R.string.tx_expense
        },
    )
    val title = item.transferSummary
        ?: item.merchant?.displayName
        ?: tx.rawCounterparty
        ?: tx.note?.takeIf { it.isNotBlank() }
        ?: item.category?.name
        ?: genericTitle
    val dateAndAccount = listOfNotNull(
        item.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
        item.account?.name,
    ).joinToString(" · ")
    val accent = item.category?.let { Color(it.color) } ?: when {
        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        tx.amountMinor >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val hasBankDetails = item.account?.iban != null || tx.source != dev.whekin.whfin.data.db.TxSource.MANUAL ||
        tx.rawCounterparty != null || tx.counterpartyIban != null || tx.note != null ||
        tx.origAmountMinor != null || item.fundedByConversionMinor != null
    // Подтверждение pending-черновика — самое частое решение в этой раскладке, поэтому он
    // стоит первым и единственным залитым действием, а не спрятан за отдельным status-листом.
    val confirmPending = onConfirm?.takeIf { tx.status == TxStatus.PENDING }
    val hasQuickActions = confirmPending != null || onEdit != null || onCorrect != null || onDebt != null ||
        onClearDebt != null || onSplit != null || onClearSplit != null

    LazyColumn(
        modifier.fillMaxWidth().heightIn(max = 680.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "transaction-heading") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = .14f),
                    contentColor = accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            CategoryIcons.resolve(item.category?.icon, isTransfer = isTransfer),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                    WhfinAmount(
                        formatMinor(presentationAmount.minor, presentationAmount.currency),
                        symbol = currencySymbol(presentationAmount.currency),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (item.destinationAmountMinor != null && item.destinationCurrency != null) {
                        WhfinAmount(
                            "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                            symbol = currencySymbol(item.destinationCurrency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        dateAndAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null || onCorrect != null) {
                    Box {
                        WhfinIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.transaction_actions),
                            onClick = { actionMenuExpanded = true },
                            outlined = false,
                        )
                        WhfinActionMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            onDelete?.let { delete -> DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.transaction_delete),
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
                                    actionMenuExpanded = false
                                    delete()
                                },
                            ) }
                            onCorrect?.let { correct -> DropdownMenuItem(
                                text = { Text(stringResource(R.string.transaction_correct)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    actionMenuExpanded = false
                                    correct()
                                },
                            ) }
                        }
                    }
                }
            }
        }
        item(key = "transaction-summary") {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailEditableRow(
                    label = stringResource(R.string.tx_detail_status),
                    value = if (tx.source == TxSource.SMS) {
                        stringResource(R.string.status_sms)
                    } else {
                        tx.status.label()
                    },
                    onClick = onChangeStatus.takeUnless { tx.source == TxSource.SMS },
                )
                if (!isTransfer) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailEditableRow(
                        label = stringResource(R.string.tx_detail_category),
                        value = item.category?.name ?: stringResource(R.string.feed_uncategorized),
                        onClick = onChangeCategory,
                    )
                }
                if (item.isDebt) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        stringResource(R.string.debt_label),
                        stringResource(
                            R.string.debt_person_owes,
                            item.debtPersonName ?: "—",
                            formatMinor(item.debtMinor ?: 0L, tx.currency),
                        ),
                    )
                }
                item.splitOnPeople.forEach { (name, amount) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        stringResource(R.string.split_on_person, name),
                        formatMinor(amount, tx.currency),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (tx.note?.isNotBlank() == true && tx.note != title) {
            item(key = "transaction-note") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WhfinSectionLabel(stringResource(R.string.tx_note))
                    Text(tx.note, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (hasBankDetails) item(key = "transaction-bank-details-toggle") {
            TextButton(onClick = { showBankDetails = !showBankDetails }) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = if (showBankDetails) 180f else 0f),
                )
                Text(stringResource(R.string.tx_detail_more))
            }
        }
        if (hasBankDetails && showBankDetails) item(key = "transaction-bank-details") {
            WhfinLedgerGroup {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    item.account?.iban?.let { DetailRow("IBAN", it) }
                    DetailRow(stringResource(R.string.tx_detail_source), tx.source.name.lowercase().replaceFirstChar(Char::titlecase))
                    tx.rawCounterparty?.let { DetailRow(stringResource(R.string.tx_detail_counterparty), it) }
                    tx.counterpartyIban?.let { DetailRow(stringResource(R.string.tx_detail_counterparty_iban), it) }
                    tx.note?.let { DetailRow(stringResource(R.string.tx_detail_bank_description), it) }
                    if (tx.origAmountMinor != null && tx.origCurrency != null) DetailRow(
                        stringResource(R.string.tx_detail_original_amount),
                        formatMinor(kotlin.math.abs(tx.origAmountMinor), tx.origCurrency),
                    )
                    if (item.fundedByConversionMinor != null && item.fundedByConversionCurrency != null) DetailRow(
                        stringResource(R.string.tx_detail_converted_from),
                        formatMinor(item.fundedByConversionMinor, item.fundedByConversionCurrency),
                    )
                }
            }
        }
        if (hasQuickActions) item(key = "transaction-actions") {
            // Капс-подпись «ДЕЙСТВИЯ» над рядом кнопок ничего не добавляла: рельс из подписанных
            // действий уже очевиден, а капс тратил строку и телеграфировал.
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (confirmPending != null) item {
                        DetailQuickAction(
                            Icons.Default.CheckCircle,
                            stringResource(R.string.transaction_confirm),
                            confirmPending,
                            filled = true,
                        )
                    }
                    if (onEdit != null) item {
                        DetailQuickAction(Icons.Default.Edit, stringResource(R.string.action_edit), onEdit)
                    }
                    if (onCorrect != null) item {
                        DetailQuickAction(Icons.Default.Edit, stringResource(R.string.transaction_correct), onCorrect)
                    }
                    if (onClearDebt != null) item {
                        DetailQuickAction(Icons.Default.PersonAdd, stringResource(R.string.debt_clear), onClearDebt)
                    } else if (onDebt != null) item {
                        DetailQuickAction(Icons.Default.PersonAdd, stringResource(R.string.debt_action_short), onDebt)
                    }
                    if (onClearSplit != null) item {
                        DetailQuickAction(Icons.AutoMirrored.Filled.CallSplit, stringResource(R.string.split_clear), onClearSplit)
                    } else if (onSplit != null) item {
                        DetailQuickAction(Icons.AutoMirrored.Filled.CallSplit, stringResource(R.string.split_action_short), onSplit)
                    }
                }
            }
        }
    }
}

@Preview(name = "Transaction details", widthDp = 400, heightDp = 620, showBackground = true)
@Preview(
    name = "Transaction details dark",
    widthDp = 400,
    heightDp = 620,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Transaction details font 1.5",
    widthDp = 400,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(name = "Transaction details compact", widthDp = 400, heightDp = 480, showBackground = true)
@Composable
private fun TransactionDetailsPreview() {
    val account = AccountEntity(
        id = 1,
        name = "Cash",
        type = AccountType.CASH,
        currency = "GEL",
    )
    val category = CategoryEntity(
        id = 1,
        name = "Eating out",
        kind = CategoryKind.EXPENSE,
        icon = "Restaurant",
        color = 0xFFC45D3A.toInt(),
    )
    val item = FeedItem(
        tx = TransactionEntity(
            id = 1,
            accountId = account.id,
            amountMinor = -2_000,
            currency = "GEL",
            occurredAt = System.currentTimeMillis(),
            categoryId = category.id,
            status = TxStatus.MANUAL,
            source = TxSource.MANUAL,
        ),
        merchant = null,
        category = category,
        account = account,
        cardHint = null,
        day = LocalDate.of(2026, 7, 19),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionDetailsContent(
                item = item,
                modifier = Modifier.fillMaxSize(),
                onChangeCategory = {},
                onDelete = {},
                onEdit = {},
                onDebt = {},
                onClearDebt = null,
                onSplit = {},
                onChangeStatus = {},
            )
        }
    }
}

@Preview(name = "Transaction details pending", widthDp = 400, heightDp = 620, showBackground = true)
@Preview(
    name = "Transaction details pending dark",
    widthDp = 400,
    heightDp = 620,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Transaction details pending font 1.5",
    widthDp = 400,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun TransactionDetailsPendingPreview() {
    val account = AccountEntity(
        id = 1,
        name = "Everyday",
        type = AccountType.BANK,
        currency = "GEL",
        iban = "GE00CD0000000000000001",
    )
    val category = CategoryEntity(
        id = 1,
        name = "Eating out",
        kind = CategoryKind.EXPENSE,
        icon = "Restaurant",
        color = 0xFFC45D3A.toInt(),
    )
    val item = FeedItem(
        tx = TransactionEntity(
            id = 2,
            accountId = account.id,
            amountMinor = -1_270,
            currency = "GEL",
            occurredAt = System.currentTimeMillis(),
            rawCounterparty = "COURTYARD COFFEE",
            categoryId = category.id,
            status = TxStatus.PENDING,
            source = TxSource.SMS,
        ),
        merchant = null,
        category = category,
        account = account,
        cardHint = "••0000",
        day = LocalDate.of(2026, 7, 19),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionDetailsContent(
                item = item,
                modifier = Modifier.fillMaxSize(),
                onChangeCategory = {},
                onDelete = null,
                onEdit = null,
                onDebt = {},
                onClearDebt = null,
                onSplit = {},
                onChangeStatus = {},
                onConfirm = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionStatusSheet(
    current: TxStatus?,
    onDismiss: () -> Unit,
    onSelect: (TxStatus) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.transaction_status_title), style = MaterialTheme.typography.headlineSmall)
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                TxStatus.entries.forEachIndexed { index, status ->
                    WhfinLedgerRow(
                        title = status.label(),
                        supportingText = stringResource(status.descriptionResource()),
                        icon = Icons.Default.TaskAlt,
                        trailing = if (status == current) {{ Icon(Icons.Default.Check, null) }} else null,
                        onClick = { onSelect(status) },
                        divider = index != TxStatus.entries.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TxStatus.label(): String = stringResource(
    when (this) {
        TxStatus.PENDING -> R.string.status_pending
        TxStatus.CONFIRMED -> R.string.status_confirmed
        TxStatus.MANUAL -> R.string.status_manual
    },
)

private fun TxStatus.descriptionResource(): Int = when (this) {
    TxStatus.PENDING -> R.string.status_pending_description
    TxStatus.CONFIRMED -> R.string.status_confirmed_description
    TxStatus.MANUAL -> R.string.status_manual_description
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.58f))
    }
}

/**
 * Редактируемая строка сводки без chevron читалась как статичная запись базы, поэтому статус
 * и категорию не находили. Тихий chevron возвращает affordance, не превращая строку в кнопку.
 */
@Composable
private fun DetailEditableRow(label: String, value: String, onClick: (() -> Unit)?) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.58f))
        if (onClick != null) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = contentColor)
            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtPersonSheet(
    item: FeedItem,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSelect: (PersonEntity) -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.debt_who_owes), style = MaterialTheme.typography.headlineSmall)
            Text(
                formatMinor(kotlin.math.abs(item.tx.amountMinor), item.tx.currency),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            people.forEach { person ->
                Surface(
                    onClick = { onSelect(person) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (person.name == item.debtPersonName) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(person.color).copy(alpha = .22f), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase()) }
                        }
                        Text(person.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            WhfinField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.debt_new_person),
                modifier = Modifier.fillMaxWidth(),
            )
            if (name.isNotBlank()) WhfinButton(
                stringResource(R.string.debt_add_and_select), { onAdd(name) }, Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class SplitMode { HALF, FULL, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitSheet(
    item: FeedItem,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onAddPerson: (String, (Long) -> Unit) -> Unit,
    onSave: (List<SplitShare>) -> Unit,
) {
    val total = kotlin.math.abs(item.tx.amountMinor)
    // Предзаполнение из существующей разбивки (одна персона — быстрый путь)
    val existing = item.splitOnPeople.firstOrNull()
    var selectedPersonId by remember {
        mutableStateOf(people.firstOrNull { it.name == existing?.first }?.id ?: people.firstOrNull()?.id)
    }
    var mode by remember {
        mutableStateOf(
            when (existing?.second) {
                null -> SplitMode.HALF
                total -> SplitMode.FULL
                total / 2 -> SplitMode.HALF
                else -> SplitMode.CUSTOM
            },
        )
    }
    var customText by remember {
        mutableStateOf(existing?.second?.let { (it / 100.0).toString() } ?: "")
    }
    var newName by remember { mutableStateOf("") }

    val onThemMinor = when (mode) {
        SplitMode.HALF -> total / 2
        SplitMode.FULL -> total
        SplitMode.CUSTOM -> parseToMinor(customText)?.coerceIn(0, total) ?: 0L
    }
    val purpose = if (mode == SplitMode.FULL) AllocationPurpose.GIFT else AllocationPurpose.SHARED
    val canSave = selectedPersonId != null && onThemMinor > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.split_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                formatMinor(total, item.tx.currency),
                style = MaterialTheme.typography.displaySmall,
            )

            Text(stringResource(R.string.split_with_whom), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinChoiceRail {
                items(people, key = { it.id }) { person ->
                    WhfinFilterPill(
                        label = person.name,
                        selected = selectedPersonId == person.id,
                        onClick = { selectedPersonId = person.id },
                    )
                }
            }
            WhfinField(
                value = newName,
                onValueChange = { newName = it },
                label = stringResource(R.string.debt_new_person),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (newName.isNotBlank()) TextButton(onClick = {
                        onAddPerson(newName.trim()) { id -> selectedPersonId = id; newName = "" }
                    }) { Text(stringResource(R.string.action_add)) }
                },
            )

            Text(stringResource(R.string.split_how_much), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_half),
                        selected = mode == SplitMode.HALF,
                        onClick = { mode = SplitMode.HALF },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_full),
                        selected = mode == SplitMode.FULL,
                        onClick = { mode = SplitMode.FULL },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_custom),
                        selected = mode == SplitMode.CUSTOM,
                        onClick = { mode = SplitMode.CUSTOM },
                    )
                }
            }
            if (mode == SplitMode.CUSTOM) {
                WhfinField(
                    value = customText,
                    onValueChange = { customText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                    label = stringResource(R.string.split_amount_on_them),
                    suffix = item.tx.currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Подсказка по итогу: на человека / на себя
            if (canSave) {
                val onMe = total - onThemMinor
                Text(
                    stringResource(
                        R.string.split_preview,
                        formatMinor(onThemMinor, item.tx.currency),
                        formatMinor(onMe, item.tx.currency),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            WhfinButton(
                stringResource(R.string.action_save),
                onClick = {
                    val id = selectedPersonId ?: return@WhfinButton
                    onSave(listOf(SplitShare(personId = id, amountMinor = onThemMinor, purpose = purpose)))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
        }
    }
}

private enum class FeedFilter { ALL, EXPENSES, INCOME, TRANSFERS, DRAFTS }
private enum class FeedSort { NEWEST, OLDEST, AMOUNT }

@Composable
private fun FeedSearch(
    search: String,
    onSearchChange: (String) -> Unit,
    searchVisible: Boolean,
) {
    if (!searchVisible) return
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    WhfinField(
        value = search,
        onValueChange = onSearchChange,
        label = null,
        leadingIcon = Icons.Default.Search,
        placeholder = stringResource(R.string.feed_search_hint),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(focusRequester),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedFilterSheet(
    filter: FeedFilter,
    sort: FeedSort,
    categories: List<CategoryEntity>,
    selectedCategoryIds: Set<Long>,
    onApply: (FeedFilter, FeedSort, Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftFilter by remember(filter) { mutableStateOf(filter) }
    var draftSort by remember(sort) { mutableStateOf(sort) }
    var draftCategories by remember(selectedCategoryIds) { mutableStateOf(selectedCategoryIds) }
    var showAllCategories by remember { mutableStateOf(false) }

    val eligibleCategories = remember(categories, draftFilter) {
        when (draftFilter) {
            FeedFilter.EXPENSES -> categories.filter { it.kind == CategoryKind.EXPENSE }
            FeedFilter.INCOME -> categories.filter { it.kind == CategoryKind.INCOME }
            FeedFilter.TRANSFERS -> emptyList()
            FeedFilter.DRAFTS, FeedFilter.ALL -> categories
        }
    }
    val quickCategories = remember(eligibleCategories, draftCategories) {
        (eligibleCategories.filter { it.id in draftCategories } + eligibleCategories)
            .distinctBy { it.id }
            .take(4)
    }

    if (showAllCategories) {
        FilterCategorySelector(
            categories = eligibleCategories,
            selectedIds = draftCategories,
            onToggle = { category ->
                draftCategories = if (category.id in draftCategories) {
                    draftCategories - category.id
                } else {
                    draftCategories + category.id
                }
            },
            onBack = { showAllCategories = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.66f)
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feed_filter_sort),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.feed_filters_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val activeCount = (if (draftFilter != FeedFilter.ALL) 1 else 0) +
                    (if (draftSort != FeedSort.NEWEST) 1 else 0) + draftCategories.size
                if (activeCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            activeCount.toString(),
                            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WhfinSectionLabel(stringResource(R.string.feed_transaction_type))
                val filterOptions = listOf(
                    FeedFilter.ALL to R.string.feed_filter_all,
                    FeedFilter.EXPENSES to R.string.feed_filter_expenses,
                    FeedFilter.INCOME to R.string.feed_filter_income,
                    FeedFilter.TRANSFERS to R.string.feed_filter_transfers,
                    FeedFilter.DRAFTS to R.string.feed_filter_drafts,
                )
                WhfinChoiceRail {
                    items(filterOptions, key = { it.first.name }) { (value, label) ->
                    WhfinFilterPill(
                        label = stringResource(label),
                        selected = draftFilter == value,
                        leadingIcon = when (value) {
                            FeedFilter.ALL -> Icons.Default.SelectAll
                            FeedFilter.EXPENSES -> Icons.Default.ArrowUpward
                            FeedFilter.INCOME -> Icons.Default.ArrowDownward
                            FeedFilter.TRANSFERS -> Icons.Default.SwapHoriz
                            FeedFilter.DRAFTS -> Icons.Default.Schedule
                        },
                        onClick = {
                            draftFilter = value
                            draftCategories = when (value) {
                                FeedFilter.EXPENSES -> draftCategories.filterTo(mutableSetOf()) { id ->
                                    categories.any { it.id == id && it.kind == CategoryKind.EXPENSE }
                                }
                                FeedFilter.INCOME -> draftCategories.filterTo(mutableSetOf()) { id ->
                                    categories.any { it.id == id && it.kind == CategoryKind.INCOME }
                                }
                                FeedFilter.TRANSFERS -> emptySet()
                                FeedFilter.DRAFTS, FeedFilter.ALL -> draftCategories
                            }
                        },
                    )
                    }
                }

                if (draftFilter != FeedFilter.TRANSFERS && quickCategories.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WhfinSectionLabel(
                            stringResource(R.string.tx_detail_category),
                            Modifier.weight(1f),
                        )
                        if (draftCategories.isNotEmpty()) {
                            Text(
                                stringResource(R.string.feed_categories_selected, draftCategories.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 20.dp)) {
                        val minSlotWidth = if (LocalDensity.current.fontScale >= 1.3f) 96.dp else 64.dp
                        val slotCount = (maxWidth.value / minSlotWidth.value).toInt().coerceIn(2, 5)
                        val visibleQuickCategories = quickCategories.take(slotCount - 1)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            visibleQuickCategories.forEach { category ->
                            FilterCategoryTile(
                                category = category,
                                selected = category.id in draftCategories,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    draftCategories = if (category.id in draftCategories) {
                                        draftCategories - category.id
                                    } else {
                                        draftCategories + category.id
                                    }
                                },
                            )
                            }
                            FilterCategoryTile(
                                category = null,
                                selected = draftCategories.any { id ->
                                    visibleQuickCategories.none { it.id == id }
                                },
                                modifier = Modifier.weight(1f),
                                onClick = { showAllCategories = true },
                            )
                        }
                    }
                }

                WhfinSectionLabel(stringResource(R.string.feed_sort_by))
                val sortOptions = listOf(
                    FeedSort.NEWEST to R.string.feed_sort_newest,
                    FeedSort.OLDEST to R.string.feed_sort_oldest,
                    FeedSort.AMOUNT to R.string.feed_sort_amount,
                )
                WhfinChoiceRail {
                    items(sortOptions, key = { it.first.name }) { (value, label) ->
                        WhfinFilterPill(
                            label = stringResource(label),
                            selected = draftSort == value,
                            leadingIcon = when (value) {
                                FeedSort.NEWEST -> Icons.Default.ArrowDownward
                                FeedSort.OLDEST -> Icons.Default.ArrowUpward
                                FeedSort.AMOUNT -> Icons.AutoMirrored.Filled.TrendingUp
                            },
                            onClick = { draftSort = value },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WhfinButton(
                    label = stringResource(R.string.feed_filters_reset),
                    onClick = {
                        draftFilter = FeedFilter.ALL
                        draftSort = FeedSort.NEWEST
                        draftCategories = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    style = WhfinActionStyle.Secondary,
                )
                WhfinButton(
                    label = stringResource(R.string.feed_filters_apply),
                    onClick = { onApply(draftFilter, draftSort, draftCategories) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterCategoryTile(
    category: CategoryEntity?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) tint.copy(alpha = .12f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, tint.copy(alpha = .7f)) else null,
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = .14f)) {
                Icon(
                    imageVector = category?.let { CategoryIcons.resolve(it.icon) } ?: Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Text(
                category?.name ?: stringResource(R.string.categories_more),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterCategorySelector(
    categories: List<CategoryEntity>,
    selectedIds: Set<Long>,
    onToggle: (CategoryEntity) -> Unit,
    onBack: () -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        WhfinDialogSystemBars()
        DemoWorkspaceFrame {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WhfinBackButton(stringResource(R.string.action_back), onBack)
                    Text(
                        stringResource(R.string.categories_show_all),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CategoryKind.entries.forEach { kind ->
                        val kindCategories = categories.filter { it.kind == kind }
                        if (kindCategories.isNotEmpty()) {
                            item(key = "all-filter-category-label-$kind") {
                                WhfinSectionLabel(stringResource(
                                    if (kind == CategoryKind.EXPENSE) R.string.categories_expense else R.string.categories_income,
                                ))
                            }
                            items(kindCategories, key = { "all-filter-category-${it.id}" }) { category ->
                                Column(Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = { onToggle(category) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.small,
                                        color = Color.Transparent,
                                    ) {
                                        WhfinLedgerRow(
                                            title = category.name,
                                            icon = CategoryIcons.resolve(category.icon),
                                            iconTint = Color(category.color),
                                            trailing = if (category.id in selectedIds) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                        )
                                    }
                                    HorizontalDivider(
                                        Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
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
}

@Composable
internal fun AddRequestEffect(
    requestKey: Int,
    onConsumed: () -> Unit,
    onAdd: () -> Unit,
) {
    LaunchedEffect(requestKey) {
        if (requestKey > 0) {
            onAdd()
            onConsumed()
        }
    }
}

@Composable
private fun TransferBundleRow(count: Int, onExpand: () -> Unit) {
    WhfinLedgerGroup(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = stringResource(R.string.transfer_bundle_title),
            supportingText = stringResource(R.string.transfer_bundle_count, count),
            icon = Icons.Default.SwapHoriz,
            trailing = { Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.categories_show_all)) },
            onClick = onExpand,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    item: FeedItem,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSelect: (CategoryEntity) -> Unit,
    onCreateCategory: (String, CategoryKind, String, Int) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val kind = if (item.tx.amountMinor >= 0) CategoryKind.INCOME else CategoryKind.EXPENSE
    var customIcon by remember { mutableStateOf(if (kind == CategoryKind.EXPENSE) "VolunteerActivism" else "Work") }
    var customColor by remember { mutableIntStateOf(if (kind == CategoryKind.EXPENSE) 0xFFD16D5A.toInt() else 0xFF78906F.toInt()) }
    val visible = categories.filter { !it.isSystem && it.kind == kind }
    val title = item.transferSummary ?: item.merchant?.displayName ?: item.tx.rawCounterparty
        ?: stringResource(R.string.feed_no_description)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                formatMinor(item.tx.amountMinor, item.tx.currency, withSign = true),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
            Text(stringResource(if (kind == CategoryKind.EXPENSE) R.string.categories_expense else R.string.categories_income),
                style = MaterialTheme.typography.titleMedium)
            if (creating) {
                WhfinField(
                    name,
                    { name = it.take(32) },
                    stringResource(R.string.category_name),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                CategoryAppearancePicker(customIcon, customColor, { customIcon = it }, { customColor = it })
                WhfinButton(label = stringResource(R.string.category_create), onClick = {
                    onCreateCategory(name.trim(), kind, customIcon, customColor)
                    creating = false
                }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            } else {
                CategoryGrid(visible, item.tx.categoryId, onSelect, maxHeight = 350.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                WhfinButton(
                    stringResource(R.string.category_new), { creating = true },
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
                    style = WhfinActionStyle.Secondary, leadingIcon = Icons.Default.Add,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit,
    metricMinor: Long? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhfinSectionLabel(title, Modifier.weight(1f))
        // The day's own total belongs to the day's label: it is the same fact, said once.
        if (metricMinor != null) WhfinAmount(
            text = formatMinor(-metricMinor, "GEL", withSign = true),
            symbol = currencySymbol("GEL"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onAction) { Text(action, maxLines = 1) }
    }
}

/**
 * How long today's money lasts, said once and only while it is news.
 *
 * The row carries the consequence in words as well as colour: a runway that does not reach the
 * declared payday must still read as short to someone who cannot see the accent.
 */
@Composable
internal fun HomeRunwayRow(
    runway: HomeRunway,
    onOpenAccounts: () -> Unit,
) {
    val accent = if (runway.shortOfIncome) {
        WhfinThemeTokens.colors.warning
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val burn = stringResource(
        R.string.home_runway_burn,
        formatMinor(runway.dailyBurnMinor, "GEL"),
    )
    val payday = runway.nextIncome?.let { window ->
        stringResource(R.string.home_runway_income, incomeWindowLabel(window))
    }
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-runway"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = pluralStringResource(
                if (runway.shortOfIncome) R.plurals.home_runway_days_short else R.plurals.home_runway_days,
                runway.daysLeft,
                runway.daysLeft,
            ),
            titleMaxLines = 2,
            supportingText = listOfNotNull(burn, payday).joinToString(" · "),
            supportingMaxLines = 2,
            icon = Icons.Outlined.Schedule,
            iconTint = accent,
            markerColor = if (runway.shortOfIncome) accent else null,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onOpenAccounts,
        )
    }
}

/**
 * What this month still owes, named rather than folded into a forecast.
 *
 * The number stays out of the pace insight on purpose: a projection the person can also read in
 * Statistics must mean the same thing on both screens, and an obligation is a fact about the future
 * rather than a rate. Naming the payees is what makes the sum checkable.
 */
@Composable
internal fun HomeRecurringRow(charges: List<RecurringCharge>) {
    val total = charges.sumOf(RecurringCharge::typicalMinor)
    val named = charges.take(MAX_NAMED_CHARGES).joinToString(" · ") { it.label }
    val rest = charges.size - MAX_NAMED_CHARGES
    val supporting = if (rest > 0) {
        "$named · ${pluralStringResource(R.plurals.home_recurring_more, rest, rest)}"
    } else {
        named
    }
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-recurring"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = stringResource(R.string.home_recurring_title),
            supportingText = supporting,
            supportingMaxLines = 2,
            icon = Icons.Outlined.EventRepeat,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            trailing = {
                WhfinAmount(
                    text = formatMinor(total, "GEL"),
                    symbol = currencySymbol("GEL"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}

private const val MAX_NAMED_CHARGES = 3

/**
 * Borrowed money the balances above still count as the person's own.
 *
 * One row per currency, because a debt in dollars and a debt in lari are two different promises and
 * adding them would need a rate to say something that needs none.
 */
@Composable
internal fun HomeDebtsOwedRow(
    debts: List<HomeDebt>,
    onOpenAccounts: () -> Unit,
) {
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-debts-owed"),
        tonal = true,
    ) {
        val shown = debts.take(2)
        shown.forEachIndexed { index, debt ->
            WhfinLedgerRow(
                title = stringResource(R.string.home_debts_owed_title),
                supportingText = debt.people.take(2).joinToString(" · ").takeIf(String::isNotEmpty),
                icon = Icons.Outlined.Handshake,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                trailing = {
                    WhfinAmount(
                        text = formatMinor(debt.outstandingMinor, debt.currency),
                        symbol = currencySymbol(debt.currency),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                divider = index != shown.lastIndex,
                onClick = onOpenAccounts,
            )
        }
    }
}

/** A declared window reads as one span: "5–10 Sep", or two dates when it crosses a month. */
@Composable
private fun incomeWindowLabel(window: NextIncomeWindow): String {
    val dayMonth = remember { DateTimeFormatter.ofPattern("d MMM") }
    return when {
        window.from == window.to -> window.from.format(dayMonth)
        window.from.month == window.to.month ->
            "${window.from.dayOfMonth}–${window.to.format(dayMonth)}"
        else -> "${window.from.format(dayMonth)} – ${window.to.format(dayMonth)}"
    }
}

/**
 * The one row that stands for everything Home decided not to raise yet.
 *
 * Kept quiet on purpose: the conditions behind it are real but ranked below what is already shown,
 * and a second alarming block would defeat the cap it exists to enforce.
 */
@Composable
internal fun HomeNoticesFold(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-notices-fold"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = if (expanded) {
                stringResource(R.string.home_notices_collapse)
            } else {
                pluralStringResource(R.plurals.home_notices_folded, count, count)
            },
            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onToggle,
        )
    }
}

@Composable
private fun CredoSyncReminderCard(
    reminder: CredoSyncReminder,
    onClick: () -> Unit,
) {
    val age = reminder.daysSinceSync?.let { days ->
        pluralStringResource(R.plurals.home_credo_sync_days, days, days)
    } ?: stringResource(R.string.home_credo_sync_never)
    val waiting = reminder.awaitingStatementCount.takeIf { it > 0 }?.let { count ->
        pluralStringResource(R.plurals.home_credo_sync_waiting, count, count)
    }
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-credo-sync-reminder"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = stringResource(R.string.home_credo_sync_title),
            supportingText = listOfNotNull(age, waiting).joinToString(" · "),
            supportingMaxLines = 2,
            icon = Icons.Default.Sync,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onClick,
        )
    }
}

@Composable
private fun HomeInsightsSection(
    insights: List<HomeInsight>,
    onOpenAnalytics: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WhfinSectionLabel(stringResource(R.string.home_insights_title))
        WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            insights.forEachIndexed { index, insight ->
                HomeInsightRow(insight, onOpenAnalytics)
                if (index < insights.lastIndex) HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeInsightRow(
    insight: HomeInsight,
    onClick: () -> Unit,
) {
    val projected = when (insight) {
        is HomeInsight.SpendingPace -> insight.projectedExpenseMinor
        is HomeInsight.CategoryDriver -> insight.projectedExpenseMinor
    }
    val previous = when (insight) {
        is HomeInsight.SpendingPace -> insight.previousMonthExpenseMinor
        is HomeInsight.CategoryDriver -> insight.previousMonthExpenseMinor
    }
    val improving = projected < previous
    val accent = if (improving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    // A forecast is a number with a comparison, not a sentence about one. The reading stays in the
    // ledger grammar the rest of Home uses: what it is on the left, how much on the right.
    val title = when (insight) {
        is HomeInsight.SpendingPace -> stringResource(R.string.home_insight_pace)
        is HomeInsight.CategoryDriver -> insight.name ?: stringResource(R.string.analytics_uncategorized)
    }
    val supporting = stringResource(R.string.home_insight_previous_month, formatMinor(previous, "GEL"))
    val icon = when (insight) {
        is HomeInsight.SpendingPace -> Icons.AutoMirrored.Filled.TrendingUp
        is HomeInsight.CategoryDriver -> Icons.Default.Category
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(32.dp).background(accent.copy(alpha = .14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WhfinAmount(
                text = formatMinor(projected, "GEL"),
                symbol = currencySymbol("GEL"),
                color = accent,
            )
        }
    }
}

@Composable
private fun MonthlyFlowSummary(
    income: Long,
    expenses: Long,
    onClick: () -> Unit,
    unconverted: List<AnalyticsCurrencyValue> = emptyList(),
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WhfinSectionLabel(stringResource(R.string.feed_this_month), Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.analytics_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Результат месяца — герой блока; доход/расход остаются контекстом под ним.
            val net = income - expenses
            WhfinAmount(
                formatMinor(net, "GEL", withSign = true),
                symbol = currencySymbol("GEL"),
                style = MaterialTheme.typography.headlineMedium,
                color = if (net < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryValue(
                    Modifier.weight(1f),
                    stringResource(R.string.summary_income), income,
                    MaterialTheme.colorScheme.primary,
                )
                SummaryValue(
                    Modifier.weight(1f),
                    stringResource(R.string.summary_expenses), -expenses,
                    MaterialTheme.colorScheme.tertiary,
                )
            }
            // Строка, которой в итоге нет: валютный расход без курса своего дня не превращается
            // в ноль и не прячется — иначе сумма месяца молча меньше, чем прожитый месяц.
            if (unconverted.isNotEmpty()) {
                val named = unconverted.take(2).joinToString(" · ") {
                    formatMinor(it.expenseMinor, it.currency)
                }
                val rest = unconverted.size - 2
                Text(
                    stringResource(
                        R.string.home_month_unconverted,
                        if (rest > 0) "$named +$rest" else named,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Итоговая черта книги: блок месяца закрывается двойной линейкой, обычные разделители
            // ленты остаются одинарными.
            WhfinTotalRule()
        }
    }
}

/**
 * Знак вместо стрелки: направленные глифы читались двусмысленно ("вниз" одновременно значит
 * и «пришло на счёт», и «стало меньше"). Подписанная табличная сумма однозначна и тише.
 */
@Composable
private fun SummaryValue(
    modifier: Modifier,
    label: String,
    value: Long,
    color: Color,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WhfinAmount(
            formatMinor(value, "GEL", withSign = true),
            symbol = currencySymbol("GEL"),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

/**
 * The way back into a setup that was walked past.
 *
 * Skipping is allowed, so this offers rather than nags: it is the quietest notice kind, it withdraws
 * on its own as soon as a bank exists, and refusing it here is permanent — an offer that cannot be
 * turned off for good is a demand.
 */
@Composable
internal fun SetupInvitationCard(onResume: () -> Unit, onDismiss: () -> Unit) {
    WhfinNotice(
        title = stringResource(R.string.home_setup_invitation_title),
        body = stringResource(R.string.home_setup_invitation_body),
        icon = Icons.Default.AccountBalance,
        kind = WhfinNoticeKind.Info,
        actionLabel = stringResource(R.string.home_setup_invitation_action),
        onAction = onResume,
        dismissIcon = Icons.Default.Close,
        dismissContentDescription = stringResource(R.string.home_setup_invitation_dismiss),
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SmsOnboardingCard(onEnable: () -> Unit, onDismiss: () -> Unit) {
    WhfinNotice(
        title = stringResource(R.string.sms_onboarding_title),
        body = stringResource(R.string.sms_onboarding_body),
        icon = Icons.Default.Sms,
        kind = WhfinNoticeKind.Attention,
        actionLabel = stringResource(R.string.sms_onboarding_action),
        onAction = onEnable,
        dismissIcon = Icons.Default.Close,
        dismissContentDescription = stringResource(R.string.sms_onboarding_dismiss),
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun DayHeader(
    day: LocalDate,
    expensesByCurrency: Map<String, Long>,
    gelFromConversions: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // День недели полезен: операции вспоминают как «в субботу», а не по номеру дня. Год печатаем
    // только для прошлых лет, иначе он занимает место в каждом заголовке текущего года.
    val label = when (day) {
        LocalDate.now() -> stringResource(R.string.date_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> day.format(
            DateTimeFormatter.ofPattern(
                if (day.year == LocalDate.now().year) "EEE, d MMM" else "EEE, d MMM yyyy",
            ),
        )
    }
    val directGel = expensesByCurrency["GEL"] ?: 0L
    val totalGel = directGel + gelFromConversions
    val hasBreakdown = expensesByCurrency.keys.any { it != "GEL" } || gelFromConversions > 0L
    // День без расходов (только переводы/доход) не должен печатать бессмысленный "0.00 ₾".
    // Если в GEL нечего показать, ведём итогом единственную иностранную валюту дня.
    val foreignTotals = expensesByCurrency.filterKeys { it != "GEL" }
    val totalCurrency = when {
        totalGel > 0L -> "GEL"
        foreignTotals.size == 1 -> foreignTotals.keys.first()
        else -> null
    }
    val totalText = when {
        totalGel > 0L -> formatMinor(totalGel, "GEL")
        foreignTotals.size == 1 -> foreignTotals.entries.first()
            .let { (currency, total) -> formatMinor(total, currency) }
        else -> null
    }
    val showBreakdown = hasBreakdown && (totalText != null || foreignTotals.size > 1)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                Text(label.uppercase(), Modifier.padding(start = 9.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (totalText != null || showBreakdown) Row(
                Modifier.then(if (showBreakdown) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onToggle) else Modifier)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (totalText != null) WhfinAmount(totalText,
                    symbol = totalCurrency?.let(::currencySymbol),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                if (showBreakdown) Icon(Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded && showBreakdown) Text(
            buildList {
                if (directGel > 0L) add(formatMinor(directGel, "GEL"))
                if (gelFromConversions > 0L) add("FX ${formatMinor(gelFromConversions, "GEL")}")
                expensesByCurrency.entries.filter { it.key != "GEL" }.sortedBy { it.key }
                    .forEach { (currency, total) -> add(formatMinor(total, currency)) }
            }.joinToString("  ·  "),
            modifier = Modifier.align(Alignment.End).padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** БАНКОВСКИЙ КАПС -> Title Case: "OPENAI *CHATGPT SUBSCR" читается тяжело. */
private fun humanizeTitle(raw: String): String {
    val letters = raw.filter(Char::isLetter)
    if (letters.isEmpty() || letters != letters.uppercase()) return raw
    return raw.lowercase().split(" ").joinToString(" ") { word ->
        // Первая БУКВА слова, а не первый символ ("*chatgpt" -> "*Chatgpt")
        val index = word.indexOfFirst(Char::isLetter)
        if (index < 0) word
        else word.take(index) + word[index].titlecase() + word.drop(index + 1)
    }
}

@Composable
internal fun UnroutedOperationRow(
    operation: UnroutedOperation,
    onClick: () -> Unit,
) {
    val diagnostic = operation.diagnostic
    val grouped = diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
        diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
    val title = diagnostic.counterparty?.let(::humanizeTitle) ?: stringResource(
        when (diagnostic.kind) {
            SmsDiagnosticKind.CARD_PAYMENT -> R.string.sms_kind_card
            SmsDiagnosticKind.OUTGOING_TRANSFER -> R.string.sms_kind_outgoing
            SmsDiagnosticKind.INCOMING_TRANSFER -> R.string.sms_kind_incoming
            SmsDiagnosticKind.DEPOSIT_TOP_UP -> R.string.sms_kind_deposit_top_up
            SmsDiagnosticKind.BILL_PAYMENT -> R.string.sms_kind_bill
            SmsDiagnosticKind.CASH_DEPOSIT -> R.string.sms_kind_cash_deposit
            SmsDiagnosticKind.INTEREST -> R.string.sms_kind_interest
            SmsDiagnosticKind.OWN_TRANSFER -> R.string.sms_kind_own_transfer
            SmsDiagnosticKind.CURRENCY_EXCHANGE -> R.string.sms_kind_exchange
            SmsDiagnosticKind.IGNORED, SmsDiagnosticKind.UNRECOGNIZED -> R.string.feed_unrouted_operation
        },
    )
    val cardHint = diagnostic.cardLast4?.let {
        stringResource(R.string.sms_card_suffix, it)
    }
    val routingLabel = stringResource(
        if (grouped) R.string.feed_unrouted_choose_accounts else R.string.feed_unrouted_choose_account,
    )
    val currency = diagnostic.currency ?: diagnostic.balanceCurrency ?: "—"
    val amount = diagnostic.amountMinor ?: 0L
    val signedAmount = when (diagnostic.kind) {
        SmsDiagnosticKind.CARD_PAYMENT,
        SmsDiagnosticKind.OUTGOING_TRANSFER -> -kotlin.math.abs(amount)
        SmsDiagnosticKind.INCOMING_TRANSFER,
        SmsDiagnosticKind.DEPOSIT_TOP_UP -> kotlin.math.abs(amount)
        else -> kotlin.math.abs(amount)
    }
    val withSign = diagnostic.kind != SmsDiagnosticKind.OWN_TRANSFER &&
        diagnostic.kind != SmsDiagnosticKind.CURRENCY_EXCHANGE

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("unrouted-operation-${diagnostic.id}"),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = .11f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (grouped) Icons.Default.SwapHoriz else Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f),
                    )
                    Text(
                        listOfNotNull(stringResource(R.string.feed_bank_sms_source), cardHint)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    WhfinAmount(
                        text = formatMinor(signedAmount, currency, withSign = withSign),
                        symbol = currencySymbol(currency),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (
                        diagnostic.secondaryAmountMinor != null &&
                        diagnostic.secondaryCurrency != null
                    ) {
                        WhfinAmount(
                            text = "→ ${formatMinor(
                                diagnostic.secondaryAmountMinor,
                                diagnostic.secondaryCurrency,
                            )}",
                            symbol = currencySymbol(diagnostic.secondaryCurrency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier.size(6.dp).background(
                                MaterialTheme.colorScheme.tertiary,
                                CircleShape,
                            ),
                        )
                        Text(
                            routingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
        }
    }
}

@Composable
internal fun FeedRow(
    item: FeedItem,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onConfirmPending: (() -> Unit)? = null,
) {
    val tx = item.tx
    val isTransfer = tx.isTransfer || tx.transferGroupId != null
    val title = item.transferSummary
        ?: (if (isTransfer) stringResource(R.string.feed_own_transfer) else null)
        ?: (item.merchant?.displayName ?: tx.rawCounterparty ?: tx.note)?.let(::humanizeTitle)
        ?: item.category?.let { category ->
            if (category.isSystem && category.name == dev.whekin.whfin.data.db.CategorySeeder.UNACCOUNTED) {
                stringResource(R.string.category_unaccounted)
            } else {
                category.name
            }
        }
        ?: stringResource(R.string.feed_no_description)
    val categoryName = when {
        tx.isTransfer || tx.transferGroupId != null -> stringResource(R.string.feed_transfer)
        item.category != null ->
            if (item.category.isSystem && item.category.name == dev.whekin.whfin.data.db.CategorySeeder.UNACCOUNTED) {
                stringResource(R.string.category_unaccounted)
            } else {
                item.category.name
            }
        else -> stringResource(R.string.feed_uncategorized)
    }
    // Источник: кеш/название счёта, для карточного счёта — маска карты
    val sourceHint = item.cardHint ?: item.account?.name
    val splitHint = item.splitOnPeople.firstOrNull()?.let { (name, _) ->
        if (item.splitOnPeople.size > 1) "$name +${item.splitOnPeople.size - 1}" else name
    }
    val subtitle = listOfNotNull(categoryName, sourceHint, splitHint).joinToString(" · ")
    val amountColor = when {
        tx.isTransfer || tx.transferGroupId != null -> MaterialTheme.colorScheme.onSurfaceVariant
        tx.amountMinor > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("feed-row-${item.tx.id}").combinedClickable(
            onClick = onClick,
            onLongClickLabel = stringResource(R.string.transactions_select_action),
            onLongClick = onLongClick,
        ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Маркер категории — круг с тихой заливкой, как в статистике и формах. Обведённый
            // квадрат делал иконку самым тяжёлым элементом строки и спорил с ledger-сеткой.
            Surface(shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else
                    item.category?.let { Color(it.color).copy(alpha = .14f) } ?: MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else
                            CategoryIcons.resolve(item.category?.icon, isTransfer = tx.isTransfer),
                        contentDescription = if (selected) stringResource(R.string.transactions_selected) else null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else
                            item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (item.isDebt) Text(
                    stringResource(R.string.debt_person_owes, item.debtPersonName ?: "—", formatMinor(item.debtMinor ?: 0L, tx.currency)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                WhfinAmount(
                    formatMinor(
                        if (isTransfer) kotlin.math.abs(tx.amountMinor) else tx.amountMinor,
                        tx.currency,
                        withSign = !isTransfer,
                    ),
                    symbol = currencySymbol(tx.currency),
                    color = amountColor,
                )
                if (isTransfer && item.destinationAmountMinor != null && item.destinationCurrency != null &&
                    item.destinationCurrency != tx.currency) {
                    WhfinAmount(
                        "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                        symbol = currencySymbol(item.destinationCurrency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Оплата через авто-конвертацию: реальная цена в исходной валюте
                if (item.fundedByConversionMinor != null && item.fundedByConversionCurrency != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            "≈ ${formatMinor(item.fundedByConversionMinor, item.fundedByConversionCurrency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tx.status == TxStatus.PENDING) {
                    // Confirming a draft is the most repeated gesture there is, so the marker that
                    // says it is a draft is also the control that clears it — one tap, in the feed.
                    val confirm = onConfirmPending
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = if (confirm == null) Modifier else Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                onClickLabel = stringResource(R.string.transaction_confirm),
                                onClick = confirm,
                            )
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                            .offset(x = (-6).dp),
                    ) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                        Text(stringResource(R.string.status_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        if (confirm != null) Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
        )
    }
    }
}

@Preview(name = "Home populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Home dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Home font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Preview(name = "Home compact", widthDp = 400, heightDp = 500, showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContentPreview() {
    val account = AccountEntity(1, "Credo GEL •0001", AccountType.BANK, currency = "GEL", iban = "GE00CD0000000000000001")
    val category = CategoryEntity(1, "Subscriptions", kind = CategoryKind.EXPENSE, icon = "Subscriptions", color = 0xFF5D7F91.toInt())
    val transaction = TransactionEntity(
        id = 1,
        accountId = account.id,
        amountMinor = -2_360,
        currency = "USD",
        occurredAt = System.currentTimeMillis(),
        rawCounterparty = "OPENAI *CHATGPT SUBSCR",
        categoryId = category.id,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
    )
    val item = FeedItem(
        transaction,
        MerchantEntity(1, "openai", "OpenAI subscription", category.id),
        category,
        account,
        "••0001",
        fundedByConversionMinor = 6_346,
        fundedByConversionCurrency = "GEL",
        day = LocalDate.now(),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WhfinContextHeader(
                    stringResource(R.string.home_spendable),
                    formatMinor(38_140, "GEL"),
                    valueSymbol = currencySymbol("GEL"),
                ) {
                    WhfinIconButton(Icons.AutoMirrored.Filled.TrendingUp, "Statistics", {}, outlined = false)
                    WhfinIconButton(Icons.AutoMirrored.Outlined.ReceiptLong, "History", {}, outlined = false)
                }
                MonthlyFlowSummary(730_800, 109_127, {})
                HomeRunwayRow(
                    HomeRunway(
                        daysLeft = 4,
                        dailyBurnMinor = 9_500,
                        nextIncome = NextIncomeWindow(
                            LocalDate.now().withDayOfMonth(5).plusMonths(1),
                            LocalDate.now().withDayOfMonth(10).plusMonths(1),
                        ),
                        shortOfIncome = true,
                    ),
                    {},
                )
                HomeRecurringRow(
                    listOf(
                        RecurringCharge("iban:GE00CD0000000000000009", "Landlord", 120_000, 3, LocalDate.now()),
                        RecurringCharge("merchant:2", "Silknet", 6_000, 12, LocalDate.now()),
                    ),
                )
                HomePhysicalCardBalance(
                    balances = listOf(
                        PhysicalCardHomeBalance(
                            accountId = account.id,
                            accountName = "Everyday",
                            balanceMinor = 9_540,
                            cardLast4s = listOf("0000"),
                        ),
                    ),
                    notificationsEnabled = true,
                    onOpenAccounts = {},
                    onEnableNotifications = {},
                    onTopUp = {},
                )
                HomeDebtsOwedRow(
                    listOf(HomeDebt("GEL", 18_000, listOf("Maya"))),
                    {},
                )
                HomeNoticesFold(count = 3, expanded = false, onToggle = {})
                HomeSectionHeader("Today", "All transactions", {}, metricMinor = 4_720)
                HomeInsightsSection(
                    listOf(
                        HomeInsight.SpendingPace(169_147, 96_000),
                        HomeInsight.CategoryDriver("Subscriptions", 70_740, 21_400),
                    ),
                    {},
                )
                HomeSectionHeader("Today", "All transactions", {})
                FeedRow(item, {})
            }
        }
    }
}
