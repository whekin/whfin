package dev.whekin.whfin.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.preferences.nextDisplayCurrency
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.data.rates.CoinGeckoPriceProvider
import dev.whekin.whfin.data.rates.NbgFiatRateProvider
import dev.whekin.whfin.data.rates.NetWorthSource
import dev.whekin.whfin.data.rates.RatesRepository
import dev.whekin.whfin.data.rates.PIVOT_CURRENCY
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CREDO_PROVIDER
import dev.whekin.whfin.data.db.insertBankLedger
import androidx.room.withTransaction
import java.time.LocalTime
import dev.whekin.whfin.data.categorization.CategorySuggester
import dev.whekin.whfin.data.sms.SmsTransactionImporter
import dev.whekin.whfin.data.mutation.AllocationMutation
import dev.whekin.whfin.data.mutation.ManualMutation
import dev.whekin.whfin.data.mutation.MutationSelection
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.whekin.whfin.data.mutation.MutationRejection
import dev.whekin.whfin.data.mutation.MutationReport
import dev.whekin.whfin.data.mutation.TransactionMutationException
import dev.whekin.whfin.data.mutation.TransactionMutationModule
import dev.whekin.whfin.ui.sms.SmsRoutingAccount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class CredoSyncReminder(
    val daysSinceSync: Int?,
    val awaitingStatementCount: Int,
)

internal fun credoSyncReminder(
    lastCompletedAt: Long?,
    awaitingStatementCount: Int,
    hasCredoAccounts: Boolean,
    nowMillis: Long,
    latestCredoImportAt: Long? = null,
): CredoSyncReminder? {
    if (!hasCredoAccounts) return null
    val effectiveLastSync = listOfNotNull(lastCompletedAt, latestCredoImportAt).maxOrNull()
    if (effectiveLastSync == null) {
        return if (awaitingStatementCount > 0) {
            CredoSyncReminder(daysSinceSync = null, awaitingStatementCount = awaitingStatementCount)
        } else null
    }
    val days = ((nowMillis - effectiveLastSync).coerceAtLeast(0L) / MILLIS_PER_DAY).toInt()
    return if (days >= CREDO_SYNC_REMINDER_DAYS) {
        CredoSyncReminder(daysSinceSync = days, awaitingStatementCount = awaitingStatementCount)
    } else null
}

private const val MILLIS_PER_DAY = 86_400_000L
private const val CREDO_SYNC_REMINDER_DAYS = 30

data class FeedItem(
    val tx: TransactionEntity,
    val merchant: MerchantEntity?,
    val category: CategoryEntity?,
    val account: AccountEntity?,
    /** "••0001" если у счёта ровно одна карта, иначе null. */
    val cardHint: String?,
    val transferSummary: String? = null,
    val destinationAmountMinor: Long? = null,
    val destinationCurrency: String? = null,
    val destinationAccountId: Long? = null,
    /**
     * Оплата в валюте, на которую банк автоматически сконвертировал деньги
     * (не хватило валюты на счёте): сколько это стоило в исходной валюте.
     * Конвертация при этом скрывается из ленты — остаётся одна честная покупка.
     */
    val fundedByConversionMinor: Long? = null,
    val fundedByConversionCurrency: String? = null,
    val debtPersonName: String? = null,
    val debtMinor: Long? = null,
    val isDebt: Boolean = false,
    /** Доли на людей (SHARED/GIFT): имя → сумма на этого человека (abs, minor). */
    val splitOnPeople: List<Pair<String, Long>> = emptyList(),
    val day: LocalDate,
)

data class UnroutedOperation(
    val diagnostic: SmsDiagnosticEntity,
    val day: LocalDate,
)

data class PhysicalCardHomeBalance(
    val accountId: Long,
    val accountName: String,
    val balanceMinor: Long,
    val cardLast4s: List<String>,
)

/** Одна доля разбивки: сколько потрачено на человека и с каким смыслом. */
data class SplitShare(
    val personId: Long,
    val amountMinor: Long,
    val purpose: AllocationPurpose,
)

internal fun buildBaseFeedItems(
    transactions: List<TransactionEntity>,
    merchants: List<MerchantEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    masksByAccount: Map<Long, List<String>>,
    zone: ZoneId,
): List<FeedItem> {
    val merchantById = merchants.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    // Своё имя счёта читается лучше технического "GEL •0001": валюта уже видна по сумме строки,
    // а хвост IBAN оставляем как различитель между счетами одного банка.
    fun accountLabel(account: AccountEntity): String = when {
        account.type == AccountType.CASH -> account.name
        account.iban == null -> account.name
        account.name.isBlank() -> "${account.currency} •${account.iban.takeLast(4)}"
        else -> "${account.name} •${account.iban.takeLast(4)}"
    }
    val transferLegs = transactions.filter { it.transferGroupId != null }.groupBy { it.transferGroupId }
    return transactions.filter { tx ->
        val legs = tx.transferGroupId?.let(transferLegs::get).orEmpty()
        tx.transferGroupId == null || tx.amountMinor < 0 || legs.none { it.amountMinor < 0 }
    }.map { tx ->
        val accountMasks = masksByAccount[tx.accountId].orEmpty()
        val destinationLeg = tx.transferGroupId?.let(transferLegs::get)
            ?.firstOrNull { it.id != tx.id && it.amountMinor > 0 }
        val destination = destinationLeg?.let { accountById[it.accountId] }
        val current = accountById[tx.accountId]
        val isCurrencyExchange = tx.note?.let { note ->
            note.contains("exchange", ignoreCase = true) || note.contains("კონვერტ", ignoreCase = true)
        } == true
        val ibanPeer = tx.counterpartyIban?.let { iban ->
            accounts.firstOrNull { it.iban == iban && it.currency == tx.currency }
        }
        val peerLabel = ibanPeer?.let(::accountLabel)
            ?: tx.counterpartyIban?.takeLast(4)?.let { "${tx.currency} •$it" }
        val inferredDirection = if (isCurrencyExchange && current != null) {
            "${accountLabel(current)} → FX"
        } else if (tx.isTransfer && current != null && peerLabel != null) {
            if (tx.amountMinor < 0) "${accountLabel(current)} → $peerLabel"
            else "$peerLabel → ${accountLabel(current)}"
        } else null
        FeedItem(
            tx = tx,
            merchant = tx.merchantId?.let(merchantById::get),
            category = tx.categoryId?.let(categoryById::get),
            account = current,
            cardHint = accountMasks.singleOrNull()?.let { "••$it" },
            transferSummary = destination?.let { target ->
                current?.let { "${accountLabel(it)} → ${accountLabel(target)}" }
            } ?: inferredDirection,
            destinationAmountMinor = destinationLeg?.amountMinor,
            destinationCurrency = destinationLeg?.currency,
            destinationAccountId = destinationLeg?.accountId,
            day = Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate(),
        )
    }.let { linkAutoConversions(it, zone) }
}

internal fun applyDebtAllocations(
    items: List<FeedItem>,
    allocations: List<TransactionAllocationEntity>,
    people: List<PersonEntity>,
): List<FeedItem> {
    val personById = people.associateBy { it.id }
    val debtByTransaction = allocations.filter { it.purpose == AllocationPurpose.LOAN }
        .groupBy { it.transactionId }
    // Доли на людей (совместное/подарок) — справочное измерение, не долг
    val splitByTransaction = allocations
        .filter { it.personId != null && (it.purpose == AllocationPurpose.SHARED || it.purpose == AllocationPurpose.GIFT) }
        .groupBy { it.transactionId }
    return items.map { item ->
        val debts = debtByTransaction[item.tx.id].orEmpty()
        val debt = debts.firstOrNull()
        val splits = splitByTransaction[item.tx.id].orEmpty()
            .groupBy { it.personId }
            .mapNotNull { (personId, allocs) ->
                val name = personId?.let(personById::get)?.name ?: return@mapNotNull null
                name to allocs.sumOf { kotlin.math.abs(it.amountMinor) }
            }
        item.copy(
            debtPersonName = debt?.personId?.let(personById::get)?.name,
            debtMinor = debts.takeIf { it.isNotEmpty() }?.sumOf { kotlin.math.abs(it.amountMinor) },
            isDebt = debts.isNotEmpty(),
            splitOnPeople = splits,
        )
    }
}

private fun linkAutoConversions(items: List<FeedItem>, zone: ZoneId): List<FeedItem> {
    val conversions = items.filter {
        it.tx.transferGroupId != null &&
            it.destinationCurrency != null &&
            it.destinationCurrency != it.tx.currency &&
            it.destinationAmountMinor != null
    }
    if (conversions.isEmpty()) return items

    val hiddenGroupIds = mutableSetOf<Long>()
    val fundedByTxId = mutableMapOf<Long, Pair<Long, String>>()
    val usedPayments = mutableSetOf<Long>()

    for (conversion in conversions) {
        val received = conversion.destinationAmountMinor!!
        val receivedCurrency = conversion.destinationCurrency!!
        val payment = items.firstOrNull { candidate ->
            val spent = -candidate.tx.amountMinor
            val leftover = received - spent
            candidate.tx.id !in usedPayments &&
                candidate.tx.transferGroupId == null &&
                !candidate.tx.isTransfer &&
                candidate.tx.amountMinor < 0 &&
                (conversion.destinationAccountId == null ||
                    candidate.tx.accountId == conversion.destinationAccountId) &&
                candidate.tx.currency == receivedCurrency &&
                leftover >= 0 &&
                leftover <= maxOf(received / 20, 100) &&
                kotlin.math.abs(
                    java.time.temporal.ChronoUnit.DAYS.between(candidate.day, conversion.day),
                ) <= 1
        } ?: continue
        usedPayments += payment.tx.id
        hiddenGroupIds += conversion.tx.transferGroupId!!
        fundedByTxId[payment.tx.id] = kotlin.math.abs(conversion.tx.amountMinor) to conversion.tx.currency
    }

    return items.mapNotNull { item ->
        when {
            item.tx.transferGroupId in hiddenGroupIds -> null
            else -> fundedByTxId[item.tx.id]?.let { (amount, currency) ->
                item.copy(fundedByConversionMinor = amount, fundedByConversionCurrency = currency)
            } ?: item
        }
    }
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as WhfinApp).db
    private val debtRepository = dev.whekin.whfin.data.debt.DebtRepository(db)
    private val smsImporter = SmsTransactionImporter(db)
    private val transactionMutations = TransactionMutationModule(db)
    private val zone = ZoneId.systemDefault()

    /** Contradictions the last integrity pass found; Home says so rather than only the log. */
    val integrityIssues: StateFlow<Int> = (app as WhfinApp).integrityIssues

    private val _rejected = MutableStateFlow<MutationRejection?>(null)

    /**
     * True when the ledger refused the last change.
     *
     * The mutation module is a contract, and a screen that breaks it used to take the whole app
     * down with it — a rejection is a bug in the caller, but the person typing an expense must not
     * pay for it with a crash and a lost entry. The refusal itself is what the user sees; the
     * specific rule goes to the log, because it names an internal invariant, not something they did.
     */
    val rejected: StateFlow<MutationRejection?> = _rejected.asStateFlow()

    fun dismissRejection() { _rejected.value = null }

    /**
     * Runs a batch change and speaks up when it did nothing.
     *
     * A protected row is skipped rather than refused, so a delete that removes none of the selected
     * operations would otherwise look exactly like a delete that worked.
     */
    private fun mutateBatch(block: suspend () -> MutationReport) {
        mutate {
            val report = block()
            if (report.changed == 0 && report.skipped > 0) {
                _rejected.value = report.skippedReason ?: MutationRejection.IMPORTED_IS_PROTECTED
            }
        }
    }

    /** Runs a ledger change, turning a refused one into a message instead of a crash. */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (rejection: TransactionMutationException) {
                Log.w("WHFIN", "Ledger refused a change: ${rejection.message}")
                _rejected.value = rejection.rejection
            }
        }
    }

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unroutedOperations: StateFlow<List<UnroutedOperation>> = db.smsDiagnosticDao().observeUnrouted()
        .map { diagnostics ->
            diagnostics.mapNotNull { diagnostic ->
                val occurredAt = diagnostic.occurredAt ?: return@mapNotNull null
                UnroutedOperation(
                    diagnostic = diagnostic,
                    day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val smsRoutingAccounts: StateFlow<List<SmsRoutingAccount>> = combine(
        accounts,
        db.financialGroupDao().observeActive(),
    ) { accounts, groups ->
        val groupNames = groups.associate { it.id to it.name }
        accounts.filter { it.type == AccountType.BANK || it.type == AccountType.SAVINGS }
            .map { account -> SmsRoutingAccount(account, account.groupId?.let(groupNames::get)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val cardHints = combine(
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { instruments, links ->
        val byId = instruments.associateBy { it.id }
        links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
            accountLinks.mapNotNull { byId[it.instrumentId]?.last4 }
        }
    }

    /** Physical-card money the Home screen can judge without opening Accounts. */
    val physicalCardBalances: StateFlow<List<PhysicalCardHomeBalance>> = combine(
        accounts,
        db.transactionDao().observeAccountBalances(),
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { accounts, balances, instruments, links ->
        val balanceByAccount = balances.associate { it.accountId to it.totalMinor }
        val physicalById = instruments
            .filter { it.type == PaymentInstrumentType.PHYSICAL_CARD && !it.isArchived }
            .associateBy { it.id }
        val cardsByAccount = links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
            accountLinks.mapNotNull { physicalById[it.instrumentId]?.last4 }.distinct().sorted()
        }
        accounts.filter { it.currency.equals("GEL", ignoreCase = true) }
            .mapNotNull { account ->
                cardsByAccount[account.id]?.takeIf(List<String>::isNotEmpty)?.let { cards ->
                    PhysicalCardHomeBalance(
                        accountId = account.id,
                        accountName = account.name,
                        balanceMinor = balanceByAccount[account.id] ?: 0L,
                        cardLast4s = cards,
                    )
                }
            }
            .sortedBy(PhysicalCardHomeBalance::balanceMinor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val baseItems: StateFlow<List<FeedItem>> = combine(
        db.transactionDao().observeFeed(limit = 500),
        db.merchantDao().observeAll(),
        categories,
        accounts,
        cardHints,
    ) { txs, merchants, categories, accounts, masksByAccount ->
        buildBaseFeedItems(txs, merchants, categories, accounts, masksByAccount, zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<FeedItem>> = combine(
        baseItems,
        db.transactionAllocationDao().observeAll(),
        people,
    ) { items, allocations, people ->
        applyDebtAllocations(items, allocations, people)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val voidedImported: StateFlow<List<TransactionEntity>> = db.transactionDao().observeVoidedImported()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Скоринг умных подсказок: частота с затуханием + совместимость суммы. */
    val categorySuggester: StateFlow<CategorySuggester?> = db.transactionDao()
        .observeCategorySamples(System.currentTimeMillis() - CategorySuggester.LOOKBACK_MILLIS)
        .map<List<dev.whekin.whfin.data.db.CategorySample>, CategorySuggester?> {
            CategorySuggester(it, System.currentTimeMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Категории, отсортированные по уместности (без суммы) — для компактной формы добавления. */
    val categoriesByUsage: StateFlow<List<CategoryEntity>> = combine(
        categories,
        categorySuggester,
    ) { cats, suggester ->
        suggester?.rankCategories(cats) ?: cats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val preferences = UiPreferences(getApplication<Application>())

    internal val credoSyncReminder: StateFlow<CredoSyncReminder?> = combine(
        preferences.lastCredoSyncAt,
        db.statementImportDao().observeLatestCredoImportAt(),
        db.transactionDao().observeAwaitingStatementSmsCount(),
        db.financialGroupDao().observeActive(),
    ) { lastCompletedAt, latestCredoImportAt, awaitingStatementCount, groups ->
        credoSyncReminder(
            lastCompletedAt = lastCompletedAt,
            latestCredoImportAt = latestCredoImportAt,
            awaitingStatementCount = awaitingStatementCount,
            hasCredoAccounts = groups.any { it.provider == CREDO_PROVIDER },
            nowMillis = System.currentTimeMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The same headline as Accounts, so the two screens can never disagree about what is owned. */
    val netWorth: StateFlow<ConvertedTotal?> = NetWorthSource(db, preferences).observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val displayCurrency: StateFlow<String> = preferences.displayCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PIVOT_CURRENCY)

    private val ratesRepository = RatesRepository(
        db = db,
        providers = listOf(NbgFiatRateProvider(), CoinGeckoPriceProvider()),
    )

    init {
        // The headline is the same on both screens, so it must not depend on visiting Accounts first.
        viewModelScope.launch { withContext(Dispatchers.IO) { ratesRepository.refreshIfStale() } }
    }

    fun rotateDisplayCurrency() {
        viewModelScope.launch {
            preferences.setDisplayCurrency(nextDisplayCurrency(displayCurrency.value))
        }
    }

    fun addManual(tx: ManualTransaction) {
        mutate {
            // Сегодняшняя запись получает текущее время, вчерашняя — полдень,
            // чтобы не прыгать в начало дня в ленте
            val time = if (tx.day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
            transactionMutations.createManual(
                ManualMutation(
                    accountId = tx.accountId,
                    amountMinor = tx.amountMinor,
                    destinationAccountId = tx.destinationAccountId,
                    destinationAmountMinor = tx.destinationAmountMinor,
                    categoryId = tx.categoryId,
                    note = tx.note,
                    occurredAt = tx.day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
        }
    }

    fun addDebt(debt: dev.whekin.whfin.data.debt.NewDebt) {
        mutate { debtRepository.open(debt) }
    }

    fun resolveUnrouted(
        diagnosticId: Long,
        accountId: Long,
        cardType: PaymentInstrumentType,
    ) {
        viewModelScope.launch {
            smsImporter.resolveDiagnostic(diagnosticId, accountId, cardType)
        }
    }

    fun resolveGroupedUnrouted(
        diagnosticId: Long,
        fromAccountId: Long,
        toAccountId: Long,
    ) {
        viewModelScope.launch {
            smsImporter.resolveGroupedDiagnostic(diagnosticId, fromAccountId, toAccountId)
        }
    }

    fun addCredoAccount(name: String, currency: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                insertCredoAccount(cleanName, currency)
            }
        }
    }

    fun createCredoAccountAndResolve(
        diagnosticId: Long,
        name: String,
        currency: String,
        cardType: PaymentInstrumentType,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                val accountId = insertCredoAccount(cleanName, currency)
                if (accountId > 0) {
                    smsImporter.resolveDiagnostic(diagnosticId, accountId, cardType)
                }
            }
        }
    }

    private suspend fun insertCredoAccount(name: String, currency: String): Long =
        db.insertBankLedger(CREDO_PROVIDER, name, currency)

    fun assignCategory(item: FeedItem, categoryId: Long) {
        mutate {
            transactionMutations.assignCategory(item.tx.id, categoryId)
            item.merchant?.let { merchant ->
                db.merchantDao().setCategory(merchant.id, categoryId)
                db.transactionDao().categorizeUnassignedForMerchant(merchant.id, categoryId)
            }
        }
    }

    fun createCategory(name: String, kind: CategoryKind, icon: String, color: Int) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.categoryDao().insert(CategoryEntity(
                name = clean,
                kind = kind,
                icon = icon,
                color = color,
                sortOrder = (db.categoryDao().all().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ))
        }
    }

    fun createCashCurrency(rawCurrency: String) {
        val currency = rawCurrency.trim().uppercase().take(8)
        if (currency.length < 3) return
        viewModelScope.launch {
            if (db.accountDao().allActive().any { it.type == dev.whekin.whfin.data.db.AccountType.CASH && it.currency == currency }) return@launch
            db.accountDao().insert(AccountEntity(
                name = "Cash",
                type = dev.whekin.whfin.data.db.AccountType.CASH,
                currency = currency,
                sortOrder = 1000,
            ))
        }
    }

    fun updateManual(item: FeedItem, value: ManualTransaction) {
        if (item.tx.source != TxSource.MANUAL) return
        mutate {
            val oldTime = Instant.ofEpochMilli(item.tx.occurredAt).atZone(zone).toLocalTime()
            val occurredAt = value.day.atTime(oldTime).atZone(zone).toInstant().toEpochMilli()
            transactionMutations.updateManual(
                item.tx.id,
                ManualMutation(
                    accountId = value.accountId,
                    amountMinor = value.amountMinor,
                    destinationAccountId = value.destinationAccountId,
                    destinationAmountMinor = value.destinationAmountMinor,
                    categoryId = value.categoryId,
                    note = value.note,
                    occurredAt = occurredAt,
                ),
            )
        }
    }

    fun deleteManual(item: FeedItem) {
        if (item.tx.source != TxSource.MANUAL) return
        mutateBatch {
            transactionMutations.delete(listOf(MutationSelection(item.tx.id, item.tx.transferGroupId)))
        }
    }

    fun correctImported(item: FeedItem) {
        if (item.tx.source !in setOf(TxSource.STATEMENT, TxSource.SMS)) return
        mutate {
            transactionMutations.voidTransaction(item.tx.id)
        }
    }

    fun restoreImported(transactionId: Long) {
        mutate {
            transactionMutations.restoreTransaction(transactionId)
        }
    }

    fun assignDebt(item: FeedItem, personId: Long) {
        if (item.tx.amountMinor >= 0) return
        mutate {
            transactionMutations.replaceAllocations(
                item.tx.id,
                listOf(AllocationMutation(
                    amountMinor = item.tx.amountMinor,
                    categoryId = item.tx.categoryId,
                    personId = personId,
                    purpose = AllocationPurpose.LOAN,
                )),
            )
        }
    }

    fun addPersonAndAssignDebt(item: FeedItem, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || item.tx.amountMinor >= 0) return
        viewModelScope.launch {
            val personId = db.personDao().insert(PersonEntity(
                name = clean,
                color = 0xFF78906F.toInt(),
            ))
            assignDebt(item, personId)
        }
    }

    fun clearAllocations(item: FeedItem) {
        mutate { transactionMutations.replaceAllocations(item.tx.id, emptyList()) }
    }

    /**
     * Разбить расход по людям (вариант 1: справочное измерение, свой расход не меняется).
     * [onPeople] — сколько потрачено на каждого человека (положит. minor); purpose определяет,
     * совместно (SHARED) или подарок (GIFT). Остаток идёт на себя (PERSONAL), без personId.
     * Ноль долей — очистка разбивки.
     */
    fun saveSplit(item: FeedItem, onPeople: List<SplitShare>) {
        if (item.tx.amountMinor >= 0) return
        mutate {
            val total = kotlin.math.abs(item.tx.amountMinor)
            var remaining = total
            val allocations = buildList {
                onPeople.filter { it.amountMinor > 0 }.forEach { share ->
                    val amount = share.amountMinor.coerceAtMost(remaining)
                    if (amount <= 0L) return@forEach
                    remaining -= amount
                    add(TransactionAllocationEntity(
                        transactionId = item.tx.id,
                        amountMinor = -amount,
                        categoryId = item.tx.categoryId,
                        personId = share.personId,
                        purpose = share.purpose,
                    ))
                }
                if (remaining > 0) add(TransactionAllocationEntity(
                    transactionId = item.tx.id,
                    amountMinor = -remaining,
                    categoryId = item.tx.categoryId,
                    personId = null,
                    purpose = AllocationPurpose.PERSONAL,
                ))
            }
            transactionMutations.replaceAllocations(
                item.tx.id,
                allocations.map { allocation ->
                    AllocationMutation(
                        amountMinor = allocation.amountMinor,
                        categoryId = allocation.categoryId,
                        personId = allocation.personId,
                        purpose = allocation.purpose,
                        note = allocation.note,
                    )
                },
            )
        }
    }

    fun addPerson(name: String, onCreated: (Long) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = db.personDao().insert(PersonEntity(name = clean, color = 0xFF78906F.toInt()))
            onCreated(id)
        }
    }

    fun updateStatus(item: FeedItem, status: TxStatus) {
        if (item.tx.status == status) return
        mutate {
            transactionMutations.setReviewStatus(
                listOf(MutationSelection(item.tx.id, item.tx.transferGroupId)),
                status,
            )
        }
    }

    fun updateStatuses(items: List<FeedItem>, status: TxStatus) {
        if (items.isEmpty()) return
        mutateBatch {
            transactionMutations.setReviewStatus(
                items.map { MutationSelection(it.tx.id, it.tx.transferGroupId) },
                status,
            )
        }
    }

    fun deleteItems(items: List<FeedItem>) {
        if (items.isEmpty()) return
        mutateBatch {
            transactionMutations.delete(items.map { MutationSelection(it.tx.id, it.tx.transferGroupId) })
        }
    }
}
