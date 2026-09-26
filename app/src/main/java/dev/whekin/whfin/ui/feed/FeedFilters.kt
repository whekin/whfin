package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.data.db.CategoryKind
import androidx.compose.ui.text.style.TextOverflow
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame

internal enum class FeedSort { NEWEST, OLDEST, AMOUNT }

@Composable
internal fun FeedSearch(
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
internal fun FeedFilterSheet(
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
            FeedFilter.WAITING_BANK, FeedFilter.NEEDS_REVIEW, FeedFilter.ALL -> categories
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
                    FeedFilter.NEEDS_REVIEW to R.string.home_needs_attention,
                    FeedFilter.WAITING_BANK to R.string.feed_waiting_bank,
                )
                WhfinChoiceRail(
                    // The sheet may open on a filter the app applied rather than the reader, and a
                    // rail scrolled past it would answer "what is on?" with options that all read off.
                    revealIndex = filterOptions.indexOfFirst { it.first == draftFilter },
                ) {
                    items(filterOptions, key = { it.first.name }) { (value, label) ->
                    WhfinFilterPill(
                        label = stringResource(label),
                        selected = draftFilter == value,
                        leadingIcon = when (value) {
                            FeedFilter.ALL -> Icons.Default.SelectAll
                            FeedFilter.EXPENSES -> Icons.Default.ArrowUpward
                            FeedFilter.INCOME -> Icons.Default.ArrowDownward
                            FeedFilter.TRANSFERS -> Icons.Default.SwapHoriz
                            FeedFilter.NEEDS_REVIEW -> Icons.Outlined.PendingActions
                            FeedFilter.WAITING_BANK -> Icons.Outlined.History
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
                                FeedFilter.WAITING_BANK, FeedFilter.NEEDS_REVIEW, FeedFilter.ALL -> draftCategories
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
                        // Three suggestions plus More leave enough width for readable names.
                        val slotCount = (maxWidth.value / minSlotWidth.value).toInt().coerceIn(2, 4)
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
                maxLines = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2,
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
