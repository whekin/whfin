package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.crypto.CryptoBankTransfer
import dev.whekin.whfin.data.income.WeekendRule
import dev.whekin.whfin.data.crypto.CryptoBankTransferRepository
import dev.whekin.whfin.data.crypto.CryptoHistoryRepository
import dev.whekin.whfin.data.crypto.HttpCryptoTransferProvider
import dev.whekin.whfin.data.crypto.cryptoBankCandidates
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.income.IncomeExpectation
import dev.whekin.whfin.data.income.IncomeExpectations
import dev.whekin.whfin.data.income.IncomeSourceRepository
import dev.whekin.whfin.data.preferences.UiPreferences
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IncomeSourcesState(
    val expectations: List<IncomeExpectation>,
    val ended: List<IncomeSourceEntity>,
    val accounts: List<AccountEntity>,
    val month: YearMonth,
    val isReadingChain: Boolean = false,
    val transfers: List<CryptoBankTransfer> = emptyList(),
    val linkedTransfers: List<CryptoBankTransfer> = emptyList(),
    val message: String? = null,
)

class IncomeSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val preferences = UiPreferences(app)
    private val sources = IncomeSourceRepository(db)
    private val bridges = CryptoBankTransferRepository(db)
    private val zone = ZoneId.systemDefault()
    private val reading = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val readFailed = MutableStateFlow(false)

    /** Rows already spoken for by a split or a debt; neither may be swept into a transfer pair. */
    private val allocatedIds = combine(
        db.transactionAllocationDao().observeAll(),
        db.debtDao().observeEvents(),
    ) { allocations, debtEvents ->
        allocations.mapTo(mutableSetOf()) { it.transactionId } + debtEvents.mapNotNull { it.transactionId }
    }

    private val ledger = combine(
        db.incomeSourceDao().observeAll(),
        db.transactionDao().observeAllActive(),
        db.accountDao().observeActive(),
        allocatedIds,
        db.incomeSourceDao().observePayments(),
    ) { declarations, transactions, accounts, allocated, payments ->
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val linked = transactions.filter { it.transferGroupId != null }.groupBy { it.transferGroupId }
            .values.mapNotNull { rows ->
                if (rows.size != 2) return@mapNotNull null
                val out = rows.singleOrNull { it.source == TxSource.CRYPTO && it.amountMinor < 0 }
                    ?: return@mapNotNull null
                val credit = rows.singleOrNull { it.amountMinor > 0 &&
                    accounts.any { account -> account.id == it.accountId && account.type == AccountType.BANK } }
                    ?: return@mapNotNull null
                CryptoBankTransfer(out, credit)
            }.sortedByDescending { it.withdrawal.occurredAt }
        IncomeSourcesState(
            expectations = IncomeExpectations.of(
                declarations.filter { it.endedOn == null }, transactions, month, today, zone, payments,
            ),
            ended = declarations.filter { it.endedOn != null },
            accounts = accounts, month = month,
            transfers = cryptoBankCandidates(transactions, accounts, allocated),
            linkedTransfers = linked,
        )
    }

    val state: StateFlow<IncomeSourcesState?> = combine(ledger, reading, message, readFailed) { ledger, reading, message, failed ->
        ledger.copy(isReadingChain = reading, message = message, expectations = ledger.expectations.map { expectation ->
            expectation.copy(unreadable = failed && ledger.accounts.any {
                it.id == expectation.source.accountId && it.type == AccountType.CRYPTO
            })
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One persistent history feeds Accounts, income expectations, the feed and analytics. */
    fun refreshFromChain() {
        if (reading.value || getApplication<WhfinApp>().isDemoMode) return
        reading.value = true
        viewModelScope.launch {
            try {
                val endpoints = preferences.cryptoEndpoints.first()
                val result = withContext(Dispatchers.IO) {
                    CryptoHistoryRepository(db, HttpCryptoTransferProvider({ endpoints })).refreshAll()
                }
                readFailed.value = result.failed > 0 || result.unsupported > 0
                message.value = getApplication<Application>().getString(R.string.crypto_history_result, result.imported, result.failed) +
                    if (result.unsupported > 0) " " + getApplication<Application>().getString(R.string.crypto_history_scope) else ""
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                readFailed.value = true
                message.value = getApplication<Application>().getString(R.string.income_sources_save_failed)
            } finally {
                reading.value = false
            }
        }
    }

    fun save(
        existing: IncomeSourceEntity?, label: String, amountMinor: Long, currency: String,
        accountId: Long?, dayFrom: Int, weekendRule: WeekendRule,
        startedOn: Long = existing?.startedOn ?: LocalDate.now(zone).withDayOfMonth(1).toEpochDay(),
    ) = mutate {
        sources.save(IncomeSourceEntity(
            id = existing?.id ?: 0, label = label.trim(), amountMinor = amountMinor,
            currency = currency.trim().uppercase(), accountId = accountId,
            expectedDayFrom = dayFrom, expectedDayTo = dayFrom, weekendRule = weekendRule,
            startedOn = startedOn, endedOn = existing?.endedOn,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        ))
    }

    fun confirmPayment(source: IncomeSourceEntity, transaction: TransactionEntity) =
        mutate { sources.confirmPayment(source.id, transaction.id, zone) }

    fun forgetPayment(transaction: TransactionEntity) = mutate { sources.forgetPayment(transaction.id) }

    fun link(transfer: CryptoBankTransfer) = mutate { bridges.link(transfer.withdrawal.id, transfer.credit.id) }
    fun unlink(transfer: CryptoBankTransfer) = mutate { bridges.unlink(requireNotNull(transfer.withdrawal.transferGroupId)) }
    fun end(source: IncomeSourceEntity) = mutate {
        db.incomeSourceDao().upsert(source.copy(endedOn = maxOf(source.startedOn, LocalDate.now(zone).toEpochDay())))
    }
    fun delete(source: IncomeSourceEntity) = mutate { db.incomeSourceDao().delete(source.id) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); message.value = null }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { message.value = getApplication<Application>().getString(R.string.income_sources_save_failed) }
        }
    }
}
