package dev.whekin.whfin.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val categoryRangeMonths: Int,
    val trendFilter: AnalyticsTrendFilter,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zoneId = ZoneId.systemDefault()
    private val selectedMonth = MutableStateFlow(YearMonth.now(zoneId))
    private val categoryRangeMonths = MutableStateFlow(1)
    private val trendFilter = MutableStateFlow<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)

    private val transactions: Flow<List<TransactionEntity>> = selectedMonth.flatMapLatest { month ->
        val yearStart = YearMonth.of(month.year, 1)
        val rangeStart = minOf(yearStart, month.minusMonths(11))
        val rangeEnd = YearMonth.of(month.year + 1, 1)
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

    private val controls = combine(selectedMonth, categoryRangeMonths, trendFilter) { month, range, filter ->
        AnalyticsControls(month, range, filter)
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
        )
    }.map<AnalyticsData, AnalyticsUiState> { data ->
        if (data.hasAnyTransactions) AnalyticsUiState.Content(data)
        else AnalyticsUiState.Empty(data.selectedMonth)
    }.catch {
        emit(AnalyticsUiState.Error(selectedMonth.value))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState.Loading)

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (next <= YearMonth.now(zoneId)) selectedMonth.value = next
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
