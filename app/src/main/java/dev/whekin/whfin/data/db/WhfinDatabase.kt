package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The schema a first install creates.
 *
 * Development collapsed its accumulated versions into this one baseline while WHFIN had no user but
 * its author and no copy worth keeping. From the first real release onwards that is no longer true:
 * every schema change then has to arrive as a data-preserving migration with a test, because the
 * ledger on the other side is somebody's actual money.
 */
const val WHFIN_DATABASE_VERSION = 1

@Database(
    entities = [
        AccountEntity::class,
        FinancialGroupEntity::class,
        PaymentInstrumentEntity::class,
        InstrumentAccountLinkEntity::class,
        WalletAddressEntity::class,
        CryptoAssetEntity::class,
        TransferGroupEntity::class,
        StatementSourceEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        TransactionEntity::class,
        PersonEntity::class,
        TransactionAllocationEntity::class,
        DebtCaseEntity::class,
        DebtEventEntity::class,
        StatementImportEntity::class,
        ReconciliationIssueEntity::class,
        SmsDiagnosticEntity::class,
        CryptoBalanceEntity::class,
        ExchangeRateEntity::class,
        ExchangeRateHistoryEntity::class,
        CounterpartyRuleEntity::class,
        IncomeSourceEntity::class,
    ],
    version = WHFIN_DATABASE_VERSION,
    exportSchema = true,
)
abstract class WhfinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun transactionAllocationDao(): TransactionAllocationDao
    abstract fun debtDao(): DebtDao
    abstract fun statementImportDao(): StatementImportDao
    abstract fun reconciliationIssueDao(): ReconciliationIssueDao
    abstract fun financialGroupDao(): FinancialGroupDao
    abstract fun paymentInstrumentDao(): PaymentInstrumentDao
    abstract fun cryptoDao(): CryptoDao
    abstract fun statementSourceDao(): StatementSourceDao
    abstract fun smsDiagnosticDao(): SmsDiagnosticDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun counterpartyRuleDao(): CounterpartyRuleDao
    abstract fun incomeSourceDao(): IncomeSourceDao

    companion object {
        const val NAME = "whfin.db"

        @Volatile
        private var instance: WhfinDatabase? = null

        fun get(context: Context): WhfinDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context, NAME).also { instance = it }
            }

        fun open(context: Context, name: String): WhfinDatabase = Room.databaseBuilder(
            context.applicationContext,
            WhfinDatabase::class.java,
            name,
        ).build()
    }
}
