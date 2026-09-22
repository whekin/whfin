package dev.whekin.whfin.ui.analytics

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsSnapshotTest {
    @Test fun monthLabelAndFiguresAlwaysComeFromTheSameCalculation() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<WhfinApp>()
        val db = app.db
        val month = YearMonth.now(LedgerCalendar.zone)
        val account = db.accountDao().insert(AccountEntity(name = "Analytics test", type = AccountType.CASH, currency = "GEL"))
        repeat(4) { offset ->
            db.transactionDao().insert(TransactionEntity(accountId = account, currency = "GEL", amountMinor = -100L * (offset + 1),
                occurredAt = month.minusMonths(offset.toLong()).atDay(1).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli(),
                status = TxStatus.MANUAL, source = TxSource.MANUAL))
        }
        val vm = AnalyticsViewModel(app)
        val seen = CopyOnWriteArrayList<AnalyticsUiModel>()
        val observer = launch(Dispatchers.Unconfined) { vm.uiState.collect { seen += it } }
        suspend fun ready(target: YearMonth) = withTimeout(5000) {
            vm.uiState.first { it.period.month == target && (it.state as? AnalyticsUiState.Content)?.data?.period == it.period }
        }
        try {
            ready(month)
            repeat(3) { index -> vm.selectPreviousPeriod(); ready(month.minusMonths(index + 1L)) }
            val inconsistent = seen.filter { (it.state as? AnalyticsUiState.Content)?.data?.period?.let { dataPeriod -> dataPeriod != it.period } == true }
            assertTrue("Mixed period snapshots: ${inconsistent.map { it.period to (it.state as AnalyticsUiState.Content).data.period }}", inconsistent.isEmpty())
        } finally {
            observer.cancelAndJoin()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
