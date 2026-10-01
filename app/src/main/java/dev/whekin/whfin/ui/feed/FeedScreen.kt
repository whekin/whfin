package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.mutation.canDeleteLocally
import dev.whekin.whfin.data.mutation.isBalanceAdjustment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import dev.whekin.whfin.ui.counterpartyLabel
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import dev.whekin.whfin.ui.bank.canLaunch
import dev.whekin.whfin.ui.bank.launchBank
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.sms.needsRoutingDecision
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.ui.components.CategoryGrid
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.convertedTotalLabel
import dev.whekin.whfin.ui.formatDecimal
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.data.mutation.MutationRejection
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import dev.whekin.whfin.data.notifications.PhysicalCardBalanceStatus
import dev.whekin.whfin.data.notifications.physicalCardBalanceStatus
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.transfer.OwnTransferSide
import dev.whekin.whfin.ui.transfer.OwnTransferChoice
import dev.whekin.whfin.ui.transfer.OwnTransferSheet
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.sms.SmsRoutingSheet
import dev.whekin.whfin.ui.analytics.AnalyticsUiModel
import dev.whekin.whfin.ui.analytics.AnalyticsUiState
import dev.whekin.whfin.ui.analytics.AnalyticsViewModel
import androidx.compose.material.icons.outlined.FactCheck
import dev.whekin.whfin.ui.settings.integrityFamilyLabel

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
    bankSyncStatuses: List<dev.whekin.whfin.data.sync.BankSyncStatus> = emptyList(),
    showSetupInvitation: Boolean = false,
    onResumeSetup: () -> Unit = {},
    onDismissSetupInvitation: () -> Unit = {},
    deferredCategoryCount: Int = 0,
    onReviewDeferredCategories: () -> Unit = {},
    onDismissDeferredCategories: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    /** "Review all" opens the same set Home just listed, not the whole ledger. */
    onReviewAll: () -> Unit = {},
    onWaitingBank: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenCredoSync: () -> Unit = {},
    onOpenTbcSync: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    /** Home carries the app's stable way into Settings; it is not a property of Accounts. */
    onOpenSettings: (() -> Unit)? = null,
    hasLowBalanceNotificationPermission: Boolean = true,
    onRequestLowBalanceNotificationPermission: () -> Unit = {},
    addRequestKey: Int = 0,
    onAddRequestConsumed: () -> Unit = {},
    /** One-shot: open the record with the "needs a decision" filter already applied. */
    reviewRequestKey: Int = 0,
    waitingRequestKey: Int = 0,
    onWaitingRequestConsumed: () -> Unit = {},
    onReviewRequestConsumed: () -> Unit = {},
    /** One-shot: open this row's details, wherever it sits in the ledger. */
    openTransactionId: Long? = null,
    onOpenTransactionConsumed: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
) {
    val context = LocalContext.current
    val homeAnalyticsState = collectHomeAnalyticsState(mode == FeedMode.HOME)
    val items by viewModel.items.collectAsState()
    val formState by viewModel.formSaveState.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoriesByUsage by viewModel.categoriesByUsage.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val people by viewModel.people.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val unroutedOperations by viewModel.unroutedOperations.collectAsState()
    val rejected by viewModel.rejected.collectAsState()
    val integrityIssues by viewModel.integrityIssues.collectAsState()
    val integrityNoticeVisible by viewModel.integrityNoticeVisible.collectAsState()
    val integrityCodes by viewModel.integrityCodes.collectAsState()
    var showBankSync by remember { mutableStateOf(false) }
    val bankSyncTimes by viewModel.bankSyncTimes.collectAsState()
    if (showBankSync) BankSyncSheet(bankSyncTimes, { showBankSync = false }, bankSyncStatuses) { bank ->
        showBankSync = false
        if (bank == "Credo") onOpenCredoSync() else onOpenTbcSync()
    }
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
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var rememberedCategoryFor by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LaunchedEffect(rememberedCategoryFor) {
        if (rememberedCategoryFor != null) {
            kotlinx.coroutines.delay(accessibility?.calculateRecommendedTimeoutMillis(4000, containsText = true) ?: 4000)
            rememberedCategoryFor = null
        }
    }
    var routingFor by remember { mutableStateOf<UnroutedOperation?>(null) }
    var categoryFor by remember { mutableStateOf<FeedItem?>(null) }
    var deleteFor by remember { mutableStateOf<FeedItem?>(null) }
    var correctFor by remember { mutableStateOf<FeedItem?>(null) }
    var debtFor by remember { mutableStateOf<FeedItem?>(null) }
    var splitFor by remember { mutableStateOf<FeedItem?>(null) }
    var ownTransferFor by remember { mutableStateOf<FeedItem?>(null) }
    val ownLinkGroupIds by viewModel.ownLinkGroupIds.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<FeedItem?>(null) }
    var expandedTransferDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var expandedExpenseDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FeedFilter.ALL) }
    // Applied once per request rather than on every visit, so a later return to the record keeps
    // whatever the reader last chose.
    LaunchedEffect(reviewRequestKey) {
        if (reviewRequestKey != 0) {
            filter = FeedFilter.NEEDS_REVIEW
            onReviewRequestConsumed()
        }
    }
    LaunchedEffect(waitingRequestKey) {
        if (waitingRequestKey != 0) { filter = FeedFilter.WAITING_BANK; onWaitingRequestConsumed() }
    }
    LaunchedEffect(openTransactionId) {
        openTransactionId?.let { id ->
            // Read by id rather than searched for in the loaded window: a finding can point at a
            // row far older than the last few hundred, and arriving at a screen that silently shows
            // nothing would be worse than the bare id it replaced.
            details = viewModel.itemById(id)
            onOpenTransactionConsumed()
        }
    }
    var sort by remember { mutableStateOf(FeedSort.NEWEST) }
    var categoryFilters by remember { mutableStateOf(emptySet<Long>()) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var noticesExpanded by remember { mutableStateOf(false) }
    val monthFlow by viewModel.monthFlow.collectAsState()
    // One signal, taken from the pipeline that produces the numbers: while it has no answer the
    // screen shows the shape of what is coming instead of a total of nothing.
    val restoring by viewModel.restoring.collectAsState()
    val feedLoaded = monthFlow != null && !restoring
    val attention by viewModel.attention.collectAsState()
    val recent by viewModel.recentActivity.collectAsState()
    val moneySplit by viewModel.moneySplit.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()
    val cashForecast by viewModel.cashForecast.collectAsState()
    val recurringDue = cashForecast?.stillDue.orEmpty()
    val debtsOwed by viewModel.debtsOwed.collectAsState()
    val homeAnalytics = (homeAnalyticsState?.state as? AnalyticsUiState.Content)?.data
    val income = homeAnalytics?.incomeMinor ?: monthFlow?.incomeMinor ?: 0L
    val expenses = homeAnalytics?.expenseMinor ?: monthFlow?.expenseMinor ?: 0L
    val homeInsights = homeAnalytics?.let(::deriveHomeInsights).orEmpty()
    val runway = cashForecast?.runway
    val visibleItems = items.filter { item ->
        val matchesType = matchesFeedFilter(item, filter)
        val haystack = listOfNotNull(
            item.transferSummary, item.merchant?.displayName, item.tx.rawCounterparty,
            // Searchable in both alphabets, whichever one the rows are printed in: the owner may
            // type what the screen shows or what the statement shows, and neither should miss.
            item.merchant?.displayName?.let { counterpartyLabel(it, latin = true) },
            item.tx.rawCounterparty?.let { counterpartyLabel(it, latin = true) },
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
            // An unrouted message cannot be confirmed — it needs an account first — but it is
            // exactly as much "something the owner still has to answer" as a draft is, and Home
            // counts it as one. A filter that dropped it would send "Review all" to a shorter list
            // than the one it was pressed from.
            FeedFilter.NEEDS_REVIEW -> diagnostic.needsRoutingDecision()
            FeedFilter.WAITING_BANK -> false
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
    val hasReviewableSelection = selectedItems.any { it.tx.source == TxSource.SMS && it.tx.status == TxStatus.PENDING }
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
            .then(if (selectionMode || mode != FeedMode.HOME) Modifier else Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection)),
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
                        icon = Icons.Default.CheckCircle,
                        enabled = hasReviewableSelection,
                        contentDescription = stringResource(R.string.transactions_mark_reviewed_selected),
                        onClick = {
                            viewModel.updateStatuses(selectedItems, TxStatus.CONFIRMED)
                            selectedIds = emptySet()
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
                val total = if (mode == FeedMode.HOME) moneySplit?.available else moneySplit?.total
                val totalAmount = total?.amount
                val headline = stringResource(
                    if (mode == FeedMode.HOME) R.string.home_spendable else R.string.balance_total,
                )
                val headerActions: @Composable RowScope.() -> Unit = {
                    if (mode == FeedMode.HOME) {
                        // Analytics and the full record are destinations now, so the two icons that
                        // used to be their only doors are gone from here. What the header keeps is
                        // the one thing that had no stable place at all.
                        if (showCredoSyncReminder) BankSyncIndicator(bankSyncStatuses) { showBankSync = true }
                        onOpenSettings?.let { openSettings ->
                            WhfinIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                onClick = openSettings,
                                outlined = false,
                            )
                        }
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
                if (mode == FeedMode.HOME) WhfinContextHeader(
                    label = convertedTotalLabel(headline, total),
                    value = if (totalAmount == null) "—" else formatDecimal(totalAmount, total.currency),
                    valueSymbol = currencySymbol(total?.currency ?: displayCurrency),
                    scrollBehavior = headerScrollBehavior, onValueClick = viewModel::rotateDisplayCurrency,
                    valueClickLabel = stringResource(R.string.net_worth_rotate), actions = headerActions,
                ) else dev.whekin.whfin.core.ui.WhfinListHeader(
                    title = stringResource(when (filter) {
                        FeedFilter.WAITING_BANK -> R.string.feed_waiting_bank
                        FeedFilter.NEEDS_REVIEW -> R.string.home_needs_attention
                        FeedFilter.EXPENSES -> R.string.feed_filter_expenses
                        FeedFilter.INCOME -> R.string.feed_filter_income
                        FeedFilter.TRANSFERS -> R.string.feed_filter_transfers
                        FeedFilter.ALL -> R.string.transactions_history_title
                    }),
                    subtitle = if (feedLoaded) stringResource(R.string.history_result_count, timelineEntries.size)
                        else stringResource(R.string.analytics_transactions_loading),
                    actions = headerActions,
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 28.dp),
        ) {
        if (mode == FeedMode.HOME && !selectionMode) {
            if (!feedLoaded) {
                item(key = "skeleton") { HomeSkeleton() }
            } else if (homeNothingRecorded(feedLoaded, items, unroutedOperations, recurringDue, debtsOwed) &&
                integrityIssues == 0 && physicalCardBalances.isEmpty() && deferredCategoryCount == 0) {
                item(key = "empty") {
                    WhfinStatePane(
                        state = WhfinPaneState.Empty,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        actionLabel = stringResource(if (showSetupInvitation) R.string.home_setup_invitation_action else R.string.add_transaction),
                        onAction = if (showSetupInvitation) onResumeSetup else ({ showAdd = true }),
                    )
                }
            } else {
            // Home reads as one answer to "how am I doing for money", in the order the answer is
            // built: what there is, how far it goes, what is already owed out of it, what still
            // needs a decision, what just happened — and only then the month, compactly.
            val visibleRunway = visibleRunway(runway)
            visibleRunway?.let { reading ->
                item(key = "runway") { HomeRunwayRow(reading, onOpenAccounts = onOpenAccounts) }
            }
            // The forecast block lists the same expected payments inside it. Naming them twice made
            // a prediction look like two separate facts, so the standalone row speaks only when
            // nothing above it has already spoken for them.
            val billsNamedByRunway = visibleRunway?.recurringOccurrences?.isNotEmpty() == true
            if (showsRecurringSeparately(recurringDue.isNotEmpty(), billsNamedByRunway)) item(key = "recurring") {
                HomeRecurringRow(recurringDue)
            }
            if (debtsOwed.isNotEmpty()) item(key = "debts-owed") {
                HomeDebtsOwedRow(debtsOwed, onOpenAccounts)
            }
            val waitingCount = items.filter(::waitingForBank).distinctBy { it.tx.transferGroupId?.let { group -> "group:$group" } ?: "tx:${it.tx.id}" }.size
            if (waitingCount > 0) item(key = "waiting-bank") {
                WhfinLedgerRow(title = stringResource(R.string.home_waiting_bank, waitingCount),
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onWaitingBank, trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) })
            }

            val lowCardBalances = physicalCardBalances.filter {
                physicalCardBalanceStatus(it.balanceMinor) != PhysicalCardBalanceStatus.Enough
            }
            // Every standing condition is a block competing for the same first screenful, so they
            // are ranked and capped rather than stacked in the order the code happens to know them.
            val presentNotices = buildSet {
                if (lowCardBalances.isNotEmpty()) add(HomeNotice.CARD_BALANCE)
                if (showSetupInvitation) add(HomeNotice.SETUP)
                if (integrityIssues > 0 && integrityNoticeVisible) add(HomeNotice.INTEGRITY)
                if (credoReminder != null && showCredoSyncReminder) add(HomeNotice.CREDO_SYNC)
                if (showSmsOnboarding) add(HomeNotice.SMS_ONBOARDING)
            }
            val triage = triageHomeNotices(presentNotices, expanded = noticesExpanded)
            // Standing conditions and unanswered drafts were two stacks: an unnamed pile of cards,
            // then a heading for the rows. They are one question — what still needs the owner —
            // so they sit under one heading, conditions first because they carry their own action.
            if (triage.visible.isNotEmpty() || triage.foldable > 0 || attention.isNotEmpty()) {
                item(key = "decision-header") {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_needs_attention),
                        action = stringResource(R.string.home_review_all).takeIf { attention.isNotEmpty() },
                        onAction = onReviewAll,
                        icon = Icons.Outlined.PendingActions,
                    )
                }
            }
            items(triage.visible, key = { "notice-${it.name}" }) { notice ->
                when (notice) {
                    HomeNotice.CARD_BALANCE -> HomePhysicalCardBalance(
                        balances = lowCardBalances,
                        notificationsEnabled = hasLowBalanceNotificationPermission,
                        onOpenAccounts = onOpenAccounts,
                        onEnableNotifications = onRequestLowBalanceNotificationPermission,
                        isBankLaunchable = context::canLaunch,
                        onOpenBank = context::launchBank,
                    )
                    HomeNotice.SETUP -> SetupInvitationCard(onResumeSetup, onDismissSetupInvitation)
                    // Technical state stays quiet unless the ledger contradicts itself; everything
                    // else about it lives in Data health. It says so with the icon of a check that
                    // ran, not a warning triangle: the accounts are open and the money is countable,
                    // and it can be set aside until the findings themselves change.
                    HomeNotice.INTEGRITY -> HomeIntegrityNotice(
                        count = integrityIssues,
                        codes = integrityCodes,
                        onOpenDataHealth = onOpenDataHealth,
                        onSetAside = viewModel::acknowledgeIntegrity,
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

            items(attention.filterIsInstance<FeedTimelineEntry.Unrouted>().take(3),
                key = { "home-unrouted-${it.operation.diagnostic.id}" }) { entry ->
                UnroutedOperationRow(operation = entry.operation, onClick = { routingFor = entry.operation })
            }

            if (deferredCategoryCount > 0) item(key = "deferred-category-review") {
                DeferredCategoryNotice(
                    count = deferredCategoryCount,
                    onReview = onReviewDeferredCategories,
                    onSetAside = onDismissDeferredCategories,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
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
                        icon = if (recent.isToday) Icons.Outlined.Today else Icons.Outlined.History,
                    )
                }
                items(recent.items, key = { "home-recent-${it.tx.id}" }) { item ->
                    FeedRow(item = item, onClick = { details = item })
                }
            }
            item(key = "summary") {
                MonthlyFlowSummary(
                    income = income,
                    expenses = expenses,
                    onClick = onOpenAnalytics,
                    insights = homeInsights,
                    unconverted = homeAnalytics?.otherCurrencyExpenses.orEmpty(),
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
            if (!feedLoaded) {
                item(key = "skeleton") { FeedSkeleton() }
            }
            if (feedLoaded && items.isEmpty() && unroutedOperations.isEmpty()) {
                item(key = "empty") {
                    WhfinStatePane(
                        state = WhfinPaneState.Empty,
                        title = stringResource(R.string.transactions_history_title),
                        body = stringResource(R.string.feed_empty),
                        illustration = dev.whekin.whfin.core.ui.WhfinIllustrationScene.History.takeIf {
                            search.isBlank() && filter == FeedFilter.ALL && categoryFilters.isEmpty()
                        },
                    )
                }
            }
        grouped.forEach { (day, dayEntries) ->
            val dayItems = dayEntries.mapNotNull { (it as? FeedTimelineEntry.Transaction)?.item }
            item(key = "header-$day") {
                // Расходы дня: GEL показываем сразу, остальные валюты раскрываются по тапу.
                val expenses = dayExpenses(dayItems)
                DayHeader(
                    day = day,
                    expensesByCurrency = expenses.byCurrency,
                    gelFromConversions = expenses.gelFromConversions,
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
            formState = formState,
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            onDismiss = { showAdd = false },
            onSave = { manual ->
                viewModel.addManual(manual)
            },
            onSaveDebt = { debt -> viewModel.addDebt(debt) },
            onCreateCategory = viewModel::createCategory,
            onCreateCashCurrency = viewModel::createCashCurrency,
            rankCategories = rankCategories,
            counterparties = counterparties,
        )
    }

    editFor?.let { item ->
        AddTransactionSheet(
            formState = formState,
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            editing = item,
            onDismiss = { editFor = null },
            onSave = {},
            onSaveDebt = {},
            onUpdate = { original, value -> viewModel.updateManual(original, value) },
            onCreateCategory = viewModel::createCategory,
            onCreateCashCurrency = viewModel::createCashCurrency,
            rankCategories = rankCategories,
            counterparties = counterparties,
        )
    }

    details?.let { original ->
        val item = items.firstOrNull { it.tx.id == original.tx.id } ?: original
        TransactionDetailsSheet(
            item = item,
            categoryAcknowledgement = rememberedCategoryFor?.takeIf { it.first == item.tx.id }?.second,
            onDismiss = { details = null; rememberedCategoryFor = null },
            onChangeCategory = {
                rememberedCategoryFor = null
                details = null
                categoryFor = item
            },
            onDelete = if (item.tx.canDeleteLocally()) {{
                details = null
                deleteFor = item
            }} else null,
            onCorrect = if (item.tx.source in setOf(dev.whekin.whfin.data.db.TxSource.STATEMENT, dev.whekin.whfin.data.db.TxSource.SMS, dev.whekin.whfin.data.db.TxSource.BANK_HOLD)) {{
                details = null
                correctFor = item
            }} else null,
            onEdit = if (item.tx.source == dev.whekin.whfin.data.db.TxSource.MANUAL) {{
                details = null
                editFor = item
            }} else null,
            onDebt = if (item.tx.amountMinor < 0 && !item.tx.isTransfer && item.tx.transferGroupId == null && item.splitOnPeople.isEmpty()) {{ details = null; debtFor = item }} else null,
            onClearDebt = if (item.isDebt) {{ viewModel.clearAllocations(item); details = null }} else null,
            onSplit = if (item.tx.amountMinor < 0 && !item.tx.isTransfer && item.tx.transferGroupId == null && !item.isDebt) {{ details = null; splitFor = item }} else null,
            onClearSplit = if (item.splitOnPeople.isNotEmpty()) {{ viewModel.clearAllocations(item); details = null }} else null,
            onConfirm = {
                viewModel.updateStatus(item, TxStatus.CONFIRMED)
                details = null
            },
            // Only a plain, unspoken-for row can be joined: a split or a debt already says whose
            // money it is, and a row already inside a movement has its answer.
            onOwnTransfer = if (
                item.tx.transferGroupId == null && !item.tx.isTransfer && !item.isDebt &&
                item.splitOnPeople.isEmpty() && item.tx.amountMinor != 0L
            ) {{
                details = null
                ownTransferFor = item
            }} else null,
            onClearOwnTransfer = if (item.tx.transferGroupId in ownLinkGroupIds) {{
                viewModel.unlinkOwnTransfer(item)
                details = null
            }} else null,
        )
    }

    ownTransferFor?.let { item ->
        val accountList by viewModel.accounts.collectAsState()
        var candidates by remember(item.tx.id) { mutableStateOf<List<OwnTransferSide>?>(null) }
        LaunchedEffect(item.tx.id) { candidates = viewModel.ownTransferCandidates(item) }
        candidates?.let { offered ->
            OwnTransferSheet(
                transaction = item.tx,
                candidates = offered,
                accounts = accountList,
                onDismiss = { ownTransferFor = null; details = item },
                onConfirm = { choice ->
                    when (choice) {
                        is OwnTransferChoice.Existing -> viewModel.linkOwnTransfer(item, choice.sides)
                        is OwnTransferChoice.Recorded -> viewModel.recordOwnTransferLeg(
                            item, choice.accountId, choice.amountMinor, choice.currency, choice.occurredAt,
                        )
                    }
                    ownTransferFor = null
                },
            )
        }
    }

    categoryFor?.let { item ->
        CategoryPickerSheet(
            item = item,
            // Сумма и валюта операции известны — пикер ранжируется умными подсказками.
            categories = remember(categories, suggester, item.tx.id) {
                suggester?.rankCategories(categories, item.tx.amountMinor, item.tx.currency) ?: categories
            },
            onDismiss = { categoryFor = null; details = item },
            onSelect = { category ->
                viewModel.assignCategory(item, category.id) {
                    if (categoryFor?.tx?.id == item.tx.id) {
                        categoryFor = null
                        if (item.merchant != null) rememberedCategoryFor = item.tx.id to System.nanoTime()
                        details = item.copy(category = category, tx = item.tx.copy(categoryId = category.id))
                    }
                }
            },
            onCreateCategory = viewModel::createCategory,
            formState = formState,
            onCreateAndSelect = { name, kind, icon, color ->
                viewModel.createCategoryAndAssign(item, name, kind, icon, color) { category ->
                    categoryFor = null
                    details = item.copy(category = category, tx = item.tx.copy(categoryId = category.id))
                }
            },
        )
    }

    deleteFor?.let { item ->
        WhfinConfirmDialog(
            title = stringResource(R.string.transaction_delete),
            body = stringResource(
                if (item.tx.isBalanceAdjustment()) R.string.balance_adjustment_delete_body
                else if (item.tx.transferGroupId != null) R.string.transaction_delete_transfer_body
                else R.string.transaction_delete_body,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.deleteLocal(item); deleteFor = null },
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
            onDismiss = { debtFor = null; details = item },
            formState = formState,
            onSave = { viewModel.saveExpenseBeneficiary(item, it, dev.whekin.whfin.data.db.AllocationPurpose.LOAN) },
        )
    }
    splitFor?.let { item ->
        SplitSheet(
            item = item,
            people = people,
            onDismiss = { splitFor = null; details = item },
            formState = formState,
            onSave = { beneficiary, purpose -> viewModel.saveExpenseBeneficiary(item, beneficiary, purpose) },
        )
    }
}


@Composable
private fun collectHomeAnalyticsState(enabled: Boolean): AnalyticsUiModel? {
    if (!enabled) return null
    val analyticsViewModel: AnalyticsViewModel = viewModel(key = "home-analytics")
    val state by analyticsViewModel.uiState.collectAsState()
    return state
}

/**
 * What the books say about themselves, said once and without an alarm.
 *
 * A contradiction is worth knowing about and is not an emergency: the accounts open, everything
 * else still adds up. Left as a permanent block with a warning triangle and no way out but a
 * repair, it became a demand — the shortest route to a quiet first screen was to go and fix the
 * data. So it names what was found rather than that something was, says what is not blocked, and
 * offers to be set aside in a word instead of an unlabelled cross.
 */
@Composable
internal fun HomeIntegrityNotice(
    count: Int,
    codes: List<String>,
    onOpenDataHealth: () -> Unit,
    onSetAside: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WhfinNotice(
        // One kind of finding has a name worth printing; several at once share no name, and the
        // general sentence is then the honest thing to say.
        title = integrityNoticeTitle(codes)?.let { stringResource(it) }
            ?: stringResource(R.string.home_integrity_title),
        body = pluralStringResource(R.plurals.home_integrity_body, count, count),
        icon = Icons.Outlined.FactCheck,
        kind = WhfinNoticeKind.Info,
        actionLabel = stringResource(R.string.data_health_title),
        onAction = onOpenDataHealth,
        secondaryActionLabel = stringResource(R.string.home_integrity_dismiss),
        onSecondaryAction = onSetAside,
        modifier = modifier.fillMaxWidth().testTag("home-integrity"),
    )
}

@Composable
internal fun DeferredCategoryNotice(count: Int, onReview: () -> Unit, onSetAside: () -> Unit,
    modifier: Modifier = Modifier) {
    WhfinNotice(
        title = stringResource(R.string.home_category_review_title),
        body = stringResource(R.string.home_category_review_body, count),
        icon = Icons.Default.TaskAlt,
        actionLabel = stringResource(R.string.home_category_review_action),
        onAction = onReview,
        secondaryActionLabel = stringResource(R.string.home_category_review_later),
        onSecondaryAction = onSetAside,
        modifier = modifier.fillMaxWidth().testTag("home-category-review"),
    )
}

/** The family every finding belongs to, when they all belong to one. */
internal fun integrityNoticeTitle(codes: List<String>): Int? =
    codes.takeIf { it.isNotEmpty() }?.map(::integrityFamilyLabel)?.distinct()?.singleOrNull()

internal enum class FeedFilter { ALL, EXPENSES, INCOME, TRANSFERS, NEEDS_REVIEW, WAITING_BANK }

/**
 * Which rows a filter keeps.
 *
 * Named rather than inlined because one of them has to agree with something outside this screen:
 * `NEEDS_REVIEW` is where "Review all" on Home leads, so it holds exactly the
 * unrouted operations that require an account choice. Categorisation is optional.
 */
internal fun matchesFeedFilter(item: FeedItem, filter: FeedFilter): Boolean = when (filter) {
    FeedFilter.ALL -> true
    FeedFilter.EXPENSES -> !item.tx.isTransfer && item.tx.amountMinor < 0 && !item.tx.isTransfer && item.tx.transferGroupId == null && !item.isDebt
    FeedFilter.INCOME -> !item.tx.isTransfer && item.tx.amountMinor > 0
    FeedFilter.TRANSFERS -> item.tx.isTransfer || item.tx.transferGroupId != null
    FeedFilter.NEEDS_REVIEW -> false
    FeedFilter.WAITING_BANK -> waitingForBank(item)
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
                        nextIncome = NextPayday(
                            usual = LocalDate.of(2026, 9, 5),
                            expected = LocalDate.of(2026, 9, 7),
                            weekendAdjusted = true,
                        ),
                        shortOfIncome = true,
                        shortfallMinor = 98_600,
                        recurringOccurrences = listOf(
                            RecurringOccurrence(
                                RecurringCharge(
                                    "iban:GE00CD0000000000000009",
                                    "Landlord",
                                    120_000,
                                    3,
                                    LocalDate.now().minusMonths(1),
                                ),
                                LocalDate.now().withDayOfMonth(3).plusMonths(1),
                            ),
                        ),
                    ),
                    onOpenAccounts = {},
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
                )
                HomeDebtsOwedRow(
                    listOf(HomeDebt("GEL", 18_000, listOf("Maya"))),
                    {},
                )
                HomeNoticesFold(count = 3, expanded = false, onToggle = {})
                HomeSectionHeader("Today", "All transactions", {}, metricMinor = 4_720)
                HomeSectionHeader("Today", "All transactions", {})
                FeedRow(item, {})
                MonthlyFlowSummary(
                    income = 730_800,
                    expenses = 109_127,
                    onClick = {},
                    insights = listOf(
                        HomeInsight.SpendingPace(169_147, 96_000),
                        HomeInsight.CategoryDriver("Subscriptions", 70_740, 21_400),
                    ),
                )
            }
        }
    }
}
