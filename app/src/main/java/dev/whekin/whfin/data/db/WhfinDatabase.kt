package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The schema a first install creates.
 *
 * Development collapsed its accumulated versions into this one baseline while WHFIN had no user but
 * its author and no copy worth keeping. From the first real release onwards that is no longer true:
 * every schema change then has to arrive as a data-preserving migration with a test, because the
 * ledger on the other side is somebody's actual money.
 */
const val WHFIN_DATABASE_VERSION = 6

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
        IncomeSourcePaymentEntity::class,
        SavingsPlanEntity::class,
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
    abstract fun savingsPlanDao(): SavingsPlanDao

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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_plans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`monthlyTargetMinor` INTEGER NOT NULL, " +
                "`goalMinor` INTEGER, " +
                "`goalBy` INTEGER, " +
                "`startedOn` INTEGER NOT NULL, " +
                "`endedOn` INTEGER, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_plans_currency_startedOn` " +
                "ON `savings_plans` (`currency`, `startedOn`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_savings_plans_endedOn` ON `savings_plans` (`endedOn`)",
        )
    }
}

/**
 * Where the bank's own deposit number is kept, on both sides of the question that learns it.
 *
 * Two nullable columns and nothing else: existing rows are correct as null, because nobody has been
 * asked yet. `accounts` holds the answer once given, `sms_diagnostics` holds the number the message
 * printed until then — the question has to be able to say which deposit it is about.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `depositNumber` TEXT")
        db.execSQL("ALTER TABLE `sms_diagnostics` ADD COLUMN `depositNumber` TEXT")
    }
}

/** Payday is an estimate with a weekend preference, not a mandatory date range. */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `income_sources` ADD COLUMN `weekendRule` TEXT NOT NULL DEFAULT 'EARLIER'")
    }
}

/** Which credits the owner confirmed as a declared source's pay; nothing is inferred for them. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `income_source_payments` (" +
                "`transactionId` INTEGER NOT NULL, `incomeSourceId` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`), " +
                "FOREIGN KEY(`incomeSourceId`) REFERENCES `income_sources`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_income_source_payments_incomeSourceId` " +
                "ON `income_source_payments` (`incomeSourceId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_income_source_payments_transactionId` " +
                "ON `income_source_payments` (`transactionId`)",
        )
    }
}

/** The hand-made link outgrew the wallet it was built for and is now named for what it is. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `transfer_groups` SET `type` = 'OWN_LINK' WHERE `type` = 'CRYPTO_BRIDGE'")
    }
}
