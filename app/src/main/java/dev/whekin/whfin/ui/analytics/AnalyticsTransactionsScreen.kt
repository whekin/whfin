package dev.whekin.whfin.ui.analytics

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.feed.DayHeader
import dev.whekin.whfin.ui.feed.FeedItem
import dev.whekin.whfin.ui.feed.FeedRow
import dev.whekin.whfin.ui.feed.TransactionDetailsSheet
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnalyticsTransactionsScreen(
    request: AnalyticsTransactionsRequest,
    onBack: () -> Unit,
    viewModel: AnalyticsTransactionsViewModel = viewModel(),
) {
    LaunchedEffect(request) { viewModel.bind(request) }
    val state by viewModel.uiState.collectAsState()
    when (val value = state) {
        AnalyticsTransactionsUiState.Loading -> AnalyticsTransactionsState(
            request = request,
            paneState = WhfinPaneState.Loading,
            title = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_loading),
            body = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_loading_body),
            onBack = onBack,
        )
        is AnalyticsTransactionsUiState.Empty -> AnalyticsTransactionsState(
            request = value.request,
            paneState = WhfinPaneState.Empty,
            title = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_empty_title),
            body = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_empty_body),
            onBack = onBack,
        )
        is AnalyticsTransactionsUiState.Error -> AnalyticsTransactionsState(
            request = value.request,
            paneState = WhfinPaneState.Error,
            title = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_error_title),
            body = androidx.compose.ui.res.stringResource(R.string.analytics_transactions_error_body),
            onBack = onBack,
        )
        is AnalyticsTransactionsUiState.Content -> AnalyticsTransactionsContent(
            request = value.request,
            items = value.items,
            onBack = onBack,
        )
    }
}

@Composable
private fun AnalyticsTransactionsState(
    request: AnalyticsTransactionsRequest,
    paneState: WhfinPaneState,
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "transactions-header") { AnalyticsTransactionsHeader(onBack) }
            item(key = "transactions-scope") { AnalyticsTransactionsScope(request) }
            item(key = "transactions-state") {
                WhfinStatePane(paneState, title, body, Modifier.fillMaxWidth())
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun AnalyticsTransactionsContent(
    request: AnalyticsTransactionsRequest,
    items: List<FeedItem>,
    onBack: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var expandedDays by remember { mutableStateOf(emptySet<LocalDate>()) }
    val grouped = items.groupBy { it.day }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navigationBottom + 24.dp),
        ) {
            item(key = "transactions-header") { AnalyticsTransactionsHeader(onBack) }
            item(key = "transactions-scope") { AnalyticsTransactionsScope(request) }
            grouped.forEach { (day, dayItems) ->
                item(key = "transactions-day-$day") {
                    val expensesByCurrency = dayItems.groupBy { it.tx.currency }
                        .mapValues { (_, values) -> -values.sumOf { it.tx.amountMinor.coerceAtMost(0L) } }
                        .filterValues { it > 0L }
                    val gelFromConversions = dayItems
                        .filter { it.tx.currency != "GEL" && it.fundedByConversionCurrency == "GEL" }
                        .sumOf { it.fundedByConversionMinor ?: 0L }
                    DayHeader(
                        day = day,
                        expensesByCurrency = expensesByCurrency,
                        gelFromConversions = gelFromConversions,
                        expanded = day in expandedDays,
                        onToggle = {
                            expandedDays = if (day in expandedDays) expandedDays - day else expandedDays + day
                        },
                    )
                }
                items(dayItems, key = { "analytics-transaction-${it.tx.id}" }) { item ->
                    FeedRow(item, onClick = { details = item })
                }
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
    details?.let { item ->
        TransactionDetailsSheet(
            item = item,
            onDismiss = { details = null },
            onChangeCategory = null,
            onDelete = null,
            onEdit = null,
            onDebt = null,
            onClearDebt = null,
        )
    }
}

@Composable
private fun AnalyticsTransactionsHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.action_back),
                    onClick = onBack,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.analytics_transactions_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AnalyticsTransactionsScope(request: AnalyticsTransactionsRequest) {
    val filterName = if (request.categoryFilterEnabled) {
        request.filterName
    } else {
        androidx.compose.ui.res.stringResource(R.string.analytics_all_expenses)
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhfinSectionHeader(title = monthTitle(request.month), supportingText = filterName)
        WhfinSectionLabel(androidx.compose.ui.res.stringResource(R.string.analytics_transactions_total))
        Text(
            formatMinor(request.expectedExpenseMinor, "GEL"),
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.tertiary,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val previewRequest = AnalyticsTransactionsRequest(
    month = java.time.YearMonth.of(2026, 7),
    categoryFilterEnabled = true,
    categoryId = 1,
    filterName = "Groceries",
    expectedExpenseMinor = 38_200,
)

private val previewItems = run {
    val account = AccountEntity(1, "Credo GEL •0001", AccountType.BANK, currency = "GEL")
    val category = CategoryEntity(
        id = 1,
        name = "Groceries",
        kind = CategoryKind.EXPENSE,
        icon = "ShoppingCart",
        color = 0xff4f725f.toInt(),
    )
    listOf(
        FeedItem(
            tx = TransactionEntity(
                id = 1,
                accountId = 1,
                amountMinor = -12_480,
                currency = "GEL",
                occurredAt = LocalDate.of(2026, 7, 12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                merchantId = 1,
                categoryId = 1,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
            merchant = MerchantEntity(1, "carrefour", "Carrefour", 1),
            category = category,
            account = account,
            cardHint = "••0001",
            day = LocalDate.of(2026, 7, 12),
        ),
        FeedItem(
            tx = TransactionEntity(
                id = 2,
                accountId = 1,
                amountMinor = -25_720,
                currency = "GEL",
                occurredAt = LocalDate.of(2026, 7, 5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                merchantId = 2,
                categoryId = 1,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
            merchant = MerchantEntity(2, "agrohub", "Agrohub", 1),
            category = category,
            account = account,
            cardHint = "••0001",
            day = LocalDate.of(2026, 7, 5),
        ),
    )
}

@Preview(name = "Analytics transactions light", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Analytics transactions dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Analytics transactions font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Preview(name = "Analytics transactions compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsTransactionsPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsTransactionsContent(previewRequest, previewItems, {})
        }
    }
}

@Preview(name = "Analytics transactions empty", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun AnalyticsTransactionsEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsTransactionsState(
                previewRequest,
                WhfinPaneState.Empty,
                "No matching expenses",
                "Try another month or category.",
                {},
            )
        }
    }
}
