package dev.whekin.whfin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0")
    suspend fun allActive(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE iban = :iban AND currency = :currency LIMIT 1")
    suspend fun byIbanAndCurrency(iban: String, currency: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE groupId = :groupId")
    suspend fun countInGroup(groupId: Long): Int

    @Query(
        "SELECT a.* FROM accounts a " +
            "JOIN instrument_account_links l ON l.accountId = a.id " +
            "JOIN payment_instruments i ON i.id = l.instrumentId " +
            "WHERE i.last4 = :last4 AND a.currency = :currency AND a.isArchived = 0"
    )
    suspend fun byCardAndCurrency(last4: String, currency: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 AND type IN ('BANK', 'SAVINGS') AND currency = :currency")
    suspend fun bankAccountsByCurrency(currency: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE groupId = :groupId AND isArchived = 0 ORDER BY sortOrder, id")
    suspend fun byGroup(groupId: Long): List<AccountEntity>
}

@Dao
interface FinancialGroupDao {
    @Query("SELECT * FROM financial_groups WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<FinancialGroupEntity>>

    @Query("SELECT * FROM financial_groups WHERE id = :id")
    suspend fun byId(id: Long): FinancialGroupEntity?

    @Query("SELECT * FROM financial_groups WHERE type = :type AND provider = :provider LIMIT 1")
    suspend fun byProvider(type: FinancialGroupType, provider: String): FinancialGroupEntity?

    @Insert suspend fun insert(group: FinancialGroupEntity): Long
    @Update suspend fun update(group: FinancialGroupEntity)
    @Query("DELETE FROM financial_groups WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PaymentInstrumentDao {
    @Query("SELECT * FROM payment_instruments WHERE isArchived = 0 ORDER BY id")
    fun observeActive(): Flow<List<PaymentInstrumentEntity>>
    @Query("SELECT * FROM instrument_account_links")
    fun observeLinks(): Flow<List<InstrumentAccountLinkEntity>>

    @Query(
        "SELECT COUNT(*) FROM instrument_account_links l " +
            "JOIN payment_instruments i ON i.id = l.instrumentId " +
            "JOIN accounts a ON a.id = l.accountId " +
            "WHERE i.isArchived = 0 AND a.isArchived = 0"
    )
    fun observeConfiguredCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM instrument_account_links l " +
            "JOIN payment_instruments i ON i.id = l.instrumentId " +
            "JOIN accounts a ON a.id = l.accountId " +
            "WHERE i.isArchived = 0 AND a.isArchived = 0"
    )
    suspend fun configuredCount(): Int

    @Query("SELECT * FROM payment_instruments WHERE groupId = :groupId AND last4 = :last4 LIMIT 1")
    suspend fun byLast4(groupId: Long, last4: String): PaymentInstrumentEntity?

    @Query("SELECT i.* FROM payment_instruments i JOIN instrument_account_links l ON l.instrumentId = i.id WHERE l.accountId = :accountId ORDER BY i.id")
    suspend fun forAccount(accountId: Long): List<PaymentInstrumentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: PaymentInstrumentEntity): Long
    @Update suspend fun update(item: PaymentInstrumentEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(item: InstrumentAccountLinkEntity): Long
    @Query("DELETE FROM instrument_account_links WHERE accountId = :accountId") suspend fun unlinkAccount(accountId: Long)

    @Transaction
    suspend fun replaceForAccount(account: AccountEntity, cards: List<Pair<String, PaymentInstrumentType>>) {
        val groupId = requireNotNull(account.groupId)
        unlinkAccount(account.id)
        cards.distinctBy { it.first }.forEach { (last4, type) ->
            val existing = byLast4(groupId, last4)
            val instrumentId = existing?.id ?: insert(PaymentInstrumentEntity(groupId = groupId, type = type, last4 = last4))
            check(instrumentId > 0)
            link(InstrumentAccountLinkEntity(instrumentId, account.id))
        }
    }

    @Transaction
    suspend fun linkForAccount(account: AccountEntity, last4: String, type: PaymentInstrumentType) {
        linkForAccounts(listOf(account), last4, type)
    }

    @Transaction
    suspend fun linkForAccounts(accounts: List<AccountEntity>, last4: String, type: PaymentInstrumentType) {
        require(last4.matches(Regex("\\d{4}")))
        require(accounts.isNotEmpty())
        val groupId = requireNotNull(accounts.first().groupId)
        require(accounts.all { it.groupId == groupId })
        val existing = byLast4(groupId, last4)
        if (existing != null && existing.type != type) update(existing.copy(type = type))
        val instrumentId = existing?.id
            ?: insert(PaymentInstrumentEntity(groupId = groupId, type = type, last4 = last4))
                .takeIf { it > 0 }
            ?: requireNotNull(byLast4(groupId, last4)).id
        accounts.forEach { account -> link(InstrumentAccountLinkEntity(instrumentId, account.id)) }
    }
}

@Dao
interface CryptoDao {
    @Insert suspend fun insertAddress(item: WalletAddressEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAsset(item: CryptoAssetEntity): Long
    @Query("SELECT * FROM crypto_assets WHERE chainId = :chainId AND contractAddress IS :contract LIMIT 1")
    suspend fun asset(chainId: String, contract: String?): CryptoAssetEntity?
    @Query("SELECT * FROM wallet_addresses WHERE id = :id") suspend fun addressById(id: Long): WalletAddressEntity?
    @Query("SELECT * FROM wallet_addresses WHERE chainId = :chainId AND address = :address LIMIT 1")
    suspend fun address(chainId: String, address: String): WalletAddressEntity?
    @Query("SELECT * FROM wallet_addresses ORDER BY id") fun observeAddresses(): Flow<List<WalletAddressEntity>>
}

@Dao
interface StatementSourceDao {
    @Query("SELECT * FROM statement_sources ORDER BY id")
    fun observeAll(): Flow<List<StatementSourceEntity>>
    @Insert suspend fun insert(item: StatementSourceEntity): Long
    @Query("SELECT * FROM statement_sources WHERE accountId = :accountId AND type = 'ACCOUNT' LIMIT 1")
    suspend fun forAccount(accountId: Long): StatementSourceEntity?
    @Query("SELECT * FROM statement_sources WHERE instrumentId = :instrumentId AND type = 'CARD' LIMIT 1")
    suspend fun forInstrument(instrumentId: Long): StatementSourceEntity?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isSystem = 1 AND name = :name LIMIT 1")
    suspend fun systemByName(name: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun delete(id: Long)

    @Query("UPDATE categories SET name = :newName WHERE name = :oldName AND isSystem = 0")
    suspend fun rename(oldName: String, newName: String)
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants WHERE normalizedKey = :key LIMIT 1")
    suspend fun byKey(key: String): MerchantEntity?

    @Query(
        "SELECT m.* FROM merchants m JOIN merchant_aliases a ON a.merchantId = m.id " +
            "WHERE a.pattern = :pattern LIMIT 1"
    )
    suspend fun byAlias(pattern: String): MerchantEntity?

    /** Резолв: сначала канон, потом алиасы. */
    @Transaction
    suspend fun resolve(normalized: String): MerchantEntity? =
        byKey(normalized) ?: byAlias(normalized)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: MerchantAliasEntity): Long

    @Query("UPDATE merchants SET categoryId = :categoryId WHERE id = :merchantId")
    suspend fun setCategory(merchantId: Long, categoryId: Long?)

    @Query("SELECT * FROM merchants ORDER BY displayName")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE categoryId IS NULL")
    suspend fun uncategorized(): List<MerchantEntity>
}

@Dao
interface TransactionDao {
    @Insert suspend fun insertTransferGroup(group: TransferGroupEntity): Long

    @Query("UPDATE transfer_groups SET type = :type, note = :note WHERE id = :id")
    suspend fun updateTransferGroup(id: Long, type: TransferGroupType, note: String?)
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAt DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    fun observeFeed(limit: Int, offset: Int = 0): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis " +
            "ORDER BY occurredAt DESC"
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE externalKey = :key LIMIT 1")
    suspend fun byExternalKey(key: String): TransactionEntity?

    @Query("SELECT externalKey FROM transactions WHERE externalKey IS NOT NULL")
    suspend fun allExternalKeys(): List<String>

    /** SMS-черновики и ручные банковские операции, которые можно сверить с выпиской. */
    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND (status = 'PENDING' OR (status = 'MANUAL' AND source = 'MANUAL')) " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun reconciliationCandidates(accountId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query(
        "SELECT t.* FROM transactions t JOIN accounts a ON a.id = t.accountId " +
            "WHERE a.groupId = :groupId AND t.isTransfer = 1 AND t.transferGroupId IS NULL " +
            "AND t.occurredAt BETWEEN :fromMillis AND :toMillis ORDER BY t.occurredAt"
    )
    suspend fun ungroupedTransfers(groupId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query(
        "SELECT t.* FROM transactions t JOIN accounts a ON a.id = t.accountId " +
            "LEFT JOIN transfer_groups g ON g.id = t.transferGroupId " +
            "WHERE a.groupId = :groupId AND t.isTransfer = 1 " +
            "AND (LOWER(t.note) LIKE '%exchange%' OR t.note LIKE '%კონვერტ%') " +
            "AND (t.transferGroupId IS NULL OR g.type = 'CONVERSION') " +
            "AND t.occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun conversionTransfers(groupId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET transferGroupId = NULL WHERE id = :id")
    suspend fun clearTransferGroup(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE transferGroupId = :groupId AND source = 'MANUAL'")
    suspend fun deleteManualTransferGroup(groupId: Long)

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId ORDER BY amountMinor")
    suspend fun byTransferGroup(groupId: Long): List<TransactionEntity>

    @Query("DELETE FROM transfer_groups WHERE id = :groupId")
    suspend fun deleteTransferGroup(groupId: Long)

    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id = :transactionId")
    suspend fun setTransferGroup(transactionId: Long, groupId: Long)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchantId = :merchantId AND categoryId IS NULL")
    suspend fun categorizeUnassignedForMerchant(merchantId: Long, categoryId: Long)

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId"
    )
    suspend fun sumByAccount(accountId: Long): Long

    @Query(
        "SELECT accountId, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM transactions GROUP BY accountId"
    )
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    /** Частота использования категорий — для сортировки «частые первыми» в формах. */
    @Query(
        "SELECT categoryId, COUNT(*) AS cnt FROM transactions WHERE categoryId IS NOT NULL " +
            "GROUP BY categoryId ORDER BY cnt DESC"
    )
    fun observeCategoryUsage(): Flow<List<CategoryUsage>>

    /** Итоги по категориям за период; переводы между своими счетами исключены. */
    @Query(
        "SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS txCount FROM transactions " +
            "WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis " +
            "AND transferGroupId IS NULL AND isTransfer = 0 " +
            "GROUP BY categoryId"
    )
    suspend fun totalsByCategory(fromMillis: Long, toMillis: Long): List<CategoryTotal>
}

data class AccountBalance(
    val accountId: Long,
    val totalMinor: Long,
)

data class CategoryUsage(
    val categoryId: Long,
    val cnt: Int,
)

data class CategoryTotal(
    val categoryId: Long?,
    val totalMinor: Long,
    val txCount: Int,
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM people WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun byId(id: Long): PersonEntity?

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query(
        "SELECT p.id AS personId, p.name, " +
            "-COALESCE(SUM(CASE WHEN a.purpose IN ('LOAN', 'REPAYMENT') THEN a.amountMinor ELSE 0 END), 0) AS debtMinor " +
            "FROM people p LEFT JOIN transaction_allocations a ON a.personId = p.id " +
            "WHERE p.isArchived = 0 GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
    )
    fun observeDebtBalances(): Flow<List<PersonDebtBalance>>
}

data class PersonDebtBalance(val personId: Long, val name: String, val debtMinor: Long)

@Dao
interface TransactionAllocationDao {
    @Query("SELECT * FROM transaction_allocations ORDER BY id")
    fun observeAll(): Flow<List<TransactionAllocationEntity>>

    @Query("SELECT * FROM transaction_allocations WHERE transactionId = :transactionId ORDER BY id")
    fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>>

    @Query("SELECT * FROM transaction_allocations WHERE transactionId = :transactionId ORDER BY id")
    suspend fun forTransaction(transactionId: Long): List<TransactionAllocationEntity>

    @Insert
    suspend fun insertAll(allocations: List<TransactionAllocationEntity>)

    @Query("DELETE FROM transaction_allocations WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    @Transaction
    suspend fun replaceForTransaction(transactionId: Long, allocations: List<TransactionAllocationEntity>) {
        require(allocations.all { it.transactionId == transactionId })
        deleteForTransaction(transactionId)
        insertAll(allocations)
    }
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt_cases ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END, openedAt DESC")
    fun observeCases(): Flow<List<DebtCaseEntity>>

    @Query("SELECT * FROM debt_events ORDER BY occurredAt DESC, id DESC")
    fun observeEvents(): Flow<List<DebtEventEntity>>

    @Query("SELECT * FROM debt_cases WHERE id = :id")
    suspend fun caseById(id: Long): DebtCaseEntity?

    @Query("SELECT * FROM debt_events WHERE debtCaseId = :caseId ORDER BY occurredAt, id")
    suspend fun eventsForCase(caseId: Long): List<DebtEventEntity>

    @Insert suspend fun insertCase(item: DebtCaseEntity): Long
    @Insert suspend fun insertEvent(item: DebtEventEntity): Long
    @Update suspend fun updateCase(item: DebtCaseEntity)
}

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<StatementImportEntity>>

    @Query("SELECT * FROM statement_imports WHERE accountId = :accountId ORDER BY importedAt DESC")
    fun observeForAccount(accountId: Long): Flow<List<StatementImportEntity>>

    @Insert
    suspend fun insert(item: StatementImportEntity): Long
}

@Dao
interface ReconciliationIssueDao {
    @Query("SELECT * FROM reconciliation_issues WHERE state = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<ReconciliationIssueEntity>>

    @Transaction
    @Query("SELECT * FROM reconciliation_issues WHERE state = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpenWithTransactions(): Flow<List<ReconciliationIssueWithTransaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(issue: ReconciliationIssueEntity): Long

    @Query("UPDATE reconciliation_issues SET importId = :importId WHERE transactionId = :transactionId AND state = 'OPEN'")
    suspend fun moveOpenToImport(transactionId: Long, importId: Long)

    @Query("UPDATE reconciliation_issues SET state = 'KEPT' WHERE id = :id")
    suspend fun keep(id: Long)
}

@Dao
interface SmsDiagnosticDao {
    @Query("SELECT * FROM sms_diagnostics ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<SmsDiagnosticEntity>>

    @Query("SELECT * FROM sms_diagnostics WHERE id = :id")
    suspend fun byId(id: Long): SmsDiagnosticEntity?

    @Query("SELECT * FROM sms_diagnostics WHERE externalKey = :externalKey LIMIT 1")
    suspend fun byExternalKey(externalKey: String): SmsDiagnosticEntity?

    @Insert
    suspend fun insert(item: SmsDiagnosticEntity): Long

    @Update
    suspend fun update(item: SmsDiagnosticEntity)

    @Query("DELETE FROM sms_diagnostics")
    suspend fun deleteAll()
}

data class ReconciliationIssueWithTransaction(
    @Embedded val issue: ReconciliationIssueEntity,
    @Relation(parentColumn = "transactionId", entityColumn = "id")
    val transaction: TransactionEntity,
)
