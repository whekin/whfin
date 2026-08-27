package dev.whekin.whfin.ui.savings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.backup.LedgerRestoreState
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.savings.SavingsPlanDraft
import dev.whekin.whfin.data.savings.SavingsPlanRepository
import dev.whekin.whfin.data.savings.calculateSavingsAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SavingsMonthUi(
    val month: YearMonth,
    val reserveBalanceMinor: Long,
    val paceMinor: Long,
    val targetMinor: Long?,
)

data class SavingsScreenData(
    val currency: String,
    val months: List<SavingsMonthUi>,
    val currentPlan: SavingsPlanEntity?,
    val currentReserveMinor: Long,
    val currentPaceMinor: Long,
    val rollingThreeMonthMinor: Long,
    val monthsOnPace: Int,
    val evaluatedMonths: Int,
    val hasReserve: Boolean,
    val availableCurrencies: List<String> = listOf(currency),
    val rollingMonthsIncluded: Int = 3,
    val bankApps: List<SupportedBankApp> = emptyList(),
)

internal fun buildSavingsScreenData(
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    plans: List<SavingsPlanEntity>,
    today: LocalDate,
    zone: ZoneId,
    currency: String = "GEL",
): SavingsScreenData {
    val currentMonth = YearMonth.from(today)
    val reserveIds = accounts.asSequence()
        .filter { it.type != AccountType.CRYPTO }
        .filter { it.fundRole == FundRole.RESERVE && it.currency == currency }
        .map(AccountEntity::id)
        .toSet()
    val earliest = transactions.asSequence()
        .filterNot(TransactionEntity::isVoided)
        .filter { it.accountId in reserveIds && it.currency == currency }
        .minOfOrNull { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }
    // Before the first ledger evidence the balance is unknown, not zero. Never invent a year of
    // empty history for someone who has only just imported their first statement.
    val chartStart = minOf(earliest ?: currentMonth, currentMonth)
    val analytics = calculateSavingsAnalytics(
        accounts = accounts,
        transactions = transactions,
        currency = currency,
        fromMonth = chartStart,
        throughMonth = currentMonth,
        zoneId = zone,
    )
    val currencyPlans = plans.filter { it.currency == currency }.sortedBy { it.startedOn }
    val months = analytics.months.map { point ->
        SavingsMonthUi(
            month = point.month,
            reserveBalanceMinor = point.reserveBalanceMinor,
            paceMinor = point.paceMinor,
            targetMinor = currencyPlans.planFor(point.month)?.monthlyTargetMinor,
        )
    }
    val currentPlan = currencyPlans.planFor(currentMonth)
    val completeEvaluated = months
        .filter { it.month < currentMonth }
        .takeLast(12)
        .filter { it.targetMinor != null }
    val rollingComplete = months.filter { it.month < currentMonth }.takeLast(3)
    return SavingsScreenData(
        currency = currency,
        months = months,
        currentPlan = currentPlan,
        currentReserveMinor = analytics.endingReserveBalanceMinor,
        currentPaceMinor = months.lastOrNull()?.paceMinor ?: 0L,
        rollingThreeMonthMinor = rollingComplete.takeIf { it.isNotEmpty() }
            ?.let { points -> points.sumOf(SavingsMonthUi::paceMinor) / points.size }
            ?: 0L,
        monthsOnPace = completeEvaluated.count { it.paceMinor >= requireNotNull(it.targetMinor) },
        evaluatedMonths = completeEvaluated.size,
        hasReserve = reserveIds.isNotEmpty(),
        availableCurrencies = accounts.filter { it.type != AccountType.CRYPTO && it.fundRole == FundRole.RESERVE }
            .map { it.currency }.distinct().sortedWith(compareBy<String> { it != "GEL" }.thenBy { it }),
        rollingMonthsIncluded = rollingComplete.size,
    )
}

private fun List<SavingsPlanEntity>.planFor(month: YearMonth): SavingsPlanEntity? {
    val firstDay = month.atDay(1).toEpochDay()
    return lastOrNull { plan ->
        plan.startedOn <= firstDay && (plan.endedOn == null || plan.endedOn >= firstDay)
    }
}

class SavingsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val repository = SavingsPlanRepository(db)
    private val zone = ZoneId.systemDefault()
    private val selectedCurrency = MutableStateFlow<String?>(null)
    private val accountsAndGroups = combine(
        db.accountDao().observeActive(), db.financialGroupDao().observeActive(), ::Pair,
    )

    private data class Snapshot(
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val plans: List<SavingsPlanEntity>,
        val groups: List<FinancialGroupEntity>,
        val currency: String?,
    )

    val state: StateFlow<SavingsScreenData?> = combine(
        accountsAndGroups,
        db.transactionDao().observeAllActive(),
        db.savingsPlanDao().observeAll(),
        LedgerRestoreState.active,
        selectedCurrency,
    ) { accountState, transactions, plans, restoring, currency ->
        if (restoring) null else Snapshot(accountState.first, transactions, plans, accountState.second, currency)
    }.map { snapshot ->
        snapshot ?: return@map null
        withContext(Dispatchers.Default) {
            val currencies = snapshot.accounts.filter {
                it.type != AccountType.CRYPTO && it.fundRole == FundRole.RESERVE
            }.map { it.currency }.distinct()
            val currency = snapshot.currency?.takeIf { it in currencies }
                ?: "GEL".takeIf { it in currencies } ?: currencies.firstOrNull() ?: "GEL"
            buildSavingsScreenData(
                accounts = snapshot.accounts,
                transactions = snapshot.transactions,
                plans = snapshot.plans,
                today = LocalDate.now(zone),
                zone = zone,
                currency = currency,
            ).copy(bankApps = bankAppsForReserve(snapshot.accounts, snapshot.groups, currency) {
                getApplication<Application>().packageManager.getLaunchIntentForPackage(it) != null
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun savePlan(monthlyTargetMinor: Long, goalMinor: Long?, goalBy: LocalDate?) {
        val currency = state.value?.currency ?: return
        viewModelScope.launch {
            repository.set(
                SavingsPlanDraft(
                    currency = currency,
                    monthlyTargetMinor = monthlyTargetMinor,
                    goalMinor = goalMinor,
                    goalBy = goalBy,
                ),
            )
        }
    }

    fun clearPlan() {
        val currency = state.value?.currency ?: return
        viewModelScope.launch { repository.clear(currency) }
    }

    fun selectCurrency(currency: String) { selectedCurrency.value = currency }
}
