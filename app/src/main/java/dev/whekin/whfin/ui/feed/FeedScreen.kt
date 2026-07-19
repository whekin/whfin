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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.ui.parseToMinor
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
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
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.components.CategoryGrid
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinStatePane
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.theme.WhfinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    demoMode: Boolean = false,
    showSmsOnboarding: Boolean,
    onEnableSms: () -> Unit,
    onDismissSmsOnboarding: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    addRequestKey: Int = 0,
    onAddRequestConsumed: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsState()
    val balance by viewModel.totalBalanceMinor.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoriesByUsage by viewModel.categoriesByUsage.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val people by viewModel.people.collectAsState()
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var categoryFor by remember { mutableStateOf<FeedItem?>(null) }
    var deleteFor by remember { mutableStateOf<FeedItem?>(null) }
    var debtFor by remember { mutableStateOf<FeedItem?>(null) }
    var splitFor by remember { mutableStateOf<FeedItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
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
    val now = LocalDate.now()
    val monthItems = items.filter {
        it.day.month == now.month && it.day.year == now.year &&
            !it.tx.isTransfer && it.tx.transferGroupId == null && !it.isDebt &&
            it.tx.source != dev.whekin.whfin.data.db.TxSource.ADJUSTMENT && it.category?.isSystem != true
    }
    // Сводка месяца — только в основной валюте, чужие валюты не подмешиваем
    // (мультивалютная сводка появится вместе с курсами на экране статистики)
    val income = monthItems.filter { it.tx.currency == "GEL" }.sumOf { it.tx.amountMinor.coerceAtLeast(0) }
    val expenses = -monthItems.filter { it.tx.currency == "GEL" }.sumOf { it.tx.amountMinor.coerceAtMost(0) }
    val visibleItems = items.filter { item ->
        val matchesType = when (filter) {
            FeedFilter.ALL -> true
            FeedFilter.EXPENSES -> !item.tx.isTransfer && item.tx.amountMinor < 0 && !item.isDebt
            FeedFilter.INCOME -> !item.tx.isTransfer && item.tx.amountMinor > 0
            FeedFilter.TRANSFERS -> item.tx.isTransfer || item.tx.transferGroupId != null
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
    val sortedItems = when (sort) {
        FeedSort.NEWEST -> visibleItems.sortedByDescending { it.tx.occurredAt }
        FeedSort.OLDEST -> visibleItems.sortedBy { it.tx.occurredAt }
        FeedSort.AMOUNT -> visibleItems.sortedWith(
            compareByDescending<FeedItem> { it.day }
                .thenByDescending { kotlin.math.abs(it.tx.amountMinor) },
        )
    }
    val grouped = sortedItems.groupBy { it.day }
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
        topBar = {
            if (selectionMode) {
                WhfinContextHeader(
                    label = stringResource(R.string.transactions_selected),
                    value = selectedIds.size.toString(),
                ) {
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
                WhfinContextHeader(
                    label = stringResource(if (demoMode) R.string.demo_mode_header else R.string.balance_total),
                    value = formatMinor(balance, "GEL"),
                    scrollBehavior = headerScrollBehavior,
                ) {
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
        },
    ) { contentPadding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 28.dp),
        ) {
        if (!selectionMode) {
            item(key = "summary") { MonthlyFlowSummary(income, expenses, onOpenAnalytics) }
            item(key = "feed-tools") {
                FeedSearch(
                    search = search,
                    onSearchChange = { search = it },
                    searchVisible = showSearch,
                )
            }
            if (showSmsOnboarding) item(key = "sms-onboarding") {
                SmsOnboardingCard(onEnableSms, onDismissSmsOnboarding)
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                WhfinStatePane(
                    state = WhfinPaneState.Empty,
                    title = stringResource(R.string.tab_feed),
                    body = stringResource(R.string.feed_empty),
                )
            }
        }
        grouped.forEach { (day, dayItems) ->
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
            if (transfers.size >= 3 && day !in expandedTransferDays) {
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
                items(dayItems, key = { it.tx.id }) { item ->
                    FeedRow(
                        item = item,
                        selected = item.tx.id in selectedIds,
                        onClick = { if (selectionMode) toggleSelection(item) else details = item },
                        onLongClick = { toggleSelection(item) },
                    )
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

    if (showBatchStatus) TransactionStatusSheet(
        current = selectedItems.map { it.tx.status }.distinct().singleOrNull(),
        onDismiss = { showBatchStatus = false },
        onSelect = { status ->
            viewModel.updateStatuses(selectedItems, status)
            showBatchStatus = false
            selectedIds = emptySet()
        },
    )

    if (showBatchDelete) AlertDialog(
        onDismissRequest = { showBatchDelete = false },
        title = { Text(stringResource(R.string.transactions_delete_selected)) },
        text = { Text(stringResource(R.string.transactions_delete_selected_body, selectedItems.size)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.deleteItems(selectedItems)
                showBatchDelete = false
                selectedIds = emptySet()
            }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showBatchDelete = false }) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showAdd) {
        AddTransactionSheet(
            accounts = accounts,
            categories = categoriesByUsage,
            people = people,
            onDismiss = { showAdd = false },
            onSave = { manual ->
                viewModel.addManual(manual)
                showAdd = false
            },
            onSaveDebt = { debt -> viewModel.addDebt(debt); showAdd = false },
            onCreateCategory = viewModel::createCategory,
            onCreateCashCurrency = viewModel::createCashCurrency,
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
        val suggester by viewModel.categorySuggester.collectAsState()
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
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.transaction_delete)) },
            text = { Text(stringResource(if (item.tx.transferGroupId != null) R.string.transaction_delete_transfer_body else R.string.transaction_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteManual(item); deleteFor = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.action_cancel)) } },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsSheet(
    item: FeedItem,
    onDismiss: () -> Unit,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
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
            onEdit = onEdit,
            onDebt = onDebt,
            onClearDebt = onClearDebt,
            onSplit = onSplit,
            onClearSplit = onClearSplit,
            onChangeStatus = onChangeStatus,
        )
    }
}

@Composable
private fun TransactionDetailsContent(
    item: FeedItem,
    modifier: Modifier = Modifier,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
) {
    val tx = item.tx
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
    val hasQuickActions = onEdit != null || onDebt != null || onClearDebt != null ||
        onSplit != null || onClearSplit != null

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
                    Text(
                        formatMinor(tx.amountMinor, tx.currency),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    )
                    if (item.destinationAmountMinor != null && item.destinationCurrency != null) {
                        Text(
                            "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        dateAndAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null) {
                    Box {
                        WhfinIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.transaction_actions),
                            onClick = { actionMenuExpanded = true },
                            outlined = false,
                        )
                        DropdownMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
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
                                    onDelete()
                                },
                            )
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
                    value = tx.status.label(),
                    onClick = onChangeStatus,
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
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                WhfinSectionLabel(stringResource(R.string.transaction_actions))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEdit != null) item {
                        DetailQuickAction(Icons.Default.Edit, stringResource(R.string.action_edit), onEdit)
                    }
                    if (onClearDebt != null) item {
                        DetailQuickAction(Icons.Default.PersonAdd, stringResource(R.string.debt_clear), onClearDebt)
                    } else if (onDebt != null) item {
                        DetailQuickAction(Icons.Default.PersonAdd, stringResource(R.string.debt_action_short), onDebt)
                    }
                    if (onClearSplit != null) item {
                        DetailQuickAction(Icons.Default.CallSplit, stringResource(R.string.split_clear), onClearSplit)
                    } else if (onSplit != null) item {
                        DetailQuickAction(Icons.Default.CallSplit, stringResource(R.string.split_action_short), onSplit)
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
    }
}

@Composable
private fun DetailQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.id }) { person ->
                    FilterChip(
                        selected = selectedPersonId == person.id,
                        onClick = { selectedPersonId = person.id },
                        label = { Text(person.name) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == SplitMode.HALF, { mode = SplitMode.HALF }, { Text(stringResource(R.string.split_half)) })
                FilterChip(mode == SplitMode.FULL, { mode = SplitMode.FULL }, { Text(stringResource(R.string.split_full)) })
                FilterChip(mode == SplitMode.CUSTOM, { mode = SplitMode.CUSTOM }, { Text(stringResource(R.string.split_custom)) })
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

private enum class FeedFilter { ALL, EXPENSES, INCOME, TRANSFERS }
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
            FeedFilter.ALL -> categories
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
                                FeedFilter.ALL -> draftCategories
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
                                FeedSort.AMOUNT -> Icons.Default.TrendingUp
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
private fun MonthlyFlowSummary(income: Long, expenses: Long, onClick: () -> Unit) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryValue(
                    Modifier.weight(1f),
                    stringResource(R.string.summary_income), income,
                    Icons.Default.ArrowDownward, MaterialTheme.colorScheme.primary,
                )
                SummaryValue(
                    Modifier.weight(1f),
                    stringResource(R.string.summary_expenses), expenses,
                    Icons.Default.ArrowUpward, MaterialTheme.colorScheme.tertiary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SummaryValue(
    modifier: Modifier,
    label: String,
    value: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMinor(value, "GEL"), style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
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
    val label = when (day) {
        LocalDate.now() -> stringResource(R.string.date_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val directGel = expensesByCurrency["GEL"] ?: 0L
    val totalGel = directGel + gelFromConversions
    val hasBreakdown = expensesByCurrency.keys.any { it != "GEL" } || gelFromConversions > 0L
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                Text(label.uppercase(), Modifier.padding(start = 9.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.then(if (hasBreakdown) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onToggle) else Modifier)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(formatMinor(totalGel, "GEL"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                if (hasBreakdown) Icon(Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded && hasBreakdown) Text(
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
internal fun FeedRow(
    item: FeedItem,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
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
            Surface(shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else
                    item.category?.let { Color(it.color).copy(alpha = .12f) } ?: MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else
                    item.category?.let { Color(it.color).copy(alpha = .28f) } ?: MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else
                            CategoryIcons.resolve(item.category?.icon, isTransfer = tx.isTransfer),
                        contentDescription = if (selected) stringResource(R.string.transactions_selected) else null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else
                            item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
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
                Text(
                    formatMinor(
                        if (isTransfer) kotlin.math.abs(tx.amountMinor) else tx.amountMinor,
                        tx.currency,
                        withSign = !isTransfer,
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    fontWeight = if (isTransfer) FontWeight.Normal else FontWeight.SemiBold,
                    color = amountColor,
                )
                if (isTransfer && item.destinationAmountMinor != null && item.destinationCurrency != null &&
                    item.destinationCurrency != tx.currency) {
                    Text(
                        "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                        Text(stringResource(R.string.status_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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

@Preview(name = "Feed populated", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Feed dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Feed font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
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
                WhfinContextHeader(stringResource(R.string.balance_total), formatMinor(559_417, "GEL")) {
                    WhfinIconButton(Icons.Default.Search, "Search", {}, outlined = false)
                    WhfinIconButton(Icons.Default.FilterAlt, "Filter", {}, outlined = false)
                }
                MonthlyFlowSummary(730_800, 109_127, {})
                SmsOnboardingCard({}, {})
                DayHeader(LocalDate.now(), mapOf("USD" to 2_360), 6_346, true, {})
                FeedRow(item, {}, selected = true)
            }
        }
    }
}
