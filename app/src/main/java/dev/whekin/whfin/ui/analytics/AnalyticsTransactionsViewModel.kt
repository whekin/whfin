package dev.whekin.whfin.ui.analytics

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.feed.FeedItem
import dev.whekin.whfin.ui.feed.applyDebtAllocations
import dev.whekin.whfin.ui.feed.buildBaseFeedItems
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@Immutable
internal data class AnalyticsTransactionsRequest(
    val month: YearMonth,
    val categoryFilterEnabled: Boolean,
    val categoryId: Long?,
    val filterName: String,
    val expectedExpenseMinor: Long,
)

internal sealed interface AnalyticsTransactionsUiState {
    data object Loading : AnalyticsTransactionsUiState
    data class Empty(val request: AnalyticsTransactionsRequest) : AnalyticsTransactionsUiState
    data class Error(val request: AnalyticsTransactionsRequest) : AnalyticsTransactionsUiState
    data class Content(
        val request: AnalyticsTransactionsRequest,
        val items: List<FeedItem>,
    ) : AnalyticsTransactionsUiState
}

private data class HistoryBase(
    val items: List<FeedItem>,
    val categories: List<CategoryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyticsTransactionsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zoneId = ZoneId.systemDefault()
    private val request = MutableStateFlow<AnalyticsTransactionsRequest?>(null)

    private val cardHints = combine(
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { instruments, links ->
        val byId = instruments.associateBy { it.id }
        links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
            accountLinks.mapNotNull { byId[it.instrumentId]?.last4 }
        }
    }

    val uiState = request.filterNotNull().flatMapLatest { value ->
        // One day on each side keeps an automatic FX conversion linked to a purchase
        // that lands on the first or last day of the requested month.
        val start = value.month.atDay(1).minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = value.month.plusMonths(1).atDay(1).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val base = combine(
            db.transactionDao().observeRange(start, end),
            db.merchantDao().observeAll(),
            db.categoryDao().observeAll(),
            db.accountDao().observeActive(),
            cardHints,
        ) { transactions, merchants, categories, accounts, masksByAccount ->
            HistoryBase(
                items = buildBaseFeedItems(
                    transactions,
                    merchants,
                    categories,
                    accounts,
                    masksByAccount,
                    zoneId,
                ),
                categories = categories,
            )
        }
        combine(
            base,
            db.transactionAllocationDao().observeAll(),
            db.personDao().observeActive(),
        ) { history, allocations, people ->
            val items = applyDebtAllocations(history.items, allocations, people)
            val filtered = filterAnalyticsTransactions(
                items = items,
                allocations = allocations,
                categories = history.categories,
                request = value,
            )
            if (filtered.isEmpty()) AnalyticsTransactionsUiState.Empty(value)
            else AnalyticsTransactionsUiState.Content(value, filtered)
        }
    }.catch {
        request.value?.let { emit(AnalyticsTransactionsUiState.Error(it)) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AnalyticsTransactionsUiState.Loading,
    )

    fun bind(value: AnalyticsTransactionsRequest) {
        request.value = value
    }
}

internal fun filterAnalyticsTransactions(
    items: List<FeedItem>,
    allocations: List<TransactionAllocationEntity>,
    categories: List<CategoryEntity>,
    request: AnalyticsTransactionsRequest,
): List<FeedItem> {
    val categoryById = categories.associateBy { it.id }
    val allocationsByTransaction = allocations.groupBy { it.transactionId }
    return items.asSequence()
        .filter { item -> YearMonth.from(item.day) == request.month }
        .filter { item ->
            val tx = item.tx
            !tx.isTransfer && tx.transferGroupId == null && tx.amountMinor < 0L &&
                tx.source != TxSource.ADJUSTMENT &&
                (tx.currency == "GEL" || item.fundedByConversionCurrency == "GEL")
        }
        .filter { item ->
            val transactionAllocations = allocationsByTransaction[item.tx.id].orEmpty()
            val included = transactionAllocations.filterNot {
                it.purpose == AllocationPurpose.LOAN || it.purpose == AllocationPurpose.REPAYMENT
            }
            val categoryIds = when {
                transactionAllocations.isNotEmpty() && included.isEmpty() -> emptyList()
                included.isNotEmpty() -> included.map { it.categoryId ?: item.tx.categoryId }
                else -> listOf(item.tx.categoryId)
            }.filter { categoryId -> categoryId?.let(categoryById::get)?.isSystem != true }
            categoryIds.isNotEmpty() && (!request.categoryFilterEnabled || request.categoryId in categoryIds)
        }
        .sortedByDescending { it.tx.occurredAt }
        .toList()
}
