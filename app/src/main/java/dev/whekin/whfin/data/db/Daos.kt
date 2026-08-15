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

    @Query("SELECT * FROM accounts WHERE isArchived = 1 ORDER BY sortOrder, id")
    fun observeArchived(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun allForIntegrity(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0")
    suspend fun allActive(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE iban = :iban AND currency = :currency LIMIT 1")
    suspend fun byIbanAndCurrency(iban: String, currency: String): AccountEntity?

    @Query(
        "SELECT * FROM accounts WHERE groupId = :groupId AND currency = :currency " +
            "AND iban IS NULL AND isArchived = 0 AND type IN ('BANK', 'SAVINGS') ORDER BY id",
    )
    suspend fun unboundBankLedgers(groupId: Long, currency: String): List<AccountEntity>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query(
        "UPDATE accounts SET name = :name, fundRole = :fundRole, bankProduct = :bankProduct " +
            "WHERE groupId = :groupId AND iban = :iban",
    )
    suspend fun updateIbanContainer(
        groupId: Long,
        iban: String,
        name: String,
        fundRole: FundRole,
        bankProduct: BankProduct?,
    )

    @Query("UPDATE accounts SET bankProduct = :bankProduct WHERE groupId = :groupId AND iban = :iban")
    suspend fun updateIbanBankProduct(groupId: Long, iban: String, bankProduct: BankProduct)

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

    /** Every asset ledger of one watch-only address, archived ones included: they still hold the index. */
    @Query("SELECT * FROM accounts WHERE walletAddressId = :addressId ORDER BY id")
    suspend fun byWalletAddress(addressId: Long): List<AccountEntity>

    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE accounts SET isArchived = 1 WHERE walletAddressId = :addressId")
    suspend fun archiveWallet(addressId: Long)

    @Query("UPDATE accounts SET isArchived = 0 WHERE id = :id")
    suspend fun restore(id: Long)
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
    @Query("UPDATE payment_instruments SET isPrimary = 0 WHERE isPrimary = 1") suspend fun clearPrimary()

    @Transaction
    suspend fun replaceForAccount(
        account: AccountEntity,
        cards: List<Pair<String, PaymentInstrumentType>>,
        primaryLast4: String? = null,
    ) {
        replaceForAccounts(listOf(account), cards, primaryLast4)
    }

    @Transaction
    suspend fun replaceForAccounts(
        accounts: List<AccountEntity>,
        cards: List<Pair<String, PaymentInstrumentType>>,
        primaryLast4: String? = null,
    ) {
        require(accounts.isNotEmpty())
        val groupId = requireNotNull(accounts.first().groupId)
        require(accounts.all { it.groupId == groupId })
        require(primaryLast4 == null || cards.any { it.first == primaryLast4 })
        val editedPrimary = accounts.any { account -> forAccount(account.id).any { it.isPrimary } }
        accounts.forEach { account -> unlinkAccount(account.id) }
        cards.distinctBy { it.first }.forEach { (last4, type) ->
            val existing = byLast4(groupId, last4)
            if (existing != null && existing.type != type) update(existing.copy(type = type))
            val instrumentId = existing?.id
                ?: insert(PaymentInstrumentEntity(groupId = groupId, type = type, last4 = last4))
                    .takeIf { it > 0 }
                ?: requireNotNull(byLast4(groupId, last4)).id
            check(instrumentId > 0)
            accounts.forEach { account -> link(InstrumentAccountLinkEntity(instrumentId, account.id)) }
        }
        if (primaryLast4 != null || editedPrimary) {
            clearPrimary()
            primaryLast4?.let { last4 ->
                val primary = requireNotNull(byLast4(groupId, last4))
                update(primary.copy(isPrimary = true))
            }
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
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates") fun observeAll(): Flow<List<ExchangeRateEntity>>
    @Query("SELECT * FROM exchange_rates") suspend fun all(): List<ExchangeRateEntity>

    /** A refresh replaces the quote for a code; quotes are current values, not a price history. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ExchangeRateEntity>)

    @Query("SELECT * FROM exchange_rate_history WHERE code = :code AND onDate = :onDate LIMIT 1")
    suspend fun historical(code: String, onDate: String): ExchangeRateHistoryEntity?

    /** A past day's quote never changes, so re-reading it may overwrite the same row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistorical(items: List<ExchangeRateHistoryEntity>)
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
    @Query("SELECT * FROM wallet_addresses ORDER BY id") suspend fun allAddresses(): List<WalletAddressEntity>

    /** Accounts and their observations hang off the address by CASCADE, so this drops the wallet. */
    @Query("SELECT * FROM crypto_assets WHERE id = :id") suspend fun assetById(id: Long): CryptoAssetEntity?

    @Query("SELECT * FROM crypto_balances ORDER BY accountId") fun observeBalances(): Flow<List<CryptoBalanceEntity>>
    @Query("SELECT * FROM crypto_balances WHERE accountId = :accountId LIMIT 1")
    suspend fun balance(accountId: Long): CryptoBalanceEntity?

    /** Idempotent by account: a refresh replaces the observation instead of appending history. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(item: CryptoBalanceEntity): Long
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
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isVoided = 0 ORDER BY occurredAt DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isVoided = 0 ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    fun observeFeed(limit: Int, offset: Int = 0): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis " +
            "AND isVoided = 0 ORDER BY occurredAt DESC"
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    /** The first recorded day, which is where paging statistics backwards stops being useful. */
    @Query("SELECT MIN(occurredAt) FROM transactions WHERE isVoided = 0")
    fun observeEarliestOccurredAt(): Flow<Long?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE externalKey = :key LIMIT 1")
    suspend fun byExternalKey(key: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE correctionOfTransactionId = :transactionId " +
            "ORDER BY createdAt DESC, id DESC",
    )
    suspend fun correctionsFor(transactionId: Long): List<TransactionEntity>

    /**
     * Corrections that still claim their source is withdrawn.
     *
     * A revoked correction stays in the table as evidence, so "is this row corrected right now"
     * cannot be answered by mere existence.
     */
    @Query(
        "SELECT * FROM transactions WHERE correctionOfTransactionId = :transactionId " +
            "AND correctionRevokedAt IS NULL ORDER BY createdAt DESC, id DESC",
    )
    suspend fun activeCorrectionsFor(transactionId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun allForIntegrity(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING' AND isVoided = 0")
    suspend fun pendingCount(): Int

    /** User-facing SMS operations not yet replaced by statement evidence; grouped legs count once. */
    @Query(
        "SELECT COUNT(DISTINCT CASE WHEN transferGroupId IS NULL THEN -id ELSE transferGroupId END) " +
            "FROM transactions WHERE source = 'SMS' AND isVoided = 0",
    )
    fun observeAwaitingStatementSmsCount(): Flow<Int>

    @Query(
        "SELECT * FROM transactions WHERE isVoided = 1 AND source IN ('STATEMENT', 'SMS') " +
            "ORDER BY occurredAt DESC, id DESC",
    )
    fun observeVoidedImported(): Flow<List<TransactionEntity>>

    /**
     * Foreign-currency rows still waiting for the GEL value of their own day.
     * Transfers are skipped: they never reach income, expenses or category totals.
     */
    @Query(
        "SELECT * FROM transactions WHERE currency != :pivot AND gelValueMinor IS NULL " +
            "AND isTransfer = 0 AND transferGroupId IS NULL AND isVoided = 0 ORDER BY occurredAt DESC"
    )
    suspend fun awaitingValuation(pivot: String): List<TransactionEntity>

    @Query("UPDATE transactions SET gelValueMinor = :gelValueMinor, gelRateOn = :gelRateOn WHERE id = :id")
    suspend fun setGelValue(id: Long, gelValueMinor: Long, gelRateOn: String)

    @Query("SELECT externalKey FROM transactions WHERE accountId = :accountId AND externalKey IS NOT NULL")
    suspend fun externalKeysForAccount(accountId: Long): List<String>

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId AND source = 'ADJUSTMENT' " +
            "AND externalKey LIKE 'opening|%' AND isVoided = 0 ORDER BY occurredAt, id LIMIT 1",
    )
    suspend fun openingAnchor(accountId: Long): TransactionEntity?

    /** SMS rows remain reconcilable because source records evidence independently of active status. */
    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND ((source = 'SMS' AND status IN ('PENDING', 'CONFIRMED')) " +
            "OR (status = 'MANUAL' AND source = 'MANUAL')) " +
            "AND isVoided = 0 AND occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun reconciliationCandidates(accountId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND status = 'CONFIRMED' AND source = 'STATEMENT' " +
            "AND isVoided = 0 AND occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun statementCandidates(
        accountId: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<TransactionEntity>

    @Query(
            "SELECT t.* FROM transactions t JOIN accounts a ON a.id = t.accountId " +
            "WHERE a.groupId = :groupId AND t.isTransfer = 1 AND t.transferGroupId IS NULL " +
            "AND t.source IN ('STATEMENT', 'SMS') " +
            "AND t.isVoided = 0 AND t.occurredAt BETWEEN :fromMillis AND :toMillis ORDER BY t.occurredAt"
    )
    suspend fun ungroupedTransfers(groupId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query(
        "SELECT t.* FROM transactions t JOIN accounts a ON a.id = t.accountId " +
            "LEFT JOIN transfer_groups g ON g.id = t.transferGroupId " +
            "WHERE a.groupId = :groupId AND t.isTransfer = 1 AND t.source IN ('STATEMENT', 'SMS') " +
            "AND (t.transferGroupId IS NULL OR NOT EXISTS (" +
            "SELECT 1 FROM transactions sms WHERE sms.transferGroupId = t.transferGroupId " +
            "AND sms.source = 'SMS' AND sms.isVoided = 0)) " +
            "AND (t.transferGroupId IS NULL OR g.note IS NULL OR g.note NOT LIKE 'Credo SMS %') " +
            "AND ((t.transferGroupId IS NOT NULL AND g.type = 'CONVERSION') " +
            "OR (t.transferGroupId IS NULL AND " +
            "(LOWER(t.note) LIKE '%exchange%' OR t.note LIKE '%კონვერტ%'))) " +
            "AND t.isVoided = 0 AND t.occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun conversionTransfers(groupId: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>

    /**
     * Imported pairings are derived data and may be rebuilt when later statements reveal better legs.
     * Opening anchors are included only to detach groups produced by older pairing code; they are not
     * candidates for a new pair.
     */
    @Query(
        "SELECT DISTINCT t.transferGroupId FROM transactions t " +
        "JOIN accounts a ON a.id = t.accountId " +
            "JOIN transfer_groups g ON g.id = t.transferGroupId " +
            "WHERE a.groupId = :groupId AND t.transferGroupId IS NOT NULL AND t.isVoided = 0 " +
            "AND (t.source IN ('STATEMENT', 'SMS') " +
            "OR (t.source = 'ADJUSTMENT' AND t.externalKey LIKE 'opening|%')) " +
            "AND NOT EXISTS (SELECT 1 FROM transactions sms WHERE sms.transferGroupId = t.transferGroupId " +
            "AND sms.source = 'SMS' AND sms.isVoided = 0) " +
            "AND (g.note IS NULL OR g.note NOT LIKE 'Credo SMS %')",
    )
    suspend fun rebuildableTransferGroupIds(groupId: Long): List<Long>

    @Query("UPDATE transactions SET transferGroupId = NULL WHERE id = :id")
    suspend fun clearTransferGroup(id: Long)

    @Query("UPDATE transactions SET transferGroupId = NULL WHERE transferGroupId IN (:groupIds)")
    suspend fun clearTransferGroups(groupIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("UPDATE transactions SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatus(ids: List<Long>, status: TxStatus)

    @Query("UPDATE transactions SET status = :status WHERE transferGroupId IN (:groupIds)")
    suspend fun updateTransferGroupStatus(groupIds: List<Long>, status: TxStatus)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE transferGroupId IN (:groupIds)")
    suspend fun deleteByTransferGroupIds(groupIds: List<Long>)

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId ORDER BY amountMinor")
    suspend fun byTransferGroup(groupId: Long): List<TransactionEntity>

    @Query("DELETE FROM transfer_groups WHERE id IN (:groupIds)")
    suspend fun deleteTransferGroups(groupIds: List<Long>)

    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id = :transactionId")
    suspend fun setTransferGroup(transactionId: Long, groupId: Long)

    @Query("UPDATE transactions SET transferGroupId = :groupId, isTransfer = 1 WHERE id IN (:transactionIds)")
    suspend fun attachToTransferGroup(transactionIds: List<Long>, groupId: Long)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchantId = :merchantId AND categoryId IS NULL AND isVoided = 0")
    suspend fun categorizeUnassignedForMerchant(merchantId: Long, categoryId: Long)

    /**
     * Rows whose bank operation label is still readable in their note.
     *
     * A row imported before a rule existed keeps no trace of what the bank called it except this
     * note, so this is the only way an operation-kind rule can reach the history it was written for.
     */
    @Query(
        "SELECT id, note FROM transactions WHERE categoryId IS NULL AND isVoided = 0 " +
            "AND isTransfer = 0 AND source = 'STATEMENT' AND note IS NOT NULL AND note != ''"
    )
    suspend fun uncategorizedStatementNotes(): List<StatementNoteRow>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id AND categoryId IS NULL AND isVoided = 0")
    suspend fun categorizeIfUnassigned(id: Long, categoryId: Long)

    @Query(
        "SELECT COUNT(*) AS totalExpenses, " +
            "COALESCE(SUM(CASE WHEN categoryId IS NOT NULL THEN 1 ELSE 0 END), 0) AS categorizedExpenses, " +
            "COALESCE(SUM(CASE WHEN categoryId IS NULL AND merchantId IS NULL THEN 1 ELSE 0 END), 0) AS withoutMerchant " +
            "FROM transactions WHERE amountMinor < 0 AND isTransfer = 0 AND isVoided = 0 " +
            "AND source != 'ADJUSTMENT'"
    )
    fun observeCategoryCoverage(): Flow<CategoryCoverage>

    @Query(
        "SELECT m.id AS merchantId, m.displayName AS displayName, COUNT(*) AS transactionCount, " +
            "MAX(t.occurredAt) AS latestAt FROM transactions t " +
            "JOIN merchants m ON m.id = t.merchantId " +
            "WHERE t.amountMinor < 0 AND t.categoryId IS NULL AND t.isTransfer = 0 AND t.isVoided = 0 " +
            "AND t.source != 'ADJUSTMENT' " +
            // A row that names the recipient's account is answered by recipient, not by the spelling
            // of their name; listing it here too would ask the same question twice.
            "AND (t.counterpartyIban IS NULL OR t.counterpartyIban = '') " +
            "GROUP BY m.id, m.displayName ORDER BY transactionCount DESC, latestAt DESC, m.displayName COLLATE NOCASE"
    )
    fun observeUncategorizedMerchants(): Flow<List<UncategorizedMerchant>>

    /**
     * Uncategorized outgoing transfers, one row per recipient account rather than per spelling of
     * their name. The most recent spelling represents the group, because that is the one the user
     * has just seen in the feed.
     *
     * An account the user owns is never a counterparty. Credo prints the destination account on a
     * cash deposit, which is the user's own ledger: grouped as a recipient it would invite a rule
     * about themselves.
     */
    @Query(
        "SELECT t.counterpartyIban AS iban, COUNT(*) AS transactionCount, " +
            "SUM(t.amountMinor) AS totalMinor, MAX(t.occurredAt) AS latestAt, " +
            "t.currency AS currency, " +
            "(SELECT r.rawCounterparty FROM transactions r " +
            "WHERE r.counterpartyIban = t.counterpartyIban AND r.rawCounterparty IS NOT NULL " +
            "ORDER BY r.occurredAt DESC LIMIT 1) AS displayName " +
            "FROM transactions t " +
            "WHERE t.counterpartyIban IS NOT NULL AND t.counterpartyIban != '' " +
            "AND t.counterpartyIban NOT IN (SELECT iban FROM accounts WHERE iban IS NOT NULL) " +
            "AND t.categoryId IS NULL AND t.amountMinor < 0 AND t.isTransfer = 0 " +
            "AND t.isVoided = 0 AND t.source != 'ADJUSTMENT' " +
            "GROUP BY t.counterpartyIban, t.currency " +
            "ORDER BY transactionCount DESC, latestAt DESC"
    )
    fun observeUncategorizedCounterparties(): Flow<List<UncategorizedCounterparty>>

    /**
     * The same question asked of money coming in.
     *
     * Income is where most of the value in a ledger enters, and it arrives from far fewer parties
     * than expenses go to, so naming a sender once settles a lot at a time. Rows the bank could not
     * attribute to anyone — cash at a terminal, deposit interest — carry no account and are absent
     * here on purpose: their meaning comes from the operation, not from a counterparty.
     */
    @Query(
        "SELECT t.counterpartyIban AS iban, COUNT(*) AS transactionCount, " +
            "SUM(t.amountMinor) AS totalMinor, MAX(t.occurredAt) AS latestAt, " +
            "t.currency AS currency, " +
            "(SELECT r.rawCounterparty FROM transactions r " +
            "WHERE r.counterpartyIban = t.counterpartyIban AND r.rawCounterparty IS NOT NULL " +
            "ORDER BY r.occurredAt DESC LIMIT 1) AS displayName " +
            "FROM transactions t " +
            "WHERE t.counterpartyIban IS NOT NULL AND t.counterpartyIban != '' " +
            "AND t.counterpartyIban NOT IN (SELECT iban FROM accounts WHERE iban IS NOT NULL) " +
            "AND t.categoryId IS NULL AND t.amountMinor > 0 AND t.isTransfer = 0 " +
            "AND t.isVoided = 0 AND t.source != 'ADJUSTMENT' " +
            "GROUP BY t.counterpartyIban, t.currency " +
            "ORDER BY totalMinor DESC, latestAt DESC"
    )
    fun observeUncategorizedIncomeCounterparties(): Flow<List<UncategorizedCounterparty>>

    @Query(
        "SELECT COUNT(*) AS totalExpenses, " +
            "COALESCE(SUM(CASE WHEN categoryId IS NOT NULL THEN 1 ELSE 0 END), 0) AS categorizedExpenses, " +
            "0 AS withoutMerchant " +
            "FROM transactions WHERE amountMinor > 0 AND isTransfer = 0 AND isVoided = 0 " +
            "AND source != 'ADJUSTMENT'"
    )
    fun observeIncomeCoverage(): Flow<CategoryCoverage>

    @Query(
        "UPDATE transactions SET categoryId = :categoryId " +
            "WHERE counterpartyIban = :iban AND categoryId IS NULL AND isVoided = 0"
    )
    suspend fun categorizeUnassignedForCounterparty(iban: String, categoryId: Long)

    /** How much of the ledger each merchant accounts for; the evidence behind a category proposal. */
    @Query(
        "SELECT merchantId AS merchantId, COUNT(*) AS transactionCount FROM transactions " +
            "WHERE merchantId IS NOT NULL AND isVoided = 0 AND isTransfer = 0 " +
            "AND source != 'ADJUSTMENT' GROUP BY merchantId"
    )
    fun observeMerchantUsage(): Flow<List<MerchantUsage>>

    /** Money that arrived in a window, for comparing a declared income against what actually came. */
    @Query(
        "SELECT * FROM transactions WHERE amountMinor > 0 AND isTransfer = 0 AND isVoided = 0 " +
            "AND occurredAt >= :fromMillis AND occurredAt < :toMillis"
    )
    fun observeIncomeBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND isVoided = 0"
    )
    suspend fun sumByAccount(accountId: Long): Long

    @Query(
        "SELECT accountId, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM transactions " +
            "WHERE isVoided = 0 GROUP BY accountId"
    )
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    /** Частота использования категорий — для сортировки «частые первыми» в формах. */
    @Query(
        "SELECT categoryId, COUNT(*) AS cnt FROM transactions WHERE categoryId IS NOT NULL AND isVoided = 0 " +
            "GROUP BY categoryId ORDER BY cnt DESC"
    )
    fun observeCategoryUsage(): Flow<List<CategoryUsage>>

    /** Сырьё для умных подсказок категорий: категоризированные расходы за период. */
    @Query(
        "SELECT categoryId, amountMinor, currency, occurredAt FROM transactions " +
            "WHERE categoryId IS NOT NULL AND amountMinor < 0 AND isTransfer = 0 AND isVoided = 0 " +
            "AND occurredAt >= :sinceMillis"
    )
    fun observeCategorySamples(sinceMillis: Long): Flow<List<CategorySample>>

    /** Итоги по категориям за период; переводы между своими счетами исключены. */
    @Query(
        "SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS txCount FROM transactions " +
            "WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis " +
            "AND transferGroupId IS NULL AND isTransfer = 0 AND isVoided = 0 " +
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

data class CategorySample(
    val categoryId: Long,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
)

data class CategoryTotal(
    val categoryId: Long?,
    val totalMinor: Long,
    val txCount: Int,
)

data class MerchantUsage(
    val merchantId: Long,
    val transactionCount: Int,
)

data class UncategorizedCounterparty(
    val iban: String,
    val displayName: String?,
    val transactionCount: Int,
    val totalMinor: Long,
    val currency: String,
    val latestAt: Long,
)

data class StatementNoteRow(
    val id: Long,
    val note: String,
)

data class CategoryCoverage(
    val totalExpenses: Int,
    val categorizedExpenses: Int,
    val withoutMerchant: Int,
)

data class UncategorizedMerchant(
    val merchantId: Long,
    val displayName: String,
    val transactionCount: Int,
    val latestAt: Long,
)

@Dao
interface CounterpartyRuleDao {
    @Query("SELECT * FROM counterparty_rules ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<CounterpartyRuleEntity>>

    @Query("SELECT * FROM counterparty_rules")
    suspend fun all(): List<CounterpartyRuleEntity>

    @Query("SELECT * FROM counterparty_rules WHERE iban = :iban")
    suspend fun byIban(iban: String): CounterpartyRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CounterpartyRuleEntity): Long

    @Query("DELETE FROM counterparty_rules WHERE iban = :iban")
    suspend fun deleteByIban(iban: String)
}

@Dao
interface IncomeSourceDao {
    @Query("SELECT * FROM income_sources ORDER BY endedOn IS NOT NULL, startedOn DESC, id DESC")
    fun observeAll(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE endedOn IS NULL ORDER BY startedOn DESC")
    suspend fun active(): List<IncomeSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: IncomeSourceEntity): Long

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun delete(id: Long)
}

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
            "-COALESCE(SUM(CASE WHEN t.id IS NOT NULL AND a.purpose IN ('LOAN', 'REPAYMENT') " +
            "THEN a.amountMinor ELSE 0 END), 0) AS debtMinor " +
            "FROM people p LEFT JOIN transaction_allocations a ON a.personId = p.id " +
            "LEFT JOIN transactions t ON t.id = a.transactionId AND t.isVoided = 0 " +
            "WHERE p.isArchived = 0 GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
    )
    fun observeDebtBalances(): Flow<List<PersonDebtBalance>>
}

data class PersonDebtBalance(val personId: Long, val name: String, val debtMinor: Long)

@Dao
interface TransactionAllocationDao {
    @Query("SELECT * FROM transaction_allocations ORDER BY id")
    fun observeAll(): Flow<List<TransactionAllocationEntity>>

    @Query("SELECT * FROM transaction_allocations ORDER BY id")
    suspend fun allForIntegrity(): List<TransactionAllocationEntity>

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

    /** Потрачено на человека (SHARED/GIFT) за период, по валютам; долги считаются отдельно через DebtCase. */
    @Query(
            "SELECT a.personId AS personId, t.currency AS currency, SUM(-a.amountMinor) AS spentMinor " +
            "FROM transaction_allocations a JOIN transactions t ON t.id = a.transactionId " +
            "WHERE a.personId IS NOT NULL AND a.purpose IN ('SHARED', 'GIFT') " +
            "AND t.isVoided = 0 " +
            "AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis " +
            "GROUP BY a.personId, t.currency"
    )
    fun observePersonSpending(fromMillis: Long, toMillis: Long): Flow<List<PersonSpending>>
}

data class PersonSpending(val personId: Long, val currency: String, val spentMinor: Long)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt_cases ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END, openedAt DESC")
    fun observeCases(): Flow<List<DebtCaseEntity>>

    @Query("SELECT * FROM debt_events ORDER BY occurredAt DESC, id DESC")
    fun observeEvents(): Flow<List<DebtEventEntity>>

    @Query("SELECT * FROM debt_cases WHERE id = :id")
    suspend fun caseById(id: Long): DebtCaseEntity?

    @Query("SELECT * FROM debt_cases ORDER BY id")
    suspend fun allCasesForIntegrity(): List<DebtCaseEntity>

    @Query("SELECT * FROM debt_events WHERE debtCaseId = :caseId ORDER BY occurredAt, id")
    suspend fun eventsForCase(caseId: Long): List<DebtEventEntity>

    @Query("SELECT * FROM debt_events WHERE id = :eventId")
    suspend fun eventById(eventId: Long): DebtEventEntity?

    @Query("SELECT * FROM debt_events ORDER BY id")
    suspend fun allEventsForIntegrity(): List<DebtEventEntity>

    @Query("SELECT * FROM debt_events WHERE transactionId = :transactionId LIMIT 1")
    suspend fun eventsForTransaction(transactionId: Long): List<DebtEventEntity>

    @Insert suspend fun insertCase(item: DebtCaseEntity): Long
    @Insert suspend fun insertEvent(item: DebtEventEntity): Long
    @Update suspend fun updateEvent(item: DebtEventEntity)
    @Update suspend fun updateCase(item: DebtCaseEntity)
}

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<StatementImportEntity>>

    /** Upgrade-safe freshness baseline for runs completed before the dedicated preference existed. */
    @Query("SELECT MAX(importedAt) FROM statement_imports WHERE origin = 'CREDO_SYNC'")
    fun observeLatestCredoImportAt(): Flow<Long?>

    @Query("SELECT * FROM statement_imports WHERE accountId = :accountId ORDER BY importedAt DESC")
    fun observeForAccount(accountId: Long): Flow<List<StatementImportEntity>>

    /** What this account is already known to cover: how far the next sync has to reach back. */
    @Query("SELECT * FROM statement_imports WHERE accountId = :accountId")
    suspend fun forAccount(accountId: Long): List<StatementImportEntity>

    /** Whether this installation holds any statement at all: a first connection has nothing yet. */
    @Query("SELECT COUNT(*) FROM statement_imports")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: StatementImportEntity): Long

    @Query(
        "SELECT * FROM statement_imports WHERE accountId = :accountId AND periodFrom IS NOT NULL " +
            "AND openingBalanceMinor IS NOT NULL ORDER BY periodFrom, importedAt, id LIMIT 1",
    )
    suspend fun earliestWithOpeningBalance(accountId: Long): StatementImportEntity?

    /** Only no-op imports are safe to forget: no ledger rows or reconciliations depend on them. */
    @Query(
        "DELETE FROM statement_imports WHERE id = :id AND inserted = 0 AND reconciled = 0 AND reviewCount = 0",
    )
    suspend fun deleteIfNoEffect(id: Long): Int
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

    @Query(
        "SELECT * FROM sms_diagnostics " +
            "WHERE transactionId IS NULL " +
            "AND outcome IN ('NEEDS_CARD_MAPPING', 'CHOOSE_ACCOUNT') " +
            "AND occurredAt IS NOT NULL AND amountMinor IS NOT NULL AND currency IS NOT NULL " +
            "ORDER BY occurredAt DESC, id DESC"
    )
    fun observeUnrouted(): Flow<List<SmsDiagnosticEntity>>

    @Query(
        "SELECT * FROM sms_diagnostics WHERE transactionId IS NULL " +
            "AND outcome IN ('NEEDS_CARD_MAPPING', 'CHOOSE_ACCOUNT') " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis ORDER BY occurredAt, id"
    )
    suspend fun unroutedBetween(fromMillis: Long, toMillis: Long): List<SmsDiagnosticEntity>

    @Query(
        "SELECT * FROM sms_diagnostics WHERE transactionId IS NULL " +
            "AND outcome IN ('NEEDS_CARD_MAPPING', 'CHOOSE_ACCOUNT') " +
            "AND occurredAt IS NOT NULL AND amountMinor IS NOT NULL AND currency IS NOT NULL " +
            "ORDER BY occurredAt, id"
    )
    suspend fun unrouted(): List<SmsDiagnosticEntity>

    /**
     * Whether some other message already explains this ledger row: one row never explains two.
     * The message being evaluated is excluded, so re-reading it does not refuse its own row.
     */
    @Query(
        "SELECT COUNT(*) FROM sms_diagnostics " +
            "WHERE transactionId = :transactionId AND externalKey != :externalKey"
    )
    suspend fun countOtherForTransaction(transactionId: Long, externalKey: String): Int

    @Query("SELECT * FROM sms_diagnostics WHERE id = :id")
    suspend fun byId(id: Long): SmsDiagnosticEntity?

    @Query(
        "SELECT * FROM sms_diagnostics WHERE kind = 'CARD_PAYMENT' " +
            "AND cardLast4 = :last4 AND transactionId IS NULL " +
            "AND outcome IN ('NEEDS_CARD_MAPPING', 'CHOOSE_ACCOUNT') " +
            "ORDER BY occurredAt, id"
    )
    suspend fun unresolvedCardPayments(last4: String): List<SmsDiagnosticEntity>

    @Query("SELECT * FROM sms_diagnostics WHERE externalKey = :externalKey LIMIT 1")
    suspend fun byExternalKey(externalKey: String): SmsDiagnosticEntity?

    /**
     * Possible payments a cancellation could retract. Identity and ambiguity are deliberately
     * decided by SmsCancellationMatcher, where merchant and all candidates are still visible.
     */
    @Query(
        "SELECT * FROM sms_diagnostics WHERE kind = 'CARD_PAYMENT' " +
            "AND (transactionId IS NOT NULL OR reason = 'STATEMENT_COVERS_PERIOD') " +
            "AND externalKey != :cancellationExternalKey " +
            "AND amountMinor = :amountMinor AND currency = :currency AND cardLast4 = :cardLast4 " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY ABS(occurredAt - :occurredAt), id"
    )
    suspend fun cancellationCandidates(
        cancellationExternalKey: String,
        amountMinor: Long,
        currency: String,
        cardLast4: String,
        occurredAt: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<SmsDiagnosticEntity>

    @Query(
        "SELECT * FROM sms_diagnostics WHERE kind = :kind AND transactionId IS NOT NULL " +
            "AND amountMinor = :amountMinor AND currency = :currency " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis AND id != :excludeId " +
            "ORDER BY ABS(occurredAt - :occurredAt), id"
    )
    suspend fun matchingImported(
        kind: SmsDiagnosticKind,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        fromMillis: Long,
        toMillis: Long,
        excludeId: Long = 0,
    ): List<SmsDiagnosticEntity>

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
