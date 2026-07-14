package dev.whekin.whfin.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "financial_groups", indices = [Index("type")])
data class FinancialGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: FinancialGroupType,
    /** Bank name/provider for BANK; wallet app name for WALLET. */
    val provider: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(entity = FinancialGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WalletAddressEntity::class, parentColumns = ["id"], childColumns = ["walletAddressId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CryptoAssetEntity::class, parentColumns = ["id"], childColumns = ["cryptoAssetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("groupId"), Index("walletAddressId"), Index("cryptoAssetId"),
        // Credo can export several currency ledgers under the same IBAN.
        Index(value = ["iban", "currency"], unique = true),
        Index(value = ["walletAddressId", "cryptoAssetId"], unique = true),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val groupId: Long? = null,
    /** ISO 4217 для фиата (GEL, USD), тикер для крипты (USDT). */
    val currency: String,
    val iban: String? = null,
    /** For CRYPTO: one balance = one address/network + one asset contract. */
    val walletAddressId: Long? = null,
    val cryptoAssetId: Long? = null,
    /** Цель накопления для SAVINGS, в minor units. */
    val savingsGoalMinor: Long? = null,
    val savingsMode: SavingsMode? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "payment_instruments",
    foreignKeys = [ForeignKey(
        entity = FinancialGroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("groupId"), Index(value = ["groupId", "last4"], unique = true)],
)
data class PaymentInstrumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val type: PaymentInstrumentType,
    val last4: String,
    val label: String? = null,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "instrument_account_links",
    primaryKeys = ["instrumentId", "accountId"],
    foreignKeys = [
        ForeignKey(entity = PaymentInstrumentEntity::class, parentColumns = ["id"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("accountId")],
)
data class InstrumentAccountLinkEntity(val instrumentId: Long, val accountId: Long)

@Entity(
    tableName = "wallet_addresses",
    foreignKeys = [ForeignKey(entity = FinancialGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId"), Index(value = ["chainId", "address"], unique = true)],
)
data class WalletAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    /** CAIP-2-ish stable id, e.g. eip155:1 or tron:mainnet. */
    val chainId: String,
    val address: String,
    val label: String? = null,
)

@Entity(
    tableName = "crypto_assets",
    indices = [Index(value = ["chainId", "contractAddress"], unique = true)],
)
data class CryptoAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chainId: String,
    /** null means native asset; symbols are display-only and never identity. */
    val contractAddress: String? = null,
    val symbol: String,
    val name: String,
    val decimals: Int,
)

@Entity(tableName = "transfer_groups", indices = [Index("type")])
data class TransferGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransferGroupType,
    val note: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Двухуровневое дерево: null = корневая категория. */
    val parentId: Long? = null,
    val kind: CategoryKind,
    /** Имя Material-иконки. */
    val icon: String,
    /** ARGB. */
    val color: Int,
    /** Системные (Unaccounted) нельзя удалить/переименовать. */
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * Мерчант/контрагент с выученной категорией — память "раз присвоил — навсегда".
 * Покрывает и магазины (EXAMPLE MARKET), и отправителей (EXAMPLE EMPLOYER -> Salary),
 * и получателей переводов (EXAMPLE LANDLORD -> Rent).
 */
@Entity(
    tableName = "merchants",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index(value = ["normalizedKey"], unique = true), Index("categoryId")],
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Каноническое нормализованное имя (lowercase, без ">город страна", trim). */
    val normalizedKey: String,
    val displayName: String,
    val categoryId: Long? = null,
)

/** Алиас написания мерчанта (nikora trade jsc -> NIKORA). Pattern хранится нормализованным. */
@Entity(
    tableName = "merchant_aliases",
    foreignKeys = [ForeignKey(
        entity = MerchantEntity::class,
        parentColumns = ["id"],
        childColumns = ["merchantId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["pattern"], unique = true), Index("merchantId")],
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantId: Long,
    val pattern: String,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransferGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferGroupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["accountId", "occurredAt"]),
        Index("categoryId"),
        Index("merchantId"),
        Index("transferGroupId"),
        Index(value = ["externalKey"], unique = true),
        Index("status"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Minor units валюты счёта, со знаком: расход < 0, доход > 0. */
    val amountMinor: Long,
    val currency: String,
    /** Оригинальная сумма для FX-платежей (23.60 USD при списании в GEL). */
    val origAmountMinor: Long? = null,
    val origCurrency: String? = null,
    /** Момент покупки (из SMS / из Description выписки), epoch millis UTC. */
    val occurredAt: Long,
    /** Момент списания по выписке (на 1-2 дня позже покупки). */
    val postedAt: Long? = null,
    val merchantId: Long? = null,
    /** Сырое имя мерчанта/контрагента как пришло из источника (до нормализации). */
    val rawCounterparty: String? = null,
    val counterpartyIban: String? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val status: TxStatus,
    val source: TxSource,
    /** Multi-leg transfer/conversion/bridge group. */
    val transferGroupId: Long? = null,
    /**
     * Перевод между своими счетами/конвертация, даже если парная транзакция
     * ещё не создана (счёт-получатель не заведён). Исключается из статистики трат.
     */
    @ColumnInfo(defaultValue = "0")
    val isTransfer: Boolean = false,
    /** Баланс счёта после операции (из SMS/выписки) — для сверки цепочки. */
    val balanceAfterMinor: Long? = null,
    /** Ключ дедупликации между источниками. */
    val externalKey: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
)

@Entity(tableName = "people", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: PersonRole? = null,
    val color: Int,
    val isArchived: Boolean = false,
)

/**
 * Доля реальной банковской транзакции. Если долей нет, используются categoryId и amountMinor родителя.
 * Сумма всех долей должна равняться amountMinor родительской транзакции.
 */
@Entity(
    tableName = "transaction_allocations",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("transactionId"), Index("categoryId"), Index("personId")],
)
data class TransactionAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    /** Signed minor units; allocations sum exactly to the parent transaction amount. */
    val amountMinor: Long,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val purpose: AllocationPurpose,
    val note: String? = null,
)

@Entity(
    tableName = "debt_cases",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId"), Index("status"), Index("openedAt")],
)
data class DebtCaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val direction: DebtDirection,
    /** Positive amount in the debt's own currency. */
    val originalAmountMinor: Long,
    val currency: String,
    val openedAt: Long,
    val status: DebtStatus = DebtStatus.OPEN,
    val closedAt: Long? = null,
    val note: String? = null,
)

@Entity(
    tableName = "debt_events",
    foreignKeys = [
        ForeignKey(entity = DebtCaseEntity::class, parentColumns = ["id"], childColumns = ["debtCaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("debtCaseId"), Index("accountId"), Index("transactionId"), Index("occurredAt")],
)
data class DebtEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtCaseId: Long,
    val kind: DebtEventKind,
    /** What really moved. It may use another amount/currency than the debt. */
    val actualAmountMinor: Long? = null,
    val actualCurrency: String? = null,
    val accountId: Long? = null,
    val transactionId: Long? = null,
    /** Positive reduction of the outstanding amount, denominated in DebtCase.currency. */
    val debtValueMinor: Long = 0,
    val closesCase: Boolean = false,
    val occurredAt: Long,
    val note: String? = null,
)

@Entity(
    tableName = "statement_sources",
    foreignKeys = [
        ForeignKey(entity = FinancialGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PaymentInstrumentEntity::class, parentColumns = ["id"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId"), Index("accountId"), Index("instrumentId")],
)
data class StatementSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val type: StatementSourceType,
    val accountId: Long? = null,
    val instrumentId: Long? = null,
    val label: String,
)

@Entity(
    tableName = "statement_imports",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StatementSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("sourceId"), Index(value = ["accountId", "periodFrom", "periodTo"])],
)
data class StatementImportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val sourceId: Long? = null,
    val fileName: String? = null,
    @ColumnInfo(defaultValue = "'FILE'")
    val origin: StatementImportOrigin = StatementImportOrigin.FILE,
    /** Inclusive statement coverage, epoch day. */
    val periodFrom: Long?,
    val periodTo: Long?,
    val openingBalanceMinor: Long?,
    val closingBalanceMinor: Long?,
    val totalRows: Int,
    val inserted: Int,
    val duplicates: Int,
    val reconciled: Int,
    @ColumnInfo(defaultValue = "0")
    val reviewCount: Int = 0,
    val importedAt: Long,
)

@Entity(
    tableName = "reconciliation_issues",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StatementImportEntity::class, parentColumns = ["id"], childColumns = ["importId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("accountId"), Index(value = ["transactionId"], unique = true), Index("importId"), Index("state")],
)
data class ReconciliationIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val transactionId: Long,
    val importId: Long,
    val state: ReconciliationIssueState = ReconciliationIssueState.OPEN,
    val createdAt: Long,
)

/**
 * Structured local audit record for SMS processing. Raw message bodies are never stored.
 * Parsed fields are sufficient to explain and retry account/card resolution.
 */
@Entity(
    tableName = "sms_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["externalKey"], unique = true),
        Index("outcome"),
        Index("receivedAt"),
        Index("transactionId"),
        Index("accountId"),
    ],
)
data class SmsDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalKey: String,
    val kind: SmsDiagnosticKind,
    val outcome: SmsDiagnosticOutcome,
    val reason: SmsDiagnosticReason? = null,
    val receivedAt: Long,
    val occurredAt: Long? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val secondaryAmountMinor: Long? = null,
    val secondaryCurrency: String? = null,
    val balanceMinor: Long? = null,
    val balanceCurrency: String? = null,
    val cardLast4: String? = null,
    val counterparty: String? = null,
    val fromIban: String? = null,
    val toIban: String? = null,
    val transactionId: Long? = null,
    val accountId: Long? = null,
    val updatedAt: Long,
)
