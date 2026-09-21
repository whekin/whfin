package dev.whekin.whfin.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sms.BankSmsBank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.LocalDate

internal data class SetupAccountReview(
    val account: AccountEntity,
    val provider: String?,
    val balance: Long,
    val bankBalance: Long?,
    val bankDay: Long?,
    val difference: Long?,
    val chainBalance: CryptoBalanceEntity?,
    val pending: Int,
    val fingerprint: String,
    val checked: Boolean = false,
)

/** Compare once at the closing date, never row-by-row within a bank's unordered day. */
internal fun setupAccountReview(
    account: AccountEntity,
    provider: String?,
    transactions: List<TransactionEntity>,
    imports: List<StatementImportEntity>,
    chainBalance: CryptoBalanceEntity? = null,
): SetupAccountReview {
    val rows = transactions.filter { it.accountId == account.id && !it.isVoided }.sortedBy { it.id }
    val balance = rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
    val anchor = imports.filter { it.accountId == account.id && it.origin != StatementImportOrigin.USER_OPENING &&
        it.closingBalanceMinor != null && it.periodTo != null }
        .maxWithOrNull(compareBy<StatementImportEntity> { it.periodTo }.thenBy { it.importedAt }.thenBy { it.id })
    val difference = anchor?.let {
        val cutoff = LocalDate.ofEpochDay(requireNotNull(it.periodTo)).plusDays(1)
            .atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
        val atCutoff = rows.filter { row -> row.source != TxSource.BANK_HOLD && (row.postedAt ?: row.occurredAt) < cutoff }
            .fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
        Math.subtractExact(atCutoff, requireNotNull(it.closingBalanceMinor))
    }
    // Only balance/evidence changes invalidate the owner's check; categorising an unchanged payment does not.
    val evidence = buildString {
        append(account.id); append('|'); append(account.iban); append('|'); append(account.currency)
        append('|'); append(account.name); append('|'); append(provider); append('|'); append(account.type)
        append('|'); append(chainBalance?.baseUnits); append('|'); append(chainBalance?.decimals)
        append('|'); append(anchor?.closingBalanceMinor); append('|'); append(anchor?.periodTo)
        rows.forEach { append("|${it.id}:${it.amountMinor}:${it.occurredAt}:${it.postedAt}:${it.source}") }
    }
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(evidence.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return SetupAccountReview(account, provider, balance, anchor?.closingBalanceMinor, anchor?.periodTo,
        difference, chainBalance, rows.count { it.source == TxSource.BANK_HOLD }, "${account.id}:$fingerprint")
}

internal data class SetupOverview(
    val accounts: List<SetupAccountReview>,
    val bankAccounts: Map<BankSmsBank, Int>,
    val bankImports: Set<BankSmsBank>,
    val categories: Int,
    val uncategorized: Int,
    val incomes: Int,
    val savingsPlans: Int,
    val debts: Int,
    val unrouted: Int,
) {
    val checked: Int get() = accounts.count { it.checked }
    val allChecked: Boolean get() = accounts.all { it.checked }
}

internal sealed interface SetupOverviewState {
    data object Loading : SetupOverviewState
    data class Ready(val value: SetupOverview) : SetupOverviewState
    data object Failed : SetupOverviewState
}

internal class SetupOverviewViewModel(app: Application) : AndroidViewModel(app) {
    private val whfin = app as WhfinApp
    private val db = whfin.userDb
    private val runtime = whfin.runtimeModes
    private val revision = MutableStateFlow(0)
    private val checksMutex = Mutex()
    val state: StateFlow<SetupOverviewState> = combine(
        db.invalidationTracker.createFlow("accounts", "transactions", "statement_imports", "financial_groups",
            "categories", "income_sources", "savings_plans", "debt_cases", "sms_diagnostics", "crypto_balances"),
        revision,
    ) { _, _ -> Unit }.map {
        try { SetupOverviewState.Ready(read()) as SetupOverviewState }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { SetupOverviewState.Failed }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SetupOverviewState.Loading)

    private suspend fun read(): SetupOverview = db.withTransaction {
        val accounts = db.accountDao().allActive().filter { it.type != AccountType.PERSON }
        val groups = db.financialGroupDao().all().associateBy { it.id }
        val transactions = db.transactionDao().allForIntegrity()
        val imports = db.statementImportDao().all()
        val chains = db.cryptoDao().allBalances().associateBy { it.accountId }
        val checks = runtime.personalSetupChecks
        val reviews = accounts.map { account ->
            setupAccountReview(account, groups[account.groupId]?.let { it.provider ?: it.name }, transactions,
                imports, chains[account.id]).let { it.copy(checked = it.fingerprint in checks) }
        }
        val banks = BankSmsBank.entries.associateWith { bank -> reviews.filter { bank.accepts(it.account, it.provider) } }
        SetupOverview(reviews,
            bankAccounts = banks.mapValues { (_, rows) -> rows.map { it.account.iban ?: "id:${it.account.id}" }.distinct().size },
            bankImports = banks.filterValues { rows -> imports.any { it.accountId in rows.map { r -> r.account.id } && it.origin != StatementImportOrigin.USER_OPENING } }.keys,
            categories = db.categoryDao().all().count { !it.isSystem },
            uncategorized = transactions.count { !it.isVoided && !it.isTransfer && it.amountMinor < 0 && it.categoryId == null && it.source != TxSource.ADJUSTMENT },
            incomes = db.incomeSourceDao().active().size,
            savingsPlans = db.savingsPlanDao().allForIntegrity().count { it.endedOn == null },
            debts = db.debtDao().allCasesForIntegrity().count { it.status == DebtStatus.OPEN },
            unrouted = db.smsDiagnosticDao().unrouted().size)
    }

    fun retry() { revision.value++ }
    fun check(accountId: Long, fingerprint: String, checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { checksMutex.withLock {
            val snapshot = try { read() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { revision.value++; return@withLock }
            val current = snapshot.accounts.firstOrNull { it.account.id == accountId }
            if (current?.fingerprint != fingerprint) { revision.value++; return@withLock }
            val values = runtime.personalSetupChecks.filterNot { it.startsWith("$accountId:") }.toMutableSet()
            if (checked) values += fingerprint
            try { runtime.personalSetupChecks = values }
            catch (_: Exception) { revision.value++; return@withLock }
            revision.value++
        } }
    }
}
