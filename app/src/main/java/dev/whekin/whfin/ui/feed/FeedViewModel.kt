package dev.whekin.whfin.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryKind
import androidx.room.withTransaction
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val day: LocalDate,
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
    fun accountLabel(account: AccountEntity): String =
        "${account.currency} •${account.iban?.takeLast(4) ?: account.name.takeLast(4)}"
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
    return items.map { item ->
        val debts = debtByTransaction[item.tx.id].orEmpty()
        val debt = debts.firstOrNull()
        item.copy(
            debtPersonName = debt?.personId?.let(personById::get)?.name,
            debtMinor = debts.takeIf { it.isNotEmpty() }?.sumOf { kotlin.math.abs(it.amountMinor) },
            isDebt = debts.isNotEmpty(),
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
    private val zone = ZoneId.systemDefault()

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val cardHints = combine(
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { instruments, links ->
        val byId = instruments.associateBy { it.id }
        links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
            accountLinks.mapNotNull { byId[it.instrumentId]?.last4 }
        }
    }

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

    /** Категории, отсортированные по частоте использования — для компактной формы добавления. */
    val categoriesByUsage: StateFlow<List<CategoryEntity>> = combine(
        categories,
        db.transactionDao().observeCategoryUsage(),
    ) { cats, usage ->
        val rank = usage.withIndex().associate { (index, u) -> u.categoryId to index }
        cats.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBalanceMinor: StateFlow<Long> = combine(
        db.accountDao().observeActive(),
        db.transactionDao().observeAccountBalances(),
    ) { accounts, balances ->
        val gelIds = accounts.filter { it.currency == "GEL" }.mapTo(mutableSetOf()) { it.id }
        balances.filter { it.accountId in gelIds }.sumOf { it.totalMinor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun addManual(tx: ManualTransaction) {
        viewModelScope.launch {
            val account = db.accountDao().byId(tx.accountId) ?: return@launch
            // Сегодняшняя запись получает текущее время, вчерашняя — полдень,
            // чтобы не прыгать в начало дня в ленте
            val time = if (tx.day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
            if (tx.destinationAccountId != null) {
                val destination = db.accountDao().byId(tx.destinationAccountId) ?: return@launch
                db.withTransaction {
                    val now = System.currentTimeMillis()
                    val groupId = db.transactionDao().insertTransferGroup(
                        TransferGroupEntity(
                            type = if (account.currency == destination.currency) TransferGroupType.TRANSFER
                                else TransferGroupType.CONVERSION,
                            note = tx.note,
                            createdAt = now,
                        ),
                    )
                    val occurredAt = tx.day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    db.transactionDao().insertAll(
                        listOf(
                            TransactionEntity(
                                accountId = account.id, amountMinor = -kotlin.math.abs(tx.amountMinor), currency = account.currency,
                                occurredAt = occurredAt, note = tx.note, status = TxStatus.MANUAL, source = TxSource.MANUAL,
                                transferGroupId = groupId, isTransfer = true, createdAt = now,
                            ),
                            TransactionEntity(
                                accountId = destination.id,
                                amountMinor = kotlin.math.abs(tx.destinationAmountMinor ?: tx.amountMinor),
                                currency = destination.currency,
                                occurredAt = occurredAt, note = tx.note, status = TxStatus.MANUAL, source = TxSource.MANUAL,
                                transferGroupId = groupId, isTransfer = true, createdAt = now,
                            ),
                        ),
                    )
                }
            } else db.transactionDao().insert(
                TransactionEntity(
                    accountId = account.id,
                    amountMinor = tx.amountMinor,
                    currency = account.currency,
                    occurredAt = tx.day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    categoryId = tx.categoryId,
                    note = tx.note,
                    status = TxStatus.MANUAL,
                    source = TxSource.MANUAL,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun addDebt(debt: dev.whekin.whfin.data.debt.NewDebt) {
        viewModelScope.launch { debtRepository.open(debt) }
    }

    fun assignCategory(item: FeedItem, categoryId: Long) {
        viewModelScope.launch {
            db.transactionDao().update(item.tx.copy(categoryId = categoryId))
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
        viewModelScope.launch {
            val account = db.accountDao().byId(value.accountId) ?: return@launch
            val oldTime = Instant.ofEpochMilli(item.tx.occurredAt).atZone(zone).toLocalTime()
            val occurredAt = value.day.atTime(oldTime).atZone(zone).toInstant().toEpochMilli()
            db.withTransaction {
                val groupId = item.tx.transferGroupId
                if (groupId == null) {
                    db.transactionDao().update(item.tx.copy(
                        accountId = account.id,
                        amountMinor = value.amountMinor,
                        currency = account.currency,
                        categoryId = value.categoryId,
                        note = value.note,
                        occurredAt = occurredAt,
                    ))
                } else {
                    val destination = value.destinationAccountId?.let { db.accountDao().byId(it) } ?: return@withTransaction
                    val legs = db.transactionDao().byTransferGroup(groupId)
                    val outgoing = legs.firstOrNull { it.amountMinor < 0 } ?: item.tx
                    val incoming = legs.firstOrNull { it.id != outgoing.id } ?: return@withTransaction
                    db.transactionDao().updateTransferGroup(
                        groupId,
                        if (account.currency == destination.currency) TransferGroupType.TRANSFER else TransferGroupType.CONVERSION,
                        value.note,
                    )
                    db.transactionDao().update(outgoing.copy(
                        accountId = account.id, currency = account.currency,
                        amountMinor = -kotlin.math.abs(value.amountMinor), occurredAt = occurredAt, note = value.note,
                    ))
                    db.transactionDao().update(incoming.copy(
                        accountId = destination.id, currency = destination.currency,
                        amountMinor = kotlin.math.abs(value.destinationAmountMinor ?: value.amountMinor),
                        occurredAt = occurredAt, note = value.note,
                    ))
                }
            }
        }
    }

    fun deleteManual(item: FeedItem) {
        if (item.tx.source != TxSource.MANUAL) return
        viewModelScope.launch {
            db.withTransaction {
                val groupId = item.tx.transferGroupId
                if (groupId != null) {
                    db.transactionDao().deleteManualTransferGroup(groupId)
                    db.transactionDao().deleteTransferGroup(groupId)
                } else {
                    db.transactionDao().delete(item.tx.id)
                }
            }
        }
    }

    fun assignDebt(item: FeedItem, personId: Long) {
        if (item.tx.amountMinor >= 0) return
        viewModelScope.launch {
            db.transactionAllocationDao().replaceForTransaction(
                item.tx.id,
                listOf(TransactionAllocationEntity(
                    transactionId = item.tx.id,
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
        viewModelScope.launch { db.transactionAllocationDao().deleteForTransaction(item.tx.id) }
    }

    fun updateStatus(item: FeedItem, status: TxStatus) {
        if (item.tx.status == status) return
        viewModelScope.launch { db.transactionDao().update(item.tx.copy(status = status)) }
    }

    fun updateStatuses(items: List<FeedItem>, status: TxStatus) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                val groupIds = items.mapNotNull { it.tx.transferGroupId }.distinct()
                val transactionIds = items.filter { it.tx.transferGroupId == null }.map { it.tx.id }.distinct()
                if (transactionIds.isNotEmpty()) db.transactionDao().updateStatus(transactionIds, status)
                if (groupIds.isNotEmpty()) db.transactionDao().updateTransferGroupStatus(groupIds, status)
            }
        }
    }

    fun deleteItems(items: List<FeedItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                val groupIds = items.mapNotNull { it.tx.transferGroupId }.distinct()
                val transactionIds = items.filter { it.tx.transferGroupId == null }.map { it.tx.id }.distinct()
                if (transactionIds.isNotEmpty()) db.transactionDao().deleteByIds(transactionIds)
                if (groupIds.isNotEmpty()) {
                    db.transactionDao().deleteByTransferGroupIds(groupIds)
                    db.transactionDao().deleteTransferGroups(groupIds)
                }
            }
        }
    }
}
