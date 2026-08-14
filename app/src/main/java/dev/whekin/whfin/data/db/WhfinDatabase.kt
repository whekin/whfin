package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val WHFIN_DATABASE_VERSION = 3

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `payment_instruments` ADD COLUMN `isPrimary` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/** Adds the transfer-recipient memory. Existing rows are untouched: the table starts empty. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `counterparty_rules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`iban` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`personId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_counterparty_rules_iban` " +
                "ON `counterparty_rules` (`iban`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counterparty_rules_categoryId` " +
                "ON `counterparty_rules` (`categoryId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counterparty_rules_personId` " +
                "ON `counterparty_rules` (`personId`)",
        )
    }
}

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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
