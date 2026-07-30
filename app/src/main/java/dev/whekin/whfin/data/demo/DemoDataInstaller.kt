package dev.whekin.whfin.data.demo

import android.content.Context
import androidx.room.withTransaction
import dev.whekin.whfin.data.backup.WhfinBackupManager
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CryptoBalanceEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class DemoDataInstaller(
    private val context: Context,
    private val database: WhfinDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun install() {
        val summary = context.assets.open(ASSET_NAME).use { input ->
            WhfinBackupManager(database).restore(input)
        }
        val fixtureDate = summary.exportedAt.atZone(ZoneOffset.UTC).toLocalDate()
        val today = LocalDate.now(clock)
        val dayDelta = ChronoUnit.DAYS.between(fixtureDate, today)
        if (dayDelta != 0L) shiftDates(dayDelta)
        seedChainBalances()
    }

    /**
     * Chain balances are observations, not ledger rows, so the portable fixture cannot carry them.
     * The demo seeds them directly instead of asking a public node about a made-up address.
     */
    private suspend fun seedChainBalances() {
        val observedAt = clock.millis() - OBSERVED_MINUTES_AGO
        database.withTransaction {
            val accounts = database.accountDao().allActive()
                .filter { it.type == AccountType.CRYPTO }
            accounts.forEach { account ->
                val asset = account.cryptoAssetId?.let { database.cryptoDao().assetById(it) }
                    ?: return@forEach
                val baseUnits = DEMO_CHAIN_BALANCES[asset.symbol to asset.chainId] ?: return@forEach
                database.cryptoDao().upsertBalance(
                    CryptoBalanceEntity(
                        accountId = account.id,
                        baseUnits = baseUnits,
                        decimals = asset.decimals,
                        observedAt = observedAt,
                        source = "demo",
                    ),
                )
            }
        }
        database.invalidationTracker.refreshAsync()
    }

    private suspend fun shiftDates(dayDelta: Long) {
        val millisDelta = Math.multiplyExact(dayDelta, MILLIS_PER_DAY)
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            listOf(
                "transfer_groups" to listOf("createdAt"),
                "transactions" to listOf("occurredAt", "postedAt", "createdAt"),
                "debt_cases" to listOf("openedAt", "closedAt"),
                "debt_events" to listOf("occurredAt"),
                "statement_imports" to listOf("importedAt"),
                "reconciliation_issues" to listOf("createdAt"),
            ).forEach { (table, columns) ->
                columns.forEach { column ->
                    sql.execSQL(
                        "UPDATE `$table` SET `$column` = `$column` + ? WHERE `$column` IS NOT NULL",
                        arrayOf(millisDelta),
                    )
                }
            }
            listOf("periodFrom", "periodTo").forEach { column ->
                sql.execSQL(
                    "UPDATE `statement_imports` SET `$column` = `$column` + ? WHERE `$column` IS NOT NULL",
                    arrayOf(dayDelta),
                )
            }
        }
        database.invalidationTracker.refreshAsync()
    }

    companion object {
        const val DATABASE_NAME = "whfin-demo.db"
        const val ASSET_NAME = "whfin-demo-v7.json"
        const val FIXTURE_VERSION = 1
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val OBSERVED_MINUTES_AGO = 26L * 60 * 1000

        /** Invented holdings, in exact base units, keyed by asset and chain. */
        private val DEMO_CHAIN_BALANCES = mapOf(
            ("TRX" to "tron:mainnet") to "4821500000",
            ("USDT" to "tron:mainnet") to "1250750000",
            ("USDT" to "eip155:1") to "318400000",
        )
    }
}
