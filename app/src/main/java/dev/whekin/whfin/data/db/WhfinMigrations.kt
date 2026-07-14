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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
