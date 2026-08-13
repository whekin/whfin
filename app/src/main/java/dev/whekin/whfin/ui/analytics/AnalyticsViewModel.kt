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
import java.time.Instant
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
    data object Empty : AnalyticsUiState
    data object Error : AnalyticsUiState
    data class Content(val data: AnalyticsData) : AnalyticsUiState
}

/**
 * The period and its two arrows are true regardless of what the numbers are doing, so they live
 * outside the state union: a loading or failing screen still says which period it is about, and its
 * arrows still work instead of silently doing nothing.
 */
internal data class AnalyticsUiModel(
    val period: AnalyticsPeriod,
    val canSelectPrevious: Boolean,
    val canSelectNext: Boolean,
    val state: AnalyticsUiState,
)

private data class AnalyticsInputs(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val allocations: List<TransactionAllocationEntity>,
)

private data class AnalyticsControls(
    val period: AnalyticsPeriod,
    val trendEndMonth: YearMonth,
    val trendFilter: AnalyticsTrendFilter,
)

private data class AnalyticsWindow(
    val period: AnalyticsPeriod,
    val trendEndMonth: YearMonth,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zoneId = ZoneId.systemDefault()
    private val initialMonth = YearMonth.now(zoneId)
    private val window = MutableStateFlow(
        AnalyticsWindow(AnalyticsPeriod.month(initialMonth), initialMonth),
    )
    private val trendFilter = MutableStateFlow<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)

    /** Paging back past the first recorded month only produces identical empty screens. */
    private val earliestMonth: Flow<YearMonth?> = db.transactionDao().observeEarliestOccurredAt()
        .map { millis ->
            millis?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zoneId)) }
        }

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

    private val transactions: Flow<List<TransactionEntity>> = window.flatMapLatest { value ->
        // Twelve months before the period covers both the 1/3/6/12 category rail and the
        // year-over-year comparison; the trend window can reach further back on its own.
        val rangeStart = minOf(value.period.start.minusMonths(12), value.trendEndMonth.minusMonths(11))
        val rangeEnd = maxOf(value.period.end, value.trendEndMonth).plusMonths(1)
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

    private val controls = combine(window, trendFilter) { value, filter ->
        AnalyticsControls(value.period, value.trendEndMonth, filter)
    }

    private val calculated: Flow<AnalyticsUiState> = combine(inputs, controls) { input, control ->
        calculateAnalytics(
            transactions = input.transactions,
            categories = input.categories,
            allocations = input.allocations,
            period = control.period,
            trendFilter = control.trendFilter,
            zoneId = zoneId,
            trendEndMonth = control.trendEndMonth,
        )
    }.map<AnalyticsData, AnalyticsUiState> { data ->
        if (data.hasAnyTransactions) AnalyticsUiState.Content(data) else AnalyticsUiState.Empty
    }.catch {
        emit(AnalyticsUiState.Error)
    }

    val uiState = combine(window, earliestMonth, calculated) { value, earliest, state ->
        model(value.period, earliest, state)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        model(window.value.period, null, AnalyticsUiState.Loading),
    )

    private fun model(
        period: AnalyticsPeriod,
        earliest: YearMonth?,
        state: AnalyticsUiState,
    ) = AnalyticsUiModel(
        period = period,
        // Before the first row is known the back arrow stays available: the alternative is a
        // dead control on a screen that has simply not finished loading.
        canSelectPrevious = earliest == null || period.previous().end >= earliest,
        canSelectNext = period.next().start <= YearMonth.now(zoneId),
        state = state,
    )

    fun selectPreviousPeriod() {
        selectPeriod(window.value.period.previous())
    }

    fun selectNextPeriod() {
        selectPeriod(window.value.period.next())
    }

    /** A tap on a bar of the year chart is the drill-down from a year into one of its months. */
    fun selectMonth(month: YearMonth) {
        selectPeriod(AnalyticsPeriod.month(month))
    }

    fun setScale(scale: AnalyticsScale) {
        val current = window.value.period
        if (current.scale == scale) return
        selectPeriod(current.withScale(scale))
    }

    private fun selectPeriod(period: AnalyticsPeriod) {
        val now = YearMonth.now(zoneId)
        if (period.start > now) return
        // The chosen period must be reachable, but its anchor month must not run into the future:
        // switching a past year back to months would otherwise land on a month that cannot exist.
        val anchor = minOf(period.month, now)
        val resolved = period.copy(month = anchor)
        window.value = AnalyticsWindow(
            period = resolved,
            trendEndMonth = trendWindowEndAfterSelecting(window.value.trendEndMonth, resolved.month),
        )
    }

    fun showAllExpensesTrend() {
        trendFilter.value = AnalyticsTrendFilter.All
    }

    fun showCategoryTrend(categoryId: Long?) {
        trendFilter.value = AnalyticsTrendFilter.Category(categoryId)
    }
}
