package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.IncomeExpectation
import dev.whekin.whfin.data.income.IncomeExpectations
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class IncomeSourcesState(
    val expectations: List<IncomeExpectation>,
    val ended: List<IncomeSourceEntity>,
    val accounts: List<AccountEntity>,
    val month: YearMonth,
)

class IncomeSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zone: ZoneId = ZoneId.systemDefault()

    private val today = LocalDate.now(zone)
    private val month = YearMonth.from(today)
    private val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val state: StateFlow<IncomeSourcesState?> = combine(
        db.incomeSourceDao().observeAll(),
        db.transactionDao().observeIncomeBetween(monthStart, monthEnd),
        db.accountDao().observeActive(),
    ) { sources, transactions, accounts ->
        IncomeSourcesState(
            expectations = IncomeExpectations.of(
                sources.filter { it.endedOn == null },
                transactions,
                month,
                today,
                zone,
            ),
            ended = sources.filter { it.endedOn != null },
            accounts = accounts,
            month = month,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(
        existing: IncomeSourceEntity?,
        label: String,
        amountMinor: Long,
        currency: String,
        accountId: Long?,
        dayFrom: Int,
        dayTo: Int,
    ) {
        val clean = label.trim().ifEmpty { return }
        if (amountMinor <= 0) return
        val from = dayFrom.coerceIn(1, 28)
        viewModelScope.launch {
            db.incomeSourceDao().upsert(
                IncomeSourceEntity(
                    id = existing?.id ?: 0,
                    label = clean,
                    amountMinor = amountMinor,
                    currency = currency.uppercase(),
                    accountId = accountId,
                    expectedDayFrom = from,
                    expectedDayTo = dayTo.coerceIn(from, 28),
                    // A declaration describes the present onwards. Editing one keeps the day it
                    // started on, so correcting a typo does not silently rewrite which months it
                    // claims to describe.
                    startedOn = existing?.startedOn ?: LocalDate.now(zone).withDayOfMonth(1).toEpochDay(),
                    endedOn = existing?.endedOn,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Closes an era instead of deleting it. The months it described keep their explanation, and the
     * next declaration starts where this one stopped.
     */
    fun end(source: IncomeSourceEntity) {
        viewModelScope.launch {
            db.incomeSourceDao().upsert(
                source.copy(endedOn = LocalDate.now(zone).toEpochDay()),
            )
        }
    }

    fun delete(source: IncomeSourceEntity) {
        viewModelScope.launch { db.incomeSourceDao().delete(source.id) }
    }
}
