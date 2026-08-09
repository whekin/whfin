package dev.whekin.whfin.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.rates.NbgHistoricalRateProvider
import dev.whekin.whfin.data.rates.TransactionValuationRepository
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data class Empty(val selectedMonth: YearMonth) : AnalyticsUiState
    data class Error(val selectedMonth: YearMonth) : AnalyticsUiState
    data class Content(val data: AnalyticsData) : AnalyticsUiState
}

private data class AnalyticsInputs(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val allocations: List<TransactionAllocationEntity>,
)

private data class AnalyticsControls(
    val selectedMonth: YearMonth,
    val trendEndMonth: YearMonth,
    val categoryRangeMonths: Int,
    val trendFilter: AnalyticsTrendFilter,
)

private data class AnalyticsPeriod(
    val selectedMonth: YearMonth,
    val trendEndMonth: YearMonth,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zoneId = ZoneId.systemDefault()
    private val initialMonth = YearMonth.now(zoneId)
    private val period = MutableStateFlow(AnalyticsPeriod(initialMonth, initialMonth))
    private val categoryRangeMonths = MutableStateFlow(1)
    private val trendFilter = MutableStateFlow<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)

    private val valuation = TransactionValuationRepository(
        db = db,
        provider = NbgHistoricalRateProvider(),
    )

    init {
        // A foreign-currency row is worth the rate of its own day, and that day is looked up once.
        // Opening statistics is the moment it matters, so the gap is closed here rather than on write,
        // where it would put the network in front of saving an expense.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { valuation.backfill() } }
        }
    }

    private val transactions: Flow<List<TransactionEntity>> = period.flatMapLatest { value ->
        val month = value.selectedMonth
        val yearStart = YearMonth.of(month.year, 1)
        val rangeStart = minOf(yearStart, month.minusMonths(11), value.trendEndMonth.minusMonths(11))
        val rangeEnd = maxOf(YearMonth.of(month.year + 1, 1), value.trendEndMonth.plusMonths(1))
        db.transactionDao().observeRange(
            rangeStart.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            rangeEnd.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    private val inputs = combine(
        transactions,
        db.categoryDao().observeAll(),
        db.transactionAllocationDao().observeAll(),
    ) { transactions, categories, allocations ->
        AnalyticsInputs(transactions, categories, allocations)
    }

    private val controls = combine(period, categoryRangeMonths, trendFilter) { value, range, filter ->
        AnalyticsControls(value.selectedMonth, value.trendEndMonth, range, filter)
    }

    val uiState = combine(inputs, controls) { input, control ->
        calculateAnalytics(
            transactions = input.transactions,
            categories = input.categories,
            allocations = input.allocations,
            selectedMonth = control.selectedMonth,
            categoryRangeMonths = control.categoryRangeMonths,
            trendFilter = control.trendFilter,
            zoneId = zoneId,
            trendEndMonth = control.trendEndMonth,
        )
    }.map<AnalyticsData, AnalyticsUiState> { data ->
        if (data.hasAnyTransactions) AnalyticsUiState.Content(data)
        else AnalyticsUiState.Empty(data.selectedMonth)
    }.catch {
        emit(AnalyticsUiState.Error(period.value.selectedMonth))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState.Loading)

    fun previousMonth() {
        selectMonth(period.value.selectedMonth.minusMonths(1))
    }

    fun nextMonth() {
        selectMonth(period.value.selectedMonth.plusMonths(1))
    }

    fun selectMonth(month: YearMonth) {
        if (month <= YearMonth.now(zoneId)) {
            val current = period.value
            period.value = AnalyticsPeriod(
                selectedMonth = month,
                trendEndMonth = trendWindowEndAfterSelecting(current.trendEndMonth, month),
            )
        }
    }

    fun setCategoryRange(months: Int) {
        if (months in setOf(1, 3, 6, 12)) categoryRangeMonths.value = months
    }

    fun showAllExpensesTrend() {
        trendFilter.value = AnalyticsTrendFilter.All
    }

    fun showCategoryTrend(categoryId: Long?) {
        trendFilter.value = AnalyticsTrendFilter.Category(categoryId)
    }
}
