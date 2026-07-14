package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinPrimaryIconAction
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
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
    showSmsOnboarding: Boolean,
    onEnableSms: () -> Unit,
    onDismissSmsOnboarding: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
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
    var showAdd by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<FeedItem?>(null) }
    var expandedTransferDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var expandedExpenseDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FeedFilter.ALL) }
    var sort by remember { mutableStateOf(FeedSort.NEWEST) }
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
        matchesType && (search.isBlank() || haystack.contains(search.trim(), ignoreCase = true))
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            WhfinPrimaryIconAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_transaction),
                onClick = { showAdd = true },
            )
        },
    ) { contentPadding ->
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 96.dp),
        ) {
        item(key = "context-header") {
            WhfinContextHeader(
                label = stringResource(R.string.balance_total),
                value = formatMinor(balance, "GEL"),
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
                    icon = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.feed_filter_sort),
                    onClick = { showFilterSheet = true },
                    outlined = false,
                    selected = filter != FeedFilter.ALL || sort != FeedSort.NEWEST,
                )
                WhfinIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = onOpenSettings,
                    outlined = false,
                )
            }
        }
        item(key = "summary") { MonthlyFlowSummary(income, expenses, onOpenAnalytics) }
        item(key = "feed-tools") {
            FeedTools(
                search = search,
                onSearchChange = { search = it },
                searchVisible = showSearch,
                filter = filter,
                onFilterChange = { filter = it },
            )
        }
        if (showSmsOnboarding) item(key = "sms-onboarding") {
            SmsOnboardingCard(onEnableSms, onDismissSmsOnboarding)
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
                items(regular, key = { it.tx.id }) { item -> FeedRow(item, onClick = { details = item }) }
            } else {
                items(dayItems, key = { it.tx.id }) { item -> FeedRow(item, onClick = { details = item }) }
            }
        }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
    }

    if (showFilterSheet) FeedFilterSheet(
        filter = filter,
        sort = sort,
        onFilter = { filter = it },
        onSort = { sort = it },
        onDismiss = { showFilterSheet = false },
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
            onDebt = if (item.tx.amountMinor < 0) {{ details = null; debtFor = item }} else null,
            onClearDebt = if (item.isDebt) {{ viewModel.clearAllocations(item); details = null }} else null,
        )
    }

    categoryFor?.let { item ->
        CategoryPickerSheet(
            item = item,
            categories = categories,
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
) {
    val tx = item.tx
    val isTransfer = tx.isTransfer || tx.transferGroupId != null
    val title = item.transferSummary
        ?: item.merchant?.displayName
        ?: tx.rawCounterparty
        ?: tx.note
        ?: stringResource(R.string.feed_no_description)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                formatMinor(kotlin.math.abs(tx.amountMinor), tx.currency),
                style = MaterialTheme.typography.displaySmall,
            )
            if (item.destinationAmountMinor != null && item.destinationCurrency != null) {
                Text(
                    "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            WhfinLedgerGroup(tonal = true) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    DetailRow(stringResource(R.string.tx_detail_date), item.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)))
                    DetailRow(stringResource(R.string.tx_detail_account), item.account?.name ?: "—")
                    item.account?.iban?.let { DetailRow("IBAN", it) }
                    DetailRow(stringResource(R.string.tx_detail_source), tx.source.name.lowercase().replaceFirstChar(Char::titlecase))
                    DetailRow(stringResource(R.string.tx_detail_status), tx.status.name.lowercase().replaceFirstChar(Char::titlecase))
                    if (!isTransfer) DetailRow(
                        stringResource(R.string.tx_detail_category),
                        item.category?.name ?: stringResource(R.string.feed_uncategorized),
                    )
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
                    if (item.isDebt) DetailRow(
                        stringResource(R.string.debt_label),
                        stringResource(R.string.debt_person_owes, item.debtPersonName ?: "—", formatMinor(item.debtMinor ?: 0L, tx.currency)),
                    )
                }
            }
            Text(stringResource(R.string.transaction_actions), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isTransfer && onChangeCategory != null) DetailAction(
                icon = Icons.Default.Category,
                title = stringResource(R.string.tx_detail_category),
                value = item.category?.name ?: stringResource(R.string.feed_uncategorized),
                onClick = onChangeCategory,
            )
            if (onDebt != null) DetailAction(
                icon = Icons.Default.PersonAdd,
                title = stringResource(R.string.debt_mark),
                value = item.debtPersonName ?: stringResource(R.string.debt_not_set),
                onClick = onDebt,
            )
            if (onEdit != null) DetailAction(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.transaction_edit),
                value = stringResource(R.string.transaction_manual_hint),
                onClick = onEdit,
            )
            if (onDelete != null) WhfinButton(
                stringResource(R.string.transaction_delete), onDelete, Modifier.fillMaxWidth(),
                style = WhfinActionStyle.Destructive, leadingIcon = Icons.Default.DeleteOutline,
            )
            if (onClearDebt != null) WhfinButton(
                stringResource(R.string.debt_clear), onClearDebt, Modifier.fillMaxWidth(),
                style = WhfinActionStyle.Secondary,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.58f))
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    WhfinLedgerRow(
        title = title,
        supportingText = value,
        icon = icon,
        trailing = { Icon(Icons.Default.ExpandMore, null, modifier = Modifier.graphicsLayer(rotationZ = -90f)) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtPersonSheet(
    item: FeedItem,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSelect: (PersonEntity) -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.debt_new_person)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            if (name.isNotBlank()) WhfinButton(
                stringResource(R.string.debt_add_and_select), { onAdd(name) }, Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class FeedFilter { ALL, EXPENSES, INCOME, TRANSFERS }
private enum class FeedSort { NEWEST, OLDEST, AMOUNT }

@Composable
private fun FeedTools(
    search: String,
    onSearchChange: (String) -> Unit,
    searchVisible: Boolean,
    filter: FeedFilter,
    onFilterChange: (FeedFilter) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (searchVisible) {
            val focusRequester = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.feed_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                FeedFilter.ALL to R.string.feed_filter_all,
                FeedFilter.EXPENSES to R.string.feed_filter_expenses,
                FeedFilter.INCOME to R.string.feed_filter_income,
                FeedFilter.TRANSFERS to R.string.feed_filter_transfers,
            ).forEach { (value, label) ->
                val selected = filter == value
                WhfinFilterPill(stringResource(label), selected, { onFilterChange(value) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedFilterSheet(
    filter: FeedFilter,
    sort: FeedSort,
    onFilter: (FeedFilter) -> Unit,
    onSort: (FeedSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.feed_filter_sort), style = MaterialTheme.typography.headlineSmall)
            WhfinSectionLabel(stringResource(R.string.feed_transaction_type))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FeedFilter.ALL to R.string.feed_filter_all,
                    FeedFilter.EXPENSES to R.string.feed_filter_expenses,
                    FeedFilter.INCOME to R.string.feed_filter_income,
                    FeedFilter.TRANSFERS to R.string.feed_filter_transfers,
                ).forEach { (value, label) ->
                    WhfinFilterPill(stringResource(label), filter == value, { onFilter(value) })
                }
            }
            WhfinSectionLabel(stringResource(R.string.feed_sort_by), Modifier.padding(top = 4.dp))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                listOf(
                    FeedSort.NEWEST to R.string.feed_sort_newest,
                    FeedSort.OLDEST to R.string.feed_sort_oldest,
                    FeedSort.AMOUNT to R.string.feed_sort_amount,
                ).forEachIndexed { index, (value, label) ->
                    WhfinLedgerRow(
                        title = stringResource(label),
                        trailing = if (sort == value) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = { onSort(value) },
                        divider = index < 2,
                    )
                }
            }
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
private fun CategoryPickerSheet(
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
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
                OutlinedTextField(name, { name = it.take(32) }, label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
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
internal fun FeedRow(item: FeedItem, onClick: () -> Unit) {
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
    val subtitle = if (sourceHint != null) "$categoryName · $sourceHint" else categoryName
    val amountColor = when {
        tx.isTransfer || tx.transferGroupId != null -> MaterialTheme.colorScheme.onSurfaceVariant
        tx.amountMinor > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.small,
                color = item.category?.let { Color(it.color).copy(alpha = .12f) } ?: MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, item.category?.let { Color(it.color).copy(alpha = .28f) } ?: MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        CategoryIcons.resolve(item.category?.icon, isTransfer = tx.isTransfer), null,
                        tint = item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                    WhfinIconButton(Icons.Default.Tune, "Filter", {}, outlined = false)
                    WhfinIconButton(Icons.Default.Settings, "Settings", {}, outlined = false)
                }
                MonthlyFlowSummary(730_800, 109_127, {})
                FeedTools("", {}, false, FeedFilter.ALL, {})
                SmsOnboardingCard({}, {})
                DayHeader(LocalDate.now(), mapOf("USD" to 2_360), 6_346, true, {})
                FeedRow(item, {})
            }
        }
    }
}
