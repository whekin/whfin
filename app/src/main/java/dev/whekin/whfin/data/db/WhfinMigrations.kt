package dev.whekin.whfin.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debt_cases` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personId` INTEGER NOT NULL,
                `direction` TEXT NOT NULL,
                `originalAmountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `openedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `closedAt` INTEGER,
                `note` TEXT,
                FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_cases_personId` ON `debt_cases` (`personId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_cases_status` ON `debt_cases` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_cases_openedAt` ON `debt_cases` (`openedAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debt_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `debtCaseId` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `actualAmountMinor` INTEGER,
                `actualCurrency` TEXT,
                `accountId` INTEGER,
                `transactionId` INTEGER,
                `debtValueMinor` INTEGER NOT NULL,
                `closesCase` INTEGER NOT NULL,
                `occurredAt` INTEGER NOT NULL,
                `note` TEXT,
                FOREIGN KEY(`debtCaseId`) REFERENCES `debt_cases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_events_debtCaseId` ON `debt_events` (`debtCaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_events_accountId` ON `debt_events` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_events_transactionId` ON `debt_events` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_events_occurredAt` ON `debt_events` (`occurredAt`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sms_diagnostics` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `externalKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `outcome` TEXT NOT NULL,
                `reason` TEXT,
                `receivedAt` INTEGER NOT NULL,
                `occurredAt` INTEGER,
                `amountMinor` INTEGER,
                `currency` TEXT,
                `secondaryAmountMinor` INTEGER,
                `secondaryCurrency` TEXT,
                `balanceMinor` INTEGER,
                `balanceCurrency` TEXT,
                `cardLast4` TEXT,
                `counterparty` TEXT,
                `fromIban` TEXT,
                `toIban` TEXT,
                `transactionId` INTEGER,
                `accountId` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_diagnostics_externalKey` ON `sms_diagnostics` (`externalKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_diagnostics_outcome` ON `sms_diagnostics` (`outcome`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_diagnostics_receivedAt` ON `sms_diagnostics` (`receivedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_diagnostics_transactionId` ON `sms_diagnostics` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_diagnostics_accountId` ON `sms_diagnostics` (`accountId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `statement_imports` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'FILE'")
        // These names are generated only by our MyCredo connector. Existing user-picked files remain FILE.
        db.execSQL(
            "UPDATE `statement_imports` SET `origin` = 'CREDO_SYNC' " +
                "WHERE UPPER(`fileName`) LIKE 'MYCREDO\\_%' ESCAPE '\\'",
        )
    }
}

/**
 * Watch-only chain balances are observations, not transactions, so they get their own table with the
 * moment they were read. Nothing existing is touched: an empty table simply means "never refreshed".
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `crypto_balances` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `baseUnits` TEXT NOT NULL,
                `decimals` INTEGER NOT NULL,
                `observedAt` INTEGER NOT NULL,
                `source` TEXT,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_crypto_balances_accountId` ON `crypto_balances` (`accountId`)")
    }
}

/**
 * Quotes are observations too: an empty table means "no rate yet", which the UI must show as an
 * unconverted native amount rather than as a zero.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exchange_rates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `code` TEXT NOT NULL,
                `gelPerUnit` TEXT NOT NULL,
                `observedAt` INTEGER NOT NULL,
                `validOn` TEXT,
                `source` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_code` ON `exchange_rates` (`code`)")
    }
}

/**
 * The GEL value of a foreign-currency row is booked once, at the rate of its own day.
 *
 * Existing rows start empty rather than being back-valued at today's rate, because that would make
 * every past month drift with the market. A backfill fills them from the historical quote instead.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `gelValueMinor` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `gelRateOn` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exchange_rate_history` (
                `code` TEXT NOT NULL,
                `onDate` TEXT NOT NULL,
                `gelPerUnit` TEXT NOT NULL,
                `validOn` TEXT,
                `observedAt` INTEGER NOT NULL,
                PRIMARY KEY(`code`, `onDate`)
            )
            """.trimIndent(),
        )
    }
}

/** Corrections are append-only: active queries ignore the voided source row, but backup keeps it. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isVoided` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `correctionOfTransactionId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_correctionOfTransactionId` " +
                "ON `transactions` (`correctionOfTransactionId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_isVoided` ON `transactions` (`isVoided`)")
    }
}

/** Debt corrections preserve the event log while removing mistaken events from balances. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `debt_events` ADD COLUMN `isVoided` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `debt_events` ADD COLUMN `correctionOfEventId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_debt_events_correctionOfEventId` " +
                "ON `debt_events` (`correctionOfEventId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_events_isVoided` ON `debt_events` (`isVoided`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
)
